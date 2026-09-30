package com.whylag.ui;

import com.whylag.core.Fmt;
import com.whylag.core.LagEvent;
import com.whylag.core.Level;
import com.whylag.core.Thresholds;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.LongConsumer;
import javax.swing.JComponent;
import javax.swing.SwingUtilities;
import javax.swing.ToolTipManager;

/**
 * Block 6a, the lag event list behind the "Lags (n)" row (contract 5.4), in the layout only while that row is open.
 * The range's closed events, NEWEST FIRST, at most {@link Thresholds#EVENT_ROWS} rows of 213 x 24 (as wide as the
 * list is: the row fills it and the length stays 6 px in from the right edge), 2 px apart: a
 * {@link Ui#CARD} ground; the verdict's shape, 8 x 8 at x 9 (BAD for an event not judged yet); the start clock in
 * RuneScape {@link Ui#LABEL} at x 23; the group's word in RuneScape {@link Ui#TEXT} at x 63; the length, "14 s",
 * right-aligned at x 207; baseline 17. A selected row has the {@link Ui#CARD_SELECTED} ground, a 3 px orange left
 * edge and white text. The tooltip is the verdict's headline. More rows than fit: a last line "and 3 more" in
 * RuneScape Small {@link Ui#LABEL}, 14 px. No event: one line, 18 px, "Nothing in the last minute." (10, 60:
 * "... 10 minutes.", "... 60 minutes."). An open event is not listed. A press on a row hands its event's id to the
 * panel, which selects it, or clears the selection when it was the selected one.
 *
 * <p>A selected row draws all three of its texts white, the clock included.
 *
 * <p>Choice: an unselected row's 3 px edge shows the row's own ground, as the picture's transparent border does.
 * <p>Choice: the shape sits at y 8 of its row.
 * <p>Choice: the "and 3 more" line is 2 px under the last row, at x 9, baseline 11.
 * <p>Choice: the empty line is drawn at x 0, baseline 13.
 * <p>Choice: a range of another length reads "Nothing in the last {n} minutes."
 * <p>Choice: an event with no verdict has no tooltip.
 * <p>Choice: a press in a 2 px gap, or on the "more" line, does nothing; a hand cursor shows over a row.
 */
final class EventList extends JComponent
{
	static final int ROW_H = 24;
	static final int ROW_GAP = 2;
	static final int MORE_H = 14;
	static final int EMPTY_H = 18;
	static final int EDGE = 3;
	static final int SHAPE_X = 9;
	static final int SHAPE_Y = 8;
	static final int SHAPE_SIZE = 8;
	static final int TIME_X = 23;
	static final int LABEL_X = 63;
	static final int LENGTH_RIGHT = 207;
	static final int BASELINE = 17;
	static final int MORE_X = 9;
	static final int MORE_BASELINE = 11;
	static final int EMPTY_BASELINE = 13;

	private final List<LagEvent> rows = new ArrayList<>();
	private int more;
	private long selectedId = -1;
	private int minutes = 10;
	private ZoneId zone;
	private int height = EMPTY_H;
	private int paints;

	/** A list whose rows hand the id of their event to {@code onRow} when pressed. */
	EventList(LongConsumer onRow)
	{
		setOpaque(true);
		ToolTipManager.sharedInstance().registerComponent(this);
		addMouseListener(new MouseAdapter()
		{
			@Override
			public void mousePressed(MouseEvent e)
			{
				final int row = rowAt(e.getY());
				if (row >= 0 && SwingUtilities.isLeftMouseButton(e))
				{
					onRow.accept(rows.get(row).id);
				}
			}
		});
		addMouseMotionListener(new MouseAdapter()
		{
			@Override
			public void mouseMoved(MouseEvent e)
			{
				setCursor(Cursor.getPredefinedCursor(rowAt(e.getY()) >= 0 ? Cursor.HAND_CURSOR
					: Cursor.DEFAULT_CURSOR));
			}
		});
	}

	/**
	 * The range's events (closed, oldest first), the selected id (-1 none), the range in minutes and the zone of the
	 * clocks. Answers true when the list's height changed.
	 */
	boolean set(List<LagEvent> rangeEvents, long selected, int rangeMinutes, ZoneId clockZone)
	{
		rows.clear();
		int closed = 0;
		final List<LagEvent> events = rangeEvents == null ? Collections.emptyList() : rangeEvents;
		for (int i = events.size() - 1; i >= 0; i--)
		{
			final LagEvent e = events.get(i);
			if (e == null || e.open)
			{
				continue;
			}
			closed++;
			if (rows.size() < Thresholds.EVENT_ROWS)
			{
				rows.add(e);
			}
		}
		more = closed - rows.size();
		selectedId = selected;
		minutes = rangeMinutes;
		zone = clockZone;
		final int old = height;
		height = measure();
		return height != old;
	}

	/** The rows as painted, newest first. */
	List<LagEvent> rows()
	{
		return Collections.unmodifiableList(rows);
	}

	/** How many closed events of the range have no row. */
	int more()
	{
		return more;
	}

	/** How many times this list has painted: a folded list is never painted (T17). */
	int paints()
	{
		return paints;
	}

	/** The words of an empty list for that range. */
	static String emptyWords(int rangeMinutes)
	{
		return rangeMinutes == 1 ? "Nothing in the last minute." : "Nothing in the last " + rangeMinutes + " minutes.";
	}

	/** The words of the "more" line. */
	static String moreWords(int n)
	{
		return "and " + Fmt.thousands(n) + " more";
	}

	/** The row under y, or -1 (a gap, the "more" line, below the rows). */
	int rowAt(int y)
	{
		if (y < 0)
		{
			return -1;
		}
		final int row = y / (ROW_H + ROW_GAP);
		return row < rows.size() && y % (ROW_H + ROW_GAP) < ROW_H ? row : -1;
	}

	/** The top of row i. */
	static int rowY(int i)
	{
		return i * (ROW_H + ROW_GAP);
	}

	private int measure()
	{
		if (rows.isEmpty())
		{
			return EMPTY_H;
		}
		final int h = rows.size() * ROW_H + (rows.size() - 1) * ROW_GAP;
		return more > 0 ? h + ROW_GAP + MORE_H : h;
	}

	@Override
	public String getToolTipText(MouseEvent e)
	{
		final int row = rowAt(e.getY());
		if (row < 0)
		{
			return null;
		}
		final LagEvent ev = rows.get(row);
		return ev.verdict == null || ev.verdict.headline.isEmpty() ? null : ev.verdict.headline;
	}

	@Override
	public Dimension getPreferredSize()
	{
		return new Dimension(Ui.WIDTH, height);
	}

	@Override
	protected void paintComponent(Graphics graphics)
	{
		paints++;
		final int width = Ui.widthOf(this);
		final Graphics2D g = Ui.prepare(graphics);
		g.setColor(Ui.GROUND);
		g.fillRect(0, 0, width, height);
		if (rows.isEmpty())
		{
			Ui.text(g, emptyWords(minutes), 0, EMPTY_BASELINE, Ui.RSS, Ui.LABEL);
			return;
		}
		for (int i = 0; i < rows.size(); i++)
		{
			paintRow(g, rows.get(i), rowY(i), width);
		}
		if (more > 0)
		{
			final int top = rowY(rows.size() - 1) + ROW_H + ROW_GAP;
			Ui.text(g, moreWords(more), MORE_X, top + MORE_BASELINE, Ui.RSS, Ui.LABEL);
		}
	}

	private void paintRow(Graphics2D g, LagEvent e, int y, int width)
	{
		final boolean chosen = e.id == selectedId;
		g.setColor(chosen ? Ui.CARD_SELECTED : Ui.CARD);
		g.fillRect(0, y, width, ROW_H);
		if (chosen)
		{
			g.setColor(Ui.ORANGE);
			g.fillRect(0, y, EDGE, ROW_H);
		}
		final Level level = e.verdict == null || e.verdict.level == null ? Level.BAD : e.verdict.level;
		Shape.paint(g, level, SHAPE_X, y + SHAPE_Y, SHAPE_SIZE);
		final Color time = chosen ? Ui.WHITE : Ui.LABEL;
		final Color words = chosen ? Ui.WHITE : Ui.TEXT;
		Ui.text(g, Fmt.clock(e.startWallMs, zone), TIME_X, y + BASELINE, Ui.RS, time);
		Ui.text(g, e.group().label(), LABEL_X, y + BASELINE, Ui.RS, words);
		final String length = e.lengthS() + " s";
		Ui.text(g, length, width - (Ui.WIDTH - LENGTH_RIGHT) - Ui.width(Ui.RS, length), y + BASELINE, Ui.RS, words);
	}
}
