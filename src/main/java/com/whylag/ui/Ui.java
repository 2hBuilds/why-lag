package com.whylag.ui;

import com.whylag.core.Level;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.PluginPanel;

/**
 * The panel's colours, fonts and text helpers (contract 5): one place, so every block of picture 18 paints from the
 * same table. Stateless; the painters call it on the Swing thread.
 *
 * <p><b>Fonts.</b> The three RuneScape faces at their native 16 px, and ONE derived font, {@link #RSB32}: the bold
 * face at its exact double, for the two big lines of the answer card and nothing else. A pixel font is sharp only
 * at whole multiples, so no other size is ever made; {@code AnswerCardTest} scans this package and finds the one
 * derivation, here. Text anti-aliasing is off everywhere ({@link #prepare}).
 *
 * <p><b>Colours.</b> The table of contract 5. The three level colours are {@code ColorScheme}'s progress colours;
 * red TEXT is never the stock red, which measures under 4.5:1 on the card, but {@link #BAD_TEXT}.
 *
 * <p>Choice: text is measured on a 1 x 1 image with the painters' own settings, so a measured width is painted.
 */
final class Ui
{
	/**
	 * The narrowest a block may be, and the width it assumes while it has no size yet: {@code PANEL_WIDTH} (225) less
	 * the 6 px border on each side. A block paints to {@link #widthOf} - its own width - and never to this constant.
	 */
	static final int WIDTH = PluginPanel.PANEL_WIDTH - 2 * PluginPanel.BORDER_OFFSET;

	/** The panel; the divider inside the card; the ground between blocks. #282828. */
	static final Color GROUND = ColorScheme.DARK_GRAY_COLOR;
	/** The card, the cells, the lanes, the rows, the fold rows, the buttons. #1E1E1E. */
	static final Color CARD = ColorScheme.DARKER_GRAY_COLOR;
	/** A selected row; an OPENED fold row. #3C3C3C. */
	static final Color CARD_SELECTED = ColorScheme.DARKER_GRAY_HOVER_COLOR;
	/** Chip borders, the axis, the card's edge at NO_DATA. #4D4D4D. */
	static final Color RULE = ColorScheme.MEDIUM_GRAY_COLOR;
	/** Button borders. #171717. */
	static final Color BORDER = ColorScheme.BORDER_COLOR;
	/** Body text. #C6C6C6. */
	static final Color TEXT = ColorScheme.TEXT_COLOR;
	/** Labels, small lines, chevrons. #A5A5A5. */
	static final Color LABEL = ColorScheme.LIGHT_GRAY_COLOR;
	/** Values, line 2 of the answer, selected text. */
	static final Color WHITE = new Color(255, 255, 255);
	/** The chosen chip, "Fix", the main button, the selection. #DC8A00. */
	static final Color ORANGE = ColorScheme.BRAND_ORANGE;
	/** OK: shape, edge, line AND text. 55,240,70. */
	static final Color OK = ColorScheme.PROGRESS_COMPLETE_COLOR;
	/** WARN: shape, edge, line AND text. 230,150,30. */
	static final Color WARN = ColorScheme.PROGRESS_INPROGRESS_COLOR;
	/** BAD: shape, edge, line. NEVER text. 230,30,30. */
	static final Color BAD = ColorScheme.PROGRESS_ERROR_COLOR;
	/** Every red TEXT: the big line, a culprit's value, a strip value. 255,90,90. */
	static final Color BAD_TEXT = new Color(255, 90, 90);
	/** The ground of the culprit cell. #3A2424. */
	static final Color CULPRIT = new Color(58, 36, 36);
	/** The name and the unit in the culprit cell. #D6C0C0. */
	static final Color CULPRIT_LABEL = new Color(214, 192, 192);
	/** An event band. */
	static final Color BAND = new Color(230, 150, 30, 33);
	/** The band in the culprit lane. */
	static final Color BAND_CULPRIT = new Color(230, 30, 30, 102);
	/** The band of the selected event. */
	static final Color BAND_SELECTED = new Color(220, 138, 0, 77);
	/** The 1 px shadow under a lane's value. */
	static final Color SHADOW = new Color(0, 0, 0);

	/** RuneScape, 16 px. */
	static final Font RS = FontManager.getRunescapeFont();
	/** RuneScape Bold, 16 px. */
	static final Font RSB = FontManager.getRunescapeBoldFont();
	/** RuneScape Small, 16 px. */
	static final Font RSS = FontManager.getRunescapeSmallFont();
	/** RuneScape Bold at 32 px, its exact double: the answer card's two big lines, and nothing else. */
	static final Font RSB32 = FontManager.getRunescapeBoldFont().deriveFont(32f);

	/** What a line that does not fit ends with: three ASCII dots (the RuneScape faces have no U+2026). */
	static final String DOTS = "...";

	/** The image text is measured on; see the class notes. */
	private static final Graphics2D MEASURE = measureGraphics();

	private Ui()
	{
	}

	/**
	 * The width a block paints to and hit-tests against: its own width, held at {@link #WIDTH} at the least, and
	 * {@link #WIDTH} while it has no size yet.
	 */
	static int widthOf(java.awt.Component c)
	{
		return c.getWidth() > 0 ? Math.max(WIDTH, c.getWidth()) : WIDTH;
	}

	/** The graphics as a {@link Graphics2D} with text anti-aliasing, shape anti-aliasing and fractions off. */
	static Graphics2D prepare(Graphics g)
	{
		final Graphics2D g2 = (Graphics2D) g;
		g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_OFF);
		g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
		g2.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_OFF);
		return g2;
	}

	/** The metrics of a font as the blocks paint it. */
	static FontMetrics metrics(Font font)
	{
		synchronized (MEASURE)
		{
			return MEASURE.getFontMetrics(font);
		}
	}

	/** The painted width of {@code text} in {@code font}; 0 for null or "". */
	static int width(Font font, String text)
	{
		return text == null || text.isEmpty() ? 0 : metrics(font).stringWidth(text);
	}

	/** Draws {@code text} with its baseline at {@code baseline}, left edge at {@code x}. */
	static void text(Graphics2D g, String text, int x, int baseline, Font font, Color colour)
	{
		if (text == null || text.isEmpty())
		{
			return;
		}
		g.setFont(font);
		g.setColor(colour);
		g.drawString(text, x, baseline);
	}

	/** {@link #text}, with a 1 px black shadow one pixel right and down. */
	static void shadowed(Graphics2D g, String text, int x, int baseline, Font font, Color colour)
	{
		text(g, text, x + 1, baseline + 1, font, SHADOW);
		text(g, text, x, baseline, font, colour);
	}

	/**
	 * {@code text} as it fits {@code room} px of {@code font}: whole when it fits, else cut at a character and ended
	 * with {@link #DOTS}. Never clipped mid-glyph.
	 */
	static String fit(Font font, String text, int room)
	{
		final String full = text == null ? "" : text;
		if (width(font, full) <= room)
		{
			return full;
		}
		for (int end = full.length() - 1; end > 0; end--)
		{
			final String cut = full.substring(0, end).trim() + DOTS;
			if (width(font, cut) <= room)
			{
				return cut;
			}
		}
		return width(font, DOTS) <= room ? DOTS : "";
	}

	/**
	 * {@code text} wrapped on spaces into lines of {@code font}: the first line has {@code firstRoom} px, every
	 * other {@code room} px. At most {@code maxLines}: what does not fit on the last one is cut with
	 * {@link #DOTS} ({@link #fit}). A word wider than its line is cut the same way. "" gives no line.
	 */
	static List<String> wrap(Font font, String text, int firstRoom, int room, int maxLines)
	{
		if (text == null || text.trim().isEmpty() || maxLines <= 0)
		{
			return Collections.emptyList();
		}
		final List<String> all = new ArrayList<>();
		final StringBuilder line = new StringBuilder();
		for (String word : text.trim().split(" +"))
		{
			final int lineRoom = all.isEmpty() ? firstRoom : room;
			if (line.length() == 0)
			{
				line.append(word);
				continue;
			}
			final String longer = line + " " + word;
			if (width(font, longer) <= lineRoom)
			{
				line.append(' ').append(word);
			}
			else
			{
				all.add(fit(font, line.toString(), lineRoom));
				line.setLength(0);
				line.append(word);
			}
		}
		all.add(fit(font, line.toString(), all.isEmpty() ? firstRoom : room));
		if (all.size() <= maxLines)
		{
			return all;
		}
		// Too many lines: the last one allowed takes the rest, cut with the dots.
		final List<String> out = new ArrayList<>(all.subList(0, maxLines - 1));
		final String rest = String.join(" ", all.subList(maxLines - 1, all.size()));
		out.add(fit(font, rest, maxLines == 1 ? firstRoom : room));
		return out;
	}

	/** The colour of a level's TEXT: OK green, WARN amber, BAD {@link #BAD_TEXT}, NO_DATA {@link #LABEL}. */
	static Color textColour(Level level)
	{
		if (level == null)
		{
			return LABEL;
		}
		switch (level)
		{
			case OK:
				return OK;
			case WARN:
				return WARN;
			case BAD:
				return BAD_TEXT;
			default:
				return LABEL;
		}
	}

	/**
	 * The colour of a level's MARK - a shape, an edge, a line: OK green, WARN amber, BAD the stock red, and
	 * NO_DATA {@link #LABEL}, the hollow ring's grey.
	 */
	static Color markColour(Level level)
	{
		if (level == null)
		{
			return LABEL;
		}
		switch (level)
		{
			case OK:
				return OK;
			case WARN:
				return WARN;
			case BAD:
				return BAD;
			default:
				return LABEL;
		}
	}

	/** The colour of a coloured EDGE or border: the level's mark, and {@link #RULE} at NO_DATA. */
	static Color edgeColour(Level level)
	{
		return level == null || level == Level.NO_DATA ? RULE : markColour(level);
	}

	private static Graphics2D measureGraphics()
	{
		final Graphics2D g = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB).createGraphics();
		return prepare(g);
	}
}
