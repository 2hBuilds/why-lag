package com.whylag.core;

import java.util.Arrays;

/**
 * Finds the lag events in the rings (contract 6.1, 6.2, 3.4, 3.6): lot L3's {@link Detector}. It reads every
 * second once, in order, on the sampler thread. It alone adds to and resets the three usuals of the
 * {@link Session}; it never touches the {@link EventLog} (the engine writes that, from the listener).
 *
 * <p><b>Triggers</b> (contract 6.2), judged in each second as it is read:
 * <ul>
 * <li>DISCONNECT: the DISCONNECT flag. It opens an event always, also inside the wait after a long event.</li>
 * <li>LONG_LOAD: the first second WITHOUT the LOADING flag after a LOADING run (consecutive readable seconds that
 * carry the flag) whose summed {@code loadingMs} is {@link Thresholds#LOAD_LONG_MS} or more. No look-ahead: a run
 * still loading at the newest second has not ended.</li>
 * <li>FRAME_GAP: the worst frame at or over {@code settings.frameGapLimitMs(f)}, f the second's own FOCUSED
 * flag (3.7).</li>
 * <li>GC_PAUSE: a KNOWN pause of {@link Thresholds#GC_PAUSE_MS} or more touches the second, its first to its last ms
 * both included (3.3). An inferred collection has no length and fires nothing.</li>
 * <li>TICK_OFF: a tick that arrived in the second has {@link TickRing#corrected} at or over
 * {@link Thresholds#TICK_OFF_MS} (the frame interval taken off, C5).</li>
 * <li>NO_TICK: the second's state is in-game, it drew a frame, and at its END the newest tick that arrived before
 * that instant (masked or not) is {@link Thresholds#NO_TICK_MS} or more old; with no tick at all before it, nothing
 * fires.</li>
 * <li>RESENT: the second re-sent something, and the {@link Thresholds#RESENT_WINDOW_S} window sent at least the OS
 * minimum with a re-sent share of {@link Thresholds#RESENT_PER_MILLE} or more.</li>
 * <li>RTT_SPIKE: a fresh RTT at or over BOTH {@code usual x RTT_SPIKE_PCT / 100} and
 * {@code usual + RTT_SPIKE_ADD_MS}, this world's usual known, and no click jump in the second: its sent minus the
 * median sent of the last {@link Thresholds#CLICK_BASE_S} unmasked quiet seconds (0 while there are fewer) is under
 * the OS's click size (C21). It opens an event only when {@link Thresholds#RTT_SPIKE_OPENS}.</li>
 * </ul>
 * A second that {@link Masks} masks fires only DISCONNECT and LONG_LOAD (6.1).
 *
 * <p><b>Events.</b> An event opens on the first opening trigger; every later trigger second extends it; it closes
 * once {@link Thresholds#EVENT_QUIET_S} quiet seconds have passed, and its {@code endSec} is its last trigger second.
 * An event that would run past {@link Thresholds#EVENT_MAX_S} is closed with {@code becameCondition}, and no new
 * event opens until {@link Thresholds#EVENT_QUIET_S} quiet seconds have passed, except by DISCONNECT. Events never
 * overlap. The numbers of contract 3.4 are taken over the span when the event is opened, in every second that adds
 * a trigger to it (the object {@link #open()} answers, so it changes only then), and when it closes.
 *
 * <p><b>The usuals.</b> A second feeds them only when it is unmasked, quiet and read while no event was open - so
 * the quiet seconds of an open event, the ones that close it included, feed nothing. {@code rttUsual} and
 * {@code rttSession} take its RTT when it is fresh; {@code fpsUsual} takes its frames. {@code rttUsual} is reset
 * when the world column changes from one second to the next.
 *
 * <p><b>The id</b> (contract 3.4): 0, 1, 2 ... in the order the events open, from this detector's own counter, which
 * nothing restarts; an event keeps it from {@link DetectorListener#opened} through every {@link #open()} to
 * {@link DetectorListener#closed}.
 *
 * <p><b>Falling behind.</b> When the ring drops seconds before this detector has read them (it fell a whole ring
 * behind), it goes on from the ring's tail; an open event may then close, its numbers taken over what the ring still
 * holds. The look either side of a span (sent and re-sent over {@code startSec - RESENT_LOOK_S .. endSec +
 * RESENT_LOOK_S}) takes the seconds the ring holds when the numbers are taken: live, an open event leaves out those
 * not written yet, and its close, {@link Thresholds#EVENT_QUIET_S} seconds later, takes them.
 *
 * <p>Choice: a masked second where DISCONNECT or LONG_LOAD fired is a trigger second; any other masked second is quiet.
 * <p>Choice: EVENT_MAX_S closes an event once a trigger second would make it longer; it ends at the trigger before.
 * <p>Choice: an event that goes quiet at exactly EVENT_MAX_S seconds closes normally, not as a condition.
 * <p>Choice: the quiet unmasked seconds of the wait after a long event feed the usuals and the click base.
 * <p>Choice: of several opening triggers in an event's first second, first is the earliest in Trigger's order.
 * <p>Choice: the click base takes every unmasked quiet second, in an event or not; the judged second is not in it.
 * <p>Choice: RESENT's window is the RESENT_WINDOW_S seconds ending with the second, masked too; share rounded down.
 * <p>Choice: the OS of the counters, for RESENT's minimum and the click jump, is the session's (Session.os).
 * <p>Choice: sent either side of the span is what the ring holds then: live, open() lacks seconds not yet written.
 * <p>Choice: an event's heap limit and memory source come from the settings of the advance that takes its numbers.
 * <p>Choice: the rttUsual reset compares with the last second this detector read; its first second resets nothing.
 * <p>Choice: seconds the ring dropped unread count as quiet, masked seconds; a LOADING run is broken there.
 * <p>Choice: an advance past the ring's head reads to the head, and a later advance reads the rest.
 * <p>Choice: quiet(sec) is true for a second not read, or no longer held (the last SECONDS + 1 read are kept).
 * <p>Choice: a loadingMs of -1 (no data) adds nothing to a LOADING run.
 */
public final class LagDetector implements Detector
{
	private static final Trigger[] TRIGGERS = Trigger.values();
	/** The bits of the triggers that may open an event: every one, less RTT_SPIKE unless RTT_SPIKE_OPENS (6.2). */
	private static final int OPENING = openingBits();
	/** The quiet store keeps {@code sec << TRIGGER_BITS | bits} per slot. */
	private static final int TRIGGER_BITS = TRIGGERS.length;
	private static final long TRIGGER_MASK = (1L << TRIGGER_BITS) - 1;
	private static final int PER_MILLE = 1000;
	private static final int PERCENT = 100;

	/** What each second read fired, for {@link #quiet(long)}: {@code sec << TRIGGER_BITS | bits}; -1 = none. */
	private final long[] fired = new long[Thresholds.SECONDS + 1];

	/** The next second to read. */
	private long next;
	/** The id the next event gets. */
	private long nextId;

	/** The open event as the last trigger second left it; null = none. */
	private volatile LagEvent open;
	private long openId;
	private long openStart;
	private long openEnd;
	private int openTriggers;
	private Trigger openFirst;
	private int openRttBeforeMs;
	/** Quiet seconds since the open event's last trigger second. */
	private int quietRun;

	/** True in the wait after an event closed at EVENT_MAX_S. */
	private boolean waiting;
	/** Quiet seconds in a row inside the wait. */
	private int waitQuiet;

	private boolean inLoadRun;
	private long loadRunMs;

	private boolean worldSeen;
	private int lastWorld;

	/** The sent units of the last CLICK_BASE_S unmasked quiet seconds, as a ring. */
	private final int[] clickBase = new int[Thresholds.CLICK_BASE_S];
	private final int[] clickSorted = new int[Thresholds.CLICK_BASE_S];
	private int clickHeld;
	private int clickNext;

	private int[] frameScratch = new int[Thresholds.EVENT_MAX_S];
	private int[] rttScratch = new int[Thresholds.EVENT_MAX_S];

	public LagDetector()
	{
		Arrays.fill(fired, -1);
	}

	@Override
	public void advance(Session s, long throughSec, SettingsView settings, DetectorListener out)
	{
		final SecondRing ring = s.seconds;
		final long last = Math.min(throughSec, ring.head());
		while (next <= last)
		{
			if (next >= ring.tail() && read(s, next, settings, out))
			{
				next++;
				continue;
			}
			// The ring dropped seconds before this detector read them, or while it read one.
			final long resume = Math.max(ring.tail(), next + 1);
			lost(s, resume - next, settings, out);
			next = resume;
		}
	}

	@Override
	public LagEvent open()
	{
		return open;
	}

	@Override
	public boolean quiet(long sec)
	{
		return triggers(sec) == 0;
	}

	/**
	 * The {@link Trigger} bits that fired in {@code sec}; 0 when none did, or when this detector has not read it or no
	 * longer holds it.
	 */
	int triggers(long sec)
	{
		if (sec < 0)
		{
			return 0;
		}
		final long v = fired[slot(sec)];
		return v >= 0 && v >>> TRIGGER_BITS == sec ? (int) (v & TRIGGER_MASK) : 0;
	}

	/**
	 * Reads second {@code sec}: judges its triggers, moves the event, feeds the usuals. False when the ring overtook
	 * the detector while it read (then nothing was changed).
	 */
	private boolean read(Session s, long sec, SettingsView settings, DetectorListener out)
	{
		final SecondRing ring = s.seconds;
		final int flags = ring.flags(sec);
		final int state = ring.state(sec);
		final int frames = ring.frames(sec);
		final int worstFrameMs = ring.worstFrameMs(sec);
		final int loadingMs = ring.loadingMs(sec);
		final int world = ring.world(sec);
		final int rttMs = ring.rttMs(sec);
		final int rttAgeS = ring.rttAgeS(sec);
		final int sent = ring.sentUnits(sec);
		final int resent = ring.resentUnits(sec);
		final boolean masked = Masks.masked(ring, sec, settings);
		if (!ring.valid(sec))
		{
			return false;
		}

		final boolean worldChanged = worldSeen && world != lastWorld;
		final boolean loading = Flags.has(flags, Flags.LOADING);
		final boolean fresh = fresh(rttMs, rttAgeS);
		int bits = 0;
		if (Flags.has(flags, Flags.DISCONNECT))
		{
			bits |= Trigger.DISCONNECT.bit();
		}
		if (!loading && inLoadRun && loadRunMs >= Thresholds.LOAD_LONG_MS)
		{
			bits |= Trigger.LONG_LOAD.bit();
		}
		if (!masked)
		{
			if (worstFrameMs >= settings.frameGapLimitMs(Flags.has(flags, Flags.FOCUSED)))
			{
				bits |= Trigger.FRAME_GAP.bit();
			}
			final long fromMs = s.msOfSec(sec);
			final long toMs = s.msOfSec(sec + 1);
			if (s.gcs.longestPauseMs(fromMs, toMs - 1) >= Thresholds.GC_PAUSE_MS)
			{
				bits |= Trigger.GC_PAUSE.bit();
			}
			bits |= tickTriggers(s.ticks, fromMs, toMs, State.inGame(state) && frames > 0);
			if (resent > 0 && resentCounts(ring, sec, s.os))
			{
				bits |= Trigger.RESENT.bit();
			}
			// A new world's RTT is judged against the new world's usual, which the reset below empties.
			if (fresh && spike(rttMs, worldChanged ? -1 : s.rttUsual.median(), sent, s.os))
			{
				bits |= Trigger.RTT_SPIKE.bit();
			}
		}

		if (worldChanged)
		{
			s.rttUsual.reset();
		}
		worldSeen = true;
		lastWorld = world;
		if (loading)
		{
			if (!inLoadRun)
			{
				inLoadRun = true;
				loadRunMs = 0;
			}
			loadRunMs += Math.max(0, loadingMs);
		}
		else
		{
			inLoadRun = false;
			loadRunMs = 0;
		}
		fired[slot(sec)] = sec << TRIGGER_BITS | bits;

		final boolean inEvent = open != null;
		step(s, sec, bits, settings, out);
		if (!masked && bits == 0)
		{
			if (!inEvent)
			{
				if (fresh)
				{
					s.rttUsual.add(rttMs);
					s.rttSession.add(rttMs);
				}
				s.fpsUsual.add(frames);
			}
			remember(sent);
		}
		return true;
	}

	/** Moves the event by one second whose trigger bits are {@code bits}. */
	private void step(Session s, long sec, int bits, SettingsView settings, DetectorListener out)
	{
		if (open != null)
		{
			if (bits == 0)
			{
				quietRun++;
				if (quietRun >= Thresholds.EVENT_QUIET_S)
				{
					close(s, settings, out, false);
				}
				return;
			}
			if (sec - openStart + 1 <= Thresholds.EVENT_MAX_S)
			{
				openEnd = sec;
				openTriggers |= bits;
				quietRun = 0;
				open = build(s, settings, true, false);
				return;
			}
			close(s, settings, out, true);
			waiting = true;
			waitQuiet = 0;
			if ((bits & Trigger.DISCONNECT.bit()) != 0)
			{
				begin(s, sec, bits, Trigger.DISCONNECT, settings, out);
			}
			return;
		}
		if (waiting)
		{
			if (bits == 0)
			{
				waitQuiet++;
				if (waitQuiet >= Thresholds.EVENT_QUIET_S)
				{
					waiting = false;
				}
			}
			else
			{
				waitQuiet = 0;
				if ((bits & Trigger.DISCONNECT.bit()) != 0)
				{
					begin(s, sec, bits, Trigger.DISCONNECT, settings, out);
				}
			}
			return;
		}
		final int opening = bits & OPENING;
		if (opening != 0)
		{
			begin(s, sec, bits, TRIGGERS[Integer.numberOfTrailingZeros(opening)], settings, out);
		}
	}

	/** Opens an event in {@code sec}, numbered by the next id, and tells {@code out}. */
	private void begin(Session s, long sec, int bits, Trigger first, SettingsView settings, DetectorListener out)
	{
		openId = nextId++;
		openStart = sec;
		openEnd = sec;
		openTriggers = bits;
		openFirst = first;
		openRttBeforeMs = s.rttUsual.median();
		quietRun = 0;
		waiting = false;
		waitQuiet = 0;
		final LagEvent e = build(s, settings, true, false);
		open = e;
		out.opened(e);
	}

	/** Closes the open event with its numbers taken now, and tells {@code out}. */
	private void close(Session s, SettingsView settings, DetectorListener out, boolean becameCondition)
	{
		final LagEvent e = build(s, settings, false, becameCondition);
		open = null;
		out.closed(e);
	}

	/** Seconds the ring dropped unread: counted as quiet, masked seconds (see the class notes). */
	private void lost(Session s, long count, SettingsView settings, DetectorListener out)
	{
		inLoadRun = false;
		loadRunMs = 0;
		if (open != null)
		{
			quietRun = (int) Math.min(Integer.MAX_VALUE, quietRun + count);
			if (quietRun >= Thresholds.EVENT_QUIET_S)
			{
				close(s, settings, out, false);
			}
		}
		else if (waiting)
		{
			waitQuiet = (int) Math.min(Integer.MAX_VALUE, waitQuiet + count);
			if (waitQuiet >= Thresholds.EVENT_QUIET_S)
			{
				waiting = false;
			}
		}
	}

	/**
	 * The open event with every number of contract 3.4 taken over {@code openStart .. openEnd} from the rings as they
	 * stand. Every -1 is "no data"; {@code gcPauseMs} is 0 when measured and none, -1 when it cannot be known.
	 */
	private LagEvent build(Session s, SettingsView settings, boolean isOpen, boolean becameCondition)
	{
		final SecondRing ring = s.seconds;
		final long start = openStart;
		final long end = openEnd;

		int world = -1;
		int region = -1;
		int players = -1;
		int npcs = -1;
		final int firstWorld = ring.world(start);
		final int firstRegion = ring.region(start);
		final int firstPlayers = ring.players(start);
		final int firstNpcs = ring.npcs(start);
		if (ring.valid(start))
		{
			world = firstWorld;
			region = firstRegion;
			players = firstPlayers;
			npcs = firstNpcs;
		}

		final int span = (int) (end - start + 1);
		if (frameScratch.length < span)
		{
			frameScratch = new int[span];
			rttScratch = new int[span];
		}
		int framesHeld = 0;
		int rttsHeld = 0;
		int worstFrameMs = -1;
		int rttMaxMs = -1;
		int heapUsedMb = -1;
		int sysCpuPct = -1;
		int gameBusyPct = -1;
		for (long k = start; k <= end; k++)
		{
			final int frames = ring.frames(k);
			final int worst = ring.worstFrameMs(k);
			final int rtt = ring.rttMs(k);
			final int age = ring.rttAgeS(k);
			final int heap = ring.heapUsedMb(k);
			final int sys = ring.sysCpuPct(k);
			final int busy = ring.busyPm(k);
			if (!ring.valid(k))
			{
				continue;
			}
			frameScratch[framesHeld++] = frames;
			worstFrameMs = Math.max(worstFrameMs, worst);
			if (fresh(rtt, age))
			{
				rttScratch[rttsHeld++] = rtt;
				rttMaxMs = Math.max(rttMaxMs, rtt);
			}
			heapUsedMb = Math.max(heapUsedMb, heap);
			sysCpuPct = Math.max(sysCpuPct, sys);
			gameBusyPct = Math.max(gameBusyPct, Fmt.busyPct(busy));
		}
		final int fps = median(frameScratch, framesHeld);
		final int rttMs = median(rttScratch, rttsHeld);

		// The span's ticks: arrived in [start, end + 1) and not LOGIN_MASK (3.5, 6.3); every gap counted.
		final TickRing ticks = s.ticks;
		final long fromMs = s.msOfSec(start);
		final long toMs = s.msOfSec(end + 1);
		long gapSum = 0;
		int tickCount = 0;
		int worstTickGapMs = -1;
		int worstCorrectedTickMs = -1;
		final long head = ticks.head();
		for (long q = firstTickAtOrAfter(ticks, fromMs); q <= head; q++)
		{
			final long atMs = ticks.atMs(q);
			final int gapMs = ticks.gapMs(q);
			final int corrected = ticks.corrected(q);
			final int tickFlags = ticks.flags(q);
			if (!ticks.valid(q))
			{
				continue;
			}
			if (atMs >= toMs)
			{
				break;
			}
			if (Flags.has(tickFlags, Flags.LOGIN_MASK))
			{
				continue;
			}
			gapSum += gapMs;
			tickCount++;
			worstTickGapMs = Math.max(worstTickGapMs, gapMs);
			worstCorrectedTickMs = Math.max(worstCorrectedTickMs, corrected);
		}
		final int meanTickGapMs = tickCount == 0 ? -1 : (int) Math.min(Integer.MAX_VALUE, gapSum / tickCount);

		long sentSum = 0;
		long resentSum = 0;
		for (long k = Math.max(0, start - Thresholds.RESENT_LOOK_S); k <= end + Thresholds.RESENT_LOOK_S; k++)
		{
			final int sent = ring.sentUnits(k);
			final int resent = ring.resentUnits(k);
			if (ring.valid(k))
			{
				sentSum += sent;
				resentSum += resent;
			}
		}

		final int gcPauseMs = settings.memorySource == MemorySource.MANAGEMENT
			? s.gcs.longestPauseMs(fromMs, toMs - 1) : -1;
		final int heapMaxMb = settings.heapMaxMb > 0 ? settings.heapMaxMb : -1;

		return new LagEvent(openId, start, end, s.wallMsOf(start), openTriggers, openFirst, world, region, players,
			npcs, fps, worstFrameMs, meanTickGapMs, worstTickGapMs, worstCorrectedTickMs, rttMs, rttMaxMs,
			openRttBeforeMs, toInt(sentSum), toInt(resentSum), gcPauseMs, heapUsedMb, heapMaxMb, sysCpuPct,
			gameBusyPct, isOpen, becameCondition, null);
	}

	/** RTT_SPIKE's test on a fresh RTT against this world's usual (-1 = none) and the click base (6.2, C21). */
	private boolean spike(int rttMs, int usualMs, int sent, Os os)
	{
		if (usualMs < 0)
		{
			return false;
		}
		if (rttMs < usualMs * Thresholds.RTT_SPIKE_PCT / PERCENT || rttMs < usualMs + Thresholds.RTT_SPIKE_ADD_MS)
		{
			return false;
		}
		return (long) sent - clickBase() < os.clickSent();
	}

	/** The median sent of the last CLICK_BASE_S unmasked quiet seconds; 0 while there are fewer. */
	private int clickBase()
	{
		if (clickHeld < clickBase.length)
		{
			return 0;
		}
		System.arraycopy(clickBase, 0, clickSorted, 0, clickHeld);
		return median(clickSorted, clickHeld);
	}

	/** One more unmasked quiet second's sent units for the click base. */
	private void remember(int sent)
	{
		clickBase[clickNext] = sent;
		clickNext = (clickNext + 1) % clickBase.length;
		if (clickHeld < clickBase.length)
		{
			clickHeld++;
		}
	}

	private int slot(long sec)
	{
		return (int) (sec % fired.length);
	}

	/** TICK_OFF and NO_TICK for the second that runs over {@code [fromMs, toMs)} (6.2). */
	private static int tickTriggers(TickRing ticks, long fromMs, long toMs, boolean noTickMayFire)
	{
		int bits = 0;
		final long firstIn = firstTickAtOrAfter(ticks, fromMs);
		final long firstAfter = firstTickAtOrAfter(ticks, toMs);
		for (long q = firstIn; q < firstAfter; q++)
		{
			final int corrected = ticks.corrected(q);
			if (ticks.valid(q) && corrected >= Thresholds.TICK_OFF_MS)
			{
				bits |= Trigger.TICK_OFF.bit();
				break;
			}
		}
		if (noTickMayFire)
		{
			// The newest tick that arrived before the second's end, masked or not.
			final long newest = firstAfter - 1;
			if (newest >= 0)
			{
				final long atMs = ticks.atMs(newest);
				if (ticks.valid(newest) && toMs - atMs >= Thresholds.NO_TICK_MS)
				{
					bits |= Trigger.NO_TICK.bit();
				}
			}
		}
		return bits;
	}

	/**
	 * The RESENT window's test for second {@code sec}: the RESENT_WINDOW_S seconds that end with it sent at least the
	 * OS minimum, and re-sent RESENT_PER_MILLE of it or more.
	 */
	private static boolean resentCounts(SecondRing ring, long sec, Os os)
	{
		long sent = 0;
		long resent = 0;
		for (long k = Math.max(0, sec - Thresholds.RESENT_WINDOW_S + 1); k <= sec; k++)
		{
			final int sentK = ring.sentUnits(k);
			final int resentK = ring.resentUnits(k);
			if (ring.valid(k))
			{
				sent += sentK;
				resent += resentK;
			}
		}
		return sent > 0 && sent >= os.resentMinSent() && resent * PER_MILLE / sent >= Thresholds.RESENT_PER_MILLE;
	}

	/**
	 * The sequence of the first readable tick that arrived at or after session ms {@code ms}; {@code head + 1} when
	 * none did. Ticks arrive in time order, so a binary search finds it; a slot overwritten meanwhile is older than
	 * everything the ring still holds.
	 */
	private static long firstTickAtOrAfter(TickRing ticks, long ms)
	{
		final long head = ticks.head();
		long lo = Math.min(ticks.tail(), head + 1);
		long hi = head + 1;
		while (lo < hi)
		{
			final long mid = (lo + hi) >>> 1;
			final long atMs = ticks.atMs(mid);
			if (!ticks.valid(mid) || atMs < ms)
			{
				lo = mid + 1;
			}
			else
			{
				hi = mid;
			}
		}
		return lo;
	}

	/** A fresh RTT (contract 3.3): {@code rttMs >= 0} and {@code 0 <= rttAgeS <= RTT_STALE_S}. */
	private static boolean fresh(int rttMs, int rttAgeS)
	{
		return rttMs >= 0 && rttAgeS >= 0 && rttAgeS <= Thresholds.RTT_STALE_S;
	}

	/** The median of the first {@code count} values, the LOWER middle one of an even count; -1 when there are none. */
	private static int median(int[] values, int count)
	{
		if (count == 0)
		{
			return -1;
		}
		Arrays.sort(values, 0, count);
		return values[(count - 1) / 2];
	}

	private static int toInt(long v)
	{
		return (int) Math.min(Integer.MAX_VALUE, v);
	}

	private static int openingBits()
	{
		int bits = 0;
		for (Trigger t : Trigger.values())
		{
			if (t != Trigger.RTT_SPIKE || Thresholds.RTT_SPIKE_OPENS)
			{
				bits |= t.bit();
			}
		}
		return bits;
	}
}
