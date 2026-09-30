package com.whylag;

import com.whylag.core.Icon;
import com.whylag.core.Level;
import java.awt.image.BufferedImage;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import net.runelite.client.util.ImageUtil;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

/**
 * The badge's eight pictures (contract P2.7, T20): every file is in the jar, 24 x 24 with alpha, named by
 * {@link BadgeIcons#fileName}; the corner carries the state's shape; the globe keeps its natural colours while the
 * other three are tinted; the corners are clear; OK, NO_DATA and NONE have no picture; and the eight are loaded once,
 * in the constructor, so {@link BadgeIcons#get} answers the same object every time. The pixels are those of
 * {@code whylag_icons.py} (its {@code --check} proves the files match the maps).
 */
public class BadgeIconsTest
{
	/** The four causes that have a picture, and the two states. */
	private static final Icon[] PICTURED = {Icon.WORLD, Icon.LINE, Icon.PC, Icon.UNKNOWN};
	private static final Level[] STATES = {Level.WARN, Level.BAD};

	private static final int BAD_S = 0xFFE61E1E;      // 230,30,30: the red square's face
	private static final int WARN_S = 0xFFE6961E;     // 230,150,30: the amber triangle's face
	private static final int SEA = 0xFF2A64C8;        // the set-A globe's sea

	@Test
	public void everyFileIsThereAndTwentyFourSquare()
	{
		final Set<String> names = new TreeSet<>();
		for (Icon icon : PICTURED)
		{
			for (Level level : STATES)
			{
				final String name = BadgeIcons.fileName(icon, level);
				assertEquals("badge-" + icon.file() + "-" + (level == Level.WARN ? "warn" : "bad") + ".png", name);
				assertNotNull("in the jar, next to BadgeIcons: " + name, BadgeIcons.class.getResource(name));
				final BufferedImage img = ImageUtil.loadImageResource(BadgeIcons.class, name);
				assertEquals(name, 24, img.getWidth());
				assertEquals(name, 24, img.getHeight());
				assertTrue(name + " has alpha", img.getColorModel().hasAlpha());
				names.add(name);
			}
		}
		assertEquals(new TreeSet<>(Arrays.asList(
			"badge-world-warn.png", "badge-world-bad.png", "badge-line-warn.png", "badge-line-bad.png",
			"badge-pc-warn.png", "badge-pc-bad.png",
			"badge-unknown-warn.png", "badge-unknown-bad.png")), names);

		final BadgeIcons icons = new BadgeIcons();
		for (Icon icon : PICTURED)
		{
			for (Level level : STATES)
			{
				final BufferedImage img = icons.get(icon, level);
				assertNotNull(icon + " " + level, img);
				assertEquals(24, img.getWidth());
				assertEquals(24, img.getHeight());
			}
		}
	}

	@Test
	public void theCornerCarriesTheStateShape()
	{
		final BadgeIcons icons = new BadgeIcons();
		for (Icon icon : PICTURED)
		{
			assertEquals(icon + ": the red square", BAD_S, icons.get(icon, Level.BAD).getRGB(19, 19));
			assertEquals(icon + ": the amber triangle", WARN_S, icons.get(icon, Level.WARN).getRGB(19, 19));
			for (Level level : STATES)
			{
				// the 9 x 9 mark spans x, y 15 .. 23: its top row's middle and its bottom corners are its black edge
				final BufferedImage img = icons.get(icon, level);
				assertEquals(icon + " " + level + ": the mark's top", 0xFF0A0A0A, img.getRGB(19, 15));
				assertEquals(icon + " " + level + ": the mark's bottom left", 0xFF0A0A0A, img.getRGB(15, 23));
				assertEquals(icon + " " + level + ": the mark's bottom right", 0xFF0A0A0A, img.getRGB(23, 23));
			}
		}
	}

	@Test
	public void theGlobeKeepsItsNaturalColours()
	{
		final BadgeIcons icons = new BadgeIcons();
		final BufferedImage bad = icons.get(Icon.WORLD, Level.BAD);
		final BufferedImage warn = icons.get(Icon.WORLD, Level.WARN);
		assertEquals("the sea, when bad", SEA, bad.getRGB(1, 10));
		assertEquals("the sea, when warn", SEA, warn.getRGB(1, 10));
		for (int y = 0; y < 24; y++)
		{
			for (int x = 0; x < 24; x++)
			{
				if (x < 15 || y < 15)
				{
					assertEquals("the globe outside its corner is the same in both states (" + x + ", " + y + ")",
						bad.getRGB(x, y), warn.getRGB(x, y));
				}
			}
		}
	}

	@Test
	public void theOtherIconsAreTinted()
	{
		final BadgeIcons icons = new BadgeIcons();
		assertEquals("the monitor's screen, warn", WARN_S, icons.get(Icon.PC, Level.WARN).getRGB(10, 8));
		assertEquals("the monitor's screen, bad", BAD_S, icons.get(Icon.PC, Level.BAD).getRGB(10, 8));
		for (Icon icon : new Icon[] {Icon.LINE, Icon.PC, Icon.UNKNOWN})
		{
			assertTrue(icon + " is tinted by the state outside its corner",
				differsOutsideTheCorner(icons.get(icon, Level.WARN), icons.get(icon, Level.BAD)));
		}
		assertFalse("the globe is not", differsOutsideTheCorner(icons.get(Icon.WORLD, Level.WARN),
			icons.get(Icon.WORLD, Level.BAD)));
	}

	@Test
	public void theCornersAreTransparent()
	{
		final BadgeIcons icons = new BadgeIcons();
		for (Icon icon : PICTURED)
		{
			for (Level level : STATES)
			{
				assertEquals(icon + " " + level + ": (23, 0) is clear", 0, icons.get(icon, level).getRGB(23, 0) >>> 24);
			}
		}
	}

	@Test
	public void noIconForOkOrNoDataOrNone()
	{
		final BadgeIcons icons = new BadgeIcons();
		for (Icon icon : Icon.values())
		{
			assertNull(icon + " OK", icons.get(icon, Level.OK));
			assertNull(icon + " NO_DATA", icons.get(icon, Level.NO_DATA));
			assertNull(icon + " null", icons.get(icon, null));
			assertNull(BadgeIcons.fileName(icon, Level.OK));
			assertNull(BadgeIcons.fileName(icon, Level.NO_DATA));
		}
		assertNull(icons.get(Icon.NONE, Level.WARN));
		assertNull(icons.get(Icon.NONE, Level.BAD));
		assertNull(icons.get(null, Level.BAD));
		assertNull(BadgeIcons.fileName(Icon.NONE, Level.BAD));
		assertNull(BadgeIcons.fileName(null, Level.BAD));
		assertEquals("badge-world-bad.png", BadgeIcons.fileName(Icon.WORLD, Level.BAD));
		assertEquals("badge-unknown-warn.png", BadgeIcons.fileName(Icon.UNKNOWN, Level.WARN));
	}

	/** T20: the eight files are loaded once, in the constructor; {@code get} loads nothing and answers one object. */
	@Test
	public void loadsEveryFileOnce()
	{
		final Map<String, Integer> loads = new HashMap<>();
		final BadgeIcons counted = new BadgeIcons(name ->
		{
			loads.merge(name, 1, Integer::sum);
			return new BufferedImage(24, 24, BufferedImage.TYPE_INT_ARGB);
		});
		assertEquals("eight files", 8, loads.size());
		for (Map.Entry<String, Integer> e : loads.entrySet())
		{
			assertEquals(e.getKey() + " is loaded once", 1, (int) e.getValue());
		}
		final BufferedImage first = counted.get(Icon.WORLD, Level.BAD);
		for (int i = 0; i < 100; i++)
		{
			for (Icon icon : Icon.values())
			{
				for (Level level : Level.values())
				{
					counted.get(icon, level);
				}
			}
			assertSame(first, counted.get(Icon.WORLD, Level.BAD));
		}
		assertEquals("get loads nothing", 8, loads.values().stream().mapToInt(Integer::intValue).sum());

		final BadgeIcons real = new BadgeIcons();
		for (Icon icon : PICTURED)
		{
			for (Level level : STATES)
			{
				assertSame(icon + " " + level, real.get(icon, level), real.get(icon, level));
			}
		}
		assertNotEquals("each file its own picture", real.get(Icon.PC, Level.BAD), real.get(Icon.PC, Level.WARN));
	}

	/** Choice of BadgeIcons: a file that fails to load leaves its slot null, and the other nine still load. */
	@Test
	public void aFileThatFailsToLoadIsNullAndTheRestLoad()
	{
		final BadgeIcons icons = new BadgeIcons(name ->
		{
			if (name.equals("badge-line-bad.png"))
			{
				throw new IllegalArgumentException(name);
			}
			return new BufferedImage(24, 24, BufferedImage.TYPE_INT_ARGB);
		});
		assertNull(icons.get(Icon.LINE, Level.BAD));
		assertNotNull(icons.get(Icon.LINE, Level.WARN));
		assertNotNull(icons.get(Icon.WORLD, Level.BAD));
	}

	private static boolean differsOutsideTheCorner(BufferedImage a, BufferedImage b)
	{
		for (int y = 0; y < 24; y++)
		{
			for (int x = 0; x < 24; x++)
			{
				if ((x < 15 || y < 15) && a.getRGB(x, y) != b.getRGB(x, y))
				{
					return true;
				}
			}
		}
		return false;
	}
}
