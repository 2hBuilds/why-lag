package com.whylag.core;

import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * The detector's mask (contract 6.1): one test per rule - the login screen, a load, a hop, the login mask, the tail
 * of a load, and an unfocused second under FPS Control's unfocused limit - each on a second that no OTHER rule
 * masks, so a rule that went missing fails its own test. With them, the three named cases of contract 7 (L3):
 * {@code unfocusedWithoutACapIsNotMasked}, {@code secondAfterALoadIsMasked} and {@code twoSecondsAfterALoadIsNot}.
 */
public class MasksTest
{
	private static final int N = 200;

	@Test
	public void aSteadySecondIsNotMasked()
	{
		final Trace t = Trace.steady(N);
		final Session s = t.build();
		for (long sec = 0; sec < N; sec++)
		{
			assertFalse("steady second " + sec, Masks.masked(s.seconds, sec, t.settings()));
		}
	}

	@Test
	public void theLoginScreenIsMasked()
	{
		final Trace t = Trace.steady(N).loginAt(60);
		final Session s = t.build();
		assertEquals("the premise: NOT_LOGGED_IN alone", Flags.NOT_LOGGED_IN, s.seconds.flags(30) & Flags.MASKED);
		assertTrue(Masks.masked(s.seconds, 30, t.settings()));
		assertTrue("the first second of the trace too", Masks.masked(s.seconds, 0, t.settings()));
	}

	@Test
	public void aLoadIsMasked()
	{
		final Trace t = Trace.steady(N).loading(100, 300);
		final Session s = t.build();
		assertEquals("the premise: LOADING alone", Flags.LOADING, s.seconds.flags(100) & Flags.MASKED);
		assertTrue(Masks.masked(s.seconds, 100, t.settings()));
		assertFalse("the second before a load is not its tail", Masks.masked(s.seconds, 99, t.settings()));
	}

	@Test
	public void aHopIsMasked()
	{
		final Trace t = Trace.steady(N).hop(100, 302);
		final Session s = t.build();
		assertEquals("the premise: HOP alone", Flags.HOP, s.seconds.flags(100) & Flags.MASKED);
		assertTrue(Masks.masked(s.seconds, 100, t.settings()));
	}

	@Test
	public void theLoginMaskIsMasked()
	{
		// A hop at 100 masks the fifteen ticks from 101,000: 101,400 .. 109,800, so seconds 101 .. 109.
		final Trace t = Trace.steady(N).hop(100, 302);
		final Session s = t.build();
		for (long sec = 101; sec <= 109; sec++)
		{
			final int maskFlags = s.seconds.flags(sec) & Flags.MASKED;
			assertEquals("the premise: LOGIN_MASK alone @" + sec, Flags.LOGIN_MASK, maskFlags);
			assertTrue("login mask @" + sec, Masks.masked(s.seconds, sec, t.settings()));
		}
		assertFalse("the sixteenth tick is not masked", Masks.masked(s.seconds, 110, t.settings()));
		// A lost connection that ends masks its fifteen ticks too: 71,400 .. 79,800 after a disconnect at 70.
		final Trace lost = Trace.steady(N).disconnect(70);
		final Session l = lost.build();
		assertTrue(Masks.masked(l.seconds, 71, lost.settings()));
		assertTrue(Masks.masked(l.seconds, 79, lost.settings()));
		assertFalse(Masks.masked(l.seconds, 80, lost.settings()));
	}

	@Test
	public void secondAfterALoadIsMasked()
	{
		final Trace t = Trace.steady(N).loading(100, 300);
		final Session s = t.build();
		assertEquals("the premise: no mask flag after a load", 0, s.seconds.flags(101) & Flags.MASKED);
		assertTrue("the tail of a load", Masks.masked(s.seconds, 101, t.settings()));
	}

	@Test
	public void twoSecondsAfterALoadIsNot()
	{
		final Trace t = Trace.steady(N).loading(100, 300);
		final Session s = t.build();
		assertFalse(Masks.masked(s.seconds, 102, t.settings()));
		// The tail follows the LAST second of a run: a two-second load masks 102, not 103.
		final Trace run = Trace.steady(N).loading(100, 1000).loading(101, 400);
		final Session r = run.build();
		assertTrue(Masks.masked(r.seconds, 102, run.settings()));
		assertFalse(Masks.masked(r.seconds, 103, run.settings()));
	}

	@Test
	public void aLoadingFrameCarriesTheTailOnToTheSecondAfterIt()
	{
		// loading(119, 800) and a 1,450 ms frame that ends 300 ms into 121: the trace flags 121 LOADING (6.1).
		final Trace t = Trace.steady(N).loading(119, 800).frameGap(121, 300, 1450);
		final Session s = t.build();
		assertTrue("the tail of 119", Masks.masked(s.seconds, 120, t.settings()));
		assertTrue("the loading frame's second", Masks.masked(s.seconds, 121, t.settings()));
		assertTrue("the tail of 121", Masks.masked(s.seconds, 122, t.settings()));
		assertFalse(Masks.masked(s.seconds, 123, t.settings()));
	}

	@Test
	public void unfocusedUnderFpsControlsUnfocusedLimitIsMasked()
	{
		final SettingsView v = fpsControlUnfocused(Renderer.CPU, 10, false, "", 0);
		assertEquals("the premise", CapSource.FPS_CONTROL_UNFOCUSED, v.capSource(false));
		final Trace t = Trace.steady(N).unfocused(100, 120).settings(v);
		final Session s = t.build();
		assertEquals("the premise: no mask flag", 0, s.seconds.flags(110) & Flags.MASKED);
		assertTrue(Masks.masked(s.seconds, 110, v));
		assertFalse("a focused second under the same settings", Masks.masked(s.seconds, 99, v));
	}

	@Test
	public void unfocusedWithoutACapIsNotMasked()
	{
		final Trace t = Trace.steady(N).unfocused(100, 120);
		final Session s = t.build();
		assertFalse("no FPS Control at all", Masks.masked(s.seconds, 110, t.settings()));
		final SettingsView limitOff = new SettingsView(Renderer.CPU, true, true, 30, false, 10, false, "", 0, 0, "",
			0, 60, 768, MemorySource.MANAGEMENT, Os.WINDOWS, "");
		assertEquals("the premise", CapSource.FPS_CONTROL, limitOff.capSource(false));
		assertFalse("FPS Control on, its unfocused limit off", Masks.masked(s.seconds, 110, limitOff));
		final SettingsView zero = fpsControlUnfocused(Renderer.CPU, 0, false, "", 0);
		assertEquals("the premise", CapSource.CLIENT_50, zero.capSource(false));
		assertFalse("an unfocused limit of 0 is no cap", Masks.masked(s.seconds, 110, zero));
		final SettingsView lowerTarget = fpsControlUnfocused(Renderer.GPU, 60, true, "OFF", 30);
		assertEquals("the premise", CapSource.GPU_TARGET, lowerTarget.capSource(false));
		assertFalse("the GPU's lower target is the cap in force", Masks.masked(s.seconds, 110, lowerTarget));
	}

	@Test
	public void aLostConnectionAndANoFrameSecondAreNotMaskedByThemselves()
	{
		final Trace t = Trace.steady(N).disconnect(100).fps(150, 150, 0);
		final Session s = t.build();
		assertTrue("the premise", Flags.has(s.seconds.flags(100), Flags.DISCONNECT));
		assertFalse("DISCONNECT is a trigger, not a mask", Masks.masked(s.seconds, 100, t.settings()));
		assertTrue("the premise", Flags.has(s.seconds.flags(150), Flags.NO_FRAMES));
		assertFalse(Masks.masked(s.seconds, 150, t.settings()));
	}

	@Test
	public void aSecondBeforeTheTailIsNotSearched()
	{
		// 3,700 seconds wrap the ring: the tail is 100, and second 99, a LOADING second, can no longer be read.
		final Trace t = Trace.steady(3700).loading(99, 500);
		final Session s = t.build();
		assertEquals("the premise", 100, s.seconds.tail());
		assertFalse(Masks.masked(s.seconds, 100, t.settings()));
		// The same load inside the ring does mask the second after it.
		final Trace inside = Trace.steady(3700).loading(199, 500);
		assertTrue(Masks.masked(inside.build().seconds, 200, inside.settings()));
	}

	/** FPS Control active with its focused limit off and its unfocused limit on at {@code unfocusedLimit}. */
	private static SettingsView fpsControlUnfocused(Renderer renderer, int unfocusedLimit, boolean unlockFps,
		String vsync, int target)
	{
		return new SettingsView(renderer, true, false, 0, true, unfocusedLimit, unlockFps, vsync, target, 0, "", 0, 60,
			768, MemorySource.MANAGEMENT, Os.WINDOWS, "");
	}
}
