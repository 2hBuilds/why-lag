package com.whylag.ui;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.awt.image.BufferedImage;
import java.util.function.LongSupplier;
import javax.swing.JComponent;
import javax.swing.SwingUtilities;
import javax.swing.ToolTipManager;

/**
 * Block 1 of the panel (contract 5): 213 x 20 (as wide as the panel gives it). "Why Lag" in RuneScape Bold white on
 * the left, baseline 15; at the right end the settings gear (1.0.1, lot C), 12 x 12, drawn in code; and left of the
 * gear, in RuneScape Small {@link Ui#LABEL}, "World 416", or "Not logged in" while the snapshot's world is 0.
 *
 * <p><b>The gear</b> is grey at rest and orange under the mouse, as 2h Bank Portfolio Tracker's is, with the tooltip
 * {@value #GEAR_TIP}. A left press on it runs the hook the panel gave, which opens the settings menu under it.
 *
 * <p><b>A press that closes the menu opens nothing</b> (the bank tracker's rule). The gear cannot see its own menu:
 * RuneLite makes every popup heavy-weight, and Swing's popup grabber cancels an open popup on any press OUTSIDE it -
 * the gear is outside - BEFORE that press reaches the gear's own mouse listener. A second press on the gear
 * therefore always finds the menu already closed, and would open it again in a loop. So the panel tells the row
 * WHEN the menu went away ({@link #menuClosed}), and a press within {@value #MENU_REOPEN_MS} ms of that is the close
 * it really was: it opens nothing.
 *
 * <p>Choice: the world words are right-aligned 6 px left of the gear on the same baseline, 15.
 * <p>Choice: before the first snapshot the world words are empty.
 * <p>Choice: the gear's hit area is its 12 px, 4 px more on its left and the whole 20 px of the row's height, so a
 * small glyph is not a small target; the tooltip and the hand cursor follow the same area.
 * <p>Choice: 300 ms is a click and not a pause: longer than the gap between the grabber's cancel and the press it
 * belongs to, shorter than any deliberate re-open.
 * <p>Choice: the clock is a field so a test can move it; the client reads the wall clock.
 */
final class HeaderRow extends JComponent
{
	static final int HEIGHT = 20;
	/** The gear's side, in px. */
	static final int GEAR = 12;
	/** The gear's top edge: it is centred on the row. */
	static final int GEAR_Y = (HEIGHT - GEAR) / 2;
	/** The gear's tooltip. */
	static final String GEAR_TIP = "Settings";
	/** How long after the menu closes a press on the gear opens nothing. */
	static final long MENU_REOPEN_MS = 300L;
	private static final int BASELINE = 15;
	private static final int WORLD_GAP = 6;
	private static final int HIT_LEFT = 4;
	private static final int TEETH = 8;
	private static final String TITLE = "Why Lag";
	/** What {@link #menuClosedAt} holds while the menu has never closed. */
	private static final long NEVER = Long.MIN_VALUE;
	/** The gear at rest and under the mouse, drawn once. */
	private static final BufferedImage GEAR_REST = gear(Ui.LABEL);
	private static final BufferedImage GEAR_HOT = gear(Ui.ORANGE);

	/** What "now" is when a press asks how long ago the menu closed; a seam: tests move it. */
	LongSupplier clock = System::currentTimeMillis;

	private final Runnable openMenu;
	private String world = "";
	private long menuClosedAt = NEVER;
	private boolean hot;

	/** A row whose gear runs {@code openMenu} when a press on it opens the menu. */
	HeaderRow(Runnable openMenu)
	{
		this.openMenu = openMenu;
		setOpaque(true);
		ToolTipManager.sharedInstance().registerComponent(this);
		addMouseListener(new MouseAdapter()
		{
			@Override
			public void mousePressed(MouseEvent e)
			{
				if (SwingUtilities.isLeftMouseButton(e) && onGear(e.getX(), e.getY(), Ui.widthOf(HeaderRow.this))
					&& gearPressOpens())
				{
					HeaderRow.this.openMenu.run();
				}
			}

			@Override
			public void mouseExited(MouseEvent e)
			{
				over(false);
			}
		});
		addMouseMotionListener(new MouseAdapter()
		{
			@Override
			public void mouseMoved(MouseEvent e)
			{
				over(onGear(e.getX(), e.getY(), Ui.widthOf(HeaderRow.this)));
			}
		});
	}

	/** The snapshot's world: 0 is not logged in (contract 3.8). */
	void set(int snapshotWorld)
	{
		world = snapshotWorld == 0 ? "Not logged in" : "World " + snapshotWorld;
	}

	/** The words left of the gear, "" before the first snapshot. */
	String worldWords()
	{
		return world;
	}

	/** The gear's box in the row's own coordinates: 12 x 12 at the right edge. */
	Rectangle gearBounds()
	{
		return new Rectangle(Ui.widthOf(this) - GEAR, GEAR_Y, GEAR, GEAR);
	}

	/** The menu went away, by whatever means (the gear, a press elsewhere, Escape, a choice): the time is kept. */
	void menuClosed()
	{
		menuClosedAt = clock.getAsLong();
	}

	/**
	 * Whether a press on the gear now opens the menu: true unless the menu went away within the last
	 * {@value #MENU_REOPEN_MS} ms, in which case this press is the one that took it away.
	 */
	boolean gearPressOpens()
	{
		return menuClosedAt == NEVER || clock.getAsLong() - menuClosedAt >= MENU_REOPEN_MS;
	}

	/** True while the gear is drawn in its hot colour. */
	boolean hot()
	{
		return hot;
	}

	/** True when a point is on the gear's hit area: {@link #HIT_LEFT} px left of it to the row's edge, the row's height. */
	static boolean onGear(int x, int y, int width)
	{
		return x >= width - GEAR - HIT_LEFT && x < width && y >= 0 && y < HEIGHT;
	}

	private void over(boolean on)
	{
		if (hot != on)
		{
			hot = on;
			setCursor(Cursor.getPredefinedCursor(on ? Cursor.HAND_CURSOR : Cursor.DEFAULT_CURSOR));
			repaint();
		}
	}

	@Override
	public String getToolTipText(MouseEvent e)
	{
		return onGear(e.getX(), e.getY(), Ui.widthOf(this)) ? GEAR_TIP : null;
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
		final int gearX = width - GEAR;
		Ui.text(g, world, gearX - WORLD_GAP - Ui.width(Ui.RSS, world), BASELINE, Ui.RSS, Ui.LABEL);
		g.drawImage(hot ? GEAR_HOT : GEAR_REST, gearX, GEAR_Y, null);
	}

	/**
	 * The gear as 2h Bank Portfolio Tracker draws it: a ring of stroke, not a filled disc, so the middle stays clear
	 * and the glyph reads as a gear at 12 px, and eight round-capped teeth every 45 degrees, rooted on the ring's own
	 * circle. Every length is a fraction of the side. Drawn and not loaded: this plugin ships no image files.
	 */
	private static BufferedImage gear(Color colour)
	{
		final BufferedImage img = new BufferedImage(GEAR, GEAR, BufferedImage.TYPE_INT_ARGB);
		final Graphics2D g = img.createGraphics();
		try
		{
			g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
			g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
			g.setColor(colour);
			final double centre = GEAR / 2.0;
			final double ring = centre * 0.52;
			final float ringWidth = (float) Math.max(1.5, GEAR * 0.2);
			final float toothWidth = (float) Math.max(1.3, GEAR * 0.16);
			final double toothOut = centre - toothWidth / 2.0 - 0.25;
			g.setStroke(new BasicStroke(ringWidth, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
			g.draw(new Ellipse2D.Double(centre - ring, centre - ring, ring * 2.0, ring * 2.0));
			g.setStroke(new BasicStroke(toothWidth, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
			for (int i = 0; i < TEETH; i++)
			{
				final double angle = i * 2.0 * Math.PI / TEETH;
				final double cos = Math.cos(angle);
				final double sin = Math.sin(angle);
				g.draw(new Line2D.Double(centre + cos * ring, centre + sin * ring, centre + cos * toothOut,
					centre + sin * toothOut));
			}
		}
		finally
		{
			g.dispose();
		}
		return img;
	}
}
