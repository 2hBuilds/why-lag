package com.whylag;

import com.whylag.core.Answer;
import com.whylag.core.BadgeStyle;
import com.whylag.core.BadgeView;
import com.whylag.core.Cause;
import com.whylag.core.Icon;
import com.whylag.core.Level;
import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import net.runelite.client.ui.FontManager;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * The picture of the game badge (contract P2.3, P2.4), by pixel and with the real RuneScape font: the sizes of the
 * four styles and the widest badge, every line of 3.6's table inside the box with 7 px to its right edge, the box's
 * ground and two frames, the four status shapes against the rule of picture 22's page, the colours of the two lines,
 * where the picture and the lines sit, what dimming touches, and when there is no picture at all.
 *
 * <p>The expected pixels are worked out here from the contract's words - the shape rule is written again, the text's
 * ink is found by drawing each line alone - not read back from the painter. Where a colour is laid over the
 * translucent ground, the test asks Java2D itself for the result on a small image that holds the ground exactly
 * ({@link #over}), and checks that result against the SrcOver formula within one step.
 */
public class BadgePainterTest
{
	private static final BadgeIcons ICONS = new BadgeIcons();
	private static final BadgePainter PAINTER = new BadgePainter(ICONS);
	private static final FontMetrics METRICS = metrics();

	/** The box (P2.4): the ground, the outer frame, the inner frame. */
	private static final int GROUND = argb(156, 70, 61, 50);
	private static final int OUTER = argb(218, 56, 48, 40);
	private static final int INNER = argb(218, 84, 73, 60);
	/** The status shapes' colours (P2.4), by Level ordinal: OK, WARN, BAD, NO_DATA. */
	private static final int[] SHAPE = {argb(255, 55, 240, 70), argb(255, 230, 150, 30), argb(255, 230, 30, 30),
		argb(255, 150, 150, 150)};
	/** Line 1's colours (P2.4), by Level ordinal. */
	private static final int[] LINE_ONE = {argb(255, 55, 240, 70), argb(255, 240, 160, 40), argb(255, 255, 90, 90),
		argb(255, 170, 170, 170)};
	private static final int WHITE = argb(255, 255, 255, 255);
	private static final int BLACK = argb(255, 0, 0, 0);
	private static final int SEA = argb(255, 0x2A, 0x64, 0xC8);

	private static final BadgeStyle[] WORD_STYLES = {BadgeStyle.ICON_AND_WORDS, BadgeStyle.SHAPE_AND_WORDS};

	// ---------------------------------------------------------------- sizes

	@Test
	public void sizesByStyle()
	{
		final Answer world = Answer.of(Cause.SLOW_WORLD);
		assertSize("ICON, smooth: the circle", 22, 22, paint(BadgeStyle.ICON, Level.OK, Answer.SMOOTH, false));
		assertSize("ICON, measuring: the ring", 22, 22, paint(BadgeStyle.ICON, Level.NO_DATA, Answer.MEASURING, false));
		assertSize("ICON, a lag: the icon", 32, 32, paint(BadgeStyle.ICON, Level.BAD, world, false));
		assertSize("ICON, slow: the icon", 32, 32, paint(BadgeStyle.ICON, Level.WARN, Answer.of(Cause.FRAME_CAP),
			false));
		assertSize("ICON, dimmed: the icon", 32, 32, paint(BadgeStyle.ICON, Level.BAD, world, true));
		assertSize("an icon only at WARN or BAD", 22, 22, paint(BadgeStyle.ICON, Level.OK, world, false));

		for (Answer a : table())
		{
			for (Level level : Level.values())
			{
				assertSize("SHAPE_ONLY is 22 x 22 in every state: " + a.oneLine + " " + level, 22, 22,
					paint(BadgeStyle.SHAPE_ONLY, level, a, false));
				assertSize("dimmed too", 22, 22, paint(BadgeStyle.SHAPE_ONLY, level, a, true));
			}
		}

		for (BadgeStyle style : WORD_STYLES)
		{
			for (Answer a : table())
			{
				for (Level level : Level.values())
				{
					final int pw = pictureSide(style, level, a.icon);
					final int n = a.line2.isEmpty() ? 1 : 2;
					final int tw = Math.max(width(a.line1), width(a.line2));
					assertSize(style + " " + a.oneLine + " " + level + ": 5 + pw + 5 + tw + 7 by 8 + max(ph, 17 n)",
						5 + pw + 5 + tw + 7, 8 + Math.max(pw, 17 * n), paint(style, level, a, false));
				}
			}
		}
		assertEquals("two lines", 42, paint(BadgeStyle.ICON_AND_WORDS, Level.BAD, world, false).getHeight());
		assertEquals("two lines", 42, paint(BadgeStyle.SHAPE_AND_WORDS, Level.BAD, world, false).getHeight());
		assertEquals("one line", 25, paint(BadgeStyle.ICON_AND_WORDS, Level.OK, Answer.SMOOTH, false).getHeight());
		assertEquals("one line", 25, paint(BadgeStyle.SHAPE_AND_WORDS, Level.OK, Answer.SMOOTH, false).getHeight());
		assertEquals("one line", 25, paint(BadgeStyle.SHAPE_AND_WORDS, Level.NO_DATA, Answer.WAITING, false)
			.getHeight());
		assertEquals("an empty line takes no room", 25, PAINTER.paint(new BadgeView(true, BadgeStyle.SHAPE_AND_WORDS,
			Level.BAD, Icon.WORLD, false, "", "Not you", "x", "")).getHeight());
	}

	/** The widest line of 3.6's table is "Disconnected", 76 px: the widest badges are 117 x 42 and 107 x 42. */
	@Test
	public void theWidestBadge()
	{
		assertEquals("\"Disconnected\" at 16 px in RuneScape regular", 76, width("Disconnected"));
		for (Answer a : table())
		{
			assertTrue(a.line1 + " is no wider than Disconnected", width(a.line1) <= 76);
			assertTrue(a.line2 + " is no wider than Disconnected", width(a.line2) <= 76);
		}
		int iconWords = 0;
		int shapeWords = 0;
		for (Answer a : table())
		{
			for (Level level : Level.values())
			{
				iconWords = Math.max(iconWords, paint(BadgeStyle.ICON_AND_WORDS, level, a, false).getWidth());
				shapeWords = Math.max(shapeWords, paint(BadgeStyle.SHAPE_AND_WORDS, level, a, false).getWidth());
			}
		}
		assertEquals(117, iconWords);
		assertEquals(107, shapeWords);
		final Answer disconnected = Answer.of(Cause.DISCONNECT);
		assertSize("Icon and words", 117, 42, paint(BadgeStyle.ICON_AND_WORDS, Level.BAD, disconnected, false));
		assertSize("Shape and words", 107, 42, paint(BadgeStyle.SHAPE_AND_WORDS, Level.BAD, disconnected, false));
	}

	/**
	 * Every line of 3.6's table, in both word styles and every level, is inside the box with 7 px to its right edge.
	 * Each line's ink lies exactly where the contract puts the line (found by drawing the line alone), so the line
	 * starts at x 5 + pw + 5 and the widest one ends, by its advance, exactly 7 px from the edge; and no glyph or
	 * shadow pixel lies in the last six columns before the frames.
	 */
	@Test
	public void everyLineFits()
	{
		for (BadgeStyle style : WORD_STYLES)
		{
			for (Answer a : table())
			{
				for (Level level : Level.values())
				{
					final BufferedImage img = paint(style, level, a, false);
					final String what = style + " " + a.oneLine + " " + level;
					final int w = img.getWidth();
					final int h = img.getHeight();
					final int textX = 5 + pictureSide(style, level, a.icon) + 5;
					final int n = a.line2.isEmpty() ? 1 : 2;
					final int top = (h - 17 * n) / 2;
					assertLines(img, textX, top, level, a);
					for (String line : new String[] {a.line1, a.line2})
					{
						if (!line.isEmpty())
						{
							assertTrue(what + ": " + line + " has 7 px to the right edge",
								w - (textX + width(line)) >= 7);
						}
					}
					assertEquals(what + ": the widest line ends 7 px from the edge", 7,
						w - (textX + Math.max(width(a.line1), width(a.line2))));
					for (int y = 2; y <= h - 3; y++)
					{
						for (int x = w - 6; x <= w - 3; x++)
						{
							assertEquals(what + ": no ink at (" + x + ", " + y + ")", hex(GROUND),
								hex(img.getRGB(x, y)));
						}
					}
				}
			}
		}
	}

	// ---------------------------------------------------------------- the box

	@Test
	public void theBoxHasItsTwoFrames()
	{
		final int outer = over(GROUND, OUTER, 1f, false);
		final int inner = over(GROUND, INNER, 1f, false);
		assertClose("the outer frame over the ground", srcOver(GROUND, OUTER), outer);
		assertClose("the inner frame over the ground", srcOver(GROUND, INNER), inner);

		final Answer world = Answer.of(Cause.SLOW_WORLD);
		final BufferedImage[] badges = {
			paint(BadgeStyle.ICON, Level.OK, Answer.SMOOTH, false),
			paint(BadgeStyle.ICON, Level.BAD, world, false),
			paint(BadgeStyle.ICON_AND_WORDS, Level.BAD, Answer.of(Cause.DISCONNECT), false),
			paint(BadgeStyle.SHAPE_AND_WORDS, Level.OK, Answer.SMOOTH, false),
			paint(BadgeStyle.SHAPE_ONLY, Level.NO_DATA, Answer.MEASURING, true)};
		for (BufferedImage img : badges)
		{
			final int w = img.getWidth();
			final int h = img.getHeight();
			final String what = w + " x " + h;
			for (int y = 0; y < h; y++)
			{
				for (int x = 0; x < w; x++)
				{
					if (x == 0 || y == 0 || x == w - 1 || y == h - 1)
					{
						assertEquals(what + ": outer frame at (" + x + ", " + y + ")", hex(outer),
							hex(img.getRGB(x, y)));
					}
					else if (x == 1 || y == 1 || x == w - 2 || y == h - 2)
					{
						assertEquals(what + ": inner frame at (" + x + ", " + y + ")", hex(inner),
							hex(img.getRGB(x, y)));
					}
				}
			}
			for (int x = 2; x <= w - 3; x++)
			{
				assertEquals(what + ": the ground under the top frames", hex(GROUND), hex(img.getRGB(x, 2)));
				assertEquals(what + ": the ground under the top frames", hex(GROUND), hex(img.getRGB(x, 3)));
			}
			for (int y = 2; y <= h - 3; y++)
			{
				assertEquals(what + ": the ground inside the left frames", hex(GROUND), hex(img.getRGB(2, y)));
				assertEquals(what + ": the ground inside the left frames", hex(GROUND), hex(img.getRGB(3, y)));
				assertEquals(what + ": the ground inside the right frames", hex(GROUND), hex(img.getRGB(w - 3, y)));
			}
		}
	}

	// ---------------------------------------------------------------- the shapes

	@Test
	public void theShapeFollowsThePixelRule()
	{
		for (Level level : Level.values())
		{
			final BufferedImage img = paint(BadgeStyle.SHAPE_ONLY, level, Answer.of(Cause.SLOW_WORLD), false);
			assertPicture(level + " at (4, 4)", img, expectedShape(level), 4, 4);
		}
		// spot checks of the page's rule, pixel by pixel, in the 14 x 14 picture (the 12 x 12 shape at 1, 1)
		final BufferedImage ok = expectedShape(Level.OK);
		assertEquals("the circle's widest row reaches the left", hex(SHAPE[0]), hex(ok.getRGB(1, 6)));
		assertEquals("its black edge beyond", hex(BLACK), hex(ok.getRGB(0, 6)));
		assertEquals("a corner of the circle's square is clear", 0, ok.getRGB(1, 1));
		final BufferedImage warn = expectedShape(Level.WARN);
		assertEquals("the triangle's point is two pixels wide", hex(SHAPE[1]), hex(warn.getRGB(6, 1)));
		assertEquals(hex(SHAPE[1]), hex(warn.getRGB(7, 1)));
		assertEquals("black beside the point", hex(BLACK), hex(warn.getRGB(5, 1)));
		assertEquals("black above the point", hex(BLACK), hex(warn.getRGB(6, 0)));
		assertEquals("the base spans the width", hex(SHAPE[1]), hex(warn.getRGB(1, 12)));
		final BufferedImage bad = expectedShape(Level.BAD);
		assertEquals("the square fills its 12 x 12", hex(SHAPE[2]), hex(bad.getRGB(1, 1)));
		assertEquals(hex(SHAPE[2]), hex(bad.getRGB(12, 12)));
		assertEquals("its edge is black", hex(BLACK), hex(bad.getRGB(0, 5)));
		assertEquals("left, right, above or under only: the edge's corners are clear", 0, bad.getRGB(0, 0));
		final BufferedImage ring = expectedShape(Level.NO_DATA);
		assertEquals("the ring is grey", hex(SHAPE[3]), hex(ring.getRGB(6, 3)));
		assertEquals("the hole has a black edge", hex(BLACK), hex(ring.getRGB(6, 4)));
		assertEquals("the middle of the hole is clear", 0, ring.getRGB(6, 6));

		for (Level level : Level.values())
		{
			assertEquals("the painter's colour for " + level, hex(SHAPE[level.ordinal()]),
				hex(BadgePainter.shapeColour(level).getRGB()));
		}
	}

	// ---------------------------------------------------------------- the words

	@Test
	public void lineOneTakesTheLevelsColour()
	{
		final Answer world = Answer.of(Cause.SLOW_WORLD);
		for (BadgeStyle style : WORD_STYLES)
		{
			for (Level level : Level.values())
			{
				final BufferedImage img = paint(style, level, world, false);
				final int textX = 5 + pictureSide(style, level, world.icon) + 5;
				final int h = img.getHeight();
				final String what = style + " " + level;
				final Rectangle one = box(img, LINE_ONE[level.ordinal()], textX, 0, h);
				final Rectangle two = box(img, WHITE, textX, 0, h);
				assertNotNull(what + ": line 1 in " + hex(LINE_ONE[level.ordinal()]), one);
				assertNotNull(what + ": line 2 in white", two);
				assertTrue(what + ": line 1 above line 2", one.y + one.height <= two.y);
				assertNotNull(what + ": the shadow", box(img, BLACK, textX, 0, h));
				for (Level other : Level.values())
				{
					if (other != level)
					{
						assertNull(what + ": no other level's text colour", box(img, LINE_ONE[other.ordinal()],
							textX, 0, h));
					}
				}
				assertNull(what + ": red text is never the shape's 230,30,30", box(img, SHAPE[2], textX, 0, h));
				assertNull(what + ": amber text is never the shape's 230,150,30", box(img, SHAPE[1], textX, 0, h));
			}
		}
		assertEquals(hex(argb(255, 255, 90, 90)), hex(BadgePainter.lineOneColour(Level.BAD).getRGB()));
		assertEquals(hex(argb(255, 240, 160, 40)), hex(BadgePainter.lineOneColour(Level.WARN).getRGB()));
		assertEquals(hex(argb(255, 55, 240, 70)), hex(BadgePainter.lineOneColour(Level.OK).getRGB()));
		assertEquals(hex(argb(255, 170, 170, 170)), hex(BadgePainter.lineOneColour(Level.NO_DATA).getRGB()));
	}

	/**
	 * The picture at x 5, centred in the height, but a 14 px shape beside two lines at y 5; the picture-only styles
	 * at (4, 4); the lines at x 5 + pw + 5, 17 px apart, the block centred in the height, each baseline 13 px under
	 * its line's top. A line's ink is found by drawing it alone, so the positions are the contract's, not the
	 * painter's.
	 */
	@Test
	public void wherePictureAndLinesSit()
	{
		final Answer world = Answer.of(Cause.SLOW_WORLD);
		final BufferedImage globe = ICONS.get(Icon.WORLD, Level.BAD);

		assertPicture("ICON: the icon at (4, 4)", paint(BadgeStyle.ICON, Level.BAD, world, false), globe, 4, 4);
		assertPicture("ICON: the shape at (4, 4)", paint(BadgeStyle.ICON, Level.OK, Answer.SMOOTH, false),
			expectedShape(Level.OK), 4, 4);
		assertPicture("SHAPE_ONLY: the shape at (4, 4)", paint(BadgeStyle.SHAPE_ONLY, Level.BAD, world, false),
			expectedShape(Level.BAD), 4, 4);

		// 42 high: the icon centred, (42 - 24) / 2 = 9; the shape beside two lines at y 5; the lines' tops 4 and 21
		final BufferedImage iconWords = paint(BadgeStyle.ICON_AND_WORDS, Level.BAD, world, false);
		assertPicture("the icon beside two lines", iconWords, globe, 5, 9);
		assertLines(iconWords, 5 + 24 + 5, 4, Level.BAD, world);
		final BufferedImage shapeWords = paint(BadgeStyle.SHAPE_AND_WORDS, Level.BAD, world, false);
		assertPicture("the shape beside two lines", shapeWords, expectedShape(Level.BAD), 5, 5);
		assertLines(shapeWords, 5 + 14 + 5, 4, Level.BAD, world);

		// 25 high: the shape centred, (25 - 14) / 2 = 5.5, rounded down; the line's top (25 - 17) / 2 = 4
		final BufferedImage smooth = paint(BadgeStyle.SHAPE_AND_WORDS, Level.OK, Answer.SMOOTH, false);
		assertPicture("the shape beside one line", smooth, expectedShape(Level.OK), 5, 5);
		assertLines(smooth, 5 + 14 + 5, 4, Level.OK, Answer.SMOOTH);

		// an icon beside ONE line (a view the model never builds): 32 high, the icon at y 4, the line's top 7
		final BadgeView oneLine = new BadgeView(true, BadgeStyle.ICON_AND_WORDS, Level.BAD, Icon.WORLD, false,
			"World lag", "", "World lag", "");
		final BufferedImage iconOne = PAINTER.paint(oneLine);
		assertSize("an icon beside one line", 5 + 24 + 5 + width("World lag") + 7, 32, iconOne);
		assertPicture("the icon beside one line", iconOne, globe, 5, 4);
		final Rectangle ink = box(iconOne, LINE_ONE[Level.BAD.ordinal()], 34, 0, 32);
		final Rectangle alone = inkOf("World lag");
		alone.translate(34, 7 + 13);
		assertEquals("the line's top at (32 - 17) / 2, rounded down", alone, ink);
	}

	// ---------------------------------------------------------------- dimmed, hidden, which picture

	@Test
	public void dimmedHalvesThePictureNotTheBox()
	{
		final Answer world = Answer.of(Cause.SLOW_WORLD);
		for (BadgeStyle style : BadgeStyle.values())
		{
			final BufferedImage plain = paint(style, Level.BAD, world, false);
			final BufferedImage dim = paint(style, Level.BAD, world, true);
			final String what = style.toString();
			assertSize(what + ": the same size", plain.getWidth(), plain.getHeight(), dim);
			final int w = plain.getWidth();
			final int h = plain.getHeight();
			final boolean words = style.words();
			final boolean icon = style.icon();
			final BufferedImage picture = icon ? ICONS.get(Icon.WORLD, Level.BAD) : expectedShape(Level.BAD);
			final int px = words ? 5 : 4;
			final int py = words ? (icon ? 9 : 5) : 4;

			// the box: both frames and the ground around the picture are the same pixels
			for (int y = 0; y < h; y++)
			{
				for (int x = 0; x < w; x++)
				{
					final boolean frame = x <= 1 || y <= 1 || x >= w - 2 || y >= h - 2;
					final boolean margin = x <= 3 || y <= 3 || x >= w - 3;
					if (frame || margin)
					{
						assertEquals(what + ": the box is not dimmed at (" + x + ", " + y + ")",
							hex(plain.getRGB(x, y)), hex(dim.getRGB(x, y)));
					}
				}
			}
			// the picture: each of its pixels at full and at half strength over the ground
			for (int y = 0; y < picture.getHeight(); y++)
			{
				for (int x = 0; x < picture.getWidth(); x++)
				{
					final int p = picture.getRGB(x, y);
					final String at = what + ": picture pixel (" + x + ", " + y + ")";
					assertEquals(at + ", plain", hex(over(GROUND, p, 1f, true)), hex(plain.getRGB(px + x, py + y)));
					assertEquals(at + ", dimmed", hex(over(GROUND, p, 0.5f, true)), hex(dim.getRGB(px + x, py + y)));
					if ((p >>> 24) == 255)
					{
						assertTrue(at + ": half strength lets the box through",
							(dim.getRGB(px + x, py + y) >>> 24) < 255);
					}
				}
			}
			// the words: opaque at full strength, never opaque at half
			if (words)
			{
				final int textX = px + picture.getWidth() + 5;
				int opaquePlain = 0;
				for (int y = 2; y <= h - 3; y++)
				{
					for (int x = textX; x <= w - 3; x++)
					{
						if ((plain.getRGB(x, y) >>> 24) == 255)
						{
							opaquePlain++;
						}
						assertTrue(what + ": dimmed words at (" + x + ", " + y + ") are not opaque",
							(dim.getRGB(x, y) >>> 24) < 255);
					}
				}
				assertTrue(what + ": the plain words are opaque", opaquePlain > 0);
				assertNull(what + ": no full-strength line 1 when dimmed",
					box(dim, LINE_ONE[Level.BAD.ordinal()], textX, 0, h));
				assertNull(what + ": no full-strength line 2 when dimmed", box(dim, WHITE, textX, 0, h));
			}
		}
	}

	@Test
	public void aHiddenViewPaintsNothing()
	{
		assertNull(PAINTER.paint(BadgeView.HIDDEN));
		assertNull("not visible, whatever else it holds", PAINTER.paint(new BadgeView(false,
			BadgeStyle.ICON_AND_WORDS, Level.BAD, Icon.WORLD, false, "World lag", "Not you", "World lag - not you",
			"Ticks 1,240 ms, ping 41 ms")));
		assertNull(PAINTER.paint(null));
	}

	@Test
	public void aWordStyleWithNoIconDrawsTheShape()
	{
		final BufferedImage smooth = paint(BadgeStyle.ICON_AND_WORDS, Level.OK, Answer.SMOOTH, false);
		assertSize("smooth: the 14 px circle", 5 + 14 + 5 + width("Smooth") + 7, 25, smooth);
		assertPicture("the circle", smooth, expectedShape(Level.OK), 5, 5);

		final BufferedImage measuring = paint(BadgeStyle.ICON_AND_WORDS, Level.NO_DATA, Answer.MEASURING, false);
		assertSize("measuring: the ring", 5 + 14 + 5 + width("Measuring") + 7, 25, measuring);
		assertPicture("the ring", measuring, expectedShape(Level.NO_DATA), 5, 5);

		final Answer world = Answer.of(Cause.SLOW_WORLD);
		final BufferedImage okWithIcon = paint(BadgeStyle.ICON_AND_WORDS, Level.OK, world, false);
		assertPicture("an icon at OK is not drawn: the circle", okWithIcon, expectedShape(Level.OK), 5, 5);

		final BufferedImage noIcon = PAINTER.paint(new BadgeView(true, BadgeStyle.ICON_AND_WORDS, Level.BAD, Icon.NONE,
			false, "Lag", "Can't tell why", "Lag - can't tell why", ""));
		assertPicture("a lag with no picture: the square", noIcon, expectedShape(Level.BAD), 5, 5);

		final BadgePainter noPictures = new BadgePainter(new BadgeIcons(name -> null));
		final BufferedImage missing = noPictures.paint(view(BadgeStyle.ICON_AND_WORDS, Level.BAD, world, false));
		assertSize("a picture that did not load: the shape's size", 5 + 14 + 5 + width("World lag") + 7, 42, missing);
		assertPicture("and the shape", missing, expectedShape(Level.BAD), 5, 5);
	}

	@Test
	public void shapeAndWordsNeverDrawsAnIcon()
	{
		for (Answer a : table())
		{
			for (Level level : new Level[] {Level.WARN, Level.BAD})
			{
				final BufferedImage img = paint(BadgeStyle.SHAPE_AND_WORDS, level, a, false);
				final String what = a.oneLine + " " + level;
				final int n = a.line2.isEmpty() ? 1 : 2;
				assertSize(what + ": the 14 px shape's size",
					5 + 14 + 5 + Math.max(width(a.line1), width(a.line2)) + 7, 8 + Math.max(14, 17 * n), img);
				assertPicture(what + ": the shape", img, expectedShape(level), 5,
					n == 2 ? 5 : (img.getHeight() - 14) / 2);
				assertNull(what + ": no globe", box(img, SEA, 0, 0, img.getHeight()));
				for (int y = 2; y <= img.getHeight() - 3; y++)
				{
					for (int x = 19; x <= 23; x++)
					{
						assertEquals(what + ": nothing between the shape and the words", hex(GROUND),
							hex(img.getRGB(x, y)));
					}
				}
			}
		}
	}

	// ---------------------------------------------------------------- bare (the infobox's picture, addendum D)

	/** The style shows icons, the view has one, the level is BAD: the 24 x 24 file, pixel for pixel, no box. */
	@Test
	public void bareIconIsTheIconFile()
	{
		final Answer world = Answer.of(Cause.SLOW_WORLD);
		for (Level level : new Level[] {Level.WARN, Level.BAD})
		{
			final BufferedImage bare = PAINTER.bare(view(BadgeStyle.ICON, level, world, false));
			final BufferedImage file = ICONS.get(Icon.WORLD, level);
			assertSize(level + ": the icon file's size", 24, 24, bare);
			for (int y = 0; y < 24; y++)
			{
				for (int x = 0; x < 24; x++)
				{
					assertEquals(level + " (" + x + ", " + y + ")", hex(file.getRGB(x, y)), hex(bare.getRGB(x, y)));
				}
			}
		}
	}

	/** No icon to draw: the status shape doubled, each pixel of the 14 px shape a 2 x 2 block. */
	@Test
	public void bareShapeIsTheShapeDoubled()
	{
		final Answer world = Answer.of(Cause.SLOW_WORLD);
		for (Level level : Level.values())
		{
			final BufferedImage shape = expectedShape(level);
			final BufferedImage bare = PAINTER.bare(view(BadgeStyle.SHAPE_ONLY, level, world, false));
			assertSize(level + ": 28 x 28", 28, 28, bare);
			for (int y = 0; y < 28; y++)
			{
				for (int x = 0; x < 28; x++)
				{
					assertEquals(level + " (" + x + ", " + y + ")", hex(shape.getRGB(x / 2, y / 2)),
						hex(bare.getRGB(x, y)));
				}
			}
		}
	}

	/** ICON style with nothing to show an icon for (smooth, no icon): the shape, doubled. */
	@Test
	public void bareIconStyleWhileSmoothIsTheShape()
	{
		final BufferedImage bare = PAINTER.bare(view(BadgeStyle.ICON, Level.OK, Answer.SMOOTH, false));
		assertSize("smooth: the doubled circle", 28, 28, bare);
		final BufferedImage circle = expectedShape(Level.OK);
		for (int y = 0; y < 28; y++)
		{
			for (int x = 0; x < 28; x++)
			{
				assertEquals("(" + x + ", " + y + ")", hex(circle.getRGB(x / 2, y / 2)), hex(bare.getRGB(x, y)));
			}
		}
		final BufferedImage noIcon = PAINTER.bare(new BadgeView(true, BadgeStyle.ICON, Level.BAD, Icon.NONE, false,
			"Lag", "Can't tell why", "Lag - can't tell why", ""));
		assertSize("a lag with no picture: the square, doubled", 28, 28, noIcon);
		assertEquals(hex(SHAPE[Level.BAD.ordinal()]), hex(noIcon.getRGB(2, 2)));
		assertNull("nothing to draw for a hidden view", PAINTER.bare(BadgeView.HIDDEN));
		assertNull(PAINTER.bare(null));
	}

	/** Dimmed: the whole picture at half strength, so no pixel is more than half opaque. */
	@Test
	public void bareOfADimmedViewIsHalfStrength()
	{
		final Answer world = Answer.of(Cause.SLOW_WORLD);
		final BadgeView[] views = {view(BadgeStyle.ICON, Level.BAD, world, true),
			view(BadgeStyle.SHAPE_ONLY, Level.BAD, world, true), view(BadgeStyle.ICON, Level.OK, Answer.SMOOTH, true)};
		for (BadgeView v : views)
		{
			final BufferedImage plain = PAINTER.bare(new BadgeView(true, v.style, v.level, v.icon, false, v.line1,
				v.line2, v.tip1, v.tip2));
			final BufferedImage dim = PAINTER.bare(v);
			assertSize(v.style + ": the same size", plain.getWidth(), plain.getHeight(), dim);
			int opaque = 0;
			for (int y = 0; y < dim.getHeight(); y++)
			{
				for (int x = 0; x < dim.getWidth(); x++)
				{
					final int plainAlpha = plain.getRGB(x, y) >>> 24;
					final int alpha = dim.getRGB(x, y) >>> 24;
					assertTrue(v.style + " (" + x + ", " + y + "): alpha " + alpha + " is at most 128", alpha <= 128);
					if (plainAlpha == 255)
					{
						opaque++;
						assertTrue("an opaque pixel keeps half of it: " + alpha, alpha >= 127);
					}
				}
			}
			assertTrue(v.style + ": the picture has opaque pixels to halve", opaque > 0);
		}
	}

	/** The painter keeps no picture of its own to hand out: a new image every call, for the icon and the shape. */
	@Test
	public void bareIsANewImageEveryCall()
	{
		final Answer world = Answer.of(Cause.SLOW_WORLD);
		for (BadgeStyle style : new BadgeStyle[] {BadgeStyle.ICON, BadgeStyle.SHAPE_ONLY})
		{
			final BadgeView v = view(style, Level.BAD, world, false);
			final BufferedImage a = PAINTER.bare(v);
			final BufferedImage b = PAINTER.bare(v);
			assertTrue(style + ": two calls, two images", a != b);
			assertTrue(style + ": never the icon file itself", a != ICONS.get(Icon.WORLD, Level.BAD));
			a.setRGB(0, 0, 0x12345678);
			assertTrue("drawing on one does not change the next", b.getRGB(0, 0) != 0x12345678);
			assertTrue(PAINTER.bare(v).getRGB(0, 0) != 0x12345678);
		}
	}

	// ---------------------------------------------------------------- helpers

	/** Every row of contract 3.6's table: the answers of the fifteen rules and of the three card states. */
	static List<Answer> table()
	{
		final List<Answer> out = new ArrayList<>();
		for (Cause c : new Cause[] {Cause.ALL_CLEAR, Cause.SLOW_WORLD, Cause.PING_JUMPY, Cause.PING_HIGH,
			Cause.UPLOAD_LOSS, Cause.DISCONNECT, Cause.DELIVERY_GAP, Cause.SLOW_DRAWING, Cause.FRAME_CAP,
			Cause.CLIENT_BUSY, Cause.CLIENT_WAITING, Cause.MAP_LOAD, Cause.GC_PAUSE, Cause.HEAP_CAP_LOW,
			Cause.NOT_SURE})
		{
			out.add(Answer.of(c));
		}
		out.add(Answer.MEASURING);
		out.add(Answer.NOT_LOGGED_IN);
		out.add(Answer.WAITING);
		assertEquals(18, out.size());
		return out;
	}

	/** A visible view as the model makes one: the answer's lines and icon, its one line as the tooltip. */
	static BadgeView view(BadgeStyle style, Level level, Answer a, boolean dimmed)
	{
		return new BadgeView(true, style, level, a.icon, dimmed, a.line1, a.line2, a.oneLine, "");
	}

	private static BufferedImage paint(BadgeStyle style, Level level, Answer a, boolean dimmed)
	{
		final BufferedImage img = PAINTER.paint(view(style, level, a, dimmed));
		assertNotNull(img);
		assertEquals("ARGB", BufferedImage.TYPE_INT_ARGB, img.getType());
		return img;
	}

	/** 24 when the style draws the answer's icon at this level (P2.4), else 14, the status shape. */
	private static int pictureSide(BadgeStyle style, Level level, Icon icon)
	{
		return style.icon() && icon != Icon.NONE && (level == Level.WARN || level == Level.BAD) ? 24 : 14;
	}

	private static int width(String s)
	{
		return s.isEmpty() ? 0 : METRICS.stringWidth(s);
	}

	private static FontMetrics metrics()
	{
		final Graphics2D g = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB).createGraphics();
		hints(g);
		final FontMetrics m = g.getFontMetrics(FontManager.getRunescapeFont());
		g.dispose();
		return m;
	}

	private static void hints(Graphics2D g)
	{
		g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
		g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_OFF);
		g.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_OFF);
	}

	/**
	 * The status shape of P2.4 written again from the contract's words, 14 x 14: for x, y in 0 .. 11, with
	 * r2 = (x + 0.5 - 6)^2 + (y + 0.5 - 6)^2, a pixel is inside when OK r2 &lt;= 36, NO_DATA 9 &lt; r2 &lt;= 36, WARN
	 * |x + 0.5 - 6| &lt;= (y + 1) / 2, BAD always; inside at (x + 1, y + 1) in the level's colour, a pixel that is
	 * not inside with an inside pixel left, right, above or under it black, the rest clear.
	 */
	static BufferedImage expectedShape(Level level)
	{
		final BufferedImage img = new BufferedImage(14, 14, BufferedImage.TYPE_INT_ARGB);
		for (int y = -1; y <= 12; y++)
		{
			for (int x = -1; x <= 12; x++)
			{
				if (in(level, x, y))
				{
					img.setRGB(x + 1, y + 1, SHAPE[level.ordinal()]);
				}
				else if (in(level, x - 1, y) || in(level, x + 1, y) || in(level, x, y - 1) || in(level, x, y + 1))
				{
					img.setRGB(x + 1, y + 1, BLACK);
				}
			}
		}
		return img;
	}

	private static boolean in(Level level, int x, int y)
	{
		if (x < 0 || y < 0 || x > 11 || y > 11)
		{
			return false;
		}
		final double r2 = Math.pow(x + 0.5 - 6, 2) + Math.pow(y + 0.5 - 6, 2);
		switch (level)
		{
			case OK:
				return r2 <= 36;
			case NO_DATA:
				return r2 <= 36 && r2 > 9;
			case WARN:
				return Math.abs(x + 0.5 - 6) <= (y + 1) / 2.0;
			default:
				return true;
		}
	}

	/** The picture's every pixel at (x0, y0) of the badge: laid over the ground, as the painter lays it. */
	private static void assertPicture(String what, BufferedImage badge, BufferedImage picture, int x0, int y0)
	{
		for (int y = 0; y < picture.getHeight(); y++)
		{
			for (int x = 0; x < picture.getWidth(); x++)
			{
				assertEquals(what + ": pixel (" + x + ", " + y + ") of the picture",
					hex(over(GROUND, picture.getRGB(x, y), 1f, true)), hex(badge.getRGB(x0 + x, y0 + y)));
			}
		}
	}

	/** Line 1 (and line 2) of the answer, drawn at x with the block's top at {@code top}: their ink, where it goes. */
	private static void assertLines(BufferedImage badge, int x, int top, Level level, Answer a)
	{
		final int h = badge.getHeight();
		final Rectangle one = inkOf(a.line1);
		one.translate(x, top + 13);
		assertEquals(a.line1 + ": line 1's ink", one, box(badge, LINE_ONE[level.ordinal()], x, 0, h));
		if (!a.line2.isEmpty())
		{
			final Rectangle two = inkOf(a.line2);
			two.translate(x, top + 17 + 13);
			assertEquals(a.line2 + ": line 2's ink, 17 px lower", two, box(badge, WHITE, x, 0, h));
		}
	}

	/** Where a line's ink lies against its origin (x, baseline): the line drawn alone, in the badge's font. */
	private static Rectangle inkOf(String line)
	{
		final BufferedImage img = new BufferedImage(200, 60, BufferedImage.TYPE_INT_ARGB);
		final Graphics2D g = img.createGraphics();
		hints(g);
		g.setFont(FontManager.getRunescapeFont());
		g.setColor(Color.RED);
		g.drawString(line, 20, 30);
		g.dispose();
		final Rectangle r = box(img, Color.RED.getRGB(), 0, 0, 60);
		assertNotNull(line + " has ink", r);
		r.translate(-20, -30);
		return r;
	}

	/** The smallest rectangle holding every pixel of exactly this ARGB at x >= fromX and y in [y0, y1); null = none. */
	private static Rectangle box(BufferedImage img, int argb, int fromX, int y0, int y1)
	{
		int minX = Integer.MAX_VALUE;
		int minY = Integer.MAX_VALUE;
		int maxX = -1;
		int maxY = -1;
		for (int y = Math.max(0, y0); y < Math.min(img.getHeight(), y1); y++)
		{
			for (int x = Math.max(0, fromX); x < img.getWidth(); x++)
			{
				if (img.getRGB(x, y) == argb)
				{
					minX = Math.min(minX, x);
					minY = Math.min(minY, y);
					maxX = Math.max(maxX, x);
					maxY = Math.max(maxY, y);
				}
			}
		}
		return maxX < 0 ? null : new Rectangle(minX, minY, maxX - minX + 1, maxY - minY + 1);
	}

	/**
	 * What Java2D makes of {@code top} laid with SrcOver at {@code alpha} over a pixel that holds exactly
	 * {@code under}, on a small ARGB image: as an image ({@code drawImage}, the painter's way with pictures) or as a
	 * colour ({@code drawRect}, the painter's way with the frames).
	 */
	static int over(int under, int top, float alpha, boolean asImage)
	{
		final BufferedImage dst = new BufferedImage(3, 3, BufferedImage.TYPE_INT_ARGB);
		for (int y = 0; y < 3; y++)
		{
			for (int x = 0; x < 3; x++)
			{
				dst.setRGB(x, y, under);
			}
		}
		final Graphics2D g = dst.createGraphics();
		hints(g);
		g.setComposite(AlphaComposite.SrcOver.derive(alpha));
		if (asImage)
		{
			final BufferedImage src = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
			src.setRGB(0, 0, top);
			g.drawImage(src, 0, 0, null);
		}
		else
		{
			g.setColor(new Color(top, true));
			g.drawRect(0, 0, 2, 2);
		}
		g.dispose();
		return dst.getRGB(0, 0);
	}

	/** The SrcOver formula on straight (not premultiplied) ARGB, rounded. */
	private static int srcOver(int under, int top)
	{
		final double ta = (top >>> 24) / 255.0;
		final double ua = (under >>> 24) / 255.0;
		final double oa = ta + ua * (1 - ta);
		int out = (int) Math.round(oa * 255) << 24;
		for (int shift = 16; shift >= 0; shift -= 8)
		{
			final double tc = (top >>> shift) & 0xFF;
			final double uc = (under >>> shift) & 0xFF;
			out |= (int) Math.round((tc * ta + uc * ua * (1 - ta)) / oa) << shift;
		}
		return out;
	}

	private static void assertClose(String what, int want, int got)
	{
		for (int shift = 24; shift >= 0; shift -= 8)
		{
			assertTrue(what + ": " + hex(want) + " against " + hex(got),
				Math.abs(((want >>> shift) & 0xFF) - ((got >>> shift) & 0xFF)) <= 1);
		}
	}

	private static void assertSize(String what, int w, int h, BufferedImage img)
	{
		assertNotNull(what, img);
		assertEquals(what + ": width", w, img.getWidth());
		assertEquals(what + ": height", h, img.getHeight());
	}

	static int argb(int a, int r, int g, int b)
	{
		return a << 24 | r << 16 | g << 8 | b;
	}

	static String hex(int argb)
	{
		return String.format("%08X", argb);
	}
}
