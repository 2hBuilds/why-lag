package com.whylag.core;

/**
 * One tick as the client thread saw it (contract 3.3): a mutable carrier, allocated once and reused, which
 * {@link TickRing#put} copies.
 *
 * <p>{@code atMs} is milliseconds since {@link Session#startNanos}, a {@code long} like every session ms (an int
 * would wrap after 24.8 days of uptime); {@code gapMs} the time since the tick before, 0 .. {@code Integer.MAX_VALUE};
 * {@code frameMs} the frame time that belongs to the tick - the larger of the worst CLOSED frame interval inside
 * its gap and the frame still OPEN when it arrived; {@code cycleJump} how far the game cycle moved; {@code rttMs}
 * the latest RTT when it arrived (-1 none); {@code flags} {@link Flags} bits.
 */
public final class TickRow
{
	public long atMs;
	public int gapMs, frameMs, cycleJump, rttMs, flags;
}
