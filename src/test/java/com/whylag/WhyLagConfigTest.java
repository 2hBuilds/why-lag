package com.whylag;

import com.whylag.core.BadgeStyle;
import com.whylag.core.WhenSmooth;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

/**
 * Pins the config (contract 3.9): the group, the four keys and the stored names of the two enum settings are FROZEN
 * at first release - renaming one silently discards every user's setting - so they are written here as literals;
 * the four settings of the game badge sit in the one section, "Game screen"; their defaults are what RuneLite's
 * config proxy answers with nothing stored; and the interface is flat, because
 * {@code ConfigManager.setDefaultConfiguration} walks only {@code getDeclaredMethods()} (an inherited item gets no
 * default and the config panel then fails on {@code Enum.valueOf(type, null)}).
 */
public class WhyLagConfigTest
{
	/** The four settings of the game badge, as literals. */
	private static final List<String> BADGE_KEYS = Arrays.asList("badgeShow", "badgeStyle", "badgeWhenSmooth",
		"badgeChatLine");

	private static List<Method> items()
	{
		final List<Method> out = new ArrayList<>();
		for (Method m : WhyLagConfig.class.getMethods())
		{
			if (m.getParameterCount() == 0 && m.isAnnotationPresent(ConfigItem.class))
			{
				out.add(m);
			}
		}
		return out;
	}

	@Test
	public void theKeysAreExactlyTheseFour()
	{
		final Set<String> keys = new TreeSet<>();
		for (Method m : items())
		{
			keys.add(m.getAnnotation(ConfigItem.class).keyName());
		}
		assertEquals(new TreeSet<>(Arrays.asList("badgeShow", "badgeStyle", "badgeWhenSmooth", "badgeChatLine")),
			keys);
		assertEquals(4, items().size());
		assertTrue("fewer than ten keys", keys.size() < 10);
	}

	@Test
	public void theGroupIsWhylag()
	{
		final ConfigGroup group = WhyLagConfig.class.getAnnotation(ConfigGroup.class);
		assertNotNull("@ConfigGroup is missing", group);
		assertEquals("whylag", group.value());
		assertEquals("whylag", WhyLagConfig.GROUP);
	}

	@Test
	public void theInterfaceIsFlat()
	{
		assertEquals("it extends Config and nothing else", Arrays.asList(Config.class),
			Arrays.asList(WhyLagConfig.class.getInterfaces()));
		for (Method m : items())
		{
			assertEquals(m.getName() + " must be declared here, not inherited", WhyLagConfig.class,
				m.getDeclaringClass());
			assertTrue(m.getName() + " needs a default body", m.isDefault());
		}
	}

	@Test
	public void keysAndPositionsAreUnique()
	{
		final Set<String> keys = new HashSet<>();
		final Set<Integer> positions = new HashSet<>();
		for (Method m : items())
		{
			final ConfigItem item = m.getAnnotation(ConfigItem.class);
			assertTrue(keys.add(item.keyName()));
			assertTrue(positions.add(item.position()));
		}
	}

	/**
	 * The one section is a {@code String} constant of the interface, "gameScreen", named "Game screen"; the four
	 * badge settings carry it and no item is outside it. RuneLite keys a section by the constant's VALUE.
	 */
	@Test
	public void theBadgeKeysAreInTheGameScreenSection() throws IllegalAccessException
	{
		final List<Field> sections = new ArrayList<>();
		for (Field f : WhyLagConfig.class.getDeclaredFields())
		{
			if (f.isAnnotationPresent(ConfigSection.class))
			{
				sections.add(f);
			}
		}
		assertEquals("one section", 1, sections.size());
		final Field field = sections.get(0);
		assertEquals("a section is a String constant of the interface", String.class, field.getType());
		assertEquals("gameScreen", field.get(null));
		assertEquals("gameScreen", WhyLagConfig.GAME_SCREEN);
		final ConfigSection section = field.getAnnotation(ConfigSection.class);
		assertEquals("Game screen", section.name());
		assertEquals("The small badge on the game screen", section.description());
		assertEquals(10, section.position());
		assertFalse("open by default", section.closedByDefault());

		int inSection = 0;
		for (Method m : items())
		{
			final ConfigItem item = m.getAnnotation(ConfigItem.class);
			if (BADGE_KEYS.contains(item.keyName()))
			{
				assertEquals(item.keyName(), "gameScreen", item.section());
				assertTrue(item.keyName() + " sits under the section", item.position() > section.position());
				inSection++;
			}
			else
			{
				assertEquals(item.keyName() + " is in no section", "", item.section());
			}
		}
		assertEquals(4, inSection);
	}

	@Test
	public void theDefaults()
	{
		final WhyLagConfig config = new WhyLagConfig()
		{
		};
		assertTrue(config.badgeShow());
		assertSame(BadgeStyle.ICON, config.badgeStyle());
	}

	/** The badge's four defaults as RuneLite hands them to the plugin with nothing stored: on, Icon, Show, on. */
	@Test
	public void theBadgeDefaults()
	{
		final WhyLagConfig config = proxy();
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

	@Test
	public void theWordsOfTheItems() throws NoSuchMethodException
	{
		assertItem("badgeShow", "Show on game screen", "A small badge on the game screen that shows what is lagging",
			11);
		assertItem("badgeStyle", "Style",
			"A picture of the cause, the picture with words, a shape with words, or the shape alone", 12);
		assertItem("badgeWhenSmooth", "When smooth",
			"Show the green circle while all is well, or hide the badge until something lags", 13);
		assertItem("badgeChatLine", "Chat line on a lag", "One game message after each lag that names the cause", 14);
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

	/** The item whose method (and key) is {@code key}: its words and its position. */
	private static void assertItem(String key, String name, String description, int position)
		throws NoSuchMethodException
	{
		final ConfigItem item = WhyLagConfig.class.getMethod(key).getAnnotation(ConfigItem.class);
		assertEquals(key, item.keyName());
		assertEquals(name, item.name());
		assertEquals(description, item.description());
		assertEquals(position, item.position());
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

	/**
	 * The config as RuneLite hands it to a plugin with nothing stored: a {@link Proxy} of the interface whose every
	 * item answers its own default body, called the way {@code ConfigInvocationHandler.callDefaultMethod} calls it
	 * (a private lookup, {@code unreflectSpecial}, bound to the proxy).
	 */
	private static WhyLagConfig proxy()
	{
		return (WhyLagConfig) Proxy.newProxyInstance(WhyLagConfig.class.getClassLoader(),
			new Class<?>[] {WhyLagConfig.class}, (proxy, method, args) ->
			{
				if (!method.isDefault())
				{
					throw new UnsupportedOperationException(method.getName() + " has no default body");
				}
				return MethodHandles.privateLookupIn(WhyLagConfig.class, MethodHandles.lookup())
					.unreflectSpecial(method, WhyLagConfig.class)
					.bindTo(proxy)
					.invokeWithArguments(args == null ? new Object[0] : args);
			});
	}
}
