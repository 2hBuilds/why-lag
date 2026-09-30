package com.whylag.core;

import java.util.ArrayList;
import java.util.List;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

/**
 * The detector (contract 6.1, 6.2, 3.4, 3.6; the test list of contract 7, L3): each trigger fires at its line and
 * not one below it; the named traps (a teleport, a long load, low frame rates, a cap of 4 fps, a click burst, a stale
 * RTT, the frame gap limit of the second's own focus); how events open, merge, close and turn into conditions; what
 * feeds the usuals; the event's own numbers, open and closed; and the ids.
 *
 * <p>Every trace is a {@link Trace}; its steady second is 50 fps with a 22 ms worst frame, ticks every 600 ms from
 * session ms 0, a fresh RTT of 40, 900 bytes sent a second, on the CPU renderer (cap 50, so the frame gap limit is
 * {@link Thresholds#FRAME_GAP_MS}). The expected numbers below are worked out by hand from those rules.
 *
 * <p>Choice: an at-the-line test takes its line from Thresholds, so a line tuned live moves with it (NO_TICK: pinned).
 * <p>Choice: the lost-seconds and live-growth cases write a Session by hand; Trace writes a whole trace at once.
 * <p>Choice: LagDetector's package-private triggers(sec) shows which triggers fired in a second (3.10 allows it).
 */
public class LagDetectorTest
{
	private static final int N = 200;

	// ---------------------------------------------------------------- one per trigger, at the line

	@Test
	public void disconnectFires()
	{
		final Run r = run(Trace.steady(N).disconnect(120));
		assertEquals(Trigger.DISCONNECT.bit(), r.d.triggers(120));
		assertEquals("no flag, no trigger", 0, r.d.triggers(119));
		assertEquals(1, r.rec.closed.size());
		final LagEvent e = r.rec.closed.get(0);
		assertEquals(120, e.startSec);
		assertEquals(120, e.endSec);
		assertEquals(Trigger.DISCONNECT, e.first);
		assertEquals(Trigger.DISCONNECT.bit(), e.triggers);
	}

	@Test
	public void longLoadFiresAtTheLine()
	{
		// A run of LOAD_LONG_MS (2,000: 1,000 ms a second from 100) fires LONG_LOAD in the first second after it.
		final int after = 100 + (Thresholds.LOAD_LONG_MS + 999) / 1000;
		final Run at = run(loadRun(Trace.steady(N), 100, Thresholds.LOAD_LONG_MS));
		assertEquals(Trigger.LONG_LOAD.bit(), at.d.triggers(after));
		assertEquals(1, at.rec.closed.size());
		assertEquals(after, at.rec.closed.get(0).startSec);
		final Run below = run(loadRun(Trace.steady(N), 100, Thresholds.LOAD_LONG_MS - 1));
		assertTrue("a run of one ms less", below.rec.opened.isEmpty());
		assertAllQuiet(below, 0, N - 1);
	}

	@Test
	public void frameGapFiresAtTheLine()
	{
		// The CPU renderer's cap of 50 leaves the limit at its floor, FRAME_GAP_MS (200).
		final int line = Trace.steady(1).settings().frameGapLimitMs(true);
		final Run at = run(Trace.steady(N).frameGap(120, 500, line));
		assertEquals(Trigger.FRAME_GAP.bit(), at.d.triggers(120));
		assertEquals(Trigger.FRAME_GAP, at.rec.closed.get(0).first);
		final Run below = run(Trace.steady(N).frameGap(120, 500, line - 1));
		assertEquals(0, below.d.triggers(120));
		assertTrue(below.rec.opened.isEmpty());
	}

	@Test
	public void tickOffFiresAtTheLine()
	{
		// The first tick of 120 comes TICK_OFF_MS + 22 ms late (722 ms after the one before): less the steady 22 ms
		// frame it carries, TICK_OFF_MS off; its catch-up gap (478) is as far off the other way.
		final int frame = Trace.steady(1).build().ticks.frameMs(0);
		final Run at = run(Trace.steady(N).tickLate(120, Thresholds.TICK_OFF_MS + frame, true));
		assertEquals(Trigger.TICK_OFF.bit(), at.d.triggers(120));
		assertEquals(Trigger.TICK_OFF, at.rec.closed.get(0).first);
		final Run below = run(Trace.steady(N).tickLate(120, Thresholds.TICK_OFF_MS + frame - 1, true));
		assertEquals(0, below.d.triggers(120));
		assertTrue(below.rec.opened.isEmpty());
	}

	@Test
	public void noTickFiresAtTheLine()
	{
		// The ticks of 50 .. 52 are gone; the newest before 51,000 is 49,800: 1,200 ms old at the second's end.
		assertEquals("the premise: the hole is laid for this line", 1200, Thresholds.NO_TICK_MS);
		final Run at = run(Trace.steady(N).noTicks(50, 52));
		assertEquals(0, at.d.triggers(49));
		assertEquals(Trigger.NO_TICK.bit(), at.d.triggers(50));
		assertEquals(Trigger.NO_TICK.bit(), at.d.triggers(52));
		final LagEvent e = at.rec.closed.get(0);
		assertEquals(50, e.startSec);
		assertEquals(Trigger.NO_TICK, e.first);
		assertTrue("the tick after the hole is off", e.has(Trigger.TICK_OFF));
		assertEquals(53, e.endSec);
		// Every tick from 49,200 on one ms later: the newest before 51,000 is 49,801, 1,199 ms old.
		final Run below = run(Trace.steady(N).tickLate(49, 1, false).noTicks(50, 52));
		assertEquals(0, below.d.triggers(50));
		assertEquals(Trigger.NO_TICK.bit(), below.d.triggers(51));
		assertEquals(51, below.rec.closed.get(0).startSec);
	}

	@Test
	public void resentFiresAtTheLine()
	{
		// Windows counts bytes: RESENT_WINDOW_S seconds of 900 is 14,400 sent; the least re-sent that makes
		// RESENT_PER_MILLE of it (144 is 10 per mille) fires, one less (143 is 9) does not.
		final long windowSent = (long) Thresholds.RESENT_WINDOW_S * 900;
		final int line = (int) ((Thresholds.RESENT_PER_MILLE * windowSent + 999) / 1000);
		final Run at = run(Trace.steady(N).resent(120, line));
		assertEquals(Trigger.RESENT.bit(), at.d.triggers(120));
		assertEquals(Trigger.RESENT, at.rec.closed.get(0).first);
		assertEquals("the window still holds the share, but 121 re-sent nothing", 0, at.d.triggers(121));
		final Run below = run(Trace.steady(N).resent(120, line - 1));
		assertEquals(0, below.d.triggers(120));
		assertTrue(below.rec.opened.isEmpty());
		// The least sent before re-sends count: nothing sent in the window but its last second, which sends the least
		// (RESENT_MIN_BYTES), or one less.
		final int first = 121 - Thresholds.RESENT_WINDOW_S;
		final Run least = run(Trace.steady(N).sent(first, 120, 0).sent(120, 120, Os.WINDOWS.resentMinSent())
			.resent(120, 1000));
		assertEquals(Trigger.RESENT.bit(), least.d.triggers(120));
		final Run under = run(Trace.steady(N).sent(first, 120, 0).sent(120, 120, Os.WINDOWS.resentMinSent() - 1)
			.resent(120, 1000));
		assertEquals(0, under.d.triggers(120));
		// Elsewhere the counters are segments, and the least is RESENT_MIN_UNITS of them.
		final Run linux = run(Trace.steady(N).os(Os.LINUX).sent(first, 120, 0).sent(120, 120, Os.LINUX.resentMinSent())
			.resent(120, 1));
		assertEquals(Trigger.RESENT.bit(), linux.d.triggers(120));
		final Run linuxUnder = run(Trace.steady(N).os(Os.LINUX).sent(first, 120, 0)
			.sent(120, 120, Os.LINUX.resentMinSent() - 1).resent(120, 1));
		assertEquals(0, linuxUnder.d.triggers(120));
	}

	@Test
	public void rttSpikeFiresAtTheLine()
	{
		// A usual of 40: the line is the larger of 40 x 200 % = 80 and 40 + 50 = 90.
		final int line = spikeLine(40);
		final Run at = run(Trace.steady(N).usual(40).rtt(120, 120, line));
		assertEquals(Trigger.RTT_SPIKE.bit(), at.d.triggers(120));
		final Run below = run(Trace.steady(N).usual(40).rtt(120, 120, line - 1));
		assertEquals(0, below.d.triggers(120));
		// A usual of 100 (every RTT of the trace is 100): the larger of 200 and 150, the other half of the rule.
		final int high = spikeLine(100);
		final Run pct = run(Trace.steady(N).rtt(0, N - 1, 100).rtt(120, 120, high));
		assertEquals(100, pct.s.rttUsual.median());
		assertEquals(Trigger.RTT_SPIKE.bit(), pct.d.triggers(120));
		final Run pctBelow = run(Trace.steady(N).rtt(0, N - 1, 100).rtt(120, 120, high - 1));
		assertEquals(0, pctBelow.d.triggers(120));
		// No usual yet at second 10 (ten samples): nothing to spike against.
		final Trace early = Trace.steady(N).rtt(10, 10, 300);
		final Session es = early.build();
		final LagDetector ed = new LagDetector();
		ed.advance(es, 10, early.settings(), new Recorder());
		assertEquals("the premise", -1, es.rttUsual.median());
		assertEquals(0, ed.triggers(10));
	}

	// ---------------------------------------------------------------- the traps

	@Test
	public void teleportIsNotListed()
	{
		// A 1.6 s load with its long frame and no ticks: the tick after it (102,000, 2,400 ms after the one before)
		// lands in the tail of the load and fires nothing.
		final Run r = run(Trace.steady(N).loading(100, 1000).loading(101, 600).noTicks(100, 101)
			.frameGap(101, 800, 1200));
		assertTrue(r.rec.opened.isEmpty());
		assertAllQuiet(r, 0, N - 1);
	}

	@Test
	public void longLoadIsListed()
	{
		final Trace t = Trace.steady(N).loading(100, 1000).loading(101, 1000).loading(102, 400);
		final Session s = t.build();
		final LagDetector d = new LagDetector();
		final Recorder rec = new Recorder();
		d.advance(s, 102, t.settings(), rec);
		assertTrue("nothing fires while the run is still loading", rec.opened.isEmpty());
		assertEquals(0, d.triggers(100) | d.triggers(101) | d.triggers(102));
		d.advance(s, 103, t.settings(), rec);
		assertEquals(1, rec.opened.size());
		assertEquals(103, rec.opened.get(0).startSec);
		assertEquals(Trigger.LONG_LOAD, rec.opened.get(0).first);
		d.advance(s, N - 1, t.settings(), rec);
		final LagEvent e = rec.closed.get(0);
		assertEquals(103, e.startSec);
		assertEquals(103, e.endSec);
		assertEquals(Trigger.LONG_LOAD.bit(), e.triggers);
	}

	@Test
	public void noTickIsJudgedAtTheSecondsEnd()
	{
		// A hop at 40 masks the ticks 41,400 .. 49,800. Moved one ms later, the newest tick before 51,000 is the
		// masked 49,801: 1,199 ms old, so NO_TICK does not fire in 50 - though the newest UNMASKED tick, 40,800, is
		// ten seconds old.
		final Run masked = run(Trace.steady(N).hop(40, 302).tickLate(49, 1, false).noTicks(50, 50));
		assertEquals("the premise: 50 is not masked", 0, masked.s.seconds.flags(50) & Flags.MASKED);
		assertEquals("a LOGIN_MASK tick still counts", 0, masked.d.triggers(50));
		// Unmoved, the masked 49,800 is 1,200 ms old at the second's end: it fires.
		final Run fires = run(Trace.steady(N).hop(40, 302).noTicks(50, 50));
		assertEquals(Trigger.NO_TICK.bit(), fires.d.triggers(50));
		// With no tick at all before a second's end, nothing fires - not even 5 s in.
		final Run none = run(Trace.steady(30).noTicks(0, 5));
		assertTrue(none.rec.opened.isEmpty());
		assertAllQuiet(none, 0, 29);
		// Only in an in-game second that drew a frame: no tick from 117,600 on, and 120 is a lost connection's
		// second, or a second with no frame.
		final Run lost = run(Trace.steady(N).noTicks(118, 121).disconnect(120));
		assertEquals(Trigger.NO_TICK.bit(), lost.d.triggers(119));
		assertEquals("CONNECTION_LOST is not in-game", Trigger.DISCONNECT.bit(), lost.d.triggers(120));
		assertEquals(Trigger.NO_TICK.bit(), lost.d.triggers(121));
		final Run frameless = run(Trace.steady(N).noTicks(118, 121).fps(120, 120, 0));
		assertEquals(Trigger.NO_TICK.bit(), frameless.d.triggers(119));
		assertEquals("no frame drawn", 0, frameless.d.triggers(120));
		assertEquals(Trigger.NO_TICK.bit(), frameless.d.triggers(121));
	}

	@Test
	public void lowFpsDoesNotFireTheTickTrigger()
	{
		// At 8 fps a tick waits up to one 125 ms frame: late by one frame, early by one frame on the catch-up, or
		// the whole grid moved by one frame. Off by 125, less the 125 ms frame, is 0 (C5).
		final Trace slow = Trace.steady(N).fps(100, 140, 8).tickLate(105, 125, true).tickLate(110, 125, false)
			.tickLate(120, 125, true);
		final Run r = run(slow);
		assertTrue(r.rec.opened.isEmpty());
		assertAllQuiet(r, 0, N - 1);
		// At 50 fps the same lateness is 103 ms off, under TICK_OFF_MS since the first live look; a tick TICK_OFF_MS
		// off after the 22 ms frame fires.
		assertEquals(0, run(Trace.steady(N).tickLate(105, 125, true)).d.triggers(105));
		final Run fast = run(Trace.steady(N).tickLate(105, Thresholds.TICK_OFF_MS + 22, true));
		assertEquals(Trigger.TICK_OFF.bit(), fast.d.triggers(105));
	}

	@Test
	public void capOfFourFpsFiresNothing()
	{
		// FPS Control at 4 fps: the cap interval is 250, the frame gap limit 375 (C9).
		final SettingsView capped = new SettingsView(Renderer.CPU, true, true, 4, false, 0, false, "", 0, 0, "", 0,
			60, Os.WINDOWS, "");
		assertEquals("the premise", 375, capped.frameGapLimitMs(true));
		final Trace t = Trace.steady(N).fps(100, 150, 4).tickLate(110, 250, true).tickLate(120, 250, false);
		final Run r = run(t, capped);
		assertTrue(r.rec.opened.isEmpty());
		assertAllQuiet(r, 0, N - 1);
		// Without the cap the 250 ms frames are frame gaps.
		final Run uncapped = run(Trace.steady(N).fps(100, 150, 4));
		assertEquals(Trigger.FRAME_GAP.bit(), uncapped.d.triggers(100) & Trigger.FRAME_GAP.bit());
	}

	@Test
	public void clickBurstIsNotASpike()
	{
		// C21: sent jumps from 900 to 1,600 in the spike's second, 700 over the quiet median.
		final int spike = spikeLine(40) + 60;
		final Run r = run(Trace.steady(N).usual(40).rtt(120, 120, spike).sent(120, 120, 1600));
		assertEquals(0, r.d.triggers(120));
		// The jump's line is the OS's click size, CLICK_SENT_BYTES (600) on Windows: that much voids the spike, one
		// byte less does not.
		final int jump = Os.WINDOWS.clickSent();
		assertEquals(0, run(Trace.steady(N).usual(40).rtt(120, 120, spike).sent(120, 120, 900 + jump)).d.triggers(120));
		assertEquals(Trigger.RTT_SPIKE.bit(),
			run(Trace.steady(N).usual(40).rtt(120, 120, spike).sent(120, 120, 900 + jump - 1)).d.triggers(120));
	}

	@Test
	public void steadySendingStillSpikes()
	{
		// The steady 900 a second is over CLICK_SENT_BYTES, but it is no JUMP over the quiet median.
		final Run r = run(Trace.steady(N).usual(40).rtt(120, 120, spikeLine(40) + 60));
		assertEquals(Trigger.RTT_SPIKE.bit(), r.d.triggers(120));
	}

	@Test
	public void teleportFrameAfterLoadOpensNothing()
	{
		// The load's long frame ends in the next second, flagged LOADING by the loading-frame rule.
		final Run next = run(Trace.steady(N).loading(119, 800).frameGap(120, 300, 450));
		assertTrue("the premise", Flags.has(next.s.seconds.flags(120), Flags.LOADING));
		assertTrue(next.rec.opened.isEmpty());
		assertAllQuiet(next, 0, N - 1);
		// A frame that ends two seconds after the load: 120 is a no-frame second in the load's tail, 121 is flagged.
		final Run later = run(Trace.steady(N).loading(119, 800).frameGap(121, 300, 1450));
		assertTrue("the premise", Flags.has(later.s.seconds.flags(120), Flags.NO_FRAMES));
		assertTrue("the premise", Flags.has(later.s.seconds.flags(121), Flags.LOADING));
		assertTrue(later.rec.opened.isEmpty());
		assertAllQuiet(later, 0, N - 1);
	}

	@Test
	public void staleRttIsNoSpike()
	{
		final Run r = run(Trace.steady(N).usual(40).rtt(120, 120, 300).rttStale(120, 120));
		assertEquals("the premise: the RTT is kept", 300, r.s.seconds.rttMs(120));
		assertEquals(0, r.d.triggers(120));
		assertEquals(Trigger.RTT_SPIKE.bit(), run(Trace.steady(N).usual(40).rtt(120, 120, 300)).d.triggers(120));
	}

	@Test
	public void rttSpikeAloneOpensNothing()
	{
		final Run r = run(Trace.steady(N).usual(40).rtt(120, 122, 300));
		for (long sec = 120; sec <= 122; sec++)
		{
			assertEquals(Trigger.RTT_SPIKE.bit(), r.d.triggers(sec));
			assertFalse(r.d.quiet(sec));
		}
		assertTrue(r.rec.opened.isEmpty());
		assertNull(r.d.open());
		// A spike is still a trigger second: it extends an event that another trigger opened.
		final Run extend = run(Trace.steady(N).usual(40).frameGap(120, 500, 300).rtt(124, 124, 300)
			.rtt(128, 128, 300));
		assertEquals(1, extend.rec.closed.size());
		final LagEvent e = extend.rec.closed.get(0);
		assertEquals(120, e.startSec);
		assertEquals(128, e.endSec);
		assertEquals(Trigger.FRAME_GAP, e.first);
		assertEquals(Trigger.FRAME_GAP.bit() | Trigger.RTT_SPIKE.bit(), e.triggers);
	}

	@Test
	public void theFrameGapLimitIsTheSecondsOwn()
	{
		// FPS Control's limit on at 3 and its unfocused limit on at 60; GPU unlocked, V-Sync off, a target of 4.
		final SettingsView v = new SettingsView(Renderer.GPU, true, true, 3, true, 60, true, "OFF", 4, 0, "", 0, 60,
			Os.WINDOWS, "");
		assertEquals("focused: FPS Control's 3", 499, v.frameGapLimitMs(true));
		assertEquals("unfocused: the GPU's 4", 375, v.frameGapLimitMs(false));
		final Trace t = Trace.steady(N).unfocused(120, 120).frameGap(120, 500, 400).frameGap(140, 500, 400);
		final Run r = run(t, v);
		assertFalse("the unfocused second is not masked", Masks.masked(r.s.seconds, 120, v));
		assertEquals("400 ms, unfocused", Trigger.FRAME_GAP.bit(), r.d.triggers(120));
		assertEquals("400 ms, focused", 0, r.d.triggers(140));
	}

	@Test
	public void aMaskedSecondFiresOnlyDisconnectAndLongLoad()
	{
		// A load with a 900 ms frame in it fires nothing.
		final Run load = run(Trace.steady(N).loading(120, 500).frameGap(120, 800, 900));
		assertEquals(0, load.d.triggers(120));
		assertTrue(load.rec.opened.isEmpty());
		// A lost connection inside a hop's login mask fires, and opens an event.
		final Run lost = run(Trace.steady(N).hop(100, 302).disconnect(105));
		assertTrue("the premise", Masks.masked(lost.s.seconds, 105, Trace.steady(N).settings()));
		assertEquals(Trigger.DISCONNECT.bit(), lost.d.triggers(105));
		assertEquals(105, lost.rec.closed.get(0).startSec);
	}

	// ---------------------------------------------------------------- events

	@Test
	public void fiveQuietSecondsClose()
	{
		final Trace t = Trace.steady(N).frameGap(100, 500, 300).frameGap(106, 500, 300);
		final Session s = t.build();
		final LagDetector d = new LagDetector();
		final Recorder rec = new Recorder();
		d.advance(s, 104, t.settings(), rec);
		assertNotNull("four quiet seconds keep it open", d.open());
		assertTrue(rec.closed.isEmpty());
		d.advance(s, 105, t.settings(), rec);
		assertNull("the fifth closes it", d.open());
		assertEquals(1, rec.closed.size());
		assertEquals(100, rec.closed.get(0).endSec);
		d.advance(s, N - 1, t.settings(), rec);
		assertEquals(2, rec.closed.size());
		assertEquals(106, rec.closed.get(1).startSec);
		assertEquals(106, rec.closed.get(1).endSec);
	}

	@Test
	public void fourQuietSecondsMerge()
	{
		final Run r = run(Trace.steady(N).frameGap(100, 500, 300).frameGap(105, 500, 300));
		assertEquals(1, r.rec.closed.size());
		final LagEvent e = r.rec.closed.get(0);
		assertEquals(100, e.startSec);
		assertEquals(105, e.endSec);
		assertEquals(6, e.lengthS());
		assertFalse(e.becameCondition);
	}

	@Test
	public void longEventBecomesACondition()
	{
		// A frame gap every second 100 .. 299. The event cannot pass 120 s: the trigger of 220 closes it at 219 and
		// starts the wait; the triggers of the wait and the one at 302 open nothing; 303 .. 307 are the five quiet
		// seconds that end the wait, so 308, the very next second, opens.
		final Trace t = Trace.steady(400);
		for (int sec = 100; sec <= 299; sec++)
		{
			t.frameGap(sec, 500, 300);
		}
		t.frameGap(302, 500, 300).frameGap(308, 500, 300);
		final Run r = run(t);
		assertEquals(2, r.rec.closed.size());
		final LagEvent condition = r.rec.closed.get(0);
		assertEquals(100, condition.startSec);
		assertEquals(219, condition.endSec);
		assertEquals(Thresholds.EVENT_MAX_S, condition.lengthS());
		assertTrue(condition.becameCondition);
		final LagEvent after = r.rec.closed.get(1);
		assertEquals(308, after.startSec);
		assertFalse(after.becameCondition);
		// An event of exactly EVENT_MAX_S that ends quietly closes normally.
		final Trace exact = Trace.steady(400);
		for (int sec = 100; sec <= 219; sec++)
		{
			exact.frameGap(sec, 500, 300);
		}
		final LagEvent whole = run(exact).rec.closed.get(0);
		assertEquals(219, whole.endSec);
		assertFalse(whole.becameCondition);
	}

	@Test
	public void aDisconnectOpensInsideTheWaitAfterALongEvent()
	{
		final Trace t = Trace.steady(400);
		for (int sec = 100; sec <= 220; sec++)
		{
			t.frameGap(sec, 500, 300);
		}
		t.disconnect(222);
		final Run r = run(t);
		assertEquals(2, r.rec.closed.size());
		assertTrue(r.rec.closed.get(0).becameCondition);
		assertEquals(219, r.rec.closed.get(0).endSec);
		final LagEvent d1 = r.rec.closed.get(1);
		assertEquals("two seconds after the close, inside the wait", 222, d1.startSec);
		assertEquals(Trigger.DISCONNECT, d1.first);
		// A frame gap in the same place opens nothing.
		final Trace control = Trace.steady(400);
		for (int sec = 100; sec <= 220; sec++)
		{
			control.frameGap(sec, 500, 300);
		}
		control.frameGap(222, 500, 300);
		assertEquals(1, run(control).rec.closed.size());
		// A lost connection in the very second that crosses EVENT_MAX_S opens the next event there.
		final Trace crossing = Trace.steady(400);
		for (int sec = 100; sec <= 220; sec++)
		{
			crossing.frameGap(sec, 500, 300);
		}
		crossing.disconnect(220);
		final Run c = run(crossing);
		assertEquals(2, c.rec.closed.size());
		assertEquals(219, c.rec.closed.get(0).endSec);
		assertEquals(220, c.rec.closed.get(1).startSec);
		assertEquals(Trigger.DISCONNECT, c.rec.closed.get(1).first);
		assertEquals(Trigger.FRAME_GAP.bit() | Trigger.DISCONNECT.bit(), c.rec.closed.get(1).triggers);
	}

	// ---------------------------------------------------------------- the usuals

	@Test
	public void usualIgnoresEventSeconds()
	{
		// An event at 100 (at 6 fps from 101, which fires nothing) closes at 105: 0 .. 99 and 106 .. 199 feed.
		final Trace t = Trace.steady(N).frameGap(100, 500, 300).fps(101, 105, 6);
		final Run r = run(t);
		assertEquals(1, r.rec.closed.size());
		assertEquals(100, r.rec.closed.get(0).endSec);
		assertEquals(194, r.s.fpsUsual.count());
		assertEquals(194, r.s.rttSession.count());
		assertEquals(194, r.s.rttUsual.count());
		assertEquals(50, r.s.fpsUsual.median());
	}

	@Test
	public void usualResetsWhenTheWorldColumnChanges()
	{
		// 30 samples from the trace, 100 from 0 .. 99; the hop to world 302 at 100 empties it; 101 .. 109 are the
		// login mask, 110 .. 199 feed 90.
		final Trace t = Trace.steady(N).usual(40).hop(100, 302);
		final Session s = t.build();
		final LagDetector d = new LagDetector();
		final Recorder rec = new Recorder();
		d.advance(s, 99, t.settings(), rec);
		assertEquals(130, s.rttUsual.count());
		d.advance(s, 100, t.settings(), rec);
		assertEquals(0, s.rttUsual.count());
		d.advance(s, N - 1, t.settings(), rec);
		assertEquals(90, s.rttUsual.count());
		assertEquals("every world", 190, s.rttSession.count());
		// A lost connection keeps the world: nothing is reset.
		final Run lost = run(Trace.steady(N).usual(40).disconnect(100));
		assertEquals(30 + 100 + 90, lost.s.rttUsual.count());
	}

	@Test
	public void whatEachUsualTakes()
	{
		// A stale RTT feeds neither RTT usual; frames are fed all the same.
		final Run stale = run(Trace.steady(100).rttStale(0, 99));
		assertEquals(0, stale.s.rttUsual.count());
		assertEquals(0, stale.s.rttSession.count());
		assertEquals(100, stale.s.fpsUsual.count());
		// No RTT feeds nothing either.
		final Run none = run(Trace.steady(100).rttNoData(0, 49, NoData.NOT_CONNECTED));
		assertEquals(50, none.s.rttUsual.count());
		assertEquals(50, none.s.rttSession.count());
		// Fresh RTTs and frames, as they are: 50 x 30 ms and 50 x 60 ms, the lower middle is 30.
		final Run fresh = run(Trace.steady(100).rtt(0, 49, 30).rtt(50, 99, 60).fps(0, 99, 45));
		assertEquals(100, fresh.s.rttUsual.count());
		assertEquals(30, fresh.s.rttUsual.median());
		assertEquals(30, fresh.s.rttSession.median());
		assertEquals(100, fresh.s.fpsUsual.count());
		assertEquals(45, fresh.s.fpsUsual.median());
	}

	@Test
	public void theQuietSecondsOfAnOpenEventFeedNoUsual()
	{
		// One event of 100 .. 105: the four quiet seconds between its triggers and the five that close it add none.
		final Trace t = Trace.steady(N).frameGap(100, 500, 300).frameGap(105, 500, 300);
		final Session s = t.build();
		final LagDetector d = new LagDetector();
		final Recorder rec = new Recorder();
		d.advance(s, 99, t.settings(), rec);
		assertEquals(100, s.fpsUsual.count());
		d.advance(s, 104, t.settings(), rec);
		assertEquals(100, s.fpsUsual.count());
		d.advance(s, 110, t.settings(), rec);
		assertEquals("the closing seconds", 100, s.fpsUsual.count());
		assertEquals(1, rec.closed.size());
		d.advance(s, 111, t.settings(), rec);
		assertEquals(101, s.fpsUsual.count());
		assertEquals(101, s.rttSession.count());
		assertEquals(101, s.rttUsual.count());
		d.advance(s, N - 1, t.settings(), rec);
		assertEquals(189, s.fpsUsual.count());
	}

	// ---------------------------------------------------------------- the event's numbers

	@Test
	public void eventCarriesItsOwnNumbers()
	{
		final Trace t = Trace.steady(300)
			.players(120, 120, 12).players(121, 125, 7)
			.npcs(120, 120, 40).npcs(121, 125, 3)
			.region(120, 120, 12850).region(121, 125, 99)
			.fps(121, 121, 30).fps(122, 122, 35).fps(124, 124, 45)
			.frameGap(120, 400, 450)
			.tickLate(122, 305, false)
			.frameGap(125, 400, 300)
			.rtt(120, 120, 60).rtt(121, 121, 80).rtt(122, 122, 45).rtt(123, 123, 85).rttStale(124, 124)
			.rtt(125, 125, 70)
			.sent(117, 117, 3000).sent(118, 118, 1000).sent(127, 127, 700).sent(128, 128, 5000)
			.resent(119, 2).resent(126, 30).resent(128, 50);
		final Run r = run(t);
		assertEquals(1, r.rec.closed.size());
		final LagEvent e = r.rec.closed.get(0);
		assertEquals(0, e.id);
		assertEquals(120, e.startSec);
		assertEquals("the freeze at 125", 125, e.endSec);
		assertEquals(r.s.wallMsOf(120), e.startWallMs);
		assertEquals(Trigger.FRAME_GAP.bit() | Trigger.TICK_OFF.bit(), e.triggers);
		assertEquals(Trigger.FRAME_GAP, e.first);
		assertEquals("the first second's", 416, e.world);
		assertEquals(12850, e.region);
		assertEquals(12, e.players);
		assertEquals(40, e.npcs);
		assertEquals("frames 50, 30, 35, 50, 45, 50: the lower middle", 45, e.fps);
		assertEquals(450, e.worstFrameMs);
		assertEquals("ten gaps: nine of 600 and one of 905, 630.5 rounded down", 630, e.meanTickGapMs);
		assertEquals(905, e.worstTickGapMs);
		assertEquals("905 - 600 - the 29 ms frame of 35 fps", 276, e.worstCorrectedTickMs);
		assertEquals("fresh 60, 80, 45, 85, 70", 70, e.rttMs);
		assertEquals(85, e.rttMaxMs);
		assertEquals("0 .. 119 fed 40", 40, e.rttBeforeMs);
		assertEquals("118 .. 127: 1,000 + 8 x 900 + 700", 8900, e.sentUnits);
		assertEquals("2 at 119 and 30 at 126", 32, e.resentUnits);
		assertFalse(e.open);
		assertFalse(e.becameCondition);
		assertNull(e.verdict);
		// As it opened, the event held its first second alone - with the look either side over 118 .. 122.
		final LagEvent o = r.rec.opened.get(0);
		assertEquals(0, o.id);
		assertEquals(120, o.startSec);
		assertEquals(120, o.endSec);
		assertTrue(o.open);
		assertEquals(Trigger.FRAME_GAP.bit(), o.triggers);
		assertEquals(50, o.fps);
		assertEquals(600, o.meanTickGapMs);
		assertEquals(600, o.worstTickGapMs);
		assertEquals(0, o.worstCorrectedTickMs);
		assertEquals(60, o.rttMs);
		assertEquals(60, o.rttMaxMs);
		assertEquals(1000 + 4 * 900, o.sentUnits);
		assertEquals(2, o.resentUnits);
	}

	@Test
	public void anEventsPlaceIsItsFirstSeconds()
	{
		// A freeze at 120 in world 416, a hop to 302 at 121, and a lost connection at 123 inside the hop's login
		// mask, which extends the event: its place is that of 120, where the lag began.
		final Run r = run(Trace.steady(N).frameGap(120, 500, 300).players(120, 120, 9).npcs(120, 120, 4)
			.region(120, 120, 777).hop(121, 302).disconnect(123));
		final LagEvent e = r.rec.closed.get(0);
		assertEquals(120, e.startSec);
		assertEquals(123, e.endSec);
		assertEquals(416, e.world);
		assertEquals(9, e.players);
		assertEquals(4, e.npcs);
		assertEquals(777, e.region);
	}

	@Test
	public void anEventLeavesOutTheLoginMasksTicks()
	{
		// A lost connection at 105, inside the login mask of a hop at 100: its only ticks carry LOGIN_MASK.
		final LagEvent masked = run(Trace.steady(N).hop(100, 302).disconnect(105)).rec.closed.get(0);
		assertEquals(105, masked.startSec);
		assertEquals(105, masked.endSec);
		assertEquals(-1, masked.meanTickGapMs);
		assertEquals(-1, masked.worstTickGapMs);
		assertEquals(-1, masked.worstCorrectedTickMs);
		// The same lost connection with no hop counts its two ticks.
		final LagEvent plain = run(Trace.steady(N).disconnect(105)).rec.closed.get(0);
		assertEquals(600, plain.meanTickGapMs);
		assertEquals(600, plain.worstTickGapMs);
		assertEquals(0, plain.worstCorrectedTickMs);
	}

	@Test
	public void theFirstTriggerIsTheFirstInTriggerOrder()
	{
		// A freeze and a late tick in the opening second: FRAME_GAP comes before TICK_OFF in Trigger's order.
		final LagEvent both = run(Trace.steady(N).tickLate(120, 700, false).frameGap(120, 500, 300)).rec.closed
			.get(0);
		assertEquals(Trigger.FRAME_GAP.bit() | Trigger.TICK_OFF.bit(), both.triggers);
		assertEquals(Trigger.FRAME_GAP, both.first);
		assertEquals(Trigger.TICK_OFF, run(Trace.steady(N).tickLate(120, 700, false)).rec.closed.get(0).first);
		// A spike and a re-send: RTT_SPIKE comes first in the order but cannot open, so RESENT opened the event.
		final LagEvent spike = run(Trace.steady(N).usual(40).rtt(120, 120, 300).resent(120, 144)).rec.closed.get(0);
		assertEquals(Trigger.RTT_SPIKE.bit() | Trigger.RESENT.bit(), spike.triggers);
		assertEquals(Trigger.RESENT, spike.first);
	}

	@Test
	public void rttBeforeIsTheUsualAtTheOpening()
	{
		// The event opens at 120 on a usual of 40 (0 .. 119 fed it); the hop at 122 empties the usual, and the event
		// closes at 125, the fifth quiet second, with no usual left.
		final Trace t = Trace.steady(N).frameGap(120, 500, 300).hop(122, 302);
		final Session s = t.build();
		final LagDetector d = new LagDetector();
		final Recorder rec = new Recorder();
		d.advance(s, 125, t.settings(), rec);
		assertEquals(1, rec.closed.size());
		assertEquals("the usual when the event closed", -1, s.rttUsual.median());
		final LagEvent e = rec.closed.get(0);
		assertEquals(120, e.endSec);
		assertEquals("the usual when the event opened", 40, e.rttBeforeMs);
		assertEquals(40, rec.opened.get(0).rttBeforeMs);
		// With no usual yet at the opening (20 samples), -1.
		assertEquals(-1, run(Trace.steady(N).frameGap(20, 500, 300)).rec.closed.get(0).rttBeforeMs);
	}

	// ---------------------------------------------------------------- the open event

	@Test
	public void openCarriesTheNumbersSoFar()
	{
		// A slow world: ticks 900 apart in 120 .. 129 (TICK_OFF in each second), none in 130, 1,100 apart in
		// 131 .. 139, a 70 ms ping at 125. One event, 120 .. 139, closed at 144.
		final Trace t = Trace.steady(300).ticksEvery(120, 129, 900).ticksEvery(130, 139, 1100).rtt(125, 125, 70);
		final Session s = t.build();
		final LagDetector d = new LagDetector();
		final Recorder rec = new Recorder();
		LagEvent last = null;
		for (long k = 0; k < 300; k++)
		{
			d.advance(s, k, t.settings(), rec);
			final LagEvent o = d.open();
			if (k < 120 || k >= 144)
			{
				assertNull("nothing open @" + k, o);
				continue;
			}
			assertNotNull("open @" + k, o);
			assertTrue(o.open);
			assertEquals(0, o.id);
			assertEquals(120, o.startSec);
			assertEquals("the last trigger second @" + k, k == 130 ? 129 : Math.min(k, 139), o.endSec);
			assertEquals("the worst tick so far @" + k, k <= 130 ? 900 : 1100, o.worstTickGapMs);
			assertEquals("the ping so far @" + k, k < 125 ? 40 : 70, o.rttMaxMs);
			assertEquals(40, o.rttMs);
			last = o;
		}
		assertEquals(1, rec.closed.size());
		final LagEvent closed = rec.closed.get(0);
		assertFalse(closed.open);
		assertEquals(139, closed.endSec);
		assertEquals("every field but open", describe(last, 0, true).replace("open=true", "open=false"),
			describe(closed, 0, true));
	}

	@Test
	public void openIsTheSameObjectWhileNothingWasAdded()
	{
		final Trace t = Trace.steady(N).frameGap(100, 500, 300).frameGap(103, 500, 300);
		final Session s = t.build();
		final LagDetector d = new LagDetector();
		final Recorder rec = new Recorder();
		d.advance(s, 100, t.settings(), rec);
		final LagEvent first = d.open();
		assertSame("the event handed to opened", rec.opened.get(0), first);
		d.advance(s, 101, t.settings(), rec);
		assertSame(first, d.open());
		d.advance(s, 102, t.settings(), rec);
		assertSame(first, d.open());
		d.advance(s, 103, t.settings(), rec);
		final LagEvent second = d.open();
		assertNotSame("a trigger second was added", first, second);
		assertEquals(103, second.endSec);
		d.advance(s, 107, t.settings(), rec);
		assertSame("four quiet seconds in one advance", second, d.open());
		d.advance(s, 108, t.settings(), rec);
		assertNull(d.open());
	}

	@Test
	public void idsCountUpFromZeroAndAreKept()
	{
		final Trace t = Trace.steady(300).frameGap(100, 500, 300).frameGap(150, 500, 300).frameGap(152, 500, 300)
			.hop(200, 302).frameGap(250, 500, 300);
		for (int pass = 0; pass < 2; pass++)
		{
			final Session s = t.build();
			final LagDetector d = new LagDetector();
			final Recorder rec = new Recorder();
			for (long k = 0; k < 300; k++)
			{
				d.advance(s, k, t.settings(), rec);
				final LagEvent o = d.open();
				if (o != null)
				{
					final long handed = rec.opened.get(rec.opened.size() - 1).id;
					assertEquals("open() @" + k + " keeps the id opened handed", handed, o.id);
				}
			}
			assertEquals(3, rec.opened.size());
			assertEquals(3, rec.closed.size());
			for (int i = 0; i < 3; i++)
			{
				assertEquals("pass " + pass + ": opened", i, rec.opened.get(i).id);
				assertEquals("pass " + pass + ": closed", i, rec.closed.get(i).id);
			}
			assertEquals("the hop restarts nothing", 250, rec.closed.get(2).startSec);
			assertEquals(302, rec.closed.get(2).world);
		}
	}

	// ---------------------------------------------------------------- reading

	@Test
	public void anAdvancePastTheHeadReadsToTheHead()
	{
		final Trace t = Trace.steady(N).frameGap(150, 500, 300);
		final Session s = t.build();
		final LagDetector d = new LagDetector();
		final Recorder rec = new Recorder();
		d.advance(s, 10_000, t.settings(), rec);
		assertEquals(1, rec.closed.size());
		d.advance(s, 10_000, t.settings(), rec);
		d.advance(s, 5, t.settings(), rec);
		assertEquals("nothing is read twice", 1, rec.opened.size());
		assertEquals(Trigger.FRAME_GAP.bit(), d.triggers(150));
		assertTrue("a second never read is quiet", d.quiet(N + 50));
		// A live ring grows: an advance past its head leaves the seconds not written yet for a later advance.
		final Session live = new Session(1_000_000_000L, 0, Os.WINDOWS, java.time.ZoneOffset.UTC);
		final LagDetector growing = new LagDetector();
		final Recorder seen = new Recorder();
		for (long sec = 0; sec < 100; sec++)
		{
			put(live, sec, 22);
		}
		growing.advance(live, 150, t.settings(), seen);
		for (long sec = 100; sec < N; sec++)
		{
			put(live, sec, sec == 150 ? 300 : 22);
		}
		growing.advance(live, N - 1, t.settings(), seen);
		assertEquals(1, seen.closed.size());
		assertEquals(150, seen.closed.get(0).startSec);
	}

	@Test
	public void aDetectorAWholeRingBehindResumesAtTheTail()
	{
		// A session written by hand: a frame gap at 5, read; then seconds up to 4,005, which wrap the ring so that
		// it begins at 406. The first second it still holds is a frame gap too, and so is 3,900.
		final Session s = new Session(1_000_000_000L, 0, Os.WINDOWS, java.time.ZoneOffset.UTC);
		final SettingsView v = Trace.steady(1).settings();
		final LagDetector d = new LagDetector();
		final Recorder rec = new Recorder();
		for (long sec = 0; sec <= 5; sec++)
		{
			put(s, sec, sec == 5 ? 300 : 22);
		}
		d.advance(s, s.seconds.head(), v, rec);
		assertNotNull(d.open());
		final long last = 4005;
		final long tail = last - (Thresholds.SECONDS - 1);
		for (long sec = 6; sec <= last; sec++)
		{
			put(s, sec, sec == tail || sec == 3900 ? 300 : 22);
		}
		assertEquals("the premise", tail, s.seconds.tail());
		d.advance(s, s.seconds.head(), v, rec);
		// The lost seconds 6 .. 405 are quiet: the early event closed on them, as an event, before 406 was read; so
		// the frame gap of 406 opens an event of its own, not a condition's wait.
		assertEquals(3, rec.closed.size());
		final LagEvent early = rec.closed.get(0);
		assertEquals(5, early.endSec);
		assertFalse("closed by quiet seconds, not as a condition", early.becameCondition);
		assertEquals("its second is no longer readable", -1, early.world);
		assertEquals(tail, rec.closed.get(1).startSec);
		assertEquals(1, rec.closed.get(1).id);
		assertEquals(416, rec.closed.get(1).world);
		assertEquals(3900, rec.closed.get(2).startSec);
		assertEquals(2, rec.closed.get(2).id);
	}

	@Test
	public void describeNamesEveryField()
	{
		// describe() is how these tests and the property test compare two events field by field: a field added to
		// LagEvent must be added to it too, or the comparisons would pass over it unseen.
		int fields = 0;
		for (java.lang.reflect.Field f : LagEvent.class.getFields())
		{
			fields += java.lang.reflect.Modifier.isStatic(f.getModifiers()) ? 0 : 1;
		}
		final LagEvent e = new LagEvent(1, 2, 3, 4, 5, Trigger.TICK_OFF, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17,
			18, 19, true, false, null);
		final String text = describe(e, 0, true);
		assertEquals(text, fields, text.split(" ").length);
		assertEquals("the id left out", fields - 1, describe(e, 0, false).split(" ").length);
		for (int v = 1; v <= 19; v++)
		{
			assertTrue("value " + v + " in " + text, (text + " ").contains("=" + v + " "));
		}
	}

	// ---------------------------------------------------------------- helpers

	/** A trace read in one advance, with its own settings. */
	private static Run run(Trace t)
	{
		return run(t, t.settings());
	}

	/** A trace read in one advance, with {@code settings}. */
	private static Run run(Trace t, SettingsView settings)
	{
		final Session s = t.build();
		final LagDetector d = new LagDetector();
		final Recorder rec = new Recorder();
		d.advance(s, t.seconds() - 1, settings, rec);
		return new Run(s, d, rec);
	}

	private static void assertAllQuiet(Run r, long from, long to)
	{
		for (long sec = from; sec <= to; sec++)
		{
			assertEquals("triggers @" + sec, 0, r.d.triggers(sec));
		}
	}

	/** {@code totalMs} of LOADING from second {@code from} on, 1,000 ms a second: one run. */
	private static Trace loadRun(Trace t, int from, int totalMs)
	{
		int sec = from;
		for (int left = totalMs; left > 0; left -= 1000)
		{
			t.loading(sec++, Math.min(1000, left));
		}
		return t;
	}

	/** The least RTT that spikes against {@code usualMs}: both halves of the RTT_SPIKE rule (6.2). */
	private static int spikeLine(int usualMs)
	{
		return Math.max(usualMs * Thresholds.RTT_SPIKE_PCT / 100, usualMs + Thresholds.RTT_SPIKE_ADD_MS);
	}

	/** One steady second, by hand, with a worst frame of {@code worstFrameMs}. */
	private static void put(Session s, long sec, int worstFrameMs)
	{
		final FrameSecond f = new FrameSecond();
		f.frames = 50;
		f.worstFrameMs = worstFrameMs;
		f.worstFrameEndMs = 500;
		f.state = State.LOGGED_IN;
		f.flags = Flags.FOCUSED;
		f.world = 416;
		s.seconds.putFrame(sec, f);
		final HostSecond h = new HostSecond();
		h.rttMs = 40;
		h.rttAgeS = 0;
		h.sentUnits = 900;
		s.seconds.putHost(sec, h);
	}

	/**
	 * Every field of {@code e} as text, its seconds and wall time moved back by {@code shiftSec}; the id left out
	 * when {@code withId} is false. Two events are equal in every field when their texts are.
	 */
	static String describe(LagEvent e, long shiftSec, boolean withId)
	{
		if (e == null)
		{
			return "null";
		}
		return (withId ? "id=" + e.id + " " : "")
			+ "start=" + (e.startSec - shiftSec) + " end=" + (e.endSec - shiftSec)
			+ " wall=" + (e.startWallMs - shiftSec * 1000) + " triggers=" + e.triggers + " first=" + e.first
			+ " world=" + e.world + " region=" + e.region + " players=" + e.players + " npcs=" + e.npcs
			+ " fps=" + e.fps + " worstFrame=" + e.worstFrameMs + " meanTick=" + e.meanTickGapMs
			+ " worstTick=" + e.worstTickGapMs + " worstCorrected=" + e.worstCorrectedTickMs + " rtt=" + e.rttMs
			+ " rttMax=" + e.rttMaxMs + " rttBefore=" + e.rttBeforeMs + " sent=" + e.sentUnits
			+ " resent=" + e.resentUnits + " open=" + e.open + " becameCondition=" + e.becameCondition
			+ " verdict=" + e.verdict;
	}

	/** A session, its detector, and what the detector told. */
	static final class Run
	{
		final Session s;
		final LagDetector d;
		final Recorder rec;

		Run(Session s, LagDetector d, Recorder rec)
		{
			this.s = s;
			this.d = d;
			this.rec = rec;
		}
	}

	/** Every event opened and closed, in order, and the two kinds as one log. */
	static final class Recorder implements DetectorListener
	{
		final List<LagEvent> opened = new ArrayList<>();
		final List<LagEvent> closed = new ArrayList<>();
		final List<String> log = new ArrayList<>();

		@Override
		public void opened(LagEvent e)
		{
			opened.add(e);
			log.add("opened " + describe(e, 0, true));
		}

		@Override
		public void closed(LagEvent e)
		{
			closed.add(e);
			log.add("closed " + describe(e, 0, true));
		}
	}
}
