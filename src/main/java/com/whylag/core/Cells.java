package com.whylag.core;

/**
 * The three small cells of the panel (contract 3.8, 5.2): the three tiles, as picture 18 draws them, each a third
 * of the panel's width. Pure and static. The tiles stay DATA (the lane values and the report read them); this class
 * turns them into what the panel paints, so the snapshot builder builds nothing new and the panel holds no cell
 * rule.
 *
 * <p><b>{@link #now}</b>, with no event selected, reads the snapshot's three tiles:
 * <table>
 * <caption>The cells of now</caption>
 * <tr><th>Cell</th><th>Value</th><th>Unit</th><th>Level</th><th>Tip</th></tr>
 * <tr><td>FPS</td><td>the number of the Frame rate tile's value</td><td>fps</td><td>the tile's</td>
 * <td>"Frame rate: 50 fps, worst 35 ms"</td></tr>
 * <tr><td>Tick</td><td>the number of the Server ticks tile's value: the window's mean gap</td><td>ms</td>
 * <td>the tile's</td><td>"Ticks: 600 ms, &plusmn;13 ms"</td></tr>
 * <tr><td>Ping</td><td>the number of the Ping tile's value</td><td>ms</td><td>the tile's</td>
 * <td>"Ping: 41 ms, was 39 ms"</td></tr>
 * </table>
 *
 * <p><b>{@link #of}</b>, for ONE selected event, reads {@link EventView#tiles} of it, by the same rules, with one
 * difference: the Tick cell shows the event's WORST tick ({@code worstTickGapMs}, "1,240"; -1: "-") at
 * {@code Levels.tick(worstCorrectedTickMs)}, and its tip is "Ticks: worst 1,240 ms, mean 952 ms", a part at -1
 * left out. The ticks lane beside the graph keeps the mean: the cell answers "how bad did it get".
 *
 * <p><b>The rules.</b>
 * <ul>
 * <li>"The number of a value" is the text before its LAST space, read as a whole number with the commas taken out,
 * held in 0 .. 9,999 and printed through {@link Fmt#thousands}: "1,240 ms" gives "1,240", 12,400 gives "9,999".</li>
 * <li>A tile whose value is "-" gives the value "-", the unit "" and the level NO_DATA, and its reason goes in the
 * tip: "Ping: Nothing sent", "Frame rate: Not logged in". A cell has no room for it, so the reason is never
 * painted in it.</li>
 * <li>A tip is {@link Lane#label()}, ": ", the tile's value, then ", " and its small line unless that is "". With no
 * data it is the label, ": " and the reason.</li>
 * <li>No cell of {@link #now} is a culprit. In {@link #of} the culprit is the cell of {@code e.group().lane()}; a
 * "Not sure" event (the group UNSURE, which has no lane) marks none. The culprit's level is the event's verdict's
 * (BAD while it has none), whatever its own number says, so its edge, its ground and its shape agree.</li>
 * </ul>
 *
 * <p>Choice: a tile value with no number to read (never made) is a dash at NO_DATA; its tip keeps the tile's words.
 * <p>Choice: a dash with no reason (an event's field at -1) has the tip "label: -": "Frame rate: -", "Ticks: -".
 * <p>Choice: a tip prints the true number: a worst tick of 12,400 ms is "9,999" in the cell, "worst 12,400" in its tip.
 */
public final class Cells
{
	/** The largest number a tile's cell prints, and the largest worst tick: "9,999" (contract 5.2). */
	private static final int MAX_NUMBER = 9999;
	/** No number could be read from a tile's value. */
	private static final int NO_NUMBER = -1;
	private static final String DASH = "-";

	private Cells()
	{
	}

	/**
	 * The three cells with no event selected, in {@link Lane} order; none is a culprit. Each follows its tile.
	 */
	public static Cell[] now(PanelSnapshot s)
	{
		final Tile[] t = s.tiles;
		return new Cell[] {
			tileCell(Lane.FRAME_RATE, "FPS", tile(t, Lane.FRAME_RATE), "fps"),
			tileCell(Lane.TICKS, "Tick", tile(t, Lane.TICKS), "ms"),
			tileCell(Lane.PING, "Ping", tile(t, Lane.PING), "ms")};
	}

	/** The three cells of ONE event (a selected row), in {@link Lane} order, its culprit marked. */
	public static Cell[] of(LagEvent e)
	{
		final Tile[] t = EventView.tiles(e);
		final Cell[] out = {
			tileCell(Lane.FRAME_RATE, "FPS", t[0], "fps"),
			worstTickCell(e),
			tileCell(Lane.PING, "Ping", t[2], "ms")};
		final Lane culprit = e.group().lane();
		if (culprit != null)
		{
			final Cell c = out[culprit.ordinal()];
			final Level level = e.verdict == null ? Level.BAD : e.verdict.level;
			out[culprit.ordinal()] = new Cell(c.lane, c.name, c.value, c.unit, level, true, c.tip);
		}
		return out;
	}

	/** A cell of one tile: the number of its value, or a dash with the reason in the tip. */
	private static Cell tileCell(Lane lane, String name, Tile t, String unit)
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
		return new Cell(lane, name, Fmt.thousands(n), unit, t.level, false, tip);
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
