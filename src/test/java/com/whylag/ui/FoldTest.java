package com.whylag.ui;

import com.whylag.GraphRange;
import java.awt.Component;
import java.awt.image.BufferedImage;
import java.util.Arrays;
import java.util.List;
import org.junit.Test;
import static com.whylag.ui.PanelFixtures.Drawn;
import static com.whylag.ui.PanelFixtures.edt;
import static com.whylag.ui.PanelFixtures.is;
import static com.whylag.ui.PanelFixtures.panel;
import static com.whylag.ui.PanelFixtures.press;
import static com.whylag.ui.PanelFixtures.record;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * The fold rows (contract 5, blocks 5 and 6; T17): both folded in a new panel; one press opens and one closes; the
 * two are independent; a folded block is not in the layout and never paints; the chevron turns; an open row is
 * lighter; "Lags (n)" counts the range's events; a fold keeps the selection; the state outlives a hide; a press
 * lays the panel out once; {@code PanelControl.fold} sets both.
 */
public class FoldTest
{
	@Test
	public void bothRowsAreFoldedInANewPanel()
	{
		final WhyLagPanel p = PanelFixtures.onEdt(() -> new WhyLagPanel(new PanelFixtures.StubActions(),
			GraphRange.TEN_MIN, false));
		assertFalse(p.graphsOpen());
		assertFalse(p.lagsOpen());
		assertFalse(p.graphsRow().isOpen());
		assertFalse(p.lagsRow().isOpen());
		assertFalse(children(p).contains(p.strips()));
		assertFalse(children(p).contains(p.eventList()));
		final WhyLagPanel shown = panel(PanelFixtures.lag(), false, false, false, new PanelFixtures.StubActions());
		assertFalse("showing a snapshot opens nothing", shown.graphsOpen() || shown.lagsOpen());
	}

	@Test
	public void oneClickOpensAndOneClickCloses()
	{
		final WhyLagPanel p = panel(PanelFixtures.quiet(), false);
		press(p.graphsRow(), 100, 10);
		assertTrue(p.graphsOpen());
		assertTrue(children(p).contains(p.strips()));
		press(p.graphsRow(), 5, 20);
		assertFalse(p.graphsOpen());
		assertFalse(children(p).contains(p.strips()));
		press(p.lagsRow(), 200, 13);
		assertTrue("anywhere on the row, the chevron included", p.lagsOpen());
		press(p.lagsRow(), 60, 2);
		assertFalse(p.lagsOpen());
	}

	@Test
	public void theRowsAreIndependent()
	{
		final WhyLagPanel p = panel(PanelFixtures.quiet(), false);
		press(p.graphsRow(), 100, 10);
		assertTrue(p.graphsOpen());
		assertFalse(p.lagsOpen());
		press(p.lagsRow(), 100, 10);
		assertTrue(p.graphsOpen());
		assertTrue(p.lagsOpen());
		press(p.graphsRow(), 100, 10);
		assertFalse(p.graphsOpen());
		assertTrue(p.lagsOpen());
		assertFalse(children(p).contains(p.strips()));
		assertTrue(children(p).contains(p.eventList()));
	}

	/** T17: no StripChart and no EventList among the panel's components, and neither one's paintComponent runs. */
	@Test
	public void aFoldedBlockIsNotInTheLayout()
	{
		final WhyLagPanel folded = panel(PanelFixtures.lag(), false);
		for (Component c : folded.getComponents())
		{
			assertFalse(c.toString(), c instanceof StripChart);
			assertFalse(c.toString(), c instanceof EventList);
		}
		PanelFixtures.paintPanel(folded);
		assertEquals("the folded chart never paints", 0, folded.strips().paints());
		assertEquals("the folded list never paints", 0, folded.eventList().paints());
		assertEquals("and is not measured: the folded height, the picture's 443 less the button's 10 + 29", 443 - 10 - 29,
			PanelFixtures.contentHeight(folded));

		final WhyLagPanel open = panel(PanelFixtures.lag(), true);
		PanelFixtures.paintPanel(open);
		assertTrue("an open chart paints", open.strips().paints() > 0);
		assertTrue("an open list paints", open.eventList().paints() > 0);
		assertTrue(open.strips().isVisible() && open.strips().getParent() == open);
		assertTrue(open.eventList().isVisible() && open.eventList().getParent() == open);

		edt(() -> open.fold(false, false));
		final int before = open.strips().paints();
		PanelFixtures.paintPanel(open);
		assertEquals("folded again, it stops painting", before, open.strips().paints());
		assertFalse(children(open).contains(open.strips()));
	}

	/** Down while folded, up while open, by pixel: the arms meet at x 199, at the bottom or at the top. */
	@Test
	public void theChevronTurns()
	{
		final WhyLagPanel p = panel(PanelFixtures.quiet(), false);
		final BufferedImage down = PanelFixtures.paint(p.graphsRow());
		assertTrue("the point is at the bottom", is(down, 199, 15, Ui.LABEL));
		assertFalse(is(down, 199, 10, Ui.LABEL));
		assertTrue("the arms start at the top", is(down, 194, 10, Ui.LABEL) && is(down, 204, 10, Ui.LABEL));
		assertFalse(is(down, 194, 15, Ui.LABEL));

		edt(() -> p.fold(true, false));
		final BufferedImage up = PanelFixtures.paint(p.graphsRow());
		assertTrue("the point is at the top", is(up, 199, 10, Ui.LABEL));
		assertFalse(is(up, 199, 15, Ui.LABEL));
		assertTrue("the arms end at the bottom", is(up, 194, 15, Ui.LABEL) && is(up, 204, 15, Ui.LABEL));
		assertFalse(is(up, 194, 10, Ui.LABEL));

		for (BufferedImage img : new BufferedImage[] {down, up})
		{
			int ink = 0;
			for (int y = 0; y < 26; y++)
			{
				for (int x = 150; x < 213; x++)
				{
					if (is(img, x, y, Ui.LABEL))
					{
						ink++;
						assertTrue("chevron ink inside x 194 .. 204, y 10 .. 16: " + x + "," + y,
							x >= 194 && x <= 204 && y >= 10 && y <= 16);
					}
				}
			}
			assertEquals("two arms of six pixels meeting in one", 11, ink);
		}
	}

	@Test
	public void anOpenRowIsLighter()
	{
		final WhyLagPanel p = panel(PanelFixtures.quiet(), false);
		assertTrue(is(PanelFixtures.paint(p.graphsRow()), 100, 3, Ui.CARD));
		assertTrue(is(PanelFixtures.paint(p.lagsRow()), 100, 3, Ui.CARD));
		edt(() -> p.fold(true, true));
		assertTrue(is(PanelFixtures.paint(p.graphsRow()), 100, 3, Ui.CARD_SELECTED));
		assertTrue(is(PanelFixtures.paint(p.lagsRow()), 100, 3, Ui.CARD_SELECTED));
		assertEquals(new java.awt.Color(0x3C3C3C), Ui.CARD_SELECTED);
	}

	/** n is the closed events of the range, the snapshot's rangeEvents: "Lags (0)", "Lags (1)", "Lags (500)". */
	@Test
	public void lagsCountIsTheRangesEvents()
	{
		assertEquals("Lags (0)", lagsWords(PanelFixtures.quiet()));
		assertEquals("Lags (1)", lagsWords(PanelFixtures.lag()));
		assertEquals("Lags (500)", lagsWords(PanelFixtures.fiveHundred()));
		assertEquals("Lags (7)", lagsWords(PanelFixtures.sevenEvents()));
		assertEquals("Graphs", panel(PanelFixtures.quiet(), false).graphsRow().words());
	}

	private static String lagsWords(PanelFixtures.Fixture f)
	{
		final WhyLagPanel p = panel(f, false);
		final StringBuilder painted = new StringBuilder();
		for (Drawn d : record(p.lagsRow()))
		{
			painted.append(d.text);
			assertEquals(18, d.y);
		}
		assertEquals(p.lagsRow().words(), painted.toString());
		return painted.toString();
	}

	/** A fold does not touch the selection: the card and the cells keep showing the picked event. */
	@Test
	public void foldingKeepsTheSelection()
	{
		final WhyLagPanel p = panel(PanelFixtures.lag(), true);
		assertEquals(3, p.selectedId());
		edt(() -> p.fold(true, false));
		assertEquals(3, p.selectedId());
		edt(() -> p.show(PanelFixtures.lag().snapshot));
		assertEquals(3, p.selectedId());
		assertEquals("World lag", p.card().line1());
		assertTrue(p.cells().cells()[1].culprit);
		edt(() -> p.fold(false, false));
		assertEquals(3, p.selectedId());
		assertEquals("World lag", p.card().line1());
	}

	/** Open, hidden, shown again: still open. (A hide clears the selection, never the folds.) */
	@Test
	public void theStateOutlivesDeactivate()
	{
		final WhyLagPanel p = panel(PanelFixtures.lag(), true);
		edt(p::onDeactivate);
		assertTrue(p.graphsOpen());
		assertTrue(p.lagsOpen());
		assertEquals("the hide cleared the selection", -1, p.selectedId());
		edt(p::onActivate);
		assertTrue(p.graphsOpen());
		assertTrue(p.lagsOpen());
		assertTrue(children(p).contains(p.strips()));
		assertTrue(children(p).contains(p.eventList()));
		assertTrue(p.graphsRow().isOpen() && p.lagsRow().isOpen());
	}

	/** T11 gains "a click on a fold row": one press, one layout. */
	@Test
	public void aClickOnAFoldRowLaysOutOnce()
	{
		final WhyLagPanel p = panel(PanelFixtures.lag(), false);
		final PanelFixtures.Counting counting = PanelFixtures.Counting.install();
		try
		{
			press(p.graphsRow(), 100, 10);
			assertEquals(1, counting.layoutsOf(p));
			press(p.lagsRow(), 100, 10);
			assertEquals(2, counting.layoutsOf(p));
			press(p.lagsRow(), 100, 10);
			assertEquals(3, counting.layoutsOf(p));
			edt(() -> p.fold(false, false));
			assertEquals(4, counting.layoutsOf(p));
			edt(() -> p.fold(false, false));
			assertEquals("no change, no layout", 4, counting.layoutsOf(p));
		}
		finally
		{
			counting.close();
		}
	}

	@Test
	public void panelControlFolds()
	{
		final WhyLagPanel p = panel(PanelFixtures.lag(), false);
		edt(() -> p.fold(true, false));
		assertTrue(p.graphsOpen());
		assertFalse(p.lagsOpen());
		assertTrue(children(p).contains(p.strips()));
		assertFalse(children(p).contains(p.eventList()));
		edt(() -> p.fold(false, true));
		assertFalse(p.graphsOpen());
		assertTrue(p.lagsOpen());
		assertFalse(children(p).contains(p.strips()));
		assertTrue(children(p).contains(p.eventList()));
		PanelFixtures.layOut(p);
		assertEquals("Lags open with one row: 443 + 3 + 24, less the button's 10 + 29", 443 + 3 + 24 - 10 - 29,
			PanelFixtures.contentHeight(p));
	}

	private static List<Component> children(WhyLagPanel p)
	{
		return PanelFixtures.onEdt(() -> Arrays.asList(p.getComponents()));
	}
}
