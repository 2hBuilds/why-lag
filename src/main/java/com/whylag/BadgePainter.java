package com.whylag;

import com.whylag.core.BadgeStyle;
import com.whylag.core.BadgeView;
import com.whylag.core.Level;
import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import net.runelite.client.ui.FontManager;

/**
 * The picture of one game badge (contract P2.3, P2.4): ONE {@code TYPE_INT_ARGB} image that holds the whole badge,
 * box included, made once per view; the overlay keeps it and draws it every frame (T18). Pure Java2D: the one
 * RuneLite call is {@link FontManager#getRunescapeFont()}, the badge's face at its native 16 px.
 *
 * <p><b>The box</b>, in RuneLite's overlay colours and painted by our own code (RuneLite's
 * {@code BackgroundComponent} makes two colours on every call): the ground 70,61,50 alpha 156 over the whole
 * rectangle, a 1 px outer frame 56,48,40 alpha 218, and a 1 px inner frame 84,73,60 alpha 218 one pixel inside it.
 *
 * <p><b>The picture</b> is a 24 x 24 icon of {@link BadgeIcons} (the style shows icons, the view has one and its
 * level is WARN or BAD; the status shape is already in the file's corner), else the 14 x 14 status shape of the
 * level: a 12 x 12 shape with a 1 px black edge, pixel for pixel the rule of picture 22's page ({@code inside}).
 *
 * <p><b>The words</b> (styles that show words): RuneScape regular at 16 px, anti-aliasing off, each line drawn
 * twice, black one pixel down and right, then in its colour; line 1 in the level's colour (red TEXT is always
 * 255,90,90, never the shape's 230,30,30), line 2 white. A line is 17 px high with its baseline 13 px under its
 * top; a line that is "" is not drawn and takes no room.
 *
 * <p><b>Sizes</b> ({@code pw}, {@code ph} the picture's, {@code n} the lines drawn, {@code tw} the widest line):
 * ICON and SHAPE_ONLY are {@code pw + 8} by {@code ph + 8} with the picture at (4, 4); the two word styles are
 * {@code 5 + pw + 5 + tw + 7} by {@code 8 + max(ph, 17 n)}, the picture at x 5 centred in the height (but a 14 px
 * shape beside two lines at y 5, beside line 1), the block of lines at x {@code 5 + pw + 5} centred in the height.
 * The widest badge is 117 x 42 with an icon and 107 x 42 with a shape ("Disconnected", 76 px).
 *
 * <p><b>Dimmed</b>: the picture and the words at half strength ({@code AlphaComposite.SrcOver.derive(0.5f)}); the
 * box is not.
 *
 * <p><b>Bare</b> ({@link #bare}, addendum D): the picture of the two styles without words, WITHOUT the box, for the
 * RuneLite infobox ({@code BadgeInfoBox}), which draws its own background and centres a smaller picture in it.
 *
 * <p>Choice: the frames are drawn OVER the ground (SrcOver), as BackgroundComponent does: RuneLite's own box.
 * <p>Choice: the ground is WRITTEN (Src), exactly 70,61,50 alpha 156; SrcOver on a clear image rounds a step off.
 * <p>Choice: a half pixel of centring rounds down: the 14 px shape beside ONE line sits at y 5, as beside two.
 * <p>Choice: the four shapes are made once, in the constructor; text is measured in the hints it is drawn with.
 * <p>Choice: a picture that BadgeIcons does not have (a file that did not load) is drawn as the status shape.
 * <p>Choice: a null style, level or icon reads as ICON, NO_DATA, NONE; no line to draw keeps the formula at 0.
 */
public final class BadgePainter
{
	/** The box's ground (RuneLite's {@code ComponentConstants.STANDARD_BACKGROUND_COLOR}). */
	private static final Color GROUND = new Color(70, 61, 50, 156);
	/** The outer frame: the ground's colour times 0.8, its alpha times 1.4, as {@code BackgroundComponent}. */
	private static final Color FRAME_OUTER = new Color(56, 48, 40, 218);
	/** The inner frame, one pixel inside the outer: the ground's colour times 1.2, its alpha times 1.4. */
	private static final Color FRAME_INNER = new Color(84, 73, 60, 218);
	/** The edge of every status shape, and the shadow of every line. */
	private static final Color BLACK = new Color(0, 0, 0);
	/** Line 2, whose side it is. */
	private static final Color LINE_TWO = new Color(255, 255, 255);

	/** The status shape's colour, by {@link Level} ordinal: OK, WARN, BAD, NO_DATA. */
	private static final Color[] SHAPE = {
		new Color(55, 240, 70), new Color(230, 150, 30), new Color(230, 30, 30), new Color(150, 150, 150)};
	/** Line 1's colour, and the tooltip's first line, by {@link Level} ordinal. Red text is never stock red. */
	private static final Color[] LINE_ONE = {
		new Color(55, 240, 70), new Color(240, 160, 40), new Color(255, 90, 90), new Color(170, 170, 170)};

	/** The status shape: 12 x 12 and a 1 px edge all round. */
	private static final int SHAPE_PX = 14;
	/** The shape's own side, without its edge. */
	private static final int SHAPE_INSIDE = 12;
	/** Where the picture sits in ICON and SHAPE_ONLY; the box is the picture and twice this. */
	private static final int PICTURE_ONLY_PAD = 4;
	/** The word styles: the picture's x, the gap before the words, the room after them, top and bottom together. */
	private static final int WORDS_LEFT = 5, WORDS_GAP = 5, WORDS_RIGHT = 7, WORDS_HEIGHT_PAD = 8;
	/** The bare picture's status shape is the 14 px shape doubled: each pixel becomes this many by this many. */
	private static final int BARE_SHAPE_SCALE = 2;
	/** The y of a 14 px shape beside two lines: beside line 1. */
	private static final int SHAPE_BESIDE_TWO_Y = 5;
	/** A line's height, and its baseline under its top. */
	private static final int LINE_PX = 17, BASELINE = 13;

	private final BadgeIcons icons;
	private final Font font;
	private final FontMetrics metrics;
	/** The four status shapes, by {@link Level} ordinal. */
	private final BufferedImage[] shapes = new BufferedImage[Level.values().length];

	public BadgePainter(BadgeIcons icons)
	{
		this.icons = icons;
		this.font = FontManager.getRunescapeFont();
		final BufferedImage scratch = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
		final Graphics2D g = scratch.createGraphics();
		try
		{
			pixelHints(g);
			this.metrics = g.getFontMetrics(font);
		}
		finally
		{
			g.dispose();
		}
		for (Level level : Level.values())
		{
			shapes[level.ordinal()] = shape(level);
		}
	}

	/** The whole badge, box included, as a new image; null when the view is not visible. */
	public BufferedImage paint(BadgeView v)
	{
		if (v == null || !v.visible)
		{
			return null;
		}
		final BadgeStyle style = v.style == null ? BadgeStyle.ICON : v.style;
		final Level level = v.level == null ? Level.NO_DATA : v.level;

		BufferedImage picture = null;
		if (style.icon() && (level == Level.WARN || level == Level.BAD))
		{
			picture = icons.get(v.icon, level);
		}
		final boolean shape = picture == null;
		if (shape)
		{
			picture = shapes[level.ordinal()];
		}
		final int pw = picture.getWidth();
		final int ph = picture.getHeight();

		final String[] lines = new String[2];
		final Color[] colours = new Color[2];
		int n = 0;
		int tw = 0;
		if (style.words())
		{
			if (!v.line1.isEmpty())
			{
				lines[n] = v.line1;
				colours[n++] = lineOneColour(level);
			}
			if (!v.line2.isEmpty())
			{
				lines[n] = v.line2;
				colours[n++] = LINE_TWO;
			}
			for (int i = 0; i < n; i++)
			{
				tw = Math.max(tw, metrics.stringWidth(lines[i]));
			}
		}

		final int width;
		final int height;
		final int px;
		final int py;
		if (style.words())
		{
			width = WORDS_LEFT + pw + WORDS_GAP + tw + WORDS_RIGHT;
			height = WORDS_HEIGHT_PAD + Math.max(ph, LINE_PX * n);
			px = WORDS_LEFT;
			py = shape && n == 2 ? SHAPE_BESIDE_TWO_Y : (height - ph) / 2;
		}
		else
		{
			width = pw + 2 * PICTURE_ONLY_PAD;
			height = ph + 2 * PICTURE_ONLY_PAD;
			px = PICTURE_ONLY_PAD;
			py = PICTURE_ONLY_PAD;
		}

		final BufferedImage out = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
		final Graphics2D g = out.createGraphics();
		try
		{
			pixelHints(g);
			g.setComposite(AlphaComposite.Src);
			g.setColor(GROUND);
			g.fillRect(0, 0, width, height);
			g.setComposite(AlphaComposite.SrcOver);
			g.setColor(FRAME_OUTER);
			g.drawRect(0, 0, width - 1, height - 1);
			g.setColor(FRAME_INNER);
			g.drawRect(1, 1, width - 3, height - 3);

			if (v.dimmed)
			{
				g.setComposite(AlphaComposite.SrcOver.derive(0.5f));
			}
			g.drawImage(picture, px, py, null);

			if (n > 0)
			{
				g.setFont(font);
				final int x = px + pw + WORDS_GAP;
				final int top = (height - LINE_PX * n) / 2;
				for (int i = 0; i < n; i++)
				{
					final int baseline = top + LINE_PX * i + BASELINE;
					g.setColor(BLACK);
					g.drawString(lines[i], x + 1, baseline + 1);
					g.setColor(colours[i]);
					g.drawString(lines[i], x, baseline);
				}
			}
		}
		finally
		{
			g.dispose();
		}
		return out;
	}

	/**
	 * The badge's picture WITHOUT the box, for the infobox (addendum D): the 24 x 24 icon file as it is when the style
	 * shows icons, the view has one and its level is WARN or BAD; else the status shape doubled to 28 x 28 by
	 * nearest neighbour (each pixel of the 14 px shape becomes 2 x 2), so it holds its own in a 35 px infobox.
	 * Dimmed: every pixel at half strength, as {@link #paint} does the picture. A new image on every call; null when
	 * the view is null or not visible. The words of a word style are not part of it.
	 *
	 * <p>Choice: the pictures are copied pixel by pixel ({@code getRGB} / {@code setRGB}), so no blend can move one.
	 * <p>Choice: a null style, level or icon reads as ICON, NO_DATA, NONE, as {@link #paint} reads them.
	 */
	public BufferedImage bare(BadgeView v)
	{
		if (v == null || !v.visible)
		{
			return null;
		}
		final BadgeStyle style = v.style == null ? BadgeStyle.ICON : v.style;
		final Level level = v.level == null ? Level.NO_DATA : v.level;

		BufferedImage picture = null;
		if (style.icon() && (level == Level.WARN || level == Level.BAD))
		{
			picture = icons.get(v.icon, level);
		}
		final int scale = picture == null ? BARE_SHAPE_SCALE : 1;
		if (picture == null)
		{
			picture = shapes[level.ordinal()];
		}
		final int w = picture.getWidth() * scale;
		final int h = picture.getHeight() * scale;
		final BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
		for (int y = 0; y < h; y++)
		{
			for (int x = 0; x < w; x++)
			{
				out.setRGB(x, y, picture.getRGB(x / scale, y / scale));
			}
		}
		if (!v.dimmed)
		{
			return out;
		}
		final BufferedImage half = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
		final Graphics2D g = half.createGraphics();
		try
		{
			g.setComposite(AlphaComposite.SrcOver.derive(0.5f));
			g.drawImage(out, 0, 0, null);
		}
		finally
		{
			g.dispose();
		}
		return half;
	}

	/**
	 * Line 1's colour for a level, which the tooltip's first line takes too: OK 55,240,70; WARN 240,160,40; BAD
	 * 255,90,90; NO_DATA 170,170,170 (a null is NO_DATA's).
	 */
	static Color lineOneColour(Level level)
	{
		return LINE_ONE[(level == null ? Level.NO_DATA : level).ordinal()];
	}

	/** The status shape's colour for a level: OK 55,240,70; WARN 230,150,30; BAD 230,30,30; NO_DATA 150,150,150. */
	static Color shapeColour(Level level)
	{
		return SHAPE[(level == null ? Level.NO_DATA : level).ordinal()];
	}

	/**
	 * The rule of picture 22's page: for x, y in 0 .. 11, with r2 = (x + 0.5 - 6)^2 + (y + 0.5 - 6)^2, a pixel is
	 * inside when OK: r2 &lt;= 36; NO_DATA: r2 &lt;= 36 and r2 &gt; 9 (a ring); WARN: |x + 0.5 - 6| &lt;= (y + 1) / 2
	 * (a triangle, point up); BAD: always (a square). Outside 0 .. 11 nothing is inside.
	 */
	private static boolean inside(Level level, int x, int y)
	{
		if (x < 0 || y < 0 || x >= SHAPE_INSIDE || y >= SHAPE_INSIDE)
		{
			return false;
		}
		final double dx = x + 0.5 - 6;
		final double dy = y + 0.5 - 6;
		final double r2 = dx * dx + dy * dy;
		switch (level)
		{
			case OK:
				return r2 <= 36;
			case NO_DATA:
				return r2 <= 36 && r2 > 9;
			case WARN:
				return Math.abs(dx) <= (y + 1) / 2.0;
			default:
				return true;
		}
	}

	/**
	 * One status shape, 14 x 14: an inside pixel at (x + 1, y + 1) in the level's colour; a pixel that is not inside
	 * but has an inside pixel left, right, above or under it, black; every other pixel clear.
	 */
	private static BufferedImage shape(Level level)
	{
		final BufferedImage img = new BufferedImage(SHAPE_PX, SHAPE_PX, BufferedImage.TYPE_INT_ARGB);
		final int colour = shapeColour(level).getRGB();
		final int black = BLACK.getRGB();
		for (int y = -1; y <= SHAPE_INSIDE; y++)
		{
			for (int x = -1; x <= SHAPE_INSIDE; x++)
			{
				if (inside(level, x, y))
				{
					img.setRGB(x + 1, y + 1, colour);
				}
				else if (inside(level, x - 1, y) || inside(level, x + 1, y) || inside(level, x, y - 1)
					|| inside(level, x, y + 1))
				{
					img.setRGB(x + 1, y + 1, black);
				}
			}
		}
		return img;
	}

	/** Pixel fonts and pixel shapes: no anti-aliasing of either, no fractional metrics. */
	private static void pixelHints(Graphics2D g)
	{
		g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
		g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_OFF);
		g.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_OFF);
	}
}
