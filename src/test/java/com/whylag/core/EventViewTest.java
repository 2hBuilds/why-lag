package com.whylag.core;

import org.junit.Test;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

/**
 * One event as the four tiles and the five lane values (contract 3.8, 5.2, 5.3): the picture's "lag found" event,
 * the dash for every field with no data, the CPU lane's two halves, and the memory tile's "pause 0 ms" against
 * "pause n/a" - neither of which is ever the hollow ring.
 */
public class EventViewTest
{
	/**
	 * The picture's event: 50 fps, worst frame 34, ticks 952 mean / 1,240 worst, ping 41 (was 41), 607 of 768 MB,
	 * pause 22, PC 37 % and game 95 %.
	 */
	static LagEvent picturesEvent()
	{
		return event(50, 34, 952, 1240, 640, 41, 41, 607, 768, 22, 37, 95);
	}

	@Test
	public void thePicturesEvent()
	{
		final LagEvent e = picturesEvent();
		final Tile[] t = EventView.tiles(e);
		assertEquals(4, t.length);
		assertEquals(Lane.TILES, t.length);
		assertTile(t[0], Lane.FRAME_RATE, "50 fps", "worst 34 ms", Level.OK);
		assertTile(t[1], Lane.TICKS, "952 ms", "worst 1,240", Level.BAD);
		assertTile(t[2], Lane.PING, "41 ms", "was 41 ms", Level.OK);
		assertTile(t[3], Lane.MEMORY, "79 %", "pause 22 ms", Level.OK);
		for (Tile tile : t)
		{
			assertEquals(NoData.NONE, tile.noData);
		}
		assertArrayEquals("five lane values, the CPU lane's PC half last",
			new String[] {"50 fps", "952 ms", "41 ms", "79 %", "PC 37 %"}, EventView.laneValues(e));
		assertArrayEquals(new Level[] {Level.OK, Level.BAD, Level.OK, Level.OK, Level.OK}, EventView.laneLevels(e));
		assertEquals("the CPU lane's Game half", "Game 95 %", EventView.cpuGameValue(e));
	}

	/** The first four lane values and levels ARE the tiles'; the fifth is the CPU lane, which has no tile. */
	@Test
	public void laneValuesAreTheTileValues()
	{
		for (LagEvent e : new LagEvent[] {picturesEvent(), event(-1, -1, -1, -1, -1, -1, -1, -1, 768, -1, -1, -1),
			event(12, 400, 1500, 2400, 900, 310, 40, 740, 768, 350, 99, 100)})
		{
			final Tile[] tiles = EventView.tiles(e);
			final String[] values = EventView.laneValues(e);
			final Level[] levels = EventView.laneLevels(e);
			assertEquals(5, values.length);
			assertEquals(5, levels.length);
			assertEquals(Lane.values().length, values.length);
			for (int i = 0; i < Lane.TILES; i++)
			{
				assertEquals(Lane.values()[i], tiles[i].lane);
				assertEquals("lane " + i, tiles[i].value, values[i]);
				assertEquals("lane " + i, tiles[i].level, levels[i]);
			}
			assertEquals(Levels.cpu(e.sysCpuPct), levels[Lane.CPU.ordinal()]);
		}
	}

	@Test
	public void noDataFieldsGiveADash()
	{
		final LagEvent none = event(-1, -1, -1, -1, -1, -1, -1, -1, 768, -1, -1, -1);
		final Tile[] t = EventView.tiles(none);
		for (int i = 0; i < Lane.TILES; i++)
		{
			assertTile(t[i], Lane.values()[i], "-", "", Level.NO_DATA);
		}
		assertArrayEquals(new String[] {"-", "-", "-", "-", "-"}, EventView.laneValues(none));
		assertArrayEquals(new Level[] {Level.NO_DATA, Level.NO_DATA, Level.NO_DATA, Level.NO_DATA, Level.NO_DATA},
			EventView.laneLevels(none));
		assertEquals("", EventView.cpuGameValue(none));
		assertEquals("a limit of 0 is no data too", "-", EventView.tiles(event(50, 34, 952, 1240, 640, 41, 41, 607, 0,
			22, 37, 95))[3].value);
	}

	/** No CPU data: the PC half is "-" at NO_DATA, the Game half "" - each on its own, and both. */
	@Test
	public void noCpuDataGivesADash()
	{
		final LagEvent neither = event(50, 34, 952, 1240, 640, 41, 41, 607, 768, 22, -1, -1);
		assertEquals("-", EventView.laneValues(neither)[4]);
		assertEquals(Level.NO_DATA, EventView.laneLevels(neither)[4]);
		assertEquals("", EventView.cpuGameValue(neither));

		final LagEvent pcOnly = event(50, 34, 952, 1240, 640, 41, 41, 607, 768, 22, 37, -1);
		assertEquals("PC 37 %", EventView.laneValues(pcOnly)[4]);
		assertEquals(Level.OK, EventView.laneLevels(pcOnly)[4]);
		assertEquals("", EventView.cpuGameValue(pcOnly));

		final LagEvent gameOnly = event(50, 34, 952, 1240, 640, 41, 41, 607, 768, 22, -1, 95);
		assertEquals("-", EventView.laneValues(gameOnly)[4]);
		assertEquals(Level.NO_DATA, EventView.laneLevels(gameOnly)[4]);
		assertEquals("Game 95 %", EventView.cpuGameValue(gameOnly));

		assertEquals("the four tiles do not move", "79 %", EventView.laneValues(neither)[3]);
	}

	/** The CPU lane's level is the whole PC's: a busy game thread is no sign of trouble on its own. */
	@Test
	public void theCpuLevelFollowsThePc()
	{
		final LagEvent pcBusy = event(50, 34, 600, 610, 0, 41, 41, 400, 768, 0, 96, 10);
		assertEquals(Level.BAD, EventView.laneLevels(pcBusy)[4]);
		assertEquals("PC 96 %", EventView.laneValues(pcBusy)[4]);
		final LagEvent gameBusy = event(50, 34, 600, 610, 0, 41, 41, 400, 768, 0, 20, 100);
		assertEquals(Level.OK, EventView.laneLevels(gameBusy)[4]);
		assertEquals("Game 100 %", EventView.cpuGameValue(gameBusy));
		assertEquals(Level.WARN, EventView.laneLevels(event(50, 34, 600, 610, 0, 41, 41, 400, 768, 0, 85, 40))[4]);
	}

	@Test
	public void aMissingSubFieldLeavesTheValue()
	{
		// Known values with unknown small lines: no usual yet, pauses not known, no worst gap.
		final Tile[] t = EventView.tiles(event(50, -1, 952, -1, -1, 41, -1, 607, 768, -1, 37, 95));
		assertTile(t[0], Lane.FRAME_RATE, "50 fps", "", Level.OK);
		assertTile(t[1], Lane.TICKS, "952 ms", "", Level.NO_DATA);
		assertTile(t[2], Lane.PING, "41 ms", "", Level.OK);
		assertTile(t[3], Lane.MEMORY, "79 %", "pause n/a", Level.OK);
	}

	/** gcPauseMs 0 is "pause 0 ms" (measured, none), -1 is "pause n/a" (not measured): both OK, never the ring. */
	@Test
	public void heapWithoutAPauseIsNeverTheHollowRing()
	{
		final Tile measuredNone = EventView.tiles(event(50, 34, 600, 610, 0, 41, 41, 607, 768, 0, 37, 95))[3];
		assertTile(measuredNone, Lane.MEMORY, "79 %", "pause 0 ms", Level.OK);
		final Tile notMeasured = EventView.tiles(event(50, 34, 600, 610, 0, 41, 41, 607, 768, -1, 37, 95))[3];
		assertTile(notMeasured, Lane.MEMORY, "79 %", "pause n/a", Level.OK);
		assertEquals(Level.OK, EventView.laneLevels(event(50, 34, 600, 610, 0, 41, 41, 607, 768, -1, 37, 95))[3]);
		assertEquals("a long measured pause still counts", Level.BAD,
			EventView.tiles(event(50, 34, 600, 610, 0, 41, 41, 607, 768, Thresholds.GC_BAD_MS, 37, 95))[3].level);
	}

	@Test
	public void noCapIsKnownForAPastEvent()
	{
		// 20 fps under a cap of 20 is OK on the live tile, but a past event knows no cap.
		assertEquals(Level.BAD, EventView.tiles(event(20, 50, 600, 610, 0, 41, 41, 400, 768, -1, 37, 95))[0].level);
	}

	@Test
	public void bigNumbersAreGrouped()
	{
		final Tile[] t = EventView.tiles(event(999, 9999, 1240, 9999, 9000, 999, 1500, 1000, 1024, 1234, 100, 100));
		assertEquals("worst 9,999 ms", t[0].sub);
		assertEquals("1,240 ms", t[1].value);
		assertEquals("was 1,500 ms", t[2].sub);
		assertEquals("97 %", t[3].value);
		assertEquals("pause 1,234 ms", t[3].sub);
	}

	private static void assertTile(Tile t, Lane lane, String value, String sub, Level level)
	{
		assertEquals(lane, t.lane);
		assertEquals(value, t.value);
		assertEquals(sub, t.sub);
		assertEquals(level, t.level);
	}

	private static LagEvent event(int fps, int worstFrameMs, int meanTickGapMs, int worstTickGapMs,
		int worstCorrectedTickMs, int rttMs, int rttBeforeMs, int heapUsedMb, int heapMaxMb, int gcPauseMs,
		int sysCpuPct, int gameBusyPct)
	{
		return new LagEvent(7, 100, 113, 0, Trigger.TICK_OFF.bit(), Trigger.TICK_OFF, 416, 0, 0, 0, fps,
			worstFrameMs, meanTickGapMs, worstTickGapMs, worstCorrectedTickMs, rttMs, rttMs, rttBeforeMs, 900, 0,
			gcPauseMs, heapUsedMb, heapMaxMb, sysCpuPct, gameBusyPct, false, false, null);
	}
}
