package com.whylag.core;

import java.util.ArrayList;
import java.util.List;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * What the panel reads (contract 3.8): a tile, a strip and the snapshot keep their texts and lists safe; the short
 * strip constructor means "no second series"; a strip keeps its second series (the CPU lane's Game line); the
 * snapshot carries the CPU of now, which the report reads; and the time map of contract 5.3 ({@link Strip#columnOf},
 * {@link Strip#columnStart}) cuts a range into 213 columns the snapshot and the panel both use.
 */
public class PanelTypesTest
{
	@Test
	public void nullTextsAreEmpty()
	{
		final Tile tile = new Tile(Lane.PING, Level.NO_DATA, null, null, null);
		assertEquals("", tile.value);
		assertEquals("", tile.sub);
		assertEquals(NoData.NONE, tile.noData);
		assertEquals("", new Strip(Lane.PING, new int[0], new byte[0], 0, 100, null, Level.OK).now);
		final Strip cpu = new Strip(Lane.CPU, new int[0], new byte[0], 0, 100, null, Level.NO_DATA, new int[0], null);
		assertEquals("", cpu.now);
		assertEquals("a null second value is kept as \"\"", "", cpu.now2);
		final PanelSnapshot s = snapshot(null, null, -1, -1, null);
		assertTrue(s.rangeEvents.isEmpty());
		assertTrue(s.sessionEvents.isEmpty());
		assertEquals("", s.footer);
	}

	@Test
	public void theSnapshotsListsAreUnmodifiable()
	{
		final List<LagEvent> list = new ArrayList<>();
		list.add(new LagEvent(11, 100, 113, 0, 0, Trigger.TICK_OFF, 416, 0, 0, 0, 50, 34, 952, 1240, 640, 41, 44, 41,
			900, 0, 22, 607, 768, 37, 95, false, false, null));
		final PanelSnapshot t = snapshot(list, list, 37, 95, "self");
		assertEquals("self", t.footer);
		try
		{
			t.rangeEvents.clear();
			fail("the lists are unmodifiable");
		}
		catch (UnsupportedOperationException expected)
		{
			assertEquals(1, t.sessionEvents.size());
		}
		try
		{
			t.sessionEvents.clear();
			fail("the lists are unmodifiable");
		}
		catch (UnsupportedOperationException expected)
		{
			assertEquals(1, t.rangeEvents.size());
		}
	}

	@Test
	public void theShortStripConstructorMeansNoSecondSeries()
	{
		final int[] values = {50, 49, Strip.NONE};
		final byte[] levels = {0, 0, (byte) Level.NO_DATA.ordinal()};
		final Strip s = new Strip(Lane.FRAME_RATE, values, levels, 0, 60, "50 fps", Level.OK);
		assertNull("no second series", s.values2);
		assertEquals("", s.now2);
		assertSame(values, s.values);
		assertSame(levels, s.levels);
		assertEquals(Lane.FRAME_RATE, s.lane);
		assertEquals(0, s.min);
		assertEquals(60, s.max);
		assertEquals("50 fps", s.now);
		assertEquals(Level.OK, s.nowLevel);
	}

	/** The CPU lane: the PC line with its level and value, and the Game line with only its value. */
	@Test
	public void stripKeepsItsSecondSeries()
	{
		final int[] pc = {20, 37, Strip.NONE};
		final byte[] levels = {0, 0, (byte) Level.NO_DATA.ordinal()};
		final int[] game = {40, 95, Strip.NONE};
		final Strip s = new Strip(Lane.CPU, pc, levels, 0, Thresholds.STRIP_CPU_MAX_PCT, "PC 37 %", Level.OK, game,
			"Game 95 %");
		assertEquals(Lane.CPU, s.lane);
		assertSame(pc, s.values);
		assertSame(levels, s.levels);
		assertSame(game, s.values2);
		assertEquals("PC 37 %", s.now);
		assertEquals(Level.OK, s.nowLevel);
		assertEquals("Game 95 %", s.now2);
		assertEquals(0, s.min);
		assertEquals(100, s.max);
		final Strip empty = new Strip(Lane.CPU, new int[] {Strip.NONE}, new byte[] {3}, 0, 100, "", Level.NO_DATA,
			new int[] {Strip.NONE}, "");
		assertEquals("an empty second series is an array, not null", 1, empty.values2.length);
		assertEquals(Strip.NONE, empty.values2[0]);
	}

	@Test
	public void snapshotCarriesTheCpuOfNow()
	{
		final PanelSnapshot s = snapshot(null, null, 37, 95, "");
		assertEquals(37, s.sysCpuPct);
		assertEquals(95, s.gameBusyPct);
		final PanelSnapshot none = snapshot(null, null, -1, -1, "");
		assertEquals("no data", -1, none.sysCpuPct);
		assertEquals(-1, none.gameBusyPct);
		final PanelSnapshot pcOnly = snapshot(null, null, 20, -1, "");
		assertEquals(20, pcOnly.sysCpuPct);
		assertEquals(-1, pcOnly.gameBusyPct);
	}

	/**
	 * The two helpers are exact inverses over 1, 10 and 60 minutes, in any clock (a session-ms range and the same
	 * range in wall ms give the same columns): every column is a run of instants with no gap and no overlap.
	 */
	@Test
	public void columnsTileTheRange()
	{
		final long wall = 1_790_000_000_000L;
		for (int minutes : new int[] {1, 10, 60})
		{
			final long end = 3_723_000L;
			final long start = end - minutes * 60_000L;
			assertEquals(start, Strip.columnStart(start, end, 0));
			assertEquals(end, Strip.columnStart(start, end, Thresholds.STRIP_COLUMNS));
			assertEquals(-1, Strip.columnOf(start, end, start - 1));
			assertEquals(Thresholds.STRIP_COLUMNS, Strip.columnOf(start, end, end));
			for (int c = 0; c < Thresholds.STRIP_COLUMNS; c++)
			{
				final long first = Strip.columnStart(start, end, c);
				final long last = Strip.columnStart(start, end, c + 1) - 1;
				assertTrue("column " + c + " holds an instant", last >= first);
				assertEquals(c, Strip.columnOf(start, end, first));
				assertEquals(c, Strip.columnOf(start, end, last));
				assertEquals("the same column in wall ms", c, Strip.columnOf(start + wall, end + wall, first + wall));
			}
		}
		try
		{
			Strip.columnOf(0, Thresholds.STRIP_COLUMNS - 1, 0);
			fail("a range shorter than the columns is refused");
		}
		catch (IllegalArgumentException expected)
		{
			assertEquals(0, Strip.columnOf(0, Thresholds.STRIP_COLUMNS, 0));
		}
	}

	/** At 1 min a column is 282 ms: every second touches 4 or 5 columns, so no column is left without a second. */
	@Test
	public void aSecondTouchesFourOrFiveColumnsAtOneMinute()
	{
		final long end = 3_723_000L;
		final long start = end - 60_000L;
		final boolean[] touched = new boolean[Thresholds.STRIP_COLUMNS];
		for (long ms = start; ms < end; ms += 1000)
		{
			final int from = Strip.columnOf(start, end, ms);
			final int to = Strip.columnOf(start, end, ms + 999);
			final int n = to - from + 1;
			assertTrue("second at " + ms + " touches " + n, n == 4 || n == 5);
			for (int c = from; c <= to; c++)
			{
				touched[c] = true;
			}
		}
		for (int c = 0; c < touched.length; c++)
		{
			assertTrue("column " + c, touched[c]);
		}
	}

	/** At 60 min a column is 16.9 s: it touches 17 or 18 whole seconds, and its worst value is theirs. */
	@Test
	public void aColumnHoldsSeventeenOrEighteenSecondsAtSixtyMinutes()
	{
		final long end = 3_723_000L;
		final long start = end - 3_600_000L;
		for (int c = 0; c < Thresholds.STRIP_COLUMNS; c++)
		{
			final long first = Strip.columnStart(start, end, c);
			final long last = Strip.columnStart(start, end, c + 1) - 1;
			final long seconds = Math.floorDiv(last, 1000) - Math.floorDiv(first, 1000) + 1;
			assertTrue("column " + c + " touches " + seconds + " seconds", seconds == 17 || seconds == 18);
		}
	}

	private static PanelSnapshot snapshot(List<LagEvent> range, List<LagEvent> session, int sysCpuPct,
		int gameBusyPct, String footer)
	{
		return new PanelSnapshot(0, null, 0, null, new Tile[0], 10, 0, 0, new Strip[0], range, session,
			new int[Group.values().length], session == null ? 0 : session.size(), 0, sysCpuPct, gameBusyPct, null,
			footer);
	}
}
