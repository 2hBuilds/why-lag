package com.whylag.core;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import org.junit.Test;
import static org.junit.Assert.assertEquals;

/**
 * Number and clock text (contract 3.2).
 */
public class FmtTest
{
	private static final long T = Instant.parse("2026-09-28T21:47:30Z").toEpochMilli();

	/** The range row's note while the graphs are stretched (the first live look): seconds, then whole minutes. */
	@Test
	public void held()
	{
		assertEquals("0 s", Fmt.held(0));
		assertEquals("0 s", Fmt.held(-5));
		assertEquals("30 s", Fmt.held(30_999));
		assertEquals("59 s", Fmt.held(59_999));
		assertEquals("1 min", Fmt.held(60_000));
		assertEquals("4 min", Fmt.held(299_999));
		assertEquals("59 min", Fmt.held(3_599_999));
	}

	@Test
	public void thousands()
	{
		assertEquals("0", Fmt.thousands(0));
		assertEquals("999", Fmt.thousands(999));
		assertEquals("1,000", Fmt.thousands(1000));
		assertEquals("1,240", Fmt.thousands(1240));
		assertEquals("9,999", Fmt.thousands(9999));
		assertEquals("1,234,567", Fmt.thousands(1_234_567));
		assertEquals("-1,240", Fmt.thousands(-1240));
		assertEquals("-7", Fmt.thousands(-7));
		assertEquals("-2,147,483,648", Fmt.thousands(Integer.MIN_VALUE));
	}

	@Test
	public void tenths()
	{
		assertEquals("0.9", Fmt.tenths(900));
		assertEquals("1.8", Fmt.tenths(1840));
		assertEquals("2.4", Fmt.tenths(2400));
		assertEquals("0.0", Fmt.tenths(0));
		assertEquals("rounded to the nearest tenth", "1.9", Fmt.tenths(1850));
		assertEquals("1.8", Fmt.tenths(1849));
		assertEquals("10.0", Fmt.tenths(9999));
		assertEquals("-0.9", Fmt.tenths(-900));
		assertEquals("0.0", Fmt.tenths(-40));
	}

	@Test
	public void clock()
	{
		assertEquals("21:47", Fmt.clock(T, ZoneOffset.UTC));
		assertEquals("21:47:30", Fmt.clockSeconds(T, ZoneOffset.UTC));
		assertEquals("in the player's zone", "17:47", Fmt.clock(T, ZoneId.of("America/Toronto")));
		assertEquals("00:05:09", Fmt.clockSeconds(Instant.parse("2026-09-29T00:05:09Z").toEpochMilli(),
			ZoneOffset.UTC));
		assertEquals("a missing zone reads as UTC", "21:47", Fmt.clock(T, null));
	}

	@Test
	public void ago()
	{
		assertEquals("now", Fmt.ago(T, T));
		assertEquals("now", Fmt.ago(T, T + 999));
		assertEquals("a time in the future is now", "now", Fmt.ago(T + 5000, T));
		assertEquals("1 s ago", Fmt.ago(T, T + 1000));
		assertEquals("40 s ago", Fmt.ago(T, T + 40_000));
		assertEquals("59 s ago", Fmt.ago(T, T + 59_999));
		assertEquals("1 min ago", Fmt.ago(T, T + 60_000));
		assertEquals("4 min ago", Fmt.ago(T, T + 4 * 60_000 + 59_000));
	}

	@Test
	public void agoTurnsToHoursAtSixtyMinutes()
	{
		assertEquals("59 min ago", Fmt.ago(T, T + 59 * 60_000 + 59_999));
		assertEquals("1 h ago", Fmt.ago(T, T + 60 * 60_000));
		assertEquals("3 h ago", Fmt.ago(T, T + 3 * 3_600_000 + 59 * 60_000));
		assertEquals("25 h ago", Fmt.ago(T, T + 25 * 3_600_000L));
	}

	@Test
	public void lags()
	{
		assertEquals("0 lags", Fmt.lags(0));
		assertEquals("1 lag", Fmt.lags(1));
		assertEquals("4 lags", Fmt.lags(4));
		assertEquals("1,240 lags", Fmt.lags(1240));
	}

	/** Word, space, number, space, "%": the two halves of the CPU lane's value. */
	@Test
	public void pct()
	{
		assertEquals("PC 37 %", Fmt.pct("PC", 37));
		assertEquals("Game 100 %", Fmt.pct("Game", 100));
		assertEquals("Game 95 %", Fmt.pct("Game", 95));
		assertEquals("PC 0 %", Fmt.pct("PC", 0));
	}

	/** Per mille to per cent, rounded down and held at 100; any negative is -1, "no data". */
	@Test
	public void busyPct()
	{
		assertEquals(-1, Fmt.busyPct(-1));
		assertEquals(-1, Fmt.busyPct(-40));
		assertEquals(0, Fmt.busyPct(0));
		assertEquals(0, Fmt.busyPct(9));
		assertEquals(40, Fmt.busyPct(400));
		assertEquals(95, Fmt.busyPct(955));
		assertEquals(100, Fmt.busyPct(1000));
		assertEquals(100, Fmt.busyPct(1400));
		assertEquals(100, Fmt.busyPct(Integer.MAX_VALUE));
	}

	@Test
	public void clamp()
	{
		assertEquals(5, Fmt.clamp(5, 0, 10));
		assertEquals(0, Fmt.clamp(-3, 0, 10));
		assertEquals(10, Fmt.clamp(11, 0, 10));
		assertEquals(-1, Fmt.clamp(-9, -1, 32767));
	}
}
