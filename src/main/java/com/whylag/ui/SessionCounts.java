package com.whylag.ui;

import com.whylag.core.Group;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import javax.swing.JComponent;
import javax.swing.ToolTipManager;

/**
 * Block 8, the session's counts by group (contract 5): RuneScape Small {@link Ui#TEXT}, baseline 12, 11 px between
 * items: "Conn 1", "Frame 1", "World 1", and "? 1" only while the UNSURE count is above 0 (its tooltip
 * "Can't tell"). A count over 99 prints "99+". When the items are wider than the block (213 px at the least) on one line,
 * the items that do not fit go to a second 16 px line and the block is 32 high. An item is never split and never
 * clipped. The lines are made for the block's own width ({@link Ui#widthOf}) and made again when that changes.
 *
 * <p>Choice: an item starts a new line only when it does not fit after the one before it.
 * <p>Choice: a missing count reads 0.
 */
final class SessionCounts extends JComponent
{
	static final int LINE_H = 16;
	static final int BASELINE = 12;
	static final int GAP = 11;
	static final int CAP = 99;
	static final String UNSURE_TIP = "Can't tell";
	private static final Group[] ALWAYS = {Group.CONNECTION, Group.FRAME_RATE, Group.WORLD};

	/** Each item: its words, its line (0 or 1) and its x, made for {@link #placedWidth}. */
	private final List<String> words = new ArrayList<>();
	private final List<int[]> places = new ArrayList<>();
	private int unsureItem = -1;
	private int lines = 1;
	/** The width the places were made for; 0 = not yet. */
	private int placedWidth;

	SessionCounts()
	{
		setOpaque(true);
		ToolTipManager.sharedInstance().registerComponent(this);
		set(null);
	}

	/** The session's counts by {@link Group} ordinal. Answers true when the block's height changed. */
	boolean set(int[] counts)
	{
		words.clear();
		unsureItem = -1;
		for (Group group : ALWAYS)
		{
			words.add(item(group, count(counts, group)));
		}
		if (count(counts, Group.UNSURE) > 0)
		{
			unsureItem = words.size();
			words.add(item(Group.UNSURE, count(counts, Group.UNSURE)));
		}
		final int old = lines;
		place();
		return lines != old;
	}

	/** Puts the items on their lines for the block's own width. */
	private void place()
	{
		final int room = Ui.widthOf(this);
		places.clear();
		int line = 0;
		int x = 0;
		for (String w : words)
		{
			final int width = Ui.width(Ui.RSS, w);
			if (x > 0 && x + GAP + width > room)
			{
				line++;
				x = 0;
			}
			final int at = x == 0 ? 0 : x + GAP;
			places.add(new int[] {line, at});
			x = at + width;
		}
		lines = line + 1;
		placedWidth = room;
	}

	/** Puts the items on their lines again when the block's width is not the one they were placed for. */
	private void replace()
	{
		if (Ui.widthOf(this) != placedWidth)
		{
			place();
		}
	}

	/** "Conn 1", "? 99+": the group's short word, a space and the count, over 99 as "99+". */
	static String item(Group group, int n)
	{
		return group.shortLabel() + " " + (n > CAP ? CAP + "+" : Integer.toString(Math.max(0, n)));
	}

	private static int count(int[] counts, Group g)
	{
		return counts == null || g.ordinal() >= counts.length ? 0 : counts[g.ordinal()];
	}

	/** The items as painted, in order. */
	List<String> items()
	{
		return Collections.unmodifiableList(words);
	}

	/** 1 or 2. */
	int lines()
	{
		replace();
		return lines;
	}

	@Override
	public String getToolTipText(MouseEvent e)
	{
		if (unsureItem < 0)
		{
			return null;
		}
		replace();
		final int[] p = places.get(unsureItem);
		final int w = Ui.width(Ui.RSS, words.get(unsureItem));
		final boolean inside = e.getX() >= p[1] && e.getX() < p[1] + w
			&& e.getY() >= p[0] * LINE_H && e.getY() < (p[0] + 1) * LINE_H;
		return inside ? UNSURE_TIP : null;
	}

	@Override
	public Dimension getPreferredSize()
	{
		replace();
		return new Dimension(Ui.WIDTH, LINE_H * lines);
	}

	@Override
	protected void paintComponent(Graphics graphics)
	{
		replace();
		final Graphics2D g = Ui.prepare(graphics);
		g.setColor(Ui.GROUND);
		g.fillRect(0, 0, Ui.widthOf(this), LINE_H * lines);
		for (int i = 0; i < words.size(); i++)
		{
			final int[] p = places.get(i);
			Ui.text(g, words.get(i), p[1], p[0] * LINE_H + BASELINE, Ui.RSS, Ui.TEXT);
		}
	}
}
