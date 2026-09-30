package com.whylag.core;

import java.lang.invoke.VarHandle;

/**
 * Memory collections as INTERVALS in session ms (contract 3.3, skeptic C7), numbered by a sequence from 0.
 *
 * <p>A row is a collection that started at {@code startMs} (session ms: since {@link Session#startNanos}, never
 * wall time) and paused the game for {@code durationMs}; a duration of -1 marks an INFERRED collection, seen only
 * as a fall of the heap, which has no length and so can never be blamed for a freeze. {@code heapAfterMb} is the
 * heap left after it.
 *
 * <p><b>Clamps</b> (the clamp table of contract 3.3): a put never throws for a value. Every negative
 * {@code durationMs} is stored as -1 (inferred); {@code startMs} (a {@code long}: session ms never wrap) and
 * {@code heapAfterMb} are kept whole.
 *
 * <p>{@link #put} is synchronized because two threads can write for a moment (the JVM's notification thread and
 * the sampler, while the memory source is swapped); it runs once per collection. Reading takes no lock: the same
 * rules as {@link SecondRing} apply to the sequence - one slot of slack ({@link #tail()} is
 * {@code max(0, head - capacity + 2)}), and a read counts only when {@link #valid(long)} holds after it. The span
 * queries below read that way themselves.
 *
 * <p><b>Spans are closed.</b> A span is the stretch of time from {@code fromMs} to {@code toMs}, BOTH included; a
 * pause TOUCHES it when the two share at least one millisecond point ({@code start <= toMs} and
 * {@code start + duration >= fromMs}). So a span of whole seconds {@code a .. b} is passed as its first and last
 * ms, {@code a * 1000 .. (b + 1) * 1000 - 1} (contract 3.3): a pause that STARTS at the first ms of second
 * {@code b + 1} is not in it, and a pause that ends in the next second is found from either second.
 * {@link #overlapMs} measures a covered LENGTH on the time line ({@code min(ends) - max(starts)}); the contract
 * asks it only over a frame's own interval (contract 6.3).
 */
public final class GcRing
{
	private final int capacity;

	private final long[] startMs;
	private final int[] durationMs;
	private final int[] heapAfterMb;

	private volatile long head = -1;

	/**
	 * @param capacity slots, at least 2; {@code capacity - 1} collections are readable
	 */
	public GcRing(int capacity)
	{
		if (capacity < 2)
		{
			throw new IllegalArgumentException("a ring needs at least 2 slots, got " + capacity);
		}
		this.capacity = capacity;
		startMs = new long[capacity];
		durationMs = new int[capacity];
		heapAfterMb = new int[capacity];
	}

	/**
	 * Appends one collection.
	 *
	 * @param startMs     session ms when it began
	 * @param durationMs  its pause in ms, or -1 when inferred (any negative is stored as -1)
	 * @param heapAfterMb the heap left after it, -1 unknown
	 */
	public synchronized void put(long startMs, int durationMs, int heapAfterMb)
	{
		final long seq = head + 1;
		VarHandle.storeStoreFence();
		final int i = slot(seq);
		this.startMs[i] = startMs;
		this.durationMs[i] = durationMs < 0 ? -1 : durationMs;
		this.heapAfterMb[i] = heapAfterMb;
		head = seq;
	}

	/** The sequence of the newest collection; -1 before the first. */
	public long head()
	{
		return head;
	}

	/** The oldest readable sequence: {@code max(0, head - capacity + 2)}. */
	public long tail()
	{
		return Math.max(0, head - capacity + 2);
	}

	/** {@code tail() <= seq <= head()}. Starts with an acquire fence, as {@link SecondRing#valid(long)} does. */
	public boolean valid(long seq)
	{
		VarHandle.acquireFence();
		final long h = head;
		return seq >= Math.max(0, h - capacity + 2) && seq <= h;
	}

	public long startMs(long seq)
	{
		return startMs[slot(seq)];
	}

	/** The pause in ms; -1 for an inferred collection. */
	public int durationMs(long seq)
	{
		return durationMs[slot(seq)];
	}

	public int heapAfterMb(long seq)
	{
		return heapAfterMb[slot(seq)];
	}

	/**
	 * The LENGTH of the longest known pause that touches the span; 0 when none does. Inferred rows add nothing. The
	 * event reads it over its span (contract 3.4), the memory tile over the window, which ends with the newest
	 * second (contract 5.2), and the GC_PAUSE trigger over one second (contract 6.2); each of those spans ends at
	 * the LAST ms of its last second, so a pause that starts after that second is never counted.
	 */
	public int longestPauseMs(long fromMs, long toMs)
	{
		int best = 0;
		final long newest = head;
		for (long seq = Math.max(0, newest - capacity + 2); seq <= newest; seq++)
		{
			final int i = slot(seq);
			final long start = startMs[i];
			final int length = durationMs[i];
			if (!valid(seq))
			{
				continue;
			}
			if (length >= 0 && start <= toMs && start + length >= fromMs && length > best)
			{
				best = length;
			}
		}
		return best;
	}

	/**
	 * The most ms of the span covered by ONE known pause; 0 when none covers any. A 150 ms pause with 50 ms of it
	 * inside the span answers 50. Inferred rows add nothing.
	 */
	public int overlapMs(long fromMs, long toMs)
	{
		long best = 0;
		final long newest = head;
		for (long seq = Math.max(0, newest - capacity + 2); seq <= newest; seq++)
		{
			final int i = slot(seq);
			final long start = startMs[i];
			final int length = durationMs[i];
			if (!valid(seq))
			{
				continue;
			}
			if (length >= 0)
			{
				final long covered = Math.min(start + length, toMs) - Math.max(start, fromMs);
				if (covered > best)
				{
					best = covered;
				}
			}
		}
		return (int) Math.min(Integer.MAX_VALUE, best);
	}

	/** True when an inferred collection started inside the span. */
	public boolean inferredIn(long fromMs, long toMs)
	{
		final long newest = head;
		for (long seq = Math.max(0, newest - capacity + 2); seq <= newest; seq++)
		{
			final int i = slot(seq);
			final long start = startMs[i];
			final int length = durationMs[i];
			if (valid(seq) && length < 0 && start >= fromMs && start <= toMs)
			{
				return true;
			}
		}
		return false;
	}

	/**
	 * The heap left by the newest collection WRITTEN (known or inferred); -1 before the first. That is not always
	 * the newest by start time (a row can land late), and it looks past any second: the memory tile and the memory
	 * lane read {@link #heapAfterAt} at the end of their second instead (contract 5.2, 5.3).
	 */
	public int lastHeapAfterMb()
	{
		while (true)
		{
			final long newest = head;
			if (newest < 0)
			{
				return -1;
			}
			final int heap = heapAfterMb[slot(newest)];
			if (valid(newest))
			{
				return heap;
			}
		}
	}

	/**
	 * The heap left by the newest collection that STARTED at or before {@code ms} (known or inferred), carried
	 * forward until the next one; -1 when none had started by then. Newest by start time, so a row written late
	 * still lands in its place.
	 */
	public int heapAfterAt(long ms)
	{
		long bestStart = Long.MIN_VALUE;
		int bestHeap = -1;
		boolean found = false;
		final long newest = head;
		for (long seq = Math.max(0, newest - capacity + 2); seq <= newest; seq++)
		{
			final int i = slot(seq);
			final long start = startMs[i];
			final int heap = heapAfterMb[i];
			if (!valid(seq))
			{
				continue;
			}
			if (start <= ms && (!found || start >= bestStart))
			{
				bestStart = start;
				bestHeap = heap;
				found = true;
			}
		}
		return bestHeap;
	}

	private int slot(long seq)
	{
		return (int) (seq % capacity);
	}
}
