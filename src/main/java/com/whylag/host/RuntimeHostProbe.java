package com.whylag.host;

import com.whylag.core.MemorySource;

/**
 * The fallback memory source (contract 7, L7): the JVM's {@link Runtime} and nothing else - the heap in use, the heap
 * limit and the number of cores. It adds no collection listener, so pauses cannot be known
 * ({@link MemorySource#RUNTIME}: the engine infers a collection from a fall of the heap, with no length, and wave one
 * never names memory as a cause), and it has no CPU readings: both CPU answers and the thread clock are -1.
 *
 * <p>This is what a Runtime-only build keeps when a reviewer refuses the management beans: {@code HostProbes} answers
 * it whenever {@code systemStats} is off or the management probe cannot be made. It never throws.
 *
 * <p>Choice: sizes are whole MB (1,048,576 bytes), rounded down, so 768 is what {@code -Xmx768m} reads.
 * <p>Choice: a heap limit the Runtime calls unlimited ({@code Long.MAX_VALUE}) answers -1, unknown (contract 3.7).
 */
public final class RuntimeHostProbe implements HostProbe
{
	/** One MB, a unit conversion. */
	private static final long BYTES_PER_MB = 1024L * 1024L;

	@Override
	public MemorySource source()
	{
		return MemorySource.RUNTIME;
	}

	/** Adds nothing: the Runtime has no collection listener. Always false. */
	@Override
	public boolean start(GcSink sink, long sessionStartNanos)
	{
		return false;
	}

	/** Nothing was added, so nothing is removed. Safe to call any number of times. */
	@Override
	public void stop()
	{
	}

	@Override
	public int heapUsedMb()
	{
		final Runtime runtime = Runtime.getRuntime();
		return mb(runtime.totalMemory() - runtime.freeMemory());
	}

	@Override
	public int heapMaxMb()
	{
		return limitMb(Runtime.getRuntime().maxMemory());
	}

	/** No CPU reading without the management beans. */
	@Override
	public int processCpuPct()
	{
		return -1;
	}

	/** No CPU reading without the management beans. */
	@Override
	public int systemCpuPct()
	{
		return -1;
	}

	@Override
	public int cores()
	{
		return Runtime.getRuntime().availableProcessors();
	}

	/** No thread clock without the management beans. Allocates nothing. */
	@Override
	public long currentThreadCpuNanos()
	{
		return -1;
	}

	/** Bytes as whole MB, rounded down and held under {@code Integer.MAX_VALUE}; a negative count is -1. */
	static int mb(long bytes)
	{
		if (bytes < 0)
		{
			return -1;
		}
		return (int) Math.min(Integer.MAX_VALUE, bytes / BYTES_PER_MB);
	}

	/** A heap limit in MB; -1 (unknown) when it is 0 or less, or the JVM calls it unlimited. */
	static int limitMb(long maxBytes)
	{
		if (maxBytes <= 0 || maxBytes == Long.MAX_VALUE)
		{
			return -1;
		}
		final int limit = mb(maxBytes);
		return limit > 0 ? limit : -1;
	}
}
