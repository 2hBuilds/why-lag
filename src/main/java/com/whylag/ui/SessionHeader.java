package com.whylag.ui;

import com.whylag.core.Fmt;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import javax.swing.JComponent;

/**
 * Block 7 (contract 5): 213 x 16 (as wide as the panel gives it). "This session" in RuneScape Small {@link Ui#LABEL} on the left; the session's
 * closed events, {@link Fmt#lags} ("4 lags"), in RuneScape Small white on the right; baseline 12. No line under it.
 *
 * <p>Choice: the count is right-aligned at the block's right edge (x 213 at 213 wide); before the first snapshot it reads "0 lags".
 */
final class SessionHeader extends JComponent
{
	static final int HEIGHT = 16;
	static final int BASELINE = 12;
	static final String WORDS = "This session";

	private String total = Fmt.lags(0);

	SessionHeader()
	{
		setOpaque(true);
	}

	/** The session's closed events, dropped ones included. */
	void set(int sessionTotal)
	{
		total = Fmt.lags(Math.max(0, sessionTotal));
	}

	String total()
	{
		return total;
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
		Ui.text(g, WORDS, 0, BASELINE, Ui.RSS, Ui.LABEL);
		Ui.text(g, total, width - Ui.width(Ui.RSS, total), BASELINE, Ui.RSS, Ui.WHITE);
	}
}
