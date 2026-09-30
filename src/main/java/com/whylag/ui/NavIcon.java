package com.whylag.ui;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Path2D;
import java.awt.image.BufferedImage;

/**
 * The sidebar button's icon, 16 x 16, drawn in code so the panel ships no image file: the 2hBuilds "2h" coin and the
 * plugin's own picture, four rising bars. The user chose it from the rendered sheets of 2026-09-29 (picture 29, R1,
 * then "make the other image 25 % smaller"): a hammered gold coin 9 px across in the top-left corner, its "2h" struck
 * in dark brown, and four bars 3 px wide (green, green, amber, red) rising to 9 px along the bottom (V9 of picture 30, chosen 2026-09-29).
 *
 * <p>The coin is the family mark every 2hBuilds plugin can carry; the bars are this plugin's. The two never touch:
 * the coin sits on the clear ground above the short bars, so its letters stay readable at true size.
 *
 * <p>Choice: the coin's edge is a closed path through sixteen jittered radii, the same every time, so it reads as
 * hammered rather than round; the letters are 7 x 5 pixel glyphs, the smallest "2h" that still reads.
 */
public final class NavIcon
{
	/** The size the sidebar draws a navigation icon at. */
	private static final int SIZE = 16;

	private static final Color RIM = new Color(88, 62, 16);
	private static final Color EDGE = new Color(140, 102, 30);
	private static final Color FACE = new Color(196, 156, 58);
	private static final Color INK = new Color(52, 36, 8);
	private static final Color GREEN = new Color(55, 240, 70);
	private static final Color AMBER = new Color(230, 150, 30);
	private static final Color RED = new Color(230, 30, 30);

	/** The coin's centre and the three radii of its rim, edge and face. */
	private static final double COIN_X = 4.5, COIN_Y = 4.5;
	private static final double[] RADII = {4.7, 3.9, 3.3};
	private static final Color[] RINGS = {RIM, EDGE, FACE};
	/** The hammering: one offset per sixteenth of the edge. */
	private static final double[] JITTER = {0.3, -0.2, 0.4, 0.1, -0.3, 0.2, 0.4, -0.1, 0.2, -0.4, 0.3, 0.0, -0.3, 0.4, -0.1, 0.2};

	/** The letters, one character a pixel: "2" then "h", 3 wide and 5 tall each, a 1 px gap between. */
	private static final String[] TWO = {"###", "..#", "###", "#..", "###"};
	private static final String[] H = {"#..", "#..", "###", "#.#", "#.#"};
	private static final int LETTERS_X = 1, LETTERS_Y = 2;

	/** The bars: left edge, height (75 % of 3, 6, 9, 12) and colour, each 3 px wide with a 1 px gap, standing on the bottom row. */
	private static final int[] BAR_X = {1, 5, 9, 13};
	private static final int[] BAR_HEIGHT = {2, 5, 7, 9};
	private static final Color[] BAR_COLOUR = {GREEN, GREEN, AMBER, RED};
	private static final int BAR_WIDTH = 3;

	private NavIcon()
	{
	}

	/** A new 16 x 16 ARGB image of the icon. */
	public static BufferedImage create()
	{
		final BufferedImage img = new BufferedImage(SIZE, SIZE, BufferedImage.TYPE_INT_ARGB);
		final Graphics2D g = img.createGraphics();
		try
		{
			for (int i = 0; i < BAR_X.length; i++)
			{
				g.setColor(BAR_COLOUR[i]);
				g.fillRect(BAR_X[i], SIZE - BAR_HEIGHT[i], BAR_WIDTH, BAR_HEIGHT[i]);
			}
			g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
			for (int i = 0; i < RADII.length; i++)
			{
				g.setColor(RINGS[i]);
				g.fill(coin(RADII[i]));
			}
			g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
			glyph(g, TWO, LETTERS_X, LETTERS_Y);
			glyph(g, H, LETTERS_X + 4, LETTERS_Y);
		}
		finally
		{
			g.dispose();
		}
		return img;
	}

	private static Path2D coin(double radius)
	{
		final Path2D path = new Path2D.Double();
		for (int i = 0; i < JITTER.length; i++)
		{
			final double angle = i * Math.PI * 2 / JITTER.length;
			final double r = radius + JITTER[i];
			final double x = COIN_X + Math.cos(angle) * r;
			final double y = COIN_Y + Math.sin(angle) * r;
			if (i == 0)
			{
				path.moveTo(x, y);
			}
			else
			{
				path.lineTo(x, y);
			}
		}
		path.closePath();
		return path;
	}

	private static void glyph(Graphics2D g, String[] rows, int x, int y)
	{
		g.setColor(INK);
		for (int r = 0; r < rows.length; r++)
		{
			for (int c = 0; c < rows[r].length(); c++)
			{
				if (rows[r].charAt(c) == '#')
				{
					g.fillRect(x + c, y + r, 1, 1);
				}
			}
		}
	}
}
