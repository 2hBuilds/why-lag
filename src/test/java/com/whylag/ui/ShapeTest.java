package com.whylag.ui;

import com.whylag.core.Cell;
import com.whylag.core.LagEvent;
import com.whylag.core.Level;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import org.junit.Test;
import static com.whylag.ui.PanelFixtures.Fixture;
import static com.whylag.ui.PanelFixtures.panel;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * The status shapes (contract 5): each level has its own shape - circle, triangle, square, hollow ring - at the
 * three sizes the panel uses, 7, 8 and 14; and the card, the cells and the list rows never paint a level colour
 * without the shape of that level beside it. The strips are left out on purpose: their lines and bands use the
 * level colours by design.
 */
public class ShapeTest
{
	private static final int[] SIZES = {7, 8, 14};
	private static final Level[] LEVELS = {Level.OK, Level.WARN, Level.BAD, Level.NO_DATA};

	@Test
	public void everyLevelHasItsOwnShape()
	{
		for (int size : SIZES)
		{
			final boolean[][][] masks = new boolean[LEVELS.length][][];
			for (int i = 0; i < LEVELS.length; i++)
			{
				masks[i] = mask(LEVELS[i], size);
			}
			for (int i = 0; i < LEVELS.length; i++)
			{
				for (int j = i + 1; j < LEVELS.length; j++)
				{
					assertFalse(LEVELS[i] + " and " + LEVELS[j] + " at " + size, same(masks[i], masks[j]));
				}
			}
			final int last = size - 1;
			final int mid = size / 2;
			final boolean[][] ok = masks[0];
			final boolean[][] warn = masks[1];
			final boolean[][] bad = masks[2];
			final boolean[][] ring = masks[3];

			assertEquals("BAD fills the square", size * size, count(bad));

			assertTrue("OK: a filled middle", ok[mid][mid] && ok[mid][0] && ok[0][mid] && ok[mid][last]);
			assertFalse("OK: round corners", ok[0][0] || ok[0][last] || ok[last][0] || ok[last][last]);
			assertTrue("OK: symmetric", symmetric(ok));

			assertTrue("WARN: the whole base", row(warn, last) == size);
			assertTrue("WARN: a point at the top", row(warn, 0) >= 1 && row(warn, 0) <= 2);
			for (int y = 1; y < size; y++)
			{
				assertTrue("WARN widens downward, row " + y, row(warn, y) >= row(warn, y - 1));
			}
			assertTrue("WARN: symmetric left and right", mirrored(warn));

			assertFalse("the ring is hollow", ring[mid][mid]);
			assertFalse(ring[0][0]);
			assertTrue("the ring is round", ring[mid][0] && ring[0][mid] && ring[mid][last] && ring[last][mid]);
			final int thickness = Math.max(1, size / 7);
			int band = 0;
			for (int x = 0; x < mid && ring[mid][x]; x++)
			{
				band++;
			}
			assertEquals("the ring's line is max(1, size / 7) at " + size, thickness, band);
			assertTrue(symmetric(ring));
		}
		assertColour(Level.OK, new Color(55, 240, 70));
		assertColour(Level.WARN, new Color(230, 150, 30));
		assertColour(Level.BAD, new Color(230, 30, 30));
		assertColour(Level.NO_DATA, Ui.LABEL);
	}

	/** Over the card, the cells and the list rows of every fixture: a level colour stands only beside its shape. */
	@Test
	public void noLevelColourWithoutAShape()
	{
		int cards = 0;
		int cells = 0;
		int rows = 0;
		for (Fixture f : PanelFixtures.all())
		{
			final WhyLagPanel p = panel(f, true);

			final AnswerCard card = p.card();
			final BufferedImage cardImg = PanelFixtures.paint(card);
			assertOnlyItsLevel(f + " card", cardImg, 0, 0, 213, card.height(), card.level());
			assertShape(f + " card", cardImg, card.level(), 10, 16, 14);
			cards++;

			final BufferedImage cellImg = PanelFixtures.paint(p.cells());
			final Cell[] five = p.cells().cells();
			for (int i = 0; i < five.length; i++)
			{
				final int x = CellStrip.X[i];
				assertOnlyItsLevel(f + " cell " + five[i].name, cellImg, x, 0, CellStrip.W[i], 48, five[i].level);
				assertShape(f + " cell " + five[i].name, cellImg, five[i].level, x + 5, 7, 7);
				cells++;
			}

			final EventList list = p.eventList();
			final BufferedImage listImg = PanelFixtures.paint(list);
			final List<LagEvent> shown = list.rows();
			for (int i = 0; i < shown.size(); i++)
			{
				final LagEvent e = shown.get(i);
				final Level level = e.verdict == null ? Level.BAD : e.verdict.level;
				final int y = EventList.rowY(i);
				assertOnlyItsLevel(f + " row " + i, listImg, 0, y, 213, 24, level);
				assertShape(f + " row " + i, listImg, level, 9, y + 8, 8);
				rows++;
			}
		}
		assertTrue("the check ran over many cards, cells and rows", cards > 20 && cells > 100 && rows > 20);
	}

	/**
	 * The level colours found in a region - OK green, WARN amber, BAD red and its lighter text red - name one level
	 * at most, and it is the level whose shape is painted there. A NO_DATA region shows no level colour at all.
	 */
	private static void assertOnlyItsLevel(String where, BufferedImage img, int x0, int y0, int w, int h, Level level)
	{
		final Set<Level> seen = EnumSet.noneOf(Level.class);
		for (int y = y0; y < y0 + h; y++)
		{
			for (int x = x0; x < x0 + w; x++)
			{
				final int rgb = img.getRGB(x, y);
				if (rgb == Ui.OK.getRGB())
				{
					seen.add(Level.OK);
				}
				else if (rgb == Ui.WARN.getRGB())
				{
					seen.add(Level.WARN);
				}
				else if (rgb == Ui.BAD.getRGB() || rgb == Ui.BAD_TEXT.getRGB())
				{
					seen.add(Level.BAD);
				}
			}
		}
		if (level == Level.NO_DATA)
		{
			assertTrue(where + ": no level colour beside the ring: " + seen, seen.isEmpty());
		}
		else
		{
			assertEquals(where + ": the level colours", EnumSet.of(level), seen);
		}
	}

	/** The painted pixels at (x, y) are exactly the shape of that level: its mask in its colour, the rest not. */
	private static void assertShape(String where, BufferedImage img, Level level, int x, int y, int size)
	{
		final boolean[][] m = mask(level, size);
		final int colour = Ui.markColour(level).getRGB();
		for (int py = 0; py < size; py++)
		{
			for (int px = 0; px < size; px++)
			{
				final boolean painted = img.getRGB(x + px, y + py) == colour;
				assertEquals(where + ": the " + level + " shape at " + px + "," + py, m[py][px], painted);
			}
		}
	}

	private static void assertColour(Level level, Color expected)
	{
		final BufferedImage img = new BufferedImage(14, 14, BufferedImage.TYPE_INT_ARGB);
		final Graphics2D g = img.createGraphics();
		Shape.paint(g, level, 0, 0, 14);
		g.dispose();
		// (7, 0), the top of the middle column, belongs to all four shapes at 14 px.
		assertEquals(level.toString(), expected.getRGB(), img.getRGB(7, 0));
		for (int y = 0; y < 14; y++)
		{
			for (int x = 0; x < 14; x++)
			{
				final int argb = img.getRGB(x, y);
				if ((argb >>> 24) != 0)
				{
					assertEquals(level + ": every pixel of the shape is its colour, at " + x + "," + y,
						expected.getRGB(), argb);
				}
			}
		}
	}

	/** The pixels a shape covers: [y][x]. */
	static boolean[][] mask(Level level, int size)
	{
		final BufferedImage img = new BufferedImage(size + 4, size + 4, BufferedImage.TYPE_INT_ARGB);
		final Graphics2D g = img.createGraphics();
		Shape.paint(g, level, 2, 2, size);
		g.dispose();
		final boolean[][] m = new boolean[size][size];
		for (int y = 0; y < size + 4; y++)
		{
			for (int x = 0; x < size + 4; x++)
			{
				final boolean ink = (img.getRGB(x, y) >>> 24) != 0;
				if (x < 2 || y < 2 || x >= size + 2 || y >= size + 2)
				{
					assertFalse(level + " at " + size + " paints outside its square: " + x + "," + y, ink);
				}
				else
				{
					m[y - 2][x - 2] = ink;
				}
			}
		}
		return m;
	}

	private static boolean same(boolean[][] a, boolean[][] b)
	{
		for (int y = 0; y < a.length; y++)
		{
			for (int x = 0; x < a.length; x++)
			{
				if (a[y][x] != b[y][x])
				{
					return false;
				}
			}
		}
		return true;
	}

	private static int count(boolean[][] m)
	{
		int n = 0;
		for (boolean[] row : m)
		{
			for (boolean b : row)
			{
				n += b ? 1 : 0;
			}
		}
		return n;
	}

	private static int row(boolean[][] m, int y)
	{
		int n = 0;
		for (boolean b : m[y])
		{
			n += b ? 1 : 0;
		}
		return n;
	}

	private static boolean mirrored(boolean[][] m)
	{
		final int n = m.length;
		for (int y = 0; y < n; y++)
		{
			for (int x = 0; x < n; x++)
			{
				if (m[y][x] != m[y][n - 1 - x])
				{
					return false;
				}
			}
		}
		return true;
	}

	private static boolean symmetric(boolean[][] m)
	{
		final int n = m.length;
		for (int y = 0; y < n; y++)
		{
			for (int x = 0; x < n; x++)
			{
				if (m[y][x] != m[n - 1 - y][x] || m[y][x] != m[y][n - 1 - x])
				{
					return false;
				}
			}
		}
		return mirrored(m);
	}
}
