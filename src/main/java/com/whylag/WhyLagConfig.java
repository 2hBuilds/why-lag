package com.whylag;

import com.whylag.core.BadgeStyle;
import com.whylag.core.WhenSmooth;
import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;

/**
 * The plugin's one config (contract 3.9). Five items: the memory source, then the four settings
 * of the game badge under the section "Game screen". The group, the five keys and the stored names of the two enum
 * settings ({@link BadgeStyle}, {@link WhenSmooth}) are FROZEN at first release, because renaming one silently
 * discards every user's setting ({@code WhyLagConfigTest} pins them as literals). The section's key,
 * {@link #GAME_SCREEN}, names the section only; it stores nothing.
 *
 * <p>The graph range is NOT a setting (the user, 2026-09-29: the settings "seem duplicate"): the three chips on
 * the panel are the one place it is chosen, and the panel opens on 1 min.
 *
 * <p><b>Flat on purpose.</b> RuneLite's {@code ConfigManager.setDefaultConfiguration} walks only
 * {@code getDeclaredMethods()} of the interface it is given, so an item inherited from a super-interface gets no
 * stored default and the config panel then fails on {@code Enum.valueOf(type, null)} (CLAUDE.md, "Config
 * interfaces must be FLAT"). Every item is declared here, a section is a {@code String} constant of this interface,
 * and this interface extends {@link Config} alone.
 */
@ConfigGroup(WhyLagConfig.GROUP)
public interface WhyLagConfig extends Config
{
	String GROUP = "whylag";

	@ConfigItem(
		keyName = "systemStats",
		name = "Exact memory pauses",
		description = "Read memory clean-up pauses and processor use from Java. Off: memory is estimated.",
		position = 2
	)
	default boolean systemStats()
	{
		return true;
	}

	@ConfigSection(
		name = "Game screen",
		description = "The small badge on the game screen",
		position = 10
	)
	String GAME_SCREEN = "gameScreen";

	@ConfigItem(
		keyName = "badgeShow",
		name = "Show on game screen",
		description = "A small badge on the game screen that shows what is lagging",
		position = 11,
		section = GAME_SCREEN
	)
	default boolean badgeShow()
	{
		return true;
	}

	@ConfigItem(
		keyName = "badgeStyle",
		name = "Style",
		description = "A picture of the cause, the picture with words, a shape with words, or the shape alone",
		position = 12,
		section = GAME_SCREEN
	)
	default BadgeStyle badgeStyle()
	{
		return BadgeStyle.ICON;
	}

	@ConfigItem(
		keyName = "badgeWhenSmooth",
		name = "When smooth",
		description = "Show the green circle while all is well, or hide the badge until something lags",
		position = 13,
		section = GAME_SCREEN
	)
	default WhenSmooth badgeWhenSmooth()
	{
		return WhenSmooth.SHOW;
	}

	@ConfigItem(
		keyName = "badgeChatLine",
		name = "Chat line on a lag",
		description = "One game message after each lag that names the cause",
		position = 14,
		section = GAME_SCREEN
	)
	default boolean badgeChatLine()
	{
		return true;
	}
}
