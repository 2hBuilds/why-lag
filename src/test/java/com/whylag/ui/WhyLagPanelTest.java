package com.whylag.ui;

import com.whylag.GraphRange;
import com.whylag.PanelActions;
import com.whylag.Report;
import com.whylag.core.BadgeSettings;
import com.whylag.core.BadgeStyle;
import com.whylag.core.LagEvent;
import com.whylag.core.PanelSnapshot;
import com.whylag.core.ReportText;
import com.whylag.core.WhenSmooth;
import java.awt.Window;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Predicate;
import javax.swing.JCheckBoxMenuItem;
import javax.swing.JComponent;
import javax.swing.JPopupMenu;
import javax.swing.Timer;
import org.junit.Test;
import static com.whylag.ui.PanelFixtures.contentHeight;
import static com.whylag.ui.PanelFixtures.edt;
import static com.whylag.ui.PanelFixtures.lag;
import static com.whylag.ui.PanelFixtures.layOut;
import static com.whylag.ui.PanelFixtures.onEdt;
import static com.whylag.ui.PanelFixtures.panel;
import static com.whylag.ui.PanelFixtures.quiet;
import static com.whylag.ui.PanelFixtures.yOf;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

/**
 * The panel of picture 18 as a whole (contract 5): the three heights of section 5 - quiet and folded 322 px, a lag
 * found and folded 404, the same opened with one event in the list 600 (each 39 px under the picture's own 361, 443
 * and 639: the button's 10 px gap and 29 px, taken away with 1.0.1, lot C) - and the y of every block in each; the
 * blocks in the table's order, each 213 wide; the session counts last, and the footer in developer mode under them;
 * the card before the first snapshot; the selection and the range as {@code PanelControl} drives them; the gear's
 * menu and the Troubleshoot window (the fallback after 3 s, the re-use of a window that stands, the hide); and the
 * navigation icon.
 */
public class WhyLagPanelTest
{
	/** What 1.0.1, lot C takes away from the picture's own content: the button, its 10 px gap and its 29 px. */
	private static final int BUTTON = 10 + 29;

	@Test
	public void quietFoldedIsThreeHundredTwentyTwoHigh()
	{
		final WhyLagPanel p = panel(quiet(), false);
		assertEquals("the quiet card, with its 'Last lag' line", 95, p.card().height());
		assertEquals(361 - BUTTON, contentHeight(p));
		assertEquals(361 - BUTTON + 12, p.getPreferredSize().height);
	}

	@Test
	public void lagFoldedIsFourHundredFourHigh()
	{
		final WhyLagPanel p = panel(lag(), false);
		assertEquals("two big lines, a two-line proof, a one-line fix, a when line", 177, p.card().height());
		assertEquals(2, p.card().proofLines().size());
		assertEquals(1, p.card().fixLines().size());
		assertEquals(443 - BUTTON, contentHeight(p));
	}

	@Test
	public void lagOpenedHasTheThreeLaneGraph()
	{
		final WhyLagPanel p = panel(lag(), true);
		assertEquals("one event in the list", 1, p.eventList().rows().size());
		assertEquals(106, p.strips().getHeight());
		assertEquals(24, p.eventList().getHeight());
		assertEquals(579 - BUTTON, contentHeight(p));
	}

	/** The y of each block of section 5's table, in the three states. */
	@Test
	public void everyBlockSitsAtItsY()
	{
		final WhyLagPanel quiet = panel(quiet(), false);
		assertYs(quiet, new int[] {0, 28, 131, 191, 218, 248, 286, 306});

		final WhyLagPanel folded = panel(lag(), false);
		assertYs(folded, new int[] {0, 28, 213, 273, 300, 330, 368, 388});

		final WhyLagPanel open = panel(lag(), true);
		assertYs(open, new int[] {0, 28, 213, 273, 300, 329, 439, 468, 504, 524});
	}

	private static void assertYs(WhyLagPanel p, int[] ys)
	{
		final List<JComponent> blocks = PanelFixtures.blocks(p);
		assertEquals("blocks in the layout", ys.length, blocks.size());
		for (int i = 0; i < ys.length; i++)
		{
			assertEquals(PanelFixtures.label(blocks.get(i)), ys[i], yOf(p, blocks.get(i)));
		}
	}

	/** Top down, the table's order; a folded block is absent and an open one sits under its row. */
	@Test
	public void theBlocksAreInTheTablesOrder()
	{
		final WhyLagPanel folded = panel(quiet(), false);
		assertEquals(Arrays.asList(folded.header(), folded.card(), folded.cells(), folded.rangeRow(),
			folded.graphsRow(), folded.lagsRow(), folded.sessionHeader(), folded.counts()),
			PanelFixtures.blocks(folded));

		final WhyLagPanel open = panel(quiet(), true);
		assertEquals(Arrays.asList(open.header(), open.card(), open.cells(), open.rangeRow(), open.graphsRow(),
			open.strips(), open.lagsRow(), open.eventList(), open.sessionHeader(), open.counts()),
			PanelFixtures.blocks(open));
	}

	/** The panel's LAST component is the session's counts in every state; the footer follows them in developer mode only. */
	@Test
	public void thePanelsLastComponentIsTheSessionCounts()
	{
		for (boolean open : new boolean[] {false, true})
		{
			final WhyLagPanel p = panel(lag(), open);
			assertSame("open " + open, p.counts(), p.getComponent(p.getComponentCount() - 1));
		}
		final PanelFixtures.Fixture f = new PanelFixtures.Fixture("dev", PanelFixtures.withFooter(quiet().snapshot,
			"self: frame 180 ns, tick 2 us, step 40 us"), -1);
		final WhyLagPanel dev = panel(f, false, false, true, new PanelFixtures.StubActions());
		assertSame(dev.footer(), dev.getComponent(dev.getComponentCount() - 1));
		assertSame(dev.counts(), dev.getComponent(dev.getComponentCount() - 2));
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
				assertEquals(PanelFixtures.label(block), 6, block.getX());
				assertEquals(PanelFixtures.label(block), 213, block.getWidth());
				assertEquals(PanelFixtures.label(block), 213, block.getPreferredSize().width);
			}
		}
	}

	/** Developer mode adds the footer, 14 high, 6 px under the session counts; outside it there is none. */
	@Test
	public void theFooterSitsUnderTheCountsInDeveloperModeOnly()
	{
		final PanelFixtures.Fixture f = new PanelFixtures.Fixture("dev", PanelFixtures.withFooter(quiet().snapshot,
			"self: frame 180 ns, tick 2 us, step 40 us"), -1);
		final WhyLagPanel dev = panel(f, false, false, true, new PanelFixtures.StubActions());
		assertEquals(361 - BUTTON + 6 + 14, contentHeight(dev));
		assertEquals(361 - BUTTON + 6, yOf(dev, dev.footer()));
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

	/**
	 * Before the first snapshot: "Measuring" with no proof, 46 high; the gear opens no menu and "Troubleshoot..." opens
	 * no window and answers "".
	 */
	@Test
	public void aNewPanelShowsTheCardBeforeTheFirstSnapshot()
	{
		final WhyLagPanel p = PanelFixtures.onEdt(() -> new WhyLagPanel(new PanelFixtures.StubActions(),
			GraphRange.TEN_MIN, false));
		assertEquals("Measuring", p.card().line1());
		assertEquals(46, p.card().height());
		assertEquals(10, p.range());
		assertFalse(p.isActive());
		assertEquals(0, p.activations());
		assertEquals(0, p.deactivations());
		assertSame(p, p.component());
		assertEquals(20 + 8 + 46 + 8 + 48 + 12 + 23 + 4 + 26 + 4 + 26 + 12 + 16 + 4 + 16,
			PanelFixtures.onEdt(() -> contentHeight(p)).intValue());

		PanelFixtures.onEdt(() ->
		{
			p.dialogOpener = (owner, clipboard) ->
			{
				throw new AssertionError("no window before the first snapshot");
			};
			return null;
		});
		PanelFixtures.pressGear(p);
		assertNull("the gear opens no menu", p.menu());
		assertEquals("", onEdt(p::troubleshoot));
		assertNull("and no window", p.dialog());
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
	 * The icon enlarged 8x ({@link #navIcon8x()}) is what the probe's {@code PicturesTest} writes for the lead.
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

		final BufferedImage big = navIcon8x();
		assertEquals(128, big.getWidth());
		assertEquals(128, big.getHeight());
		assertEquals("the clear top-right corner shows the dark ground", 0xFF1E1E1E, big.getRGB(127, 0));
	}

	/**
	 * The sidebar icon enlarged 8x on a dark ground, nearest neighbour, for the lead to look at: the probe's
	 * {@code PicturesTest} writes it as {@code nav-icon-8x}.
	 */
	public static BufferedImage navIcon8x()
	{
		final BufferedImage icon = NavIcon.create();
		final BufferedImage big = new BufferedImage(128, 128, BufferedImage.TYPE_INT_ARGB);
		final java.awt.Graphics2D g = big.createGraphics();
		g.setRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION,
			java.awt.RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
		g.setColor(new java.awt.Color(30, 30, 30));
		g.fillRect(0, 0, 128, 128);
		g.drawImage(icon, 0, 0, 128, 128, null);
		g.dispose();
		return big;
	}

	// ------------------------------------------------------------------ the gear's menu (1.0.1, lot C)

	/** The gear builds the menu FRESH from the last snapshot at every press; before a snapshot it builds none. */
	@Test
	public void theGearBuildsTheMenuFreshFromTheLastSnapshot()
	{
		final PanelFixtures.StubActions actions = new PanelFixtures.StubActions();
		final WhyLagPanel p = panel(quiet(), false, false, false, actions);
		assertNull("nothing is built until the gear is pressed", p.menu());

		PanelFixtures.pressGear(p);
		final JPopupMenu first = p.menu();
		assertNotNull(first);
		assertTrue("the defaults: the badge shows", ((JCheckBoxMenuItem) first.getComponent(0)).isSelected());
		assertTrue("the defaults: the chat line is on", ((JCheckBoxMenuItem) first.getComponent(3)).isSelected());

		final PanelSnapshot changed = quiet().snapshot.withBadgeSettings(new BadgeSettings(false, BadgeStyle.SHAPE_ONLY,
			WhenSmooth.HIDE, false));
		edt(() -> p.show(changed));
		PanelFixtures.pressGear(p);
		final JPopupMenu second = p.menu();
		assertNotSame("a new menu at every open", first, second);
		assertFalse("it ticks what the newest snapshot holds", ((JCheckBoxMenuItem) second.getComponent(0)).isSelected());
		assertFalse(((JCheckBoxMenuItem) second.getComponent(3)).isSelected());
		assertTrue("the first menu is left as it was", ((JCheckBoxMenuItem) first.getComponent(0)).isSelected());
		assertTrue("building a menu asks nothing of the actions", actions.written.isEmpty() && actions.reported.isEmpty());
	}

	/** Closing the menu by any means tells the header, whose press guard then swallows the gear press that follows. */
	@Test
	public void theMenusCloseIsTheHeadersGuard()
	{
		final WhyLagPanel p = panel(quiet(), false);
		final long[] now = {1_000};
		edt(() -> p.header().clock = () -> now[0]);
		PanelFixtures.pressGear(p);
		final JPopupMenu first = p.menu();

		// The popup closes (a press elsewhere, Escape, a choice) and the gear's own press follows within a click.
		PanelFixtures.closePopup(first);
		now[0] += 100;
		PanelFixtures.pressGear(p);
		assertSame("that press was the close: nothing opened", first, p.menu());

		now[0] += HeaderRow.MENU_REOPEN_MS;
		PanelFixtures.pressGear(p);
		assertNotSame("after the guard a press opens again", first, p.menu());
	}

	// ------------------------------------------------------------------ the Troubleshoot window (1.0.1, lot C)

	private static final String MINUTE_LINES = "21:50  fps 48/50/51  tick 601/952 ms  ping 40-43 ms  mem 51 %  cpu 37 %"
		+ "  lags 1  masked 0 s\n";
	private static final String NOTE_LINES = "Notes\n20:52:01  plugin started, version 1.0.1, client 1.12.38\n\n"
		+ "Warnings\n(none)\n\nErrors\n(none)\n";
	private static final String FALLBACK_CHECKS = "Verdict: The plugin's sampler did not answer in 3 s.\n\n"
		+ "Checks: not run (the sampler thread did not answer; the notes and the last 60 minutes below are from the"
		+ " last snapshot)\n\n";

	/** Actions that keep every callback: the report "comes back" only when the test says so. */
	private static final class KeptActions implements PanelActions
	{
		final List<Consumer<Report>> backs = new ArrayList<>();

		@Override
		public void rangeChanged(int minutes)
		{
		}

		@Override
		public void activated()
		{
		}

		@Override
		public void testAndReport(PanelSnapshot s, Consumer<Report> back)
		{
			backs.add(back);
		}

		@Override
		public void badgeShow(boolean on)
		{
		}

		@Override
		public void badgeStyle(BadgeStyle style)
		{
		}

		@Override
		public void badgeWhenSmooth(WhenSmooth choice)
		{
		}

		@Override
		public void badgeChatLine(boolean on)
		{
		}
	}

	/** A window-less opener that keeps every window it made, so a test can count them. */
	private static final class Windows implements BiFunction<Window, Predicate<String>, TroubleshootDialog>
	{
		final List<TroubleshootDialog> made = new ArrayList<>();

		@Override
		public TroubleshootDialog apply(Window owner, Predicate<String> clipboard)
		{
			final TroubleshootDialog d = new TroubleshootDialog(clipboard);
			made.add(d);
			return d;
		}
	}

	/** The quiet snapshot with a version, a minute log and diagnostics, as the plugin's own carry them. */
	private static PanelSnapshot withDiagnostics()
	{
		return quiet().snapshot.withDiagnostics("1.0.1", MINUTE_LINES, NOTE_LINES);
	}

	/** A panel shown {@code snapshot}, laid out, whose clipboard keeps every text in {@code copied}. */
	private static WhyLagPanel keptPanel(KeptActions actions, PanelSnapshot snapshot, List<String> copied,
		Windows windows)
	{
		final WhyLagPanel p = onEdt(() ->
		{
			final WhyLagPanel made = new WhyLagPanel(actions, null, false);
			made.dialogOpener = windows;
			made.onActivate();
			made.show(snapshot);
			layOut(made);
			return made;
		});
		p.clipboard = text ->
		{
			copied.add(text);
			return true;
		};
		return p;
	}

	/** Runs the timer's listeners once on the Swing thread, as it would when it fires. */
	private static void fire(Timer timer)
	{
		edt(() ->
		{
			timer.stop();
			for (ActionListener l : timer.getActionListeners())
			{
				l.actionPerformed(new ActionEvent(timer, ActionEvent.ACTION_PERFORMED, null));
			}
		});
	}

	/**
	 * A sampler that never answers: the window opens on "Testing..." and a one-shot timer of 3 s runs from the press;
	 * when it fires the window shows the panel's own report of the last snapshot, with the Verdict and Checks lines of
	 * a test that was not run, and "Sampler did not answer in 3 s" as its verdict. Nothing is copied until the player
	 * presses Copy report.
	 */
	@Test
	public void aTestThatNeverAnswersFillsTheWindowWithAFallbackAfterThreeSeconds()
	{
		final KeptActions actions = new KeptActions();
		final List<String> copied = new ArrayList<>();
		final Windows windows = new Windows();
		final PanelSnapshot snapshot = withDiagnostics();
		final WhyLagPanel p = keptPanel(actions, snapshot, copied, windows);
		final Timer timeout = p.testTimeout();
		assertFalse("no timer before a press", timeout.isRunning());

		assertEquals("a stub that never answers: nothing at once", "", onEdt(p::troubleshoot));
		assertEquals(1, windows.made.size());
		final TroubleshootDialog window = windows.made.get(0);
		assertSame(window, p.dialog());
		assertEquals("Testing...", window.verdictArea.getText());
		assertEquals("", window.reportArea.getText());
		assertTrue(timeout.isRunning());
		assertEquals(3000, timeout.getInitialDelay());
		assertEquals(3000, timeout.getDelay());
		assertFalse("one shot", timeout.isRepeats());
		assertEquals(1, actions.backs.size());

		fire(timeout);
		final String text = window.reportArea.getText();
		assertTrue(text, text.startsWith("2h Why Lag 1.0.1 report"));
		assertTrue(text, text.contains("Verdict: The plugin's sampler did not answer in 3 s.\n"));
		assertTrue(text, text.contains("Checks: not run"));
		assertTrue("the minute log of the last snapshot", text.contains("\nLast 60 minutes\n" + MINUTE_LINES));
		assertTrue("its notes, warnings and errors", text.endsWith(NOTE_LINES));
		assertEquals("the seam of ReportText: the block lands where a real one would",
			ReportText.of(snapshot, FALLBACK_CHECKS), text);
		assertEquals("Sampler did not answer in 3 s", window.verdictArea.getText());
		assertEquals(text, window.report());
		assertTrue("Copy report is ready", window.copyButton.isEnabled());
		assertFalse(timeout.isRunning());
		assertTrue("nothing is copied by itself", copied.isEmpty());
		edt(window.copyButton::doClick);
		assertEquals("the copy is exactly the fallback report", Arrays.asList(text), copied);
		edt(p::onDeactivate);
	}

	/** The real answer, late, is ignored; the next Troubleshoot re-uses the window for a new test. */
	@Test
	public void aLateAnswerAfterTheTimeoutIsIgnoredAndTheNextPressReusesTheWindow()
	{
		final KeptActions actions = new KeptActions();
		final List<String> copied = new ArrayList<>();
		final Windows windows = new Windows();
		final WhyLagPanel p = keptPanel(actions, withDiagnostics(), copied, windows);
		edt(p::troubleshoot);
		final TroubleshootDialog window = windows.made.get(0);
		fire(p.testTimeout());
		final String fallback = window.reportArea.getText();
		assertEquals("Sampler did not answer in 3 s", window.verdictArea.getText());

		edt(() -> actions.backs.get(0).accept(new Report("the late, real report", "A real verdict")));
		assertEquals("the window still holds the fallback", fallback, window.reportArea.getText());
		assertEquals("Sampler did not answer in 3 s", window.verdictArea.getText());
		assertFalse(p.testTimeout().isRunning());

		edt(p::troubleshoot);
		assertEquals("a second Troubleshoot while a window stands re-uses it", 1, windows.made.size());
		assertSame(window, p.dialog());
		assertEquals("a new test: Testing... again", "Testing...", window.verdictArea.getText());
		assertEquals("with an empty box", "", window.reportArea.getText());
		assertFalse(window.copyButton.isEnabled());
		assertEquals("the next press is a new test", 2, actions.backs.size());
		assertTrue(p.testTimeout().isRunning());
		edt(() -> actions.backs.get(0).accept(new Report("the first press again", "stale")));
		assertEquals("a stale answer does not end the new test", "Testing...", window.verdictArea.getText());
		assertTrue(p.testTimeout().isRunning());
		edt(() -> actions.backs.get(1).accept(new Report("the second, real report", "Its verdict")));
		assertEquals("the second, real report", window.reportArea.getText());
		assertEquals("Its verdict", window.verdictArea.getText());
		assertFalse(p.testTimeout().isRunning());
		assertTrue("nothing is copied by itself", copied.isEmpty());
		edt(p::onDeactivate);
	}

	/** An answer before the timeout stops it, and its report is the one in the window. */
	@Test
	public void anAnswerBeforeTheTimeoutStopsIt()
	{
		final PanelFixtures.StubActions stub = new PanelFixtures.StubActions();
		final WhyLagPanel p = panel(quiet(), false, false, false, stub);
		assertEquals("answered before the press returned", PanelFixtures.StubActions.REPORT, onEdt(p::troubleshoot));
		assertFalse("so nothing runs", p.testTimeout().isRunning());
		assertEquals(PanelFixtures.StubActions.REPORT, p.dialog().reportArea.getText());
		assertEquals(PanelFixtures.StubActions.VERDICT, p.dialog().verdictArea.getText());
		assertEquals("the snapshot handed over is the last one", Arrays.asList(p.last()), stub.reported);

		final KeptActions actions = new KeptActions();
		final Windows windows = new Windows();
		final WhyLagPanel q = keptPanel(actions, withDiagnostics(), new ArrayList<>(), windows);
		edt(q::troubleshoot);
		assertTrue(q.testTimeout().isRunning());
		edt(() -> actions.backs.get(0).accept(new Report("the real report", "A real verdict")));
		assertFalse("the answer stopped it", q.testTimeout().isRunning());
		assertEquals("the real report", windows.made.get(0).reportArea.getText());
		assertEquals("A real verdict", windows.made.get(0).verdictArea.getText());
		edt(p::onDeactivate);
		edt(q::onDeactivate);
	}

	/** Hiding the panel closes the window and stops the timeout; an answer that comes after goes nowhere. */
	@Test
	public void hidingThePanelDisposesTheWindowAndStopsTheTimeout()
	{
		final KeptActions actions = new KeptActions();
		final Windows windows = new Windows();
		final WhyLagPanel p = keptPanel(actions, withDiagnostics(), new ArrayList<>(), windows);
		edt(p::troubleshoot);
		final TroubleshootDialog window = windows.made.get(0);
		assertTrue(p.testTimeout().isRunning());
		assertTrue(window.isOpen());

		edt(p::onDeactivate);
		assertFalse("the timeout stopped", p.testTimeout().isRunning());
		assertFalse("the window was disposed", window.isOpen());
		assertNull("the panel keeps none", p.dialog());

		edt(() -> actions.backs.get(0).accept(new Report("a report nobody waits for", "v")));
		assertEquals("the closed window was not filled", "", window.report());

		edt(p::onActivate);
		edt(p::troubleshoot);
		assertEquals("a window that was closed is not re-used", 2, windows.made.size());
		edt(p::onDeactivate);
	}

	/** Close disposes the window, and the next Troubleshoot makes a new one rather than re-using the closed one. */
	@Test
	public void aClosedWindowIsNotReused()
	{
		final KeptActions actions = new KeptActions();
		final Windows windows = new Windows();
		final WhyLagPanel p = keptPanel(actions, withDiagnostics(), new ArrayList<>(), windows);
		edt(p::troubleshoot);
		final TroubleshootDialog first = windows.made.get(0);
		edt(first.closeButton::doClick);
		assertFalse(first.isOpen());

		edt(p::troubleshoot);
		assertEquals(2, windows.made.size());
		assertSame(windows.made.get(1), p.dialog());
		assertTrue(windows.made.get(1).isOpen());
		edt(p::onDeactivate);
	}
}
