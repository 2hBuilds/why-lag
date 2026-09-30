package com.whylag.ui;

import com.whylag.core.EventView;
import com.whylag.core.Fmt;
import com.whylag.core.Group;
import com.whylag.core.LagEvent;
import com.whylag.core.Lane;
import com.whylag.core.Level;
import com.whylag.core.PanelSnapshot;
import com.whylag.core.Strip;
import com.whylag.core.Thresholds;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.event.MouseEvent;
import java.util.Arrays;
import java.util.List;
import javax.swing.JComponent;
import javax.swing.ToolTipManager;

/**
 * Block 5a, the five stacked lanes behind the "Graphs" row (contract 5.3, the lanes of picture 15): 213 x 166, in
 * the layout only while that row is open. Five lanes 27 px high at y 0, 30, 60, 90 and 120 - frame rate, ticks,
 * ping, memory, CPU - and the time axis from y 150.
 *
 * <p>A lane: a {@link Ui#CARD} ground; its label in RuneScape Small {@link Ui#LABEL} at x 3, baseline y + 11; its
 * value right-aligned at x 210 on the same baseline, with a 1 px black shadow, in its level's text colour (one "-"
 * in {@link Ui#LABEL} when it has none); its line, one point per column in the rows y + 13 .. y + 25, coloured per
 * column by the strip's levels, with a gap where a column has no data. The CPU lane draws the game's share first,
 * grey, and the whole PC's line over it; its value is two halves, the PC's at x 210 and the game's ending 11 px to
 * its left (right-aligned at x 210 when alone). With an event selected the five values are that event's
 * ({@link EventView}).
 *
 * <p>Bands: each event of the range, from the column of its first millisecond to that of its last, at least 2 px
 * wide - {@link Ui#BAND} across the five lanes, {@link Ui#BAND_CULPRIT} in its cause's lane, and for the selected
 * event {@link Ui#BAND_SELECTED} with a 1 px orange frame from y 0 to y 147. The axis: a 1 px {@link Ui#RULE} line
 * at y 150, 3 px ticks at x 0, 71, 142 and 212, labels at baseline 164 ("-60 s", "-40 s", "-20 s" for one minute,
 * else the clock of each tick's column; while stretched, that clock with seconds) and "now" in {@link Ui#TEXT} at
 * the right. The tooltip over the lanes names
 * a column's clock and its six numbers.
 *
 * <p><b>Width.</b> The data is always {@link #COLUMNS} (213) columns; the chart may be wider (230 in the client) and
 * paints to its own width ({@link Ui#widthOf}). Pixel x of a chart {@code width} wide is column
 * {@code x * 213 / width} ({@link #columnAt}), so at 213 the map is the identity and in a wider chart some columns
 * are two pixels wide: column c starts at pixel {@code ceil(c * width / 213)} ({@link #xOf}), and the columns tile
 * the width with no gap. Bands and the selected frame map the same way, the values are right-aligned 3 px in from
 * the right edge, the axis rule fills the width and its ticks sit at 0, a third, two thirds and the last pixel
 * ({@link #tick}); the hover reads its column with the same map.
 *
 * <p>The lane texts are drawn last, each on a flat backing, so no band tint and no frame line runs through their
 * letters (picture 18 backs its Game label so), and every lane text meets the 4.5:1 rule on the card's ground.
 *
 * <p>Choice: a column starts at the first pixel that maps to it (the ceiling), so painting and hovering agree to the
 * pixel; the axis label under a tick is the clock of the column that pixel maps to.
 * <p>Choice: a column's point joins the point before it with a vertical run in the column's own colour.
 * <p>Choice: each lane text sits on a flat CARD backing: its rows and its shadow's, one pixel either side.
 * <p>Choice: bands sharing a column are painted there once, not stacked: culprit over selected over plain.
 * <p>Choice: a scale whose top is not above its bottom puts every point on the lane's lowest row.
 * <p>Choice: the first axis label starts at x 0, the next two are centred on their ticks; "now" ends at x 213.
 * <p>Choice: the label has no shadow; the value has one, and so has the "-" of a lane with no value.
 * <p>Choice: with no snapshot, or a range the column map refuses, no band and no clock is drawn ("-" in a tip).
 */
final class StripChart extends JComponent
{
	static final int HEIGHT = 166;
	static final int LANES = 5;
	static final int LANE_H = 27;
	static final int[] LANE_Y = {0, 30, 60, 90, 120};
	static final int LABEL_X = 3;
	static final int TEXT_BASELINE = 11;
	/** A lane's value is right-aligned here in the 213 px chart; in a wider one 3 px in from the right edge. */
	static final int VALUE_RIGHT = 210;
	/** The line's rows in a lane: y + 13 .. y + 25. */
	static final int LINE_TOP = 13;
	static final int LINE_BOTTOM = 25;
	/** The gap between the CPU lane's two halves. */
	static final int HALF_GAP = 11;
	static final int AXIS_Y = 150;
	static final int TICK_LEN = 3;
	/** The ticks: the start, a third, two thirds and the end (x 0, 71, 142 and 212 at 213). */
	static final int TICK_COUNT = 4;
	static final int AXIS_BASELINE = 164;
	/** The selected band's frame runs from y 0 to this row. */
	static final int FRAME_BOTTOM = 147;
	/** The tooltip answers from y 0 to this row. */
	static final int TIP_BOTTOM = 146;
	static final int COLUMNS = Thresholds.STRIP_COLUMNS;
	static final int MIN_BAND = 2;
	static final String DASH = "-";
	static final String NOW = "now";
	static final String[] ONE_MINUTE = {"-60 s", "-40 s", "-20 s"};
	static final String NO_CPU_DATA = "No CPU data on this PC";
	static final String NOT_LOGGED_IN = "Not logged in";
	/** What covers a column of a lane, weakest first: nothing, a band, the selected band, the culprit's band. */
	private static final byte NO_BAND = 0;
	private static final byte PLAIN_BAND = 1;
	private static final byte SELECTED_BAND = 2;
	private static final byte CULPRIT_BAND = 3;

	private PanelSnapshot snapshot;
	private LagEvent picked;
	private final String[] values = new String[LANES];
	private final Level[] valueLevels = new Level[LANES];
	private String game = "";
	/** Four ints a band: first column, last column, culprit lane (-1 none), 1 when it is the selected event. */
	private int[] bands = new int[0];
	private int bandCount;
	/** The band that covers each column of the lane being painted; reused. */
	private final byte[] kind = new byte[COLUMNS];
	private int paints;

	StripChart()
	{
		setOpaque(true);
		ToolTipManager.sharedInstance().registerComponent(this);
		Arrays.fill(values, "");
		Arrays.fill(valueLevels, Level.NO_DATA);
	}

	/** The snapshot to draw, and the selected event (null = none), whose numbers the lane values then show. */
	void set(PanelSnapshot s, LagEvent selected)
	{
		snapshot = s;
		picked = selected;
		if (selected != null)
		{
			final String[] v = EventView.laneValues(selected);
			final Level[] l = EventView.laneLevels(selected);
			for (int i = 0; i < LANES; i++)
			{
				values[i] = i < v.length && v[i] != null ? v[i] : "";
				valueLevels[i] = i < l.length && l[i] != null ? l[i] : Level.NO_DATA;
			}
			game = EventView.cpuGameValue(selected);
		}
		else
		{
			for (int i = 0; i < LANES; i++)
			{
				final Strip strip = strip(i);
				values[i] = strip == null ? "" : strip.now;
				valueLevels[i] = strip == null || strip.nowLevel == null ? Level.NO_DATA : strip.nowLevel;
			}
			final Strip cpu = strip(Lane.CPU.ordinal());
			game = cpu == null ? "" : cpu.now2;
		}
		placeBands();
	}

	/** How many times this chart has painted: a folded chart is never painted (T17). */
	int paints()
	{
		return paints;
	}

	/** The value at a lane's right as painted: "" = none (one dash is drawn). */
	String value(int lane)
	{
		return values[lane];
	}

	/** The CPU lane's Game half as painted: "" = none. */
	String gameValue()
	{
		return game;
	}

	/** The columns of the band of the event with that id, {first, last}; null when it has none. */
	int[] bandOf(long id)
	{
		if (snapshot == null || !validRange())
		{
			return null;
		}
		for (LagEvent e : snapshot.rangeEvents)
		{
			if (e.id == id)
			{
				return columns(e);
			}
		}
		return null;
	}

	private Strip strip(int lane)
	{
		if (snapshot == null || snapshot.strips == null || lane >= snapshot.strips.length)
		{
			return null;
		}
		return snapshot.strips[lane];
	}

	private boolean validRange()
	{
		return snapshot != null && snapshot.rangeEndWallMs - snapshot.rangeStartWallMs >= COLUMNS;
	}

	/** The first and last column of an event's band, clipped and at least {@link #MIN_BAND} wide; null = out. */
	private int[] columns(LagEvent e)
	{
		final long start = snapshot.rangeStartWallMs;
		final long end = snapshot.rangeEndWallMs;
		int a = Strip.columnOf(start, end, e.startWallMs);
		int b = Strip.columnOf(start, end, e.startWallMs + Math.max(1, e.lengthS()) * 1000L - 1);
		if (b < 0 || a >= COLUMNS)
		{
			return null;
		}
		a = Math.max(0, a);
		b = Math.min(COLUMNS - 1, Math.max(a, b));
		if (b - a + 1 < MIN_BAND)
		{
			if (b < COLUMNS - 1)
			{
				b = a + MIN_BAND - 1;
			}
			else
			{
				a = b - MIN_BAND + 1;
			}
		}
		return new int[] {a, b};
	}

	private void placeBands()
	{
		bandCount = 0;
		if (snapshot == null || !validRange())
		{
			return;
		}
		final List<LagEvent> events = snapshot.rangeEvents;
		if (bands.length < events.size() * 4)
		{
			bands = new int[events.size() * 4];
		}
		for (LagEvent e : events)
		{
			if (e == null || e.open)
			{
				continue;
			}
			final int[] c = columns(e);
			if (c == null)
			{
				continue;
			}
			final Group group = e.group();
			final Lane lane = group == null ? null : group.lane();
			final int k = bandCount * 4;
			bands[k] = c[0];
			bands[k + 1] = c[1];
			bands[k + 2] = lane == null ? -1 : lane.ordinal();
			bands[k + 3] = picked != null && picked.id == e.id ? 1 : 0;
			bandCount++;
		}
	}

	// ------------------------------------------------------------------ the tooltip

	/** The pixel where column c starts in a chart {@code width} wide (c = {@link #COLUMNS} gives the width). */
	static int xOf(int column, int width)
	{
		return (column * width + COLUMNS - 1) / COLUMNS;
	}

	/** The column under pixel x of a chart {@code width} wide, held to 0 .. {@link #COLUMNS} - 1. */
	static int columnAt(int x, int width)
	{
		return Math.max(0, Math.min(COLUMNS - 1, x * COLUMNS / width));
	}

	/** Axis tick i (0 .. 3) of a chart {@code width} wide: 0, a third, two thirds, the last pixel. */
	static int tick(int i, int width)
	{
		return i == TICK_COUNT - 1 ? width - 1 : i * width / (TICK_COUNT - 1);
	}

	@Override
	public String getToolTipText(MouseEvent e)
	{
		if (snapshot == null || e.getY() < 0 || e.getY() > TIP_BOTTOM)
		{
			return null;
		}
		if (e.getY() >= LANE_Y[Lane.CPU.ordinal()] && cpuLaneEmpty())
		{
			return anyColumn() ? NO_CPU_DATA : NOT_LOGGED_IN;
		}
		return columnText(columnAt(e.getX(), Ui.widthOf(this)));
	}

	/** "21:47:30 - 50 fps, ticks 952 ms, ping 41 ms, memory 30 %, PC 37 %, game 95 %" for column c. */
	String columnText(int c)
	{
		final String clock = validRange()
			? Fmt.clockSeconds(Strip.columnStart(snapshot.rangeStartWallMs, snapshot.rangeEndWallMs, c), snapshot.zone)
			: DASH;
		final int fps = at(strip(Lane.FRAME_RATE.ordinal()), c, false);
		final int ticks = at(strip(Lane.TICKS.ordinal()), c, false);
		final int ping = at(strip(Lane.PING.ordinal()), c, false);
		final Strip memory = strip(Lane.MEMORY.ordinal());
		final int heap = at(memory, c, false);
		final int pc = at(strip(Lane.CPU.ordinal()), c, false);
		final int gameShare = at(strip(Lane.CPU.ordinal()), c, true);
		final String memoryText = heap == Strip.NONE || memory.max <= 0
			? "memory -"
			: "memory " + ((long) heap * 100 / memory.max) + " %";
		return clock + " - "
			+ (fps == Strip.NONE ? "fps -" : Fmt.thousands(fps) + " fps") + ", "
			+ (ticks == Strip.NONE ? "ticks -" : "ticks " + Fmt.thousands(ticks) + " ms") + ", "
			+ (ping == Strip.NONE ? "ping -" : "ping " + Fmt.thousands(ping) + " ms") + ", "
			+ memoryText + ", "
			+ (pc == Strip.NONE ? "PC -" : "PC " + Fmt.thousands(pc) + " %") + ", "
			+ (gameShare == Strip.NONE ? "game -" : "game " + Fmt.thousands(gameShare) + " %");
	}

	/** Column c of a strip's first or second series; {@link Strip#NONE} when it has none. */
	private static int at(Strip s, int c, boolean second)
	{
		if (s == null)
		{
			return Strip.NONE;
		}
		final int[] v = second ? s.values2 : s.values;
		return v == null || c < 0 || c >= v.length ? Strip.NONE : v[c];
	}

	/** True when both series of the CPU lane are {@link Strip#NONE} in every column. */
	private boolean cpuLaneEmpty()
	{
		final Strip cpu = strip(Lane.CPU.ordinal());
		return cpu == null || (empty(cpu.values) && empty(cpu.values2));
	}

	/** True when some column of some strip holds a value: some second of the range counts (contract 5.3). */
	private boolean anyColumn()
	{
		for (int i = 0; i < LANES; i++)
		{
			final Strip s = strip(i);
			if (s != null && (!empty(s.values) || !empty(s.values2)))
			{
				return true;
			}
		}
		return false;
	}

	private static boolean empty(int[] v)
	{
		if (v == null)
		{
			return true;
		}
		for (int x : v)
		{
			if (x != Strip.NONE)
			{
				return false;
			}
		}
		return true;
	}

	// ------------------------------------------------------------------ painting

	@Override
	public Dimension getPreferredSize()
	{
		return new Dimension(Ui.WIDTH, HEIGHT);
	}

	@Override
	protected void paintComponent(Graphics graphics)
	{
		paints++;
		final int width = Ui.widthOf(this);
		final Graphics2D g = Ui.prepare(graphics);
		g.setColor(Ui.GROUND);
		g.fillRect(0, 0, width, HEIGHT);
		for (int y : LANE_Y)
		{
			g.setColor(Ui.CARD);
			g.fillRect(0, y, width, LANE_H);
		}
		paintBands(g, width);
		for (int lane = 0; lane < LANES; lane++)
		{
			final Strip s = strip(lane);
			if (s == null)
			{
				continue;
			}
			if (s.values2 != null)
			{
				line(g, s.values2, null, Ui.LABEL, LANE_Y[lane], s.min, s.max, width);
			}
			line(g, s.values, s.levels, null, LANE_Y[lane], s.min, s.max, width);
		}
		paintFrame(g, width);
		for (int lane = 0; lane < LANES; lane++)
		{
			paintLaneTexts(g, lane, width);
		}
		paintAxis(g, width);
	}

	/**
	 * The bands, lane by lane: each column takes the strongest band that covers it - the culprit's, then the
	 * selected event's, then the plain one - and is painted once, so bands that share a column never stack.
	 */
	private void paintBands(Graphics2D g, int width)
	{
		if (bandCount == 0)
		{
			return;
		}
		for (int lane = 0; lane < LANES; lane++)
		{
			Arrays.fill(kind, NO_BAND);
			for (int i = 0; i < bandCount; i++)
			{
				final int k = i * 4;
				final byte mine = lane == bands[k + 2] ? CULPRIT_BAND : bands[k + 3] == 1 ? SELECTED_BAND : PLAIN_BAND;
				for (int c = bands[k]; c <= bands[k + 1]; c++)
				{
					kind[c] = (byte) Math.max(kind[c], mine);
				}
			}
			int start = 0;
			for (int c = 1; c <= COLUMNS; c++)
			{
				if (c == COLUMNS || kind[c] != kind[start])
				{
					if (kind[start] != NO_BAND)
					{
						g.setColor(bandColour(kind[start]));
						g.fillRect(xOf(start, width), LANE_Y[lane], xOf(c, width) - xOf(start, width), LANE_H);
					}
					start = c;
				}
			}
		}
	}

	private static Color bandColour(byte kind)
	{
		return kind == CULPRIT_BAND ? Ui.BAND_CULPRIT : kind == SELECTED_BAND ? Ui.BAND_SELECTED : Ui.BAND;
	}

	private void paintFrame(Graphics2D g, int width)
	{
		for (int i = 0; i < bandCount; i++)
		{
			final int k = i * 4;
			if (bands[k + 3] == 1)
			{
				g.setColor(Ui.ORANGE);
				final int left = xOf(bands[k], width);
				g.drawRect(left, 0, xOf(bands[k + 1] + 1, width) - 1 - left, FRAME_BOTTOM);
			}
		}
	}

	/** One series: a point per column, joined to the column before by a vertical run; a gap at NONE. */
	private static void line(Graphics2D g, int[] v, byte[] levels, Color fixed, int laneY, int min, int max,
		int width)
	{
		if (v == null)
		{
			return;
		}
		boolean joined = false;
		int before = 0;
		for (int c = 0; c < COLUMNS && c < v.length; c++)
		{
			if (v[c] == Strip.NONE)
			{
				joined = false;
				continue;
			}
			final int y = yOf(v[c], laneY, min, max);
			g.setColor(fixed != null ? fixed : columnColour(levels, c));
			final int top = joined ? Math.min(before, y) : y;
			final int bottom = joined ? Math.max(before, y) : y;
			g.fillRect(xOf(c, width), top, xOf(c + 1, width) - xOf(c, width), bottom - top + 1);
			before = y;
			joined = true;
		}
	}

	/** The row of a value on a lane's scale: its top at y + 13, its bottom at y + 25. */
	static int yOf(int value, int laneY, int min, int max)
	{
		if (max <= min)
		{
			return laneY + LINE_BOTTOM;
		}
		final double f = Math.max(0.0, Math.min(1.0, ((double) value - min) / ((double) max - min)));
		return laneY + LINE_BOTTOM - (int) Math.round(f * (LINE_BOTTOM - LINE_TOP));
	}

	private static Color columnColour(byte[] levels, int c)
	{
		if (levels == null || c >= levels.length || levels[c] < 0 || levels[c] >= Level.values().length)
		{
			return Ui.LABEL;
		}
		return Ui.markColour(Level.values()[levels[c]]);
	}

	private void paintLaneTexts(Graphics2D g, int lane, int width)
	{
		final int y = LANE_Y[lane];
		final int valueRight = width - (Ui.WIDTH - VALUE_RIGHT);
		final String label = Lane.values()[lane].label();
		laneText(g, label, LABEL_X, y, Ui.LABEL, false, width);
		if (lane != Lane.CPU.ordinal())
		{
			final String v = values[lane];
			if (v.isEmpty() || DASH.equals(v))
			{
				dash(g, y, valueRight, width);
			}
			else
			{
				laneText(g, v, valueRight - Ui.width(Ui.RSS, v), y, Ui.textColour(valueLevels[lane]), true, width);
			}
			return;
		}
		final String pc = values[lane];
		final boolean drawPc = !pc.isEmpty() && !DASH.equals(pc);
		final boolean drawGame = !game.isEmpty();
		if (!drawPc && !drawGame)
		{
			dash(g, y, valueRight, width);
			return;
		}
		int right = valueRight;
		if (drawPc)
		{
			final int x = right - Ui.width(Ui.RSS, pc);
			laneText(g, pc, x, y, Ui.textColour(valueLevels[lane]), true, width);
			right = x - HALF_GAP;
		}
		if (drawGame)
		{
			laneText(g, game, right - Ui.width(Ui.RSS, game), y, Ui.LABEL, true, width);
		}
	}

	private static void dash(Graphics2D g, int laneY, int valueRight, int width)
	{
		laneText(g, DASH, valueRight - Ui.width(Ui.RSS, DASH), laneY, Ui.LABEL, true, width);
	}

	/**
	 * A lane text at the lane's text baseline, with the 1 px black shadow when {@code shadow}, on its flat
	 * backing: the text's rows and the shadow's, one pixel either side.
	 */
	private static void laneText(Graphics2D g, String text, int x, int laneY, Color colour, boolean shadow,
		int width)
	{
		final int left = Math.max(0, x - 1);
		final int right = Math.min(width, x + Ui.width(Ui.RSS, text) + 2);
		g.setColor(Ui.CARD);
		g.fillRect(left, laneY, right - left, LINE_TOP);
		if (shadow)
		{
			Ui.shadowed(g, text, x, laneY + TEXT_BASELINE, Ui.RSS, colour);
		}
		else
		{
			Ui.text(g, text, x, laneY + TEXT_BASELINE, Ui.RSS, colour);
		}
	}

	private void paintAxis(Graphics2D g, int width)
	{
		g.setColor(Ui.RULE);
		g.fillRect(0, AXIS_Y, width, 1);
		for (int i = 0; i < TICK_COUNT; i++)
		{
			g.fillRect(tick(i, width), AXIS_Y, 1, TICK_LEN);
		}
		Ui.text(g, NOW, width - Ui.width(Ui.RSS, NOW), AXIS_BASELINE, Ui.RSS, Ui.TEXT);
		if (!validRange())
		{
			return;
		}
		for (int i = 0; i < ONE_MINUTE.length; i++)
		{
			final String label = axisLabel(i);
			final int w = Ui.width(Ui.RSS, label);
			final int x = i == 0 ? 0 : tick(i, width) - w / 2;
			Ui.text(g, label, x, AXIS_BASELINE, Ui.RSS, Ui.LABEL);
		}
	}

	/**
	 * The label under tick i (0 .. 2): "-60 s", "-40 s", "-20 s" for one minute, else the clock of its column; while
	 * the graph is stretched, the clock of its column with seconds, the first label the session's first second.
	 * <p>Choice: a stretched span prints seconds ("21:47:30") in every range, since it may be shorter than a minute.
	 */
	String axisLabel(int i)
	{
		final int width = Ui.widthOf(this);
		final int column = columnAt(tick(i, width), width);
		if (snapshot.stretched())
		{
			return Fmt.clockSeconds(Strip.columnStart(snapshot.rangeStartWallMs, snapshot.rangeEndWallMs, column),
				snapshot.zone);
		}
		if (snapshot.rangeMinutes == 1)
		{
			return ONE_MINUTE[i];
		}
		return Fmt.clock(Strip.columnStart(snapshot.rangeStartWallMs, snapshot.rangeEndWallMs, column),
			snapshot.zone);
	}
}
