package com.whylag.ui;

import com.whylag.core.PanelSnapshot;
import com.whylag.core.Tile;
import java.awt.Component;
import org.junit.Test;
import static com.whylag.ui.PanelFixtures.Counting;
import static com.whylag.ui.PanelFixtures.StubActions;
import static com.whylag.ui.PanelFixtures.edt;
import static com.whylag.ui.PanelFixtures.onEdt;
import static com.whylag.ui.PanelFixtures.panel;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

/**
 * Showing and hiding (T9, T11): while hidden a show keeps the snapshot and paints nothing, and a show asks for a
 * snapshot only through {@code onActivate}; a one-second update of the same words changes no layout and repaints
 * each block once; a hide closes the Troubleshoot window.
 */
public class PanelActivationTest
{
	/** Hidden: the snapshot is kept (it is the one "Troubleshoot..." and the next activation use), nothing is painted. */
	@Test
	public void showWhileInactiveKeepsTheLastAndPaintsNothing()
	{
		final StubActions actions = new StubActions();
		final WhyLagPanel p = panel(PanelFixtures.quiet(), false, false, false, actions);
		assertEquals(1, actions.activated);
		edt(p::onDeactivate);
		assertFalse(p.isActive());
		assertEquals(1, p.deactivations());

		final PanelSnapshot next = PanelFixtures.lag().snapshot;
		final Counting counting = Counting.install();
		try
		{
			edt(() -> p.show(next));
			assertEquals("no repaint while hidden", 0, counting.repaintsOf(p));
			assertEquals("no layout while hidden", 0, counting.layoutsOf(p));
		}
		finally
		{
			counting.close();
		}
		assertSame("the snapshot is kept", next, p.last());
		assertEquals("the blocks still show the one before", "Lags (0)", p.lagsRow().words());
		assertEquals("Last lag 21:47, this world", p.card().when());
		assertEquals(PanelFixtures.StubActions.REPORT, onEdt(p::copyReport));
		assertSame("a copy while hidden reports the kept snapshot", next, actions.reported.get(0));

		edt(p::onActivate);
		assertTrue(p.isActive());
		assertEquals(2, p.activations());
		assertEquals("onActivate asks for one snapshot at once", 2, actions.activated);
		assertEquals("shown again, the kept snapshot is drawn at once", "Lags (1)", p.lagsRow().words());
		edt(p::onDeactivate);
	}

	/** T11: a second of new numbers with the same words, rows and range: no layout, one repaint of each block. */
	@Test
	public void oneSecondUpdateDoesNoLayout()
	{
		for (boolean open : new boolean[] {false, true})
		{
			final WhyLagPanel p = panel(PanelFixtures.clearHere(), open);
			final PanelSnapshot second = numbersMoved(PanelFixtures.clearHere().snapshot);
			final Counting counting = Counting.install();
			try
			{
				edt(() -> p.show(second));
				assertEquals("open " + open + ": no layout", 0, counting.layoutsOf(p));
				for (Component c : p.getComponents())
				{
					assertEquals("open " + open + ": one repaint of " + c.getClass().getSimpleName(), 1,
						counting.repaints(c));
				}
				assertEquals("nothing else is repainted", p.getComponentCount(), counting.repaintsOf(p));
				assertEquals("the numbers did move", "49", p.cells().cells()[0].value);
			}
			finally
			{
				counting.close();
			}
		}

		// A show whose verdict's words change the card's height lays out, once; the same words again do not.
		final WhyLagPanel p = panel(PanelFixtures.quiet(), false);
		final PanelSnapshot held = PanelFixtures.named("answer-w1").snapshot;
		final Counting counting = Counting.install();
		try
		{
			edt(() -> p.show(held));
			assertTrue("the card grew", p.card().height() > 95);
			assertEquals("one layout", 1, counting.layoutsOf(p));
			edt(() -> p.show(numbersMoved(held)));
			assertEquals("the same words again: no more", 1, counting.layoutsOf(p));
		}
		finally
		{
			counting.close();
		}
	}

	/** A hide closes the Troubleshoot window, which stops its "Copied" timer; the panel holds no window after it. */
	@Test
	public void theWindowAndItsTimerStopOnDeactivate()
	{
		final WhyLagPanel p = panel(PanelFixtures.quiet(), false);
		assertEquals(PanelFixtures.StubActions.REPORT, onEdt(p::copyReport));
		final TroubleshootDialog window = p.dialog();
		assertTrue("the window stands", window.isOpen());
		edt(window.copyButton::doClick);
		assertTrue(window.copiedTimer.isRunning());
		assertEquals("Copied", window.copyButton.getText());
		edt(p::onDeactivate);
		assertFalse("the window is closed", window.isOpen());
		assertFalse(window.copiedTimer.isRunning());
		assertNull("and the panel keeps none", p.dialog());
		assertFalse(p.testTimeout().isRunning());
	}

	/** The same snapshot with the frame rate at 49 instead of 50: numbers move, no word of the card does. */
	private static PanelSnapshot numbersMoved(PanelSnapshot s)
	{
		final Tile[] tiles = s.tiles.clone();
		tiles[0] = new Tile(tiles[0].lane, tiles[0].level, "49 fps", tiles[0].sub, tiles[0].noData);
		return new PanelSnapshot(s.wallMs + 1000, s.zone, s.world, s.verdict, tiles, s.rangeMinutes,
			s.rangeStartWallMs + 1000, s.rangeEndWallMs + 1000, s.strips, s.rangeEvents, s.sessionEvents,
			s.sessionCounts, s.sessionTotal, s.sessionStartWallMs, s.settings, s.footer);
	}
}
