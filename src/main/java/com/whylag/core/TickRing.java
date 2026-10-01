package com.whylag.core;


/**
 * Every tick, in arrival order, numbered by a sequence from 0 (contract 3.3). The client thread writes; any thread
 * reads. Primitive arrays allocated once; no method allocates and none takes a lock.
 *
 * <p>The same rules as {@link SecondRing}, on the sequence: one slot of slack ({@link #tail()} is
 * {@code max(0, head - capacity + 2)}, so a ring of capacity {@code n + 1} keeps {@code n} readable ticks), and a
 * reader checks {@link #valid(long)} before and after it reads, throwing the read away when the second check
 * fails.
 *
 * <p><b>Clamps</b> (the clamp table of contract 3.3): every value is clamped to its column on write and a put never
 * throws for a value. {@code frameMs}, {@code cycleJump} and {@code rttMs} are shorts, -1 .. 32767, every negative
 * stored as -1; {@code flags} a byte, 0 .. 127; {@code atMs} (a {@code long}: session ms never wrap) and
 * {@code gapMs} are kept whole.
 */
public final class TickRing
{
	private final int capacity;

	private final long[] atMs;
	private final int[] gapMs;
	private final short[] frameMs;
	private final short[] cycleJump;
	private final short[] rttMs;
	private final byte[] flags;

	private volatile long head = -1;
	/** Written at the start of {@link #valid(long)} for its ordering only; never read. */
	private volatile int order;

	/**
	 * @param capacity slots, at least 2; {@code capacity - 1} ticks are readable
	 */
	public TickRing(int capacity)
	{
		if (capacity < 2)
		{
			throw new IllegalArgumentException("a ring needs at least 2 slots, got " + capacity);
		}
		this.capacity = capacity;
		atMs = new long[capacity];
		gapMs = new int[capacity];
		frameMs = new short[capacity];
		cycleJump = new short[capacity];
		rttMs = new short[capacity];
		flags = new byte[capacity];
	}

	/** Appends one tick as sequence {@code head() + 1}. Client thread only. */
	public void put(TickRow v)
	{
		final long seq = head + 1;
		// The slot about to be reused belongs to sequence seq - capacity, already below tail(). The volatile read of
		// head above keeps that published head ahead of the column stores below on every CPU.
		final int i = slot(seq);
		atMs[i] = v.atMs;
		gapMs[i] = v.gapMs;
		frameMs[i] = toShort(v.frameMs);
		cycleJump[i] = toShort(v.cycleJump);
		rttMs[i] = toShort(v.rttMs);
		flags[i] = (byte) Fmt.clamp(v.flags, 0, Byte.MAX_VALUE);
		head = seq;
	}

	/** The sequence of the newest tick; -1 before the first. */
	public long head()
	{
		return head;
	}

	/** The oldest readable sequence: {@code max(0, head - capacity + 2)}. */
	public long tail()
	{
		return Math.max(0, head - capacity + 2);
	}

	/** {@code tail() <= seq <= head()}. Starts with a volatile store, as {@link SecondRing#valid(long)} does. */
	public boolean valid(long seq)
	{
		order = 0;
		final long h = head;
		return seq >= Math.max(0, h - capacity + 2) && seq <= h;
	}

	/** Session ms when the tick arrived. */
	public long atMs(long seq)
	{
		return atMs[slot(seq)];
	}

	public int gapMs(long seq)
	{
		return gapMs[slot(seq)];
	}

	public int frameMs(long seq)
	{
		return frameMs[slot(seq)];
	}

	public int cycleJump(long seq)
	{
		return cycleJump[slot(seq)];
	}

	public int rttMs(long seq)
	{
		return rttMs[slot(seq)];
	}

	public int flags(long seq)
	{
		return flags[slot(seq)];
	}

	/**
	 * How far the tick was off, with the frame interval taken out (C5):
	 * {@code max(0, abs(gapMs - TICK_MS) - frameMs)}. A tick can only be handled on a frame, so up to one frame
	 * interval of lateness or earliness is the client's own pacing, not the server's.
	 */
	public int corrected(long seq)
	{
		final int i = slot(seq);
		final long off = Math.abs((long) gapMs[i] - Thresholds.TICK_MS) - frameMs[i];
		return (int) Math.max(0, Math.min(Integer.MAX_VALUE, off));
	}

	/** True when the tick came later than one tick after the one before: {@code gapMs > TICK_MS}. */
	public boolean late(long seq)
	{
		return gapMs[slot(seq)] > Thresholds.TICK_MS;
	}

	/**
	 * True when the tick came sooner than one tick after the one before: {@code gapMs < TICK_MS}. A gap of exactly
	 * {@link Thresholds#TICK_MS} is neither late nor early. The evidence's {@code tickLate} and {@code tickEarly}
	 * count the late and the early ticks that are also off by {@link Thresholds#TICK_OFF_MS} or more (contract 6.3).
	 */
	public boolean early(long seq)
	{
		return gapMs[slot(seq)] < Thresholds.TICK_MS;
	}

	private int slot(long seq)
	{
		return (int) (seq % capacity);
	}

	private static short toShort(int v)
	{
		return (short) Fmt.clamp(v, -1, Short.MAX_VALUE);
	}
}
