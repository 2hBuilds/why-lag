package com.whylag.core;

/**
 * The four settings of the game badge as the panel's gear menu ticks them (1.0.1, lot C): whether the badge shows,
 * its {@link BadgeStyle}, its {@link WhenSmooth} choice and whether a lag sends a chat line. Immutable. The plugin
 * reads them from its config on the sampler thread, where the badge reads them too, and attaches them to the
 * snapshot, so the menu ticks what is stored and the Swing thread never reads the config.
 *
 * <p>Choice: a null style or a null when-smooth choice is kept as the config's own default ({@link BadgeStyle#ICON},
 * {@link WhenSmooth#SHOW}), so a config that answers nothing still gives a menu that can be drawn.
 */
public final class BadgeSettings
{
	/** The config's own defaults: show on, {@link BadgeStyle#ICON}, {@link WhenSmooth#SHOW}, chat line on. */
	public static final BadgeSettings DEFAULTS = new BadgeSettings(true, BadgeStyle.ICON, WhenSmooth.SHOW, true);

	public final boolean show;
	public final BadgeStyle style;
	public final WhenSmooth whenSmooth;
	public final boolean chatLine;

	/** The four settings as given; a null style or when-smooth choice is kept as the config's own default. */
	public BadgeSettings(boolean show, BadgeStyle style, WhenSmooth whenSmooth, boolean chatLine)
	{
		this.show = show;
		this.style = style == null ? BadgeStyle.ICON : style;
		this.whenSmooth = whenSmooth == null ? WhenSmooth.SHOW : whenSmooth;
		this.chatLine = chatLine;
	}
}
