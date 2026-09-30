package com.whylag.core;

import java.util.Collections;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

/**
 * The badge's four settings as the gear menu ticks them (1.0.1, lot C, C4): the config's own defaults, a null that
 * keeps them, and the snapshot that carries them - by default, and after {@code withBadgeSettings} and
 * {@code withDiagnostics}, which keep each other's fields.
 */
public class BadgeSettingsTest
{
	/** The defaults are the config's: show on, the icon, shown while smooth, the chat line on. */
	@Test
	public void theDefaultsAreTheConfigsOwn()
	{
		assertTrue(BadgeSettings.DEFAULTS.show);
		assertEquals(BadgeStyle.ICON, BadgeSettings.DEFAULTS.style);
		assertEquals(WhenSmooth.SHOW, BadgeSettings.DEFAULTS.whenSmooth);
		assertTrue(BadgeSettings.DEFAULTS.chatLine);
	}

	/** What is given is what is kept; a null style or choice is the default, so the menu can always be drawn. */
	@Test
	public void theValuesAreKeptAndANullIsTheDefault()
	{
		final BadgeSettings s = new BadgeSettings(false, BadgeStyle.SHAPE_ONLY, WhenSmooth.HIDE, false);
		assertFalse(s.show);
		assertEquals(BadgeStyle.SHAPE_ONLY, s.style);
		assertEquals(WhenSmooth.HIDE, s.whenSmooth);
		assertFalse(s.chatLine);

		final BadgeSettings none = new BadgeSettings(false, null, null, false);
		assertEquals(BadgeStyle.ICON, none.style);
		assertEquals(WhenSmooth.SHOW, none.whenSmooth);
		assertFalse("the two booleans are never defaulted", none.show || none.chatLine);
	}

	/** A snapshot built without them holds the defaults; {@code withBadgeSettings} and {@code withDiagnostics} keep the rest. */
	@Test
	public void theSnapshotCarriesThemAndEachWithMethodKeepsTheOthersFields()
	{
		final PanelSnapshot plain = new PanelSnapshot(1_000L, null, 416, null, new Tile[0], 10, 0, 0, new Strip[0],
			Collections.emptyList(), Collections.emptyList(), new int[Group.values().length], 0, 0, null, "foot");
		assertSame(BadgeSettings.DEFAULTS, plain.badgeSettings);

		final BadgeSettings odd = new BadgeSettings(false, BadgeStyle.SHAPE_AND_WORDS, WhenSmooth.HIDE, false);
		final PanelSnapshot withBadge = plain.withBadgeSettings(odd);
		assertNotSame(plain, withBadge);
		assertSame(odd, withBadge.badgeSettings);
		assertEquals(416, withBadge.world);
		assertEquals("foot", withBadge.footer);
		assertSame("the arrays are shared", plain.sessionCounts, withBadge.sessionCounts);
		assertSame("the original is untouched", BadgeSettings.DEFAULTS, plain.badgeSettings);

		final PanelSnapshot both = withBadge.withDiagnostics("1.0.1", "minutes\n", "notes");
		assertSame("the diagnostics keep the settings", odd, both.badgeSettings);
		assertEquals("1.0.1", both.pluginVersion);
		assertEquals("minutes\n", both.minuteText);
		assertEquals("notes", both.diagnosticsText);
		final PanelSnapshot back = both.withBadgeSettings(BadgeSettings.DEFAULTS);
		assertEquals("the settings keep the diagnostics", "notes", back.diagnosticsText);
		assertEquals("1.0.1", back.pluginVersion);

		assertSame("a null is the defaults", BadgeSettings.DEFAULTS, plain.withBadgeSettings(null).badgeSettings);
	}
}
