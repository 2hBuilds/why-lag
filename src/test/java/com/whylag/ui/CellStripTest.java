package com.whylag.ui;

import com.whylag.core.Cell;
import com.whylag.core.Cells;
import com.whylag.core.LagEvent;
import com.whylag.core.Lane;
import com.whylag.core.Level;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import org.junit.Test;
import static com.whylag.ui.PanelFixtures.Drawn;
import static com.whylag.ui.PanelFixtures.edt;
import static com.whylag.ui.PanelFixtures.is;
import static com.whylag.ui.PanelFixtures.panel;
import static com.whylag.ui.PanelFixtures.record;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * The five cells (contract 5.2): FPS, Tick, Ping, Mem, CPU at x 0, 43, 86, 129, 172; the culprit cell of a
 * selected event with its red edge, its ground, its red value and its square; no culprit without a selection or
 * for a "Not sure" event; the worst tick in a selected event's Tick cell and the usual in the quiet one; the CPU
 * cell's two halves; the value falling from RuneScape Bold to RuneScape to RuneScape Small to fit 35 px; a dash, a
 * ring and the reason in the tooltip; no glyph cut at the bottom; and the cells following the selection.
 */
public class CellStripTest
{
	@Test
	public void fiveCellsInLaneOrder()
	{
		final CellStrip strip = panel(PanelFixtures.quiet(), false).cells();
		final String[] names = {"FPS", "Tick", "Ping", "Mem", "CPU"};
		assertEquals(5, strip.cells().length);
		for (int i = 0; i < 5; i++)
		{
			assertEquals(Lane.values()[i], strip.cells()[i].lane);
			assertEquals(names[i], strip.cells()[i].name);
		}
		final List<Drawn> drawn = record(strip);
		for (int i = 0; i < 5; i++)
		{
			final Drawn name = find(drawn, names[i]);
			assertEquals(names[i], CellStrip.X[i] + 15, name.x);
			assertEquals(14, name.y);
			assertEquals(Ui.RSS, name.font);
		}
	}

	/** The lag fixture's selected World event: the Tick cell is the culprit, and no other cell has a border. */
	@Test
	public void theCulpritCellHasARedEdge()
	{
		final CellStrip strip = panel(PanelFixtures.lag(), false).cells();
		final Cell tick = strip.cells()[1];
		assertTrue(tick.culprit);
		assertEquals(Level.BAD, tick.level);
		final BufferedImage img = PanelFixtures.paint(strip);
		final int x0 = CellStrip.X[1];
		final int x1 = x0 + CellStrip.W[1] - 1;
		for (int x = x0; x <= x1; x++)
		{
			assertTrue("top edge " + x, is(img, x, 0, Ui.BAD));
			assertTrue("bottom edge " + x, is(img, x, 47, Ui.BAD));
		}
		for (int y = 0; y < 48; y++)
		{
			assertTrue("left edge " + y, is(img, x0, y, Ui.BAD));
			assertTrue("right edge " + y, is(img, x1, y, Ui.BAD));
		}
		assertTrue("the culprit's ground", is(img, x0 + 30, 40, Ui.CULPRIT));
		for (int y = 7; y < 14; y++)
		{
			for (int x = x0 + 5; x < x0 + 12; x++)
			{
				assertTrue("its shape is the square " + x + "," + y, is(img, x, y, Ui.BAD));
			}
		}
		final List<Drawn> drawn = record(strip);
		final Drawn value = find(drawn, "1,240");
		assertEquals(Ui.BAD_TEXT.getRGB(), value.colour.getRGB());
		assertEquals(Ui.CULPRIT_LABEL.getRGB(), find(drawn, "Tick").colour.getRGB());
		assertEquals(Ui.CULPRIT_LABEL.getRGB(), find(drawn, "ms", x0).colour.getRGB());
		for (int i = 0; i < 5; i++)
		{
			if (i == 1)
			{
				continue;
			}
			assertTrue("cell " + i + " has no coloured border", is(img, CellStrip.X[i], 0, Ui.CARD));
			assertTrue("cell " + i + " has the card's ground", is(img, CellStrip.X[i] + 30, 40, Ui.CARD));
		}
	}

	@Test
	public void aNotSureEventMarksNoCell()
	{
		final CellStrip strip = panel(PanelFixtures.notSure(), false).cells();
		for (Cell c : strip.cells())
		{
			assertFalse(c.name, c.culprit);
		}
		final BufferedImage img = PanelFixtures.paint(strip);
		for (int i = 0; i < 5; i++)
		{
			assertTrue(is(img, CellStrip.X[i], 0, Ui.CARD));
			assertTrue(is(img, CellStrip.X[i] + 30, 40, Ui.CARD));
		}
		assertEquals("its Tick cell is still the worst tick", "1,240", strip.cells()[1].value);
	}

	/** Without a selection no cell is a culprit - also while the card still holds a past event's verdict. */
	@Test
	public void noCellIsACulpritWithoutASelection()
	{
		for (String name : new String[] {"quiet", "answer-w1", "answer-g1", "answer-n3", "clear-here"})
		{
			final WhyLagPanel p = panel(PanelFixtures.named(name), false);
			assertEquals(-1, p.selectedId());
			for (Cell c : p.cells().cells())
			{
				assertFalse(name + ": " + c.name, c.culprit);
			}
			final BufferedImage img = PanelFixtures.paint(p.cells());
			for (int i = 0; i < 5; i++)
			{
				assertTrue(name, is(img, CellStrip.X[i], 0, Ui.CARD));
			}
		}
		assertEquals("the card does hold the event's verdict", "Memory stall",
			panel(PanelFixtures.named("answer-g1"), false).card().line1());
	}

	@Test
	public void theTickCellShowsTheWorstTickOfASelectedEvent()
	{
		final WhyLagPanel p = panel(PanelFixtures.lag(), true);
		assertEquals("1,240", p.cells().cells()[1].value);
		assertEquals("the ticks lane's value is the mean", "952 ms", p.strips().value(Lane.TICKS.ordinal()));
		assertEquals("1,240", find(record(p.cells()), "1,240").text);
	}

	@Test
	public void theQuietTickCellShowsTheUsual()
	{
		final CellStrip strip = panel(PanelFixtures.quiet(), false).cells();
		assertEquals("600", strip.cells()[1].value);
		final Drawn d = find(record(strip), "600");
		assertEquals(CellStrip.X[1] + 5, d.x);
		assertEquals(30, d.y);
		assertEquals(Ui.WHITE.getRGB(), d.colour.getRGB());
	}

	@Test
	public void theCpuCellShowsTheGameBigAndThePcUnder()
	{
		final List<Drawn> drawn = record(panel(PanelFixtures.quiet(), false).cells());
		final Drawn game = find(drawn, "42%");
		final Drawn pc = find(drawn, "PC 24");
		assertEquals(CellStrip.X[4] + 5, game.x);
		assertEquals(30, game.y);
		assertEquals(Ui.RSB, game.font);
		assertEquals(CellStrip.X[4] + 5, pc.x);
		assertEquals(43, pc.y);
		assertEquals(Ui.RSS, pc.font);
		assertEquals(Ui.LABEL.getRGB(), pc.colour.getRGB());
		assertTrue("PC 100, the widest unit, fits 35 px", Ui.width(Ui.RSS, "PC 100") <= 35);
	}

	/** "1,240" is 37 px in bold and falls to RS; "9,999" falls to RSS; "952" stays bold - each inside x 5 .. 39. */
	@Test
	public void aValueTooWideForBoldFallsToTheNextFace()
	{
		final CellStrip strip = PanelFixtures.onEdt(CellStrip::new);
		final String[] values = {"952", "1,240", "9,999", "100%", "999%"};
		final Cell[] cells = new Cell[5];
		for (int i = 0; i < 5; i++)
		{
			cells[i] = new Cell(Lane.values()[i], "N", values[i], "u", Level.OK, false, "");
		}
		edt(() -> strip.set(cells));
		final List<Drawn> drawn = record(strip);
		assertEquals(Ui.RSB, find(drawn, "952").font);
		assertEquals(Ui.RS, find(drawn, "1,240").font);
		assertEquals(Ui.RSS, find(drawn, "9,999").font);
		assertEquals(Ui.RS, find(drawn, "100%").font);
		assertEquals(37, Ui.width(Ui.RSB, "1,240"));
		assertEquals(33, Ui.width(Ui.RS, "1,240"));
		assertEquals(36, Ui.width(Ui.RS, "9,999"));
		for (int i = 0; i < 5; i++)
		{
			final Drawn d = find(drawn, values[i]);
			assertEquals(values[i], CellStrip.X[i] + 5, d.x);
			assertTrue(values[i] + " ends by x 39 of its cell: " + d, d.right() <= CellStrip.X[i] + 40);
		}
	}

	/** A tile's "-": a dash, no unit, the hollow ring, and the reason only in the tooltip. */
	@Test
	public void noDataIsADashAndARingAndTheReasonIsTheTooltip()
	{
		final CellStrip strip = panel(PanelFixtures.named("no-data-ping-stale"), false).cells();
		final Cell ping = strip.cells()[2];
		assertEquals("-", ping.value);
		assertEquals("", ping.unit);
		assertEquals(Level.NO_DATA, ping.level);
		final int x0 = CellStrip.X[2];
		assertEquals("Ping: Nothing sent", PanelFixtures.tip(strip, x0 + 20, 20));
		final List<Drawn> drawn = record(strip);
		assertEquals("-", find(drawn, "-", x0).text);
		for (Drawn d : drawn)
		{
			assertFalse("the reason is not painted: " + d, d.text.contains("Nothing"));
		}
		final BufferedImage img = PanelFixtures.paint(strip);
		assertTrue("the ring", is(img, x0 + 5, 7 + 3, Ui.LABEL));
		assertTrue("hollow", is(img, x0 + 5 + 3, 7 + 3, Ui.CARD));
		assertEquals("Frame rate: 50 fps, worst 35 ms", PanelFixtures.tip(strip, 20, 20));
		assertNull("no tooltip in the gap between cells", PanelFixtures.tip(strip, 41, 20));
	}

	/** Painted with no clip into a taller image, the unit's glyphs stop above the cell's bottom row, y 47. */
	@Test
	public void noGlyphIsCut()
	{
		final CellStrip strip = panel(PanelFixtures.quiet(), false).cells();
		assertEquals("fps", strip.cells()[0].unit);
		final BufferedImage img = PanelFixtures.paintUnclipped(strip, 213);
		int ink = 0;
		for (int x = 5; x < 40; x++)
		{
			for (int y = 32; y < 47; y++)
			{
				ink += is(img, x, y, Ui.LABEL) ? 1 : 0;
			}
			assertTrue("row 47 is the cell's ground", is(img, x, 47, Ui.CARD));
			for (int y = 48; y < img.getHeight(); y++)
			{
				assertTrue("nothing under the cell at " + x + "," + y, is(img, x, y, PanelFixtures.SENTINEL));
			}
		}
		assertTrue("'fps' is painted above row 47: " + ink, ink > 10);
	}

	@Test
	public void selectedEventShowsItsOwnNumbers()
	{
		final WhyLagPanel p = panel(PanelFixtures.lag(), false);
		final LagEvent d = PanelFixtures.pictureEvent();
		assertSameCells(Cells.of(d), p.cells().cells());
		final WhyLagPanel big = panel(PanelFixtures.worstTick(), false);
		assertEquals("a worst tick of 12,400 ms prints 9,999", "9,999", big.cells().cells()[1].value);
		assertEquals(Ui.RSS, find(record(big.cells()), "9,999").font);
	}

	@Test
	public void clearingTheSelectionReturnsToNow()
	{
		final WhyLagPanel p = panel(PanelFixtures.lag(), false);
		edt(() -> p.select(-1));
		assertSameCells(Cells.now(PanelFixtures.lag().snapshot), p.cells().cells());
		for (Cell c : p.cells().cells())
		{
			assertFalse(c.culprit);
		}
		edt(() -> p.select(3));
		assertTrue("selected again, the culprit is back", p.cells().cells()[1].culprit);
	}

	// ------------------------------------------------------------------ helpers

	private static void assertSameCells(Cell[] expected, Cell[] actual)
	{
		assertEquals(expected.length, actual.length);
		for (int i = 0; i < expected.length; i++)
		{
			assertEquals(expected[i].name, expected[i].value, actual[i].value);
			assertEquals(expected[i].name, expected[i].unit, actual[i].unit);
			assertEquals(expected[i].name, expected[i].level, actual[i].level);
			assertEquals(expected[i].name, expected[i].culprit, actual[i].culprit);
			assertEquals(expected[i].name, expected[i].tip, actual[i].tip);
		}
	}

	private static Drawn find(List<Drawn> drawn, String text)
	{
		return find(drawn, text, -1);
	}

	/** The string drawn with that text, in the cell that starts at {@code cellX} (-1: any cell). */
	private static Drawn find(List<Drawn> drawn, String text, int cellX)
	{
		final List<Drawn> found = new ArrayList<>();
		for (Drawn d : drawn)
		{
			if (d.text.equals(text) && (cellX < 0 || (d.x >= cellX && d.x < cellX + 41)))
			{
				found.add(d);
			}
		}
		if (found.isEmpty())
		{
			fail("not drawn: " + text + " in " + drawn);
		}
		return found.get(0);
	}
}
