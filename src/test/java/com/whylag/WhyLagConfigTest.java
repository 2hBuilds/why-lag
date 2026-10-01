package com.whylag;

import com.whylag.core.BadgeStyle;
import com.whylag.core.WhenSmooth;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

/**
 * Pins the config (contract 3.9): the group and the stored names of the two enum settings are FROZEN at first
 * release - renaming one silently discards every user's setting - so they are written here as literals; the badge's
 * defaults are what an implementation with nothing stored answers. The interface's own shape (the four keys, the one
 * section, flat and not inherited, the words of each item) is read from the interface itself, so it is the probe's
 * {@code WhyLagConfigStructureTest}.
 */
public class WhyLagConfigTest
{
	@Test
	public void theGroupIsWhylag()
	{
		assertEquals("whylag", WhyLagConfig.GROUP);
		assertEquals("gameScreen", WhyLagConfig.GAME_SCREEN);
	}

	/** The badge's four defaults, as an implementation with nothing stored answers them: on, Icon, Show, on. */
	@Test
	public void theBadgeDefaults()
	{
		final WhyLagConfig config = new WhyLagConfig()
		{
		};
		assertTrue(config.badgeShow());
		assertSame(BadgeStyle.ICON, config.badgeStyle());
		assertSame(WhenSmooth.SHOW, config.badgeWhenSmooth());
		assertTrue(config.badgeChatLine());
	}

	/**
	 * {@code ConfigManager} stores an enum setting by {@code name()} and reads it back with {@code Enum.valueOf}; the
	 * settings list shows {@code toString()}. So the constant NAMES are the stored values and are frozen, while the
	 * words shown may change. Written as literals.
	 */
	@Test
	public void theStoredEnumNamesArePinned()
	{
		assertEquals(Arrays.asList("ICON", "ICON_AND_WORDS", "SHAPE_AND_WORDS", "SHAPE_ONLY"),
			names(BadgeStyle.values()));
		assertEquals(Arrays.asList("SHOW", "HIDE"), names(WhenSmooth.values()));
		assertSame(BadgeStyle.ICON, Enum.valueOf(BadgeStyle.class, "ICON"));
		assertSame(BadgeStyle.ICON_AND_WORDS, Enum.valueOf(BadgeStyle.class, "ICON_AND_WORDS"));
		assertSame(BadgeStyle.SHAPE_AND_WORDS, Enum.valueOf(BadgeStyle.class, "SHAPE_AND_WORDS"));
		assertSame(BadgeStyle.SHAPE_ONLY, Enum.valueOf(BadgeStyle.class, "SHAPE_ONLY"));
		assertSame(WhenSmooth.SHOW, Enum.valueOf(WhenSmooth.class, "SHOW"));
		assertSame(WhenSmooth.HIDE, Enum.valueOf(WhenSmooth.class, "HIDE"));
		for (Enum<?> e : new Enum<?>[] {BadgeStyle.ICON, BadgeStyle.ICON_AND_WORDS, BadgeStyle.SHAPE_AND_WORDS,
			BadgeStyle.SHAPE_ONLY, WhenSmooth.SHOW, WhenSmooth.HIDE})
		{
			assertNotEquals("the list shows words, the store keeps the name", e.name(), e.toString());
		}
	}

	/**
	 * {@code ConfigManager} stores an enum by {@code name()} and reads it back with {@code Enum.valueOf}; the combo
	 * box shows {@code toString()}, which is the chip's label.
	 */
	@Test
	public void theRangeIsStoredByNameAndShownByLabel()
	{
		for (GraphRange r : GraphRange.values())
		{
			assertEquals(r, Enum.valueOf(GraphRange.class, r.name()));
			assertEquals(r.minutes() + " min", r.toString());
			assertEquals(r, GraphRange.of(r.minutes()));
		}
		assertEquals(1, GraphRange.ONE_MIN.minutes());
		assertEquals(10, GraphRange.TEN_MIN.minutes());
		assertEquals(60, GraphRange.SIXTY_MIN.minutes());
		assertEquals("TEN_MIN", GraphRange.TEN_MIN.name());
		assertEquals("any other number answers the default", GraphRange.TEN_MIN, GraphRange.of(7));
		assertEquals(GraphRange.TEN_MIN, GraphRange.of(0));
	}

	private static List<String> names(Enum<?>[] values)
	{
		final List<String> out = new ArrayList<>();
		for (Enum<?> e : values)
		{
			out.add(e.name());
		}
		return out;
	}
}
