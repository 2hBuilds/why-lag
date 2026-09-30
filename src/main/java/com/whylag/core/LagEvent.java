package com.whylag.core;

/**
 * One lag event, immutable (contract 3.4). Seconds are session seconds, both ends included.
 *
 * <p><b>It carries its own numbers</b>, taken over its span by the detector when it closes, so the panel can show
 * a selected event from the snapshot alone: {@code world}, {@code region}, {@code players} and {@code npcs} of its
 * FIRST second, where the lag began; {@code fps} the median frames a second, {@code worstFrameMs},
 * {@code meanTickGapMs} (every gap, no trim), {@code worstTickGapMs}, {@code worstCorrectedTickMs} (the largest
 * {@link TickRing#corrected}), {@code rttMs} / {@code rttMaxMs} (median and highest fresh RTT), {@code rttBeforeMs}
 * (this world's usual RTT when the event OPENED), {@code sentUnits} / {@code resentUnits} (over the span and
 * {@link Thresholds#RESENT_LOOK_S} either side), {@code gcPauseMs}, {@code heapUsedMb} (the highest in the span),
 * {@code heapMaxMb} (the limit; -1 when the settings' limit is 0 or less, which means unknown, contract 3.7),
 * {@code sysCpuPct} (the highest whole-PC CPU % of any second in the span) and {@code gameBusyPct} (the highest
 * {@link Fmt#busyPct} of any second in the span). Every one of them is -1 when there is no data.
 *
 * <p><b>{@code gcPauseMs} has two meanings that must not be mixed up.</b> It is the longest known pause touching
 * the span, {@code GcRing.longestPauseMs(startSec x 1000, (endSec + 1) x 1000 - 1)}: the span's first to its last
 * ms, both included. 0 = measured, and no pause touched the span. -1 = pauses cannot be known (the settings' memory
 * source is {@link MemorySource#RUNTIME}); on {@link MemorySource#MANAGEMENT} it is never -1. The panel shows them
 * apart: "pause 0 ms" against "pause n/a".
 *
 * <p><b>The id</b> is 0, 1, 2 ... in the order the detector opened the events, from one counter of the detector's
 * own that nothing restarts (not a hop, a world change, a login or a lost connection). An event keeps it from
 * opened to closed, and no id is reused, so -1 is free to mean "no event" ({@link Verdict#eventId}).
 * {@link EventLog#replace} and {@link EventLog#byId} find an event by it (contract 3.4, "The id").
 *
 * <p>An OPEN event sits in the {@link EventLog} with {@code open == true} until it closes; every reader of closed
 * events skips it. {@code verdict} is null until the event is judged.
 */
public final class LagEvent
{
	public final long id, startSec, endSec, startWallMs;
	/** {@link Trigger} bits of every trigger that fired in the event. */
	public final int triggers;
	/** The trigger that opened it. */
	public final Trigger first;
	/** -1 = no data; {@code gcPauseMs}: 0 = measured and none, -1 = cannot be known (see the class notes). */
	public final int world, region, players, npcs, fps, worstFrameMs, meanTickGapMs, worstTickGapMs,
		worstCorrectedTickMs, rttMs, rttMaxMs, rttBeforeMs, sentUnits, resentUnits, gcPauseMs, heapUsedMb,
		heapMaxMb, sysCpuPct, gameBusyPct;
	public final boolean open, becameCondition;
	/** Null until judged. */
	public final Verdict verdict;

	public LagEvent(long id, long startSec, long endSec, long startWallMs, int triggers, Trigger first,
		int world, int region, int players, int npcs, int fps, int worstFrameMs, int meanTickGapMs,
		int worstTickGapMs, int worstCorrectedTickMs, int rttMs, int rttMaxMs, int rttBeforeMs, int sentUnits,
		int resentUnits, int gcPauseMs, int heapUsedMb, int heapMaxMb, int sysCpuPct, int gameBusyPct,
		boolean open, boolean becameCondition, Verdict verdict)
	{
		this.id = id;
		this.startSec = startSec;
		this.endSec = endSec;
		this.startWallMs = startWallMs;
		this.triggers = triggers;
		this.first = first;
		this.world = world;
		this.region = region;
		this.players = players;
		this.npcs = npcs;
		this.fps = fps;
		this.worstFrameMs = worstFrameMs;
		this.meanTickGapMs = meanTickGapMs;
		this.worstTickGapMs = worstTickGapMs;
		this.worstCorrectedTickMs = worstCorrectedTickMs;
		this.rttMs = rttMs;
		this.rttMaxMs = rttMaxMs;
		this.rttBeforeMs = rttBeforeMs;
		this.sentUnits = sentUnits;
		this.resentUnits = resentUnits;
		this.gcPauseMs = gcPauseMs;
		this.heapUsedMb = heapUsedMb;
		this.heapMaxMb = heapMaxMb;
		this.sysCpuPct = sysCpuPct;
		this.gameBusyPct = gameBusyPct;
		this.open = open;
		this.becameCondition = becameCondition;
		this.verdict = verdict;
	}

	/** {@code endSec - startSec + 1}. */
	public int lengthS()
	{
		return (int) (endSec - startSec + 1);
	}

	/** True when {@code t} fired in the event. */
	public boolean has(Trigger t)
	{
		return (triggers & t.bit()) != 0;
	}

	/** What the event is counted under: its verdict's cause's group, or {@link Group#UNSURE} while unjudged. */
	public Group group()
	{
		return verdict == null ? Group.UNSURE : verdict.cause.group();
	}

	/** The same event with {@code v} as its verdict; every other field is copied. */
	public LagEvent withVerdict(Verdict v)
	{
		return new LagEvent(id, startSec, endSec, startWallMs, triggers, first, world, region, players, npcs, fps,
			worstFrameMs, meanTickGapMs, worstTickGapMs, worstCorrectedTickMs, rttMs, rttMaxMs, rttBeforeMs,
			sentUnits, resentUnits, gcPauseMs, heapUsedMb, heapMaxMb, sysCpuPct, gameBusyPct, open,
			becameCondition, v);
	}
}
