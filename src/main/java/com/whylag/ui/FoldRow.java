package com.whylag.ui;

import com.whylag.core.Fmt;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import javax.swing.JComponent;
import javax.swing.SwingUtilities;

/**
 * Blocks 5 and 6, the fold rows "Graphs" and "Lags (n)" (contract 5): 213 x 26 (as wide as the panel gives it), a {@link Ui#CARD} ground while
 * folded and {@link Ui#CARD_SELECTED} while open. The words in RuneScape white at x 8, baseline 18; the Lags row
 * adds its count in brackets, the number through {@link Fmt#thousands}. A chevron 11 x 7 at (194, 10) - 19 px in
 * from the right edge, so it follows the edge when the row is wider - two 1 px
 * lines in {@link Ui#LABEL}, points down while folded and up while open. One press anywhere on the row opens or
 * closes it; the panel holds the state.
 *
 * <p>Contract 5 names {@link Ui#LABEL} for the count, and asks 4.5:1 for every text of an enabled control; LABEL
 * on {@link Ui#CARD_SELECTED}, the open row's ground, measures 4.48:1, and {@link Ui#TEXT} 6.47:1.
 *
 * <p>Choice: the count is LABEL while the row is folded and TEXT while it is open (the paragraph above).
 * <p>Choice: the chevron's arms are six pixels each, in rows 10 .. 15 of its box, meeting in one pixel at x 199.
 * <p>Choice: the row reacts to the left button's press, and shows the hand cursor.
 */
final class FoldRow extends JComponent
{
	static final int HEIGHT = 26;
	static final int WORD_X = 8;
	static final int BASELINE = 18;
	static final int CHEVRON_X = 194;
	static final int CHEVRON_Y = 10;
	static final int CHEVRON_W = 11;
	/** Each arm of the chevron, in pixels. */
	private static final int ARM = 6;

	private final String word;
	private final boolean counted;
	private boolean open;
	private int count;

	/**
	 * A fold row. {@code counted} rows print their count in brackets after the word ("Lags (3)"); a press runs
	 * {@code onClick}.
	 */
	FoldRow(String word, boolean counted, Runnable onClick)
	{
		this.word = word;
		this.counted = counted;
		setOpaque(true);
		setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		addMouseListener(new MouseAdapter()
		{
			@Override
			public void mousePressed(MouseEvent e)
			{
				if (SwingUtilities.isLeftMouseButton(e))
				{
					onClick.run();
				}
			}
		});
	}

	void setOpen(boolean isOpen)
	{
		open = isOpen;
	}

	boolean isOpen()
	{
		return open;
	}

	/** The count in brackets: the closed events in the range. */
	void setCount(int n)
	{
		count = Math.max(0, n);
	}

	/** What the row says: "Graphs", "Lags (3)". */
	String words()
	{
		return counted ? word + " " + bracket() : word;
	}

	private String bracket()
	{
		return "(" + Fmt.thousands(count) + ")";
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
		g.setColor(open ? Ui.CARD_SELECTED : Ui.CARD);
		g.fillRect(0, 0, width, HEIGHT);
		if (counted)
		{
			final String lead = word + " ";
			Ui.text(g, lead, WORD_X, BASELINE, Ui.RS, Ui.WHITE);
			Ui.text(g, bracket(), WORD_X + Ui.width(Ui.RS, lead), BASELINE, Ui.RS, open ? Ui.TEXT : Ui.LABEL);
		}
		else
		{
			Ui.text(g, word, WORD_X, BASELINE, Ui.RS, Ui.WHITE);
		}
		g.setColor(Ui.LABEL);
		final int left = CHEVRON_X + width - Ui.WIDTH;
		final int last = left + CHEVRON_W - 1;
		for (int i = 0; i < ARM; i++)
		{
			final int row = open ? CHEVRON_Y + ARM - 1 - i : CHEVRON_Y + i;
			g.fillRect(left + i, row, 1, 1);
			g.fillRect(last - i, row, 1, 1);
		}
	}
}
