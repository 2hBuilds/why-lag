package com.whylag.core;

/**
 * ONE event as the three tiles and the three lane values (contract 3.8, 5.2, 5.3): what the panel draws in place
 * of "now" while a list row is selected. Pure and static; it reads the event's own numbers only.
 *
 * <table>
 * <caption>The tiles of a selected event</caption>
 * <tr><th>Tile</th><th>Value</th><th>Sub</th><th>Level</th></tr>
 * <tr><td>Frame rate</td><td>"50 fps" = fps</td><td>"worst 34 ms" = worstFrameMs</td>
 * <td>{@code Levels.fps(fps, 0, false)}: no cap is known for a past event</td></tr>
 * <tr><td>Server ticks</td><td>"952 ms" = meanTickGapMs</td><td>"worst 1,240" = worstTickGapMs</td>
 * <td>{@code Levels.tick(worstCorrectedTickMs)}</td></tr>
 * <tr><td>Ping</td><td>"41 ms" = rttMs</td><td>"was 41 ms" = rttBeforeMs; -1: ""</td>
 * <td>{@code Levels.ping(rttMs)}</td></tr>
 * </table>
 *
 * <p>A value field at -1 gives the value "-", the level NO_DATA and the sub "" (its {@code noData} is
 * {@link NoData#NONE}: an event has no reason to print). A sub field at -1 gives "".
 *
 * <p><b>The lanes.</b> The lane values and levels are the tiles' values and levels, so the tiles and the lanes
 * always show the same numbers.
 */
public final class EventView
{
	private static final int LANES = Lane.values().length;

	private EventView()
	{
	}

	/** Three tiles ({@link Lane#TILES}), in {@link Lane} order. */
	public static Tile[] tiles(LagEvent e)
	{
		return new Tile[] {frameRate(e), ticks(e), ping(e)};
	}

	/** Three values, in {@link Lane} order: "50 fps", "952 ms", "41 ms"; "-" = no data. */
	public static String[] laneValues(LagEvent e)
	{
		final Tile[] tiles = tiles(e);
		final String[] out = new String[LANES];
		for (int i = 0; i < tiles.length; i++)
		{
			out[i] = tiles[i].value;
		}
		return out;
	}

	/** Three levels, in {@link Lane} order: the tiles' levels. */
	public static Level[] laneLevels(LagEvent e)
	{
		final Tile[] tiles = tiles(e);
		final Level[] out = new Level[LANES];
		for (int i = 0; i < tiles.length; i++)
		{
			out[i] = tiles[i].level;
		}
		return out;
	}

	private static Tile frameRate(LagEvent e)
	{
		if (e.fps < 0)
		{
			return dash(Lane.FRAME_RATE);
		}
		final String sub = e.worstFrameMs < 0 ? "" : "worst " + Fmt.thousands(e.worstFrameMs) + " ms";
		return new Tile(Lane.FRAME_RATE, Levels.fps(e.fps, 0, false), Fmt.thousands(e.fps) + " fps", sub,
			NoData.NONE);
	}

	private static Tile ticks(LagEvent e)
	{
		if (e.meanTickGapMs < 0)
		{
			return dash(Lane.TICKS);
		}
		final String sub = e.worstTickGapMs < 0 ? "" : "worst " + Fmt.thousands(e.worstTickGapMs);
		return new Tile(Lane.TICKS, Levels.tick(e.worstCorrectedTickMs), Fmt.thousands(e.meanTickGapMs) + " ms",
			sub, NoData.NONE);
	}

	private static Tile ping(LagEvent e)
	{
		if (e.rttMs < 0)
		{
			return dash(Lane.PING);
		}
		final String sub = e.rttBeforeMs < 0 ? "" : "was " + Fmt.thousands(e.rttBeforeMs) + " ms";
		return new Tile(Lane.PING, Levels.ping(e.rttMs), Fmt.thousands(e.rttMs) + " ms", sub, NoData.NONE);
	}

	private static Tile dash(Lane lane)
	{
		return new Tile(lane, Level.NO_DATA, "-", "", NoData.NONE);
	}
}
