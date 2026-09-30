package com.whylag.core;

import org.junit.Test;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

/**
 * One event as the three tiles and the three lane values (contract 3.8, 5.2, 5.3): the picture's "lag found" event
 * and the dash for every field with no data. The memory tile and the CPU lane are gone (1.0.0, the Hub's rule).
 */
public class EventViewTest
{
	/** The picture's event: 50 fps, worst frame 34, ticks 952 mean / 1,240 worst, ping 41 (was 41). */
	static LagEvent picturesEvent()
	{
		return event(50, 34, 952, 1240, 640, 41, 41);
	}

	@Test
	public void thePicturesEvent()
	{
		final LagEvent e = picturesEvent();
		final Tile[] t = EventView.tiles(e);
		assertEquals(3, t.length);
		assertEquals(Lane.TILES, t.length);
		assertTile(t[0], Lane.FRAME_RATE, "50 fps", "worst 34 ms", Level.OK);
		assertTile(t[1], Lane.TICKS, "952 ms", "worst 1,240", Level.BAD);
		assertTile(t[2], Lane.PING, "41 ms", "was 41 ms", Level.OK);
		for (Tile tile : t)
		{
			assertEquals(NoData.NONE, tile.noData);
		}
		assertArrayEquals("three lane values", new String[] {"50 fps", "952 ms", "41 ms"}, EventView.laneValues(e));
		assertArrayEquals(new Level[] {Level.OK, Level.BAD, Level.OK}, EventView.laneLevels(e));
	}

	/** The lane values and levels ARE the tiles'. */
	@Test
	public void laneValuesAreTheTileValues()
	{
		for (LagEvent e : new LagEvent[] {picturesEvent(), event(-1, -1, -1, -1, -1, -1, -1),
			event(12, 400, 1500, 2400, 900, 310, 40)})
		{
			final Tile[] tiles = EventView.tiles(e);
			final String[] values = EventView.laneValues(e);
			final Level[] levels = EventView.laneLevels(e);
			assertEquals(3, values.length);
			assertEquals(3, levels.length);
			assertEquals(Lane.values().length, values.length);
			for (int i = 0; i < Lane.TILES; i++)
			{
				assertEquals(Lane.values()[i], tiles[i].lane);
				assertEquals("lane " + i, tiles[i].value, values[i]);
				assertEquals("lane " + i, tiles[i].level, levels[i]);
			}
		}
	}

	@Test
	public void noDataFieldsGiveADash()
	{
		final LagEvent none = event(-1, -1, -1, -1, -1, -1, -1);
		final Tile[] t = EventView.tiles(none);
		for (int i = 0; i < Lane.TILES; i++)
		{
			assertTile(t[i], Lane.values()[i], "-", "", Level.NO_DATA);
		}
		assertArrayEquals(new String[] {"-", "-", "-"}, EventView.laneValues(none));
		assertArrayEquals(new Level[] {Level.NO_DATA, Level.NO_DATA, Level.NO_DATA}, EventView.laneLevels(none));
	}

	@Test
	public void aMissingSubFieldLeavesTheValue()
	{
		// Known values with unknown small lines: no usual yet, no worst gap.
		final Tile[] t = EventView.tiles(event(50, -1, 952, -1, -1, 41, -1));
		assertTile(t[0], Lane.FRAME_RATE, "50 fps", "", Level.OK);
		assertTile(t[1], Lane.TICKS, "952 ms", "", Level.NO_DATA);
		assertTile(t[2], Lane.PING, "41 ms", "", Level.OK);
	}

	@Test
	public void noCapIsKnownForAPastEvent()
	{
		// 20 fps under a cap of 20 is OK on the live tile, but a past event knows no cap.
		assertEquals(Level.BAD, EventView.tiles(event(20, 50, 600, 610, 0, 41, 41))[0].level);
	}

	@Test
	public void bigNumbersAreGrouped()
	{
		final Tile[] t = EventView.tiles(event(999, 9999, 1240, 9999, 9000, 999, 1500));
		assertEquals("worst 9,999 ms", t[0].sub);
		assertEquals("1,240 ms", t[1].value);
		assertEquals("was 1,500 ms", t[2].sub);
	}

	private static void assertTile(Tile t, Lane lane, String value, String sub, Level level)
	{
		assertEquals(lane, t.lane);
		assertEquals(value, t.value);
		assertEquals(sub, t.sub);
		assertEquals(level, t.level);
	}

	private static LagEvent event(int fps, int worstFrameMs, int meanTickGapMs, int worstTickGapMs,
		int worstCorrectedTickMs, int rttMs, int rttBeforeMs)
	{
		return new LagEvent(7, 100, 113, 0, Trigger.TICK_OFF.bit(), Trigger.TICK_OFF, 416, 0, 0, 0, fps,
			worstFrameMs, meanTickGapMs, worstTickGapMs, worstCorrectedTickMs, rttMs, rttMs, rttBeforeMs, 900, 0,
			false, false, null);
	}
}
