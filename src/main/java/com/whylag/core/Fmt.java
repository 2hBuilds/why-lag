package com.whylag.core;

import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZoneOffset;

/**
 * Number and clock text (contract 3.2). Every method is static and pure; every answer is plain ASCII.
 */
public final class Fmt
{
	private static final int PERCENT = 100;
	private static final int PER_MILLE_PER_PERCENT = 10;

	private Fmt()
	{
	}

	/** Digits grouped by commas: 1240 is "1,240", -1240 is "-1,240". */
	public static String thousands(int n)
	{
		final String digits = Long.toString(Math.abs((long) n));
		final StringBuilder out = new StringBuilder(digits.length() + 5);
		if (n < 0)
		{
			out.append('-');
		}
		int lead = digits.length() % 3;
		if (lead == 0)
		{
			lead = 3;
		}
		out.append(digits, 0, lead);
		for (int i = lead; i < digits.length(); i += 3)
		{
			out.append(',').append(digits, i, i + 3);
		}
		return out.toString();
	}

	/**
	 * Milliseconds as seconds with one decimal, rounded to the nearest tenth (half up): 900 is "0.9", 1840 is "1.8",
	 * 1850 is "1.9", 9999 is "10.0".
	 */
	public static String tenths(int ms)
	{
		final long tenthsOfASecond = (Math.abs((long) ms) + 50) / 100;
		final String text = (tenthsOfASecond / 10) + "." + (tenthsOfASecond % 10);
		return ms < 0 && tenthsOfASecond != 0 ? "-" + text : text;
	}

	/** The wall clock in {@code zone} as "21:47" (24 hours). A null zone reads as UTC. */
	public static String clock(long wallMs, ZoneId zone)
	{
		final LocalTime t = timeOf(wallMs, zone);
		return two(t.getHour()) + ":" + two(t.getMinute());
	}

	/** The wall clock in {@code zone} as "21:47:30" (24 hours). A null zone reads as UTC. */
	public static String clockSeconds(long wallMs, ZoneId zone)
	{
		final LocalTime t = timeOf(wallMs, zone);
		return two(t.getHour()) + ":" + two(t.getMinute()) + ":" + two(t.getSecond());
	}

	/**
	 * How long ago, in whole units, rounded down: "now" (under a second, or in the future), "40 s ago",
	 * "4 min ago", and from 60 minutes on whole hours, "3 h ago".
	 */
	public static String ago(long thenWallMs, long nowWallMs)
	{
		final long seconds = (nowWallMs - thenWallMs) / 1000;
		if (seconds < 1)
		{
			return "now";
		}
		if (seconds < 60)
		{
			return seconds + " s ago";
		}
		final long minutes = seconds / 60;
		if (minutes < 60)
		{
			return minutes + " min ago";
		}
		return (minutes / 60) + " h ago";
	}

	/**
	 * How much a stretched graph holds, rounded down: "30 s" under a minute, else "4 min" (at least "0 s"); the range
	 * row's note adds "so far".
	 */
	public static String held(long ms)
	{
		final long seconds = Math.max(0, ms / 1000);
		return seconds < 60 ? seconds + " s" : thousands((int) Math.min(Integer.MAX_VALUE, seconds / 60)) + " min";
	}

	/** "0 lags", "1 lag", "4 lags", "1,240 lags". */
	public static String lags(int n)
	{
		return thousands(n) + (n == 1 ? " lag" : " lags");
	}

	/**
	 * A share with its word: the word, a space, the number, a space, "%". {@code ("PC", 37)} is "PC 37 %",
	 * {@code ("Game", 100)} is "Game 100 %". The caller leaves out a share it does not know.
	 */
	public static String pct(String word, int pct)
	{
		return word + " " + pct + " %";
	}

	/**
	 * A per mille share as a whole per cent, rounded down and held at 100: under 0 is -1 ("no data"), else
	 * {@code min(100, busyPm / 10)}. 955 is 95, 1400 is 100.
	 */
	public static int busyPct(int busyPm)
	{
		if (busyPm < 0)
		{
			return -1;
		}
		return Math.min(PERCENT, busyPm / PER_MILLE_PER_PERCENT);
	}

	/** {@code v} held inside {@code lo .. hi}. */
	public static int clamp(int v, int lo, int hi)
	{
		return v < lo ? lo : (v > hi ? hi : v);
	}

	private static LocalTime timeOf(long wallMs, ZoneId zone)
	{
		return Instant.ofEpochMilli(wallMs).atZone(zone == null ? ZoneOffset.UTC : zone).toLocalTime();
	}

	private static String two(int v)
	{
		return v < 10 ? "0" + v : Integer.toString(v);
	}
}
