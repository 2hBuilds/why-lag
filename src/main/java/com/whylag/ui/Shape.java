package com.whylag.ui;

import com.whylag.core.Level;
import java.awt.Graphics2D;

/**
 * The status shapes (contract 5): every coloured status draws its shape as well as its colour, so no status rests
 * on colour alone. OK a filled circle, WARN a filled triangle pointing up, BAD a filled square, NO_DATA a hollow
 * ring in {@link Ui#LABEL} whose line is {@code max(1, size / 7)} px thick. Sizes used: 14 on the answer card, 7 in
 * the cells, 8 in the list rows.
 *
 * <p>Choice: each shape is set pixel by pixel by one rule per level, the badge's rule of P2.4 scaled to the size.
 *
 * <p>The rule, for a pixel (px, py) of the size x size square, with its centre at (px + 0.5, py + 0.5) and the
 * square's centre at (size / 2, size / 2): the circle holds the pixels within size / 2 of the centre; the triangle
 * those within (py + 1) / 2 of the middle column, so its point is one or two pixels at the top and its base the
 * whole bottom row; the ring those of the circle further than size / 2 - thickness from the centre. So a shape is
 * sharp at every size and exactly the same in every block.
 */
final class Shape
{
	private Shape()
	{
	}

	/** Paints the shape of {@code level}, {@code size} x {@code size}, with its top left at (x, y). */
	static void paint(Graphics2D g, Level level, int x, int y, int size)
	{
		if (size <= 0)
		{
			return;
		}
		final Level l = level == null ? Level.NO_DATA : level;
		g.setColor(Ui.markColour(l));
		for (int py = 0; py < size; py++)
		{
			int run = -1;
			for (int px = 0; px <= size; px++)
			{
				final boolean in = px < size && inside(l, px, py, size);
				if (in && run < 0)
				{
					run = px;
				}
				else if (!in && run >= 0)
				{
					g.fillRect(x + run, y + py, px - run, 1);
					run = -1;
				}
			}
		}
	}

	/** True when pixel (px, py) of a {@code size} square belongs to the shape of {@code level}. */
	private static boolean inside(Level level, int px, int py, int size)
	{
		final double half = size / 2.0;
		final double dx = px + 0.5 - half;
		final double dy = py + 0.5 - half;
		final double d2 = dx * dx + dy * dy;
		switch (level)
		{
			case OK:
				return d2 <= half * half;
			case WARN:
				return Math.abs(dx) <= (py + 1) / 2.0;
			case BAD:
				return true;
			default:
				final double inner = half - Math.max(1, size / 7);
				return d2 <= half * half && d2 > inner * inner;
		}
	}
}
