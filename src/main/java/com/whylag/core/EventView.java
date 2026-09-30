package com.whylag.core;

/**
 * ONE event as the four tiles and the five lane values (contract 3.8, 5.2, 5.3): what the panel draws in place of
 * "now" while a list row is selected. Pure and static; it reads the event's own numbers only.
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
 * <tr><td>Memory</td><td>"79 %" = heapUsedMb of heapMaxMb, rounded down</td>
 * <td>"pause 22 ms" = gcPauseMs; 0: "pause 0 ms" (measured, none); -1: "pause n/a" (not measured)</td>
 * <td>{@code Levels.memory(-1, gcPauseMs)}: by the pause when it is known, else OK; never NO_DATA</td></tr>
 * </table>
 *
 * <p>A value field at -1 (for memory: a used heap under 0 or a limit of 0 or less) gives the value "-", the level
 * NO_DATA and the sub "" (its {@code noData} is {@link NoData#NONE}: an event has no reason to print). A sub field
 * at -1 gives "" (memory's "pause n/a" is the one exception). So a selected event with a real heap figure never
 * draws the hollow ring, whether a pause was measured or not.
 *
 * <p><b>The lanes.</b> The first four lane values and levels are the tiles' values and levels, so the tiles and the
 * lanes always show the same numbers. The fifth lane, {@link Lane#CPU}, has no tile: its value is the PC half,
 * "PC 37 %" ({@code sysCpuPct}; "-" when it is -1) at the level {@link Levels#cpu}, and {@link #cpuGameValue} gives
 * the Game half, "Game 95 %" ({@code gameBusyPct}; "" when it is -1).
 */
public final class EventView
{
	private static final int PERCENT = 100;
	private static final int LANES = Lane.values().length;
	private static final String PC = "PC";
	private static final String GAME = "Game";

	private EventView()
	{
	}

	/** Four tiles ({@link Lane#TILES}), in {@link Lane} order. */
	public static Tile[] tiles(LagEvent e)
	{
		return new Tile[] {frameRate(e), ticks(e), ping(e), memory(e)};
	}

	/** Five values, in {@link Lane} order: "50 fps", "952 ms", "41 ms", "79 %", "PC 37 %"; "-" = no data. */
	public static String[] laneValues(LagEvent e)
	{
		final Tile[] tiles = tiles(e);
		final String[] out = new String[LANES];
		for (int i = 0; i < tiles.length; i++)
		{
			out[i] = tiles[i].value;
		}
		out[Lane.CPU.ordinal()] = e.sysCpuPct < 0 ? "-" : Fmt.pct(PC, e.sysCpuPct);
		return out;
	}

	/** Five levels, in {@link Lane} order: the tiles' levels, then {@code Levels.cpu(sysCpuPct)}. */
	public static Level[] laneLevels(LagEvent e)
	{
		final Tile[] tiles = tiles(e);
		final Level[] out = new Level[LANES];
		for (int i = 0; i < tiles.length; i++)
		{
			out[i] = tiles[i].level;
		}
		out[Lane.CPU.ordinal()] = Levels.cpu(e.sysCpuPct);
		return out;
	}

	/** The CPU lane's Game half: "Game 95 %" from {@code gameBusyPct}; "" when it is -1. */
	public static String cpuGameValue(LagEvent e)
	{
		return e.gameBusyPct < 0 ? "" : Fmt.pct(GAME, e.gameBusyPct);
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

	/** gcPauseMs 0 is "pause 0 ms" (measured, none), -1 "pause n/a" (cannot be known); the level is never NO_DATA. */
	private static Tile memory(LagEvent e)
	{
		if (e.heapUsedMb < 0 || e.heapMaxMb <= 0)
		{
			return dash(Lane.MEMORY);
		}
		final long pct = (long) e.heapUsedMb * PERCENT / e.heapMaxMb;
		final String sub = e.gcPauseMs < 0 ? "pause n/a" : "pause " + Fmt.thousands(e.gcPauseMs) + " ms";
		return new Tile(Lane.MEMORY, Levels.memory(-1, e.gcPauseMs), pct + " %", sub, NoData.NONE);
	}

	private static Tile dash(Lane lane)
	{
		return new Tile(lane, Level.NO_DATA, "-", "", NoData.NONE);
	}
}
