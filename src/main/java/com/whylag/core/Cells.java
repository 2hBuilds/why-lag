package com.whylag.core;

/**
 * The five small cells of the panel (contract 3.8, 5.2): the four tiles and the CPU, as picture 18 draws them.
 * Pure and static. The tiles stay DATA (the lane values and the report read them); this class turns them into what
 * the panel paints, so the snapshot builder builds nothing new and the panel holds no cell rule.
 *
 * <p><b>{@link #now}</b>, with no event selected, reads the snapshot's four tiles and its two CPU numbers:
 * <table>
 * <caption>The cells of now</caption>
 * <tr><th>Cell</th><th>Value</th><th>Unit</th><th>Level</th><th>Tip</th></tr>
 * <tr><td>FPS</td><td>the number of the Frame rate tile's value</td><td>fps</td><td>the tile's</td>
 * <td>"Frame rate: 50 fps, worst 35 ms"</td></tr>
 * <tr><td>Tick</td><td>the number of the Server ticks tile's value: the window's mean gap</td><td>ms</td>
 * <td>the tile's</td><td>"Ticks: 600 ms, &plusmn;13 ms"</td></tr>
 * <tr><td>Ping</td><td>the number of the Ping tile's value</td><td>ms</td><td>the tile's</td>
 * <td>"Ping: 41 ms, was 39 ms"</td></tr>
 * <tr><td>Mem</td><td>the number of the Memory tile's value and "%", no space: "51%"</td><td>used</td>
 * <td>the tile's</td><td>"Memory: 51 %, pause 23 ms"</td></tr>
 * <tr><td>CPU</td><td>the game's share, "42%"; -1: "-"</td><td>the whole PC, "PC 24"; -1: ""</td>
 * <td>{@code Levels.cpu} of the PC; NO_DATA when both are -1</td><td>"CPU: game 42 %, PC 24 %"; both -1: "CPU: No
 * CPU data on this PC"</td></tr>
 * </table>
 *
 * <p><b>Not logged in.</b> While the snapshot's {@code world} is 0 (not logged in, contract 3.8 and 5.2) the CPU
 * cell is a dash like the four tile cells, whatever its two numbers hold: "-", "", NO_DATA and the tip "CPU: Not
 * logged in". A -1 alone cannot say why there is no number; the world can. So "CPU: No CPU data on this PC" is said
 * only while logged in.
 *
 * <p><b>{@link #of}</b>, for ONE selected event, reads {@link EventView#tiles} of it and its own CPU numbers, by the
 * same rules, with one difference: the Tick cell shows the event's WORST tick ({@code worstTickGapMs}, "1,240"; -1:
 * "-") at {@code Levels.tick(worstCorrectedTickMs)}, and its tip is "Ticks: worst 1,240 ms, mean 952 ms", a part at
 * -1 left out. The ticks lane beside the graph keeps the mean: the cell answers "how bad did it get".
 *
 * <p><b>The rules.</b>
 * <ul>
 * <li>"The number of a value" is the text before its LAST space, read as a whole number with the commas taken out,
 * held in 0 .. 9,999 and printed through {@link Fmt#thousands}: "1,240 ms" gives "1,240", 12,400 gives "9,999".</li>
 * <li>A tile whose value is "-" gives the value "-", the unit "" and the level NO_DATA, and its reason goes in the
 * tip: "Ping: Nothing sent", "Frame rate: Not logged in". A cell is 40 px wide, so the reason is never painted in
 * it.</li>
 * <li>A tip is {@link Lane#label()}, ": ", the tile's value, then ", " and its small line unless that is "". With no
 * data it is the label, ": " and the reason.</li>
 * <li>No cell of {@link #now} is a culprit. In {@link #of} the culprit is the cell of {@code e.group().lane()}; a
 * "Not sure" event (the group UNSURE, which has no lane) marks none. The culprit's level is the event's verdict's
 * (BAD while it has none), whatever its own number says, so its edge, its ground and its shape agree.</li>
 * </ul>
 *
 * <p>Choice: the CPU cell holds its two numbers in 0 .. 999 (3.1's other cell maximum); real shares stop at 100.
 * <p>Choice: a tile value with no number to read (never made) is a dash at NO_DATA; its tip keeps the tile's words.
 * <p>Choice: a dash with no reason (an event's field at -1) has the tip "label: -": "Frame rate: -", "Ticks: -".
 * <p>Choice: a tip prints the true number: a worst tick of 12,400 ms is "9,999" in the cell, "worst 12,400" in its tip.
 */
public final class Cells
{
	/** The largest number a tile's cell prints, and the largest worst tick: "9,999" (contract 5.2). */
	private static final int MAX_NUMBER = 9999;
	/** The largest share the CPU cell prints, "999%" and "PC 999" (contract 3.1; see the class notes). */
	private static final int MAX_SHARE = 999;
	/** No number could be read from a tile's value. */
	private static final int NO_NUMBER = -1;
	private static final String DASH = "-";

	private Cells()
	{
	}

	/**
	 * The five cells with no event selected, in {@link Lane} order; none is a culprit. The four tile cells follow
	 * their tiles; the CPU cell, which has no tile, reads "not logged in" from the snapshot's {@code world} (0).
	 */
	public static Cell[] now(PanelSnapshot s)
	{
		final Tile[] t = s.tiles;
		return new Cell[] {
			tileCell(Lane.FRAME_RATE, "FPS", tile(t, Lane.FRAME_RATE), "fps", ""),
			tileCell(Lane.TICKS, "Tick", tile(t, Lane.TICKS), "ms", ""),
			tileCell(Lane.PING, "Ping", tile(t, Lane.PING), "ms", ""),
			tileCell(Lane.MEMORY, "Mem", tile(t, Lane.MEMORY), "used", "%"),
			s.world == 0 ? notLoggedInCpuCell() : cpuCell(s.gameBusyPct, s.sysCpuPct)};
	}

	/** The five cells of ONE event (a selected row), in {@link Lane} order, its culprit marked. */
	public static Cell[] of(LagEvent e)
	{
		final Tile[] t = EventView.tiles(e);
		final Cell[] out = {
			tileCell(Lane.FRAME_RATE, "FPS", t[0], "fps", ""),
			worstTickCell(e),
			tileCell(Lane.PING, "Ping", t[2], "ms", ""),
			tileCell(Lane.MEMORY, "Mem", t[3], "used", "%"),
			cpuCell(e.gameBusyPct, e.sysCpuPct)};
		final Lane culprit = e.group().lane();
		if (culprit != null)
		{
			final Cell c = out[culprit.ordinal()];
			final Level level = e.verdict == null ? Level.BAD : e.verdict.level;
			out[culprit.ordinal()] = new Cell(c.lane, c.name, c.value, c.unit, level, true, c.tip);
		}
		return out;
	}

	/** A cell of one tile: the number of its value (and {@code suffix}), or a dash with the reason in the tip. */
	private static Cell tileCell(Lane lane, String name, Tile t, String unit, String suffix)
	{
		final String tip;
		if (DASH.equals(t.value))
		{
			tip = lane.label() + ": " + (t.sub.isEmpty() ? DASH : t.sub);
		}
		else
		{
			tip = lane.label() + ": " + t.value + (t.sub.isEmpty() ? "" : ", " + t.sub);
		}
		final int n = DASH.equals(t.value) ? NO_NUMBER : numberOf(t.value);
		if (n == NO_NUMBER)
		{
			return new Cell(lane, name, DASH, "", Level.NO_DATA, false, tip);
		}
		return new Cell(lane, name, Fmt.thousands(n) + suffix, unit, t.level, false, tip);
	}

	/** An event's Tick cell: its WORST tick, at the level of its largest corrected deviation. */
	private static Cell worstTickCell(LagEvent e)
	{
		final StringBuilder tip = new StringBuilder(Lane.TICKS.label()).append(": ");
		if (e.worstTickGapMs >= 0)
		{
			tip.append("worst ").append(Fmt.thousands(e.worstTickGapMs)).append(" ms");
		}
		if (e.meanTickGapMs >= 0)
		{
			tip.append(e.worstTickGapMs >= 0 ? ", " : "").append("mean ").append(Fmt.thousands(e.meanTickGapMs))
				.append(" ms");
		}
		if (e.worstTickGapMs < 0 && e.meanTickGapMs < 0)
		{
			tip.append(DASH);
		}
		if (e.worstTickGapMs < 0)
		{
			return new Cell(Lane.TICKS, "Tick", DASH, "", Level.NO_DATA, false, tip.toString());
		}
		return new Cell(Lane.TICKS, "Tick", Fmt.thousands(Math.min(e.worstTickGapMs, MAX_NUMBER)), "ms",
			Levels.tick(e.worstCorrectedTickMs), false, tip.toString());
	}

	/** The CPU cell: the game's share big, the whole PC's under it; a half at -1 is left out. */
	private static Cell cpuCell(int gameBusyPct, int sysCpuPct)
	{
		final boolean game = gameBusyPct >= 0;
		final boolean pc = sysCpuPct >= 0;
		final String value = game ? Fmt.thousands(Math.min(gameBusyPct, MAX_SHARE)) + "%" : DASH;
		final String unit = pc ? "PC " + Fmt.thousands(Math.min(sysCpuPct, MAX_SHARE)) : "";
		final Level level = Levels.cpu(sysCpuPct);   // NO_DATA while the PC half is -1, so when both are
		final String tip;
		if (!game && !pc)
		{
			tip = Lane.CPU.label() + ": No CPU data on this PC";
		}
		else
		{
			tip = Lane.CPU.label() + ": " + (game ? Fmt.pct("game", gameBusyPct) : "") + (game && pc ? ", " : "")
				+ (pc ? Fmt.pct("PC", sysCpuPct) : "");
		}
		return new Cell(Lane.CPU, "CPU", value, unit, level, false, tip);
	}

	/**
	 * The CPU cell while not logged in: a dash like the four tile cells, whatever the two numbers hold, with their
	 * reason in the tip, "CPU: Not logged in".
	 */
	private static Cell notLoggedInCpuCell()
	{
		return new Cell(Lane.CPU, "CPU", DASH, "", Level.NO_DATA, false,
			Lane.CPU.label() + ": " + NoData.NOT_LOGGED_IN.reason());
	}

	/** The tile of that lane, or a dash with no reason when the snapshot has none there. */
	private static Tile tile(Tile[] tiles, Lane lane)
	{
		final int i = lane.ordinal();
		if (tiles == null || i >= tiles.length || tiles[i] == null)
		{
			return new Tile(lane, Level.NO_DATA, DASH, "", NoData.NONE);
		}
		return tiles[i];
	}

	/**
	 * The number of a tile's value (contract 5.2): the text before its LAST space, read as a whole number with the
	 * commas taken out, held in 0 .. {@link #MAX_NUMBER}; {@link #NO_NUMBER} when that text holds no number.
	 */
	private static int numberOf(String value)
	{
		final int space = value.lastIndexOf(' ');
		final int end = space < 0 ? value.length() : space;
		long n = 0;
		boolean digits = false;
		boolean negative = false;
		for (int i = 0; i < end; i++)
		{
			final char c = value.charAt(i);
			if (c == ',')
			{
				continue;
			}
			if (c == '-' && i == 0)
			{
				negative = true;
				continue;
			}
			if (c < '0' || c > '9')
			{
				return NO_NUMBER;
			}
			digits = true;
			n = Math.min(n * 10 + (c - '0'), MAX_NUMBER + 1L);
		}
		if (!digits)
		{
			return NO_NUMBER;
		}
		return negative ? 0 : (int) Math.min(n, MAX_NUMBER);
	}
}
