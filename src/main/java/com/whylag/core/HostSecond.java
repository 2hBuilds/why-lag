package com.whylag.core;

/**
 * The sampler thread's half of one second (contract 3.3): a mutable carrier, allocated once and reused, which
 * {@link SecondRing#putHost} copies into its columns. -1 is "no data" in every int but the two unit counters;
 * {@link #conn} says why the ping is missing ({@link NoData#NONE} when it is not).
 *
 * <p><b>A fresh RTT</b> (contract 3.3): {@code rttMs >= 0} and {@code 0 <= rttAgeS <= RTT_STALE_S}. The writer keeps
 * {@link #conn} at {@link NoData#NONE} in exactly the seconds whose RTT is fresh, and writes
 * {@link NoData#STALE} for an RTT that is kept but older than {@link Thresholds#RTT_STALE_S}, or not there yet; so a
 * reader may test either and they agree. A cleared carrier is only a starting point: the writer fills it.
 */
public final class HostSecond
{
	public int rttMs, rttAgeS, sentUnits, resentUnits, heapUsedMb, procCpuPct, sysCpuPct;
	public NoData conn;

	/** A cleared carrier (see {@link #clear()}). */
	public HostSecond()
	{
		clear();
	}

	/** Every int -1, except {@code sentUnits = resentUnits = 0}; {@code conn = NONE}. */
	public void clear()
	{
		rttMs = -1;
		rttAgeS = -1;
		sentUnits = 0;
		resentUnits = 0;
		heapUsedMb = -1;
		procCpuPct = -1;
		sysCpuPct = -1;
		conn = NoData.NONE;
	}
}
