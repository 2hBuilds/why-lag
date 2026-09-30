package com.whylag.core;

/**
 * What the game badge does while all is well (contract 3.2, P2.2): the setting "When smooth" of the section "Game
 * screen" ({@code WhyLagConfig.badgeWhenSmooth}, default {@link #SHOW}). {@link #HIDE} hides the badge while smooth
 * and while measuring; a lag or a condition is drawn either way.
 *
 * <p><b>The constant NAMES are frozen at first release</b>: they are the stored values of the setting
 * ({@code WhyLagConfigTest.theStoredEnumNamesArePinned}). {@link #toString()} is the name the settings list shows.
 */
public enum WhenSmooth
{
	/** The green circle while all is well. */
	SHOW("Show"),
	/** Nothing until something lags. */
	HIDE("Hide");

	private final String label;

	WhenSmooth(String label)
	{
		this.label = label;
	}

	/** The name in the settings list: "Show", "Hide". */
	@Override
	public String toString()
	{
		return label;
	}
}
