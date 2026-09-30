package com.whylag.core;

import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

/**
 * A synthetic session written straight into the rings (contract 3.12), so the detector, the verdict and the
 * snapshot can be tested without the samplers. It never builds a {@link LagEvent}: the detector does that.
 *
 * <p><b>The frame of every trace.</b> Second 0 starts at wall time 2026-09-28T20:52:00Z, zone UTC, world 416, OS
 * WINDOWS. {@link Session#startNanos} is a fixed non-zero value.
 *
 * <p><b>The steady second</b> ({@link #steady(int)}). Every second holds exactly this, and every tick this:
 * <table>
 * <caption>The steady second</caption>
 * <tr><th>Column</th><th>Steady value</th></tr>
 * <tr><td>frames / worstFrameMs / worstFrameEndMs / slowFrames</td><td>50 / 22 / 500 / 0</td></tr>
 * <tr><td>busyPm (the second)</td><td>400, which is "Game 40 %"</td></tr>
 * <tr><td>worstBusyPm (its worst frame)</td><td>300</td></tr>
 * <tr><td>loadingMs / state / flags / world</td><td>0 / LOGGED_IN / FOCUSED / 416</td></tr>
 * <tr><td>players / npcs / region</td><td>0 / 0 / 0 (a test that needs them sets them)</td></tr>
 * <tr><td>rttMs / rttAgeS / conn</td><td>40 / 0 (fresh) / NONE</td></tr>
 * <tr><td>sentUnits / resentUnits</td><td>900 / 0</td></tr>
 * <tr><td>heapUsedMb</td><td>400 (of the settings' 768)</td></tr>
 * <tr><td>procCpuPct / sysCpuPct</td><td>40 / 20, which is "PC 20 %"</td></tr>
 * <tr><td>each tick</td><td>one every 600 ms from session ms 0; gap 600, frameMs 22, cycle jump 30, the RTT and
 * the flags of its second. The trace's FIRST tick has no tick before it: gap 600 ({@link Thresholds#TICK_MS}) and
 * cycle jump -1, as the tick sampler writes the session's first tick (contract section 7, L2)</td></tr>
 * </table>
 * Beside the seconds, a steady trace holds:
 * <ul>
 * <li>ONE collection, an INFERRED one (no length), at session ms -1000 with heap after 300. So
 * {@code heapAfterAt} reads 300 from second 0 on, the memory lane is 300 in every column, no span inside the trace
 * touches a collection, and {@code inferredIn} is false for every span inside the trace.</li>
 * <li>NO known pause: {@code GcRing.longestPauseMs} answers 0 for every span. With the default settings
 * (MANAGEMENT) that reads as "measured, none": the memory tile's small line is "pause 0 ms".</li>
 * <li>The login: {@code loggedInSinceSec} is {@code -WARMUP_S} (0 since the first live look, 2026-09-29; it was
 * -60). The trace is WARM from second 0 and nothing
 * is masked. {@link #loginAt} sets it to its second.</li>
 * <li>NO usual: the three usuals of the session are empty (the detector fills them from the quiet seconds it
 * reads). {@link #usual} makes this world's RTT usual known.</li>
 * </ul>
 *
 * <p><b>What the builders do beyond their name.</b> Each changes only what it names, in the seconds it names (both
 * ends included), and answers this trace; a second outside the trace is an {@link IllegalArgumentException}.
 * <ul>
 * <li>{@link #fps} sets {@code frames}. It ALSO raises the worst frame to the frame interval, ceil(1000 / fps),
 * never lowers it; at an interval of {@link Thresholds#SLOW_FRAME_MS} or more every frame of the second is slow
 * ({@code slowFrames} = fps, at most 127); the ticks that arrive in those seconds carry at least that interval as
 * {@code frameMs}. {@code fps(.., 0)} is a second with no frame, as the sampler fills one: {@code frames}, the
 * worst frame and its end 0, {@code slowFrames} 0, BOTH busy columns -1 (so the Game line has no data there), and
 * the NO_FRAMES flag.</li>
 * <li>{@link #frameGap} sets the worst frame and its end when it is at least as long as the second's worst so far
 * (a tie moves the end; a shorter frame leaves both alone) and, when {@code ms} is
 * {@link Thresholds#SLOW_FRAME_MS} or more, adds one slow frame; every second the frame covers WHOLE becomes a
 * no-frame second, as {@code fps(.., 0)}; ticks keep their arrival times, and those that arrive inside the frame
 * carry its length (the larger of it and their second's own frame interval). A LOADING FRAME: when any second the
 * frame touches holds loading time ({@link #loading} with more than 0 ms, called before or after), {@link #build()}
 * also sets the LOADING flag on the second the frame ends in, as the frame sampler's loading-frame rule does
 * (contract 6.1). A trace does not say where in a second its loading ms lie, so touching such a second counts as
 * overlapping its loading time. The steady frames of a second end inside it: only frameGap's frames carry a load
 * into a later second.</li>
 * <li>{@link #busy}: the worst frame's busy share. {@link #cpu}: the whole PC's CPU % ({@code sysCpuPct}) and the
 * game thread's busy % as the SECOND's share, {@code busyPm = gameBusyPct x 10}; -1 is unknown, for either.
 * {@link #procCpu}: the process's share of one core.</li>
 * <li>{@link #busyUnknown} (both busy columns) and {@link #cpuUnknown} ({@code sysCpuPct} and {@code procCpuPct})
 * write -1 in every second, and in the seconds {@link #shift} adds later too. A Runtime-only PC is
 * {@code cpuUnknown().busyUnknown()} with RUNTIME settings. {@link #cpu} after {@link #busyUnknown} or
 * {@code fps(.., 0)} writes {@code busyPm} again in its span: the last call wins.</li>
 * <li>{@link #players}, {@link #npcs}, {@link #region} (0 .. 65535): that column.</li>
 * <li>{@link #gcPause}: one known pause that STARTS {@code offsetMs} into its second. {@link #gcInferred}: one
 * collection with no length, at the START of its second (session ms {@code atSec x 1000}).
 * {@link #noBaselineCollection}: takes the steady trace's ONE collection away.</li>
 * <li>{@link #tickLate}: the FIRST tick that arrives in the second comes {@code lateMs} later; with catch-up the
 * ones after keep their times (so the next gap is 600 - late, and a late tick that would reach the next one is
 * refused), without it that tick and every later tick move by the same amount. {@link #ticksEvery}: the ticks
 * arriving in the span are replaced by ones {@code gapMs} apart - the first is {@code gapMs} after the last tick
 * before the span, stepped on by {@code gapMs} until it lies in the span (the span's first ms when no tick comes
 * before it) - and every tick after the span moves so the first of them comes {@link Thresholds#TICK_MS} after the
 * span's last; when that moves them earlier, new ticks 600 apart carry on after the last moved one and fill the hole
 * up to where the old ones ended: up to the old last tick's time, or, when that old last tick lay in the trace's
 * final 600 ms (the grid ran to the end), up to the trace's last ms. A tick that {@link #tickLate} or
 * {@link #ticksEvery} moves past the trace's last ms (to session ms {@code n x 1000} or later, for a trace of n
 * seconds) is dropped. A {@link #ticksEvery} gap that puts no tick in its span is refused. {@link #noTicks}: the
 * ticks arriving in the span are removed.</li>
 * <li>{@link #rtt}: RTT, fresh (0 ms or more). {@link #rttNoData}: RTT and age -1 with the reason. {@link #rttStale}:
 * the RTT kept, its age past {@link Thresholds#RTT_STALE_S} and growing, reason STALE. So {@code conn} is NONE in
 * exactly the seconds whose RTT is fresh, in every trace (contract 3.3). {@link #usual}: this world's usual RTT;
 * {@link #build()} adds {@link Thresholds#USUAL_MIN_SAMPLES} samples of it to {@code Session.rttUsual} and touches
 * no ring (the last call wins).</li>
 * <li>{@link #sent}, {@link #resent} ({@code resent} does not add to {@code sent}), {@link #heap}: that column.</li>
 * <li>{@link #loading}: {@code loadingMs} and the LOADING flag, in that second only (a {@link #frameGap} frame that
 * touches it also flags the second that frame ends in, above).</li>
 * <li>{@link #hop}: the HOP flag and state HOPPING in its second, the new world from it on, and - as the tick
 * sampler does - the LOGIN_MASK flag on the seconds of the next {@link Thresholds#LOGIN_MASK_TICKS} ticks from the
 * start of the next second. It does not move the login.</li>
 * <li>{@link #disconnect}: a one-second lost connection. The DISCONNECT flag and state CONNECTION_LOST in that
 * second (so a trace that ends there reads "Not logged in" at its newest second: CONNECTION_LOST is not in-game),
 * and - as the tick sampler does when a lost connection ends - the LOGIN_MASK flag on the seconds of the next
 * {@link Thresholds#LOGIN_MASK_TICKS} ticks from the start of the next second. It does not move the login.
 * Neither a hop nor a disconnect sets NOT_LOGGED_IN: the samplers set it only on OTHER, LOGIN_SCREEN and
 * LOGGING_IN seconds (contract section 7, L2).</li>
 * <li>{@link #loginAt}: every second before it is at the login screen (state LOGIN_SCREEN, NOT_LOGGED_IN, no
 * tick, nothing sent, ping NOT_LOGGED_IN); the first {@link Thresholds#LOGIN_MASK_TICKS} ticks from it on are
 * masked; {@code loggedInSinceSec} is its second.</li>
 * <li>{@link #unfocused}: the FOCUSED flag cleared. {@link #os}: the session's OS (and the default settings').
 * {@link #settings(SettingsView)}: what {@link #settings()} answers; it writes no ring.</li>
 * <li>{@link #shift}: k steady seconds come first, and every second, tick, frame, collection and mask moves later
 * by k seconds. The baseline collection moves too, to session ms {@code k x 1000 - 1000}. A login set by
 * {@link #loginAt} moves, and then the k new seconds are login-screen seconds as {@link #loginAt} writes them (no
 * tick, nothing sent, ping NOT_LOGGED_IN), so every second before the login is still at the login screen; the
 * steady login stays at {@code -WARMUP_S}. Without {@link #loginAt} the new seconds are ordinary steady seconds,
 * with ticks on the same 600 ms grid: a detector finds them quiet and feeds its usuals from them, so a shift can
 * make a usual exist sooner (contract section 7, L3, bounds its property test for that).</li>
 * </ul>
 *
 * <p><b>"Before the first collection"</b> is built in one of two ways. {@link #noBaselineCollection()}: the ring
 * is empty until the test adds a collection, so {@code heapAfterAt} is -1 and the memory lane is
 * {@link Strip#NONE} up to that collection. Or {@link #shift}: the columns that end before session ms
 * {@code k x 1000 - 1000} have no collection. A test may also build a {@link Session} by hand.
 *
 * <p><b>Default settings</b> ({@link #settings()}): renderer CPU (cap 50, CLIENT_50), heap limit 768, source
 * MANAGEMENT, refresh 60, FPS Control inactive, every renderer key unread (0 or ""), client version "". The steady
 * 900 bytes a second is over {@link Thresholds#CLICK_SENT_BYTES} on purpose: the click filter is a JUMP over the
 * quiet median, so a steady sender can still spike.
 *
 * <p>Every tick carries the flags and the RTT of the second it arrives in. {@link #build()} can be called any
 * number of times; each call answers a fresh {@link Session}. A trace longer than a ring wraps it, as a real
 * session would.
 *
 * <p><b>Now.</b> A test that asks for "now" at the end of a trace of n seconds passes {@code nowSec = n}: second
 * n - 1 is then the newest complete second, as in a live step ({@link Session#lastSec}). An earlier {@code nowSec}
 * replays the trace as it stood then.
 */
public final class Trace
{
	private static final long START_NANOS = 7_000_000_000_000L;
	private static final long START_WALL_MS = Instant.parse("2026-09-28T20:52:00Z").toEpochMilli();
	private static final int MS = 1000;
	private static final int TICK = Thresholds.TICK_MS;
	private static final int CYCLE_MS = 20;
	private static final int LAST_MS = 999;
	private static final int BYTE_MAX = 127;
	private static final int REGION_MAX = 65_535;
	private static final int PER_MILLE_PER_PERCENT = 10;

	private static final int FPS = 50;
	private static final int WORST_MS = 22;
	private static final int WORST_END_MS = 500;
	private static final int BUSY_PM = 400;
	private static final int WORST_BUSY_PM = 300;
	private static final int WORLD_ID = 416;
	private static final int RTT_MS = 40;
	private static final int SENT_UNITS = 900;
	private static final int HEAP_MB = 400;
	private static final int HEAP_MAX_MB = 768;
	private static final int HEAP_AFTER_MB = 300;
	private static final int PROC_CPU_PCT = 40;
	private static final int SYS_CPU_PCT = 20;
	private static final int REFRESH_HZ = 60;
	private static final long BASELINE_GC_MS = -1000;

	// Per-second columns, in the ring's order; TICK_FRAME is the frame a tick in that second carries.
	private static final int FRAMES = 0, WORST = 1, WORST_END = 2, SLOW = 3, BUSY = 4, WORST_BUSY = 5,
		LOADING = 6, STATE = 7, FLAGS = 8, WORLD = 9, PLAYERS = 10, NPCS = 11, REGION = 12, RTT = 13, RTT_AGE = 14,
		SENT = 15, RESENT = 16, HEAP = 17, PROC_CPU = 18, SYS_CPU = 19, TICK_FRAME = 20;
	private static final int COLUMNS = 21;
	private static final int[] STEADY = new int[COLUMNS];

	static
	{
		STEADY[FRAMES] = FPS;
		STEADY[WORST] = WORST_MS;
		STEADY[WORST_END] = WORST_END_MS;
		STEADY[SLOW] = 0;
		STEADY[BUSY] = BUSY_PM;
		STEADY[WORST_BUSY] = WORST_BUSY_PM;
		STEADY[LOADING] = 0;
		STEADY[STATE] = State.LOGGED_IN;
		STEADY[FLAGS] = Flags.FOCUSED;
		STEADY[WORLD] = WORLD_ID;
		STEADY[PLAYERS] = 0;
		STEADY[NPCS] = 0;
		STEADY[REGION] = 0;
		STEADY[RTT] = RTT_MS;
		STEADY[RTT_AGE] = 0;
		STEADY[SENT] = SENT_UNITS;
		STEADY[RESENT] = 0;
		STEADY[HEAP] = HEAP_MB;
		STEADY[PROC_CPU] = PROC_CPU_PCT;
		STEADY[SYS_CPU] = SYS_CPU_PCT;
		STEADY[TICK_FRAME] = WORST_MS;
	}

	private int n;
	private int[][] col;
	private NoData[] conn;
	/** Tick arrival times, session ms, ascending. */
	private final List<Integer> ticks = new ArrayList<>();
	/** Long frames: {start ms, end ms, length}. */
	private final List<long[]> frameGaps = new ArrayList<>();
	/** Collections: {start ms, duration (-1 inferred), heap after}. */
	private final List<long[]> gcs = new ArrayList<>();
	/** Session ms from which the next LOGIN_MASK_TICKS ticks are masked. */
	private final List<Long> maskStarts = new ArrayList<>();
	private long baselineGcMs = BASELINE_GC_MS;
	private boolean baselineCollection = true;
	private boolean busyUnknown;
	private boolean cpuUnknown;
	/** The session second of the login: the steady -WARMUP_S until {@link #loginAt} sets it. */
	private long loggedInSince = -Thresholds.WARMUP_S;
	private boolean loginSet;
	/** This world's usual RTT, or -1: none (the steady trace has no usual). */
	private int usualRttMs = -1;
	private Os os = Os.WINDOWS;
	private SettingsView settings;

	private Trace()
	{
	}

	/** {@code seconds} steady seconds (see the class notes). */
	public static Trace steady(int seconds)
	{
		if (seconds < 1)
		{
			throw new IllegalArgumentException("a trace needs at least one second, got " + seconds);
		}
		final Trace t = new Trace();
		t.n = seconds;
		t.col = new int[COLUMNS][seconds];
		for (int c = 0; c < COLUMNS; c++)
		{
			Arrays.fill(t.col[c], STEADY[c]);
		}
		t.conn = new NoData[seconds];
		Arrays.fill(t.conn, NoData.NONE);
		for (int at = 0; at < seconds * MS; at += TICK)
		{
			t.ticks.add(at);
		}
		return t;
	}

	/** {@code fps} frames a second in {@code fromSec .. toSec} (see the class notes). */
	public Trace fps(int fromSec, int toSec, int fps)
	{
		checkSpan(fromSec, toSec);
		if (fps < 0)
		{
			throw new IllegalArgumentException("fps must be 0 or more, got " + fps);
		}
		for (int s = fromSec; s <= toSec; s++)
		{
			if (fps == 0)
			{
				noFrames(s);
				continue;
			}
			final int interval = (MS + fps - 1) / fps;
			col[FRAMES][s] = fps;
			col[FLAGS][s] &= ~Flags.NO_FRAMES;
			col[WORST][s] = Math.max(col[WORST][s], interval);
			if (interval >= Thresholds.SLOW_FRAME_MS)
			{
				col[SLOW][s] = Math.min(fps, BYTE_MAX);
			}
			col[TICK_FRAME][s] = Math.max(col[TICK_FRAME][s], interval);
		}
		return this;
	}

	/**
	 * One frame of {@code ms} that ENDS {@code endOffsetMs} (0 .. 999) into {@code atSec}; ticks inside it carry
	 * {@code ms}. There is no two-argument form: every test says where the freeze was. It becomes the second's worst
	 * frame when it is at least as long as the worst so far, and a loading frame when it touches a second with
	 * loading time (see the class notes).
	 */
	public Trace frameGap(int atSec, int endOffsetMs, int ms)
	{
		checkSecond(atSec);
		if (endOffsetMs < 0 || endOffsetMs > LAST_MS)
		{
			throw new IllegalArgumentException("the end offset is 0 .. 999, got " + endOffsetMs);
		}
		if (ms < 0)
		{
			throw new IllegalArgumentException("a frame lasts 0 ms or more, got " + ms);
		}
		final long end = (long) atSec * MS + endOffsetMs;
		final long start = end - ms;
		if (ms >= col[WORST][atSec])
		{
			col[WORST][atSec] = ms;
			col[WORST_END][atSec] = endOffsetMs;
		}
		if (ms >= Thresholds.SLOW_FRAME_MS)
		{
			col[SLOW][atSec] = Math.min(BYTE_MAX, col[SLOW][atSec] + 1);
		}
		// A second the frame covers whole ended no frame: the sampler writes it as a NO_FRAMES second.
		for (long s = Math.floorDiv(start, MS) + 1; s < atSec; s++)
		{
			if (s >= 0)
			{
				noFrames((int) s);
			}
		}
		frameGaps.add(new long[] {start, end, ms});
		return this;
	}

	/** The busy share of the worst frame of {@code atSec}, per mille. */
	public Trace busy(int atSec, int perMille)
	{
		checkSecond(atSec);
		col[WORST_BUSY][atSec] = perMille;
		return this;
	}

	/** The CPU clock answers nothing: both busy columns are -1 in every second, and in those {@link #shift} adds. */
	public Trace busyUnknown()
	{
		busyUnknown = true;
		Arrays.fill(col[BUSY], -1);
		Arrays.fill(col[WORST_BUSY], -1);
		return this;
	}

	/**
	 * The whole PC's CPU % and the game thread's busy %, in every second of {@code fromSec .. toSec}: it writes
	 * {@code sysCpuPct = sysPct} and {@code busyPm = gameBusyPct x 10} (the SECOND's share; the worst frame's is
	 * {@link #busy}). -1 is unknown, for either; it writes {@code busyPm} again after {@link #busyUnknown} or
	 * {@code fps(.., 0)}: the last call wins.
	 */
	public Trace cpu(int fromSec, int toSec, int sysPct, int gameBusyPct)
	{
		checkSpan(fromSec, toSec);
		checkShare("the PC's CPU", sysPct);
		checkShare("the game thread's busy share", gameBusyPct);
		for (int s = fromSec; s <= toSec; s++)
		{
			col[SYS_CPU][s] = sysPct;
			col[BUSY][s] = gameBusyPct < 0 ? -1 : gameBusyPct * PER_MILLE_PER_PERCENT;
		}
		return this;
	}

	/** The process's share of ONE core, in every second of {@code fromSec .. toSec}; -1 is unknown. */
	public Trace procCpu(int fromSec, int toSec, int pct)
	{
		checkSpan(fromSec, toSec);
		checkShare("the process's CPU", pct);
		for (int s = fromSec; s <= toSec; s++)
		{
			col[PROC_CPU][s] = pct;
		}
		return this;
	}

	/** No CPU figure: {@code sysCpuPct} and {@code procCpuPct} are -1 in every second, and in those {@link #shift} adds. */
	public Trace cpuUnknown()
	{
		cpuUnknown = true;
		Arrays.fill(col[SYS_CPU], -1);
		Arrays.fill(col[PROC_CPU], -1);
		return this;
	}

	/** {@code n} players in the scene in {@code fromSec .. toSec}. */
	public Trace players(int fromSec, int toSec, int n)
	{
		checkSpan(fromSec, toSec);
		checkCount("players", n);
		for (int s = fromSec; s <= toSec; s++)
		{
			col[PLAYERS][s] = n;
		}
		return this;
	}

	/** {@code n} NPCs in the scene in {@code fromSec .. toSec}. */
	public Trace npcs(int fromSec, int toSec, int n)
	{
		checkSpan(fromSec, toSec);
		checkCount("npcs", n);
		for (int s = fromSec; s <= toSec; s++)
		{
			col[NPCS][s] = n;
		}
		return this;
	}

	/** The map region {@code id} (0 .. 65535) in {@code fromSec .. toSec}. */
	public Trace region(int fromSec, int toSec, int id)
	{
		checkSpan(fromSec, toSec);
		if (id < 0 || id > REGION_MAX)
		{
			throw new IllegalArgumentException("a region id is 0 .. 65535, got " + id);
		}
		for (int s = fromSec; s <= toSec; s++)
		{
			col[REGION][s] = id;
		}
		return this;
	}

	/** A known pause of {@code ms} that STARTS {@code offsetMs} into {@code atSec}. */
	public Trace gcPause(int atSec, int offsetMs, int ms, int heapAfterMb)
	{
		checkSecond(atSec);
		if (offsetMs < 0 || offsetMs > LAST_MS)
		{
			throw new IllegalArgumentException("the offset is 0 .. 999, got " + offsetMs);
		}
		if (ms < 0)
		{
			throw new IllegalArgumentException("a known pause lasts 0 ms or more, got " + ms);
		}
		gcs.add(new long[] {(long) atSec * MS + offsetMs, ms, heapAfterMb});
		return this;
	}

	/** A collection with no length at the start of {@code atSec}, as the fallback source infers one. */
	public Trace gcInferred(int atSec, int heapAfterMb)
	{
		checkSecond(atSec);
		gcs.add(new long[] {(long) atSec * MS, -1, heapAfterMb});
		return this;
	}

	/** Takes the steady trace's ONE collection away: the ring is empty until the test adds one. */
	public Trace noBaselineCollection()
	{
		baselineCollection = false;
		return this;
	}

	/** The first tick of {@code atSec} comes {@code lateMs} late: gap 600 + late; next gap 600 - late if catchUp. */
	public Trace tickLate(int atSec, int lateMs, boolean catchUp)
	{
		checkSecond(atSec);
		if (lateMs < 0)
		{
			throw new IllegalArgumentException("late is 0 ms or more, got " + lateMs);
		}
		final int idx = firstTickFrom(atSec * MS);
		if (idx < 0 || ticks.get(idx) >= (atSec + 1) * MS)
		{
			throw new IllegalArgumentException("no tick arrives in second " + atSec);
		}
		final int arrives = ticks.get(idx) + lateMs;
		if (catchUp)
		{
			if (idx + 1 < ticks.size() && arrives >= ticks.get(idx + 1))
			{
				throw new IllegalArgumentException("a late tick that catches up must still come before the next one");
			}
			ticks.set(idx, arrives);
		}
		else
		{
			for (int i = idx; i < ticks.size(); i++)
			{
				ticks.set(i, ticks.get(i) + lateMs);
			}
		}
		dropTicksPastTheEnd();
		return this;
	}

	/**
	 * Ticks {@code gapMs} apart in {@code fromSec .. toSec}; the ones after resume 600 after the last of them (see
	 * the class notes). A gap that puts no tick in the span is refused, and the trace is left as it was: a span with
	 * no tick is {@link #noTicks}.
	 */
	public Trace ticksEvery(int fromSec, int toSec, int gapMs)
	{
		checkSpan(fromSec, toSec);
		if (gapMs <= 0)
		{
			throw new IllegalArgumentException("a gap is 1 ms or more, got " + gapMs);
		}
		final int lo = fromSec * MS;
		final int hi = (toSec + 1) * MS;
		// The tick before the span is not replaced, so the first new tick is known before anything changes.
		final int before = lastTickBefore(lo);
		int at = before == Integer.MIN_VALUE ? lo : before + gapMs;
		while (at < lo)
		{
			at += gapMs;
		}
		if (at >= hi)
		{
			throw new IllegalArgumentException("a gap of " + gapMs + " ms puts no tick in " + fromSec + " .. " + toSec
				+ "; a span with no tick is noTicks");
		}
		final int oldLast = ticks.isEmpty() ? Integer.MIN_VALUE : ticks.get(ticks.size() - 1);
		removeTicks(lo, hi);
		int insertAt = firstTickFrom(lo);
		if (insertAt < 0)
		{
			insertAt = ticks.size();
		}
		int last = at;
		for (; at < hi; at += gapMs)
		{
			ticks.add(insertAt++, at);
			last = at;
		}
		if (insertAt < ticks.size())
		{
			final int delta = last + TICK - ticks.get(insertAt);
			for (int i = insertAt; i < ticks.size(); i++)
			{
				ticks.set(i, ticks.get(i) + delta);
			}
			dropTicksPastTheEnd();
			if (delta < 0)
			{
				// Moving earlier left a hole where the old ticks ended; carry the 600 ms grid back over it.
				final int until = oldLast >= n * MS - TICK ? n * MS - 1 : oldLast;
				for (int t = ticks.get(ticks.size() - 1) + TICK; t <= until; t += TICK)
				{
					ticks.add(t);
				}
			}
		}
		return this;
	}

	/** No tick arrives in {@code fromSec .. toSec}. */
	public Trace noTicks(int fromSec, int toSec)
	{
		checkSpan(fromSec, toSec);
		removeTicks(fromSec * MS, (toSec + 1) * MS);
		return this;
	}

	/** A fresh RTT of {@code ms} (0 or more; no RTT is {@link #rttNoData}) in {@code fromSec .. toSec}. */
	public Trace rtt(int fromSec, int toSec, int ms)
	{
		checkSpan(fromSec, toSec);
		if (ms < 0)
		{
			throw new IllegalArgumentException("a fresh RTT is 0 ms or more, got " + ms + "; no RTT is rttNoData");
		}
		for (int s = fromSec; s <= toSec; s++)
		{
			col[RTT][s] = ms;
			col[RTT_AGE][s] = 0;
			conn[s] = NoData.NONE;
		}
		return this;
	}

	/** No RTT in {@code fromSec .. toSec}, for the reason {@code why} (never {@link NoData#NONE}). */
	public Trace rttNoData(int fromSec, int toSec, NoData why)
	{
		checkSpan(fromSec, toSec);
		if (why == null || why == NoData.NONE)
		{
			throw new IllegalArgumentException("no data needs a reason");
		}
		for (int s = fromSec; s <= toSec; s++)
		{
			col[RTT][s] = -1;
			col[RTT_AGE][s] = -1;
			conn[s] = why;
		}
		return this;
	}

	/** The RTT goes stale in {@code fromSec .. toSec}: kept, but older than {@link Thresholds#RTT_STALE_S}. */
	public Trace rttStale(int fromSec, int toSec)
	{
		checkSpan(fromSec, toSec);
		for (int s = fromSec; s <= toSec; s++)
		{
			col[RTT_AGE][s] = Thresholds.RTT_STALE_S + 1 + (s - fromSec);
			conn[s] = NoData.STALE;
		}
		return this;
	}

	/**
	 * This world's usual RTT: {@link #build()} adds {@link Thresholds#USUAL_MIN_SAMPLES} samples of {@code rttMs} to
	 * {@code Session.rttUsual}, so its median is {@code rttMs}. The session usual and the frame rate usual stay
	 * empty, and no ring changes. The last call wins; {@link #shift} keeps it.
	 */
	public Trace usual(int rttMs)
	{
		if (rttMs < 0)
		{
			throw new IllegalArgumentException("a usual RTT is 0 ms or more, got " + rttMs);
		}
		usualRttMs = rttMs;
		return this;
	}

	/** {@code unitsPerSecond} sent in each second of {@code fromSec .. toSec}. */
	public Trace sent(int fromSec, int toSec, int unitsPerSecond)
	{
		checkSpan(fromSec, toSec);
		for (int s = fromSec; s <= toSec; s++)
		{
			col[SENT][s] = unitsPerSecond;
		}
		return this;
	}

	/** {@code units} re-sent in {@code atSec}. */
	public Trace resent(int atSec, int units)
	{
		checkSecond(atSec);
		col[RESENT][atSec] = units;
		return this;
	}

	/** {@code ms} of LOADING in {@code atSec}: its {@code loadingMs} and the LOADING flag, in that second only. */
	public Trace loading(int atSec, int ms)
	{
		checkSecond(atSec);
		if (ms < 0 || ms > MS)
		{
			throw new IllegalArgumentException("a second holds 0 .. 1000 ms of loading, got " + ms);
		}
		col[LOADING][atSec] = ms;
		col[FLAGS][atSec] |= Flags.LOADING;
		return this;
	}

	/** A hop to {@code newWorld} in {@code atSec} (see the class notes). It does not move the login. */
	public Trace hop(int atSec, int newWorld)
	{
		checkSecond(atSec);
		col[FLAGS][atSec] |= Flags.HOP;
		col[STATE][atSec] = State.HOPPING;
		for (int s = atSec; s < n; s++)
		{
			col[WORLD][s] = newWorld;
		}
		maskStarts.add((long) (atSec + 1) * MS);
		return this;
	}

	/**
	 * The connection is lost in {@code atSec} and back in the next second: the DISCONNECT flag and state
	 * CONNECTION_LOST in {@code atSec}, and - as the tick sampler does when a lost connection ends - the LOGIN_MASK
	 * flag on the seconds of the next {@link Thresholds#LOGIN_MASK_TICKS} ticks from the start of the next second.
	 * It does not move the login.
	 */
	public Trace disconnect(int atSec)
	{
		checkSecond(atSec);
		col[FLAGS][atSec] |= Flags.DISCONNECT;
		col[STATE][atSec] = State.CONNECTION_LOST;
		maskStarts.add((long) (atSec + 1) * MS);
		return this;
	}

	/** The player logs in at {@code sec}: the seconds before it are at the login screen (see the class notes). */
	public Trace loginAt(int sec)
	{
		checkSecond(sec);
		for (int s = 0; s < sec; s++)
		{
			loginScreen(s);
		}
		removeTicks(Integer.MIN_VALUE, sec * MS);
		maskStarts.add((long) sec * MS);
		loggedInSince = sec;
		loginSet = true;
		return this;
	}

	/** Second {@code s} at the login screen, as {@link #loginAt} writes the seconds before the login. */
	private void loginScreen(int s)
	{
		col[STATE][s] = State.LOGIN_SCREEN;
		col[FLAGS][s] |= Flags.NOT_LOGGED_IN;
		col[RTT][s] = -1;
		col[RTT_AGE][s] = -1;
		col[SENT][s] = 0;
		col[RESENT][s] = 0;
		conn[s] = NoData.NOT_LOGGED_IN;
	}

	/** The window is not focused in {@code fromSec .. toSec}. */
	public Trace unfocused(int fromSec, int toSec)
	{
		checkSpan(fromSec, toSec);
		for (int s = fromSec; s <= toSec; s++)
		{
			col[FLAGS][s] &= ~Flags.FOCUSED;
		}
		return this;
	}

	/** {@code usedMb} of heap used in {@code fromSec .. toSec}. */
	public Trace heap(int fromSec, int toSec, int usedMb)
	{
		checkSpan(fromSec, toSec);
		for (int s = fromSec; s <= toSec; s++)
		{
			col[HEAP][s] = usedMb;
		}
		return this;
	}

	/** The session's operating system, and the default settings' too. */
	public Trace os(Os os)
	{
		if (os == null)
		{
			throw new IllegalArgumentException("an OS is needed");
		}
		this.os = os;
		return this;
	}

	/** What {@link #settings()} answers from now on. */
	public Trace settings(SettingsView s)
	{
		settings = s;
		return this;
	}

	/**
	 * The same trace, {@code seconds} later: steady seconds first, everything else moved by as much. After
	 * {@link #loginAt} the new seconds are at the login screen, with no tick, so every second before the login still
	 * is (see the class notes).
	 */
	public Trace shift(int seconds)
	{
		if (seconds < 0)
		{
			throw new IllegalArgumentException("a trace moves later, not earlier: " + seconds);
		}
		if (seconds == 0)
		{
			return this;
		}
		final int k = seconds;
		final int[][] moved = new int[COLUMNS][n + k];
		for (int c = 0; c < COLUMNS; c++)
		{
			final boolean unknown = busyUnknown && (c == BUSY || c == WORST_BUSY)
				|| cpuUnknown && (c == SYS_CPU || c == PROC_CPU);
			Arrays.fill(moved[c], 0, k, unknown ? -1 : STEADY[c]);
			System.arraycopy(col[c], 0, moved[c], k, n);
		}
		col = moved;
		final NoData[] movedConn = new NoData[n + k];
		Arrays.fill(movedConn, 0, k, NoData.NONE);
		System.arraycopy(conn, 0, movedConn, k, n);
		conn = movedConn;
		if (loginSet)
		{
			for (int s = 0; s < k; s++)
			{
				loginScreen(s);
			}
		}

		final int by = k * MS;
		for (int i = 0; i < ticks.size(); i++)
		{
			ticks.set(i, ticks.get(i) + by);
		}
		if (!loginSet)
		{
			// The filler's ticks meet the moved ones on the same 600 ms grid.
			final List<Integer> filler = new ArrayList<>();
			for (int at = by % TICK; at < by; at += TICK)
			{
				filler.add(at);
			}
			ticks.addAll(0, filler);
		}
		for (long[] f : frameGaps)
		{
			f[0] += by;
			f[1] += by;
		}
		for (long[] g : gcs)
		{
			g[0] += by;
		}
		baselineGcMs += by;
		for (int i = 0; i < maskStarts.size(); i++)
		{
			maskStarts.set(i, maskStarts.get(i) + by);
		}
		if (loginSet)
		{
			loggedInSince += k;
		}
		n += k;
		return this;
	}

	/** A fresh session holding this trace. */
	public Session build()
	{
		final Session s = new Session(START_NANOS, START_WALL_MS, os, ZoneOffset.UTC);
		final int[] flags = col[FLAGS].clone();
		// A loading frame (contract 6.1): a frameGap frame that touches a second with loading time flags the second
		// it ends in, as the frame sampler does. Where in a second its loading ms lie is not said, so a touch counts.
		for (long[] g : frameGaps)
		{
			final int endSec = (int) Math.floorDiv(g[1], MS);
			for (long sec = Math.max(0, Math.floorDiv(g[0], MS)); sec <= endSec && sec < n; sec++)
			{
				if (col[LOADING][(int) sec] > 0)
				{
					flags[endSec] |= Flags.LOADING;
					break;
				}
			}
		}
		final boolean[] masked = new boolean[ticks.size()];
		for (long start : maskStarts)
		{
			int left = Thresholds.LOGIN_MASK_TICKS;
			for (int i = 0; i < ticks.size() && left > 0; i++)
			{
				if (ticks.get(i) >= start)
				{
					masked[i] = true;
					left--;
				}
			}
		}
		for (int i = 0; i < ticks.size(); i++)
		{
			if (masked[i])
			{
				flags[ticks.get(i) / MS] |= Flags.LOGIN_MASK;
			}
		}

		final FrameSecond f = new FrameSecond();
		final HostSecond h = new HostSecond();
		for (int sec = 0; sec < n; sec++)
		{
			f.frames = col[FRAMES][sec];
			f.worstFrameMs = col[WORST][sec];
			f.worstFrameEndMs = col[WORST_END][sec];
			f.slowFrames = col[SLOW][sec];
			f.busyPm = col[BUSY][sec];
			f.worstBusyPm = col[WORST_BUSY][sec];
			f.loadingMs = col[LOADING][sec];
			f.state = col[STATE][sec];
			f.flags = flags[sec];
			f.world = col[WORLD][sec];
			f.players = col[PLAYERS][sec];
			f.npcs = col[NPCS][sec];
			f.region = col[REGION][sec];
			s.seconds.putFrame(sec, f);
			h.rttMs = col[RTT][sec];
			h.rttAgeS = col[RTT_AGE][sec];
			h.sentUnits = col[SENT][sec];
			h.resentUnits = col[RESENT][sec];
			h.heapUsedMb = col[HEAP][sec];
			h.procCpuPct = col[PROC_CPU][sec];
			h.sysCpuPct = col[SYS_CPU][sec];
			h.conn = conn[sec];
			s.seconds.putHost(sec, h);
		}

		final TickRow row = new TickRow();
		boolean first = true;
		int previous = 0;
		for (int at : ticks)
		{
			final int sec = at / MS;
			row.atMs = (long) at;
			// The first tick has no tick before it: the steady gap and no cycle jump, as the tick sampler writes
			// the session's first tick (contract section 7, L2).
			row.gapMs = first ? TICK : at - previous;
			row.frameMs = frameOfTickAt(at, sec);
			row.cycleJump = first ? -1 : (row.gapMs + CYCLE_MS / 2) / CYCLE_MS;
			row.rttMs = col[RTT][sec];
			row.flags = flags[sec];
			s.ticks.put(row);
			previous = at;
			first = false;
		}

		if (baselineCollection)
		{
			s.gcs.put(baselineGcMs, -1, HEAP_AFTER_MB);
		}
		final List<long[]> byStart = new ArrayList<>(gcs);
		byStart.sort(Comparator.comparingLong(g -> g[0]));
		for (long[] g : byStart)
		{
			s.gcs.put(g[0], (int) g[1], (int) g[2]);
		}
		s.loggedInSince(loggedInSince);
		if (usualRttMs >= 0)
		{
			for (int i = 0; i < Thresholds.USUAL_MIN_SAMPLES; i++)
			{
				s.rttUsual.add(usualRttMs);
			}
		}
		return s;
	}

	/** The explicit settings, or the default ones (see the class notes). */
	public SettingsView settings()
	{
		if (settings != null)
		{
			return settings;
		}
		return new SettingsView(Renderer.CPU, false, false, 0, false, 0, false, "", 0, 0, "", 0, REFRESH_HZ,
			HEAP_MAX_MB, MemorySource.MANAGEMENT, os, "");
	}

	/** How many seconds the trace holds. */
	public int seconds()
	{
		return n;
	}

	private int frameOfTickAt(int at, int sec)
	{
		long ms = col[TICK_FRAME][sec];
		for (long[] g : frameGaps)
		{
			if (at >= g[0] && at <= g[1])
			{
				ms = Math.max(ms, g[2]);
			}
		}
		return (int) ms;
	}

	private void noFrames(int s)
	{
		col[FRAMES][s] = 0;
		col[WORST][s] = 0;
		col[WORST_END][s] = 0;
		col[SLOW][s] = 0;
		col[BUSY][s] = -1;
		col[WORST_BUSY][s] = -1;
		col[FLAGS][s] |= Flags.NO_FRAMES;
	}

	/** The newest tick before {@code ms}, or {@link Integer#MIN_VALUE} when none is. */
	private int lastTickBefore(int ms)
	{
		int found = Integer.MIN_VALUE;
		for (int at : ticks)
		{
			if (at >= ms)
			{
				break;
			}
			found = at;
		}
		return found;
	}

	/** The index of the first tick at or after {@code ms}, or -1. */
	private int firstTickFrom(int ms)
	{
		for (int i = 0; i < ticks.size(); i++)
		{
			if (ticks.get(i) >= ms)
			{
				return i;
			}
		}
		return -1;
	}

	private void removeTicks(int fromMs, int toMsExclusive)
	{
		ticks.removeIf(at -> at >= fromMs && at < toMsExclusive);
	}

	private void dropTicksPastTheEnd()
	{
		final int end = n * MS;
		ticks.removeIf(at -> at >= end);
	}

	private void checkSecond(int sec)
	{
		if (sec < 0 || sec >= n)
		{
			throw new IllegalArgumentException("second " + sec + " is outside the trace (0 .. " + (n - 1) + ")");
		}
	}

	private void checkSpan(int fromSec, int toSec)
	{
		checkSecond(fromSec);
		checkSecond(toSec);
		if (toSec < fromSec)
		{
			throw new IllegalArgumentException("the span " + fromSec + " .. " + toSec + " runs backwards");
		}
	}

	/** A share in %: 0 or more, or -1 for unknown. */
	private static void checkShare(String what, int pct)
	{
		if (pct < -1)
		{
			throw new IllegalArgumentException(what + " is 0 % or more, or -1 for unknown; got " + pct);
		}
	}

	private static void checkCount(String what, int count)
	{
		if (count < 0)
		{
			throw new IllegalArgumentException(what + " is a count, 0 or more; got " + count);
		}
	}
}
