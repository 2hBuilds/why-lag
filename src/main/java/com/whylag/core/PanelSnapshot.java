package com.whylag.core;

import java.time.ZoneId;
import java.util.Collections;
import java.util.List;

/**
 * Everything the panel draws in one second, immutable once built (contract 3.8): built on the sampler thread,
 * read on the Swing thread. The panel needs nothing else - a selected event is drawn from its own numbers
 * ({@link EventView}).
 *
 * <p>{@code world}: while logged in, the newest second's world column; 0 = NOT logged in (contract 5.2: the newest
 * second is not in-game, or there is none yet), whatever that column holds. The header and the report read "not
 * logged in" from it. {@code tiles}: {@link Lane#TILES} (3), one for each lane, in {@link Lane} order.
 * {@code strips}: 3, in {@link Lane} order. {@code rangeMinutes} 1, 10 or 60; the range is shorter than that while
 * {@link #stretched()}. {@code rangeEvents}: closed events in the range, oldest first.
 * {@code sessionEvents}: closed events of the session, oldest first, at most {@link Thresholds#EVENTS}.
 * {@code sessionCounts}: by {@link Group} ordinal. {@code footer}: "" outside developer mode.
 *
 * <p>{@code pluginVersion}, {@code minuteText} and {@code diagnosticsText} are what the report prints besides the
 * numbers: the plugin's version (the report's first line), the minute log's lines ({@link MinuteLog#text}) and the
 * diagnostics' three sections ({@link Diagnostics#text}). The plugin builds both texts on the sampler thread and
 * attaches them with {@link #withDiagnostics}; a snapshot built without them holds "" for each, and the report then
 * says "(none)" under each heading and leaves the version out of its first line.
 *
 * <p>{@code badgeSettings} (1.0.1, lot C) are the game badge's four settings as stored, which the panel's gear menu
 * ticks; the plugin attaches them with {@link #withBadgeSettings} on the sampler thread. A snapshot built without
 * them holds {@link BadgeSettings#DEFAULTS}.
 *
 * <p>The lists are kept unmodifiable (null becomes empty) and a null footer or text becomes ""; the arrays belong to
 * the snapshot and nobody writes them after construction.
 *
 * <p>Choice: the version travels as text in the snapshot because {@code core} cannot import {@code com.whylag.Version}
 * ({@code WhyLagGuardTest}) and the report is made from the snapshot alone.
 */
public final class PanelSnapshot
{
	private static final long MS_PER_MINUTE = 60_000L;

	public final long wallMs;
	public final ZoneId zone;
	/** The newest second's world while logged in; 0 = not logged in, whatever the world column holds. */
	public final int world;
	public final Verdict verdict;
	/** {@link Lane#TILES} (3): one for each lane, in {@link Lane} order. */
	public final Tile[] tiles;
	/** 1, 10 or 60. */
	public final int rangeMinutes;
	public final long rangeStartWallMs, rangeEndWallMs;
	/** 3, in {@link Lane} order. */
	public final Strip[] strips;
	/** Closed, oldest first, unmodifiable. */
	public final List<LagEvent> rangeEvents;
	/** Closed, oldest first, unmodifiable, at most 500. */
	public final List<LagEvent> sessionEvents;
	/** By {@link Group} ordinal. */
	public final int[] sessionCounts;
	public final int sessionTotal;
	public final long sessionStartWallMs;
	public final SettingsView settings;
	/** "" outside developer mode. */
	public final String footer;
	/** The plugin's version, "major.minor.patch"; "" = not given. */
	public final String pluginVersion;
	/** {@link MinuteLog#text}: one line per minute, oldest first, each ended with a new line; "" = none. */
	public final String minuteText;
	/** {@link Diagnostics#text}: the Notes, Warnings and Errors sections; "" = not given. */
	public final String diagnosticsText;
	/** The game badge's four settings as stored; never null. */
	public final BadgeSettings badgeSettings;

	public PanelSnapshot(long wallMs, ZoneId zone, int world, Verdict verdict, Tile[] tiles, int rangeMinutes,
		long rangeStartWallMs, long rangeEndWallMs, Strip[] strips, List<LagEvent> rangeEvents,
		List<LagEvent> sessionEvents, int[] sessionCounts, int sessionTotal, long sessionStartWallMs,
		SettingsView settings, String footer)
	{
		this(wallMs, zone, world, verdict, tiles, rangeMinutes, rangeStartWallMs, rangeEndWallMs, strips,
			rangeEvents, sessionEvents, sessionCounts, sessionTotal, sessionStartWallMs, settings, footer, "", "",
			"");
	}

	public PanelSnapshot(long wallMs, ZoneId zone, int world, Verdict verdict, Tile[] tiles, int rangeMinutes,
		long rangeStartWallMs, long rangeEndWallMs, Strip[] strips, List<LagEvent> rangeEvents,
		List<LagEvent> sessionEvents, int[] sessionCounts, int sessionTotal, long sessionStartWallMs,
		SettingsView settings, String footer, String pluginVersion, String minuteText, String diagnosticsText)
	{
		this(wallMs, zone, world, verdict, tiles, rangeMinutes, rangeStartWallMs, rangeEndWallMs, strips,
			rangeEvents, sessionEvents, sessionCounts, sessionTotal, sessionStartWallMs, settings, footer,
			pluginVersion, minuteText, diagnosticsText, BadgeSettings.DEFAULTS);
	}

	public PanelSnapshot(long wallMs, ZoneId zone, int world, Verdict verdict, Tile[] tiles, int rangeMinutes,
		long rangeStartWallMs, long rangeEndWallMs, Strip[] strips, List<LagEvent> rangeEvents,
		List<LagEvent> sessionEvents, int[] sessionCounts, int sessionTotal, long sessionStartWallMs,
		SettingsView settings, String footer, String pluginVersion, String minuteText, String diagnosticsText,
		BadgeSettings badgeSettings)
	{
		this.wallMs = wallMs;
		this.zone = zone;
		this.world = world;
		this.verdict = verdict;
		this.tiles = tiles;
		this.rangeMinutes = rangeMinutes;
		this.rangeStartWallMs = rangeStartWallMs;
		this.rangeEndWallMs = rangeEndWallMs;
		this.strips = strips;
		this.rangeEvents = rangeEvents == null ? Collections.emptyList() : Collections.unmodifiableList(rangeEvents);
		this.sessionEvents = sessionEvents == null
			? Collections.emptyList()
			: Collections.unmodifiableList(sessionEvents);
		this.sessionCounts = sessionCounts;
		this.sessionTotal = sessionTotal;
		this.sessionStartWallMs = sessionStartWallMs;
		this.settings = settings;
		this.footer = footer == null ? "" : footer;
		this.pluginVersion = pluginVersion == null ? "" : pluginVersion;
		this.minuteText = minuteText == null ? "" : minuteText;
		this.diagnosticsText = diagnosticsText == null ? "" : diagnosticsText;
		this.badgeSettings = badgeSettings == null ? BadgeSettings.DEFAULTS : badgeSettings;
	}

	/**
	 * The same snapshot with the plugin's version and the two texts of the report's last sections; every other field
	 * is copied, the lists and arrays shared.
	 */
	public PanelSnapshot withDiagnostics(String version, String minutes, String diagnostics)
	{
		return new PanelSnapshot(wallMs, zone, world, verdict, tiles, rangeMinutes, rangeStartWallMs, rangeEndWallMs,
			strips, rangeEvents, sessionEvents, sessionCounts, sessionTotal, sessionStartWallMs, settings, footer,
			version, minutes, diagnostics, badgeSettings);
	}

	/**
	 * The same snapshot with the game badge's four settings as stored; every other field is copied, the lists and
	 * arrays shared.
	 */
	public PanelSnapshot withBadgeSettings(BadgeSettings badge)
	{
		return new PanelSnapshot(wallMs, zone, world, verdict, tiles, rangeMinutes, rangeStartWallMs, rangeEndWallMs,
			strips, rangeEvents, sessionEvents, sessionCounts, sessionTotal, sessionStartWallMs, settings, footer,
			pluginVersion, minuteText, diagnosticsText, badge);
	}

	/**
	 * True while the session holds less than the range: the range runs from the session's first second to now, and
	 * the graphs stretch it over their full width (changes after the first live look, 2026-09-29).
	 */
	public boolean stretched()
	{
		return rangeEndWallMs - rangeStartWallMs < rangeMinutes * MS_PER_MINUTE;
	}
}
