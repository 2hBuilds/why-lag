package com.whylag.ui;

import com.whylag.core.Cause;
import com.whylag.core.Confidence;
import com.whylag.core.LagEvent;
import com.whylag.core.Lane;
import com.whylag.core.Level;
import com.whylag.core.Strip;
import com.whylag.core.Verdict;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import javax.imageio.ImageIO;
import javax.swing.JComponent;
import org.junit.Test;
import static com.whylag.ui.PanelFixtures.Fixture;
import static com.whylag.ui.PanelFixtures.Lanes;
import static com.whylag.ui.PanelFixtures.NOW;
import static com.whylag.ui.PanelFixtures.SENTINEL;
import static com.whylag.ui.PanelFixtures.StubActions;
import static com.whylag.ui.PanelFixtures.is;
import static com.whylag.ui.PanelFixtures.onEdt;
import static com.whylag.ui.PanelFixtures.pixel;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

/**
 * The panel fills the sidebar (the user, 2026-09-29: "left ... and we fill all the way to the right as well"): the
 * blocks start 6 px from the left edge and run to 6 px from the right edge - 230 px wide in the client's 242, 223
 * while the scroll bar shows - and at the panel's own 225 they are 213 wide and look exactly as they did. The eight
 * tests of {@code docs/why-lag-plan-fill-width-2026-09-29.md}, T1 to T8.
 *
 * <p>Choice: where a test reads the picture, it reads the pixels the blocks painted, not the constants they paint from.
 */
public class PanelFillTest
{
	private static final String FOOTER = "self: frame 180 ns, tick 2 us, step 40 us";

	// ------------------------------------------------------------------------------------------------ T1

	/** T1: at 225, 235 and 242 wide every block starts at x 6 and is 213, 223 and 230 wide; at 200 it is held at 213. */
	@Test
	public void theBlocksStartAtSixAndFillTheWidth()
	{
		final int[][] cases = {{225, 213}, {235, 223}, {242, 230}, {200, 213}};
		for (Fixture f : both())
		{
			final WhyLagPanel p = devPanel(f);
			for (int[] c : cases)
			{
				layOutAt(p, c[0]);
				int seen = 0;
				for (JComponent block : PanelFixtures.blocks(p))
				{
					if (!block.isVisible())
					{
						continue;
					}
					seen++;
					final String where = f + " at " + c[0] + ", " + block.getClass().getSimpleName();
					assertEquals(where, 6, block.getX());
					assertEquals(where, c[1], block.getWidth());
					assertEquals(where + ": the preferred width stays 213", 213, block.getPreferredSize().width);
				}
				assertTrue(f + ": the panel has blocks (" + seen + ")", seen >= 10);
			}
		}
	}

	// ------------------------------------------------------------------------------------------------ T2

	/** T2: painted with no clip into an image 40 px wider than the block, nothing lands right of the block's width. */
	@Test
	public void nothingIsPaintedPastTheBlock()
	{
		int checked = 0;
		for (Fixture f : both())
		{
			final WhyLagPanel p = devPanel(f);
			for (int panelWidth : new int[] {235, 242})
			{
				layOutAt(p, panelWidth);
				for (JComponent block : PanelFixtures.blocks(p))
				{
					final String where = f + " at " + panelWidth + ", " + block.getClass().getSimpleName();
					final int w = block.getWidth();
					final BufferedImage img = paintAsLaidOut(block, 40);
					int painted = 0;
					for (int y = 0; y < img.getHeight(); y++)
					{
						for (int x = 0; x < img.getWidth(); x++)
						{
							if (is(img, x, y, SENTINEL))
							{
								continue;
							}
							if (x >= w)
							{
								throw new AssertionError(where + ": painted at " + x + "," + y + " (width " + w + ")");
							}
							painted++;
						}
					}
					assertTrue(where + ": it painted something", painted > 0);
					checked++;
				}
			}
		}
		assertTrue("blocks checked: " + checked, checked >= 40);
	}

	// ------------------------------------------------------------------------------------------------ T3

	/** T3: at 230 the last pixel column of every block that has a ground holds that ground or its border, not the grey. */
	@Test
	public void theRightEdgeIsUsed()
	{
		for (Fixture f : both())
		{
			final WhyLagPanel p = devPanel(f);
			layOutAt(p, 242);
			final int last = 229;

			final AnswerCard card = p.card();
			assertEquals(230, card.getWidth());
			assertEquals(f + " card", Ui.CARD, pixel(paintAsLaidOut(card, 0), last, card.getHeight() / 2));

			assertEquals(f + " cells", Ui.CARD, pixel(paintAsLaidOut(p.cells(), 0), last, 47));

			final BufferedImage range = paintAsLaidOut(p.rangeRow(), 0);
			final Color chipEdge = pixel(range, last, 10);
			assertTrue(f + " the last chip's border: " + chipEdge, chipEdge.equals(Ui.RULE) || chipEdge.equals(Ui.ORANGE));

			assertEquals(f + " graphs row", Ui.CARD_SELECTED, pixel(paintAsLaidOut(p.graphsRow(), 0), last, 13));
			assertEquals(f + " lags row", Ui.CARD_SELECTED, pixel(paintAsLaidOut(p.lagsRow(), 0), last, 13));

			final BufferedImage strips = paintAsLaidOut(p.strips(), 0);
			for (int y : StripChart.LANE_Y)
			{
				assertEquals(f + " lane at " + y, Ui.CARD, pixel(strips, last, y + 26));
			}
			assertEquals(f + " axis", Ui.RULE, pixel(strips, last, StripChart.AXIS_Y));

			if (!p.eventList().rows().isEmpty())
			{
				assertEquals(f + " selected row", Ui.CARD_SELECTED, pixel(paintAsLaidOut(p.eventList(), 0), last, 12));
			}


			final HeaderRow header = p.header();
			assertEquals(230, header.getWidth());
			final java.awt.Rectangle gear = header.gearBounds();
			assertEquals(f + " the gear ends at the edge", 230, gear.x + gear.width);
			final BufferedImage head = paintAsLaidOut(header, 0);
			boolean inked = false;
			for (int y = gear.y; y < gear.y + gear.height; y++)
			{
				inked |= !pixel(head, last, y).equals(Ui.GROUND);
			}
			assertTrue(f + " the gear's last column is inked", inked);
		}
	}

	// ------------------------------------------------------------------------------------------------ T4

	/** T4: at 213, 223 and 230 the three cells and their two 3 px gaps add up to the width, 1 px apart at most. */
	@Test
	public void theThreeCellsShareTheWidth()
	{
		for (int panelWidth : new int[] {225, 235, 242})
		{
			final WhyLagPanel p = PanelFixtures.panel(PanelFixtures.quiet(), true);
			layOutAt(p, panelWidth);
			final int width = panelWidth - 12;
			assertEquals(width, p.cells().getWidth());
			final BufferedImage strip = paintAsLaidOut(p.cells(), 0);
			final List<int[]> runs = runs(strip, 47, Ui.CARD);
			assertEquals("three cells at " + width, 3, runs.size());
			assertTrue("at " + width + " the gaps are the ground, 3 px", gapsAre(strip, 47, runs, 3));
			assertEquals(0, runs.get(0)[0]);
			assertEquals(width, runs.get(2)[1]);
			shareTheWidth("cells at " + width, runs, width, 2 * 3);
		}
	}

	// ------------------------------------------------------------------------------------------------ T5

	/**
	 * T5: the three chips share the width the same way, a press in the middle of each PAINTED chip selects that chip,
	 * and the header's gear (1.0.1, lot C) is pressed over its whole hit area, from its first column to the row's
	 * last, and not one column further left.
	 */
	@Test
	public void theChipsShareTheWidthAndTheGearTakesItsPresses()
	{
		for (int panelWidth : new int[] {225, 235, 242})
		{
			final int width = panelWidth - 12;
			final StubActions actions = new StubActions();
			final WhyLagPanel p = PanelFixtures.panel(PanelFixtures.quiet(), true, true, false, actions);
			layOutAt(p, panelWidth);

			// The chips: the row's top border is the chips' edges (the chosen chip's is orange, the rest's grey).
			final BufferedImage row = paintAsLaidOut(p.rangeRow(), 0);
			final List<int[]> chips = runs(row, 0, Ui.RULE, Ui.ORANGE);
			assertEquals("three chips at " + width, 3, chips.size());
			assertTrue("3 px between the chips at " + width, gapsAre(row, 0, chips, 3));
			assertEquals("the last chip ends at the edge", width, chips.get(2)[1]);
			assertEquals("'Last' keeps its 33 px", 33, chips.get(0)[0]);
			shareTheWidth("chips at " + width, chips, width - 33, 2 * 3);
			assertTrue("the leftover pixels go to the left chips",
				width(chips.get(0)) >= width(chips.get(1)) && width(chips.get(1)) >= width(chips.get(2)));

			// A press in the middle of each painted chip selects it: 10 min, then 60, then 1 (the chosen one).
			final int[] wanted = {10, 60, 1};
			final int[] order = {1, 2, 0};
			for (int k = 0; k < 3; k++)
			{
				final int[] chip = chips.get(order[k]);
				PanelFixtures.press(p.rangeRow(), (chip[0] + chip[1] - 1) / 2, 11);
				assertEquals("chip " + order[k] + " at " + width, k + 1, actions.ranges.size());
				assertEquals(wanted[k], (int) actions.ranges.get(k));
			}

			// The gear: a press on the first and on the last column of its hit area, a press anywhere in the row's
			// height there, opens the menu (built fresh each time); one column further left opens nothing.
			final int from = width - HeaderRow.GEAR - 4;
			final javax.swing.JPopupMenu none = p.menu();
			PanelFixtures.press(p.header(), from - 1, 10);
			assertSame("one column left of the hit area: nothing", none, p.menu());
			for (int[] at : new int[][] {{from, 0}, {(from + width) / 2, 10}, {width - 1, HeaderRow.HEIGHT - 1}})
			{
				final javax.swing.JPopupMenu before = p.menu();
				PanelFixtures.press(p.header(), at[0], at[1]);
				assertNotSame(width + ": a press at " + at[0] + "," + at[1] + " opens the menu", before, p.menu());
			}
		}
	}

	// ------------------------------------------------------------------------------------------------ T6

	/**
	 * T6: at 230 the chart's 213 columns are stretched: pixel x is column {@code x * 213 / 230}. A strip whose last
	 * column alone holds a value paints it at the right edge, and a band over columns 100 to 110 paints exactly the
	 * pixels that map to those columns - from the first pixel of column 100 (108, the ceiling of 100 * 230 / 213) to
	 * the last of column 110 (119).
	 */
	@Test
	public void theGraphStretchesItsColumns()
	{
		final int width = 230;

		// The last column alone.
		final Lanes lanes = new Lanes(10).none(Lane.FRAME_RATE);
		lanes.values[Lane.FRAME_RATE.ordinal()][212] = 50;
		lanes.levels[Lane.FRAME_RATE.ordinal()][212] = (byte) Level.OK.ordinal();
		final Fixture last = new Fixture("last-column", PanelFixtures.snapshot(PanelFixtures.WORLD,
			PanelFixtures.clearAfter(PanelFixtures.WORLD), PanelFixtures.quietTiles(), 10, lanes.build(),
			Collections.emptyList()), -1);
		final WhyLagPanel p = PanelFixtures.panel(last, true);
		layOutAt(p, 242);
		assertEquals(width, p.strips().getWidth());
		final BufferedImage img = paintAsLaidOut(p.strips(), 0);
		final int row = 15;
		int lit = 0;
		int firstLit = -1;
		for (int x = 0; x < width; x++)
		{
			final boolean expected = x * 213 / width == 212;
			assertEquals("column 212 owns pixel " + x, expected, is(img, x, row, Ui.OK));
			if (expected)
			{
				lit++;
				firstLit = firstLit < 0 ? x : firstLit;
			}
		}
		assertTrue("the last column has pixels: " + lit, lit >= 1);
		assertEquals("and it reaches the right edge", width - 1, firstLit + lit - 1);

		// A band over columns 100 .. 110.
		final long start = NOW - 10 * PanelFixtures.MIN;
		final long from = Strip.columnStart(start, NOW, 100);
		final Verdict v = PanelFixtures.eventVerdict(Cause.SLOW_WORLD, 9, from, 30);
		final LagEvent band = PanelFixtures.event(9, from, 30, PanelFixtures.WORLD, v, 50, 34, 952, 1240, 640, 41,
			41);
		final Fixture banded = new Fixture("banded", PanelFixtures.snapshot(PanelFixtures.WORLD,
			PanelFixtures.clearAfter(PanelFixtures.WORLD), PanelFixtures.quietTiles(), 10, new Lanes(10).build(),
			Collections.singletonList(band)), -1);
		final WhyLagPanel q = PanelFixtures.panel(banded, true);
		layOutAt(q, 242);
		final int[] columns = q.strips().bandOf(9);
		assertEquals(100, columns[0]);
		assertEquals(110, columns[1]);
		final BufferedImage bandImg = paintAsLaidOut(q.strips(), 0);
		final Color plain = PanelFixtures.over(Ui.BAND, Ui.CARD);
		int firstBand = -1;
		int lastBand = -1;
		for (int x = 0; x < width; x++)
		{
			final int column = x * 213 / width;
			final boolean inside = column >= 100 && column <= 110;
			assertEquals("pixel " + x + " (column " + column + ") of lane 0", inside ? plain : Ui.CARD,
				pixel(bandImg, x, 26));
			if (inside)
			{
				firstBand = firstBand < 0 ? x : firstBand;
				lastBand = x;
			}
		}
		assertEquals("the band starts where column 100 starts", 108, firstBand);
		assertEquals("and ends where column 110 ends", 119, lastBand);
		assertTrue("the ticks: 0, a third, two thirds and the last pixel", is(bandImg, 0, StripChart.AXIS_Y, Ui.RULE)
			&& is(bandImg, 229, StripChart.AXIS_Y, Ui.RULE));
		for (int tick : new int[] {0, 230 / 3, 2 * 230 / 3, 229})
		{
			assertTrue("a tick at " + tick, is(bandImg, tick, StripChart.AXIS_Y + StripChart.TICK_LEN - 1, Ui.RULE));
			assertTrue("no tick next to it", tick == 0 || is(bandImg, tick - 1, StripChart.AXIS_Y + StripChart.TICK_LEN - 1,
				Ui.GROUND));
		}
	}

	// ------------------------------------------------------------------------------------------------ T7

	/**
	 * T7: a proof that needs three lines at 213 needs two at 230, and the card's height and the panel's preferred
	 * height follow the width.
	 */
	@Test
	public void aWrappingBlockGrowsShorterWhenWider()
	{
		// The shortest text that is three lines in 196 px and two in 213 (the card's room at 213 and at 230).
		final StringBuilder text = new StringBuilder("lag");
		String proof = null;
		for (int n = 1; n < 200 && proof == null; n++)
		{
			text.append(" lag");
			if (Ui.wrap(Ui.RS, text.toString(), 196, 196, 99).size() == 3
				&& Ui.wrap(Ui.RS, text.toString(), 213, 213, 99).size() == 2)
			{
				proof = text.toString();
			}
		}
		assertNotNull("a text of three lines at 196 and two at 213 exists", proof);

		final Verdict v = new Verdict(Cause.SLOW_WORLD, Confidence.LIKELY, Level.BAD, "World 416 is struggling, not you",
			proof, "Hop to a quieter world.", "Frames and ping were fine.", PanelFixtures.D_START, 14,
			PanelFixtures.WORLD, 3, null, null);
		final Fixture f = new Fixture("wrapping", PanelFixtures.snapshot(PanelFixtures.WORLD, v,
			PanelFixtures.quietTiles(), 10, new Lanes(10).build(), Collections.emptyList()), -1);
		final WhyLagPanel p = PanelFixtures.panel(f, true);

		layOutAt(p, 225);
		final int lines213 = p.card().proofLines().size();
		final int card213 = p.card().getHeight();
		final int panel213 = onEdt(() -> p.getPreferredSize().height);
		assertEquals(3, lines213);
		assertEquals(213, p.card().getWidth());

		layOutAt(p, 242);
		final int lines230 = p.card().proofLines().size();
		assertEquals(230, p.card().getWidth());
		assertEquals(2, lines230);
		assertTrue("fewer or equal lines", lines230 <= lines213);
		assertEquals("the card is one line (19 px) shorter", card213 - AnswerCard.LINE * (lines213 - lines230),
			p.card().getHeight());
		assertEquals("its preferred height agrees", p.card().getHeight(), p.card().getPreferredSize().height);
		assertEquals("the panel's preferred height follows", panel213 - AnswerCard.LINE * (lines213 - lines230),
			(int) onEdt(() -> p.getPreferredSize().height));
		final JComponent lastBlock = PanelFixtures.blocks(p).get(PanelFixtures.blocks(p).size() - 1);
		assertEquals("and it is where the blocks end", lastBlock.getY() + lastBlock.getHeight()
			+ p.getInsets().bottom, (int) onEdt(() -> p.getPreferredSize().height));

		// Back to 213: nothing is left over from the wider layout.
		layOutAt(p, 225);
		assertEquals(3, p.card().proofLines().size());
		assertEquals(card213, p.card().getHeight());
		assertEquals(panel213, (int) onEdt(() -> p.getPreferredSize().height));
	}

	// ------------------------------------------------------------------------------------------------ T8

	/**
	 * T8: at 225 wide, both folds open, the quiet and the lag panel are the same pixels as the pinned pictures
	 * ({@code src/test/resources/com/whylag/ui/panel-quiet-225.png} and {@code panel-lag-225.png}), which were made
	 * from the code as it stood and re-pinned for 1.0.1, lot C: no button, no verdict line, no version row, and the
	 * gear at the header's right end with the world words left of it.
	 */
	@Test
	public void at213NothingMoved() throws IOException
	{
		for (Fixture f : both())
		{
			final BufferedImage now = PanelFixtures.paintPanel(PanelFixtures.panel(f, true));
			final BufferedImage before = reference("panel-" + f.name + "-225.png");
			assertEquals(f + " width", before.getWidth(), now.getWidth());
			assertEquals(f + " height", before.getHeight(), now.getHeight());
			for (int y = 0; y < before.getHeight(); y++)
			{
				for (int x = 0; x < before.getWidth(); x++)
				{
					if (before.getRGB(x, y) != now.getRGB(x, y))
					{
						throw new AssertionError(f + " moved at " + x + "," + y + ": was "
							+ Integer.toHexString(before.getRGB(x, y)) + " now " + Integer.toHexString(now.getRGB(x, y)));
					}
				}
			}
		}
	}

	// ------------------------------------------------------------------------------------------------ tools

	private static List<Fixture> both()
	{
		return List.of(PanelFixtures.quiet(), PanelFixtures.lag());
	}

	/** A panel of the fixture with both folds open and the developer-mode footer. */
	private static WhyLagPanel devPanel(Fixture f)
	{
		final Fixture withFooter = new Fixture(f.name, PanelFixtures.withFooter(f.snapshot, FOOTER), f.selected);
		return PanelFixtures.panel(withFooter, true, true, true, new StubActions());
	}

	private static void layOutAt(WhyLagPanel p, int width)
	{
		onEdt(() ->
		{
			PanelFixtures.layOutAt(p, width);
			return null;
		});
	}

	/**
	 * A block painted at the size the layout gave it, with no clip, into an image {@code extra} px wider and 20 px
	 * taller than the block that starts as {@link PanelFixtures#SENTINEL}. It is not resized first.
	 */
	private static BufferedImage paintAsLaidOut(JComponent c, int extra)
	{
		return onEdt(() ->
		{
			final BufferedImage img = new BufferedImage(c.getWidth() + extra, c.getHeight() + 20,
				BufferedImage.TYPE_INT_ARGB);
			final Graphics2D g = img.createGraphics();
			try
			{
				g.setColor(SENTINEL);
				g.fillRect(0, 0, img.getWidth(), img.getHeight());
				PanelFixtures.paintComponent(c, g);
			}
			finally
			{
				g.dispose();
			}
			return img;
		});
	}

	private static int width(int[] run)
	{
		return run[1] - run[0];
	}

	private static List<int[]> runs(BufferedImage img, int y, Color a)
	{
		return runs(img, y, a, a);
	}

	/** The maximal runs of pixels of row {@code y} that are {@code a} or {@code b}, as {start, end (exclusive)}. */
	private static List<int[]> runs(BufferedImage img, int y, Color a, Color b)
	{
		final List<int[]> out = new ArrayList<>();
		int start = -1;
		for (int x = 0; x <= img.getWidth(); x++)
		{
			final boolean in = x < img.getWidth() && (is(img, x, y, a) || is(img, x, y, b));
			if (in && start < 0)
			{
				start = x;
			}
			else if (!in && start >= 0)
			{
				out.add(new int[] {start, x});
				start = -1;
			}
		}
		return out;
	}

	/** True when every gap between neighbouring runs is exactly {@code gap} px of the panel's grey. */
	private static boolean gapsAre(BufferedImage img, int y, List<int[]> runs, int gap)
	{
		for (int i = 0; i + 1 < runs.size(); i++)
		{
			if (runs.get(i + 1)[0] - runs.get(i)[1] != gap)
			{
				return false;
			}
			for (int x = runs.get(i)[1]; x < runs.get(i + 1)[0]; x++)
			{
				if (!is(img, x, y, Ui.GROUND))
				{
					return false;
				}
			}
		}
		return true;
	}

	/** The runs add up to {@code span} less the {@code gaps} px between them, and differ by 1 px at most. */
	private static void shareTheWidth(String what, List<int[]> runs, int span, int gaps)
	{
		int sum = 0;
		int min = Integer.MAX_VALUE;
		int max = 0;
		for (int[] r : runs)
		{
			sum += width(r);
			min = Math.min(min, width(r));
			max = Math.max(max, width(r));
		}
		assertEquals(what + " add up to the width", span - gaps, sum);
		assertTrue(what + " differ by " + (max - min) + " px", max - min <= 1);
	}

	private static BufferedImage reference(String name) throws IOException
	{
		try (InputStream in = PanelFillTest.class.getResourceAsStream(name))
		{
			assertNotNull("the reference picture " + name + " sits beside this test", in);
			return ImageIO.read(in);
		}
	}
}
