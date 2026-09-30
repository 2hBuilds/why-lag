package com.whylag.core;

import org.junit.Test;
import static org.junit.Assert.assertEquals;

/** Each line of the tile level rules of contract 5.2, AT the line and one below it. */
public class LevelsTest
{
	@Test
	public void frameRateBadUnderTwentyFive()
	{
		assertEquals(Level.WARN, Levels.fps(Thresholds.FPS_BAD, 0, false));
		assertEquals(Level.BAD, Levels.fps(Thresholds.FPS_BAD - 1, 0, false));
		assertEquals(Level.BAD, Levels.fps(0, 0, false));
	}

	@Test
	public void frameRateWarnUnderForty()
	{
		assertEquals(Level.OK, Levels.fps(Thresholds.FPS_WARN, 0, false));
		assertEquals(Level.WARN, Levels.fps(Thresholds.FPS_WARN - 1, 0, false));
		assertEquals(Level.OK, Levels.fps(50, 50, false));
	}

	@Test
	public void frameRateIsOkAtASelfSetCap()
	{
		// FPS Control at 20: 18 is within 2 of it (at the line), 17 is one below.
		assertEquals(Level.OK, Levels.fps(20, 20, true));
		assertEquals(Level.OK, Levels.fps(20 - Thresholds.CAP_MATCH_FPS, 20, true));
		assertEquals(Level.BAD, Levels.fps(20 - Thresholds.CAP_MATCH_FPS - 1, 20, true));
		assertEquals("above the cap by 2 counts too", Level.OK, Levels.fps(22, 20, true));
		assertEquals("a cap the player did not set excuses nothing", Level.BAD, Levels.fps(20, 20, false));
		assertEquals("no cap known", Level.BAD, Levels.fps(0, 0, true));
		// At 30 the capped rate would be WARN; within the cap it reads OK.
		assertEquals(Level.OK, Levels.fps(29, 30, true));
		assertEquals(Level.WARN, Levels.fps(27, 30, true));
	}

	@Test
	public void frameRateWithNoDataIsNoData()
	{
		assertEquals(Level.NO_DATA, Levels.fps(-1, 50, true));
	}

	@Test
	public void ticksBadAtTwoHundred()
	{
		assertEquals(Level.BAD, Levels.tick(Thresholds.TICK_BAD_MS));
		assertEquals(Level.WARN, Levels.tick(Thresholds.TICK_BAD_MS - 1));
	}

	@Test
	public void ticksWarnAtSixty()
	{
		assertEquals(Level.WARN, Levels.tick(Thresholds.TICK_WARN_MS));
		assertEquals(Level.OK, Levels.tick(Thresholds.TICK_WARN_MS - 1));
		assertEquals(Level.OK, Levels.tick(0));
		assertEquals(Level.NO_DATA, Levels.tick(-1));
	}

	@Test
	public void pingBadAtOneFifty()
	{
		assertEquals(Level.BAD, Levels.ping(Thresholds.PING_BAD_MS));
		assertEquals(Level.WARN, Levels.ping(Thresholds.PING_BAD_MS - 1));
	}

	@Test
	public void pingWarnAtEighty()
	{
		assertEquals(Level.WARN, Levels.ping(Thresholds.PING_WARN_MS));
		assertEquals(Level.OK, Levels.ping(Thresholds.PING_WARN_MS - 1));
		assertEquals(Level.OK, Levels.ping(0));
		assertEquals(Level.NO_DATA, Levels.ping(-1));
	}
}
