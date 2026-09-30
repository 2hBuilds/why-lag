package com.whylag.core;

import java.util.Locale;

/**
 * The operating system, for the one place it changes a number (contract 3.2, skeptic C1): the TCP counters of
 * {@code Ping.getTCPInfo} count BYTES on Windows and SEGMENTS elsewhere, so the least traffic before re-sends
 * count and the size of a "click jump" differ by system.
 */
public enum Os
{
	WINDOWS,
	MAC,
	LINUX,
	OTHER;

	/**
	 * The system named by {@code System.getProperty("os.name")}. Mac is tested first because "Darwin" contains
	 * "win", the order RuneLite's own {@code OSType} uses. A null or unknown name answers {@link #OTHER}.
	 */
	public static Os of(String osName)
	{
		if (osName == null)
		{
			return OTHER;
		}
		final String name = osName.toLowerCase(Locale.ROOT);
		if (name.contains("mac") || name.contains("darwin"))
		{
			return MAC;
		}
		if (name.contains("win"))
		{
			return WINDOWS;
		}
		if (name.contains("linux"))
		{
			return LINUX;
		}
		return OTHER;
	}

	/** True on Windows only: its counters are bytes, every other system's are segments (C1). */
	public boolean countsBytes()
	{
		return this == WINDOWS;
	}

	/**
	 * Least sent in the {@code RESENT_WINDOW_S} window before re-sends count: {@code RESENT_MIN_BYTES} or
	 * {@code RESENT_MIN_UNITS}.
	 */
	public int resentMinSent()
	{
		return countsBytes() ? Thresholds.RESENT_MIN_BYTES : Thresholds.RESENT_MIN_UNITS;
	}

	/** A jump in sent that voids an RTT spike: {@code CLICK_SENT_BYTES} or {@code CLICK_SENT_UNITS}. */
	public int clickSent()
	{
		return countsBytes() ? Thresholds.CLICK_SENT_BYTES : Thresholds.CLICK_SENT_UNITS;
	}
}
