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
 * second is not in-game, or there is none yet), whatever that column holds. The header, the report and the CPU cell
 * ({@link Cells}) read "not logged in" from it. {@code tiles}: {@link Lane#TILES} (4), the first four lanes, in
 * {@link Lane} order. {@code strips}: 5, in {@link Lane} order; {@code strips[4]} is the CPU lane.
 * {@code rangeMinutes} 1, 10 or 60; the range is shorter than that while {@link #stretched()}. {@code rangeEvents}: closed events in the range, oldest first.
 * {@code sessionEvents}: closed events of the session, oldest first, at most {@link Thresholds#EVENTS}.
 * {@code sessionCounts}: by {@link Group} ordinal. {@code sysCpuPct} / {@code gameBusyPct}: NOW, the numbers behind
 * the CPU lane's two halves (contract 5.3): the newest second's whole-PC CPU % and the game thread's busy %
 * ({@link Fmt#busyPct}); -1 when that half has no value (no data, or not logged in); the report reads them.
 * {@code footer}: "" outside developer mode.
 *
 * <p>The lists are kept unmodifiable (null becomes empty) and a null footer becomes ""; the arrays belong to the
 * snapshot and nobody writes them after construction.
 */
public final class PanelSnapshot
{
	private static final long MS_PER_MINUTE = 60_000L;

	public final long wallMs;
	public final ZoneId zone;
	/** The newest second's world while logged in; 0 = not logged in, whatever the world column holds. */
	public final int world;
	public final Verdict verdict;
	/** {@link Lane#TILES} (4): the first four lanes, in {@link Lane} order. */
	public final Tile[] tiles;
	/** 1, 10 or 60. */
	public final int rangeMinutes;
	public final long rangeStartWallMs, rangeEndWallMs;
	/** 5, in {@link Lane} order; {@code strips[4]} is the CPU lane. */
	public final Strip[] strips;
	/** Closed, oldest first, unmodifiable. */
	public final List<LagEvent> rangeEvents;
	/** Closed, oldest first, unmodifiable, at most 500. */
	public final List<LagEvent> sessionEvents;
	/** By {@link Group} ordinal. */
	public final int[] sessionCounts;
	public final int sessionTotal;
	public final long sessionStartWallMs;
	/** NOW: the newest second's whole-PC CPU % and the game thread's busy %; -1 = no value (no data, not logged in). */
	public final int sysCpuPct, gameBusyPct;
	public final SettingsView settings;
	/** "" outside developer mode. */
	public final String footer;

	public PanelSnapshot(long wallMs, ZoneId zone, int world, Verdict verdict, Tile[] tiles, int rangeMinutes,
		long rangeStartWallMs, long rangeEndWallMs, Strip[] strips, List<LagEvent> rangeEvents,
		List<LagEvent> sessionEvents, int[] sessionCounts, int sessionTotal, long sessionStartWallMs, int sysCpuPct,
		int gameBusyPct, SettingsView settings, String footer)
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
		this.sysCpuPct = sysCpuPct;
		this.gameBusyPct = gameBusyPct;
		this.settings = settings;
		this.footer = footer == null ? "" : footer;
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
