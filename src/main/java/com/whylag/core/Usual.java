package com.whylag.core;

import java.util.Arrays;

/**
 * A running median of ints 0 .. {@code max} (contract 3.5): what is "usual" for this player, such as the RTT of
 * this world. Every sample since the last {@link #reset()} counts; each value is kept as a count in one
 * {@code int[max + 1]} allocated once, so adding never allocates. Every method is synchronized.
 *
 * <p>The detector ALONE adds and resets (on the sampler thread); everyone else only reads {@link #median()}.
 */
public final class Usual
{
	private final int max;
	private final int[] counts;
	private int count;

	/**
	 * @param max the largest value kept; larger samples are clamped to it
	 */
	public Usual(int max)
	{
		if (max < 0)
		{
			throw new IllegalArgumentException("max must be 0 or more, got " + max);
		}
		this.max = max;
		counts = new int[max + 1];
	}

	/** One sample, clamped to 0 .. max. */
	public synchronized void add(int v)
	{
		counts[Fmt.clamp(v, 0, max)]++;
		count++;
	}

	/** Samples since the last reset. */
	public synchronized int count()
	{
		return count;
	}

	/**
	 * The median - the LOWER middle value when the count is even - or -1 while {@link #count()} is under
	 * {@link Thresholds#USUAL_MIN_SAMPLES}.
	 */
	public synchronized int median()
	{
		if (count < Thresholds.USUAL_MIN_SAMPLES)
		{
			return -1;
		}
		final int rank = (count - 1) / 2;
		int seen = 0;
		for (int v = 0; v <= max; v++)
		{
			seen += counts[v];
			if (seen > rank)
			{
				return v;
			}
		}
		return max;
	}

	/** Forgets every sample. */
	public synchronized void reset()
	{
		Arrays.fill(counts, 0);
		count = 0;
	}
}
