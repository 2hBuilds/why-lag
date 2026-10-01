package com.whylag;

import com.whylag.core.Answer;
import com.whylag.core.BadgeStyle;
import com.whylag.core.BadgeView;
import com.whylag.core.Cause;
import com.whylag.core.Level;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.runelite.client.ui.FontManager;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * Pictures for the checker's eye (contract P2.3, section 8): every state of picture 22, part A - smooth, world lag,
 * ping lag, low FPS, client froze (slow, not lag: a triangle), not sure, measuring - and the
 * dimmed world lag, in each of the four styles, each on a dark green ground ({@code badge-<style>-<state>}), and ONE
 * sheet of them all ({@code badge-sheet}). The pictures are checked in memory to exist and not to be blank; the
 * local-only probe's {@code PicturesTest} writes them to {@code build/whylag/<name>.png}.
 *
 * <p>Choice: the ground is 38,58,30 with 6 px round each badge; the sheet: styles as columns, states as rows.
 * <p>Choice: a file is badge-{style}-{state}.png, both in lower case with dashes: badge-icon-and-words-world-lag.png.
 * <p>Choice: the client-froze row is WARN with "Client froze", as picture 22's row 5 ("slow, not lag: triangle").
 */
public class BadgeRenderTest
{
	private static final Color GRASS = new Color(38, 58, 30);
	private static final int MARGIN = 6;
	private static final String[] STYLE_NAMES = {"icon", "icon-and-words", "shape-and-words", "shape-only"};

	/** One row of picture 22, part A (and the dimmed lag of part D). */
	private static final class State
	{
		final String name;
		final Level level;
		final Answer answer;
		final boolean dimmed;
		final String tip1;
		final String tip2;

		State(String name, Level level, Answer answer, boolean dimmed, String tip1, String tip2)
		{
			this.name = name;
			this.level = level;
			this.answer = answer;
			this.dimmed = dimmed;
			this.tip1 = tip1;
			this.tip2 = tip2;
		}

		BadgeView view(BadgeStyle style)
		{
			return new BadgeView(true, style, level, answer.icon, dimmed, answer.line1, answer.line2, tip1, tip2);
		}
	}

	private static List<State> states()
	{
		final List<State> out = new ArrayList<>();
		out.add(new State("smooth", Level.OK, Answer.SMOOTH, false, "Smooth. No lag for 4 min.", ""));
		final Answer world = Answer.of(Cause.SLOW_WORLD);
		out.add(new State("world-lag", Level.BAD, world, false, world.oneLine, "Ticks 1,240 ms, ping 41 ms"));
		final Answer ping = Answer.of(Cause.PING_JUMPY);
		out.add(new State("ping-lag", Level.BAD, ping, false, ping.oneLine, "Ping 310 ms, ticks 1,240 ms"));
		final Answer fps = Answer.of(Cause.SLOW_DRAWING);
		out.add(new State("low-fps", Level.BAD, fps, false, fps.oneLine, "Worst frame 480 ms, 50 fps"));
		final Answer froze = Answer.of(Cause.CLIENT_BUSY);
		out.add(new State("client-froze", Level.WARN, froze, false, froze.oneLine, "Worst frame 340 ms, 50 fps"));
		final Answer lag = Answer.of(Cause.NOT_SURE);
		out.add(new State("not-sure", Level.BAD, lag, false, lag.oneLine, "Ticks 1,240 ms, worst frame 170 ms"));
		out.add(new State("measuring", Level.NO_DATA, Answer.MEASURING, false, "Still measuring", "Ready in 40 s."));
		out.add(new State("world-lag-dimmed", Level.BAD, world, true, world.oneLine, "Ticks 1,240 ms, ping 41 ms"));
		return out;
	}

	/**
	 * The pictures of this test, by file name without the extension: every state in every style on the grass,
	 * {@code badge-<style>-<state>}, and the sheet of them all, {@code badge-sheet}. The probe's {@code PicturesTest}
	 * writes them to {@code build/whylag} for the lead's eye; this test checks them in memory.
	 */
	public static Map<String, BufferedImage> pictures()
	{
		final BadgePainter painter = new BadgePainter(new BadgeIcons());
		final List<State> states = states();
		final BadgeStyle[] styles = BadgeStyle.values();
		final BufferedImage[][] badges = new BufferedImage[states.size()][styles.length];
		final Map<String, BufferedImage> out = new LinkedHashMap<>();
		for (int r = 0; r < states.size(); r++)
		{
			for (int c = 0; c < styles.length; c++)
			{
				final BufferedImage badge = painter.paint(states.get(r).view(styles[c]));
				assertNotNull(badge);
				badges[r][c] = badge;
				final BufferedImage onGrass = new BufferedImage(badge.getWidth() + 2 * MARGIN,
					badge.getHeight() + 2 * MARGIN, BufferedImage.TYPE_INT_ARGB);
				final Graphics2D g = onGrass.createGraphics();
				g.setColor(GRASS);
				g.fillRect(0, 0, onGrass.getWidth(), onGrass.getHeight());
				g.drawImage(badge, MARGIN, MARGIN, null);
				g.dispose();
				out.put("badge-" + STYLE_NAMES[c] + "-" + states.get(r).name, onGrass);
			}
		}
		out.put("badge-sheet", sheet(states, styles, badges));
		return out;
	}

	@Test
	public void paintsEveryStateInEveryStyle()
	{
		final Map<String, BufferedImage> pictures = pictures();
		final BadgePainter painter = new BadgePainter(new BadgeIcons());
		final List<State> states = states();
		final BadgeStyle[] styles = BadgeStyle.values();

		assertEquals("8 states x 4 styles, and the sheet", 33, pictures.size());
		for (Map.Entry<String, BufferedImage> e : pictures.entrySet())
		{
			final String name = e.getKey();
			final BufferedImage picture = e.getValue();
			assertTrue(name.startsWith("badge-"));
			assertNotNull(name, picture);
			assertTrue(name + " is not blank", distinctColours(picture) >= 3);
			assertEquals(name + ": the grass in the corner", GRASS.getRGB(), picture.getRGB(0, 0));
		}
		for (int r = 0; r < states.size(); r++)
		{
			for (int c = 0; c < styles.length; c++)
			{
				final String name = "badge-" + STYLE_NAMES[c] + "-" + states.get(r).name;
				final BufferedImage badge = painter.paint(states.get(r).view(styles[c]));
				final BufferedImage picture = pictures.get(name);
				assertNotNull(name, picture);
				assertEquals(name + ": the badge and the grass round it", badge.getWidth() + 2 * MARGIN,
					picture.getWidth());
				assertEquals(badge.getHeight() + 2 * MARGIN, picture.getHeight());
				assertTrue(name + ": the badge's box is on the grass", picture.getRGB(MARGIN, MARGIN) != GRASS.getRGB());
			}
		}
	}

	/** The styles as columns, the states as rows, every badge on the grass with its state's name beside it. */
	private static BufferedImage sheet(List<State> states, BadgeStyle[] styles, BufferedImage[][] badges)
	{
		final int labelWidth = 120;
		final int headerHeight = 22;
		final int[] columnWidth = new int[styles.length];
		final int[] rowHeight = new int[states.size()];
		final Graphics2D scratch = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB).createGraphics();
		final java.awt.FontMetrics small = scratch.getFontMetrics(FontManager.getRunescapeSmallFont());
		for (int c = 0; c < styles.length; c++)
		{
			columnWidth[c] = small.stringWidth(styles[c].toString()) + 2 * MARGIN;
		}
		scratch.dispose();
		for (int r = 0; r < states.size(); r++)
		{
			for (int c = 0; c < styles.length; c++)
			{
				columnWidth[c] = Math.max(columnWidth[c], badges[r][c].getWidth() + 2 * MARGIN);
				rowHeight[r] = Math.max(rowHeight[r], badges[r][c].getHeight() + 2 * MARGIN);
			}
		}
		int width = labelWidth;
		for (int w : columnWidth)
		{
			width += w;
		}
		int height = headerHeight;
		for (int h : rowHeight)
		{
			height += h;
		}
		final BufferedImage out = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
		final Graphics2D g = out.createGraphics();
		g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_OFF);
		g.setColor(GRASS);
		g.fillRect(0, 0, width, height);
		g.setFont(FontManager.getRunescapeSmallFont());
		int x = labelWidth;
		for (int c = 0; c < styles.length; c++)
		{
			label(g, styles[c].toString(), x + MARGIN, 16);
			x += columnWidth[c];
		}
		int y = headerHeight;
		for (int r = 0; r < states.size(); r++)
		{
			label(g, states.get(r).name, MARGIN, y + rowHeight[r] / 2 + 5);
			x = labelWidth;
			for (int c = 0; c < styles.length; c++)
			{
				g.drawImage(badges[r][c], x + MARGIN, y + MARGIN, null);
				x += columnWidth[c];
			}
			y += rowHeight[r];
		}
		g.dispose();
		return out;
	}

	private static void label(Graphics2D g, String text, int x, int y)
	{
		g.setColor(Color.BLACK);
		g.drawString(text, x + 1, y + 1);
		g.setColor(Color.WHITE);
		g.drawString(text, x, y);
	}

	private static int distinctColours(BufferedImage img)
	{
		final Set<Integer> seen = new HashSet<>();
		for (int y = 0; y < img.getHeight(); y++)
		{
			for (int x = 0; x < img.getWidth(); x++)
			{
				seen.add(img.getRGB(x, y));
			}
		}
		return seen.size();
	}
}
