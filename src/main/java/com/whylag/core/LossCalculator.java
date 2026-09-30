package com.whylag.core;

/**
 * The connection's cumulative counters turned into one second (contract 3.10 and section 7, L2): what was sent and
 * re-sent since the sample before, the re-sent share over the last {@link Thresholds#RESENT_WINDOW_S} samples, and
 * the RTT with its AGE.
 *
 * <p><b>RTT age.</b> The system only measures a new round trip when new data is acknowledged, so an RTT read while
 * nothing new goes out is an old number. {@code rttAgeS} counts from the last sample in which
 * {@code sent - resent} rose; re-sent data moves {@code sent} too, so {@code sent} alone would call a frozen RTT
 * fresh.
 *
 * <p><b>{@code conn}.</b> The probe's reason passes through. {@link NoData#NONE} is written in exactly the seconds
 * whose RTT is fresh ({@code rttMs >= 0}, {@code 0 <= rttAgeS <= RTT_STALE_S}); an RTT that is older keeps its
 * number and is written {@link NoData#STALE}; no RTT yet is -1 with {@link NoData#STALE}.
 *
 * <p><b>Units</b> are bytes on Windows and segments elsewhere (C1), so the least traffic before re-sends count is
 * {@link Os#resentMinSent()}.
 *
 * <p>Sampler thread only, but {@link #requestReset()}: one volatile write from any thread, which the next
 * {@link #sample} applies first.
 *
 * <p>
 * Choice: {@link #sample} writes {@code rttMs}, {@code rttAgeS}, {@code sentUnits}, {@code resentUnits} and
 * {@code conn} of {@code out} and leaves its other fields alone.
 * <br>
 * Choice: the first sample after a start, a reset or a falling counter is the BASE: nothing sent, nothing
 * re-sent, and no RTT yet (its age cannot be known).
 * <br>
 * Choice: a sample that comes with a reason (no data) carries no counters: it adds an empty second to the
 * window, keeps the base and keeps the RTT's age running. A negative counter is read the same way.
 * <br>
 * Choice: the age is the time since the rise, rounded to the nearest whole second, so a sample a few ms early
 * or late still counts one second.
 * <br>
 * Choice: a reset forgets the window, the base and the RTT (a hop, a login, the reconnect that ends a lost
 * connection, or a world change is a new connection).
 * <br>
 * Choice: the three readers answer from the sampling thread's last sample; the two sums are volatile so that
 * another thread reads a whole number.
 */
public final class LossCalculator
{
	private static final long NANOS_PER_SECOND = 1_000_000_000L;
	private static final long MICROS_PER_MS = 1000L;
	private static final int PER_MILLE = 1000;

	private final Os os;
	private final int[] sentWindow = new int[Thresholds.RESENT_WINDOW_S];
	private final int[] resentWindow = new int[Thresholds.RESENT_WINDOW_S];
	private int next;
	private volatile long sentSum, resentSum;

	private boolean haveBase;
	private long baseSent, baseResent;
	private boolean haveRise;
	private long riseNanos;

	private volatile boolean resetRequested;

	public LossCalculator(Os os)
	{
		this.os = os == null ? Os.OTHER : os;
	}

	/**
	 * One sample: the cumulative counters {@code sent} and {@code resent} and the RTT in microseconds, read at
	 * {@code nanos}; {@code conn} says why there is no data ({@link NoData#NONE} or null: there is). Writes the five
	 * connection fields of {@code out}.
	 */
	public void sample(long nanos, long rttMicros, long sent, long resent, NoData conn, HostSecond out)
	{
		if (resetRequested)
		{
			resetRequested = false;
			clear();
		}
		final NoData why = conn == null ? NoData.NONE : conn;
		final boolean counted = why == NoData.NONE && sent >= 0 && resent >= 0;
		int dSent = 0;
		int dResent = 0;
		boolean rose = false;
		if (counted)
		{
			if (haveBase && (sent < baseSent || resent < baseResent))
			{
				// a counter that fell is a new socket (C1): start again from this sample
				clear();
			}
			if (haveBase)
			{
				final long ds = sent - baseSent;
				final long dr = resent - baseResent;
				dSent = (int) Math.min(Integer.MAX_VALUE, ds);
				dResent = (int) Math.min(Integer.MAX_VALUE, dr);
				rose = ds - dr > 0;
			}
			baseSent = sent;
			baseResent = resent;
			haveBase = true;
		}
		push(dSent, dResent);
		out.sentUnits = dSent;
		out.resentUnits = dResent;

		if (rose)
		{
			haveRise = true;
			riseNanos = nanos;
		}
		if (why != NoData.NONE)
		{
			out.rttMs = -1;
			out.rttAgeS = -1;
			out.conn = why;
		}
		else if (rttMicros < 0 || !haveRise)
		{
			out.rttMs = -1;
			out.rttAgeS = -1;
			out.conn = NoData.STALE;
		}
		else
		{
			final long age = Math.max(0L, (nanos - riseNanos + NANOS_PER_SECOND / 2) / NANOS_PER_SECOND);
			out.rttMs = (int) Math.min(Integer.MAX_VALUE, rttMicros / MICROS_PER_MS);
			out.rttAgeS = (int) Math.min(Integer.MAX_VALUE, age);
			out.conn = age <= Thresholds.RTT_STALE_S ? NoData.NONE : NoData.STALE;
		}
	}

	/** Asks for a reset: one volatile write, from any thread. The next {@link #sample} applies it first. */
	public void requestReset()
	{
		resetRequested = true;
	}

	/** Sent over the last {@link Thresholds#RESENT_WINDOW_S} samples, in this system's units. */
	public int sentInWindow()
	{
		return (int) Math.min(Integer.MAX_VALUE, sentSum);
	}

	/** Re-sent over the last {@link Thresholds#RESENT_WINDOW_S} samples. */
	public int resentInWindow()
	{
		return (int) Math.min(Integer.MAX_VALUE, resentSum);
	}

	/** The re-sent share of the window, per mille, 0 .. 1000; 0 while less than {@link Os#resentMinSent()} was sent. */
	public int perMille()
	{
		final long sent = sentSum;
		final long resent = resentSum;
		if (sent <= 0 || sent < os.resentMinSent())
		{
			return 0;
		}
		return (int) Math.max(0L, Math.min(PER_MILLE, resent * PER_MILLE / sent));
	}

	private void push(int sent, int resent)
	{
		sentSum = sentSum - sentWindow[next] + sent;
		resentSum = resentSum - resentWindow[next] + resent;
		sentWindow[next] = sent;
		resentWindow[next] = resent;
		next = (next + 1) % sentWindow.length;
	}

	private void clear()
	{
		for (int i = 0; i < sentWindow.length; i++)
		{
			sentWindow[i] = 0;
			resentWindow[i] = 0;
		}
		next = 0;
		sentSum = 0;
		resentSum = 0;
		haveBase = false;
		haveRise = false;
	}
}
