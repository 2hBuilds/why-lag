package com.whylag;

import com.whylag.core.NoData;

/**
 * One reading of the game's socket (contract 3.11): {@link ConnectionProbe#read} fills it on the sampler thread and
 * the plugin hands its four fields to the engine. Mutable and reused: one instance for the plugin's life.
 *
 * <p>With {@code conn} {@link NoData#NONE} the three numbers are the socket's own: the round trip time in
 * MICROseconds, and the cumulative sent and re-sent counters (bytes on Windows, segments elsewhere, contract 3.2).
 * Any other {@code conn} says why there is no data (contract 3.10: "conn says why there is no data"), and then the
 * three numbers are -1.
 *
 * <p>Choice: a new sample holds no data yet: the three numbers -1 and {@code conn} STALE, never null.
 */
public final class ConnSample
{
	/** Round trip time in microseconds, as {@code TCPInfo.getRTT()} answers it; -1 = no data. */
	public long rttMicros = -1;
	/** Cumulative sent (bytes on Windows, segments elsewhere); -1 = no data. */
	public long sent = -1;
	/** Cumulative re-sent, in the same unit; -1 = no data. */
	public long resent = -1;
	/** {@link NoData#NONE} when the numbers are the socket's; else why there are none. Never null. */
	public NoData conn = NoData.STALE;
}
