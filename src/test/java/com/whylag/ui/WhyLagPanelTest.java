package com.whylag.ui;

import com.whylag.GraphRange;
import com.whylag.core.LagEvent;
import java.awt.image.BufferedImage;
import java.util.Arrays;
import java.util.List;
import javax.swing.JComponent;
import org.junit.Test;
import static com.whylag.ui.PanelFixtures.contentHeight;
import static com.whylag.ui.PanelFixtures.edt;
import static com.whylag.ui.PanelFixtures.lag;
import static com.whylag.ui.PanelFixtures.panel;
import static com.whylag.ui.PanelFixtures.quiet;
import static com.whylag.ui.PanelFixtures.yOf;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

/**
 * The panel of picture 18 as a whole (contract 5): the three heights of section 5 - quiet and folded 361 px, a lag
 * found and folded 443, the same opened with one event in the list 639 - and the y of every block in each; the
 * blocks in the table's order, each 213 wide; the footer in developer mode; the card before the first snapshot; the
 * selection and the range as {@code PanelControl} drives them; and the navigation icon.
 */
public class WhyLagPanelTest
{
	@Test
	public void quietFoldedIsThreeHundredSixtyOneHigh()
	{
		final WhyLagPanel p = panel(quiet(), false);
		assertEquals("the quiet card, with its 'Last lag' line", 95, p.card().height());
		assertEquals(361, contentHeight(p));
		assertEquals(361 + 12, p.getPreferredSize().height);
	}

	@Test
	public void lagFoldedIsFourHundredFortyThreeHigh()
	{
		final WhyLagPanel p = panel(lag(), false);
		assertEquals("two big lines, a two-line proof, a one-line fix, a when line", 177, p.card().height());
		assertEquals(2, p.card().proofLines().size());
		assertEquals(1, p.card().fixLines().size());
		assertEquals(443, contentHeight(p));
	}

	@Test
	public void lagOpenedIsSixHundredThirtyNineHigh()
	{
		final WhyLagPanel p = panel(lag(), true);
		assertEquals("one event in the list", 1, p.eventList().rows().size());
		assertEquals(166, p.strips().getHeight());
		assertEquals(24, p.eventList().getHeight());
		assertEquals(639, contentHeight(p));
	}

	/** The y of each block of section 5's table, in the three states. */
	@Test
	public void everyBlockSitsAtItsY()
	{
		final WhyLagPanel quiet = panel(quiet(), false);
		assertYs(quiet, new int[] {0, 28, 131, 191, 218, 248, 286, 306, 332});

		final WhyLagPanel folded = panel(lag(), false);
		assertYs(folded, new int[] {0, 28, 213, 273, 300, 330, 368, 388, 414});

		final WhyLagPanel open = panel(lag(), true);
		assertYs(open, new int[] {0, 28, 213, 273, 300, 329, 499, 528, 564, 584, 610});
	}

	private static void assertYs(WhyLagPanel p, int[] ys)
	{
		final List<JComponent> blocks = PanelFixtures.blocks(p);
		assertEquals("blocks in the layout", ys.length, blocks.size());
		for (int i = 0; i < ys.length; i++)
		{
			assertEquals(blocks.get(i).getClass().getSimpleName(), ys[i], yOf(p, blocks.get(i)));
		}
	}

	/** Top down, the table's order; a folded block is absent and an open one sits under its row. */
	@Test
	public void theBlocksAreInTheTablesOrder()
	{
		final WhyLagPanel folded = panel(quiet(), false);
		assertEquals(Arrays.asList(folded.header(), folded.card(), folded.cells(), folded.rangeRow(),
			folded.graphsRow(), folded.lagsRow(), folded.sessionHeader(), folded.counts(), folded.buttons()),
			PanelFixtures.blocks(folded));

		final WhyLagPanel open = panel(quiet(), true);
		assertEquals(Arrays.asList(open.header(), open.card(), open.cells(), open.rangeRow(), open.graphsRow(),
			open.strips(), open.lagsRow(), open.eventList(), open.sessionHeader(), open.counts(), open.buttons()),
			PanelFixtures.blocks(open));
	}

	@Test
	public void everyBlockIsTwoHundredThirteenWideAtTheBorder()
	{
		for (boolean openRows : new boolean[] {false, true})
		{
			final WhyLagPanel p = panel(lag(), openRows);
			assertEquals(225, p.getPreferredSize().width);
			for (JComponent block : PanelFixtures.blocks(p))
			{
				assertEquals(block.getClass().getSimpleName(), 6, block.getX());
				assertEquals(block.getClass().getSimpleName(), 213, block.getWidth());
				assertEquals(block.getClass().getSimpleName(), 213, block.getPreferredSize().width);
			}
		}
	}

	/** Developer mode adds the footer, 14 high, 6 px under the button; outside it there is none. */
	@Test
	public void theFooterSitsUnderTheButtonsInDeveloperModeOnly()
	{
		final PanelFixtures.Fixture f = new PanelFixtures.Fixture("dev", PanelFixtures.withFooter(quiet().snapshot,
			"self: frame 180 ns, tick 2 us, step 40 us"), -1);
		final WhyLagPanel dev = panel(f, false, false, true, new PanelFixtures.StubActions());
		assertEquals(361 + 6 + 14, contentHeight(dev));
		assertEquals(361 + 6, yOf(dev, dev.footer()));
		assertEquals("self: frame 180 ns, tick 2 us, step 40 us", ((WhyLagPanel.Footer) dev.footer()).text());
		assertNull(panel(quiet(), false).footer());
	}

	/** A footer wider than the panel is cut with "..." and never runs past x 213. */
	@Test
	public void aLongFooterIsCut()
	{
		final WhyLagPanel.Footer footer = new WhyLagPanel.Footer();
		footer.set("self: frame 999 ns, tick 999 us, step 999 us, and more words than fit");
		assertTrue(footer.text().endsWith("..."));
		assertTrue(Ui.width(Ui.RSS, footer.text()) <= 213);
	}

	/** Before the first snapshot: "Measuring" with no proof, 46 high; the copy button disabled. */
	@Test
	public void aNewPanelShowsTheCardBeforeTheFirstSnapshot()
	{
		final WhyLagPanel p = PanelFixtures.onEdt(() -> new WhyLagPanel(new PanelFixtures.StubActions(),
			GraphRange.TEN_MIN, false));
		assertEquals("Measuring", p.card().line1());
		assertEquals(46, p.card().height());
		assertFalse(p.buttons().copyEnabled());
		assertEquals(10, p.range());
		assertFalse(p.isActive());
		assertEquals(0, p.activations());
		assertEquals(0, p.deactivations());
		assertSame(p, p.component());
		assertEquals(20 + 8 + 46 + 8 + 48 + 12 + 23 + 4 + 26 + 4 + 26 + 12 + 16 + 4 + 16 + 10 + 29,
			PanelFixtures.onEdt(() -> contentHeight(p)).intValue());
	}

	/** The initial range is the one handed in; a null one is 10 min. */
	@Test
	public void theInitialRangeIsTheConfigs()
	{
		assertEquals(60, PanelFixtures.onEdt(() -> new WhyLagPanel(new PanelFixtures.StubActions(),
			GraphRange.SIXTY_MIN, false)).range());
		assertEquals(10, PanelFixtures.onEdt(() -> new WhyLagPanel(new PanelFixtures.StubActions(), null, false))
			.range());
	}

	/**
	 * A chip press is a range change: the chips show it at once, the actions are told, and the selection is
	 * cleared; pressing the chip already shown does nothing. {@code setRange} presses the same chips.
	 */
	@Test
	public void aChipPressChangesTheRangeAndClearsTheSelection()
	{
		final PanelFixtures.StubActions actions = new PanelFixtures.StubActions();
		final WhyLagPanel p = panel(lag(), false, false, false, actions);
		assertEquals(3, p.selectedId());
		PanelFixtures.press(p.rangeRow(), RangeRow.CHIP_X[2] + 10, 10);
		assertEquals(60, p.range());
		assertEquals(60, p.rangeRow().minutes());
		assertEquals(Arrays.asList(60), actions.ranges);
		assertEquals("a range change clears the selection", -1, p.selectedId());
		assertEquals("the card is back to now", "Smooth", p.card().line1());

		PanelFixtures.press(p.rangeRow(), RangeRow.CHIP_X[2] + 30, 5);
		assertEquals("the chosen chip again: no change", 1, actions.ranges.size());
		PanelFixtures.press(p.rangeRow(), 10, 10);
		assertEquals("'Last' is not a chip", 1, actions.ranges.size());

		edt(() -> p.setRange(1));
		assertEquals(1, p.range());
		assertEquals(Arrays.asList(60, 1), actions.ranges);
	}

	/** {@code select} picks an event of the range; an id outside the range, or -1, clears the selection. */
	@Test
	public void selectPicksAnEventOfTheRange()
	{
		final WhyLagPanel p = panel(PanelFixtures.clearHere(), false);
		final LagEvent d = PanelFixtures.pictureEvent();
		edt(() -> p.select(d.id));
		assertEquals(d.id, p.selectedId());
		assertEquals("World lag", p.card().line1());
		edt(() -> p.select(99));
		assertEquals("no event 99 in the range", -1, p.selectedId());
		assertEquals("Smooth", p.card().line1());
		edt(() -> p.select(d.id));
		edt(() -> p.select(-1));
		assertEquals(-1, p.selectedId());
	}

	/** A selected event that leaves the range takes the selection with it. */
	@Test
	public void aSelectionOutsideTheNewSnapshotIsCleared()
	{
		final WhyLagPanel p = panel(lag(), false);
		assertEquals(3, p.selectedId());
		edt(() -> p.show(quiet().snapshot));
		assertEquals(-1, p.selectedId());
		assertEquals("Smooth", p.card().line1());
	}

	/**
	 * The sidebar icon (the user's pick of 2026-09-29, picture 29 R1 with the bars 25 % smaller): 16 x 16, the 9 px
	 * "2h" coin in the top-left corner and four bars in the bottom-right, on a clear ground, the two never touching.
	 * It also writes the icon enlarged 8x to build/whylag/nav-icon-8x.png for the lead to look at.
	 */
	@Test
	public void theNavigationIconIsTheCoinAndTheBars() throws Exception
	{
		final BufferedImage icon = NavIcon.create();
		assertEquals(16, icon.getWidth());
		assertEquals(16, icon.getHeight());
		int gold = 0, ink = 0, green = 0, amber = 0, red = 0;
		for (int y = 0; y < 16; y++)
		{
			for (int x = 0; x < 16; x++)
			{
				final int argb = icon.getRGB(x, y);
				if ((argb >>> 24) <= 200)
				{
					continue;
				}
				final java.awt.Color c = new java.awt.Color(argb);
				// the bars' colours first: amber (230,150,30) is redder than any gold of the coin (196 at most)
				if (c.getRed() > 200 && c.getGreen() > 120 && c.getBlue() < 60)
				{
					amber++;
					assertTrue("the amber bar: " + x + "," + y, x >= 9 && x <= 11 && y >= 9);
				}
				else if (c.getRed() > 200 && c.getGreen() < 60)
				{
					red++;
					assertTrue("the red bar: " + x + "," + y, x >= 13 && y >= 7);
				}
				else if (c.getGreen() > 200 && c.getRed() < 100)
				{
					green++;
					assertTrue("green bars along the bottom: " + x + "," + y, x >= 1 && x <= 7 && y >= 11);
				}
				else if (c.getRed() > 80 && c.getGreen() > 55 && c.getBlue() < 90 && c.getRed() > c.getGreen())
				{
					gold++;
					assertTrue("gold only in the coin's corner: " + x + "," + y, x <= 9 && y <= 9);
				}
				else if (c.getRed() < 70 && c.getGreen() < 50 && c.getBlue() < 30)
				{
					ink++;
					assertTrue("letters only on the coin: " + x + "," + y, x >= 1 && x <= 7 && y >= 2 && y <= 6);
				}
			}
		}
		assertTrue("a coin's worth of gold: " + gold, gold >= 25);
		assertEquals("every pixel of the letters: 11 in the 2, 9 in the h", 20, ink);
		assertEquals("two green bars, 3 x 2 and 3 x 5", 21, green);
		assertEquals("the amber bar, 3 x 7", 21, amber);
		assertEquals("the red bar, 3 x 9", 27, red);
		assertEquals("the top-right corner is clear", 0, icon.getRGB(15, 0) >>> 24);
		assertEquals("the bottom-left corner is clear", 0, icon.getRGB(0, 15) >>> 24);
		assertTrue("a new image on every call", icon != NavIcon.create());

		final BufferedImage big = new BufferedImage(128, 128, BufferedImage.TYPE_INT_ARGB);
		final java.awt.Graphics2D g = big.createGraphics();
		g.setRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION,
			java.awt.RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
		g.setColor(new java.awt.Color(30, 30, 30));
		g.fillRect(0, 0, 128, 128);
		g.drawImage(icon, 0, 0, 128, 128, null);
		g.dispose();
		final java.io.File out = new java.io.File("build/whylag/nav-icon-8x.png");
		out.getParentFile().mkdirs();
		javax.imageio.ImageIO.write(big, "png", out);
	}
}
