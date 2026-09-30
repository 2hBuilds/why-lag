package com.whylag.core;

/**
 * The level rules of contract 5.2 and 5.3, pure and static, so the snapshot builder and {@link EventView} agree
 * without calling each other: the three tiles. A line is included in the level it names: a ping of exactly
 * {@link Thresholds#PING_BAD_MS} is BAD. A negative reading is no data.
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
}
