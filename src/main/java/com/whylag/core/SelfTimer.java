package com.whylag.core;

import java.util.concurrent.atomic.AtomicLongArray;

/**
 * The plugin's own cost on its three paths, for developer mode (contract 3.9): the frame and tick handlers on the
 * client thread, the one-second step on the sampler thread.
 *
 * <p>{@link #add} records whatever it is given; the caller asks {@link #on()} first and times the path only when it
 * is on. It allocates nothing, takes no lock and may be called from any thread (atomic counters), so it is safe on
 * the frame path. {@link #footer()} builds the developer-mode line of the panel.
 */
public final class SelfTimer
{
	public static final int FRAME = 0, TICK = 1, STEP = 2;

	private static final int PATHS = 3;
	private static final long NANOS_PER_MICRO = 1_000L;
	private static final long NANOS_PER_MILLI = 1_000_000L;

	private final AtomicLongArray count = new AtomicLongArray(PATHS);
	private final AtomicLongArray sum = new AtomicLongArray(PATHS);
	private final AtomicLongArray max = new AtomicLongArray(PATHS);
	private volatile boolean on;

	/** True while timing is wanted. */
	public boolean on()
	{
		return on;
	}

	public void on(boolean v)
	{
		on = v;
	}

	/** One timing of {@code path} ({@link #FRAME}, {@link #TICK} or {@link #STEP}); another path is ignored. */
	public void add(int path, long nanos)
	{
		if (path < 0 || path >= PATHS)
		{
			return;
		}
		count.incrementAndGet(path);
		sum.addAndGet(path, nanos);
		long seen = max.get(path);
		while (nanos > seen && !max.compareAndSet(path, seen, nanos))
		{
			seen = max.get(path);
		}
	}

	/** Timings recorded for {@code path} since the last reset; 0 for another path. */
	public long count(int path)
	{
		return path < 0 || path >= PATHS ? 0 : count.get(path);
	}

	/** The mean timing of {@code path}, 0 when none. */
	public long meanNanos(int path)
	{
		if (path < 0 || path >= PATHS)
		{
			return 0;
		}
		final long n = count.get(path);
		return n == 0 ? 0 : sum.get(path) / n;
	}

	/** The longest timing of {@code path}, 0 when none. */
	public long maxNanos(int path)
	{
		return path < 0 || path >= PATHS ? 0 : max.get(path);
	}

	/** Forgets every timing. */
	public void reset()
	{
		for (int path = 0; path < PATHS; path++)
		{
			count.set(path, 0);
			sum.set(path, 0);
			max.set(path, 0);
		}
	}

	/**
	 * The means as one line, "self: frame 180 ns, tick 2 us, step 40 us": under 1 us in ns, under 1 ms in whole us,
	 * else in whole ms. A path with no timing yet reads "-".
	 */
	public String footer()
	{
		return "self: frame " + text(FRAME) + ", tick " + text(TICK) + ", step " + text(STEP);
	}

	private String text(int path)
	{
		if (count.get(path) == 0)
		{
			return "-";
		}
		final long mean = meanNanos(path);
		if (mean < NANOS_PER_MICRO)
		{
			return mean + " ns";
		}
		if (mean < NANOS_PER_MILLI)
		{
			return (mean / NANOS_PER_MICRO) + " us";
		}
		return (mean / NANOS_PER_MILLI) + " ms";
	}
}
