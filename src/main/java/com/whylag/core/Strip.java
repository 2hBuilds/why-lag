package com.whylag.core;

/**
 * One lane of the strip chart, as data (contract 3.8, 5.3): {@link Thresholds#STRIP_COLUMNS} values, one per pixel
 * column, each the WORST of its time slice ({@link #NONE} = no data in that column, drawn as a gap), the
 * {@link Level} ordinal of each column, the scale, and the value printed at the lane's right.
 *
 * <p>The arrays belong to the strip once built: nobody writes them after construction. A null {@code now} is kept
 * as "".
 *
 * <p><b>The time map</b> (contract 5.3). A range {@code [start, end)} is cut into {@link Thresholds#STRIP_COLUMNS}
 * columns by {@link #columnOf} and {@link #columnStart}, the one rule for the snapshot (which maps session ms) and
 * the panel (which places bands, axis clocks and the tooltip in wall ms): the two clocks differ by a constant, so
 * they meet in the same columns.
 */
public final class Strip
{
	/** A column with no data. */
	public static final int NONE = Integer.MIN_VALUE;

	/** The longest range the column map takes: {@code span x STRIP_COLUMNS}, and the rounding, must fit in a long. */
	private static final long MAX_SPAN = (Long.MAX_VALUE - Thresholds.STRIP_COLUMNS) / Thresholds.STRIP_COLUMNS;

	public final Lane lane;
	/** {@link Thresholds#STRIP_COLUMNS} long; {@link #NONE} = no data in that column. */
	public final int[] values;
	/** {@link Level} ordinal per column, of {@link #values}. */
	public final byte[] levels;
	/** The scale. */
	public final int min, max;
	/**
	 * The value at the lane's right (contract 5.3): the tile's value ("50 fps", "952 ms", "41 ms"); "" = no value
	 * now (the panel draws one "-").
	 */
	public final String now;
	/** The level of {@link #now}; NO_DATA when it is "". */
	public final Level nowLevel;

	public Strip(Lane lane, int[] values, byte[] levels, int min, int max, String now, Level nowLevel)
	{
		this.lane = lane;
		this.values = values;
		this.levels = levels;
		this.min = min;
		this.max = max;
		this.now = now == null ? "" : now;
		this.nowLevel = nowLevel;
	}

	/**
	 * The column that holds the instant {@code t} of the range {@code [start, end)}:
	 * {@code floor((t - start) x STRIP_COLUMNS / (end - start))}, from 0 to {@code STRIP_COLUMNS - 1}. An instant
	 * before the range answers -1, one at or after its end {@link Thresholds#STRIP_COLUMNS}.
	 *
	 * @throws IllegalArgumentException when the range is shorter than {@code STRIP_COLUMNS} (so a column could be
	 *                                  empty) or too long to map
	 */
	public static int columnOf(long start, long end, long t)
	{
		checkRange(start, end);
		if (t < start)
		{
			return -1;
		}
		if (t >= end)
		{
			return Thresholds.STRIP_COLUMNS;
		}
		return (int) ((t - start) * Thresholds.STRIP_COLUMNS / (end - start));
	}

	/**
	 * The first instant of column {@code c} (0 .. {@code STRIP_COLUMNS}) of the range {@code [start, end)}:
	 * {@code start + ceil(c x (end - start) / STRIP_COLUMNS)}. So {@code columnOf(columnStart(c)) == c}, column
	 * {@code c} holds the instants {@code columnStart(c) .. columnStart(c + 1) - 1}, and
	 * {@code columnStart(STRIP_COLUMNS)} is {@code end}.
	 *
	 * @throws IllegalArgumentException for a column outside 0 .. {@code STRIP_COLUMNS}, or a range as in
	 *                                  {@link #columnOf}
	 */
	public static long columnStart(long start, long end, int c)
	{
		checkRange(start, end);
		if (c < 0 || c > Thresholds.STRIP_COLUMNS)
		{
			throw new IllegalArgumentException("a column is 0 .. " + Thresholds.STRIP_COLUMNS + ", got " + c);
		}
		final long span = end - start;
		return start + (c * span + Thresholds.STRIP_COLUMNS - 1) / Thresholds.STRIP_COLUMNS;
	}

	private static void checkRange(long start, long end)
	{
		final long span = end - start;
		if (end < start || span < Thresholds.STRIP_COLUMNS || span > MAX_SPAN)
		{
			throw new IllegalArgumentException("a strip range is " + Thresholds.STRIP_COLUMNS + " ms or more, got "
				+ start + " .. " + end);
		}
	}
}
