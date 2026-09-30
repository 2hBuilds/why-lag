package com.whylag.core;

/**
 * The rules table of contract 6.3 as data: {@link #ALL} holds one {@link Rule} per row, in rank order. The rank,
 * base and ceiling of each row live here and nowhere else ({@code RulesTableTest} pins them by literal); every line
 * a predicate compares against is a {@link Thresholds} constant.
 *
 * <p>Each need, exclude and support is one small named predicate on {@link Evidence}. A predicate answers
 * {@link Evidence#NO_DATA} when the signal it reads has none, and {@link Rule} does the rest.
 *
 * <p>Choice: N3's support "{@code tickLate >= 1} or {@code noTickMs >= NO_TICK_MS}" is never "no data": both halves
 * are counts of what arrived, and a span with no tick at all answers NO.
 * <p>Choice: N6 ("No ticks") needs the NO_TICK trigger itself (changes after the first live look, 2026-09-29): an
 * event that only TICK_OFF opened - a tick late, not a stop - never becomes N6.
 * <p>Choice: among conditions a tie goes to the lower rank; among event rules the engine, not this class, applies
 * the margin ({@link VerdictEngine}).
 */
public final class Rules
{
	private static final int YES = Evidence.YES, NO = Evidence.NO, NO_DATA = Evidence.NO_DATA;

	// ------------------------------------------------------------------ the named predicates

	static final Rule.Signal DISCONNECT = e -> is(e.disconnect);
	static final Rule.Signal FRAME_GAP_IS_A_PAUSE = e -> is(e.frameGapMs >= Thresholds.GC_PAUSE_MS);
	static final Rule.Signal PAUSE_IS_LONG = e -> is(e.gcMs >= Thresholds.GC_PAUSE_MS);
	static final Rule.Signal PAUSE_COVERS_THE_FRAME = e -> e.gcCoverPct < 0 ? NO_DATA
		: is(e.gcCoverPct >= Thresholds.GC_COVER_PCT);
	static final Rule.Signal LOADED = e -> is(e.loadMs > 0);
	static final Rule.Signal LOAD_IS_LONG = e -> is(e.loadMs >= Thresholds.LOAD_LONG_MS);
	static final Rule.Signal RESENT_COUNTS = e -> is(e.resentCounts);
	static final Rule.Signal RESENT_ANY = e -> is(e.resent > 0);
	static final Rule.Signal RESENT_NONE = e -> is(e.resent == 0);
	static final Rule.Signal TICKS_LATE_OR_MISSING = e -> is(e.tickLate >= 1
		|| e.noTickMs >= Thresholds.NO_TICK_MS);
	/** N6's need: the event's NO_TICK trigger fired, a real stop; a late tick alone is not "No ticks". */
	static final Rule.Signal NO_TICK_FIRED = e -> is(e.noTick);
	static final Rule.Signal TICK_LATE = e -> is(e.tickLate >= 1);
	static final Rule.Signal TICK_EARLY = e -> is(e.tickEarly >= 1);
	static final Rule.Signal NO_TICK_EARLY = e -> is(e.tickEarly == 0);
	static final Rule.Signal RTT_KNOWN = e -> is(e.rttKnown);
	static final Rule.Signal RTT_SPIKE = e -> e.rttSpike;
	static final Rule.Signal NO_RTT_SPIKE = e -> e.rttSpike == NO_DATA ? NO_DATA : is(e.rttSpike == NO);
	/** "rttKnown and not rttSpike": no data when either half has none. */
	static final Rule.Signal PING_KNOWN_AND_CALM = e -> !e.rttKnown || e.rttSpike == NO_DATA ? NO_DATA
		: is(e.rttSpike == NO);
	static final Rule.Signal FRAMES_KEEP_UP = e -> e.frameMedianMs < 0 ? NO_DATA
		: is(e.frameMedianMs < Thresholds.LOW_FPS_FRAME_MS);
	static final Rule.Signal FRAME_OVER_LIMIT = e -> e.frameGapMs < 0 || e.frameLimitMs < 0 ? NO_DATA
		: is(e.frameGapMs >= e.frameLimitMs);
	static final Rule.Signal RTT_SWUNG = e -> e.rttMax < 0 || e.rttUsual < 0 ? NO_DATA
		: is(e.rttMax >= Thresholds.RTT_SWING_FACTOR * e.rttUsual);
	static final Rule.Signal ENOUGH_TICKS = e -> is(e.ticks >= Thresholds.SLOW_WORLD_MIN_TICKS);
	static final Rule.Signal TICKS_SLOW = e -> e.tickMedianMs < 0 ? NO_DATA
		: is(e.tickMedianMs >= Thresholds.SLOW_WORLD_MEDIAN_MS);
	static final Rule.Signal FRAMES_CLEAN = e -> e.frameGapMs < 0 ? NO_DATA : is(e.framesClean);
	static final Rule.Signal PING_NEAR_USUAL = e -> e.rtt < 0 || e.rttUsual < 0 ? NO_DATA
		: is(Math.abs(e.rtt - e.rttUsual) <= Thresholds.SLOW_WORLD_PING_MS);
	static final Rule.Signal PING_MAX_NEAR_USUAL = e -> e.rttMax < 0 || e.rttUsual < 0 ? NO_DATA
		: is(e.rttMax <= e.rttUsual + Thresholds.RTT_SPIKE_ADD_MS);
	static final Rule.Signal LASTED_LONG = e -> is(e.durationS >= Thresholds.SLOW_WORLD_LONG_S);
	/** S3's need: the client was busy, or its busy share cannot be known. */
	static final Rule.Signal BUSY_OR_UNKNOWN = e -> is(e.busyPm > Thresholds.BUSY_LOW_PM || e.busyPm == -1);
	static final Rule.Signal BUSY_HIGH = e -> e.busyPm < 0 ? NO_DATA : is(e.busyPm >= Thresholds.BUSY_HIGH_PM);
	static final Rule.Signal IDLE = e -> e.busyPm < 0 ? NO_DATA : is(e.busyPm <= Thresholds.BUSY_LOW_PM);
	/** S4's bar: the cap paces by waiting, and the gap is within CAP_WAIT_FACTOR cap intervals. */
	static final Rule.Signal CAP_EXPLAINS_THE_GAP = e -> is(e.capWaits
		&& e.frameGapMs <= Thresholds.CAP_WAIT_FACTOR * e.capIntervalMs);
	static final Rule.Signal CAP_SELF_SET = e -> is(e.capSelfSet);
	static final Rule.Signal CAP_HELD = e -> is(e.capHeldS >= Thresholds.CONDITION_HOLD_S);
	static final Rule.Signal F1_NEEDS_HOLD = e -> is(e.capSelfSet && e.capHeldS >= Thresholds.CONDITION_HOLD_S);
	static final Rule.Signal PING_HIGH = e -> e.rtt < 0 ? NO_DATA : is(e.rtt >= Thresholds.PING_BAD_MS);
	static final Rule.Signal PING_STEADY = e -> e.rttMax < 0 || e.rttMin < 0 ? NO_DATA
		: is(e.rttMax - e.rttMin < Thresholds.PING_STEADY_MS);
	static final Rule.Signal SLOW_FOR_LONG = e -> is(e.lowFpsS >= Thresholds.CONDITION_HOLD_S);
	static final Rule.Signal PING_KNOWN_AND_LOW = e -> !e.rttKnown || e.rtt < 0 ? NO_DATA
		: is(e.rtt < Thresholds.PING_WARN_MS);
	static final Rule.Signal ENOUGH_WINDOW_TICKS = e -> is(e.ticksInWindow >= Thresholds.SLOW_WORLD_WINDOW_TICKS);
	static final Rule.Signal WINDOW_TICKS_SLOW = e -> e.tickWindowMedianMs < 0 ? NO_DATA
		: is(e.tickWindowMedianMs >= Thresholds.SLOW_WORLD_WINDOW_MS);
	static final Rule.Signal NO_EVENT_IN_WINDOW = e -> is(!e.eventInWindow);
	static final Rule.Signal NO_DISCONNECT = e -> is(!e.disconnect);
	static final Rule.Signal HEAP_CAP_LOW = e -> e.heapMaxMb < 0 ? NO_DATA
		: is(e.heapMaxMb < Thresholds.HEAP_CAP_LOW_MB);

	// ------------------------------------------------------------------ the rows

	public static final Rule D1 = new Rule(1, "D1", Cause.DISCONNECT, Rule.Kind.EVENT, 200, Confidence.SURE,
		all(DISCONNECT), null, null);
	public static final Rule G1 = new Rule(2, "G1", Cause.GC_PAUSE, Rule.Kind.EVENT, 110, Confidence.SURE,
		all(FRAME_GAP_IS_A_PAUSE, PAUSE_IS_LONG, PAUSE_COVERS_THE_FRAME), all(LOADED), null);
	public static final Rule S1 = new Rule(3, "S1", Cause.MAP_LOAD, Rule.Kind.EVENT, 105, Confidence.SURE,
		all(LOAD_IS_LONG), null, null);
	public static final Rule N3 = new Rule(4, "N3", Cause.UPLOAD_LOSS, Rule.Kind.EVENT, 90, Confidence.LIKELY,
		all(RESENT_COUNTS), all(DISCONNECT), all(TICKS_LATE_OR_MISSING, RTT_SPIKE));
	public static final Rule N2 = new Rule(5, "N2", Cause.PING_JUMPY, Rule.Kind.EVENT, 85, Confidence.LIKELY,
		all(RTT_SPIKE, TICK_LATE, TICK_EARLY, FRAMES_KEEP_UP), all(RESENT_ANY, FRAME_OVER_LIMIT), all(RTT_SWUNG));
	public static final Rule W1 = new Rule(6, "W1", Cause.SLOW_WORLD, Rule.Kind.EVENT, 85, Confidence.LIKELY,
		all(ENOUGH_TICKS, TICKS_SLOW, NO_TICK_EARLY, FRAMES_CLEAN, RTT_KNOWN, NO_RTT_SPIKE, PING_NEAR_USUAL,
			PING_MAX_NEAR_USUAL, RESENT_NONE),
		all(DISCONNECT), all(LASTED_LONG));
	public static final Rule S3 = new Rule(7, "S3", Cause.CLIENT_BUSY, Rule.Kind.EVENT, 80, Confidence.HINT,
		all(FRAME_OVER_LIMIT, BUSY_OR_UNKNOWN), all(PAUSE_COVERS_THE_FRAME, LOADED, RESENT_COUNTS),
		all(BUSY_HIGH, PING_KNOWN_AND_CALM));
	public static final Rule S4 = new Rule(8, "S4", Cause.CLIENT_WAITING, Rule.Kind.EVENT, 80, Confidence.HINT,
		all(FRAME_OVER_LIMIT, IDLE), all(PAUSE_COVERS_THE_FRAME, LOADED, CAP_EXPLAINS_THE_GAP, RESENT_COUNTS),
		all(PING_KNOWN_AND_CALM));
	public static final Rule N6 = new Rule(9, "N6", Cause.DELIVERY_GAP, Rule.Kind.EVENT, 75,
		Confidence.CANT_TELL, all(NO_TICK_FIRED, FRAMES_CLEAN, RESENT_NONE), all(RTT_SPIKE, DISCONNECT),
		null);
	public static final Rule F1 = new Rule(10, "F1", Cause.FRAME_CAP, Rule.Kind.CONDITION, 120, Confidence.SURE,
		all(CAP_SELF_SET, CAP_HELD), null, null);
	public static final Rule N1 = new Rule(11, "N1", Cause.PING_HIGH, Rule.Kind.CONDITION, 60, Confidence.SURE,
		all(RTT_KNOWN, PING_HIGH, PING_STEADY), null, null);
	public static final Rule F2 = new Rule(12, "F2", Cause.SLOW_DRAWING, Rule.Kind.CONDITION, 60,
		Confidence.LIKELY, all(SLOW_FOR_LONG), all(F1_NEEDS_HOLD), all(PING_KNOWN_AND_LOW));
	public static final Rule W1C = new Rule(13, "W1c", Cause.SLOW_WORLD, Rule.Kind.CONDITION, 55, Confidence.HINT,
		all(ENOUGH_WINDOW_TICKS, WINDOW_TICKS_SLOW, NO_EVENT_IN_WINDOW, NO_DISCONNECT, FRAMES_CLEAN), null,
		all(PING_KNOWN_AND_CALM));
	public static final Rule G2 = new Rule(14, "G2", Cause.HEAP_CAP_LOW, Rule.Kind.CONDITION, 50, Confidence.SURE,
		all(HEAP_CAP_LOW), null, null);
	public static final Rule V2 = new Rule(15, "V2", Cause.ALL_CLEAR, Rule.Kind.ALWAYS, 10, Confidence.SURE,
		null, null, null);
	public static final Rule X = new Rule(16, "X", Cause.NOT_SURE, Rule.Kind.ENGINE, 0, Confidence.CANT_TELL,
		null, null, null);

	/** One rule per row of contract 6.3, in rank order. */
	public static final Rule[] ALL = {D1, G1, S1, N3, N2, W1, S3, S4, N6, F1, N1, F2, W1C, G2, V2, X};

	private Rules()
	{
	}

	/** How many rows the table has. */
	public static int count()
	{
		return ALL.length;
	}

	/** Row {@code i} of the table, 0 = rank 1. */
	public static Rule at(int i)
	{
		return ALL[i];
	}

	/** The row with this id ("D1" .. "X", "W1c"); null = none. */
	public static Rule byId(String id)
	{
		for (Rule r : ALL)
		{
			if (r.id.equals(id))
			{
				return r;
			}
		}
		return null;
	}

	/** The winning condition on this evidence: the highest score, the lower rank on a tie; null = none scores. */
	public static Rule bestCondition(Evidence e)
	{
		Rule best = null;
		int bestScore = 0;
		for (Rule r : ALL)
		{
			if (r.kind != Rule.Kind.CONDITION)
			{
				continue;
			}
			final int score = r.score(e);
			if (score > bestScore)
			{
				best = r;
				bestScore = score;
			}
		}
		return best;
	}

	private static int is(boolean holds)
	{
		return holds ? YES : NO;
	}

	private static Rule.Signal[] all(Rule.Signal... signals)
	{
		return signals;
	}
}
