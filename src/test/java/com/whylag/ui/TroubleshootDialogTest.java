package com.whylag.ui;

import com.whylag.Report;
import com.whylag.Version;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Font;
import java.awt.GraphicsEnvironment;
import java.awt.Window;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import javax.swing.JDialog;
import javax.swing.JScrollPane;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import net.runelite.client.ui.ColorScheme;
import org.junit.Test;
import static com.whylag.ui.PanelFixtures.edt;
import static com.whylag.ui.PanelFixtures.onEdt;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

/**
 * The Troubleshoot window (1.0.1, lot C, C3 and C6 item 3): the controls are built without a window and pressed here;
 * the {@link JDialog} itself is built (not shown) only where a display exists. The window opens on "Testing..." with an
 * empty report; {@code show} fills both; Copy report copies exactly the report's text and reads "Copied" for 2 s (a
 * driven timer); Close disposes; and a second test in a window that stands shows "Testing..." again.
 */
public class TroubleshootDialogTest
{
	private static final String REPORT = "2h Why Lag 1.0.1 report - sample\nVerdict: Everything looks fine here.\n\nChecks\n"
		+ "  C1 ok\n";

	private static TroubleshootDialog window(List<String> copied)
	{
		return onEdt(() -> new TroubleshootDialog(text ->
		{
			copied.add(text);
			return true;
		}));
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

	/** It opens on "Testing..." with an empty report, Copy report waiting and Close ready. */
	@Test
	public void itOpensOnTestingWithAnEmptyReport()
	{
		final TroubleshootDialog window = window(new ArrayList<>());

		assertEquals("Testing...", window.verdictArea.getText());
		assertEquals("", window.reportArea.getText());
		assertEquals("", window.report());
		assertFalse("nothing to copy yet", window.copyButton.isEnabled());
		assertEquals("Copy report", window.copyButton.getText());
		assertEquals("Close", window.closeButton.getText());
		assertTrue(window.closeButton.isEnabled());
		assertTrue(window.isOpen());
		assertEquals("Testing...", TroubleshootDialog.TESTING);
	}

	/** {@code show} fills the verdict and the report, scrolled to the top, and readies Copy report. */
	@Test
	public void showFillsBothAndReadiesTheCopyButton()
	{
		final TroubleshootDialog window = window(new ArrayList<>());

		edt(() -> window.show(new Report(REPORT, "Everything looks fine here.")));

		assertEquals("Everything looks fine here.", window.verdictArea.getText());
		assertEquals(REPORT, window.reportArea.getText());
		assertEquals("the very text of the report, nothing added", REPORT, window.report());
		assertEquals("scrolled to the top", 0, window.reportArea.getCaretPosition());
		assertTrue(window.copyButton.isEnabled());
		assertEquals("Copy report", window.copyButton.getText());
	}

	/** Copy report copies exactly the report's text, reads "Copied", and reads its label again after 2 s. */
	@Test
	public void copyReportCopiesTheTextAndReadsCopiedForTwoSeconds()
	{
		final List<String> copied = new ArrayList<>();
		final TroubleshootDialog window = window(copied);
		edt(() -> window.show(new Report(REPORT, "v")));
		final Timer timer = window.copiedTimer;
		assertFalse("no timer before a press", timer.isRunning());
		assertEquals(2000, timer.getInitialDelay());
		assertEquals(2000, timer.getDelay());
		assertFalse("one shot", timer.isRepeats());

		edt(window.copyButton::doClick);
		assertEquals("exactly the report, no more, no less", Arrays.asList(REPORT), copied);
		assertEquals("Copied", window.copyButton.getText());
		assertTrue(timer.isRunning());
		assertTrue("the button stays enabled: a second press copies again", window.copyButton.isEnabled());

		fire(timer);
		assertEquals("Copy report", window.copyButton.getText());
		assertFalse(timer.isRunning());
		assertEquals("the copy is not repeated by the timer", 1, copied.size());

		edt(window.copyButton::doClick);
		assertEquals(2, copied.size());
		assertEquals("Copied", window.copyButton.getText());
	}

	/** "Copied" shows only when the clipboard took the text. */
	@Test
	public void aRefusedClipboardLeavesTheButtonAsItWas()
	{
		final List<String> tried = new ArrayList<>();
		final TroubleshootDialog window = onEdt(() -> new TroubleshootDialog(text ->
		{
			tried.add(text);
			return false;
		}));
		edt(() -> window.show(new Report(REPORT, "v")));

		edt(window.copyButton::doClick);

		assertEquals(Arrays.asList(REPORT), tried);
		assertEquals("Copy report", window.copyButton.getText());
		assertFalse(window.copiedTimer.isRunning());
	}

	/** An empty report (a null answer, or one with no text) leaves nothing to copy. */
	@Test
	public void anEmptyReportLeavesNothingToCopy()
	{
		final TroubleshootDialog window = window(new ArrayList<>());

		edt(() -> window.show(null));
		assertEquals("", window.verdictArea.getText());
		assertEquals("", window.reportArea.getText());
		assertFalse(window.copyButton.isEnabled());

		edt(() -> window.show(new Report("", "The checks could not finish")));
		assertEquals("The checks could not finish", window.verdictArea.getText());
		assertFalse(window.copyButton.isEnabled());
	}

	/** Close disposes: the window is no longer open, its timer stops, and a second Close is harmless. */
	@Test
	public void closeDisposes()
	{
		final TroubleshootDialog window = window(new ArrayList<>());
		edt(() -> window.show(new Report(REPORT, "v")));
		edt(window.copyButton::doClick);
		assertTrue(window.copiedTimer.isRunning());
		assertTrue(window.isOpen());

		edt(window.closeButton::doClick);
		assertFalse("closed", window.isOpen());
		assertFalse("its timer stopped", window.copiedTimer.isRunning());
		edt(window::dispose);
		edt(window::toFront);
		assertFalse(window.isOpen());
	}

	/** A window that stands is re-used for a new test: "Testing..." again, an empty box, and no "Copied" left over. */
	@Test
	public void aSecondTestInTheSameWindowShowsTestingAgain()
	{
		final List<String> copied = new ArrayList<>();
		final TroubleshootDialog window = window(copied);
		edt(() -> window.show(new Report(REPORT, "The first verdict")));
		edt(window.copyButton::doClick);
		assertEquals("Copied", window.copyButton.getText());

		edt(window::testing);

		assertEquals("Testing...", window.verdictArea.getText());
		assertEquals("", window.reportArea.getText());
		assertEquals("", window.report());
		assertFalse("nothing to copy while it tests", window.copyButton.isEnabled());
		assertEquals("Copy report", window.copyButton.getText());
		assertFalse(window.copiedTimer.isRunning());
		assertTrue("it still stands", window.isOpen());

		edt(() -> window.show(new Report("the second report\n", "The second verdict")));
		assertEquals("The second verdict", window.verdictArea.getText());
		assertEquals("the second report\n", window.reportArea.getText());
		edt(window.copyButton::doClick);
		assertEquals("only the report on show is copied", Arrays.asList(REPORT, "the second report\n"), copied);
	}

	/** The report box is read-only monospace, the verdict is bold and wrapped, all in RuneLite's dark colours. */
	@Test
	public void theReportBoxIsReadOnlyMonospaceAndTheVerdictIsBoldAndWrapped()
	{
		final TroubleshootDialog window = window(new ArrayList<>());

		assertFalse(window.reportArea.isEditable());
		assertEquals(Font.MONOSPACED, window.reportArea.getFont().getName());
		assertEquals(11, window.reportArea.getFont().getSize());
		assertFalse(window.verdictArea.isEditable());
		assertTrue(window.verdictArea.getLineWrap());
		assertTrue(window.verdictArea.getWrapStyleWord());
		assertTrue(window.verdictArea.getFont().isBold());
		assertEquals(14, window.verdictArea.getFont().getSize());
		assertEquals(Color.WHITE, window.verdictArea.getForeground());
		assertFalse("a read-only box that takes the focus shows a caret", window.verdictArea.isFocusable());
		assertEquals(ColorScheme.DARKER_GRAY_COLOR, window.content.getBackground());
		assertEquals(ColorScheme.DARK_GRAY_COLOR, window.reportArea.getBackground());
	}

	/** The verdict on top, the report in a scroll pane in the middle, the two buttons at the bottom. */
	@Test
	public void theLayoutIsTheVerdictOnTopTheReportInAScrollPaneAndTwoButtons()
	{
		final TroubleshootDialog window = window(new ArrayList<>());
		final BorderLayout layout = (BorderLayout) window.content.getLayout();

		assertSame(window.verdictArea, layout.getLayoutComponent(BorderLayout.NORTH));
		final Component centre = layout.getLayoutComponent(BorderLayout.CENTER);
		assertTrue(centre instanceof JScrollPane);
		assertSame(window.reportArea, ((JScrollPane) centre).getViewport().getView());
		final Container south = (Container) layout.getLayoutComponent(BorderLayout.SOUTH);
		assertEquals(Arrays.asList(window.copyButton, window.closeButton), Arrays.asList(south.getComponents()));
	}

	/** The title names the plugin, its version and the purpose; the size is about 560 x 440. */
	@Test
	public void theTitleNamesThePluginItsVersionAndThePurpose()
	{
		assertEquals("2h Why Lag " + Version.CURRENT + " - Troubleshoot", TroubleshootDialog.TITLE);
		assertEquals(560, TroubleshootDialog.WIDTH);
		assertEquals(440, TroubleshootDialog.HEIGHT);
	}

	/** Where there is a display: a non-modal dialog that disposes on close and whose Close button disposes it. */
	@Test
	public void theHostingDialogIsNonModalDisposesOnCloseAndClosesWhenTheCloseButtonIsPressed() throws Exception
	{
		if (GraphicsEnvironment.isHeadless())
		{
			return;
		}
		final TroubleshootDialog[] made = new TroubleshootDialog[1];
		SwingUtilities.invokeAndWait(() -> made[0] = TroubleshootDialog.window((Window) null, text -> true));
		final TroubleshootDialog window = made[0];
		assertNotNull(window);

		SwingUtilities.invokeAndWait(() ->
		{
			final JDialog dialog = (JDialog) SwingUtilities.getWindowAncestor(window.content);
			assertNotNull("the controls are hosted in a dialog", dialog);
			assertEquals(TroubleshootDialog.TITLE, dialog.getTitle());
			assertEquals(java.awt.Dialog.ModalityType.MODELESS, dialog.getModalityType());
			assertEquals(javax.swing.WindowConstants.DISPOSE_ON_CLOSE, dialog.getDefaultCloseOperation());
			assertEquals(TroubleshootDialog.WIDTH, dialog.getWidth());
			assertEquals(TroubleshootDialog.HEIGHT, dialog.getHeight());
			assertFalse("built, not shown", dialog.isVisible());
			window.closeButton.doClick();
			assertFalse("pressing Close disposed it", dialog.isDisplayable());
			assertFalse(window.isOpen());
		});
	}
}
