package com.whylag.core;

/**
 * The level rules of contract 5.2 and 5.3, pure and static, so the snapshot builder and {@link EventView} agree
 * without calling each other: the four tiles, and the CPU lane. A line is included in the level it names: a ping of
 * exactly {@link Thresholds#PING_BAD_MS} is BAD. A negative reading is no data - except in {@link #memory}, which
 * never answers NO_DATA.
 */
public final class Levels
{
	private Levels()
	{
	}

	/**
	 * Frame rate: under {@link Thresholds#FPS_BAD} BAD, under {@link Thresholds#FPS_WARN} WARN - but OK when a cap
	 * the player set is in force ({@code capSelfSet}, {@code capFps > 0}) and the rate is within
	 * {@link Thresholds#CAP_MATCH_FPS} of it. {@code fps} under 0: NO_DATA.
	 */
	public static Level fps(int fps, int capFps, boolean capSelfSet)
	{
		if (fps < 0)
		{
			return Level.NO_DATA;
		}
		if (capSelfSet && capFps > 0 && Math.abs(fps - capFps) <= Thresholds.CAP_MATCH_FPS)
		{
			return Level.OK;
		}
		if (fps < Thresholds.FPS_BAD)
		{
			return Level.BAD;
		}
		return fps < Thresholds.FPS_WARN ? Level.WARN : Level.OK;
	}

	/**
	 * Server ticks, on the largest CORRECTED deviation from 600: {@link Thresholds#TICK_BAD_MS} or more BAD,
	 * {@link Thresholds#TICK_WARN_MS} or more WARN. Under 0: NO_DATA.
	 */
	public static Level tick(int correctedDeviationMs)
	{
		if (correctedDeviationMs < 0)
		{
			return Level.NO_DATA;
		}
		if (correctedDeviationMs >= Thresholds.TICK_BAD_MS)
		{
			return Level.BAD;
		}
		return correctedDeviationMs >= Thresholds.TICK_WARN_MS ? Level.WARN : Level.OK;
	}

	/** Ping: {@link Thresholds#PING_BAD_MS} or more BAD, {@link Thresholds#PING_WARN_MS} or more WARN. Under 0: NO_DATA. */
	public static Level ping(int rttMs)
	{
		if (rttMs < 0)
		{
			return Level.NO_DATA;
		}
		if (rttMs >= Thresholds.PING_BAD_MS)
		{
			return Level.BAD;
		}
		return rttMs >= Thresholds.PING_WARN_MS ? Level.WARN : Level.OK;
	}

	/**
	 * Memory: the WORSE of two halves - heap AFTER the last collection, in % of the limit, against
	 * {@link Thresholds#HEAP_WARN_PCT} / {@link Thresholds#HEAP_BAD_PCT}, and the longest known pause against
	 * {@link Thresholds#GC_WARN_MS} / {@link Thresholds#GC_BAD_MS}. A half at -1 is left out:
	 * <ul>
	 * <li>{@code heapAfterPct} -1: there is no heap-after-collection figure. That is so before the first
	 * collection, and ALWAYS for a selected event (an event carries its used heap, which is no sign of trouble).</li>
	 * <li>{@code longestPauseMs} -1: pauses CANNOT be known (the {@link MemorySource#RUNTIME} source). 0 is a
	 * measured fact, "no pause", and gives OK for that half.</li>
	 * </ul>
	 * With both halves left out the answer is OK: nothing measured speaks against memory. It NEVER answers NO_DATA,
	 * so a tile with a real heap figure never draws the hollow ring; the dash is the caller's, when it has no value.
	 */
	public static Level memory(int heapAfterPct, int longestPauseMs)
	{
		Level heap = Level.OK;
		if (heapAfterPct >= 0)
		{
			heap = heapAfterPct >= Thresholds.HEAP_BAD_PCT ? Level.BAD
				: heapAfterPct >= Thresholds.HEAP_WARN_PCT ? Level.WARN : Level.OK;
		}
		Level pause = Level.OK;
		if (longestPauseMs >= 0)
		{
			pause = longestPauseMs >= Thresholds.GC_BAD_MS ? Level.BAD
				: longestPauseMs >= Thresholds.GC_WARN_MS ? Level.WARN : Level.OK;
		}
		return pause.ordinal() > heap.ordinal() ? pause : heap;
	}

	/**
	 * The CPU lane, on the whole PC's use: {@link Thresholds#CPU_BAD_PCT} or more BAD,
	 * {@link Thresholds#CPU_WARN_PCT} or more WARN. Under 0 (-1: no data): NO_DATA. The game thread's share has no
	 * level: a busy game thread is normal with an unlocked frame rate.
	 */
	public static Level cpu(int sysCpuPct)
	{
		if (sysCpuPct < 0)
		{
			return Level.NO_DATA;
		}
		if (sysCpuPct >= Thresholds.CPU_BAD_PCT)
		{
			return Level.BAD;
		}
		return sysCpuPct >= Thresholds.CPU_WARN_PCT ? Level.WARN : Level.OK;
	}
}
