package com.whylag.core;

import java.util.Arrays;

/**
 * Reads the rings into an {@link Evidence} (contract 6.3): over an event's span, {@code startSec .. endSec}, or
 * over the window, the last {@link Thresholds#WINDOW_S} seconds up to the newest complete second, for a condition.
 * Pure: it writes nothing, and the same rings give the same evidence. Sampler thread.
 *
 * <p>From the {@link LagEvent} it judges, {@link #forEvent} reads the span, the trigger bits ({@code RESENT} is
 * {@code resentCounts}, {@code RTT_SPIKE} the spike), {@code first}, {@code rttBeforeMs} (the usual) and, only when
 * the span's first second can no longer be read, {@code world}. It never reads {@code open}: an open event is judged
 * as if it closed now. {@link #forCondition} reads, of each event in the log, its span and its {@code open} flag, for
 * W1c's need "no event open or closed in the window": the log keeps an OPEN event as {@code opened} handed it in
 * until {@code closed} replaces it (contract 3.4, 3.6), so its logged {@code endSec} is its first trigger second and
 * does not move while it stays open; an open event is taken as reaching the newest second.
 *
 * <p>An EVENT reads every second of its span, masked or not. A CONDITION leaves out the seconds that
 * {@link Flags#masked(int)} names for EVERY field, its {@code disconnect} included (contract 6.3, "Which seconds a
 * condition reads"), and its ticks are those without {@code LOGIN_MASK}. Every read of a ring is re-checked with
 * {@code valid}; a second or a tick that is no longer readable counts for nothing.
 *
 * <p>Choice: every median is taken by sorting a copy, the LOWER middle of an even count, as {@code Usual.median()}.
 * <p>Choice: a {@code frames} column of -1 counts as 0 frames; a tick's {@code frameMs} of -1 as 0 ms.
 * <p>Choice: {@code tickMedianMs} (gap less frame time) is held at 0 or more, and {@code tickOffMs}, when either of
 * its two sources exists, is held at 0 or more.
 * <p>Choice: with no readable second in the span or the window, every field keeps its "no data" start value
 * ({@code heapMaxMb} and the cap fields included), so no rule scores.
 * <p>Choice: {@code beforeResentPm} is -1 when there is no last tick or none of the look seconds is readable.
 * <p>Choice: the memory join is computed for a condition as for an event (over the window's worst frame); no
 * condition rule reads it.
 */
public final class EvidenceBuilder
{
	private static final int MS_PER_SECOND = 1000;
	private static final int PER_MILLE = 1000;
	private static final int PERCENT = 100;

	private EvidenceBuilder()
	{
	}

	/** The evidence of one event, open or closed, over its span. */
	public static Evidence forEvent(Session s, LagEvent e, SettingsView settings)
	{
		final Evidence v = new Evidence();
		v.event = true;
		v.first = e.first;
		v.durationS = e.lengthS();
		v.rttUsual = e.rttBeforeMs < 0 ? -1 : e.rttBeforeMs;
		v.resentCounts = e.has(Trigger.RESENT);
		v.noTick = e.has(Trigger.NO_TICK);
		v.world = worldOf(s.seconds, e.startSec, e.world);

		seconds(v, s, settings, e.startSec, e.endSec, false);
		v.rttSpike = !v.rttKnown || v.rttUsual < 0 ? Evidence.NO_DATA
			: e.has(Trigger.RTT_SPIKE) ? Evidence.YES : Evidence.NO;
		ticks(v, s.ticks, s.msOfSec(e.startSec), s.msOfSec(e.endSec + 1), false);
		loads(v, s.seconds, e.startSec, e.endSec);
		resends(v, s.seconds, e.startSec - Thresholds.RESENT_LOOK_S, e.endSec + Thresholds.RESENT_LOOK_S);
		beforeTheDisconnect(v, s);
		return v;
	}

	/** The evidence of the window that ends with the newest complete second, {@code s.lastSec(nowSec)}. */
	public static Evidence forCondition(Session s, long nowSec, SettingsView settings)
	{
		final Evidence v = new Evidence();
		final long last = s.lastSec(nowSec);
		if (last < 0)
		{
			return v;
		}
		final long first = Math.max(last - Thresholds.WINDOW_S + 1, s.seconds.tail());
		v.world = worldOf(s.seconds, last, 0);
		v.rttUsual = s.rttUsual.median();

		seconds(v, s, settings, first, last, true);
		v.rttSpike = !v.rttKnown || v.rttUsual < 0 ? Evidence.NO_DATA : spikeLine(v.rttMax, v.rttUsual);
		ticks(v, s.ticks, s.msOfSec(last - Thresholds.WINDOW_S + 1), s.msOfSec(last + 1), true);
		runs(v, s.seconds, settings, first, last);
		v.eventInWindow = eventIn(s.events, first, last);
		return v;
	}

	/** The spike line of contract 6.2 on one RTT against a usual, both known. */
	static int spikeLine(int rttMs, int usualMs)
	{
		return rttMs >= (long) usualMs * Thresholds.RTT_SPIKE_PCT / PERCENT
			&& rttMs >= usualMs + Thresholds.RTT_SPIKE_ADD_MS ? Evidence.YES : Evidence.NO;
	}

	/** The median of the first {@code n} values: of an even count the LOWER middle one; -1 when n is 0. */
	static int median(int[] values, int n)
	{
		if (n <= 0)
		{
			return -1;
		}
		final int[] sorted = Arrays.copyOf(values, n);
		Arrays.sort(sorted);
		return sorted[(n - 1) / 2];
	}

	// ------------------------------------------------------------------ the seconds

	private static void seconds(Evidence v, Session s, SettingsView settings, long from, long to,
		boolean condition)
	{
		final SecondRing r = s.seconds;
		final long lo = Math.max(from, r.tail());
		final long hi = Math.min(to, r.head());
		final int room = hi >= lo ? (int) (hi - lo + 1) : 0;
		final int[] rates = new int[room];
		final int[] rtts = new int[room];
		int nRates = 0;
		int nRtts = 0;
		long worstSec = -1;
		int worstMs = Integer.MIN_VALUE;
		int worstEndMs = 0;
		int worstBusyPm = -1;
		boolean worstFocused = false;
		int heap = -1;
		for (long sec = lo; sec <= hi; sec++)
		{
			final int flags = r.flags(sec);
			final int frames = r.frames(sec);
			final int worst = r.worstFrameMs(sec);
			final int worstEnd = r.worstFrameEndMs(sec);
			final int busy = r.worstBusyPm(sec);
			final int used = r.heapUsedMb(sec);
			final int rtt = r.rttMs(sec);
			final int age = r.rttAgeS(sec);
			if (!r.valid(sec))
			{
				continue;
			}
			// A condition leaves a masked second out of every field, its disconnect included (6.3).
			if (condition && Flags.masked(flags))
			{
				continue;
			}
			if (Flags.has(flags, Flags.DISCONNECT))
			{
				v.disconnect = true;
				if (v.disconnectSec < 0)
				{
					v.disconnectSec = sec;
				}
			}
			rates[nRates++] = Math.max(0, frames);
			// Strictly larger: of several seconds with the same worst frame, the EARLIEST stays.
			if (worst > worstMs)
			{
				worstMs = worst;
				worstSec = sec;
				worstEndMs = worstEnd;
				worstBusyPm = busy;
				worstFocused = Flags.has(flags, Flags.FOCUSED);
			}
			heap = Math.max(heap, used);
			if (fresh(rtt, age))
			{
				rtts[nRtts++] = rtt;
			}
		}
		if (nRates == 0)
		{
			return;
		}

		v.frameGapMs = Math.max(0, worstMs);
		v.busyPm = worstBusyPm < 0 ? -1 : worstBusyPm;
		v.framesClean = v.frameGapMs < Thresholds.FRAME_CLEAN_MS;
		v.fps = median(rates, nRates);
		v.frameMedianMs = v.fps == 0 ? MS_PER_SECOND : (MS_PER_SECOND + v.fps - 1) / v.fps;
		v.heapUsedMb = heap < 0 ? -1 : heap;
		v.heapMaxMb = settings.heapMaxMb <= 0 ? -1 : settings.heapMaxMb;

		// The focus of the ONE second judged (contract 3.7): an event's worst frame's, a condition's newest.
		final boolean focused = condition ? Flags.has(r.flags(to), Flags.FOCUSED) : worstFocused;
		final CapSource source = settings.capSource(focused);
		v.frameLimitMs = settings.frameGapLimitMs(focused);
		v.capFps = settings.capFps(focused);
		v.capIntervalMs = settings.capIntervalMs(focused);
		v.capSelfSet = source.selfSet();
		v.capWaits = source.waits();
		v.capLabel = source.label();

		if (nRtts > 0)
		{
			v.rttKnown = true;
			v.rtt = median(rtts, nRtts);
			int min = Integer.MAX_VALUE;
			int max = -1;
			for (int i = 0; i < nRtts; i++)
			{
				min = Math.min(min, rtts[i]);
				max = Math.max(max, rtts[i]);
			}
			v.rttMin = min;
			v.rttMax = max;
		}

		// The memory join, by the worst frame's own interval, both ends included (C7).
		final long end = s.msOfSec(worstSec) + worstEndMs;
		final long start = end - v.frameGapMs;
		v.gcInferred = s.gcs.inferredIn(start, end);
		if (settings.memorySource == MemorySource.MANAGEMENT)
		{
			v.gcMs = s.gcs.longestPauseMs(start, end);
			v.gcOverlapMs = s.gcs.overlapMs(start, end);
			v.gcCoverPct = v.frameGapMs == 0 ? 0
				: (int) Math.min(PERCENT, (long) v.gcOverlapMs * PERCENT / v.frameGapMs);
		}
	}

	private static boolean fresh(int rttMs, int ageS)
	{
		return rttMs >= 0 && ageS >= 0 && ageS <= Thresholds.RTT_STALE_S;
	}

	private static int worldOf(SecondRing r, long sec, int otherwise)
	{
		if (!r.valid(sec))
		{
			return otherwise;
		}
		final int world = r.world(sec);
		return r.valid(sec) && world >= 0 ? world : otherwise;
	}

	// ------------------------------------------------------------------ the runs (conditions F1 and F2)

	private static void runs(Evidence v, SecondRing r, SettingsView settings, long first, long last)
	{
		if (last < first)
		{
			return;
		}
		final int[] rates = new int[(int) (last - first + 1)];
		int n = 0;
		boolean capped = false;
		for (long sec = last; sec >= first; sec--)
		{
			final int flags = r.flags(sec);
			final int frames = Math.max(0, r.frames(sec));
			if (!r.valid(sec))
			{
				break;
			}
			if (Flags.masked(flags) || Flags.has(flags, Flags.NO_FRAMES))
			{
				continue;
			}
			if (frames >= Thresholds.FPS_WARN)
			{
				break;
			}
			final boolean focused = Flags.has(flags, Flags.FOCUSED);
			final boolean isCapped = settings.capSource(focused).selfSet()
				&& Math.abs(frames - settings.capFps(focused)) <= Thresholds.CAP_MATCH_FPS;
			if (n == 0)
			{
				capped = isCapped;
			}
			else if (isCapped != capped)
			{
				break;
			}
			rates[n++] = frames;
		}
		if (n == 0)
		{
			return;
		}
		if (capped)
		{
			v.capHeldS = n;
		}
		else
		{
			v.lowFpsS = n;
			v.lowFps = median(rates, n);
		}
	}

	// ------------------------------------------------------------------ the ticks

	private static void ticks(Evidence v, TickRing t, long fromMs, long toMs, boolean window)
	{
		final long head = t.head();
		int room = 0;
		long newestBefore = 0;
		boolean any = false;
		for (long q = head; q >= 0; q--)
		{
			final long at = t.atMs(q);
			final int flags = t.flags(q);
			if (!t.valid(q))
			{
				break;
			}
			if (at >= toMs)
			{
				continue;
			}
			if (!any)
			{
				// The newest tick before the span's end, masked or not.
				any = true;
				newestBefore = at;
			}
			if (at < fromMs)
			{
				break;
			}
			if (!Flags.has(flags, Flags.LOGIN_MASK))
			{
				room++;
			}
		}

		final int[] values = new int[room];
		int n = 0;
		int late = 0;
		int early = 0;
		int worst = -1;
		int off = -1;
		for (long q = head; q >= 0 && n < room; q--)
		{
			final long at = t.atMs(q);
			final int flags = t.flags(q);
			final int gap = t.gapMs(q);
			final int frame = Math.max(0, t.frameMs(q));
			final int corrected = t.corrected(q);
			final boolean isLate = t.late(q);
			final boolean isEarly = t.early(q);
			if (!t.valid(q))
			{
				break;
			}
			if (at >= toMs)
			{
				continue;
			}
			if (at < fromMs)
			{
				break;
			}
			if (Flags.has(flags, Flags.LOGIN_MASK))
			{
				continue;
			}
			values[n++] = window ? gap : Math.max(0, gap - frame);
			worst = Math.max(worst, gap);
			off = Math.max(off, corrected);
			if (corrected >= Thresholds.TICK_OFF_MS)
			{
				if (isLate)
				{
					late++;
				}
				else if (isEarly)
				{
					early++;
				}
			}
		}

		if (window)
		{
			v.ticksInWindow = n;
			v.tickWindowMedianMs = median(values, n);
			return;
		}
		v.ticks = n;
		v.tickLate = late;
		v.tickEarly = early;
		v.tickMedianMs = median(values, n);
		v.tickWorstMs = worst;
		v.noTickMs = any ? (int) Math.min(Integer.MAX_VALUE, Math.max(worst, toMs - newestBefore)) : -1;
		if (off >= 0 || v.noTickMs >= 0)
		{
			final int missing = v.noTickMs >= 0 ? v.noTickMs - Thresholds.TICK_MS : -1;
			v.tickOffMs = Math.max(0, Math.max(off, missing));
		}
	}

	// ------------------------------------------------------------------ loads, re-sends, D1's look

	private static boolean loading(SecondRing r, long sec)
	{
		if (!r.valid(sec))
		{
			return false;
		}
		final int flags = r.flags(sec);
		return r.valid(sec) && Flags.has(flags, Flags.LOADING);
	}

	private static void loads(Evidence v, SecondRing r, long startSec, long endSec)
	{
		// Every LOADING run with a second in startSec - 1 .. endSec, each run summed WHOLE.
		long sum = 0;
		long sec = startSec - 1;
		while (sec <= endSec)
		{
			if (!loading(r, sec))
			{
				sec++;
				continue;
			}
			long runStart = sec;
			while (loading(r, runStart - 1))
			{
				runStart--;
			}
			long runEnd = sec;
			while (loading(r, runEnd + 1))
			{
				runEnd++;
			}
			for (long k = runStart; k <= runEnd; k++)
			{
				final int ms = r.loadingMs(k);
				if (r.valid(k) && ms > 0)
				{
					sum += ms;
				}
			}
			sec = runEnd + 1;
		}
		v.loadMs = (int) Math.min(Integer.MAX_VALUE, sum);

		// The runs of the last LOADS_LOOK_S seconds, counted back from the event's END.
		int runs = 0;
		boolean inRun = false;
		final long lo = Math.max(endSec - Thresholds.LOADS_LOOK_S + 1, r.tail());
		final long hi = Math.min(endSec, r.head());
		for (long k = lo; k <= hi; k++)
		{
			final boolean is = loading(r, k);
			if (is && !inRun)
			{
				runs++;
			}
			inRun = is;
		}
		v.loads = runs;
	}

	private static void resends(Evidence v, SecondRing r, long from, long to)
	{
		final long[] sums = new long[3];
		sum(r, from, to, sums);
		v.sent = (int) Math.min(Integer.MAX_VALUE, sums[0]);
		v.resent = (int) Math.min(Integer.MAX_VALUE, sums[1]);
		v.resentPm = share(sums[0], sums[1]);
	}

	/** {sent, re-sent, readable seconds} over a span of seconds. */
	private static void sum(SecondRing r, long from, long to, long[] out)
	{
		final long lo = Math.max(from, r.tail());
		final long hi = Math.min(to, r.head());
		for (long sec = lo; sec <= hi; sec++)
		{
			final int sent = r.sentUnits(sec);
			final int resent = r.resentUnits(sec);
			if (!r.valid(sec))
			{
				continue;
			}
			out[0] += Math.max(0, sent);
			out[1] += Math.max(0, resent);
			out[2]++;
		}
	}

	/** {@code resent x 1000 / sent}, rounded down, at most 1000, 0 when nothing was sent. */
	private static int share(long sent, long resent)
	{
		if (sent <= 0)
		{
			return 0;
		}
		return (int) Math.min(PER_MILLE, resent * PER_MILLE / sent);
	}

	private static void beforeTheDisconnect(Evidence v, Session s)
	{
		if (v.disconnectSec < 0)
		{
			return;
		}
		final TickRing t = s.ticks;
		final long instant = s.msOfSec(v.disconnectSec + 1);
		for (long q = t.head(); q >= 0; q--)
		{
			final long at = t.atMs(q);
			if (!t.valid(q))
			{
				break;
			}
			if (at < instant)
			{
				v.lastTickSec = Math.floorDiv(at, (long) MS_PER_SECOND);
				break;
			}
		}
		if (v.lastTickSec < 0)
		{
			v.lastTickSec = -1;
			return;
		}

		final SecondRing r = s.seconds;
		final long lo = Math.max(v.lastTickSec - Thresholds.D1_LOOK_S + 1, r.tail());
		final long hi = Math.min(v.lastTickSec, r.head());
		final int[] rtts = new int[hi >= lo ? (int) (hi - lo + 1) : 0];
		int n = 0;
		for (long sec = lo; sec <= hi; sec++)
		{
			final int rtt = r.rttMs(sec);
			final int age = r.rttAgeS(sec);
			if (r.valid(sec) && fresh(rtt, age))
			{
				rtts[n++] = rtt;
				v.beforeRttMax = Math.max(v.beforeRttMax, rtt);
			}
		}
		v.beforeRtt = median(rtts, n);
		v.beforeRttSpike = n == 0 || v.rttUsual < 0 ? Evidence.NO_DATA : spikeLine(v.beforeRttMax, v.rttUsual);
		final long[] sums = new long[3];
		sum(r, lo, hi, sums);
		v.beforeResentPm = sums[2] == 0 ? -1 : share(sums[0], sums[1]);
	}

	// ------------------------------------------------------------------ the window's events

	/**
	 * True when an event of the log, open or closed, overlaps {@code first .. last}. An OPEN event is still running:
	 * the log holds it as {@code opened} handed it in, with {@code endSec} at its first trigger second, so it is taken
	 * as reaching {@code last}, the newest second.
	 */
	private static boolean eventIn(EventLog log, long first, long last)
	{
		for (int i = log.size() - 1; i >= 0; i--)
		{
			// The sampler thread is the log's one writer, and this runs on it: the size cannot shrink meanwhile.
			final LagEvent e = log.get(i);
			final long end = e.open ? last : e.endSec;
			if (end < first)
			{
				// Events never overlap and are held in the order they opened: the older ones ended earlier still.
				return false;
			}
			if (e.startSec <= last)
			{
				return true;
			}
		}
		return false;
	}
}
