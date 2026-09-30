package com.whylag.core;

import org.junit.Test;
import static com.whylag.core.VerdictCauseTest.USUAL;
import static com.whylag.core.VerdictCauseTest.card;
import static com.whylag.core.VerdictCauseTest.event;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * The near misses of contract section 7 (L4): every skeptic trap where a trace LOOKS like one cause and is another
 * (C2, C3, C4, C5, C9, C21), the designed "Can't tell", and the three condition cases.
 */
public class VerdictNearMissTest
{
	private final VerdictEngine judge = new VerdictEngine();

	/** C3: three late deliveries, each caught up, do not make a slow world: the median gap stays 600. */
	@Test
	public void threeLateDeliveriesAreNotASlowWorld()
	{
		final int late = Thresholds.TICK_OFF_MS + 150;
		final Trace t = Trace.steady(200).usual(USUAL).tickLate(120, late, true).tickLate(123, late, true)
			.tickLate(126, late, true);
		final Session s = t.build();
		final LagEvent e = event(s, 0, 120, 126, Trigger.TICK_OFF);
		final Evidence ev = EvidenceBuilder.forEvent(s, e, t.settings());
		assertEquals(3, ev.tickLate);
		assertEquals(3, ev.tickEarly);
		assertTrue("enough ticks for W1", ev.ticks >= Thresholds.SLOW_WORLD_MIN_TICKS);
		assertTrue("but their median is an ordinary tick", ev.tickMedianMs < Thresholds.SLOW_WORLD_MEDIAN_MS);
		assertEquals(0, Rules.W1.score(ev));

		// Late ticks with no stop are not "No ticks" since the first live look (N6 needs NO_TICK): can't tell.
		assertEquals(0, Rules.N6.score(ev));
		final Verdict v = judge.judgeEvent(s, e, t.settings());
		assertEquals(Cause.NOT_SURE, v.cause);
		assertNotEquals(Cause.SLOW_WORLD, v.cause);
		assertEquals(Level.WARN, v.level);
	}

	/** C2: loss on the way DOWN shows as a late tick with nothing re-sent and a calm ping: it cannot be placed. */
	@Test
	public void downloadLossIsCantTell()
	{
		final Trace t = Trace.steady(200).usual(USUAL).tickLate(120, 700, false);
		final Session s = t.build();
		final LagEvent e = event(s, 0, 120, 120, Trigger.TICK_OFF);
		final Verdict v = judge.judgeEvent(s, e, t.settings());
		// One late tick is no stop: not "No ticks" since the first live look (N6 needs NO_TICK), and amber.
		assertEquals(Cause.NOT_SURE, v.cause);
		assertEquals(Confidence.CANT_TELL, v.confidence);
		assertEquals("Can't tell", v.confidence.word());
		assertEquals(Group.UNSURE, v.cause.group());
		assertEquals(Level.WARN, v.level);
		final Evidence ev = EvidenceBuilder.forEvent(s, e, t.settings());
		assertEquals("a late tick is no stop", 0, Rules.N6.score(ev));
		assertEquals("nothing was re-sent: it is not upload loss", 0, Rules.N3.score(ev));
		assertEquals(0, Rules.N2.score(ev));
		assertEquals(0, Rules.W1.score(ev));
	}

	/**
	 * The designed near miss of 6.3: W1 with no support (85) against N6 (75). Since the first live look N6 needs the
	 * NO_TICK trigger, so the event carries one; without it the same ticks are W1 alone.
	 */
	@Test
	public void bareSlowWorldAgainstDeliveryGapIsCantTell()
	{
		final Trace t = Trace.steady(200).usual(USUAL).ticksEvery(120, 125, 900);
		final Session s = t.build();
		assertEquals(Cause.SLOW_WORLD, judge.judgeEvent(s, event(s, 1, 120, 125, Trigger.TICK_OFF), t.settings())
			.cause);
		final LagEvent e = event(s, 0, 120, 125, Trigger.TICK_OFF, Trigger.NO_TICK);
		final Evidence ev = EvidenceBuilder.forEvent(s, e, t.settings());
		assertEquals("six seconds: no support", 85, Rules.W1.score(ev));
		assertEquals(75, Rules.N6.score(ev));

		final Verdict v = judge.judgeEvent(s, e, t.settings());
		assertEquals(Cause.NOT_SURE, v.cause);
		assertEquals(Confidence.CANT_TELL, v.confidence);
		assertEquals("can't tell is never a red lag (the first live look)", Level.WARN, v.level);
		assertEquals("the lower rank first", Cause.SLOW_WORLD, v.alsoA);
		assertEquals(Cause.DELIVERY_GAP, v.alsoB);
		assertEquals("Can't tell yet", v.headline);
		assertEquals("It was the world or your line or the server.", v.proof);
		assertEquals("Wait for it to happen again.", v.fix);
		assertEquals(0, v.eventId);
		assertEquals(6, v.durationS);

		// Ten seconds of it: the support holds, 95 against 75, and W1 wins by 20.
		final Trace longer = Trace.steady(200).usual(USUAL).ticksEvery(120, 129, 900);
		final Session ls = longer.build();
		assertEquals(Cause.SLOW_WORLD, judge.judgeEvent(ls, event(ls, 1, 120, 129, Trigger.TICK_OFF),
			longer.settings()).cause);
	}

	/** C4: whatever else its span holds, an event that holds a disconnect is D1. */
	@Test
	public void eventHoldingADisconnectIsD1()
	{
		final Trace t = Trace.steady(200).usual(USUAL).frameGap(118, 600, 480).resent(119, 90).disconnect(120);
		final Session s = t.build();
		final LagEvent e = event(s, 0, 118, 120, Trigger.FRAME_GAP, Trigger.RESENT, Trigger.DISCONNECT);
		final Evidence ev = EvidenceBuilder.forEvent(s, e, t.settings());
		assertTrue(ev.disconnect);
		assertEquals(120, ev.disconnectSec);
		assertEquals(200, Rules.D1.score(ev));
		assertEquals("excluded by the disconnect", 0, Rules.N3.score(ev));

		final Verdict v = judge.judgeEvent(s, e, t.settings());
		assertEquals(Cause.DISCONNECT, v.cause);
		assertEquals(Confidence.SURE, v.confidence);
		assertEquals("Connection lost at 20:54", v.headline);
	}

	/** D1 covers only the event whose SPAN holds the disconnect (6.3): an earlier event keeps its own verdict. */
	@Test
	public void eventThatClosedBeforeADisconnectKeepsItsVerdict()
	{
		final Trace stall = Trace.steady(200).usual(USUAL).frameGap(100, 600, 480);
		final Session before = stall.build();
		final Verdict alone = judge.judgeEvent(before, event(before, 0, 100, 100, Trigger.FRAME_GAP),
			stall.settings());
		assertEquals(Cause.CLIENT_BUSY, alone.cause);

		final Trace t = Trace.steady(200).usual(USUAL).frameGap(100, 600, 480).disconnect(110);
		final Session s = t.build();
		final LagEvent first = event(s, 0, 100, 100, Trigger.FRAME_GAP);
		final LagEvent second = event(s, 1, 110, 110, Trigger.DISCONNECT);
		final Verdict v = judge.judgeEvent(s, first, t.settings());
		assertEquals("the disconnect ten seconds later is not in its span", Cause.CLIENT_BUSY, v.cause);
		assertTrue(v.sameAs(alone));
		assertFalse(EvidenceBuilder.forEvent(s, first, t.settings()).disconnect);
		assertEquals(Cause.DISCONNECT, judge.judgeEvent(s, second, t.settings()).cause);

		// On the card: the stall shows when it closes and is not judged again. Its show (EVENT_SHOW_S 10 since
		// 2026-09-29) ends at 110, so the second of the lost connection finds the card Smooth when it leaves the
		// state, and leaving a state is a change: the hold keeps the card until 122, after the disconnect event's own
		// show has ended (110 + 10), so that event does not reach the card either.
		final VerdictEngine engine = new VerdictEngine();
		final LagEvent firstJudged = first.withVerdict(v);
		final LagEvent secondJudged = second.withVerdict(judge.judgeEvent(s, second, t.settings()));
		for (int now = 1; now <= 140; now++)
		{
			if (now == 106)
			{
				s.events.add(firstJudged);
			}
			if (now == 117)
			{
				s.events.add(secondJudged);
			}
			final Verdict shown = engine.current(s, now, s.wallMsOf(now), t.settings());
			if (now >= 106 && now <= 110)
			{
				assertEquals("at " + now, Cause.CLIENT_BUSY, shown.cause);
			}
			if (now == 111)
			{
				assertEquals("the second of the lost connection is not in-game", Answer.HEAD_NOT_LOGGED_IN,
					shown.headline);
			}
			if (now >= 112)
			{
				// Back at once, and the stall's show is over: Smooth. The card changed at 112 (it left "Not logged
				// in"), so the disconnect (in the log at 117) waits out a hold that outlasts its own show.
				assertEquals("at " + now, Cause.ALL_CLEAR, shown.cause);
			}
		}
		assertEquals(Cause.CLIENT_BUSY, s.events.byId(0).verdict.cause);
	}

	/** C9: under a cap of 4 frames a second a 250 ms frame is the cap, not a stall. */
	@Test
	public void capAtFourFpsIsF1NotAFreeze()
	{
		final SettingsView capped = VerdictCauseTest.fpsControl(4);
		final Trace t = Trace.steady(200).usual(USUAL).fps(100, 199, 4).settings(capped);
		final Session s = t.build();
		assertEquals(375, capped.frameGapLimitMs(true));

		final LagEvent e = event(s, 0, 150, 150, Trigger.FRAME_GAP);
		final Evidence ev = EvidenceBuilder.forEvent(s, e, capped);
		assertEquals(250, ev.frameGapMs);
		assertEquals(375, ev.frameLimitMs);
		assertEquals("a 250 ms frame under the cap's limit is no freeze", 0, Rules.S3.score(ev));
		assertNotEquals(Cause.CLIENT_BUSY, judge.judgeEvent(s, e, capped).cause);

		final Verdict shown = card(s, capped, 200);
		assertEquals(Cause.FRAME_CAP, shown.cause);
		assertEquals("Frame rate is capped at 4", shown.headline);
		assertEquals(Confidence.SURE, shown.confidence);

		// The same frames with no cap set by the player are a stall of the client.
		final Session free = Trace.steady(200).usual(USUAL).fps(100, 199, 4).build();
		final SettingsView plain = Trace.steady(1).settings();
		assertEquals(Cause.CLIENT_BUSY, judge.judgeEvent(free, event(free, 0, 150, 150, Trigger.FRAME_GAP),
			plain).cause);
	}

	/** C21: a ping spike beside a re-send is lost packets, not an unsteady line. */
	@Test
	public void pingSpikeWithAResendIsN3NotN2()
	{
		final Trace t = VerdictCauseTest.n2Trace().resent(120, 90);
		final Session s = t.build();
		final LagEvent e = event(s, 0, 120, 121, Trigger.TICK_OFF, Trigger.RTT_SPIKE, Trigger.RESENT);
		final Evidence ev = EvidenceBuilder.forEvent(s, e, t.settings());
		assertEquals("a re-send excludes N2", 0, Rules.N2.score(ev));
		assertEquals("both supports", 110, Rules.N3.score(ev));
		final Verdict v = judge.judgeEvent(s, e, t.settings());
		assertEquals(Cause.UPLOAD_LOSS, v.cause);
		assertEquals(Confidence.LIKELY, v.confidence);
	}

	/** C5: at a low frame rate the ticks' timing is the client's own pacing; N2 may not read it. */
	@Test
	public void lowFpsBlocksN2()
	{
		final Trace t = Trace.steady(200).usual(USUAL).fps(110, 130, 30).rtt(120, 120, 400).rtt(121, 121, 30)
			.tickLate(120, Thresholds.TICK_OFF_MS + 150, true);
		final Session s = t.build();
		final LagEvent e = event(s, 0, 120, 121, Trigger.TICK_OFF, Trigger.RTT_SPIKE);
		final Evidence ev = EvidenceBuilder.forEvent(s, e, t.settings());
		assertEquals(30, ev.fps);
		assertEquals("ceil(1000 / 30), not under LOW_FPS_FRAME_MS", 34, ev.frameMedianMs);
		assertEquals(Evidence.YES, ev.rttSpike);
		assertEquals(0, Rules.N2.score(ev));
		assertNotEquals(Cause.PING_JUMPY, judge.judgeEvent(s, e, t.settings()).cause);

		// The N2 trace itself, at 50 frames a second, is N2.
		final VerdictCauseTest.Case n2 = VerdictCauseTest.n2();
		assertEquals(Cause.PING_JUMPY, n2.verdict().cause);
	}

	/**
	 * W1 needs a steady ping: a ping that doubled under the spike line is not the world's. It sent the trace to N6
	 * before the first live look; N6 needs a real stop (NO_TICK) since, so nothing answers and the judge can't tell.
	 */
	@Test
	public void pingRoseWithSlowTicksCannotBeTold()
	{
		final Trace t = Trace.steady(200).usual(USUAL).ticksEvery(120, 140, 740).rtt(120, 140, 85);
		final Session s = t.build();
		final LagEvent e = event(s, 0, 120, 140, Trigger.TICK_OFF);
		final Evidence ev = EvidenceBuilder.forEvent(s, e, t.settings());
		assertEquals(85, ev.rtt);
		assertEquals("85 is under the spike line of a usual of 40", Evidence.NO, ev.rttSpike);
		assertEquals(0, Rules.W1.score(ev));

		assertEquals(0, Rules.N6.score(ev));
		final Verdict v = judge.judgeEvent(s, e, t.settings());
		assertEquals(Cause.NOT_SURE, v.cause);
		assertEquals(Level.WARN, v.level);
		assertNull(v.alsoA);

		// With the ping at its usual the same ticks are the world's.
		final Trace calm = Trace.steady(200).usual(USUAL).ticksEvery(120, 140, 740);
		final Session cs = calm.build();
		assertEquals(Cause.SLOW_WORLD, judge.judgeEvent(cs, event(cs, 1, 120, 140, Trigger.TICK_OFF),
			calm.settings()).cause);
	}

	/**
	 * The GPU plugin at its defaults (GpuPluginConfig at tag runelite-parent-1.12.37: unlockFps true, vsyncMode OFF,
	 * fpsTarget 60, drawDistance 50, MSAA_2, extended map loading 3) caps at its target of 60 by line 3 of the cap
	 * rule, which paces by waiting, interval 16 ms (6.3's note). A 500 ms freeze is far past two intervals.
	 */
	@Test
	public void s3AtGpuDefaultsForALongFreeze()
	{
		final SettingsView gpu = new SettingsView(Renderer.GPU, false, false, 0, false, 0, true, "OFF", 60,
			50, "MSAA_2", 3, 60, Os.WINDOWS, "");
		assertEquals(CapSource.GPU_TARGET, gpu.capSource(true));
		assertEquals(60, gpu.capFps(true));
		assertTrue(gpu.capSource(true).waits());
		assertEquals(16, gpu.capIntervalMs(true));
		final Trace t = Trace.steady(200).usual(USUAL).frameGap(120, 600, 500).settings(gpu);
		final Session s = t.build();
		final LagEvent e = event(s, 0, 120, 120, Trigger.FRAME_GAP);
		final Evidence ev = EvidenceBuilder.forEvent(s, e, gpu);
		assertTrue(ev.capWaits);
		assertEquals(16, ev.capIntervalMs);

		final Verdict v = judge.judgeEvent(s, e, gpu);
		assertEquals(Cause.CLIENT_BUSY, v.cause);
		assertEquals(Confidence.HINT, v.confidence);
		assertEquals("A 500 ms freeze. Connection and world were fine.", v.proof);
		assertEquals("Turn plugins off one at a time; try more memory for RuneLite.", v.fix);
	}

	/** Within CAP_WAIT_FACTOR cap intervals a waiting cap explains a gap, and S3 is barred. */
	@Test
	public void s3IsBarredInsideTwoCapIntervals()
	{
		final SettingsView capped = VerdictCauseTest.fpsControl(4);
		assertTrue(capped.capSource(true).waits());
		assertEquals(250, capped.capIntervalMs(true));

		final Trace inside = Trace.steady(200).usual(USUAL).frameGap(120, 600, 450).settings(capped);
		final Session s = inside.build();
		final LagEvent e = event(s, 0, 120, 120, Trigger.FRAME_GAP);
		final Evidence ev = EvidenceBuilder.forEvent(s, e, capped);
		assertEquals("over the frame limit of 375", Evidence.YES, Rules.FRAME_OVER_LIMIT.test(ev));
		assertEquals("and the cap waits, inside two intervals of 250 ms", Evidence.YES,
			Rules.CAP_EXPLAINS_THE_GAP.test(ev));
		assertEquals("but inside two intervals of 250 ms", 0, Rules.S3.score(ev));
		assertNotEquals(Cause.CLIENT_BUSY, judge.judgeEvent(s, e, capped).cause);

		final Trace past = Trace.steady(200).usual(USUAL).frameGap(120, 600, 501).settings(capped);
		final Session ps = past.build();
		assertEquals("past two intervals", Cause.CLIENT_BUSY,
			judge.judgeEvent(ps, event(ps, 1, 120, 120, Trigger.FRAME_GAP), capped).cause);
	}

	/** What happened AFTER the last tick is no evidence about the home line (6.4). */
	@Test
	public void resendsAfterTheLastTickDoNotBlameTheLine()
	{
		final Trace t = Trace.steady(200).usual(USUAL).noTicks(100, 110).resent(102, 400).resent(105, 400)
			.disconnect(110);
		final Session s = t.build();
		final LagEvent e = event(s, 0, 101, 110, Trigger.NO_TICK, Trigger.RESENT, Trigger.DISCONNECT);
		final Evidence ev = EvidenceBuilder.forEvent(s, e, t.settings());
		assertEquals(110, ev.disconnectSec);
		assertEquals("the tick at 99.6 s", 99, ev.lastTickSec);
		assertEquals("the span's own re-sends are bad", true, ev.resentPm >= Thresholds.RESENT_PER_MILLE);
		assertEquals("the ten seconds before the last tick are clean", 0, ev.beforeResentPm);
		assertEquals(Evidence.NO, ev.beforeRttSpike);

		final Verdict v = judge.judgeEvent(s, e, t.settings());
		assertEquals(Cause.DISCONNECT, v.cause);
		assertEquals("Ping and re-sends were fine just before.", v.proof);
		assertEquals("Wait a minute, then log in again.", v.fix);
	}

	/** X never says that nothing moved: with no candidate its proof names what the first trigger saw. */
	@Test
	public void xNamesWhatMoved()
	{
		// A 90 ms frame: no stall rule reaches the frame limit.
		final Trace freeze = Trace.steady(200).usual(USUAL).frameGap(120, 290, 90);
		final Session fs = freeze.build();
		final Verdict f = judge.judgeEvent(fs, event(fs, 0, 120, 120, Trigger.FRAME_GAP), freeze.settings());
		assertCantTellNamingNothing(f);
		assertEquals("A 90 ms freeze. Its cause was not measured.", f.proof);

		// The low-frame-rate trace of lowFpsBlocksN2: ticks off, no rule answers.
		final Trace ticks = Trace.steady(200).usual(USUAL).fps(110, 130, 30).rtt(120, 120, 400)
			.rtt(121, 121, 30).tickLate(120, 250, true);
		final Session ts = ticks.build();
		final Verdict k = judge.judgeEvent(ts, event(ts, 1, 120, 121, Trigger.TICK_OFF, Trigger.RTT_SPIKE),
			ticks.settings());
		assertCantTellNamingNothing(k);
		// The tick came 250 ms late: a stretch of 850 ms with no tick, 250 more than one tick (tickOffMs, 6.3).
		assertEquals("Ticks ran 250 ms off. Cause not measured.", k.proof);

		// A spike alone, as it would be with RTT_SPIKE_OPENS.
		final Trace spike = Trace.steady(200).usual(USUAL).rtt(120, 120, 400);
		final Session ss = spike.build();
		final Verdict p = judge.judgeEvent(ss, event(ss, 2, 120, 120, Trigger.RTT_SPIKE), spike.settings());
		assertCantTellNamingNothing(p);
		assertEquals("The connection wobbled. Cause not measured.", p.proof);

		// With no fresh RTT in the span the proof says so, because it fits.
		final Trace blind = Trace.steady(200).frameGap(120, 290, 90).rttNoData(0, 199, NoData.UNSUPPORTED);
		final Session bs = blind.build();
		final Verdict b = judge.judgeEvent(bs, event(bs, 3, 120, 120, Trigger.FRAME_GAP), blind.settings());
		assertCantTellNamingNothing(b);
		assertEquals("A 90 ms freeze. Its cause was not measured. No ping data.", b.proof);

		for (Trigger first : Trigger.values())
		{
			final Verdict any = judge.judgeEvent(fs, event(fs, 9, 150, 150, first), freeze.settings());
			assertFalse(first + ": " + any.proof, any.proof.contains("Nothing measured"));
			assertFalse(first + ": " + any.proof, any.proof.toLowerCase().contains("nothing moved"));
			assertFalse(any.proof.isEmpty());
		}
	}

	/** 3.7's case: alt-tabbed under FPS Control's unfocused limit of 10, its focused limit off, the CPU renderer. */
	@Test
	public void unfocusedUnderTheUnfocusedLimitIsF1()
	{
		final SettingsView settings = new SettingsView(Renderer.CPU, true, false, 0, true, 10, false, "", 0, 0, "",
			0, 60, Os.WINDOWS, "");
		final Trace t = Trace.steady(200).usual(USUAL).fps(100, 199, 10).unfocused(100, 199).settings(settings);
		final Session s = t.build();
		final VerdictEngine engine = new VerdictEngine();
		int firstF1 = -1;
		for (int now = 1; now <= 200; now++)
		{
			final Verdict v = engine.current(s, now, s.wallMsOf(now), settings);
			if (now >= 101)
			{
				assertNotEquals("never slow drawing, at " + now, Cause.SLOW_DRAWING, v.cause);
				final Verdict direct = new VerdictEngine().conditionNow(s, now, settings);
				assertTrue("no step's winner is F2, at " + now, direct == null || direct.cause != Cause.SLOW_DRAWING);
			}
			if (v.cause == Cause.FRAME_CAP && firstF1 < 0)
			{
				firstF1 = now;
			}
			if (now >= 121)
			{
				assertEquals("F1 from 121 at the latest, at " + now, Cause.FRAME_CAP, v.cause);
				assertEquals("Frame rate is capped at 10", v.headline);
				assertEquals("Set by FPS Control (unfocused). Not lag.", v.proof);
				assertEquals(Level.WARN, v.level);
			}
		}
		assertTrue("the cap must first hold CONDITION_HOLD_S, then win as long: " + firstF1, firstF1 > 110);

		// Read as focused the same seconds would be slow drawing: the focus of the second is what decides.
		final Session focused = Trace.steady(200).usual(USUAL).fps(100, 199, 10).build();
		assertEquals(Cause.SLOW_DRAWING, card(focused, settings, 200).cause);
	}

	/** F2 is judged on its run, not on the window's median (G3). */
	@Test
	public void slowDrawingPrintsTheRateOfItsRun()
	{
		final Trace t = Trace.steady(60).usual(USUAL).fps(45, 59, 24);
		final Session s = t.build();
		final Evidence ev = EvidenceBuilder.forCondition(s, 60, t.settings());
		assertEquals("the window's median is the fast rate", 50, ev.fps);
		assertEquals(15, ev.lowFpsS);
		assertEquals(24, ev.lowFps);

		final Verdict v = new VerdictEngine().conditionNow(s, 60, t.settings());
		assertNotNull(v);
		assertEquals(Cause.SLOW_DRAWING, v.cause);
		assertEquals("24 fps for 15 s.", v.proof);
		assertEquals(Confidence.LIKELY, v.confidence);

		// On the card, second by second over a longer slow stretch: F2, and never the window's 50.
		final Trace longer = Trace.steady(120).usual(USUAL).fps(45, 119, 24);
		final Session ls = longer.build();
		final VerdictEngine engine = new VerdictEngine();
		boolean shown = false;
		for (int now = 1; now <= 120; now++)
		{
			final Verdict c = engine.current(ls, now, ls.wallMsOf(now), longer.settings());
			assertFalse("at " + now + ": " + c.proof, c.proof.contains("50 fps"));
			if (c.cause == Cause.SLOW_DRAWING)
			{
				shown = true;
				assertTrue(c.proof, c.proof.startsWith("24 fps for "));
			}
		}
		assertTrue(shown);
	}

	/** A condition leaves out the seconds masked by their flags: a load is not slow drawing. */
	@Test
	public void aLongLoadIsNotSlowDrawing()
	{
		final Trace t = Trace.steady(100).usual(USUAL).fps(80, 91, 5);
		for (int sec = 80; sec <= 91; sec++)
		{
			t.loading(sec, 1000);
		}
		final Session s = t.build();
		final VerdictEngine engine = new VerdictEngine();
		for (int now = 1; now <= 100; now++)
		{
			final Verdict v = engine.current(s, now, s.wallMsOf(now), t.settings());
			assertEquals("at " + now, Cause.ALL_CLEAR, v.cause);
			assertNull("no condition scores, at " + now, new VerdictEngine().conditionNow(s, now, t.settings()));
		}

		// The same twelve seconds with no load are slow drawing: the mask is what saved them.
		final Session slow = Trace.steady(100).usual(USUAL).fps(80, 91, 5).build();
		final Verdict v = new VerdictEngine().conditionNow(slow, 92, t.settings());
		assertNotNull(v);
		assertEquals(Cause.SLOW_DRAWING, v.cause);
	}

	/**
	 * W1c needs "no event open or closed in the window" (6.3). The log keeps an open event as opened handed it in,
	 * with its end at its first trigger second (3.4, 3.6); while it stays open it still reaches the newest second, so
	 * a slow world is not a condition then, however long ago the event opened.
	 */
	@Test
	public void w1cNeverShowsWhileAnEventIsOpen()
	{
		final Trace t = Trace.steady(220).usual(USUAL).ticksEvery(100, 219, 750);

		// With no event the slow ticks are the condition W1c, on the card long before the trace ends.
		final Session free = t.build();
		final VerdictEngine freeCard = new VerdictEngine();
		int firstW1c = -1;
		for (int now = 1; now <= 220; now++)
		{
			final Verdict v = freeCard.current(free, now, free.wallMsOf(now), t.settings());
			if (v.cause == Cause.SLOW_WORLD && firstW1c < 0)
			{
				firstW1c = now;
				assertEquals("This world is running slow", v.headline);
				assertEquals(Level.WARN, v.level);
			}
		}
		assertTrue("W1c shows with no event", firstW1c > 0);

		// The same trace with an event that opened at 100 and never closed: never W1c.
		final Session s = t.build();
		final VerdictEngine engine = new VerdictEngine();
		for (int now = 1; now <= 220; now++)
		{
			if (now == 101)
			{
				s.events.add(new VerdictCauseTest.LagEventSpec(0, 100, 100, Trigger.TICK_OFF).open(true).on(s));
			}
			final Verdict v = engine.current(s, now, s.wallMsOf(now), t.settings());
			assertNotEquals("at " + now, Cause.SLOW_WORLD, v.cause);
			if (now >= 101)
			{
				final Evidence ev = EvidenceBuilder.forCondition(s, now, t.settings());
				assertTrue("the open event is in the window, at " + now, ev.eventInWindow);
				assertEquals("at " + now, 0, Rules.W1C.score(ev));
			}
		}
		assertEquals("an open event is not a closed one: the card has no event to show", "No lag this session.",
			engine.current(s, 220, s.wallMsOf(220), t.settings()).proof);
	}

	/** The judge never reads {@code LagEvent.open}: an open event is judged as if it closed now (3.6). */
	@Test
	public void anOpenEventIsJudgedAsIfItClosedNow()
	{
		final Trace t = VerdictCauseTest.w1Trace();
		final Session s = t.build();
		final LagEvent closed = new VerdictCauseTest.LagEventSpec(5, 120, 133, Trigger.TICK_OFF).on(s);
		final LagEvent open = new VerdictCauseTest.LagEventSpec(5, 120, 133, Trigger.TICK_OFF).open(true).on(s);
		assertTrue(open.open);
		final Verdict a = judge.judgeEvent(s, closed, t.settings());
		final Verdict b = judge.judgeEvent(s, open, t.settings());
		assertEquals(Cause.SLOW_WORLD, b.cause);
		assertTrue(a.sameAs(b));
		assertEquals(a.confidence, b.confidence);
		assertEquals(a.level, b.level);
		assertEquals(a.ruledOut, b.ruledOut);
		assertEquals(a.whenWallMs, b.whenWallMs);
		assertEquals(a.durationS, b.durationS);
		assertEquals(a.world, b.world);

		// Early in the lag the span is short, and the truth at that second is "Can't tell".
		final LagEvent early = new VerdictCauseTest.LagEventSpec(5, 120, 121, Trigger.TICK_OFF).open(true).on(s);
		final Verdict e = judge.judgeEvent(s, early, t.settings());
		assertNotEquals("W1 needs SLOW_WORLD_MIN_TICKS ticks", Cause.SLOW_WORLD, e.cause);

		// Judging an open event touches neither the log nor the card.
		final VerdictEngine engine = new VerdictEngine();
		final Verdict before = engine.current(s, 134, s.wallMsOf(134), t.settings());
		engine.judgeEvent(s, open, t.settings());
		assertEquals(0, s.events.size());
		assertTrue(before == engine.current(s, 134, s.wallMsOf(134), t.settings()));
	}

	private static void assertCantTellNamingNothing(Verdict v)
	{
		assertEquals(Cause.NOT_SURE, v.cause);
		assertEquals(Confidence.CANT_TELL, v.confidence);
		assertNull(v.alsoA);
		assertNull(v.alsoB);
		assertEquals("Can't tell yet", v.headline);
		assertEquals("Wait for it to happen again.", v.fix);
	}
}
