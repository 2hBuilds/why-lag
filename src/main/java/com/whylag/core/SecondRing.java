package com.whylag.core;


/**
 * One row per session second, in two column groups with one writer each (contract 3.3): the FRAME columns are
 * written by the client thread ({@link #putFrame}), the HOST columns by the sampler thread ({@link #putHost}).
 * Any thread reads.
 *
 * <p><b>Storage.</b> Primitive arrays allocated once in the constructor, one per column: {@code short[]} and
 * {@code byte[]}, {@code int[]} for the two unit counters, {@code char[]} for {@code region} (unsigned 16 bits).
 * The slot of second {@code sec} is {@code sec % capacity}. No method allocates and none takes a lock.
 *
 * <p><b>Clamps</b> (the clamp table of contract 3.3): every value is clamped to its column on write, and a put
 * never throws for a value. The short columns ({@code frames}, {@code worstFrameMs}, {@code loadingMs},
 * {@code world}, {@code players}, {@code npcs}, {@code rttMs}, {@code rttAgeS}) hold -1 .. 32767: EVERY negative
 * value is stored as -1 ("no data"), over 32767 as 32767. {@code worstFrameEndMs} holds 0 .. 999; the byte columns
 * ({@code slowFrames}, {@code state}, {@code flags}) 0 .. 127, a negative stored as 0; {@code region} 0 .. 65535, a
 * negative stored as 0; {@code sentUnits} and {@code resentUnits} are kept whole (the writer never hands in a
 * negative); {@code conn} is the {@link NoData} ordinal, a null stored as {@link NoData#NONE}.
 *
 * <p><b>Writing.</b> Each group must be written in order from second 0: {@code sec} must be its head + 1, and the
 * writer fills a gap by writing every missed second. A put writes its columns and THEN its volatile head.
 *
 * <p><b>One slot of slack.</b> The next put reuses the slot of second {@code head + 1 - capacity}. So that a second
 * is never readable while its slot is being overwritten, {@link #tail()} is {@code max(0, newest - capacity + 2)}:
 * the slot about to be reused is already below the tail. A ring built with capacity {@code n + 1} keeps {@code n}
 * readable seconds, which is why {@link Session} builds it one slot larger than {@link Thresholds#SECONDS}.
 *
 * <p><b>Reading.</b> Call {@link #valid(long)} before reading a second (it is published) and again after (it was
 * not overwritten meanwhile); throw the read away if the second check fails. The ordering that makes the re-check
 * sound on weakly ordered CPUs (ARM) as well as on x86 comes from volatile accesses alone, no lock and no fence
 * API: each put begins by reading its volatile head, so the column stores that follow cannot move before the head
 * store of the previous put; {@code valid} begins with a volatile store to {@link #order}, so the column reads a
 * caller made before it cannot move past the volatile head reads that follow (a plain read may otherwise be
 * reordered after a later volatile read).
 */
public final class SecondRing
{
	private static final NoData[] CONN = NoData.values();
	/** The last millisecond of a second: the range of {@code worstFrameEndMs}. */
	private static final int LAST_MS = 999;

	private final int capacity;

	private final short[] frames;
	private final short[] worstFrameMs;
	private final short[] worstFrameEndMs;
	private final byte[] slowFrames;
	private final short[] loadingMs;
	private final byte[] state;
	private final byte[] flags;
	private final short[] world;
	private final short[] players;
	private final short[] npcs;
	private final char[] region;

	private final short[] rttMs;
	private final short[] rttAgeS;
	private final int[] sentUnits;
	private final int[] resentUnits;
	private final byte[] conn;

	private volatile long frameHead = -1;
	private volatile long hostHead = -1;
	/** Written at the start of {@link #valid(long)} for its ordering only; never read. */
	private volatile int order;

	/**
	 * @param capacity slots, at least 2; {@code capacity - 1} seconds are readable
	 */
	public SecondRing(int capacity)
	{
		if (capacity < 2)
		{
			throw new IllegalArgumentException("a ring needs at least 2 slots, got " + capacity);
		}
		this.capacity = capacity;
		frames = new short[capacity];
		worstFrameMs = new short[capacity];
		worstFrameEndMs = new short[capacity];
		slowFrames = new byte[capacity];
		loadingMs = new short[capacity];
		state = new byte[capacity];
		flags = new byte[capacity];
		world = new short[capacity];
		players = new short[capacity];
		npcs = new short[capacity];
		region = new char[capacity];
		rttMs = new short[capacity];
		rttAgeS = new short[capacity];
		sentUnits = new int[capacity];
		resentUnits = new int[capacity];
		conn = new byte[capacity];
	}

	/** Slots in the ring; one fewer are readable. */
	public int capacity()
	{
		return capacity;
	}

	/**
	 * The client thread's columns of second {@code sec}, which must be {@link #frameHead()} + 1.
	 *
	 * @throws IllegalArgumentException when {@code sec} is out of order (a caller bug; nothing is written)
	 */
	public void putFrame(long sec, FrameSecond v)
	{
		final long head = frameHead;
		if (sec != head + 1)
		{
			throw new IllegalArgumentException("putFrame(" + sec + ") must follow frame head " + head);
		}
		// The slot about to be reused belongs to second sec - capacity, which the head already published puts
		// below tail(). The volatile read of frameHead above keeps that published head ahead of the column stores
		// below on every CPU (a volatile read is never reordered with the stores after it).
		final int i = slot(sec);
		frames[i] = toShort(v.frames);
		worstFrameMs[i] = toShort(v.worstFrameMs);
		worstFrameEndMs[i] = (short) Fmt.clamp(v.worstFrameEndMs, 0, LAST_MS);
		slowFrames[i] = toByte(v.slowFrames);
		loadingMs[i] = toShort(v.loadingMs);
		state[i] = toByte(v.state);
		flags[i] = toByte(v.flags);
		world[i] = toShort(v.world);
		players[i] = toShort(v.players);
		npcs[i] = toShort(v.npcs);
		region[i] = (char) Fmt.clamp(v.region, 0, Character.MAX_VALUE);
		frameHead = sec;
	}

	/**
	 * The sampler thread's columns of second {@code sec}, which must be {@link #hostHead()} + 1. A null
	 * {@code conn} is stored as {@link NoData#NONE}.
	 *
	 * @throws IllegalArgumentException when {@code sec} is out of order (a caller bug; nothing is written)
	 */
	public void putHost(long sec, HostSecond v)
	{
		final long head = hostHead;
		if (sec != head + 1)
		{
			throw new IllegalArgumentException("putHost(" + sec + ") must follow host head " + head);
		}
		// Ordered after the previous head store by the volatile read of hostHead above, as in putFrame.
		final int i = slot(sec);
		rttMs[i] = toShort(v.rttMs);
		rttAgeS[i] = toShort(v.rttAgeS);
		sentUnits[i] = v.sentUnits;
		resentUnits[i] = v.resentUnits;
		conn[i] = (byte) (v.conn == null ? NoData.NONE : v.conn).ordinal();
		hostHead = sec;
	}

	/** The newest second whose frame columns are written; -1 before the first. */
	public long frameHead()
	{
		return frameHead;
	}

	/** The newest second whose host columns are written; -1 before the first. */
	public long hostHead()
	{
		return hostHead;
	}

	/** The newest second with BOTH groups written: the slower writer's head. */
	public long head()
	{
		return Math.min(frameHead, hostHead);
	}

	/** The oldest readable second: {@code max(0, max(frameHead, hostHead) - capacity + 2)}. */
	public long tail()
	{
		return tailOf(frameHead, hostHead);
	}

	/** {@code tail() <= sec <= head()}. Starts with a volatile store: see the class notes on reading. */
	public boolean valid(long sec)
	{
		order = 0;
		final long f = frameHead;
		final long h = hostHead;
		return sec >= tailOf(f, h) && sec <= Math.min(f, h);
	}

	public int frames(long sec)
	{
		return frames[slot(sec)];
	}

	public int worstFrameMs(long sec)
	{
		return worstFrameMs[slot(sec)];
	}

	public int worstFrameEndMs(long sec)
	{
		return worstFrameEndMs[slot(sec)];
	}

	public int slowFrames(long sec)
	{
		return slowFrames[slot(sec)];
	}

	public int loadingMs(long sec)
	{
		return loadingMs[slot(sec)];
	}

	public int state(long sec)
	{
		return state[slot(sec)];
	}

	public int flags(long sec)
	{
		return flags[slot(sec)];
	}

	public int world(long sec)
	{
		return world[slot(sec)];
	}

	public int players(long sec)
	{
		return players[slot(sec)];
	}

	public int npcs(long sec)
	{
		return npcs[slot(sec)];
	}

	/** The map region id, 0 .. 65535. */
	public int region(long sec)
	{
		return region[slot(sec)];
	}

	public int rttMs(long sec)
	{
		return rttMs[slot(sec)];
	}

	public int rttAgeS(long sec)
	{
		return rttAgeS[slot(sec)];
	}

	public int sentUnits(long sec)
	{
		return sentUnits[slot(sec)];
	}

	public int resentUnits(long sec)
	{
		return resentUnits[slot(sec)];
	}

	/**
	 * Why second {@code sec} has no fresh RTT: {@link NoData#NONE} in exactly the seconds whose RTT is fresh
	 * ({@code rttMs >= 0} and {@code 0 <= rttAgeS <= RTT_STALE_S}, contract 3.3), else the reason.
	 */
	public NoData conn(long sec)
	{
		return CONN[conn[slot(sec)]];
	}

	private int slot(long sec)
	{
		return (int) (sec % capacity);
	}

	private long tailOf(long frameHeadNow, long hostHeadNow)
	{
		return Math.max(0, Math.max(frameHeadNow, hostHeadNow) - capacity + 2);
	}

	private static short toShort(int v)
	{
		return (short) Fmt.clamp(v, -1, Short.MAX_VALUE);
	}

	private static byte toByte(int v)
	{
		return (byte) Fmt.clamp(v, 0, Byte.MAX_VALUE);
	}
}
