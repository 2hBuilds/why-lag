package com.whylag.ui;

import com.whylag.GraphRange;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.function.IntConsumer;
import javax.swing.JComponent;
import javax.swing.SwingUtilities;

/**
 * Block 4, the range row (contract 5): 213 x 23. "Last" in RuneScape Small {@link Ui#LABEL} at x 0, baseline 16,
 * then three chips 58 x 23 at x 33, 94 and 155 (3 px apart; the last ends at x 213): "1 min", "10 min", "60 min".
 * In a wider row "Last" keeps its 33 px and the chips share the rest ({@link #chipX}, {@link #chipW}): each is
 * (width - 33 - 6) / 3 wide, the pixels left over going to the chips from the left, one each.
 * A chip is a {@link Ui#CARD} ground with a 1 px {@link Ui#RULE} border and its words in RuneScape Small
 * {@link Ui#LABEL}, centred, baseline 16; the chosen one has an {@link Ui#ORANGE} border and white words. A press
 * on a chip hands its minutes to the panel, which tells {@code PanelActions.rangeChanged}. The row is always in
 * view: the range sets both the graphs and the list.
 *
 * <p>While the graphs are stretched (the session holds less than the range) a grey note says how much is held,
 * "30 s" over "so far", in {@link Ui#LABEL} in place of "Last", baselines 10 and 21.
 * <p>Choice: the note takes the place of "Last": the 33 px before the first chip is the only free room in the row, and
 * two short lines fit it, so no chip and no block moves.
 *
 * <p>Choice: a chip reacts to the left button's press, and the hand cursor shows over the chips.
 */
final class RangeRow extends JComponent
{
	static final int HEIGHT = 23;
	static final int[] CHIP_X = {33, 94, 155};
	static final int CHIP_W = 58;
	static final int CHIP_GAP = 3;
	static final int BASELINE = 16;
	static final String LAST = "Last";
	static final String SO_FAR = "so far";
	static final int NOTE_BASELINE_1 = 10;
	static final int NOTE_BASELINE_2 = 21;
	/** The note's room: up to the first chip, less a pixel of air. */
	static final int NOTE_ROOM = 32;

	private final GraphRange[] ranges = GraphRange.values();
	private int minutes;
	/** How much the stretched graphs hold ("30 s", "4 min"); "" while they are not stretched. */
	private String held = "";

	RangeRow(IntConsumer onChip)
	{
		setOpaque(true);
		addMouseListener(new MouseAdapter()
		{
			@Override
			public void mousePressed(MouseEvent e)
			{
				final int chip = chipAt(e.getX(), e.getY(), Ui.widthOf(RangeRow.this));
				if (chip >= 0 && SwingUtilities.isLeftMouseButton(e))
				{
					onChip.accept(ranges[chip].minutes());
				}
			}
		});
		addMouseMotionListener(new MouseAdapter()
		{
			@Override
			public void mouseMoved(MouseEvent e)
			{
				setCursor(Cursor.getPredefinedCursor(chipAt(e.getX(), e.getY(), Ui.widthOf(RangeRow.this)) >= 0
					? Cursor.HAND_CURSOR : Cursor.DEFAULT_CURSOR));
			}
		});
	}

	/** The chosen range, 1, 10 or 60. */
	void set(int chosenMinutes)
	{
		minutes = chosenMinutes;
	}

	int minutes()
	{
		return minutes;
	}

	/** The note's amount while the graphs are stretched ("30 s"); "" or null for none. */
	void held(String amount)
	{
		held = amount == null ? "" : amount;
	}

	String held()
	{
		return held;
	}

	/** Chip i's width in a row {@code width} wide: the room after "Last" less two gaps, over three; the rest from the left. */
	static int chipW(int i, int width)
	{
		final int room = width - CHIP_X[0] - 2 * CHIP_GAP;
		return room / CHIP_X.length + (i < room % CHIP_X.length ? 1 : 0);
	}

	/** Chip i's left edge in a row {@code width} wide. */
	static int chipX(int i, int width)
	{
		int x = CHIP_X[0];
		for (int k = 0; k < i; k++)
		{
			x += chipW(k, width) + CHIP_GAP;
		}
		return x;
	}

	/** The chip under (x, y) in a row {@code width} wide, 0 .. 2, or -1. */
	static int chipAt(int x, int y, int width)
	{
		if (y < 0 || y >= HEIGHT)
		{
			return -1;
		}
		for (int i = 0; i < CHIP_X.length; i++)
		{
			if (x >= chipX(i, width) && x < chipX(i, width) + chipW(i, width))
			{
				return i;
			}
		}
		return -1;
	}

	@Override
	public Dimension getPreferredSize()
	{
		return new Dimension(Ui.WIDTH, HEIGHT);
	}

	@Override
	protected void paintComponent(Graphics graphics)
	{
		final Graphics2D g = Ui.prepare(graphics);
		final int width = Ui.widthOf(this);
		g.setColor(Ui.GROUND);
		g.fillRect(0, 0, width, HEIGHT);
		if (held.isEmpty())
		{
			Ui.text(g, LAST, 0, BASELINE, Ui.RSS, Ui.LABEL);
		}
		else
		{
			Ui.text(g, Ui.fit(Ui.RSS, held, NOTE_ROOM), 0, NOTE_BASELINE_1, Ui.RSS, Ui.LABEL);
			Ui.text(g, Ui.fit(Ui.RSS, SO_FAR, NOTE_ROOM), 0, NOTE_BASELINE_2, Ui.RSS, Ui.LABEL);
		}
		for (int i = 0; i < CHIP_X.length && i < ranges.length; i++)
		{
			final boolean chosen = ranges[i].minutes() == minutes;
			final int x = chipX(i, width);
			final int w = chipW(i, width);
			g.setColor(Ui.CARD);
			g.fillRect(x, 0, w, HEIGHT);
			g.setColor(chosen ? Ui.ORANGE : Ui.RULE);
			g.drawRect(x, 0, w - 1, HEIGHT - 1);
			final String words = ranges[i].toString();
			Ui.text(g, words, x + (w - Ui.width(Ui.RSS, words)) / 2, BASELINE, Ui.RSS,
				chosen ? Ui.WHITE : Ui.LABEL);
		}
	}
}
