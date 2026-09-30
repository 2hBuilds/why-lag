package com.whylag;

/**
 * The plugin's version, in one place: the panel's last row prints it, the plugin's description ends with it, the
 * first note of the diagnostics names it and the report's first line opens with it.
 *
 * <p>It is bumped BY HAND each release, in step with {@code version=} in the export's
 * {@code runelite-plugin.properties} (the Hub reads that one). The export's {@code publish.py} refuses to commit when
 * the two differ, so a release cannot go out saying one thing to the Hub and another to the player.
 *
 * <p>Choice: the constant lives in {@code com.whylag}, not in {@code core}: {@code core} imports nothing outside its
 * own package and the JDK ({@code WhyLagGuardTest}), so the snapshot carries the version to the report as text.
 */
public final class Version
{
	/** The version this build ships as, {@code major.minor.patch}. */
	public static final String CURRENT = "1.0.0";

	private Version()
	{
	}
}
