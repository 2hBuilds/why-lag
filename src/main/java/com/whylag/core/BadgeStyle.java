package com.whylag.core;

/**
 * How the game badge draws its answer (contract 3.2, P2.3): the setting "Style" of the section "Game screen"
 * ({@code WhyLagConfig.badgeStyle}, default {@link #ICON}).
 *
 * <p><b>The constant NAMES are frozen at first release.</b> RuneLite's {@code ConfigManager} stores an enum setting
 * by {@code name()} and reads it back with {@code Enum.valueOf}, so renaming a constant silently resets every user's
 * choice ({@code WhyLagConfigTest.theStoredEnumNamesArePinned}). {@link #toString()} is the name the settings list
 * shows, and may change.
 */
public enum BadgeStyle
{
	/** The cause's icon with its status shape in the corner; a shape alone while smooth or measuring. No words. */
	ICON("Icon", true, false),
	/** The icon, and the answer's two lines beside it. */
	ICON_AND_WORDS("Icon and words", true, true),
	/** The status shape, and the answer's two lines beside it. Never an icon. */
	SHAPE_AND_WORDS("Shape and words", false, true),
	/** The status shape alone, in every state; the tooltip tells the causes apart. */
	SHAPE_ONLY("Shape only", false, false);

	private final String label;
	private final boolean icon;
	private final boolean words;

	BadgeStyle(String label, boolean icon, boolean words)
	{
		this.label = label;
		this.icon = icon;
		this.words = words;
	}

	/** The name in the settings list: "Icon", "Icon and words", "Shape and words", "Shape only". */
	@Override
	public String toString()
	{
		return label;
	}

	/** True when the style draws the cause's icon: {@link #ICON} and {@link #ICON_AND_WORDS}. */
	public boolean icon()
	{
		return icon;
	}

	/** True when the style draws the answer's words: {@link #ICON_AND_WORDS} and {@link #SHAPE_AND_WORDS}. */
	public boolean words()
	{
		return words;
	}
}
