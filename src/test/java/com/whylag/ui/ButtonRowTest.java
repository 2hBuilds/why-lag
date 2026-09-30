package com.whylag.ui;

import java.awt.GraphicsEnvironment;
import java.awt.Toolkit;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.StringSelection;
import java.awt.datatransfer.Transferable;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.List;
import javax.swing.Timer;
import org.junit.Assume;
import org.junit.Test;
import static com.whylag.ui.PanelFixtures.Drawn;
import static com.whylag.ui.PanelFixtures.StubActions;
import static com.whylag.ui.PanelFixtures.edt;
import static com.whylag.ui.PanelFixtures.onEdt;
import static com.whylag.ui.PanelFixtures.panel;
import static com.whylag.ui.PanelFixtures.press;
import static com.whylag.ui.PanelFixtures.record;
import static com.whylag.ui.PanelFixtures.tip;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

/**
 * The button (contract 5, block 9): "Copy report" alone since the world test was parked on 2026-09-30. It fills the
 * row's whole width, asks the actions for the text of the last snapshot, puts it on the clipboard and reads "Copied"
 * for 2 s; before the first snapshot it is disabled with "Nothing to copy yet".
 */
public class ButtonRowTest
{
	/**
	 * One button: the row draws "Copy report" and nothing else, in the same height, colours and font as before, and
	 * its bounds are the row's whole width, so a press on the first pixel and on the last both copy.
	 */
	@Test
	public void theOneButtonFillsTheRow()
	{
		final StubActions actions = new StubActions();
		final WhyLagPanel p = panel(PanelFixtures.quiet(), false, false, false, actions);
		final List<String> copied = fakeClipboard(p);
		final ButtonRow row = p.buttons();
		final int width = row.getWidth();
		assertEquals("the panel's own 225 gives its blocks 213", 213, width);
		assertEquals(ButtonRow.HEIGHT, row.getHeight());

		final List<Drawn> drawn = record(row);
		assertEquals("one button, one text: " + drawn, 1, drawn.size());
		final Drawn words = find(drawn, "Copy report");
		assertEquals("full strength, the text colour", Ui.TEXT.getRGB() & 0xFFFFFF, words.colour.getRGB() & 0xFFFFFF);
		assertEquals(255, words.colour.getAlpha());
		assertEquals(Ui.RS, words.font);
		assertEquals(19, words.y);
		assertEquals("centred in the whole row", (width - words.width) / 2, words.x);

		assertTrue("its bounds are the row's width", ButtonRow.onCopyButton(0, 0, width)
			&& ButtonRow.onCopyButton(width - 1, ButtonRow.HEIGHT - 1, width)
			&& !ButtonRow.onCopyButton(width, 0, width) && !ButtonRow.onCopyButton(-1, 0, width));
		assertNull("enabled: no tooltip anywhere on it", tip(row, 0, 14));
		press(row, 0, 14);
		assertEquals("the first pixel copies", 1, actions.reported.size());
		press(row, width - 1, 14);
		assertEquals("the last pixel copies", 2, actions.reported.size());
		assertEquals(2, copied.size());
		edt(p::onDeactivate);
	}

	@Test
	public void copyPutsTheReportOnTheClipboard() throws Exception
	{
		Assume.assumeFalse("headless has no clipboard", GraphicsEnvironment.isHeadless());
		final Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
		final String before = text(clipboard);
		final WhyLagPanel p = PanelFixtures.onEdt(() ->
		{
			final WhyLagPanel made = new WhyLagPanel(new StubActions(), null, false);
			made.onActivate();
			made.show(PanelFixtures.quiet().snapshot);
			return made;
		});
		try
		{
			// The system clipboard can be held for a moment by another program: press again then.
			String got = null;
			for (int attempt = 0; attempt < 10 && !StubActions.REPORT.equals(got); attempt++)
			{
				if (attempt > 0)
				{
					Thread.sleep(100);
				}
				press(p.buttons(), 150, 14);
				got = text(clipboard);
			}
			assertEquals(StubActions.REPORT, got);
			assertEquals("Copied", p.buttons().copyWords());
		}
		finally
		{
			edt(p::onDeactivate);
			if (before != null)
			{
				edt(() -> clipboard.setContents(new StringSelection(before), null));
			}
		}
	}

	/** The text is the actions' report of the LAST snapshot shown; copyReport answers it too. */
	@Test
	public void copyAsksTheActionsForTheText()
	{
		final StubActions actions = new StubActions();
		final WhyLagPanel p = panel(PanelFixtures.quiet(), false, false, false, actions);
		final List<String> copied = fakeClipboard(p);
		edt(() -> p.show(PanelFixtures.lag().snapshot));
		press(p.buttons(), 150, 14);
		assertEquals(1, actions.reported.size());
		assertSame("the last snapshot", p.last(), actions.reported.get(0));
		assertEquals(PanelFixtures.lag().snapshot.verdict.headline, actions.reported.get(0).verdict.headline);
		assertEquals(1, copied.size());
		assertEquals(StubActions.REPORT, copied.get(0));
		assertEquals(StubActions.REPORT, onEdt(p::copyReport));
		assertEquals(2, copied.size());
		edt(p::onDeactivate);
	}

	/** One non-repeating Swing timer of 2 s: "Copied", then "Copy report" again when it fires. */
	@Test
	public void copiedForTwoSeconds()
	{
		final WhyLagPanel p = panel(PanelFixtures.quiet(), false);
		fakeClipboard(p);
		final ButtonRow row = p.buttons();
		final Timer timer = row.copiedTimer();
		assertFalse(timer.isRunning());
		press(row, 150, 14);
		assertEquals("Copied", row.copyWords());
		assertTrue(timer.isRunning());
		assertEquals(2000, timer.getInitialDelay());
		assertEquals(2000, timer.getDelay());
		assertFalse(timer.isRepeats());
		assertEquals("Copied", find(record(row), "Copied").text);
		edt(() ->
		{
			timer.stop();
			for (ActionListener l : timer.getActionListeners())
			{
				l.actionPerformed(new ActionEvent(timer, ActionEvent.ACTION_PERFORMED, null));
			}
		});
		assertEquals("after 2 s", "Copy report", row.copyWords());
		assertEquals("Copy report", find(record(row), "Copy report").text);

		press(row, 150, 14);
		assertEquals("a second copy shows it again", "Copied", row.copyWords());
		assertTrue("restarted", timer.isRunning());
		assertSame("the one timer, reused", timer, row.copiedTimer());
		edt(p::onDeactivate);
	}

	/** Before the first snapshot "Copy report" is disabled, dimmed, says "Nothing to copy yet" and copies nothing. */
	@Test
	public void copyIsDisabledUntilTheFirstSnapshot()
	{
		final StubActions actions = new StubActions();
		final WhyLagPanel p = onEdt(() -> new WhyLagPanel(actions, null, false));
		final List<String> copied = fakeClipboard(p);
		edt(p::onActivate);
		assertFalse(p.buttons().copyEnabled());
		assertEquals("Nothing to copy yet", tip(p.buttons(), 150, 14));
		press(p.buttons(), 150, 14);
		assertTrue(actions.reported.isEmpty());
		assertTrue(copied.isEmpty());
		assertEquals("", onEdt(p::copyReport));
		assertEquals(120, find(record(p.buttons()), "Copy report").colour.getAlpha());
		edt(() -> p.show(PanelFixtures.quiet().snapshot));
		assertTrue(p.buttons().copyEnabled());
		assertNull("enabled: no tooltip", tip(p.buttons(), 150, 14));
		assertEquals(255, find(record(p.buttons()), "Copy report").colour.getAlpha());
	}

	// ------------------------------------------------------------------ helpers

	private static List<String> fakeClipboard(WhyLagPanel p)
	{
		final List<String> copied = new ArrayList<>();
		p.clipboard = text ->
		{
			copied.add(text);
			return true;
		};
		return copied;
	}

	private static String text(Clipboard clipboard) throws Exception
	{
		for (int attempt = 0; ; attempt++)
		{
			try
			{
				final Transferable t = clipboard.getContents(null);
				return t != null && t.isDataFlavorSupported(DataFlavor.stringFlavor)
					? (String) t.getTransferData(DataFlavor.stringFlavor) : null;
			}
			catch (IllegalStateException busy)
			{
				if (attempt >= 20)
				{
					throw busy;
				}
				Thread.sleep(50);
			}
		}
	}

	private static Drawn find(List<Drawn> drawn, String text)
	{
		for (Drawn d : drawn)
		{
			if (d.text.equals(text))
			{
				return d;
			}
		}
		throw new AssertionError("not drawn: " + text + " in " + drawn);
	}
}
