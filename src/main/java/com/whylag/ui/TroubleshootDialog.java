package com.whylag.ui;

import com.whylag.Report;
import com.whylag.Version;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dialog;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Insets;
import java.awt.Window;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.function.Predicate;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.Timer;
import javax.swing.WindowConstants;
import javax.swing.border.EmptyBorder;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;

/**
 * The window the gear menu's <i>Troubleshoot...</i> opens (1.0.1, lot C; the shape of 2h Bank Portfolio Tracker's
 * window): the verdict of the ten checks on top in one line, the whole report under it in a read-only monospace
 * box, and two buttons, <i>Copy report</i> and <i>Close</i>. Non-modal, in RuneLite's dark colours, about
 * {@value #WIDTH} x {@value #HEIGHT}.
 *
 * <p>It opens at once on "Testing..." with the report box empty - the checks take a moment - and {@link #show} fills
 * it in when the report comes back. <i>Copy report</i> puts exactly the report's text on the clipboard and reads
 * "Copied" for {@value #COPIED_MS} ms. Nothing is written to disk: the report lives in this window and, when the
 * player presses <i>Copy report</i>, on the clipboard.
 *
 * <p><b>The model and the window are apart.</b> The panel of controls ({@link #content}) is built without a window,
 * so a test can press its buttons on a machine with no screen; the {@link JDialog} that hosts it is made only by
 * {@link #window}. A window that stands is re-used for another test ({@link #testing}); one that was closed is
 * {@linkplain #isOpen not open} and the panel makes a new one.
 *
 * <p>Choice: <i>Copy report</i> is disabled while the window says "Testing..." and while the report is empty, so a
 * press always has a text to copy.
 * <p>Choice: "Copied" shows only when the clipboard took the text; otherwise the button keeps its label.
 * <p>Choice: a new answer, and a new test in the same window, put the button back to its label and stop the timer.
 * <p>Choice: the verdict is in the window and the report's own "Verdict:" line is in the report; the window adds
 * nothing to the text that is copied.
 */
final class TroubleshootDialog
{
	/** The window's title. */
	static final String TITLE = "2h Why Lag " + Version.CURRENT + " - Troubleshoot";
	/** The verdict's words until the report is in. */
	static final String TESTING = "Testing...";
	static final String COPY_TEXT = "Copy report";
	/** The copy button's words for {@value #COPIED_MS} ms after it copied. */
	static final String COPIED_TEXT = "Copied";
	static final String CLOSE_TEXT = "Close";
	static final int COPIED_MS = 2000;
	static final int WIDTH = 560;
	static final int HEIGHT = 440;
	private static final int PADDING = 10;

	/** Everything the window shows, built without a window. */
	final JPanel content = new JPanel(new BorderLayout());
	final JTextArea verdictArea = new JTextArea(TESTING);
	final JTextArea reportArea = new JTextArea();
	final JButton copyButton;
	final JButton closeButton;
	/** The "Copied" timer: one shot, {@value #COPIED_MS} ms; tests read its delay and fire it. */
	final Timer copiedTimer;
	private final Predicate<String> clipboard;
	/** The report on show, which the copy button copies; empty until {@link #show}. Swing thread. */
	private String report = "";
	/** The hosting dialog; null without a window and again once closed. Swing thread. */
	private JDialog dialog;
	private boolean closed;

	/**
	 * @param clipboard where <i>Copy report</i> puts the text; answers true when the clipboard took it
	 */
	TroubleshootDialog(Predicate<String> clipboard)
	{
		this.clipboard = clipboard;
		content.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		content.setBorder(new EmptyBorder(PADDING, PADDING, PADDING, PADDING));

		verdictArea.setEditable(false);
		verdictArea.setLineWrap(true);
		verdictArea.setWrapStyleWord(true);
		verdictArea.setOpaque(false);
		verdictArea.setFont(new Font(Font.DIALOG, Font.BOLD, 14));
		verdictArea.setForeground(Color.WHITE);
		verdictArea.setBorder(new EmptyBorder(0, 0, PADDING, 0));
		// A read-only text box that can take the focus shows its caret; this one is a label that wraps, not a box.
		verdictArea.setFocusable(false);
		content.add(verdictArea, BorderLayout.NORTH);

		reportArea.setEditable(false);
		reportArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 11));
		reportArea.setBackground(ColorScheme.DARK_GRAY_COLOR);
		reportArea.setForeground(Color.WHITE);
		reportArea.setCaretColor(Color.WHITE);
		final JScrollPane scroll = new JScrollPane(reportArea);
		scroll.setBorder(null);
		content.add(scroll, BorderLayout.CENTER);

		copyButton = button(COPY_TEXT);
		copyButton.addActionListener(e -> copy());
		copyButton.setEnabled(false);
		closeButton = button(CLOSE_TEXT);
		closeButton.addActionListener(e -> dispose());
		final JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
		buttons.setOpaque(false);
		buttons.setBorder(new EmptyBorder(PADDING, 0, 0, 0));
		buttons.add(copyButton);
		buttons.add(closeButton);
		content.add(buttons, BorderLayout.SOUTH);

		copiedTimer = new Timer(COPIED_MS, e -> copyButton.setText(COPY_TEXT));
		copiedTimer.setRepeats(false);
	}

	private static JButton button(String text)
	{
		final JButton b = new JButton(text);
		b.setFocusPainted(false);
		b.setFont(FontManager.getRunescapeSmallFont());
		b.setMargin(new Insets(1, 5, 1, 5));
		return b;
	}

	/**
	 * Swing thread. Opens the window on "Testing..." and answers it, so the caller can {@link #show} the report later.
	 *
	 * @param owner     the RuneLite frame (the panel's window ancestor), or null
	 * @param clipboard where <i>Copy report</i> puts the text
	 */
	static TroubleshootDialog open(Window owner, Predicate<String> clipboard)
	{
		final TroubleshootDialog window = window(owner, clipboard);
		window.dialog.setVisible(true);
		// Enter presses whichever control holds the focus, and a focused text box shows a caret: Close while the
		// window says "Testing..." (Copy report is disabled then).
		window.closeButton.requestFocusInWindow();
		return window;
	}

	/**
	 * The window built and sized but not yet shown: a non-modal {@link JDialog} owned by {@code owner}, titled
	 * {@link #TITLE}, closing by disposing, hosting {@link #content}. Needs a display.
	 */
	static TroubleshootDialog window(Window owner, Predicate<String> clipboard)
	{
		final TroubleshootDialog window = new TroubleshootDialog(clipboard);
		final JDialog host = new JDialog(owner, TITLE);
		host.setModalityType(Dialog.ModalityType.MODELESS);
		host.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
		host.setContentPane(window.content);
		host.setSize(WIDTH, HEIGHT);
		host.setLocationRelativeTo(owner);
		host.addWindowListener(new WindowAdapter()
		{
			@Override
			public void windowClosed(WindowEvent e)
			{
				window.closed();
			}
		});
		window.dialog = host;
		return window;
	}

	/**
	 * Swing thread. The report is in: its verdict replaces "Testing...", its text fills the box, scrolled to the top,
	 * and <i>Copy report</i> is ready when there is a text.
	 */
	void show(Report answer)
	{
		final Report r = answer == null ? new Report("", "") : answer;
		verdictArea.setText(r.verdict);
		reportArea.setText(r.text);
		reportArea.setCaretPosition(0);
		report = r.text;
		resetCopy(!report.isEmpty());
		if (!report.isEmpty())
		{
			// The answer is in: Copy report takes the focus, so Enter copies and no text box shows a caret. Answers
			// false without a window, which is a test.
			copyButton.requestFocusInWindow();
		}
	}

	/** Swing thread. Another test starts in this same window: "Testing..." and an empty box, as when it opened. */
	void testing()
	{
		verdictArea.setText(TESTING);
		reportArea.setText("");
		report = "";
		resetCopy(false);
	}

	/** True until the window is closed, by {@link #dispose}, by Close or by its own close box. */
	boolean isOpen()
	{
		return !closed;
	}

	/** Swing thread. Brings a standing window to the front; nothing without one. */
	void toFront()
	{
		final JDialog host = dialog;
		if (host != null && !closed)
		{
			host.toFront();
		}
	}

	/** Swing thread. Closes the window and stops the timer; harmless when it is already closed or never had one. */
	void dispose()
	{
		final JDialog host = dialog;
		dialog = null;
		closed();
		if (host != null)
		{
			host.dispose();
		}
	}

	/** The report on show: "" until {@link #show} and again after {@link #testing}. */
	String report()
	{
		return report;
	}

	private void closed()
	{
		closed = true;
		copiedTimer.stop();
	}

	/** The copy button back to its label, enabled or not, with the timer stopped. */
	private void resetCopy(boolean enabled)
	{
		copiedTimer.stop();
		copyButton.setText(COPY_TEXT);
		copyButton.setEnabled(enabled);
	}

	/** The copy button's press: the report to the clipboard, and "Copied" for {@value #COPIED_MS} ms if it took it. */
	private void copy()
	{
		if (clipboard.test(report))
		{
			copyButton.setText(COPIED_TEXT);
			copiedTimer.restart();
		}
	}
}
