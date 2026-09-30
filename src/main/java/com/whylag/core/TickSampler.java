package com.whylag.core;

/**
 * The client thread's per-tick record (contract 3.10 and section 7, L2): one {@link TickRow} per game tick, written
 * into {@link TickRing}.
 *
 * <p><b>The tick's frame time</b> is {@code max(frames.worstFrameSinceTickMs(), frames.openFrameMs(nanos))}: after
 * a stall the client posts the tick BEFORE it draws, so the frame that holds the stall is still open when the late
 * tick arrives.
 *
 * <p><b>The session's first tick</b> has no tick before it: its gap is {@link Thresholds#TICK_MS} and its cycle
 * jump -1, so it can fire nothing. Every later gap is the difference to the tick before, held in
 * 0 .. {@code Integer.MAX_VALUE}.
 *
 * <p><b>The login mask.</b> The count of {@link Thresholds#LOGIN_MASK_TICKS} masked ticks (re)starts whenever
 * LOGGING_IN, HOPPING or CONNECTION_LOST ENDS: at the first code handed to {@link #state} after one of them that is
 * a different code. Each masked tick carries {@link Flags#LOGIN_MASK} on its own row, and the second it arrives in
 * gets the flag too.
 *
 * <p><b>Tick flags.</b> A row carries the flags of the second it arrives in, as they stand when it arrives, and
 * nothing more, as {@code Trace} writes a tick's row from its second's flags: LOADING time in the tick's gap that
 * its own second does not hold does not reach the row.
 *
 * <p>Client thread only. {@link #tick} allocates nothing and takes no lock (T1).
 *
 * <p>
 * Choice: a tick that arrives in a second which a masked tick has already marked carries
 * {@link Flags#LOGIN_MASK} by its second's flags, though it is not one of the fifteen (as {@code Trace} writes it).
 * <br>
 * Choice: a cycle jump is the difference of the two game cycles, held in -1 .. 32767 by the ring; a game cycle
 * that fell (a new login) reads -1, no data.
 */
public final class TickSampler
{
	private final Session session;
	private final FrameSampler frames;
	private final SelfTimer timer;
	/** The one carrier, reused for every tick. */
	private final TickRow row = new TickRow();

	private boolean first = true;
	private long lastAtMs;
	private int lastCycle;
	private int lastState = State.OTHER;
	private int maskLeft;

	public TickSampler(Session s, FrameSampler frames, SelfTimer timer)
	{
		this.session = s;
		this.frames = frames;
		this.timer = timer;
	}

	/**
	 * One game tick, stamped {@code nanos}, with the game cycle and the latest RTT in ms (-1 = none). Client
	 * thread; allocates nothing.
	 */
	public void tick(long nanos, int gameCycle, int latestRttMs)
	{
		final boolean timed = timer != null && timer.on();
		final long t0 = timed ? System.nanoTime() : 0L;

		final long atMs = session.msOf(nanos);
		final TickRow r = row;
		r.atMs = atMs;
		if (first)
		{
			r.gapMs = Thresholds.TICK_MS;
			r.cycleJump = -1;
		}
		else
		{
			r.gapMs = (int) Math.max(0L, Math.min(Integer.MAX_VALUE, atMs - lastAtMs));
			r.cycleJump = (int) Math.max(-1L, Math.min(Short.MAX_VALUE, (long) gameCycle - lastCycle));
		}
		r.frameMs = Math.max(frames.worstFrameSinceTickMs(), frames.openFrameMs(nanos));
		r.rttMs = latestRttMs;

		final boolean masked = maskLeft > 0;
		if (masked)
		{
			maskLeft--;
			frames.maskSecond(session.secOf(nanos));
		}
		int fl = frames.flagsAt(nanos);
		if (masked)
		{
			fl |= Flags.LOGIN_MASK;
		}
		r.flags = fl;
		session.ticks.put(r);

		frames.resetWorstSinceTick();
		first = false;
		lastAtMs = atMs;
		lastCycle = gameCycle;

		if (timed)
		{
			timer.add(SelfTimer.TICK, System.nanoTime() - t0);
		}
	}

	/**
	 * The game state changed to {@code stateCode} ({@link State}). When LOGGING_IN, HOPPING or CONNECTION_LOST ends
	 * here, the next {@link Thresholds#LOGIN_MASK_TICKS} ticks are masked. Client thread.
	 */
	public void state(long nanos, int stateCode)
	{
		if (stateCode == lastState)
		{
			return;
		}
		if (lastState == State.LOGGING_IN || lastState == State.HOPPING || lastState == State.CONNECTION_LOST)
		{
			maskLeft = Thresholds.LOGIN_MASK_TICKS;
		}
		lastState = stateCode;
	}
}
