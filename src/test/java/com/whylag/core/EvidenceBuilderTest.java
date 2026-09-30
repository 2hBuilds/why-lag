package com.whylag.core;

import org.junit.Test;
import static com.whylag.core.VerdictCauseTest.USUAL;
import static com.whylag.core.VerdictCauseTest.event;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * The evidence of contract 6.3, field by field, against traces whose numbers are worked by hand.
 */
public class EvidenceBuilderTest
{
	private static final SettingsView DEFAULT = Trace.steady(1).settings();

	/** FPS Control with its focused limit off and its unfocused limit on at 10, on the CPU renderer (3.7). */
	private static SettingsView unfocusedLimit()
	{
		return new SettingsView(Renderer.CPU, true, false, 0, true, 10, false, "", 0, 0, "", 0, 60, Os.WINDOWS, "");
	}

	private static Evidence ofEvent(Trace t, int from, int to, Trigger first, Trigger... more)
	{
		final Session s = t.build();
		return EvidenceBuilder.forEvent(s, event(s, 0, from, to, first, more), t.settings());
	}

	private static Evidence ofWindow(Trace t, int nowSec)
	{
		return EvidenceBuilder.forCondition(t.build(), nowSec, t.settings());
	}

	@Test
	public void aSteadySpanAndASteadyWindow()
	{
		final Evidence e = ofEvent(Trace.steady(200).usual(USUAL), 120, 129, Trigger.TICK_OFF);
		assertTrue(e.event);
		assertEquals(Trigger.TICK_OFF, e.first);
		assertFalse(e.disconnect);
		assertEquals(-1, e.disconnectSec);
		assertEquals(-1, e.lastTickSec);
		assertEquals(22, e.frameGapMs);
		assertEquals(200, e.frameLimitMs);
		assertEquals(0, e.loadMs);
		assertEquals(0, e.loads);
		assertEquals(50, e.capFps);
		assertEquals(20, e.capIntervalMs);
		assertFalse(e.capSelfSet);
		assertFalse(e.capWaits);
		assertEquals("the client", e.capLabel);
		assertEquals(50, e.fps);
		assertEquals(20, e.frameMedianMs);
		assertTrue(e.framesClean);
		assertTrue(e.rttKnown);
		assertEquals(40, e.rtt);
		assertEquals(40, e.rttMin);
		assertEquals(40, e.rttMax);
		assertEquals(40, e.rttUsual);
		assertEquals(Evidence.NO, e.rttSpike);
		assertEquals("seconds 118 to 131", 14 * 900, e.sent);
		assertEquals(0, e.resent);
		assertEquals(0, e.resentPm);
		assertFalse(e.resentCounts);
		assertEquals("120.0 to 129.6 s", 17, e.ticks);
		assertEquals(0, e.tickLate);
		assertEquals(0, e.tickEarly);
		assertEquals(578, e.tickMedianMs);
		assertEquals(600, e.tickWorstMs);
		assertEquals(600, e.noTickMs);
		assertEquals(0, e.tickOffMs);
		assertEquals(10, e.durationS);
		assertEquals(416, e.world);
		assertEquals(-1, e.beforeRtt);
		assertEquals(Evidence.NO_DATA, e.beforeRttSpike);
		assertEquals(-1, e.beforeResentPm);

		final Evidence w = ofWindow(Trace.steady(200).usual(USUAL), 200);
		assertFalse(w.event);
		assertEquals(50, w.fps);
		assertEquals(22, w.frameGapMs);
		assertEquals(100, w.ticksInWindow);
		assertEquals(600, w.tickWindowMedianMs);
		assertEquals(40, w.rtt);
		assertEquals(40, w.rttUsual);
		assertEquals(Evidence.NO, w.rttSpike);
		assertEquals(0, w.lowFpsS);
		assertEquals(-1, w.lowFps);
		assertEquals(0, w.capHeldS);
		assertFalse(w.eventInWindow);
		assertEquals(416, w.world);
		assertEquals(0, w.durationS);
		assertEquals(0, w.loadMs);

		// A window at an earlier nowSec replays the trace as it stood then (3.5).
		final Trace later = Trace.steady(200).usual(USUAL).rtt(150, 199, 180);
		assertEquals(40, ofWindow(later, 150).rttMax);
		assertEquals(180, ofWindow(later, 151).rttMax);
		// Before any second is complete there is nothing to read.
		final Evidence none = ofWindow(Trace.steady(200), 0);
		assertEquals(-1, none.fps);
		assertFalse(none.capSelfSet);
	}

	/** The worked case of 6.2: loading 1000, 1000 and 400 ms in seconds 100 to 102, LONG_LOAD in 103. */
	@Test
	public void loadMsReachesBackToTheRunThatFiredLongLoad()
	{
		final Trace t = Trace.steady(200).usual(USUAL).loading(100, 1000).loading(101, 1000).loading(102, 400);
		final Evidence e = ofEvent(t, 103, 103, Trigger.LONG_LOAD);
		assertEquals(2400, e.loadMs);
		assertEquals(1, e.loads);

		// The run is summed WHOLE, its seconds after the event's start and before it alike.
		final Evidence inside = ofEvent(t, 101, 101, Trigger.FRAME_GAP);
		assertEquals(2400, inside.loadMs);
		// A run that ended two seconds before the event is not its load.
		final Evidence after = ofEvent(t, 104, 106, Trigger.FRAME_GAP);
		assertEquals(0, after.loadMs);
		assertEquals("but it is one of the loads of the last ten minutes", 1, after.loads);

		// Two runs that touch the span are both summed; a gap of one second makes them two.
		final Trace two = Trace.steady(200).usual(USUAL).loading(100, 1000).loading(101, 700).loading(103, 300)
			.loading(104, 200);
		final Evidence both = ofEvent(two, 102, 103, Trigger.FRAME_GAP);
		assertEquals(1700 + 500, both.loadMs);
		assertEquals(2, both.loads);
	}

	@Test
	public void frameMedianMsIsTheIntervalAtTheMedianRate()
	{
		final Evidence fifty = ofEvent(Trace.steady(200), 120, 124, Trigger.TICK_OFF);
		assertEquals(50, fifty.fps);
		assertEquals(20, fifty.frameMedianMs);

		final Evidence slow = ofEvent(Trace.steady(200).fps(120, 124, 24), 120, 124, Trigger.TICK_OFF);
		assertEquals(24, slow.fps);
		assertEquals("ceil(1000 / 24)", 42, slow.frameMedianMs);

		final Evidence none = ofEvent(Trace.steady(200).fps(120, 124, 0), 120, 124, Trigger.FRAME_GAP);
		assertEquals(0, none.fps);
		assertEquals(1000, none.frameMedianMs);

		// The MEDIAN rate, not the mean and not the worst: two slow seconds of five.
		final Evidence mixed = ofEvent(Trace.steady(200).fps(120, 121, 10), 120, 124, Trigger.TICK_OFF);
		assertEquals(50, mixed.fps);
		assertEquals(20, mixed.frameMedianMs);

		final Evidence gone = new Evidence();
		assertEquals("no fps, no interval", -1, gone.frameMedianMs);
	}

	@Test
	public void worldIsTheFirstSeconds()
	{
		final Trace t = Trace.steady(200).usual(USUAL).hop(125, 302);
		final Session s = t.build();
		final LagEvent e = event(s, 0, 120, 130, Trigger.TICK_OFF);
		assertEquals(416, EvidenceBuilder.forEvent(s, e, t.settings()).world);
		assertEquals("the same number as LagEvent.world", e.world, EvidenceBuilder.forEvent(s, e, t.settings()).world);
		assertEquals(302, EvidenceBuilder.forEvent(s, event(s, 1, 125, 130, Trigger.TICK_OFF), t.settings()).world);
		assertEquals(416, new VerdictEngine().judgeEvent(s, e, t.settings()).world);

		// A condition's world is the newest second's.
		assertEquals(416, EvidenceBuilder.forCondition(s, 125, t.settings()).world);
		assertEquals(302, EvidenceBuilder.forCondition(s, 126, t.settings()).world);
		assertEquals(302, EvidenceBuilder.forCondition(s, 200, t.settings()).world);
	}

	@Test
	public void aMedianOfAnEvenCountIsTheLowerMiddle()
	{
		assertEquals(-1, EvidenceBuilder.median(new int[0], 0));
		assertEquals(7, EvidenceBuilder.median(new int[] {7}, 1));
		assertEquals(3, EvidenceBuilder.median(new int[] {9, 3}, 2));
		assertEquals(5, EvidenceBuilder.median(new int[] {9, 3, 5}, 3));
		assertEquals(5, EvidenceBuilder.median(new int[] {9, 3, 7, 5}, 4));
		assertEquals("only the first n count", 3, EvidenceBuilder.median(new int[] {9, 3, 1, 1}, 2));
		final int[] kept = {9, 3, 7, 5};
		EvidenceBuilder.median(kept, 4);
		assertEquals("the values are not moved", 9, kept[0]);

		// In the evidence: two seconds, 30 and 50 frames, 40 and 80 ms of ping.
		final Trace t = Trace.steady(200).usual(USUAL).fps(120, 120, 30).rtt(121, 121, 80);
		final Evidence e = ofEvent(t, 120, 121, Trigger.TICK_OFF);
		assertEquals(30, e.fps);
		assertEquals(40, e.rtt);
		assertEquals(40, e.rttMin);
		assertEquals(80, e.rttMax);
		// As Usual.median() does.
		final Usual usual = new Usual(100);
		for (int i = 0; i < Thresholds.USUAL_MIN_SAMPLES / 2; i++)
		{
			usual.add(30);
			usual.add(50);
		}
		assertEquals(30, usual.median());

		// Four ticks in the span, gaps 850, 350, 600, 600 less the 22 ms frame: 328, 578, 578, 828.
		final Evidence ticks = ofEvent(Trace.steady(200).usual(USUAL).tickLate(120, 250, true), 120, 121,
			Trigger.TICK_OFF);
		assertEquals(4, ticks.ticks);
		assertEquals(578, ticks.tickMedianMs);
		assertEquals(850, ticks.tickWorstMs);
	}

	@Test
	public void lastTickIsBeforeTheEndOfTheDisconnectSecond()
	{
		// Steady ticks come every 600 ms from 0: one lands at 120.0 s exactly, the first ms of second 120.
		final Evidence e = ofEvent(Trace.steady(200).usual(USUAL).disconnect(119), 119, 119, Trigger.DISCONNECT);
		assertTrue(e.disconnect);
		assertEquals(119, e.disconnectSec);
		assertEquals("the tick at 119.4 s; the one at 120.0 s is not before the end of second 119", 119,
			e.lastTickSec);

		final Evidence in = ofEvent(Trace.steady(200).usual(USUAL).disconnect(120), 120, 120, Trigger.DISCONNECT);
		assertEquals("the ticks at 120.0 and 120.6 s are inside the disconnect's second", 120, in.lastTickSec);

		final Evidence quiet = ofEvent(Trace.steady(200).usual(USUAL).noTicks(112, 120).disconnect(120), 113, 120,
			Trigger.NO_TICK, Trigger.DISCONNECT);
		assertEquals("the tick at 111.6 s", 111, quiet.lastTickSec);

		// The FIRST second with the flag, and the look is the D1_LOOK_S seconds that END at the last tick.
		final Trace bad = Trace.steady(200).usual(USUAL).noTicks(112, 125).rtt(100, 101, 300).rtt(102, 111, 60)
			.resent(101, 5000).rtt(112, 125, 900).resent(115, 5000).disconnect(120).disconnect(125);
		final Evidence b = ofEvent(bad, 113, 125, Trigger.NO_TICK, Trigger.DISCONNECT);
		assertEquals(120, b.disconnectSec);
		assertEquals(111, b.lastTickSec);
		assertEquals("seconds 102 to 111 alone", 60, b.beforeRtt);
		assertEquals(60, b.beforeRttMax);
		assertEquals(Evidence.NO, b.beforeRttSpike);
		assertEquals(0, b.beforeResentPm);

		// A masked tick still counts as the last tick.
		final Evidence masked = ofEvent(Trace.steady(200).usual(USUAL).hop(115, 302).disconnect(120), 120, 120,
			Trigger.DISCONNECT);
		assertEquals(120, masked.lastTickSec);

		// No tick at all before it.
		final Evidence never = ofEvent(Trace.steady(200).usual(USUAL).noTicks(0, 120).disconnect(120), 120, 120,
			Trigger.DISCONNECT);
		assertEquals(-1, never.lastTickSec);
		assertEquals(-1, never.beforeResentPm);
		assertEquals(Evidence.NO_DATA, never.beforeRttSpike);
	}

	@Test
	public void theWorstFrameIsTheEarliestOfATie()
	{
		final Trace t = Trace.steady(200).usual(USUAL).settings(unfocusedLimit())
			.frameGap(120, 600, 450).unfocused(120, 120).frameGap(122, 500, 450);
		final Evidence e = ofEvent(t, 120, 122, Trigger.FRAME_GAP);
		assertEquals(450, e.frameGapMs);
		assertEquals("the earlier second was unfocused: FPS Control's unfocused limit", 10, e.capFps);
		assertEquals(100, e.capIntervalMs);
		assertTrue(e.capSelfSet);
		assertEquals(200, e.frameLimitMs);

		// One ms longer, the later frame is the worst, and everything comes from ITS second.
		final Trace later = Trace.steady(200).usual(USUAL).settings(unfocusedLimit())
			.frameGap(120, 600, 450).unfocused(120, 120).frameGap(122, 500, 451);
		final Evidence l = ofEvent(later, 120, 122, Trigger.FRAME_GAP);
		assertEquals(451, l.frameGapMs);
		assertEquals("focused: the client's own cap", 50, l.capFps);
		assertFalse(l.capSelfSet);

		// The window follows the same rule for its worst frame.
		final Evidence w = ofWindow(t, 125);
		assertEquals(450, w.frameGapMs);
	}

	@Test
	public void anEventReadsTheCapOfItsWorstFramesSecond()
	{
		final SettingsView settings = unfocusedLimit();
		// Alt-tabbed in seconds 120 to 124; the freeze is in the unfocused second 122.
		final Trace t = Trace.steady(200).usual(USUAL).settings(settings).unfocused(120, 124)
			.frameGap(122, 600, 480);
		final Session s = t.build();
		final Evidence e = EvidenceBuilder.forEvent(s, event(s, 0, 119, 126, Trigger.FRAME_GAP), settings);
		assertEquals(10, e.capFps);
		assertEquals(100, e.capIntervalMs);
		assertEquals(settings.frameGapLimitMs(false), e.frameLimitMs);
		assertTrue(e.capSelfSet);
		assertTrue(e.capWaits);
		assertEquals("FPS Control (unfocused)", e.capLabel);

		// The same event with the freeze in a FOCUSED second reads the focused cap.
		final Trace f = Trace.steady(200).usual(USUAL).settings(settings).unfocused(120, 124)
			.frameGap(125, 600, 480);
		final Session fs = f.build();
		final Evidence fe = EvidenceBuilder.forEvent(fs, event(fs, 0, 119, 126, Trigger.FRAME_GAP), settings);
		assertEquals(50, fe.capFps);
		assertEquals(20, fe.capIntervalMs);
		assertEquals(settings.frameGapLimitMs(true), fe.frameLimitMs);
		assertFalse(fe.capSelfSet);
		assertFalse(fe.capWaits);

		// A condition reads the NEWEST second's focus, wherever its worst frame lies.
		final Evidence unfocusedNow = EvidenceBuilder.forCondition(s, 123, settings);
		assertEquals("newest second 122, unfocused", 10, unfocusedNow.capFps);
		assertTrue(unfocusedNow.capSelfSet);
		assertEquals("FPS Control (unfocused)", unfocusedNow.capLabel);
		final Evidence focusedNow = EvidenceBuilder.forCondition(s, 130, settings);
		assertEquals("its worst frame is still the unfocused second's", 480, focusedNow.frameGapMs);
		assertEquals("newest second 129, focused", 50, focusedNow.capFps);
		assertFalse(focusedNow.capSelfSet);
		assertEquals("the client", focusedNow.capLabel);
	}

	@Test
	public void aConditionLeavesOutTheSecondsMaskedByTheirFlags()
	{
		// Login at 45 (seconds 40 to 44 of the window are the login screen, 45 to 53 carry the login mask), a hop
		// at 70 (71 to 79 masked), a load in 85. Each masked second holds a number that would show if it counted.
		final Trace t = Trace.steady(100).usual(USUAL).loginAt(45)
			.rtt(50, 50, 810).fps(50, 50, 7)
			.hop(70, 302).rtt(70, 70, 800).fps(70, 70, 6)
			.loading(85, 1000).rtt(85, 85, 900).fps(85, 85, 5)
			.rtt(86, 86, 95).frameGap(86, 990, 90);
		final Session s = t.build();
		assertTrue(Flags.has(s.seconds.flags(42), Flags.NOT_LOGGED_IN));
		assertTrue(Flags.has(s.seconds.flags(50), Flags.LOGIN_MASK));
		assertTrue(Flags.has(s.seconds.flags(70), Flags.HOP));
		assertTrue(Flags.has(s.seconds.flags(85), Flags.LOADING));
		assertFalse("the second after the load is masked by Masks alone", Flags.masked(s.seconds.flags(86)));

		final Evidence w = EvidenceBuilder.forCondition(s, 100, t.settings());
		assertEquals("the load-tail second counts", 90, w.frameGapMs);
		assertEquals(95, w.rttMax);
		assertEquals(40, w.rttMin);
		assertEquals(50, w.fps);
		assertTrue(w.framesClean);
		assertEquals(302, w.world);
		assertEquals(0, w.lowFpsS);

		// An EVENT over the same seconds reads every one of them, masked or not.
		final Evidence e = EvidenceBuilder.forEvent(s, event(s, 0, 40, 99, Trigger.FRAME_GAP), t.settings());
		assertEquals(200, e.frameGapMs);
		assertEquals(900, e.rttMax);

		// The window's ticks are those without LOGIN_MASK.
		int counted = 0;
		for (long q = s.ticks.tail(); q <= s.ticks.head(); q++)
		{
			if (s.ticks.atMs(q) >= 40_000 && !Flags.has(s.ticks.flags(q), Flags.LOGIN_MASK))
			{
				counted++;
			}
		}
		assertEquals(counted, w.ticksInWindow);
		// 100 ticks land in seconds 40 to 99; 8 of them before the login at 45 s (removed), and fifteen are masked
		// after the login and fifteen after the hop.
		assertEquals(100 - 8 - 15 - 15, counted);

		// With every second of the window masked there is nothing to read, and no condition scores.
		final Trace loading = Trace.steady(100).usual(USUAL);
		for (int sec = 0; sec < 100; sec++)
		{
			loading.loading(sec, 1000);
		}
		final Evidence none = ofWindow(loading, 100);
		assertEquals(-1, none.fps);
		assertEquals(-1, none.frameGapMs);
		assertFalse(none.rttKnown);
		assertEquals(null, Rules.bestCondition(none));

		// A lost connection in an unmasked second of the window is in the window; one before it is not.
		assertTrue(ofWindow(Trace.steady(100).disconnect(70), 100).disconnect);
		assertFalse(ofWindow(Trace.steady(100).disconnect(30), 100).disconnect);
		// Like every other field of a condition, the disconnect leaves out a second masked by its flags (6.3): a
		// lost connection in a LOADING second, or under the login mask of a hop, counts for nothing in the window.
		final Trace loadingLoss = Trace.steady(100).loading(70, 500).disconnect(70);
		final Session ll = loadingLoss.build();
		assertTrue(Flags.has(ll.seconds.flags(70), Flags.DISCONNECT));
		assertTrue(Flags.has(ll.seconds.flags(70), Flags.LOADING));
		assertFalse("a DISCONNECT second that is also LOADING",
			EvidenceBuilder.forCondition(ll, 100, loadingLoss.settings()).disconnect);
		final Trace maskedLoss = Trace.steady(100).hop(65, 302).disconnect(70);
		final Session ml = maskedLoss.build();
		assertTrue(Flags.has(ml.seconds.flags(70), Flags.DISCONNECT));
		assertTrue("the hop's login mask covers 66 to 74", Flags.has(ml.seconds.flags(70), Flags.LOGIN_MASK));
		assertFalse("a DISCONNECT second under the login mask",
			EvidenceBuilder.forCondition(ml, 100, maskedLoss.settings()).disconnect);
		// An EVENT over the same seconds reads them, masked or not.
		final Evidence lossEvent = EvidenceBuilder.forEvent(ml, event(ml, 0, 65, 72, Trigger.DISCONNECT),
			maskedLoss.settings());
		assertTrue(lossEvent.disconnect);
		assertEquals(70, lossEvent.disconnectSec);
		assertTrue(EvidenceBuilder.forEvent(ll, event(ll, 0, 70, 70, Trigger.DISCONNECT), loadingLoss.settings())
			.disconnect);
	}

	@Test
	public void theRunsSkipMaskedSecondsAndEndAtTheOtherKind()
	{
		// No cap set by the player. Back from 99: 30, 30, 30, 30, (95 loading), 20, (93 no frames), 38, 38, 38,
		// and second 89 at 50 ends the run.
		final Trace slow = Trace.steady(100).fps(96, 99, 30).fps(95, 95, 5).loading(95, 1000).fps(94, 94, 20)
			.fps(93, 93, 0).fps(90, 92, 38);
		final Evidence e = ofWindow(slow, 100);
		assertEquals(8, e.lowFpsS);
		assertEquals("the lower middle of 20, 30, 30, 30, 30, 38, 38, 38", 30, e.lowFps);
		assertEquals(0, e.capHeldS);
		assertTrue(e.lowFps < Thresholds.FPS_WARN);

		// Replayed one second earlier the run is one shorter.
		assertEquals(7, ofWindow(slow, 99).lowFpsS);
		// A second at FPS_WARN ends it; one under it does not.
		assertEquals(4, ofWindow(Trace.steady(100).fps(90, 99, 30).fps(95, 95, 40), 100).lowFpsS);
		assertEquals(10, ofWindow(Trace.steady(100).fps(90, 99, 30).fps(95, 95, 39), 100).lowFpsS);
		// The run is counted up to the NEWEST second: a fast newest second means no run.
		assertEquals(0, ofWindow(Trace.steady(100).fps(90, 98, 30), 100).lowFpsS);
		assertEquals(-1, ofWindow(Trace.steady(100).fps(90, 98, 30), 100).lowFps);

		// Under a cap of 20 set by the player: 20 +- CAP_MATCH_FPS is capped, anything else under 40 is slow.
		final SettingsView capped = VerdictCauseTest.fpsControl(20);
		final Trace cappedThenSlow = Trace.steady(100).settings(capped).fps(90, 94, 20).fps(95, 99, 30);
		final Evidence s = ofWindow(cappedThenSlow, 100);
		assertEquals("a capped second ends a slow run", 5, s.lowFpsS);
		assertEquals(30, s.lowFps);
		assertEquals(0, s.capHeldS);

		final Trace slowThenCapped = Trace.steady(100).settings(capped).fps(80, 89, 30).fps(90, 99, 22)
			.fps(93, 93, 0).fps(96, 96, 18);
		final Evidence c = ofWindow(slowThenCapped, 100);
		assertEquals("a slow second ends a capped run; the second with no frame is skipped", 9, c.capHeldS);
		assertEquals(0, c.lowFpsS);
		assertEquals(-1, c.lowFps);
		assertTrue(c.capSelfSet);
		assertEquals("23 is not within 2 of 20: slow", 1,
			ofWindow(Trace.steady(100).settings(capped).fps(90, 98, 20).fps(99, 99, 23), 100).lowFpsS);

		// The same 20 frames a second with NO cap set by the player are slow, never capped.
		final Evidence free = ofWindow(Trace.steady(100).fps(90, 99, 20), 100);
		assertEquals(10, free.lowFpsS);
		assertEquals(0, free.capHeldS);

		// A run is at most WINDOW_S.
		assertEquals(Thresholds.WINDOW_S, ofWindow(Trace.steady(200).fps(0, 199, 30), 200).lowFpsS);
		assertEquals(Thresholds.WINDOW_S,
			ofWindow(Trace.steady(200).settings(capped).fps(0, 199, 20), 200).capHeldS);
	}

	@Test
	public void lateAndEarlyTicksAreTickRings()
	{
		final int line = Thresholds.TICK_OFF_MS;
		final Trace t = Trace.steady(200).usual(USUAL).tickLate(120, line + 150, true).tickLate(124, line + 10, true)
			.tickLate(126, line + 40, true);
		final Session s = t.build();
		final Evidence e = EvidenceBuilder.forEvent(s, event(s, 0, 120, 129, Trigger.TICK_OFF), t.settings());

		int late = 0;
		int early = 0;
		int exact = 0;
		int ticks = 0;
		for (long q = s.ticks.tail(); q <= s.ticks.head(); q++)
		{
			if (s.ticks.atMs(q) < 120_000 || s.ticks.atMs(q) >= 130_000)
			{
				continue;
			}
			ticks++;
			final boolean off = s.ticks.corrected(q) >= Thresholds.TICK_OFF_MS;
			late += off && s.ticks.late(q) ? 1 : 0;
			early += off && s.ticks.early(q) ? 1 : 0;
			exact += s.ticks.gapMs(q) == Thresholds.TICK_MS ? 1 : 0;
		}
		assertEquals(ticks, e.ticks);
		assertEquals(late, e.tickLate);
		assertEquals(early, e.tickEarly);
		// line + 150 and line + 40 late are off by line + 128 and line + 18 after the 22 ms frame; line + 10 late is
		// off by line - 12, under the line (TICK_OFF_MS 250 since the first live look).
		assertEquals(2, e.tickLate);
		assertEquals(2, e.tickEarly);
		assertTrue("most ticks of the span came exactly one tick apart", exact >= 10);
		assertEquals("and a gap of exactly TICK_MS is neither", ticks - 6, exact);

		final Evidence steady = ofEvent(Trace.steady(200), 120, 129, Trigger.TICK_OFF);
		assertEquals(0, steady.tickLate);
		assertEquals(0, steady.tickEarly);

		// A long frame explains a late tick (C5): line + 150 late under a 450 ms frame is not off.
		final Trace frame = Trace.steady(200).usual(USUAL).tickLate(120, line + 150, true).frameGap(120, 450, 450);
		final Evidence f = ofEvent(frame, 120, 121, Trigger.FRAME_GAP);
		assertEquals(0, f.tickLate);
		assertEquals("the tick after it, line + 150 early under an ordinary frame, is off", 1, f.tickEarly);
	}

	@Test
	public void theStretchWithNoTick()
	{
		// Ticks at 119.4 and 122.4 s. The span ends at 123.0 s.
		final Evidence e = ofEvent(Trace.steady(200).usual(USUAL).noTicks(120, 121), 120, 122, Trigger.NO_TICK);
		assertEquals(1, e.ticks);
		assertEquals(3000, e.tickWorstMs);
		assertEquals(3000, e.noTickMs);
		// The largest corrected is 3000 - 600 - 22 = 2378; noTickMs - TICK_MS is 2400, and the larger is printed.
		assertEquals(2400, e.tickOffMs);

		// Where the frame time does not matter the two agree: a tick 700 late under a 22 ms frame is off by 678,
		// and the stretch of 1,300 ms less one tick is 700.
		final Evidence late = ofEvent(Trace.steady(200).usual(USUAL).tickLate(120, 700, false), 120, 120,
			Trigger.TICK_OFF);
		assertEquals(1300, late.noTickMs);
		assertEquals(700, late.tickOffMs);
		// And with no stretch to speak of, the largest corrected is what is left: a steady span is 0 off.
		assertEquals(0, ofEvent(Trace.steady(200), 120, 129, Trigger.TICK_OFF).tickOffMs);

		// An open stretch: no tick in the span at all, the newest one 2.6 s before its end.
		final Evidence open = ofEvent(Trace.steady(200).usual(USUAL).noTicks(120, 129), 120, 121, Trigger.NO_TICK);
		assertEquals(0, open.ticks);
		assertEquals(-1, open.tickWorstMs);
		assertEquals(-1, open.tickMedianMs);
		assertEquals(2600, open.noTickMs);
		assertEquals("noTickMs - TICK_MS", 2000, open.tickOffMs);

		final Evidence never = ofEvent(Trace.steady(200).noTicks(0, 199), 120, 121, Trigger.NO_TICK);
		assertEquals(-1, never.noTickMs);
		assertEquals(-1, never.tickOffMs);
	}

	@Test
	public void resendsAreSummedOverTheSpanAndTheLookEitherSide()
	{
		final Trace t = Trace.steady(200).usual(USUAL).resent(117, 1000).resent(118, 10).resent(120, 20)
			.resent(123, 30).resent(124, 1000);
		final Evidence e = ofEvent(t, 120, 121, Trigger.RESENT);
		assertEquals("seconds 118 to 123", 6 * 900, e.sent);
		assertEquals(60, e.resent);
		assertEquals("60 x 1000 / 5400, rounded down", 11, e.resentPm);
		assertTrue(e.resentCounts);
		assertFalse("the RESENT bit, not the share, is resentCounts",
			ofEvent(t, 120, 121, Trigger.TICK_OFF).resentCounts);

		final Evidence nothing = ofEvent(Trace.steady(200).sent(0, 199, 0).resent(120, 50), 120, 120,
			Trigger.RESENT);
		assertEquals(0, nothing.sent);
		assertEquals("nothing sent: the share is 0", 0, nothing.resentPm);
		final Evidence all = ofEvent(Trace.steady(200).sent(0, 199, 10).resent(120, 5000), 120, 120,
			Trigger.RESENT);
		assertEquals("at most 1000", 1000, all.resentPm);
	}

	@Test
	public void theConditionsSpikeAndTheWindowsEvents()
	{
		// The window's spike is the spike line on its highest fresh RTT, with no click filter.
		assertEquals(Evidence.NO, ofWindow(Trace.steady(200).usual(USUAL).rtt(150, 150, 89), 200).rttSpike);
		assertEquals(Evidence.YES, ofWindow(Trace.steady(200).usual(USUAL).rtt(150, 150, 90), 200).rttSpike);
		assertEquals("no usual", Evidence.NO_DATA, ofWindow(Trace.steady(200).rtt(150, 150, 400), 200).rttSpike);
		assertEquals("no fresh RTT", Evidence.NO_DATA,
			ofWindow(Trace.steady(200).usual(USUAL).rttStale(100, 199), 200).rttSpike);
		// A usual of 10: twice it is 20, but the line also asks for 50 ms over it.
		assertEquals(Evidence.NO, EvidenceBuilder.spikeLine(59, 10));
		assertEquals(Evidence.YES, EvidenceBuilder.spikeLine(60, 10));
		// A usual of 100: 50 over is 150, but the line also asks for twice the usual.
		assertEquals(Evidence.NO, EvidenceBuilder.spikeLine(199, 100));
		assertEquals(Evidence.YES, EvidenceBuilder.spikeLine(200, 100));

		// W1c's need: no event, open or closed, in the window (seconds 140 to 199 at nowSec 200).
		final Trace t = Trace.steady(200).usual(USUAL);
		final Session s = t.build();
		assertFalse(EvidenceBuilder.forCondition(s, 200, t.settings()).eventInWindow);
		s.events.add(event(s, 0, 130, 139, Trigger.TICK_OFF));
		assertFalse("it ended the second before the window", EvidenceBuilder.forCondition(s, 200, t.settings())
			.eventInWindow);
		assertTrue(EvidenceBuilder.forCondition(s, 199, t.settings()).eventInWindow);
		s.events.add(new VerdictCauseTest.LagEventSpec(1, 190, 191, Trigger.TICK_OFF).open(true).on(s));
		assertTrue("an open one counts", EvidenceBuilder.forCondition(s, 200, t.settings()).eventInWindow);
		final Session later = t.build();
		later.events.add(new VerdictCauseTest.LagEventSpec(1, 190, 191, Trigger.TICK_OFF).open(true).on(later));
		assertFalse("one that lies after the newest second does not",
			EvidenceBuilder.forCondition(later, 190, t.settings()).eventInWindow);
		assertTrue(EvidenceBuilder.forCondition(later, 191, t.settings()).eventInWindow);

		// The log keeps an OPEN event as opened handed it in (3.4, 3.6): its endSec is its first trigger second and
		// does not move while it stays open. One that opened at 100 and is still open at nowSec 200 is still running,
		// so it reaches the window, 140 to 199, though its logged span, 100 .. 100, ended long before it.
		final Session stillOpen = t.build();
		final LagEvent open = new VerdictCauseTest.LagEventSpec(0, 100, 100, Trigger.TICK_OFF).open(true)
			.on(stillOpen);
		assertEquals(100, open.endSec);
		stillOpen.events.add(open);
		assertTrue("an open event older than the window",
			EvidenceBuilder.forCondition(stillOpen, 200, t.settings()).eventInWindow);
		assertTrue(EvidenceBuilder.forCondition(stillOpen, 101, t.settings()).eventInWindow);
		assertFalse("before its first second it is not yet in the window",
			EvidenceBuilder.forCondition(stillOpen, 100, t.settings()).eventInWindow);
		// The same event CLOSED at 100 ended before the window.
		final Session closedAt100 = t.build();
		closedAt100.events.add(event(closedAt100, 0, 100, 100, Trigger.TICK_OFF));
		assertFalse(EvidenceBuilder.forCondition(closedAt100, 200, t.settings()).eventInWindow);
		assertTrue(EvidenceBuilder.forCondition(closedAt100, 159, t.settings()).eventInWindow);
		assertFalse(EvidenceBuilder.forCondition(closedAt100, 161, t.settings()).eventInWindow);
		// A closed event older than the window, then the open one, as the log holds them.
		final Session both = t.build();
		both.events.add(event(both, 0, 50, 55, Trigger.TICK_OFF));
		both.events.add(new VerdictCauseTest.LagEventSpec(1, 100, 100, Trigger.TICK_OFF).open(true).on(both));
		assertTrue(EvidenceBuilder.forCondition(both, 200, t.settings()).eventInWindow);
	}

	@Test
	public void aSpanTheRingNoLongerHoldsHasNoData()
	{
		final Trace t = Trace.steady(Thresholds.SECONDS + 500).usual(USUAL);
		final Session s = t.build();
		assertTrue(s.seconds.tail() > 200);
		final LagEvent old = event(s, 0, 100, 110, Trigger.FRAME_GAP);
		final Evidence e = EvidenceBuilder.forEvent(s, old, t.settings());
		assertEquals(-1, e.frameGapMs);
		assertEquals(-1, e.fps);
		assertFalse(e.rttKnown);
		assertEquals(Evidence.NO_DATA, e.rttSpike);
		final Verdict v = new VerdictEngine().judgeEvent(s, old, t.settings());
		assertEquals(Cause.NOT_SURE, v.cause);
		assertEquals(null, v.alsoA);
	}
}
