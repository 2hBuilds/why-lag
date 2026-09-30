package com.whylag.core;

import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

/**
 * Each line of the tile level rules of contract 5.2 and of the CPU lane (5.3), AT the line and one below it; and
 * the memory rule's two meanings of -1 (3.8): with both halves left out it is OK, and it never answers NO_DATA.
 */
public class LevelsTest
{
	@Test
	public void frameRateBadUnderTwentyFive()
	{
		assertEquals(Level.WARN, Levels.fps(Thresholds.FPS_BAD, 0, false));
		assertEquals(Level.BAD, Levels.fps(Thresholds.FPS_BAD - 1, 0, false));
		assertEquals(Level.BAD, Levels.fps(0, 0, false));
	}

	@Test
	public void frameRateWarnUnderForty()
	{
		assertEquals(Level.OK, Levels.fps(Thresholds.FPS_WARN, 0, false));
		assertEquals(Level.WARN, Levels.fps(Thresholds.FPS_WARN - 1, 0, false));
		assertEquals(Level.OK, Levels.fps(50, 50, false));
	}

	@Test
	public void frameRateIsOkAtASelfSetCap()
	{
		// FPS Control at 20: 18 is within 2 of it (at the line), 17 is one below.
		assertEquals(Level.OK, Levels.fps(20, 20, true));
		assertEquals(Level.OK, Levels.fps(20 - Thresholds.CAP_MATCH_FPS, 20, true));
		assertEquals(Level.BAD, Levels.fps(20 - Thresholds.CAP_MATCH_FPS - 1, 20, true));
		assertEquals("above the cap by 2 counts too", Level.OK, Levels.fps(22, 20, true));
		assertEquals("a cap the player did not set excuses nothing", Level.BAD, Levels.fps(20, 20, false));
		assertEquals("no cap known", Level.BAD, Levels.fps(0, 0, true));
		// At 30 the capped rate would be WARN; within the cap it reads OK.
		assertEquals(Level.OK, Levels.fps(29, 30, true));
		assertEquals(Level.WARN, Levels.fps(27, 30, true));
	}

	@Test
	public void frameRateWithNoDataIsNoData()
	{
		assertEquals(Level.NO_DATA, Levels.fps(-1, 50, true));
	}

	@Test
	public void ticksBadAtTwoHundred()
	{
		assertEquals(Level.BAD, Levels.tick(Thresholds.TICK_BAD_MS));
		assertEquals(Level.WARN, Levels.tick(Thresholds.TICK_BAD_MS - 1));
	}

	@Test
	public void ticksWarnAtSixty()
	{
		assertEquals(Level.WARN, Levels.tick(Thresholds.TICK_WARN_MS));
		assertEquals(Level.OK, Levels.tick(Thresholds.TICK_WARN_MS - 1));
		assertEquals(Level.OK, Levels.tick(0));
		assertEquals(Level.NO_DATA, Levels.tick(-1));
	}

	@Test
	public void pingBadAtOneFifty()
	{
		assertEquals(Level.BAD, Levels.ping(Thresholds.PING_BAD_MS));
		assertEquals(Level.WARN, Levels.ping(Thresholds.PING_BAD_MS - 1));
	}

	@Test
	public void pingWarnAtEighty()
	{
		assertEquals(Level.WARN, Levels.ping(Thresholds.PING_WARN_MS));
		assertEquals(Level.OK, Levels.ping(Thresholds.PING_WARN_MS - 1));
		assertEquals(Level.OK, Levels.ping(0));
		assertEquals(Level.NO_DATA, Levels.ping(-1));
	}

	@Test
	public void memoryHeapAfterCollectionLines()
	{
		assertEquals(Level.BAD, Levels.memory(Thresholds.HEAP_BAD_PCT, -1));
		assertEquals(Level.WARN, Levels.memory(Thresholds.HEAP_BAD_PCT - 1, -1));
		assertEquals(Level.WARN, Levels.memory(Thresholds.HEAP_WARN_PCT, -1));
		assertEquals(Level.OK, Levels.memory(Thresholds.HEAP_WARN_PCT - 1, -1));
	}

	@Test
	public void memoryPauseLines()
	{
		assertEquals(Level.BAD, Levels.memory(-1, Thresholds.GC_BAD_MS));
		assertEquals(Level.WARN, Levels.memory(-1, Thresholds.GC_BAD_MS - 1));
		assertEquals(Level.WARN, Levels.memory(-1, Thresholds.GC_WARN_MS));
		assertEquals(Level.OK, Levels.memory(-1, Thresholds.GC_WARN_MS - 1));
	}

	@Test
	public void memoryIsTheWorseOfTheTwoHalves()
	{
		assertEquals(Level.BAD, Levels.memory(40, Thresholds.GC_BAD_MS));
		assertEquals(Level.BAD, Levels.memory(Thresholds.HEAP_BAD_PCT, 10));
		assertEquals(Level.WARN, Levels.memory(Thresholds.HEAP_WARN_PCT, Thresholds.GC_WARN_MS - 1));
		assertEquals(Level.OK, Levels.memory(39, 23));
		assertEquals("a known pause with no heap figure", Level.WARN, Levels.memory(-1, Thresholds.GC_WARN_MS));
		assertEquals("a heap figure with pauses not known", Level.BAD, Levels.memory(Thresholds.HEAP_BAD_PCT, -1));
	}

	/** Both halves left out (no heap-after figure, pauses cannot be known): nothing speaks against memory. */
	@Test
	public void memoryWithNothingKnownIsOk()
	{
		assertEquals(Level.OK, Levels.memory(-1, -1));
	}

	/** 0 ms is a MEASURED fact, "no pause": OK for that half, on its own and beside a heap figure. */
	@Test
	public void aMeasuredZeroPauseIsOk()
	{
		assertEquals(Level.OK, Levels.memory(-1, 0));
		assertEquals(Level.OK, Levels.memory(Thresholds.HEAP_WARN_PCT - 1, 0));
		assertEquals("the heap half still counts", Level.WARN, Levels.memory(Thresholds.HEAP_WARN_PCT, 0));
	}

	/** Whatever the halves, known or not, the memory level is never the hollow ring's. */
	@Test
	public void memoryNeverAnswersNoData()
	{
		final int[] heaps = {-32768, -5, -1, 0, 1, 50, Thresholds.HEAP_WARN_PCT - 1, Thresholds.HEAP_WARN_PCT,
			Thresholds.HEAP_BAD_PCT - 1, Thresholds.HEAP_BAD_PCT, 100, 250, Integer.MAX_VALUE};
		final int[] pauses = {-32768, -5, -1, 0, 1, 50, Thresholds.GC_WARN_MS - 1, Thresholds.GC_WARN_MS,
			Thresholds.GC_BAD_MS - 1, Thresholds.GC_BAD_MS, 9999, Integer.MAX_VALUE};
		for (int heap : heaps)
		{
			for (int pause : pauses)
			{
				assertNotEquals("memory(" + heap + ", " + pause + ")", Level.NO_DATA, Levels.memory(heap, pause));
			}
		}
	}

	/** The CPU lane, on the whole PC's use, at each line and one below it: 84 OK, 85 WARN, 94 WARN, 95 BAD. */
	@Test
	public void cpuLines()
	{
		assertEquals(Level.OK, Levels.cpu(Thresholds.CPU_WARN_PCT - 1));
		assertEquals(Level.WARN, Levels.cpu(Thresholds.CPU_WARN_PCT));
		assertEquals(Level.WARN, Levels.cpu(Thresholds.CPU_BAD_PCT - 1));
		assertEquals(Level.BAD, Levels.cpu(Thresholds.CPU_BAD_PCT));
		assertEquals(Level.OK, Levels.cpu(84));
		assertEquals(Level.WARN, Levels.cpu(85));
		assertEquals(Level.WARN, Levels.cpu(94));
		assertEquals(Level.BAD, Levels.cpu(95));
		assertEquals(Level.BAD, Levels.cpu(100));
		assertEquals(Level.OK, Levels.cpu(0));
		assertEquals("no data", Level.NO_DATA, Levels.cpu(-1));
	}
}
