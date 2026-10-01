package com.whylag.core;

import org.junit.Test;
import static org.junit.Assert.assertEquals;

/**
 * Pins the starting value of every threshold (contract 3.1) by literal, so a change to one is a deliberate edit
 * here too. That every field is a {@code public static final} constant, and that a constant added without a pin
 * fails, is the probe's {@code ThresholdsStructureTest}. The thresholds of the memory pauses, the heap, the busy
 * share and the CPU are gone with those readings (1.0.0, the Hub's rule): none of their names is a constant any more.
 */
public class ThresholdsTest
{
	@Test
	public void everyStartingValueIsPinned()
	{
		assertEquals(3600, Thresholds.SECONDS);
		assertEquals(6000, Thresholds.TICKS);
		assertEquals(500, Thresholds.EVENTS);
		assertEquals(600, Thresholds.TICK_MS);
		assertEquals(0, Thresholds.WARMUP_S);
		assertEquals(60, Thresholds.WINDOW_S);
		assertEquals(2000, Thresholds.NO_FRAMES_MS);
		assertEquals(50, Thresholds.CLIENT_CAP_FPS);
		assertEquals(5, Thresholds.SCENE_EVERY_TICKS);
		assertEquals(30, Thresholds.USUAL_MIN_SAMPLES);
		assertEquals(50, Thresholds.SLOW_FRAME_MS);
		assertEquals(200, Thresholds.FRAME_GAP_MS);
		assertEquals(150, Thresholds.FRAME_GAP_CAP_PCT);
		assertEquals(100, Thresholds.FRAME_CLEAN_MS);
		assertEquals(25, Thresholds.LOW_FPS_FRAME_MS);
		assertEquals(40, Thresholds.FPS_WARN);
		assertEquals(25, Thresholds.FPS_BAD);
		assertEquals(2, Thresholds.CAP_MATCH_FPS);
		assertEquals(250, Thresholds.TICK_OFF_MS);
		assertEquals(200, Thresholds.TICK_WARN_MS);
		assertEquals(400, Thresholds.TICK_BAD_MS);
		assertEquals(900, Thresholds.TICK_TRIM_MS);
		assertEquals(1200, Thresholds.NO_TICK_MS);
		assertEquals(5, Thresholds.SLOW_WORLD_MIN_TICKS);
		assertEquals(660, Thresholds.SLOW_WORLD_MEDIAN_MS);
		assertEquals(620, Thresholds.SLOW_WORLD_WINDOW_MS);
		assertEquals(60, Thresholds.SLOW_WORLD_WINDOW_TICKS);
		assertEquals(20, Thresholds.SLOW_WORLD_PING_MS);
		assertEquals(10, Thresholds.SLOW_WORLD_LONG_S);
		assertEquals(15, Thresholds.LOGIN_MASK_TICKS);
		assertEquals(1, Thresholds.LOAD_TAIL_S);
		assertEquals(10, Thresholds.D1_LOOK_S);
		assertEquals(2, Thresholds.CAP_WAIT_FACTOR);
		assertEquals(10, Thresholds.TEXT_STEP_S);
		assertEquals(10, Thresholds.REFRESH_REREAD_S);
		assertEquals(200, Thresholds.RTT_SPIKE_PCT);
		assertEquals(50, Thresholds.RTT_SPIKE_ADD_MS);
		assertEquals(false, Thresholds.RTT_SPIKE_OPENS);
		assertEquals(5, Thresholds.RTT_STALE_S);
		assertEquals(3, Thresholds.RTT_SWING_FACTOR);
		assertEquals(600, Thresholds.CLICK_SENT_BYTES);
		assertEquals(4, Thresholds.CLICK_SENT_UNITS);
		assertEquals(10, Thresholds.CLICK_BASE_S);
		assertEquals(80, Thresholds.PING_WARN_MS);
		assertEquals(150, Thresholds.PING_BAD_MS);
		assertEquals(50, Thresholds.PING_STEADY_MS);
		assertEquals(30, Thresholds.PING_USUAL_LOWER_MS);
		assertEquals(16, Thresholds.RESENT_WINDOW_S);
		assertEquals(2048, Thresholds.RESENT_MIN_BYTES);
		assertEquals(8, Thresholds.RESENT_MIN_UNITS);
		assertEquals(10, Thresholds.RESENT_PER_MILLE);
		assertEquals(2, Thresholds.RESENT_LOOK_S);
		assertEquals(2000, Thresholds.LOAD_LONG_MS);
		assertEquals(600, Thresholds.LOADS_LOOK_S);
		assertEquals(5, Thresholds.EVENT_QUIET_S);
		assertEquals(120, Thresholds.EVENT_MAX_S);
		assertEquals(10, Thresholds.CONDITION_HOLD_S);
		assertEquals(10, Thresholds.VERDICT_HOLD_S);
		assertEquals("back to Smooth 15 s after a lag (2026-09-29)", 10, Thresholds.EVENT_SHOW_S);
		assertEquals(15, Thresholds.SCORE_MARGIN);
		assertEquals(10, Thresholds.SUPPORT_POINTS);
		assertEquals(6, Thresholds.EVENT_ROWS);
		assertEquals(213, Thresholds.STRIP_COLUMNS);
		assertEquals(60, Thresholds.STRIP_FPS_MAX);
		assertEquals(450, Thresholds.STRIP_TICK_MIN_MS);
		assertEquals(900, Thresholds.STRIP_TICK_MAX_MS);
		assertEquals(50, Thresholds.STRIP_TICK_PAD_MS);
		assertEquals(100, Thresholds.STRIP_PING_MAX_MS);
		assertEquals(120, Thresholds.STRIP_PING_PAD_PCT);
		assertEquals(3, Thresholds.HOST_FILL_S);
		assertEquals(15, Thresholds.BADGE_HOLD_S);
		assertEquals(30, Thresholds.CHAT_GAP_S);
	}
}
