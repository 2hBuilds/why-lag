package com.whylag.core;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeSet;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

/**
 * Pins the starting value of every threshold (contract 3.1) by literal, so a change to one is a deliberate edit
 * here too, and proves every field is a {@code public static final} constant. A constant added without a pin fails
 * {@link #everyConstantIsPinned()}.
 */
public class ThresholdsTest
{
	private static final Map<String, Object> PINNED = new LinkedHashMap<>();

	static
	{
		PINNED.put("SECONDS", 3600);
		PINNED.put("TICKS", 6000);
		PINNED.put("GC_PAUSES", 256);
		PINNED.put("EVENTS", 500);
		PINNED.put("TICK_MS", 600);
		PINNED.put("WARMUP_S", 0);
		PINNED.put("WINDOW_S", 60);
		PINNED.put("NO_FRAMES_MS", 2000);
		PINNED.put("CLIENT_CAP_FPS", 50);
		PINNED.put("SCENE_EVERY_TICKS", 5);
		PINNED.put("USUAL_MIN_SAMPLES", 30);
		PINNED.put("SLOW_FRAME_MS", 50);
		PINNED.put("FRAME_GAP_MS", 200);
		PINNED.put("FRAME_GAP_CAP_PCT", 150);
		PINNED.put("FRAME_CLEAN_MS", 100);
		PINNED.put("LOW_FPS_FRAME_MS", 25);
		PINNED.put("FPS_WARN", 40);
		PINNED.put("FPS_BAD", 25);
		PINNED.put("CAP_MATCH_FPS", 2);
		PINNED.put("TICK_OFF_MS", 250);
		PINNED.put("TICK_WARN_MS", 200);
		PINNED.put("TICK_BAD_MS", 400);
		PINNED.put("TICK_TRIM_MS", 900);
		PINNED.put("NO_TICK_MS", 1200);
		PINNED.put("SLOW_WORLD_MIN_TICKS", 5);
		PINNED.put("SLOW_WORLD_MEDIAN_MS", 660);
		PINNED.put("SLOW_WORLD_WINDOW_MS", 620);
		PINNED.put("SLOW_WORLD_WINDOW_TICKS", 60);
		PINNED.put("SLOW_WORLD_PING_MS", 20);
		PINNED.put("SLOW_WORLD_LONG_S", 10);
		PINNED.put("LOGIN_MASK_TICKS", 15);
		PINNED.put("LOAD_TAIL_S", 1);
		PINNED.put("D1_LOOK_S", 10);
		PINNED.put("CAP_WAIT_FACTOR", 2);
		PINNED.put("TEXT_STEP_S", 10);
		PINNED.put("REFRESH_REREAD_S", 10);
		PINNED.put("RTT_SPIKE_PCT", 200);
		PINNED.put("RTT_SPIKE_ADD_MS", 50);
		PINNED.put("RTT_SPIKE_OPENS", false);
		PINNED.put("RTT_STALE_S", 5);
		PINNED.put("RTT_SWING_FACTOR", 3);
		PINNED.put("CLICK_SENT_BYTES", 600);
		PINNED.put("CLICK_SENT_UNITS", 4);
		PINNED.put("CLICK_BASE_S", 10);
		PINNED.put("PING_WARN_MS", 80);
		PINNED.put("PING_BAD_MS", 150);
		PINNED.put("PING_STEADY_MS", 50);
		PINNED.put("PING_USUAL_LOWER_MS", 30);
		PINNED.put("RESENT_WINDOW_S", 16);
		PINNED.put("RESENT_MIN_BYTES", 2048);
		PINNED.put("RESENT_MIN_UNITS", 8);
		PINNED.put("RESENT_PER_MILLE", 10);
		PINNED.put("RESENT_LOOK_S", 2);
		PINNED.put("GC_PAUSE_MS", 100);
		PINNED.put("GC_COVER_PCT", 60);
		PINNED.put("GC_WARN_MS", 100);
		PINNED.put("GC_BAD_MS", 300);
		PINNED.put("HEAP_WARN_PCT", 85);
		PINNED.put("HEAP_BAD_PCT", 93);
		PINNED.put("HEAP_DROP_MB", 64);
		PINNED.put("HEAP_CAP_LOW_MB", 700);
		PINNED.put("BUSY_LOW_PM", 300);
		PINNED.put("BUSY_HIGH_PM", 600);
		PINNED.put("CPU_WARN_PCT", 85);
		PINNED.put("CPU_BAD_PCT", 95);
		PINNED.put("LOAD_LONG_MS", 2000);
		PINNED.put("LOADS_LOOK_S", 600);
		PINNED.put("EVENT_QUIET_S", 5);
		PINNED.put("EVENT_MAX_S", 120);
		PINNED.put("CONDITION_HOLD_S", 10);
		PINNED.put("VERDICT_HOLD_S", 10);
		PINNED.put("EVENT_SHOW_S", 10);
		PINNED.put("SCORE_MARGIN", 15);
		PINNED.put("SUPPORT_POINTS", 10);
		PINNED.put("EVENT_ROWS", 6);
		PINNED.put("STRIP_COLUMNS", 213);
		PINNED.put("STRIP_FPS_MAX", 60);
		PINNED.put("STRIP_TICK_MIN_MS", 450);
		PINNED.put("STRIP_TICK_MAX_MS", 900);
		PINNED.put("STRIP_TICK_PAD_MS", 50);
		PINNED.put("STRIP_PING_MAX_MS", 100);
		PINNED.put("STRIP_PING_PAD_PCT", 120);
		PINNED.put("STRIP_CPU_MAX_PCT", 100);
		PINNED.put("HOST_FILL_S", 3);
		PINNED.put("BADGE_HOLD_S", 15);
		PINNED.put("CHAT_GAP_S", 30);
	}

	@Test
	public void everyStartingValueIsPinned()
	{
		assertEquals(3600, Thresholds.SECONDS);
		assertEquals(6000, Thresholds.TICKS);
		assertEquals(256, Thresholds.GC_PAUSES);
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
		assertEquals(100, Thresholds.GC_PAUSE_MS);
		assertEquals(60, Thresholds.GC_COVER_PCT);
		assertEquals(100, Thresholds.GC_WARN_MS);
		assertEquals(300, Thresholds.GC_BAD_MS);
		assertEquals(85, Thresholds.HEAP_WARN_PCT);
		assertEquals(93, Thresholds.HEAP_BAD_PCT);
		assertEquals(64, Thresholds.HEAP_DROP_MB);
		assertEquals(700, Thresholds.HEAP_CAP_LOW_MB);
		assertEquals(300, Thresholds.BUSY_LOW_PM);
		assertEquals(600, Thresholds.BUSY_HIGH_PM);
		assertEquals(85, Thresholds.CPU_WARN_PCT);
		assertEquals(95, Thresholds.CPU_BAD_PCT);
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
		assertEquals(100, Thresholds.STRIP_CPU_MAX_PCT);
		assertEquals(3, Thresholds.HOST_FILL_S);
		assertEquals(15, Thresholds.BADGE_HOLD_S);
		assertEquals(30, Thresholds.CHAT_GAP_S);
	}

	/** Read by reflection too, so the pins above are the class's real values and not a compiler's copy. */
	@Test
	public void everyConstantIsPinned() throws IllegalAccessException
	{
		final Map<String, Object> actual = new LinkedHashMap<>();
		for (Field f : Thresholds.class.getDeclaredFields())
		{
			if (!f.isSynthetic())
			{
				actual.put(f.getName(), f.get(null));
			}
		}
		assertEquals("a new or removed threshold must be pinned here", new TreeSet<>(PINNED.keySet()),
			new TreeSet<>(actual.keySet()));
		for (Map.Entry<String, Object> e : PINNED.entrySet())
		{
			assertEquals(e.getKey(), e.getValue(), actual.get(e.getKey()));
		}
		assertEquals(86, actual.size());
	}

	/**
	 * {@code DISCONNECT_TAIL_S} was removed on 2026-09-29 (contract 3.1, gap G2 of section 11): an event that the
	 * detector makes always holds its disconnect inside its own span, so nothing reads a tail.
	 */
	@Test
	public void theDisconnectTailIsGone()
	{
		for (Field f : Thresholds.class.getDeclaredFields())
		{
			assertNotEquals("DISCONNECT_TAIL_S", f.getName());
		}
	}

	@Test
	public void everyFieldIsPublicStaticFinal()
	{
		for (Field f : Thresholds.class.getDeclaredFields())
		{
			if (f.isSynthetic())
			{
				continue;
			}
			final int m = f.getModifiers();
			assertTrue(f.getName() + " must be public static final",
				Modifier.isPublic(m) && Modifier.isStatic(m) && Modifier.isFinal(m));
			assertTrue(f.getName() + " is a number or a switch",
				f.getType() == int.class || f.getType() == boolean.class);
		}
	}

	@Test
	public void itCannotBeBuilt()
	{
		assertTrue(Modifier.isFinal(Thresholds.class.getModifiers()));
		final Constructor<?>[] constructors = Thresholds.class.getDeclaredConstructors();
		assertEquals(1, constructors.length);
		assertTrue(Modifier.isPrivate(constructors[0].getModifiers()));
		for (java.lang.reflect.Method m : Thresholds.class.getDeclaredMethods())
		{
			assertTrue("constants only, but it has " + m.getName(), m.isSynthetic());
		}
	}
}
