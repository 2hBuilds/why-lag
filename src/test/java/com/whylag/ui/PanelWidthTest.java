package com.whylag.ui;

import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import javax.swing.JComponent;
import org.junit.Test;
import static com.whylag.ui.PanelFixtures.Drawn;
import static com.whylag.ui.PanelFixtures.Fixture;
import static com.whylag.ui.PanelFixtures.SENTINEL;
import static com.whylag.ui.PanelFixtures.is;
import static com.whylag.ui.PanelFixtures.record;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * 213 px (contract 5, 7 L6): one test per block and one for the whole panel, in EVERY fixture and BOTH fold states
 * (both rows folded, both open), in developer mode so the footer is there too. For each block: its preferred width
 * is exactly 213; every string it draws measures no wider than its box with the real font; and nothing is painted
 * at x 213 or more - painted with no clip into a 260 px image, the columns 213 .. 259 stay untouched. Then the four
 * named corners: the widest counts row, the three cells ending at x 213, the range row ending at x 213, and the
 * widest fold row.
 */
public class PanelWidthTest
{
	private static final String FOOTER = "self: frame 180 ns, tick 2 us, step 40 us";

	/** The strings of one block, checked against their own boxes. */
	private interface Boxes
	{
		void check(String where, List<Drawn> drawn);
	}

	@Test
	public void headerRow()
	{
		eachBlock(WhyLagPanel::header, (where, drawn) ->
		{
			final Drawn title = drawn.get(0);
			assertEquals(where, "Why Lag", title.text);
			assertEquals(where, 0, title.x);
			if (drawn.size() > 1)
			{
				final Drawn world = drawn.get(1);
				assertEquals(where + ": the world words end 6 px left of the gear, at x 195", 213 - 12 - 6,
					world.right());
				assertTrue(where + ": clear of the title", world.x > title.right());
			}
		});
	}

	@Test
	public void answerCard()
	{
		eachBlock(WhyLagPanel::card, (where, drawn) ->
		{
			for (Drawn d : drawn)
			{
				if (d.font.equals(Ui.RSB32))
				{
					inBox(where, d, 30, 208);
				}
				else
				{
					inBox(where, d, 10, 206);
				}
			}
		});
	}

	@Test
	public void cellStrip()
	{
		eachBlock(WhyLagPanel::cells, (where, drawn) ->
		{
			assertTrue(where, drawn.size() >= 6);
			for (Drawn d : drawn)
			{
				final int cell = CellStrip.cellAt(d.x);
				assertTrue(where + ": " + d + " starts in a cell", cell >= 0);
				final int x = CellStrip.X[cell];
				final int end = x + CellStrip.W[cell];
				if (d.y == CellStrip.NAME_BASELINE)
				{
					inBox(where, d, x + 15, end);
				}
				else
				{
					inBox(where + ": the text box x 5 .. 39", d, x + 5, x + 40);
				}
			}
		});
	}

	@Test
	public void rangeRow()
	{
		eachBlock(WhyLagPanel::rangeRow, (where, drawn) ->
		{
			assertEquals(where, 4, drawn.size());
			inBox(where, drawn.get(0), 0, 33);
			for (int i = 1; i < 4; i++)
			{
				final int x = RangeRow.CHIP_X[i - 1];
				inBox(where + ": inside its chip's border", drawn.get(i), x + 1, x + 57);
			}
		});
	}

	@Test
	public void foldRows()
	{
		final Boxes words = (where, drawn) ->
		{
			for (Drawn d : drawn)
			{
				inBox(where + ": clear of the chevron", d, 8, 194);
			}
		};
		eachBlock(WhyLagPanel::graphsRow, words);
		eachBlock(WhyLagPanel::lagsRow, words);
	}

	@Test
	public void stripChart()
	{
		eachBlock(WhyLagPanel::strips, (where, drawn) ->
		{
			for (Drawn d : drawn)
			{
				inBox(where, d, 0, 213);
				if (d.y < StripChart.AXIS_Y && d.x > 100)
				{
					assertTrue(where + ": a value ends by x 210 (its shadow by 211): " + d, d.right() <= 211);
				}
			}
			// Each lane's values stay clear of its label.
			for (int lane = 0; lane < 3; lane++)
			{
				final int y = 30 * lane + 11;
				Drawn label = null;
				for (Drawn d : drawn)
				{
					if (d.y == y && d.x == 3)
					{
						label = d;
					}
				}
				assertTrue(where + ": lane " + lane + " has its label", label != null);
				for (Drawn d : drawn)
				{
					if ((d.y == y || d.y == y + 1) && d != label && d.x != 3)
					{
						assertTrue(where + ": " + d + " clear of " + label, d.x > label.right());
					}
				}
			}
		});
	}

	@Test
	public void eventList()
	{
		eachBlock(WhyLagPanel::eventList, (where, drawn) ->
		{
			for (Drawn d : drawn)
			{
				inBox(where, d, 0, 213);
				if (d.x == EventList.TIME_X)
				{
					inBox(where + ": the clock before the group", d, 23, 63);
				}
				else if (d.x == EventList.LABEL_X)
				{
					inBox(where + ": the group before the length", d, 63, 175);
				}
				else if (d.right() == EventList.LENGTH_RIGHT)
				{
					assertTrue(where + ": " + d, d.x >= 175);
				}
			}
		});
	}

	@Test
	public void sessionHeader()
	{
		eachBlock(WhyLagPanel::sessionHeader, (where, drawn) ->
		{
			assertEquals(where, 2, drawn.size());
			inBox(where, drawn.get(0), 0, 213);
			assertEquals(where, 213, drawn.get(1).right());
			assertTrue(where, drawn.get(1).x > drawn.get(0).right());
		});
	}

	@Test
	public void sessionCounts()
	{
		eachBlock(WhyLagPanel::counts, (where, drawn) ->
		{
			for (Drawn d : drawn)
			{
				inBox(where, d, 0, 213);
			}
		});
	}

	@Test
	public void footer()
	{
		eachBlock(WhyLagPanel::footer, (where, drawn) ->
		{
			assertEquals(where, 1, drawn.size());
			assertEquals(where, FOOTER, drawn.get(0).text);
			inBox(where, drawn.get(0), 0, 213);
		});
	}

	/** The whole panel: 225 wide with its border; every block at x 6, 213 wide; nothing in the border's columns. */
	@Test
	public void wholePanel()
	{
		for (Fixture f : PanelFixtures.all())
		{
			for (boolean open : new boolean[] {false, true})
			{
				final WhyLagPanel p = devPanel(f, open);
				assertEquals(f.name, 225, p.getPreferredSize().width);
				for (JComponent block : PanelFixtures.blocks(p))
				{
					assertEquals(f + " " + PanelFixtures.label(block), 6, block.getX());
					assertEquals(f + " " + PanelFixtures.label(block), 213, block.getWidth());
				}
				final BufferedImage img = PanelFixtures.paintPanel(p);
				for (int y = 0; y < img.getHeight(); y++)
				{
					for (int x = 219; x < 225; x++)
					{
						assertTrue(f + ": the right border stays the ground at " + x + "," + y,
							is(img, x, y, Ui.GROUND));
					}
				}
			}
		}
	}

	/** "Conn 99+" .. "? 99+": four items that need two lines, each whole and inside 213 px. */
	@Test
	public void fullSessionCountsRowFits()
	{
		final WhyLagPanel p = devPanel(PanelFixtures.countsOver99(), false);
		final List<Drawn> drawn = record(p.counts());
		assertEquals(4, drawn.size());
		final List<String> words = new ArrayList<>();
		for (Drawn d : drawn)
		{
			words.add(d.text);
			inBox("99+", d, 0, 213);
		}
		assertEquals(p.counts().items(), words);
		assertEquals("? 99+", words.get(3));
		assertEquals(32, p.counts().getHeight());
		assertTrue("measured: all four on one line would be over 213 px", Ui.width(Ui.RSS, String.join("", words))
			+ 3 * 11 > 213);
	}

	@Test
	public void theThreeCellsEndAtTwoHundredThirteen()
	{
		assertEquals(3, CellStrip.X.length);
		final int[] xs = {0, 72, 144};
		final int[] ws = {69, 69, 69};
		for (int i = 0; i < 3; i++)
		{
			assertEquals(xs[i], CellStrip.X[i]);
			assertEquals(ws[i], CellStrip.W[i]);
		}
		assertEquals(213, CellStrip.X[2] + CellStrip.W[2]);
		final BufferedImage img = PanelFixtures.paint(PanelFixtures.panel(PanelFixtures.quiet(), false).cells());
		for (int i = 0; i < 3; i++)
		{
			assertTrue("cell " + i + " starts at " + xs[i], is(img, xs[i], 45, Ui.CARD));
			assertTrue("cell " + i + " ends at " + (xs[i] + ws[i] - 1), is(img, xs[i] + ws[i] - 1, 45, Ui.CARD));
			if (i < 2)
			{
				for (int gap = xs[i] + ws[i]; gap < xs[i + 1]; gap++)
				{
					assertTrue("a 3 px gap at " + gap, is(img, gap, 45, Ui.GROUND));
				}
			}
		}
	}

	/**
	 * While the graphs are stretched the note "30 s" / "so far" takes the place of "Last": two lines inside the 33 px
	 * before the first chip, inside the row's 23 px, and the chips stay where they are (the first live look).
	 */
	@Test
	public void theStretchNoteFitsBeforeTheFirstChip()
	{
		final RangeRow row = PanelFixtures.panel(PanelFixtures.quiet(), false).rangeRow();
		assertEquals("a quiet fixture is not stretched", "", row.held());
		for (String held : new String[] {"0 s", "30 s", "59 s", "4 min", "59 min"})
		{
			row.held(held);
			final List<Drawn> drawn = PanelFixtures.record(row);
			assertEquals(held, 5, drawn.size());
			assertEquals(held, drawn.get(0).text);
			assertEquals(RangeRow.SO_FAR, drawn.get(1).text);
			for (int i = 0; i < 2; i++)
			{
				inBox(held, drawn.get(i), 0, RangeRow.CHIP_X[0] - 1);
				assertEquals(Ui.LABEL.getRGB(), drawn.get(i).colour.getRGB());
			}
			assertEquals(RangeRow.NOTE_BASELINE_1, drawn.get(0).y);
			assertEquals(RangeRow.NOTE_BASELINE_2, drawn.get(1).y);
			assertTrue("inside the row", RangeRow.NOTE_BASELINE_2 < RangeRow.HEIGHT);
			for (int i = 2; i < 5; i++)
			{
				final int x = RangeRow.CHIP_X[i - 2];
				inBox(held + ": inside its chip's border", drawn.get(i), x + 1, x + 57);
			}
		}
		row.held("");
		assertEquals(RangeRow.LAST, PanelFixtures.record(row).get(0).text);
	}

	@Test
	public void theRangeRowEndsAtTwoHundredThirteen()
	{
		assertEquals(213, RangeRow.CHIP_X[2] + RangeRow.CHIP_W);
		assertEquals(33, RangeRow.CHIP_X[0]);
		assertEquals(94, RangeRow.CHIP_X[1]);
		assertEquals(155, RangeRow.CHIP_X[2]);
		final RangeRow row = PanelFixtures.panel(PanelFixtures.quiet(), false).rangeRow();
		final BufferedImage img = PanelFixtures.paint(row);
		assertTrue("the chosen 1 min chip has the orange border", is(img, 33, 0, Ui.ORANGE));
		assertTrue(is(img, 94, 0, Ui.RULE));
		assertTrue("the last chip's border is the row's last column", is(img, 212, 10, Ui.RULE));
		for (int gap : new int[] {91, 92, 93, 152, 153, 154})
		{
			assertTrue("3 px between chips at " + gap, is(img, gap, 10, Ui.GROUND));
		}
		final BufferedImage unclipped = PanelFixtures.paintUnclipped(row, 260);
		for (int y = 0; y < 23; y++)
		{
			assertTrue(is(unclipped, 213, y, SENTINEL));
		}
	}

	/** "Lags (500)", 61 px from x 8, ends well before the chevron at x 194. */
	@Test
	public void theWidestFoldRowFits()
	{
		final WhyLagPanel p = devPanel(PanelFixtures.fiveHundred(), false);
		assertEquals("Lags (500)", p.lagsRow().words());
		assertEquals(61, Ui.width(Ui.RS, "Lags (500)"));
		final List<Drawn> drawn = record(p.lagsRow());
		assertEquals(8 + 61, drawn.get(drawn.size() - 1).right());
		assertTrue(drawn.get(drawn.size() - 1).right() < FoldRow.CHEVRON_X);
	}

	// ------------------------------------------------------------------ helpers

	private static WhyLagPanel devPanel(Fixture f, boolean open)
	{
		final Fixture withFooter = new Fixture(f.name, PanelFixtures.withFooter(f.snapshot, FOOTER), f.selected);
		return PanelFixtures.panel(withFooter, open, open, true, new PanelFixtures.StubActions());
	}

	/**
	 * One block in every fixture and both fold states: 213 wide, its strings in their boxes, nothing painted past
	 * x 212. A foldable block is checked where it is in the layout, and must be absent where its row is folded.
	 */
	private static void eachBlock(Function<WhyLagPanel, JComponent> blockOf, Boxes boxes)
	{
		int checked = 0;
		for (Fixture f : PanelFixtures.all())
		{
			for (boolean open : new boolean[] {false, true})
			{
				final WhyLagPanel p = devPanel(f, open);
				final JComponent block = blockOf.apply(p);
				final String where = f + (open ? " open" : " folded");
				if (block.getParent() != p)
				{
					assertTrue(where + ": only a folded chart or list is out of the layout",
						!open && (block instanceof StripChart || block instanceof EventList));
					continue;
				}
				assertEquals(where, 213, block.getPreferredSize().width);
				assertEquals(where, 213, block.getWidth());
				final List<Drawn> drawn = record(block);
				for (Drawn d : drawn)
				{
					assertTrue(where + ": " + d + " starts at x 0 or more", d.x >= 0);
					assertTrue(where + ": " + d + " ends by x 213", d.right() <= 213);
				}
				boxes.check(where, drawn);
				final BufferedImage img = PanelFixtures.paintUnclipped(block, 260);
				for (int y = 0; y < img.getHeight(); y++)
				{
					for (int x = 213; x < 260; x++)
					{
						if (!is(img, x, y, SENTINEL))
						{
							throw new AssertionError(where + ": painted at " + x + "," + y);
						}
					}
				}
				checked++;
			}
		}
		assertTrue("the block was checked in many fixtures: " + checked, checked >= 20);
	}

	private static void inBox(String where, Drawn d, int from, int to)
	{
		assertTrue(where + ": " + d + " starts at " + from + " or later", d.x >= from);
		assertTrue(where + ": " + d + " (" + d.width + " px) ends by " + to, d.right() <= to);
	}
}
