package com.whylag.ui;

import com.whylag.core.Cell;
import com.whylag.core.Cells;
import com.whylag.core.Group;
import com.whylag.core.Level;
import com.whylag.core.PanelSnapshot;
import com.whylag.core.Strip;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.event.MouseEvent;
import javax.swing.JComponent;
import javax.swing.ToolTipManager;

/**
 * Block 3, the five small cells (contract 5.2): 213 x 48, the cells at x 0, 43, 86, 129 and 172, the first four 40
 * px wide and the fifth 41, so the strip ends at x 213. In a wider strip the cells share the width less four 3 px
 * gaps ({@link #cellX}, {@link #cellW}): each is (width - 12) / 5 wide and the pixels left over go one each to the
 * LAST cells, which is what makes the 213 layout above the same rule. It paints {@link Cell}s as {@link Cells} made
 * them and holds no rule of its own.
 *
 * <p>One cell: a {@link Ui#CARD} ground; the level's shape, 7 x 7 at (5, 7); the name in RuneScape Small
 * {@link Ui#LABEL} at x 15, baseline 14; the value in white at x 5, baseline 30, in RuneScape Bold when it is 35 px
 * or less, else RuneScape when that is, else RuneScape Small; the unit in RuneScape Small {@link Ui#LABEL} at x 5,
 * baseline 43. The culprit cell has a 1 px border in its level's colour, the ground {@link Ui#CULPRIT}, its value in
 * {@link Ui#BAD_TEXT} and its name and unit in {@link Ui#CULPRIT_LABEL}. The tooltip is the cell's tip.
 *
 * <p>Choice: the leftover pixels go to the cells from the RIGHT, not from the left as the plan of 2026-09-29 says:
 * the 213 layout has always given its one leftover pixel to the fifth cell, and nothing may move at 213.
 * <p>Choice: the text inside a cell keeps its 35 px room and its insets in a wider cell; only the ground grows.
 * <p>Choice: a cell that is not the culprit draws no border: a border in the ground's colour is the ground.
 * <p>Choice: before the first snapshot the five cells are the dashes of an empty snapshot, with no tooltip.
 * <p>Choice: the 3 px gaps between the cells have no tooltip.
 */
final class CellStrip extends JComponent
{
	static final int HEIGHT = 48;
	/** The left edge and the width of each cell. */
	static final int[] X = {0, 43, 86, 129, 172};
	static final int[] W = {40, 40, 40, 40, 41};
	static final int GAP = 3;
	/** The text's box in a cell: x 5 .. 39, 35 px. */
	static final int TEXT_X = 5;
	static final int TEXT_ROOM = 35;
	static final int NAME_X = 15;
	static final int NAME_BASELINE = 14;
	static final int VALUE_BASELINE = 30;
	static final int UNIT_BASELINE = 43;
	static final int SHAPE_X = 5;
	static final int SHAPE_Y = 7;
	static final int SHAPE_SIZE = 7;

	private Cell[] cells = blank();

	CellStrip()
	{
		setOpaque(true);
		ToolTipManager.sharedInstance().registerComponent(this);
	}

	/** The five cells to paint, in lane order. */
	void set(Cell[] five)
	{
		cells = five == null ? blank() : five;
	}

	Cell[] cells()
	{
		return cells;
	}

	/** The face a value is painted in: the boldest of the three in which it fits 35 px. */
	static Font valueFace(String value)
	{
		if (Ui.width(Ui.RSB, value) <= TEXT_ROOM)
		{
			return Ui.RSB;
		}
		return Ui.width(Ui.RS, value) <= TEXT_ROOM ? Ui.RS : Ui.RSS;
	}

	/** Cell i's width in a strip {@code width} wide; the leftover pixels go one each to the last cells. */
	static int cellW(int i, int width)
	{
		final int room = width - (X.length - 1) * GAP;
		return room / X.length + (i >= X.length - room % X.length ? 1 : 0);
	}

	/** Cell i's left edge in a strip {@code width} wide. */
	static int cellX(int i, int width)
	{
		int x = 0;
		for (int k = 0; k < i; k++)
		{
			x += cellW(k, width) + GAP;
		}
		return x;
	}

	/** The cell under x in the 213 px strip, -1 in a gap or outside. */
	static int cellAt(int x)
	{
		return cellAt(x, Ui.WIDTH);
	}

	/** The cell under x in a strip {@code width} wide, -1 in a gap or outside. */
	static int cellAt(int x, int width)
	{
		for (int i = 0; i < X.length; i++)
		{
			if (x >= cellX(i, width) && x < cellX(i, width) + cellW(i, width))
			{
				return i;
			}
		}
		return -1;
	}

	@Override
	public String getToolTipText(MouseEvent e)
	{
		final int i = cellAt(e.getX(), Ui.widthOf(this));
		if (i < 0 || i >= cells.length || cells[i] == null || cells[i].tip.isEmpty())
		{
			return null;
		}
		return cells[i].tip;
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
		for (int i = 0; i < X.length && i < cells.length; i++)
		{
			if (cells[i] != null)
			{
				paintCell(g, cells[i], cellX(i, width), cellW(i, width));
			}
		}
	}

	private static void paintCell(Graphics2D g, Cell c, int x, int w)
	{
		final Level level = c.level == null ? Level.NO_DATA : c.level;
		g.setColor(c.culprit ? Ui.CULPRIT : Ui.CARD);
		g.fillRect(x, 0, w, HEIGHT);
		if (c.culprit)
		{
			g.setColor(Ui.edgeColour(level));
			g.drawRect(x, 0, w - 1, HEIGHT - 1);
		}
		Shape.paint(g, level, x + SHAPE_X, SHAPE_Y, SHAPE_SIZE);
		final Color quiet = c.culprit ? Ui.CULPRIT_LABEL : Ui.LABEL;
		Ui.text(g, c.name, x + NAME_X, NAME_BASELINE, Ui.RSS, quiet);
		Ui.text(g, c.value, x + TEXT_X, VALUE_BASELINE, valueFace(c.value), c.culprit ? Ui.BAD_TEXT : Ui.WHITE);
		Ui.text(g, c.unit, x + TEXT_X, UNIT_BASELINE, Ui.RSS, quiet);
	}

	/** The cells before the first snapshot: the dashes of an empty snapshot, with no tooltip. */
	private static Cell[] blank()
	{
		final Strip[] none = new Strip[0];
		final Cell[] made = Cells.now(new PanelSnapshot(0, null, 0, null, null, 0, 0, 0, none, null, null,
			new int[Group.values().length], 0, 0, -1, -1, null, ""));
		final Cell[] out = new Cell[made.length];
		for (int i = 0; i < made.length; i++)
		{
			final Cell c = made[i];
			out[i] = new Cell(c.lane, c.name, c.value, c.unit, c.level, false, "");
		}
		return out;
	}
}
