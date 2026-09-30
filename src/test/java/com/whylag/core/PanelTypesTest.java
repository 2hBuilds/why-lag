package com.whylag.core;

import java.util.ArrayList;
import java.util.List;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * What the panel reads (contract 3.8): a tile, a strip and the snapshot keep their texts and lists safe; the short
 * strip keeps its one series; and the time map of contract 5.3 ({@link Strip#columnOf},
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
		final PanelSnapshot s = snapshot(null, null, null);
		assertTrue(s.rangeEvents.isEmpty());
		assertTrue(s.sessionEvents.isEmpty());
		assertEquals("", s.footer);
	}

	@Test
	public void theSnapshotsListsAreUnmodifiable()
	{
		final List<LagEvent> list = new ArrayList<>();
		list.add(new LagEvent(11, 100, 113, 0, 0, Trigger.TICK_OFF, 416, 0, 0, 0, 50, 34, 952, 1240, 640, 41, 44,
			41, 900, 0, false, false, null));
		final PanelSnapshot t = snapshot(list, list, "self");
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
	public void aStripKeepsItsSeries()
	{
		final int[] values = {50, 49, Strip.NONE};
		final byte[] levels = {0, 0, (byte) Level.NO_DATA.ordinal()};
		final Strip s = new Strip(Lane.FRAME_RATE, values, levels, 0, 60, "50 fps", Level.OK);
		assertSame(values, s.values);
		assertSame(levels, s.levels);
		assertEquals(Lane.FRAME_RATE, s.lane);
		assertEquals(0, s.min);
		assertEquals(60, s.max);
		assertEquals("50 fps", s.now);
		assertEquals(Level.OK, s.nowLevel);
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

	private static PanelSnapshot snapshot(List<LagEvent> range, List<LagEvent> session, String footer)
	{
		return new PanelSnapshot(0, null, 0, null, new Tile[0], 10, 0, 0, new Strip[0], range, session,
			new int[Group.values().length], session == null ? 0 : session.size(), 0, null, footer);
	}
}
