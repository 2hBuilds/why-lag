package com.whylag.core;

/**
 * The client thread's per-frame accumulation (contract 3.10 and section 7, L2): every {@link #frame} call is
 * counted into the session second it lands in, and the first frame PAST a second's boundary closes that second into
 * the frame columns of {@link SecondRing}. Only a frame closes a second, so the ring's frame head always says where
 * the newest frame is ({@link Session#framesStopped}).
 *
 * <p><b>Missed seconds.</b> A second in which no {@code frame()} call landed is written as a filler: frames 0,
 * worst frame 0 and the {@link Flags#NO_FRAMES} flag. EVERY missed second is written, however
 * many there are; a stall is charged to the second it ENDED in, with {@code worstFrameEndMs} = where in that second
 * it ended.
 *
 * <p><b>The first frame</b> starts the frame clock: it counts in {@code frames} but closes no interval, so it adds
 * nothing to the worst frame or the slow frames.
 *
 * <p><b>The state and the flags of a second.</b> {@code state} is the code in force at the second's END;
 * {@link Flags#NOT_LOGGED_IN} goes with OTHER, LOGIN_SCREEN and LOGGING_IN; {@link Flags#LOADING},
 * {@link Flags#HOP} and {@link Flags#DISCONNECT} are set on every second that overlaps a stretch of that state (a
 * stretch runs from the {@code state()} call that entered it to the call that left it); {@code loadingMs} is the ms
 * of the second inside LOADING stretches; {@link Flags#LOADING} is also set on the second in which a frame interval
 * that overlapped any LOADING time ENDED (the loading-frame rule, contract 6.1); {@link Flags#FOCUSED}, the world
 * and the scene counts are the values in force when the second is closed; {@link Flags#LOGIN_MASK} is set on the
 * seconds in which a masked tick arrived (the tick sampler says which, {@link #maskSecond}).
 *
 * <p>Everything here runs on the client thread but {@link #focus}, which is one volatile write. {@link #frame}
 * allocates nothing, takes no lock, logs nothing and builds no string (T1, T2).
 *
 * <p>
 * Choice: state changes are kept, with their times, until the frame that closes their second; at most
 * {@value #PENDING_STATES} wait at once, and one more overwrites the code of the newest (its time is kept).
 * <br>
 * Choice: a {@code state()} call with the code already in force is no transition and changes nothing.
 * <br>
 * Choice: the state before the first {@code state()} call is OTHER, and the focus before the first
 * {@code focus()} call is true.
 * <br>
 * Choice: of two frames of the same length the LATER is the second's worst frame (as {@code Trace.frameGap}).
 * <br>
 * Choice: {@code slowFrames} stops counting at 127, the column's top.
 * <br>
 * Choice: {@link #resetWorstSinceTick()} also restarts {@link #loadingSinceTick()}, which stays true while the
 * state is LOADING.
 * <br>
 * Choice: at most {@value #PENDING_MASKS} masked seconds wait for their close; one more drops the oldest.
 * <br>
 * Choice: a frame whose time lies before the open second (a clock that ran backwards) counts in the open
 * second.
 */
public final class FrameSampler
{
	private static final long NANOS_PER_SECOND = 1_000_000_000L;
	private static final long NANOS_PER_MS = 1_000_000L;
	private static final int MS_PER_SECOND = 1000;
	/** The last ms of a second: the top of {@code worstFrameEndMs}. */
	private static final int LAST_MS = MS_PER_SECOND - 1;
	/** State changes that may wait for the frame that closes their second. */
	private static final int PENDING_STATES = 64;
	/** Masked seconds that may wait for their close. */
	private static final int PENDING_MASKS = 64;

	private final Session session;
	private final SecondRing ring;
	private final SelfTimer timer;
	/** The one carrier, reused for every second. */
	private final FrameSecond record = new FrameSecond();

	private volatile boolean focused = true;
	private int world, players, npcs, region;

	// ---- the frame clock
	private boolean started;
	private long lastFrameNanos;
	private int worstSinceTickMs;

	// ---- the open second
	private long openSec;
	private int frames, worstMs, worstEndMs, slow, extraFlags;

	// ---- the states: the code in force now, the code in force where the pending list starts, and the list
	private int liveState = State.OTHER;
	private int baseState = State.OTHER;
	/** Session nanos (since {@link Session#startNanos}) of each pending change, ascending. */
	private final long[] pendingAt = new long[PENDING_STATES];
	private final int[] pendingCode = new int[PENDING_STATES];
	private int pending;
	private boolean loadingSinceFrame, loadingSinceTick;

	// ---- the answer of walk(): plain fields, so that it allocates nothing
	private int walkFlags, walkState, walkUsed;
	private long walkLoadNanos;

	// ---- the seconds in which a masked tick arrived and which are not closed yet, ascending, a circular queue
	private final long[] maskSecs = new long[PENDING_MASKS];
	private int maskFirst, maskCount;

	public FrameSampler(Session s, SelfTimer timer)
	{
		this.session = s;
		this.ring = s.seconds;
		this.timer = timer;
		this.openSec = s.seconds.frameHead() + 1;
	}

	/**
	 * One frame, stamped {@code nanos} ({@code System.nanoTime()}), with the game cycle. Client thread; allocates
	 * nothing.
	 */
	public void frame(long nanos, int gameCycle)
	{
		final boolean timed = timer != null && timer.on();
		final long t0 = timed ? System.nanoTime() : 0L;

		final long sec = session.secOf(nanos);
		while (openSec < sec)
		{
			closeOpen();
		}
		if (frames < Integer.MAX_VALUE)
		{
			frames++;
		}
		if (started)
		{
			final long wall = Math.max(0L, nanos - lastFrameNanos);
			final int ms = (int) Math.min(wall / NANOS_PER_MS, Integer.MAX_VALUE);
			if (ms >= worstMs)
			{
				worstMs = ms;
				worstEndMs = (int) Math.max(0L, Math.min(LAST_MS, session.msOf(nanos) - openSec * MS_PER_SECOND));
			}
			if (ms >= Thresholds.SLOW_FRAME_MS && slow < Byte.MAX_VALUE)
			{
				slow++;
			}
			if (ms > worstSinceTickMs)
			{
				worstSinceTickMs = ms;
			}
			if (loadingSinceFrame || liveState == State.LOADING)
			{
				extraFlags |= Flags.LOADING;
			}
		}
		started = true;
		lastFrameNanos = nanos;
		loadingSinceFrame = false;

		if (timed)
		{
			timer.add(SelfTimer.FRAME, System.nanoTime() - t0);
		}
	}

	/** The game state changed to {@code stateCode} ({@link State}) at {@code nanos}. Client thread. */
	public void state(long nanos, int stateCode)
	{
		if (stateCode == liveState)
		{
			return;
		}
		if (stateCode == State.LOADING || liveState == State.LOADING)
		{
			loadingSinceFrame = true;
			loadingSinceTick = true;
		}
		long at = nanos - session.startNanos;
		if (pending > 0 && at < pendingAt[pending - 1])
		{
			at = pendingAt[pending - 1];
		}
		if (pending == PENDING_STATES)
		{
			pendingCode[pending - 1] = stateCode;
		}
		else
		{
			pendingAt[pending] = at;
			pendingCode[pending] = stateCode;
			pending++;
		}
		liveState = stateCode;
	}

	/** The world the player is in; written into every second closed from now on. Client thread. */
	public void world(int world)
	{
		this.world = world;
	}

	/** The scene counts and the region; written into every second closed from now on. Client thread. */
	public void scene(int players, int npcs, int region)
	{
		this.players = players;
		this.npcs = npcs;
		this.region = region;
	}

	/** The window's focus: one volatile write, from any thread. A second takes the value in force at its close. */
	public void focus(boolean focused)
	{
		this.focused = focused;
	}

	/** The longest CLOSED frame interval since {@link #resetWorstSinceTick()}, in ms; 0 before the first frame. */
	public int worstFrameSinceTickMs()
	{
		return worstSinceTickMs;
	}

	/** Ms since the last {@link #frame} call, the frame still OPEN at {@code nanos}; 0 before the first. */
	public int openFrameMs(long nanos)
	{
		if (!started)
		{
			return 0;
		}
		final long wall = Math.max(0L, nanos - lastFrameNanos);
		return (int) Math.min(wall / NANOS_PER_MS, Integer.MAX_VALUE);
	}

	/**
	 * A tick has taken its frame time: {@link #worstFrameSinceTickMs()} starts again, and so does
	 * {@link #loadingSinceTick()}.
	 */
	public void resetWorstSinceTick()
	{
		worstSinceTickMs = 0;
		loadingSinceTick = liveState == State.LOADING;
	}

	/**
	 * True when any LOADING time lies between the last {@link #resetWorstSinceTick()} and now. Client thread. The
	 * fixed API of contract 3.10 names no reader for it: a tick row takes its flags from its second alone
	 * ({@link TickSampler}), so no LOADING reaches a row through this.
	 */
	public boolean loadingSinceTick()
	{
		return loadingSinceTick || liveState == State.LOADING;
	}

	// ---------------------------------------------------------------- for the tick sampler (package-private)

	/** A masked tick arrived in session second {@code sec}: that second gets {@link Flags#LOGIN_MASK} at its close. */
	void maskSecond(long sec)
	{
		if (sec < openSec)
		{
			return;
		}
		if (maskCount > 0 && maskSecs[(maskFirst + maskCount - 1) % PENDING_MASKS] >= sec)
		{
			return;
		}
		if (maskCount == PENDING_MASKS)
		{
			maskFirst = (maskFirst + 1) % PENDING_MASKS;
			maskCount--;
		}
		maskSecs[(maskFirst + maskCount) % PENDING_MASKS] = sec;
		maskCount++;
	}

	/**
	 * The flags of the second that {@code nanos} lies in, as they stand at {@code nanos}: what a tick that arrives
	 * then carries. {@link Flags#NO_FRAMES} is not among them; it is known only at the close.
	 */
	int flagsAt(long nanos)
	{
		final long sec = session.secOf(nanos);
		walk(sec * NANOS_PER_SECOND, nanos - session.startNanos, false);
		int fl = walkFlags | stateFlag(walkState);
		if (focused)
		{
			fl |= Flags.FOCUSED;
		}
		if (sec == openSec)
		{
			fl |= extraFlags;
		}
		for (int i = 0; i < maskCount; i++)
		{
			if (maskSecs[(maskFirst + i) % PENDING_MASKS] == sec)
			{
				fl |= Flags.LOGIN_MASK;
				break;
			}
		}
		return fl;
	}

	// ---------------------------------------------------------------- closing a second

	/** Writes the open second into the ring and opens the next. */
	private void closeOpen()
	{
		final long start = openSec * NANOS_PER_SECOND;
		walk(start, start + NANOS_PER_SECOND, true);
		consume(walkUsed);
		baseState = walkState;

		int fl = walkFlags | extraFlags | stateFlag(walkState);
		if (focused)
		{
			fl |= Flags.FOCUSED;
		}
		if (frames == 0)
		{
			fl |= Flags.NO_FRAMES;
		}
		while (maskCount > 0 && maskSecs[maskFirst] < openSec)
		{
			maskFirst = (maskFirst + 1) % PENDING_MASKS;
			maskCount--;
		}
		if (maskCount > 0 && maskSecs[maskFirst] == openSec)
		{
			fl |= Flags.LOGIN_MASK;
			maskFirst = (maskFirst + 1) % PENDING_MASKS;
			maskCount--;
		}

		final FrameSecond r = record;
		r.frames = frames;
		r.worstFrameMs = worstMs;
		r.worstFrameEndMs = worstEndMs;
		r.slowFrames = slow;
		r.loadingMs = (int) Math.min(MS_PER_SECOND, walkLoadNanos / NANOS_PER_MS);
		r.state = walkState;
		r.flags = fl;
		r.world = world;
		r.players = players;
		r.npcs = npcs;
		r.region = region;
		ring.putFrame(openSec, r);

		openSec++;
		frames = 0;
		worstMs = 0;
		worstEndMs = 0;
		slow = 0;
		extraFlags = 0;
	}

	/**
	 * Walks the pending state changes over the span {@code start .. end} (session nanos) and leaves the answer in
	 * the four {@code walk} fields: the LOADING, HOP and DISCONNECT bits of every stretch that overlaps the span,
	 * the LOADING time inside it, the code in force at its end and how many pending changes lie before that end.
	 * {@code closing}: the span is a whole second, its end excluded. Otherwise the span ends at a moment inside a
	 * second, that moment included, and the code in force at it counts even when it was entered at that moment.
	 */
	private void walk(long start, long end, boolean closing)
	{
		int st = baseState;
		long pos = start;
		int fl = 0;
		long load = 0;
		int i = 0;
		while (i < pending && (closing ? pendingAt[i] < end : pendingAt[i] <= end))
		{
			final long t = pendingAt[i];
			if (t > pos)
			{
				fl |= stretchFlag(st);
				if (st == State.LOADING)
				{
					load += t - pos;
				}
				pos = t;
			}
			st = pendingCode[i];
			i++;
		}
		if (end > pos || !closing)
		{
			fl |= stretchFlag(st);
			if (st == State.LOADING && end > pos)
			{
				load += end - pos;
			}
		}
		walkFlags = fl;
		walkLoadNanos = load;
		walkState = st;
		walkUsed = i;
	}

	/** Drops the {@code n} oldest pending changes. */
	private void consume(int n)
	{
		if (n <= 0)
		{
			return;
		}
		final int left = pending - n;
		if (left > 0)
		{
			System.arraycopy(pendingAt, n, pendingAt, 0, left);
			System.arraycopy(pendingCode, n, pendingCode, 0, left);
		}
		pending = left;
	}

	/** The flag a stretch of {@code state} puts on every second it overlaps; 0 for a state that has none. */
	private static int stretchFlag(int state)
	{
		switch (state)
		{
			case State.LOADING:
				return Flags.LOADING;
			case State.HOPPING:
				return Flags.HOP;
			case State.CONNECTION_LOST:
				return Flags.DISCONNECT;
			default:
				return 0;
		}
	}

	/** {@link Flags#NOT_LOGGED_IN} for OTHER, LOGIN_SCREEN and LOGGING_IN; 0 for every other code. */
	private static int stateFlag(int state)
	{
		return state == State.OTHER || state == State.LOGIN_SCREEN || state == State.LOGGING_IN
			? Flags.NOT_LOGGED_IN
			: 0;
	}
}
