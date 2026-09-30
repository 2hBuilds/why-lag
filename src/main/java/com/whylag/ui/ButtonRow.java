package com.whylag.ui;

import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import javax.swing.JComponent;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.ToolTipManager;

/**
 * Block 9, the button (contract 5): "Copy report" alone, 213 x 29, filling the row's whole width, a
 * {@link Ui#CARD} ground with a 1 px {@link Ui#BORDER} and its words in RuneScape, centred, baseline 19, in
 * {@link Ui#TEXT}: a press asks the panel to copy the report; after a copy it reads "Copied" for 2 s (one
 * non-repeating Swing timer, stopped when the panel is hidden). Disabled, tooltip "Nothing to copy yet", until the
 * first snapshot.
 *
 * <p>Choice: "alpha 120" dims the words only (their colour at alpha 120); the ground and the border stay whole.
 * <p>Choice: a disabled "Copy report" draws its words at alpha 120.
 * <p>Choice: stopping the timer early puts the words back to "Copy report" at once.
 * <p>Choice: the button reacts to the left mouse button's press; a hand cursor shows over it while it is enabled.
 */
final class ButtonRow extends JComponent
{
	static final int HEIGHT = 29;
	static final int BASELINE = 19;
	static final int DISABLED_ALPHA = 120;
	static final int COPIED_MS = 2000;
	static final String COPY = "Copy report";
	static final String COPIED = "Copied";
	static final String NOTHING_YET = "Nothing to copy yet";
	/** The words of a disabled button: their colour at alpha 120. */
	static final Color TEXT_DISABLED = dim(Ui.TEXT);

	private final Timer copiedTimer;
	private boolean copyEnabled;
	private boolean copied;

	/** A row whose "Copy report" runs {@code onCopy} when pressed while enabled. */
	ButtonRow(Runnable onCopy)
	{
		setOpaque(true);
		ToolTipManager.sharedInstance().registerComponent(this);
		copiedTimer = new Timer(COPIED_MS, e -> showCopied(false));
		copiedTimer.setRepeats(false);
		addMouseListener(new MouseAdapter()
		{
			@Override
			public void mousePressed(MouseEvent e)
			{
				if (SwingUtilities.isLeftMouseButton(e) && copyEnabled
					&& onCopyButton(e.getX(), e.getY(), Ui.widthOf(ButtonRow.this)))
				{
					onCopy.run();
				}
			}
		});
		addMouseMotionListener(new MouseAdapter()
		{
			@Override
			public void mouseMoved(MouseEvent e)
			{
				final boolean hand = copyEnabled && onCopyButton(e.getX(), e.getY(), Ui.widthOf(ButtonRow.this));
				setCursor(Cursor.getPredefinedCursor(hand ? Cursor.HAND_CURSOR : Cursor.DEFAULT_CURSOR));
			}
		});
	}

	/** "Copy report" is enabled from the first snapshot on. */
	void setCopyEnabled(boolean enabled)
	{
		copyEnabled = enabled;
	}

	boolean copyEnabled()
	{
		return copyEnabled;
	}

	/** A copy was made: the words read "Copied" for 2 s, the timer restarted. */
	void copiedNow()
	{
		showCopied(true);
		copiedTimer.restart();
	}

	/** The panel was hidden: the timer stops and the words go back to "Copy report". */
	void stopTimer()
	{
		copiedTimer.stop();
		showCopied(false);
	}

	/** The button's words now. */
	String copyWords()
	{
		return copied ? COPIED : COPY;
	}

	/** The "Copied" timer (tests read its delay and fire it). */
	Timer copiedTimer()
	{
		return copiedTimer;
	}

	private void showCopied(boolean on)
	{
		if (copied != on)
		{
			copied = on;
			repaint();
		}
	}

	/** True when a point is on the button: it spans the row's whole width, {@code width} px. */
	static boolean onCopyButton(int x, int y, int width)
	{
		return x >= 0 && x < width && y >= 0 && y < HEIGHT;
	}

	@Override
	public String getToolTipText(MouseEvent e)
	{
		if (onCopyButton(e.getX(), e.getY(), Ui.widthOf(this)) && !copyEnabled)
		{
			return NOTHING_YET;
		}
		return null;
	}

	@Override
	public Dimension getPreferredSize()
	{
		return new Dimension(Ui.WIDTH, HEIGHT);
	}

	@Override
	protected void paintComponent(Graphics graphics)
	{
		final Graphics2D g = Ui.prepare(graphics);
		final int width = Ui.widthOf(this);
		g.setColor(Ui.GROUND);
		g.fillRect(0, 0, width, HEIGHT);
		g.setColor(Ui.CARD);
		g.fillRect(0, 0, width, HEIGHT);
		g.setColor(Ui.BORDER);
		g.drawRect(0, 0, width - 1, HEIGHT - 1);
		final String words = copyWords();
		Ui.text(g, words, (width - Ui.width(Ui.RS, words)) / 2, BASELINE, Ui.RS,
			copyEnabled ? Ui.TEXT : TEXT_DISABLED);
	}

	private static Color dim(Color c)
	{
		return new Color(c.getRed(), c.getGreen(), c.getBlue(), DISABLED_ALPHA);
	}
}
