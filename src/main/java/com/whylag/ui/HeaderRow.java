package com.whylag.ui;

import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import javax.swing.JComponent;

/**
 * Block 1 of the panel (contract 5): 213 x 20 (as wide as the panel gives it). "Why Lag" in RuneScape Bold white on the left, baseline 15; on the
 * right, in RuneScape Small {@link Ui#LABEL}, "World 416", or "Not logged in" while the snapshot's world is 0.
 *
 * <p>Choice: the world words are right-aligned at the block's right edge (x 213 at 213 wide) on the same baseline, 15.
 * <p>Choice: before the first snapshot the right side is empty.
 */
final class HeaderRow extends JComponent
{
	static final int HEIGHT = 20;
	private static final int BASELINE = 15;
	private static final String TITLE = "Why Lag";

	private String world = "";

	HeaderRow()
	{
		setOpaque(true);
	}

	/** The snapshot's world: 0 is not logged in (contract 3.8). */
	void set(int snapshotWorld)
	{
		world = snapshotWorld == 0 ? "Not logged in" : "World " + snapshotWorld;
	}

	/** The words on the right, "" before the first snapshot. */
	String worldWords()
	{
		return world;
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
		Ui.text(g, TITLE, 0, BASELINE, Ui.RSB, Ui.WHITE);
		Ui.text(g, world, width - Ui.width(Ui.RSS, world), BASELINE, Ui.RSS, Ui.LABEL);
	}
}
