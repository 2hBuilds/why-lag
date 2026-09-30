package com.whylag.core;

import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * The shared test helper does what contract 3.12 says: a steady trace is 50 fps, 600 ms ticks and no re-sends in
 * every second, PC 20 % and game 40 %, one inferred collection before it, no known pause, and a login at -60 that
 * makes it warm from second 0; and each builder method changes ONLY what it names (and what 3.12 says it changes
 * beyond its name). Every change below is measured against a steady trace of the same length, column by column and
 * second by second, so a builder that touched one column too many fails here rather than inside another lot's test.
 */
public class TraceTest
{
	private static final int N = 100;

	// ---------------------------------------------------------------- the steady trace

	@Test
	public void aSteadyTraceIsFiftyFpsSixHundredMsTicksAndNoResendsInEverySecond()
	{
		final Session s = Trace.steady(300).build();
		final SecondRing r = s.seconds;
		assertEquals(0, r.tail());
		assertEquals(299, r.head());
		for (long sec = 0; sec < 300; sec++)
		{
			assertEquals("frames @" + sec, 50, r.frames(sec));
			assertEquals("re-sent @" + sec, 0, r.resentUnits(sec));
			assertEquals(22, r.worstFrameMs(sec));
			assertEquals(500, r.worstFrameEndMs(sec));
			assertEquals(0, r.slowFrames(sec));
			assertEquals("the second's share: game 40 %", 400, r.busyPm(sec));
			assertEquals("its worst frame's share", 300, r.worstBusyPm(sec));
			assertEquals(0, r.loadingMs(sec));
			assertEquals(State.LOGGED_IN, r.state(sec));
			assertEquals("focused, logged in, nothing masked", Flags.FOCUSED, r.flags(sec));
			assertEquals(416, r.world(sec));
			assertEquals(40, r.rttMs(sec));
			assertEquals("fresh", 0, r.rttAgeS(sec));
			assertEquals(900, r.sentUnits(sec));
			assertEquals(400, r.heapUsedMb(sec));
			assertEquals(NoData.NONE, r.conn(sec));
		}
		final TickRing t = s.ticks;
		assertEquals("300 s of ticks 600 ms apart", 499, t.head());
		for (long q = t.tail(); q <= t.head(); q++)
		{
			assertEquals(600 * q, t.atMs(q));
			assertEquals("the first tick too has the steady gap", 600, t.gapMs(q));
			assertEquals(22, t.frameMs(q));
			assertEquals("the first tick has no tick before it: no cycle jump", q == 0 ? -1 : 30, t.cycleJump(q));
			assertEquals(0, t.corrected(q));
		}
	}

	@Test
	public void theFrameOfEveryTrace()
	{
		final Trace trace = Trace.steady(10);
		final Session s = trace.build();
		assertEquals(Instant.parse("2026-09-28T20:52:00Z").toEpochMilli(), s.startWallMs);
		assertEquals(ZoneOffset.UTC, s.zone);
		assertEquals("20:52", Fmt.clock(s.wallMsOf(0), s.zone));
		assertEquals(Os.WINDOWS, s.os);
		assertEquals(10, trace.seconds());
		assertEquals(0, s.secOf(s.startNanos));
		assertEquals("second 0 starts at the start", 1, s.secOf(s.startNanos + 1_000_000_000L));
	}

	@Test
	public void theDefaultSettingsAreTheClientsOwnFiftyFpsCap()
	{
		final SettingsView v = Trace.steady(10).settings();
		assertEquals(Renderer.CPU, v.renderer);
		assertEquals(50, v.capFps(true));
		assertEquals(CapSource.CLIENT_50, v.capSource(true));
		assertEquals(768, v.heapMaxMb);
		assertEquals(MemorySource.MANAGEMENT, v.memorySource);
		assertEquals(60, v.refreshHz);
		assertEquals(Os.WINDOWS, v.os);
		assertFalse(v.fpsControlActive);
	}

	@Test
	public void heapAfterCollectionIsThreeHundredFromTheFirstSecondAndTouchesNoSpan()
	{
		final GcRing g = Trace.steady(100).build().gcs;
		assertEquals("one collection, before the trace", 0, g.head());
		assertEquals(-1000, g.startMs(0));
		assertEquals("it has no length", -1, g.durationMs(0));
		assertEquals(300, g.heapAfterAt(0));
		assertEquals(300, g.heapAfterAt(99_999));
		assertEquals(300, g.lastHeapAfterMb());
		assertEquals(0, g.longestPauseMs(0, 99_999));
		assertEquals("the tile's pause over the last window: measured, none", 0,
			g.longestPauseMs((99 - Thresholds.WINDOW_S + 1) * 1000L, 99_999));
		assertFalse(g.inferredIn(0, 99_999));
	}

	/** Every second: the whole PC at 20 % ("PC 20 %"), the process at 40, the game thread's second 400 per mille. */
	@Test
	public void theSteadyCpuIsPcTwentyGameForty()
	{
		final SecondRing r = Trace.steady(N).build().seconds;
		for (long sec = 0; sec < N; sec++)
		{
			assertEquals("sysCpuPct @" + sec, 20, r.sysCpuPct(sec));
			assertEquals("procCpuPct @" + sec, 40, r.procCpuPct(sec));
			assertEquals("busyPm @" + sec, 400, r.busyPm(sec));
			assertEquals("worstBusyPm @" + sec, 300, r.worstBusyPm(sec));
			assertEquals("game 40 %", 40, Fmt.busyPct(r.busyPm(sec)));
		}
		final Session shifted = Trace.steady(10).shift(5).build();
		assertEquals("the seconds a shift adds are steady too", 20, shifted.seconds.sysCpuPct(0));
		assertEquals(400, shifted.seconds.busyPm(0));
	}

	/**
	 * No known pause in a steady trace: {@code longestPauseMs} is 0 for every span - each second, and the window
	 * the memory tile reads at every newest second - which on the default MANAGEMENT source reads "measured, none",
	 * the memory tile's "pause 0 ms".
	 */
	@Test
	public void aSteadyTraceHasNoKnownPause()
	{
		final Trace trace = Trace.steady(N);
		final GcRing g = trace.build().gcs;
		assertEquals(MemorySource.MANAGEMENT, trace.settings().memorySource);
		assertEquals(0, g.longestPauseMs(Long.MIN_VALUE / 2, Long.MAX_VALUE / 2));
		for (long sec = 0; sec < N; sec++)
		{
			assertEquals("second " + sec, 0, g.longestPauseMs(sec * 1000, sec * 1000 + 999));
			assertEquals(0, g.overlapMs(sec * 1000, sec * 1000 + 1000));
			assertEquals("the window that ends with second " + sec, 0,
				g.longestPauseMs((sec - Thresholds.WINDOW_S + 1) * 1000, (sec + 1) * 1000 - 1));
		}
		assertEquals("the baseline collection has no length", 0, g.longestPauseMs(-2_000, 0));
	}

	/** The steady login is at -WARMUP_S (0 since the first live look): warm from second 0, nothing to wait for. */
	@Test
	public void aSteadyTraceIsAlreadyWarm()
	{
		final Session s = Trace.steady(10).build();
		assertEquals(-Thresholds.WARMUP_S, s.loggedInSinceSec());
		assertTrue(s.warm(0));
		assertEquals(0, s.warmupLeftS(0));
		assertEquals("a shift keeps the steady login", -Thresholds.WARMUP_S,
			Trace.steady(10).shift(20).build().loggedInSinceSec());
	}

	/**
	 * {@code loginAt(30)}: the login is at 30, warm WARMUP_S after it (at 30 itself since the first live look, not at
	 * 29); a shift moves a login that loginAt set.
	 */
	@Test
	public void loginAtSetsTheLogin()
	{
		final Session s = Trace.steady(200).loginAt(30).build();
		assertEquals(30, s.loggedInSinceSec());
		assertFalse(s.warm(30 + Thresholds.WARMUP_S - 1));
		assertTrue(s.warm(30 + Thresholds.WARMUP_S));
		assertEquals(Math.max(0, Thresholds.WARMUP_S - 20), s.warmupLeftS(50));
		assertEquals("shift moves it", 50, Trace.steady(200).loginAt(30).shift(20).build().loggedInSinceSec());
		assertEquals("a hop does not move it", 30, Trace.steady(200).loginAt(30).hop(100, 302).build()
			.loggedInSinceSec());
	}

	/** {@code noBaselineCollection()}: the ring is empty until the test adds a collection. */
	@Test
	public void noBaselineCollectionLeavesTheRingEmpty()
	{
		final Session empty = Trace.steady(N).noBaselineCollection().build();
		assertChanged(empty, keys());
		assertEquals(-1, empty.gcs.head());
		assertEquals(-1, empty.gcs.heapAfterAt(99_999));
		assertEquals(-1, empty.gcs.lastHeapAfterMb());
		final Session later = Trace.steady(N).noBaselineCollection().gcInferred(60, 300).build();
		assertEquals(Arrays.asList("60000 -1 300"), gcs(later));
		assertEquals("before the first collection", -1, later.gcs.heapAfterAt(59_999));
		assertEquals(300, later.gcs.heapAfterAt(60_000));
	}

	/** {@code shift(k)} moves the baseline collection to {@code k x 1000 - 1000}: the columns before it have none. */
	@Test
	public void shiftMovesTheBaselineCollection()
	{
		final GcRing g = Trace.steady(N).shift(20).build().gcs;
		assertEquals(Arrays.asList("19000 -1 300"), gcs(g));
		assertEquals(-1, g.heapAfterAt(18_999));
		assertEquals(300, g.heapAfterAt(19_000));
		assertEquals("and a trace without one stays without", -1,
			Trace.steady(N).noBaselineCollection().shift(20).build().gcs.head());
	}

	@Test
	public void buildCanBeCalledAgainAndAnswersAFreshEqualSession()
	{
		final Trace trace = Trace.steady(N).frameGap(50, 500, 450).gcPause(60, 0, 150, 350).hop(70, 302);
		final Session a = trace.build();
		final Session b = trace.build();
		assertNotSameSession(a, b);
		assertEquals(seconds(a), seconds(b));
		assertEquals(ticks(a), ticks(b));
		assertEquals(gcs(a), gcs(b));
	}

	@Test
	public void everyTickCarriesTheFlagsAndThePingOfItsSecond()
	{
		final Session s = Trace.steady(N).unfocused(10, 20).rtt(30, 40, 85).loading(45, 300).hop(60, 302)
			.rttNoData(80, 85, NoData.ERROR).build();
		for (long q = s.ticks.tail(); q <= s.ticks.head(); q++)
		{
			final long sec = s.ticks.atMs(q) / 1000;
			assertEquals("tick " + q, s.seconds.flags(sec), s.ticks.flags(q));
			assertEquals("tick " + q, s.seconds.rttMs(sec), s.ticks.rttMs(q));
		}
	}

	@Test
	public void aTraceLongerThanTheRingWrapsIt()
	{
		final Session s = Trace.steady(4000).build();
		assertEquals(3999, s.seconds.head());
		assertEquals("the ring keeps its 3,600 readable seconds", 3600, s.seconds.head() - s.seconds.tail() + 1);
	}

	@Test
	public void aSecondOutsideTheTraceIsRefused()
	{
		final Trace t = Trace.steady(10);
		refused(() -> t.fps(5, 10, 30));
		refused(() -> t.busy(-1, 500));
		refused(() -> t.frameGap(5, 1000, 300));
		refused(() -> t.rtt(6, 5, 40));
		refused(() -> t.rttNoData(1, 2, NoData.NONE));
		refused(() -> t.shift(-1));
	}

	// ---------------------------------------------------------------- each builder changes only what it names

	@Test
	public void fpsChangesTheFrameRateOfItsSecondsOnly()
	{
		final Session s = Trace.steady(N).fps(40, 42, 24).build();
		assertChanged(s, keys().span("frames", 40, 42).span("worstFrameMs", 40, 42));
		for (long sec = 40; sec <= 42; sec++)
		{
			assertEquals(24, s.seconds.frames(sec));
			assertEquals("the frame interval at 24 fps", 42, s.seconds.worstFrameMs(sec));
		}
		assertTickTimingUnchanged(s);
		assertTickFrames(s, 40_000, 43_000, 42);
	}

	@Test
	public void fpsUnderTwentyMakesEveryFrameSlow()
	{
		final Session s = Trace.steady(N).fps(40, 40, 8).build();
		assertChanged(s, keys().span("frames", 40, 40).span("worstFrameMs", 40, 40).span("slowFrames", 40, 40));
		assertEquals(125, s.seconds.worstFrameMs(40));
		assertEquals(8, s.seconds.slowFrames(40));
		assertTickFrames(s, 40_000, 41_000, 125);
	}

	@Test
	public void fpsZeroIsASecondWithNoFrame()
	{
		final Session s = Trace.steady(N).fps(40, 41, 0).build();
		assertChanged(s, keys().span("frames", 40, 41).span("worstFrameMs", 40, 41).span("worstFrameEndMs", 40, 41)
			.span("busyPm", 40, 41).span("worstBusyPm", 40, 41).span("flags", 40, 41));
		assertEquals(Flags.FOCUSED | Flags.NO_FRAMES, s.seconds.flags(40));
		assertEquals(-1, s.seconds.busyPm(41));
		assertTickTimingUnchanged(s);
	}

	@Test
	public void frameGapChangesItsSecondAndTheTicksInsideIt()
	{
		// 50.050 .. 50.500: the tick at 50.400 arrives inside the frame; the ones at 49.800 and 51.000 do not.
		final Session s = Trace.steady(N).frameGap(50, 500, 450).build();
		assertChanged(s, keys().span("worstFrameMs", 50, 50).span("slowFrames", 50, 50));
		assertEquals(450, s.seconds.worstFrameMs(50));
		assertEquals("it ended 500 ms into the second", 500, s.seconds.worstFrameEndMs(50));
		assertEquals(1, s.seconds.slowFrames(50));
		assertTickTimingUnchanged(s);
		assertTickFrames(s, 50_050, 50_501, 450);
	}

	/**
	 * A frame under {@link Thresholds#SLOW_FRAME_MS} is the worst frame of its second but not a slow one: 3.12's
	 * "adds one slow frame when ms is SLOW_FRAME_MS or more", pinned at the line and one below it.
	 */
	@Test
	public void frameGapAddsASlowFrameOnlyFromSlowFrameMs()
	{
		// 50.390 .. 50.420: longer than the steady 22 ms, so the worst frame; the tick at 50.400 is inside it.
		final Session s = Trace.steady(N).frameGap(50, 420, 30).build();
		assertChanged(s, keys().span("worstFrameMs", 50, 50).span("worstFrameEndMs", 50, 50));
		assertEquals(30, s.seconds.worstFrameMs(50));
		assertEquals(420, s.seconds.worstFrameEndMs(50));
		assertEquals("a 30 ms frame is not a slow one", 0, s.seconds.slowFrames(50));
		assertTickTimingUnchanged(s);
		assertTickFrames(s, 50_390, 50_421, 30);

		final int line = Thresholds.SLOW_FRAME_MS;
		assertEquals("one below the line", 0, Trace.steady(N).frameGap(50, 420, line - 1).build().seconds.slowFrames(50));
		assertEquals("at the line", 1, Trace.steady(N).frameGap(50, 420, line).build().seconds.slowFrames(50));
		assertChanged(Trace.steady(N).frameGap(50, 420, line).build(), keys().span("worstFrameMs", 50, 50)
			.span("worstFrameEndMs", 50, 50).span("slowFrames", 50, 50));
	}

	@Test
	public void aFrameLongerThanASecondLeavesTheSecondsItCoversWithNoFrame()
	{
		// 48.800 .. 50.300: second 49 ended no frame at all.
		final Session s = Trace.steady(N).frameGap(50, 300, 1500).build();
		assertChanged(s, keys().span("worstFrameMs", 50, 50).span("worstFrameEndMs", 50, 50)
			.span("slowFrames", 50, 50).span("frames", 49, 49).span("worstFrameMs", 49, 49)
			.span("worstFrameEndMs", 49, 49).span("busyPm", 49, 49).span("worstBusyPm", 49, 49)
			.span("flags", 49, 49));
		assertEquals(0, s.seconds.frames(49));
		assertTrue(Flags.has(s.seconds.flags(49), Flags.NO_FRAMES));
		assertEquals(300, s.seconds.worstFrameEndMs(50));
		assertTickFrames(s, 48_800, 50_301, 1500);
	}

	/**
	 * 3.12: frameGap sets the worst frame and its end when it is at least as long as the second's worst so far. A
	 * shorter frame leaves both alone; a tie keeps the length and moves the end.
	 */
	@Test
	public void aShorterFrameLeavesTheWorstFrameAlone()
	{
		// 50.410 .. 50.420: 10 ms, shorter than the steady 22; no tick arrives inside it.
		final Session s = Trace.steady(N).frameGap(50, 420, 10).build();
		assertChanged(s, keys());
		assertEquals(22, s.seconds.worstFrameMs(50));
		assertEquals(500, s.seconds.worstFrameEndMs(50));
		assertEquals(0, s.seconds.slowFrames(50));
		assertTickTimingUnchanged(s);
		for (long q = s.ticks.tail(); q <= s.ticks.head(); q++)
		{
			assertEquals("tick at " + s.ticks.atMs(q), 22, s.ticks.frameMs(q));
		}
		// 50.398 .. 50.420: a tie. The worst stays 22 and its end moves; the tick at 50.400 carries 22 still.
		final Session tie = Trace.steady(N).frameGap(50, 420, 22).build();
		assertChanged(tie, keys().span("worstFrameEndMs", 50, 50));
		assertEquals(22, tie.seconds.worstFrameMs(50));
		assertEquals(420, tie.seconds.worstFrameEndMs(50));
		assertTickTimingUnchanged(tie);
	}

	/**
	 * A loading frame (contract 6.1, as 3.12 writes it for a trace): a frameGap frame that touches a second with
	 * loading time flags the second it ENDS in with LOADING, whatever the order of the calls. A frame that touches
	 * no loading time flags nothing, and a LOADING flag with no loading time is not loading time.
	 */
	@Test
	public void aFrameThatTouchesLoadingTimeFlagsTheSecondItEndsIn()
	{
		// loading(49, 800); a 1,450 ms frame 49.850 .. 51.300 touches second 49. Second 51, where it ends, gets the
		// flag; second 50, which it covers whole, is a no-frame second with no loading time and no LOADING flag.
		final Session s = Trace.steady(N).loading(49, 800).frameGap(51, 300, 1450).build();
		assertChanged(s, keys().span("loadingMs", 49, 49).span("flags", 49, 51).span("frames", 50, 50)
			.span("worstFrameMs", 50, 51).span("worstFrameEndMs", 50, 51).span("busyPm", 50, 50)
			.span("worstBusyPm", 50, 50).span("slowFrames", 51, 51));
		assertEquals(Flags.FOCUSED | Flags.LOADING, s.seconds.flags(49));
		assertEquals(Flags.FOCUSED | Flags.NO_FRAMES, s.seconds.flags(50));
		assertEquals(Flags.FOCUSED | Flags.LOADING, s.seconds.flags(51));
		assertEquals("the flag, not loading time", 0, s.seconds.loadingMs(51));
		assertEquals("the order of the calls does not matter", seconds(s),
			seconds(Trace.steady(N).frameGap(51, 300, 1450).loading(49, 800).build()));
		assertTickTimingUnchanged(s);

		// 50.850 .. 51.300 touches seconds 50 and 51, neither with loading time: nothing is flagged.
		final Session beside = Trace.steady(N).loading(49, 800).frameGap(51, 300, 450).build();
		assertEquals(Flags.FOCUSED, beside.seconds.flags(51));
		// loading(49, 0) sets the flag and no loading time: the frame 49.850 .. 50.300 is no loading frame.
		final Session noTime = Trace.steady(N).loading(49, 0).frameGap(50, 300, 450).build();
		assertEquals(Flags.FOCUSED | Flags.LOADING, noTime.seconds.flags(49));
		assertEquals(Flags.FOCUSED, noTime.seconds.flags(50));
	}

	@Test
	public void busyChangesTheWorstFramesShareOnly()
	{
		final Session s = Trace.steady(N).busy(50, 700).build();
		assertChanged(s, keys().span("worstBusyPm", 50, 50));
		assertEquals(700, s.seconds.worstBusyPm(50));
		assertEquals("the second's own share is untouched", 400, s.seconds.busyPm(50));
	}

	@Test
	public void busyUnknownChangesBothBusyColumnsEverywhereAndNothingElse()
	{
		final Session s = Trace.steady(N).busyUnknown().build();
		assertChanged(s, keys().span("busyPm", 0, N - 1).span("worstBusyPm", 0, N - 1));
		assertEquals(-1, s.seconds.worstBusyPm(0));
		assertEquals(-1, s.seconds.busyPm(N - 1));
		final Session shifted = Trace.steady(N).busyUnknown().shift(5).build();
		assertEquals("seconds added by a shift are unknown too", -1, shifted.seconds.busyPm(0));
	}

	/**
	 * {@code cpu} writes the PC's CPU and the SECOND's busy share (game % x 10) in its seconds only; -1 is unknown
	 * for either; after {@code busyUnknown} or {@code fps(.., 0)} it writes the busy share again: the last call wins.
	 */
	@Test
	public void cpuChangesItsSecondsOnly()
	{
		final Session s = Trace.steady(N).cpu(40, 42, 96, 100).build();
		assertChanged(s, keys().span("sysCpuPct", 40, 42).span("busyPm", 40, 42));
		assertEquals(96, s.seconds.sysCpuPct(41));
		assertEquals(1000, s.seconds.busyPm(41));
		assertEquals("the worst frame's share is busy()'s", 300, s.seconds.worstBusyPm(41));
		assertEquals("the process's share is procCpu()'s", 40, s.seconds.procCpuPct(41));
		assertTickTimingUnchanged(s);

		final Session unknown = Trace.steady(N).cpu(50, 50, -1, -1).build();
		assertChanged(unknown, keys().span("sysCpuPct", 50, 50).span("busyPm", 50, 50));
		assertEquals(-1, unknown.seconds.sysCpuPct(50));
		assertEquals(-1, unknown.seconds.busyPm(50));

		final Session afterUnknown = Trace.steady(N).busyUnknown().cpu(40, 40, 30, 50).build();
		assertEquals("the last call wins", 500, afterUnknown.seconds.busyPm(40));
		assertEquals(-1, afterUnknown.seconds.busyPm(39));
		assertEquals(-1, afterUnknown.seconds.worstBusyPm(40));
		final Session afterNoFrames = Trace.steady(N).fps(40, 40, 0).cpu(40, 40, 30, 50).build();
		assertEquals(500, afterNoFrames.seconds.busyPm(40));
		assertEquals(0, afterNoFrames.seconds.frames(40));
		final Session beforeNoFrames = Trace.steady(N).cpu(40, 40, 30, 50).fps(40, 40, 0).build();
		assertEquals("the last call wins the other way too", -1, beforeNoFrames.seconds.busyPm(40));
		refused(() -> Trace.steady(N).cpu(40, 40, -2, 50));
		refused(() -> Trace.steady(N).cpu(40, 40, 30, -5));
	}

	@Test
	public void procCpuChangesItsColumnOnly()
	{
		final Session s = Trace.steady(N).procCpu(40, 45, 250).build();
		assertChanged(s, keys().span("procCpuPct", 40, 45));
		assertEquals(250, s.seconds.procCpuPct(45));
		assertChanged(Trace.steady(N).procCpu(50, 50, -1).build(), keys().span("procCpuPct", 50, 50));
		refused(() -> Trace.steady(N).procCpu(40, 40, -3));
	}

	/** A Runtime-only PC: both CPU columns -1 in every second, and in the seconds a shift adds later. */
	@Test
	public void cpuUnknownCoversTheSecondsShiftAdds()
	{
		final Session s = Trace.steady(N).cpuUnknown().build();
		assertChanged(s, keys().span("sysCpuPct", 0, N - 1).span("procCpuPct", 0, N - 1));
		assertEquals(-1, s.seconds.sysCpuPct(0));
		assertEquals(-1, s.seconds.procCpuPct(N - 1));
		assertEquals("the busy columns are busyUnknown()'s", 400, s.seconds.busyPm(0));
		final Session shifted = Trace.steady(N).cpuUnknown().shift(5).build();
		assertEquals("seconds added by a shift are unknown too", -1, shifted.seconds.sysCpuPct(0));
		assertEquals(-1, shifted.seconds.procCpuPct(4));
		assertEquals(400, shifted.seconds.busyPm(0));
		final Session runtimeOnly = Trace.steady(N).cpuUnknown().busyUnknown().shift(5).build();
		for (long sec = 0; sec < N + 5; sec++)
		{
			assertEquals(-1, runtimeOnly.seconds.sysCpuPct(sec));
			assertEquals(-1, runtimeOnly.seconds.busyPm(sec));
		}
		assertEquals("cpu() after cpuUnknown() writes its span", 30,
			Trace.steady(N).cpuUnknown().cpu(40, 40, 30, 50).build().seconds.sysCpuPct(40));
	}

	@Test
	public void playersNpcsAndRegionChangeTheirColumnOnly()
	{
		assertChanged(Trace.steady(N).players(40, 45, 12).build(), keys().span("players", 40, 45));
		assertChanged(Trace.steady(N).npcs(40, 45, 30).build(), keys().span("npcs", 40, 45));
		final Session s = Trace.steady(N).region(40, 45, 12_850).build();
		assertChanged(s, keys().span("region", 40, 45));
		assertEquals(12_850, s.seconds.region(40));
		assertEquals("the top of a region id", 65_535, Trace.steady(N).region(0, 0, 65_535).build().seconds.region(0));
		final Session all = Trace.steady(N).players(10, 10, 200).npcs(10, 10, 45).region(10, 10, 12_342).build();
		assertEquals(200, all.seconds.players(10));
		assertEquals(45, all.seconds.npcs(10));
		assertEquals(12_342, all.seconds.region(10));
		assertTickTimingUnchanged(all);
		refused(() -> Trace.steady(N).players(1, 1, -1));
		refused(() -> Trace.steady(N).npcs(1, 1, -1));
		refused(() -> Trace.steady(N).region(1, 1, 65_536));
		refused(() -> Trace.steady(N).region(1, 1, -1));
	}

	@Test
	public void gcPauseAddsOneKnownPauseAndNothingElse()
	{
		final Session s = Trace.steady(N).gcPause(50, 250, 150, 400).build();
		assertChanged(s, keys());
		assertTickTimingUnchanged(s);
		assertEquals(Arrays.asList("-1000 -1 300", "50250 150 400"), gcs(s));
		assertEquals("second 50, its first to its last ms", 150, s.gcs.longestPauseMs(50_000, 50_999));
		assertEquals(0, s.gcs.longestPauseMs(49_000, 49_999));
	}

	@Test
	public void gcInferredAddsOneCollectionWithNoLengthAtTheSecondsStart()
	{
		final Session s = Trace.steady(N).gcInferred(50, 350).build();
		assertChanged(s, keys());
		assertEquals(Arrays.asList("-1000 -1 300", "50000 -1 350"), gcs(s));
		assertTrue(s.gcs.inferredIn(50_000, 50_999));
		assertFalse("second 49's span ends at its last ms", s.gcs.inferredIn(49_000, 49_999));
		assertEquals(0, s.gcs.longestPauseMs(0, N * 1000 - 1));
	}

	@Test
	public void tickLateWithCatchUpMovesOneTick()
	{
		// Second 51's first tick is 51.000: it comes at 51.100, and 51.600 keeps its time.
		final Session s = Trace.steady(N).tickLate(51, 100, true).build();
		assertChanged(s, keys());
		final List<String> before = timing(Trace.steady(N).build());
		final List<String> after = timing(s);
		assertEquals(before.size(), after.size());
		final int late = 85;
		assertEquals("51100 700 22 35", after.get(late));
		assertEquals("51600 500 22 25", after.get(late + 1));
		for (int i = 0; i < before.size(); i++)
		{
			if (i != late && i != late + 1)
			{
				assertEquals("tick " + i, before.get(i), after.get(i));
			}
		}
	}

	@Test
	public void tickLateWithoutCatchUpMovesEveryLaterTick()
	{
		final Session s = Trace.steady(N).tickLate(51, 100, false).build();
		assertChanged(s, keys());
		final TickRing t = s.ticks;
		assertEquals(51_100, t.atMs(85));
		assertEquals(700, t.gapMs(85));
		for (long q = 86; q <= t.head(); q++)
		{
			assertEquals(600, t.gapMs(q));
			assertEquals(600 * q + 100, t.atMs(q));
		}
		assertTrue(t.late(85));
	}

	@Test
	public void aLateTickThatWouldPassTheNextIsRefused()
	{
		refused(() -> Trace.steady(N).tickLate(51, 600, true));
	}

	/** 3.12: a tick that tickLate moves past the trace's last ms is dropped. */
	@Test
	public void aTickThatTickLateMovesPastTheEndIsDropped()
	{
		// A 10 s trace: 9.000 comes at 9.500, and 9.600 would come at 10.100, past its last ms, so it is dropped.
		final Session s = Trace.steady(10).tickLate(9, 500, false).build();
		assertChanged(s, keys());
		final TickRing t = s.ticks;
		assertEquals("one tick fewer", Trace.steady(10).build().ticks.head() - 1, t.head());
		assertEquals(9_500, t.atMs(t.head()));
		assertEquals("600 + 500 after 8.400", 1_100, t.gapMs(t.head()));
		for (long q = t.tail(); q <= t.head(); q++)
		{
			assertTrue("no tick at or past 10.000: " + t.atMs(q), t.atMs(q) < 10_000);
		}
	}

	@Test
	public void ticksEveryChangesTheTicksOfItsSpanAndThenResumes()
	{
		final Session s = Trace.steady(N).ticksEvery(40, 49, 740).build();
		assertChanged(s, keys());
		final TickRing t = s.ticks;
		boolean afterTheSpan = false;
		int inTheSpan = 0;
		for (long q = t.tail(); q <= t.head(); q++)
		{
			final long at = t.atMs(q);
			if (at < 40_000)
			{
				assertEquals(600 * q, at);
				assertEquals(600, t.gapMs(q));
			}
			else if (at < 50_000)
			{
				assertEquals("tick at " + at, 740, t.gapMs(q));
				inTheSpan++;
			}
			else
			{
				assertEquals("tick at " + at + " after the span", 600, t.gapMs(q));
				afterTheSpan = true;
			}
		}
		assertEquals("10 s at 740 ms", 14, inTheSpan);
		assertTrue(afterTheSpan);
		assertTrue("the grid runs on to the end of the trace", t.atMs(t.head()) >= N * 1000 - 600);
		assertEquals("the span's last is 49.960, so the ticks after move 160 ms LATER", 99_760, t.atMs(t.head()));
		assertEquals("none is dropped and none added", Trace.steady(N).build().ticks.head() - 17 + 14, t.head());

		// A gap that puts no tick in the span (39.600 + 5.000 is past 41.999) is refused, and changes nothing.
		final Trace refusedTrace = Trace.steady(N);
		refused(() -> refusedTrace.ticksEvery(40, 41, 5000));
		assertEquals(ticks(Trace.steady(N).build()), ticks(refusedTrace.build()));
	}

	/**
	 * 3.12: when the ticks after the span move EARLIER, new ticks 600 ms apart fill the hole up to where the old ones
	 * ended - to the trace's last ms when the old grid ran to it, else to the old last tick's time.
	 */
	@Test
	public void ticksEveryFillsTheHoleWhenTheTicksAfterMoveEarlier()
	{
		// 900 ms apart from 39.600: 40.500 .. 49.500. The old 50.400 comes TICK_MS after 49.500, at 50.100, so every
		// tick after the span comes 300 ms EARLIER and the old last, 99.600, lands at 99.300; the grid is carried on
		// to the trace's end with one new tick, 99.900.
		final Session s = Trace.steady(N).ticksEvery(40, 49, 900).build();
		assertChanged(s, keys());
		final TickRing t = s.ticks;
		assertEquals("67 before the span, 11 in it, 83 moved and 1 new", 161, t.head());
		for (long q = 0; q <= 66; q++)
		{
			assertEquals(600 * q, t.atMs(q));
		}
		for (long q = 67; q <= 77; q++)
		{
			assertEquals(40_500 + 900 * (q - 67), t.atMs(q));
			assertEquals(900, t.gapMs(q));
		}
		assertEquals("the span's last tick", 49_500, t.atMs(77));
		assertEquals("the next comes TICK_MS after it", 50_100, t.atMs(78));
		for (long q = 78; q <= t.head(); q++)
		{
			assertEquals(50_100 + 600 * (q - 78), t.atMs(q));
			assertEquals("tick at " + t.atMs(q), 600, t.gapMs(q));
		}
		assertEquals("the hole is filled up to the trace's end", 99_900, t.atMs(t.head()));

		// When the old ticks ended early (none from 90 s on: the last is 89.400), the moved ones end at 89.100 and
		// nothing is added: 89.700 would pass 89.400.
		final Session cut = Trace.steady(N).noTicks(90, 99).ticksEvery(40, 49, 900).build();
		assertEquals(89_100, cut.ticks.atMs(cut.ticks.head()));
		assertEquals("17 replaced by 11", Trace.steady(N).noTicks(90, 99).build().ticks.head() - 6,
			cut.ticks.head());
	}

	/** 3.12: a tick that ticksEvery moves past the trace's last ms is dropped. */
	@Test
	public void aTickThatTicksEveryMovesPastTheEndIsDropped()
	{
		// 1,100 ms apart from 96.600: 97.700 and 98.800. The old 99.000 comes TICK_MS after 98.800, 400 ms LATER,
		// at 99.400; the old 99.600 would come at 100.000, past the last ms of a 100 s trace, so it is dropped.
		final Session s = Trace.steady(N).ticksEvery(97, 98, 1100).build();
		assertChanged(s, keys());
		final TickRing t = s.ticks;
		assertEquals("3 replaced by 2, and 1 dropped", Trace.steady(N).build().ticks.head() - 2, t.head());
		assertEquals(97_700, t.atMs(t.head() - 2));
		assertEquals(1_100, t.gapMs(t.head() - 2));
		assertEquals(98_800, t.atMs(t.head() - 1));
		assertEquals(1_100, t.gapMs(t.head() - 1));
		assertEquals(99_400, t.atMs(t.head()));
		assertEquals(600, t.gapMs(t.head()));
	}

	@Test
	public void noTicksRemovesTheTicksOfItsSpanOnly()
	{
		final Session s = Trace.steady(N).noTicks(40, 45).build();
		assertChanged(s, keys());
		final TickRing t = s.ticks;
		for (long q = t.tail(); q <= t.head(); q++)
		{
			final long at = t.atMs(q);
			assertTrue("tick at " + at, at < 40_000 || at >= 46_000);
			if (at == 46_200)
			{
				assertEquals("from 39.600 to 46.200", 6_600, t.gapMs(q));
			}
		}
		assertEquals(Trace.steady(N).build().ticks.head() - 10, t.head());
	}

	@Test
	public void rttChangesThePingOfItsSecondsOnly()
	{
		final Session s = Trace.steady(N).rtt(40, 45, 85).build();
		assertChanged(s, keys().span("rttMs", 40, 45));
		assertEquals(85, s.seconds.rttMs(40));
		assertEquals(0, s.seconds.rttAgeS(45));
		assertTickTimingUnchanged(s);
	}

	@Test
	public void rttNoDataChangesThePingItsAgeAndItsReason()
	{
		final Session s = Trace.steady(N).rttNoData(40, 45, NoData.UNSUPPORTED).build();
		assertChanged(s, keys().span("rttMs", 40, 45).span("rttAgeS", 40, 45).span("conn", 40, 45));
		assertEquals(-1, s.seconds.rttMs(42));
		assertEquals(NoData.UNSUPPORTED, s.seconds.conn(42));
	}

	@Test
	public void rttStaleKeepsThePingAndAgesIt()
	{
		final Session s = Trace.steady(N).rttStale(40, 45).build();
		assertChanged(s, keys().span("rttAgeS", 40, 45).span("conn", 40, 45));
		assertEquals("the last RTT is kept", 40, s.seconds.rttMs(40));
		assertTrue(s.seconds.rttAgeS(40) > Thresholds.RTT_STALE_S);
		assertEquals(s.seconds.rttAgeS(40) + 5, s.seconds.rttAgeS(45));
		assertEquals(NoData.STALE, s.seconds.conn(45));
	}

	/**
	 * Contract 3.3: {@code conn} is NONE in exactly the seconds whose RTT is fresh ({@code rttMs >= 0} and
	 * {@code 0 <= rttAgeS <= RTT_STALE_S}), whichever builders wrote the ping. So a reader may test either.
	 */
	@Test
	public void connIsNoneExactlyWhereTheRttIsFresh()
	{
		final Session s = Trace.steady(N).loginAt(10).rtt(20, 25, 85).rttNoData(30, 35, NoData.ERROR).rttStale(40, 45)
			.rttNoData(50, 52, NoData.NOT_CONNECTED).rttStale(51, 55).rtt(54, 54, 0).hop(70, 302).disconnect(80)
			.shift(5).build();
		int fresh = 0;
		for (long sec = s.seconds.tail(); sec <= s.seconds.head(); sec++)
		{
			final int rtt = s.seconds.rttMs(sec);
			final int age = s.seconds.rttAgeS(sec);
			final boolean isFresh = rtt >= 0 && age >= 0 && age <= Thresholds.RTT_STALE_S;
			assertEquals("second " + sec, isFresh, s.seconds.conn(sec) == NoData.NONE);
			fresh += isFresh ? 1 : 0;
		}
		assertTrue("the steady seconds are fresh", fresh > N / 2);
		assertEquals("an RTT of 0 ms is fresh", NoData.NONE, s.seconds.conn(59));
		refused(() -> Trace.steady(N).rtt(1, 2, -1));
	}

	/** The detector fills the usuals from the quiet seconds it reads; a trace on its own has none. */
	@Test
	public void aSteadyTraceHasNoUsual()
	{
		final Session s = Trace.steady(N).build();
		assertEquals(0, s.rttUsual.count());
		assertEquals(-1, s.rttUsual.median());
		assertEquals(0, s.rttSession.count());
		assertEquals(0, s.fpsUsual.count());
	}

	/** {@code usual(40)}: this world's RTT usual is known at 40 and nothing else changes. */
	@Test
	public void usualFillsThisWorldsRttUsualOnly()
	{
		final Trace trace = Trace.steady(N).usual(40);
		final Session s = trace.build();
		assertEquals(Thresholds.USUAL_MIN_SAMPLES, s.rttUsual.count());
		assertEquals(40, s.rttUsual.median());
		assertEquals("the session usual is not named", 0, s.rttSession.count());
		assertEquals("nor the frame rate usual", 0, s.fpsUsual.count());
		assertChanged(s, keys());
		assertTickTimingUnchanged(s);
		assertEquals(gcs(Trace.steady(N).build()), gcs(s));
		assertEquals("a fresh session each build, with the usual again", 40, trace.build().rttUsual.median());
		assertEquals("the last call wins", 90, Trace.steady(N).usual(40).usual(90).build().rttUsual.median());
		assertEquals("a shift keeps it", 40, Trace.steady(N).usual(40).shift(10).build().rttUsual.median());
		refused(() -> Trace.steady(N).usual(-1));
	}

	@Test
	public void sentAndResentChangeTheirColumnOnly()
	{
		assertChanged(Trace.steady(N).sent(40, 45, 1600).build(), keys().span("sentUnits", 40, 45));
		final Session s = Trace.steady(N).resent(50, 300).build();
		assertChanged(s, keys().span("resentUnits", 50, 50));
		assertEquals(300, s.seconds.resentUnits(50));
		assertEquals("re-sent is not added to sent", 900, s.seconds.sentUnits(50));
	}

	@Test
	public void heapChangesTheUsedHeapOnly()
	{
		final Session s = Trace.steady(N).heap(40, 45, 700).build();
		assertChanged(s, keys().span("heapUsedMb", 40, 45));
		assertEquals(700, s.seconds.heapUsedMb(45));
		assertEquals("heap after collection is a collection's, not the used heap", 300, s.gcs.heapAfterAt(45_000));
	}

	@Test
	public void loadingChangesItsSecondOnly()
	{
		final Session s = Trace.steady(N).loading(50, 800).build();
		assertChanged(s, keys().span("loadingMs", 50, 50).span("flags", 50, 50));
		assertEquals(800, s.seconds.loadingMs(50));
		assertEquals(Flags.FOCUSED | Flags.LOADING, s.seconds.flags(50));
		assertEquals("the state is not named", State.LOGGED_IN, s.seconds.state(50));
	}

	@Test
	public void hopChangesItsSecondTheWorldAndMasksTheNextFifteenTicks()
	{
		// The fifteen ticks after second 50 arrive 51.000 .. 59.400: seconds 51 .. 59 are masked.
		final Session s = Trace.steady(N).hop(50, 302).build();
		assertChanged(s, keys().span("flags", 50, 59).span("state", 50, 50).span("world", 50, N - 1));
		assertEquals(Flags.FOCUSED | Flags.HOP, s.seconds.flags(50));
		assertEquals(State.HOPPING, s.seconds.state(50));
		assertEquals(416, s.seconds.world(49));
		assertEquals(302, s.seconds.world(N - 1));
		assertEquals(Flags.FOCUSED | Flags.LOGIN_MASK, s.seconds.flags(59));
		assertEquals(Flags.FOCUSED, s.seconds.flags(60));
		assertTickTimingUnchanged(s);
	}

	/**
	 * A one-second lost connection: its second, and - as the tick sampler does when a lost connection ends - the
	 * next fifteen ticks masked, 51.000 .. 59.400, so seconds 51 .. 59 carry LOGIN_MASK. Nothing else moves: no
	 * NOT_LOGGED_IN, no tick time, no login. A trace that ends in the lost second reads not in-game there.
	 */
	@Test
	public void disconnectChangesItsSecondAndMasksTheNextFifteenTicks()
	{
		final Session s = Trace.steady(N).disconnect(50).build();
		assertChanged(s, keys().span("flags", 50, 59).span("state", 50, 50));
		assertEquals(Flags.FOCUSED | Flags.DISCONNECT, s.seconds.flags(50));
		assertEquals(State.CONNECTION_LOST, s.seconds.state(50));
		assertFalse("not in-game", State.inGame(s.seconds.state(50)));
		assertEquals(Flags.FOCUSED | Flags.LOGIN_MASK, s.seconds.flags(51));
		assertEquals(Flags.FOCUSED | Flags.LOGIN_MASK, s.seconds.flags(59));
		assertEquals(Flags.FOCUSED, s.seconds.flags(60));
		int masked = 0;
		for (long q = s.ticks.tail(); q <= s.ticks.head(); q++)
		{
			final boolean inMask = s.ticks.atMs(q) >= 51_000 && s.ticks.atMs(q) <= 59_400;
			assertEquals("tick at " + s.ticks.atMs(q), inMask, Flags.has(s.ticks.flags(q), Flags.LOGIN_MASK));
			masked += inMask ? 1 : 0;
		}
		assertEquals(Thresholds.LOGIN_MASK_TICKS, masked);
		assertTickTimingUnchanged(s);
		assertEquals("the login stays", -Thresholds.WARMUP_S, s.loggedInSinceSec());
		final Session endsThere = Trace.steady(N).disconnect(N - 1).build();
		assertFalse(State.inGame(endsThere.seconds.state(endsThere.lastSec(N))));
	}

	@Test
	public void loginAtPutsTheSecondsBeforeItAtTheLoginScreenAndMasksFifteenTicks()
	{
		// The first fifteen ticks from second 20 arrive 20.400 .. 28.800: seconds 20 .. 28 are masked.
		final Session s = Trace.steady(N).loginAt(20).build();
		assertChanged(s, keys().span("state", 0, 19).span("flags", 0, 28).span("rttMs", 0, 19)
			.span("rttAgeS", 0, 19).span("sentUnits", 0, 19).span("conn", 0, 19));
		assertEquals(State.LOGIN_SCREEN, s.seconds.state(0));
		assertEquals(Flags.FOCUSED | Flags.NOT_LOGGED_IN, s.seconds.flags(19));
		assertEquals(NoData.NOT_LOGGED_IN, s.seconds.conn(19));
		assertEquals(Flags.FOCUSED | Flags.LOGIN_MASK, s.seconds.flags(28));
		assertEquals(Flags.FOCUSED, s.seconds.flags(29));
		assertEquals("no tick before the login", 20_400, s.ticks.atMs(0));
		assertEquals("the first tick has the steady gap", 600, s.ticks.gapMs(0));
		assertEquals("and no cycle jump", -1, s.ticks.cycleJump(0));
	}

	@Test
	public void unfocusedClearsTheFocusOfItsSecondsOnly()
	{
		final Session s = Trace.steady(N).unfocused(40, 45).build();
		assertChanged(s, keys().span("flags", 40, 45));
		assertEquals(0, s.seconds.flags(40));
	}

	@Test
	public void osChangesTheSessionAndTheDefaultSettingsOnly()
	{
		final Trace t = Trace.steady(N).os(Os.LINUX);
		final Session s = t.build();
		assertChanged(s, keys());
		assertTickTimingUnchanged(s);
		assertEquals(Os.LINUX, s.os);
		assertEquals(Os.LINUX, t.settings().os);
	}

	@Test
	public void settingsAreAnsweredAndWriteNoRing()
	{
		final SettingsView v = SettingsView.unknown(512, Os.MAC, MemorySource.RUNTIME);
		final Trace t = Trace.steady(N).settings(v);
		assertSame(v, t.settings());
		assertChanged(t.build(), keys());
		assertEquals(gcs(Trace.steady(N).build()), gcs(t.build()));
	}

	@Test
	public void shiftMovesTheWholeTraceLater()
	{
		final Trace original = Trace.steady(N).frameGap(50, 500, 450).gcPause(50, 0, 150, 400).hop(70, 302)
			.noTicks(80, 82);
		final Session before = original.build();
		final Session after = original.shift(20).build();
		assertEquals(N + 20, original.seconds());
		final Map<String, String> a = seconds(before);
		final Map<String, String> b = seconds(after);
		for (Map.Entry<String, String> e : a.entrySet())
		{
			final String[] key = e.getKey().split("@");
			final String moved = key[0] + "@" + (Integer.parseInt(key[1]) + 20);
			assertEquals(moved, e.getValue(), b.get(moved));
		}
		final Map<String, String> steady = seconds(Trace.steady(20).build());
		for (Map.Entry<String, String> e : steady.entrySet())
		{
			assertEquals("the new first seconds are steady: " + e.getKey(), e.getValue(), b.get(e.getKey()));
		}
		// Every old tick is 20 s later with the same gap and frame; the ones before it are steady.
		final List<String> old = timing(before);
		final List<String> now = timing(after);
		final int filler = now.size() - old.size();
		for (int i = 0; i < old.size(); i++)
		{
			final String[] o = old.get(i).split(" ");
			final String[] m = now.get(filler + i).split(" ");
			assertEquals(Long.parseLong(o[0]) + 20_000, Long.parseLong(m[0]));
			assertEquals(o[1], m[1]);
			assertEquals(o[2], m[2]);
		}
		for (int i = 0; i < filler; i++)
		{
			assertEquals("the filler ticks are 600 apart", "600", now.get(i).split(" ")[1]);
		}
		assertEquals("the join is one tick apart", "600", now.get(filler).split(" ")[1]);
		assertEquals(Arrays.asList("19000 -1 300", "70000 150 400"), gcs(after));
	}

	/**
	 * 3.12: after loginAt, the seconds a shift adds are at the login screen too, with no tick, so every second
	 * before the login still is. {@code loginAt(20).shift(10)} is then {@code loginAt(30)} of a trace 10 s longer,
	 * second for second.
	 */
	@Test
	public void shiftAfterLoginAtKeepsEverySecondBeforeTheLoginAtTheLoginScreen()
	{
		final Session s = Trace.steady(N).loginAt(20).shift(10).build();
		assertEquals(30, s.loggedInSinceSec());
		for (long sec = 0; sec < 30; sec++)
		{
			assertEquals("state @" + sec, State.LOGIN_SCREEN, s.seconds.state(sec));
			assertTrue("NOT_LOGGED_IN @" + sec, Flags.has(s.seconds.flags(sec), Flags.NOT_LOGGED_IN));
			assertEquals(NoData.NOT_LOGGED_IN, s.seconds.conn(sec));
			assertEquals(-1, s.seconds.rttMs(sec));
			assertEquals(0, s.seconds.sentUnits(sec));
		}
		assertEquals(State.LOGGED_IN, s.seconds.state(30));
		assertEquals(seconds(Trace.steady(N + 10).loginAt(30).build()), seconds(s));
		assertEquals("no tick before the login: the old first, 20.400, is now 30.400", 30_400,
			s.ticks.atMs(s.ticks.tail()));
		assertEquals(0, s.ticks.tail());
		assertEquals("still the first tick: the steady gap", 600, s.ticks.gapMs(0));
		assertEquals(-1, s.ticks.cycleJump(0));
		assertEquals("the same ticks, 10 s later", Trace.steady(N).loginAt(20).build().ticks.head(), s.ticks.head());
	}

	// ---------------------------------------------------------------- helpers

	/** The per-second columns of the changed session differ from a steady one of its length at exactly these keys. */
	private static void assertChanged(Session changed, Keys expected)
	{
		final Session steady = Trace.steady((int) (changed.seconds.head() + 1)).build();
		final Map<String, String> a = seconds(steady);
		final Map<String, String> b = seconds(changed);
		final Set<String> diff = new TreeSet<>();
		final Set<String> all = new TreeSet<>(a.keySet());
		all.addAll(b.keySet());
		for (String k : all)
		{
			if (!Objects.equals(a.get(k), b.get(k)))
			{
				diff.add(k);
			}
		}
		assertEquals(expected.set, diff);
	}

	private static void assertTickTimingUnchanged(Session changed)
	{
		final List<String> steady = timing(Trace.steady((int) (changed.seconds.head() + 1)).build());
		final List<String> now = timing(changed);
		assertEquals(steady.size(), now.size());
		for (int i = 0; i < steady.size(); i++)
		{
			final String[] a = steady.get(i).split(" ");
			final String[] b = now.get(i).split(" ");
			assertEquals("tick " + i + " arrives when it did", a[0], b[0]);
			assertEquals("tick " + i + " has its gap", a[1], b[1]);
		}
	}

	/** Ticks arriving in {@code fromMs .. toMs - 1} carry {@code frameMs}; every other tick the steady 22. */
	private static void assertTickFrames(Session s, int fromMs, int toMs, int frameMs)
	{
		int inside = 0;
		for (long q = s.ticks.tail(); q <= s.ticks.head(); q++)
		{
			final long at = s.ticks.atMs(q);
			final boolean in = at >= fromMs && at < toMs;
			assertEquals("tick at " + at, in ? frameMs : 22, s.ticks.frameMs(q));
			inside += in ? 1 : 0;
		}
		assertTrue("at least one tick is inside", inside > 0);
	}

	private static Map<String, String> seconds(Session s)
	{
		final Map<String, String> m = new TreeMap<>();
		final SecondRing r = s.seconds;
		for (long sec = r.tail(); sec <= r.head(); sec++)
		{
			m.put("frames@" + sec, "" + r.frames(sec));
			m.put("worstFrameMs@" + sec, "" + r.worstFrameMs(sec));
			m.put("worstFrameEndMs@" + sec, "" + r.worstFrameEndMs(sec));
			m.put("slowFrames@" + sec, "" + r.slowFrames(sec));
			m.put("busyPm@" + sec, "" + r.busyPm(sec));
			m.put("worstBusyPm@" + sec, "" + r.worstBusyPm(sec));
			m.put("loadingMs@" + sec, "" + r.loadingMs(sec));
			m.put("state@" + sec, "" + r.state(sec));
			m.put("flags@" + sec, "" + r.flags(sec));
			m.put("world@" + sec, "" + r.world(sec));
			m.put("players@" + sec, "" + r.players(sec));
			m.put("npcs@" + sec, "" + r.npcs(sec));
			m.put("region@" + sec, "" + r.region(sec));
			m.put("rttMs@" + sec, "" + r.rttMs(sec));
			m.put("rttAgeS@" + sec, "" + r.rttAgeS(sec));
			m.put("sentUnits@" + sec, "" + r.sentUnits(sec));
			m.put("resentUnits@" + sec, "" + r.resentUnits(sec));
			m.put("heapUsedMb@" + sec, "" + r.heapUsedMb(sec));
			m.put("procCpuPct@" + sec, "" + r.procCpuPct(sec));
			m.put("sysCpuPct@" + sec, "" + r.sysCpuPct(sec));
			m.put("conn@" + sec, r.conn(sec).name());
		}
		return m;
	}

	/** Each tick as "atMs gapMs frameMs cycleJump". */
	private static List<String> timing(Session s)
	{
		final List<String> out = new ArrayList<>();
		final TickRing t = s.ticks;
		for (long q = t.tail(); q <= t.head(); q++)
		{
			out.add(t.atMs(q) + " " + t.gapMs(q) + " " + t.frameMs(q) + " " + t.cycleJump(q));
		}
		return out;
	}

	/** Each tick in full, the derived rtt and flags included. */
	private static List<String> ticks(Session s)
	{
		final List<String> out = new ArrayList<>();
		final TickRing t = s.ticks;
		for (long q = t.tail(); q <= t.head(); q++)
		{
			out.add(t.atMs(q) + " " + t.gapMs(q) + " " + t.frameMs(q) + " " + t.cycleJump(q) + " " + t.rttMs(q)
				+ " " + t.flags(q));
		}
		return out;
	}

	/** Each collection as "startMs durationMs heapAfterMb". */
	private static List<String> gcs(Session s)
	{
		return gcs(s.gcs);
	}

	private static List<String> gcs(GcRing g)
	{
		final List<String> out = new ArrayList<>();
		for (long q = g.tail(); q <= g.head(); q++)
		{
			out.add(g.startMs(q) + " " + g.durationMs(q) + " " + g.heapAfterMb(q));
		}
		return out;
	}

	private static void assertNotSameSession(Session a, Session b)
	{
		assertNotSame("two builds are two sessions", a, b);
		assertNotSame("with rings of their own", a.seconds, b.seconds);
	}

	private static void refused(Runnable r)
	{
		try
		{
			r.run();
			fail("refused expected");
		}
		catch (IllegalArgumentException expected)
		{
			// the builder said why
		}
	}

	private static Keys keys()
	{
		return new Keys();
	}

	/** The expected changed keys, "column@second". */
	private static final class Keys
	{
		final Set<String> set = new TreeSet<>();

		Keys span(String column, int from, int to)
		{
			for (int s = from; s <= to; s++)
			{
				set.add(column + "@" + s);
			}
			return this;
		}
	}
}
