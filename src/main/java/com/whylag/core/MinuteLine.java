package com.whylag.core;

import java.time.ZoneId;

/**
 * One minute of the session in one line, immutable: the clock of the minute's first second, the frame rate, the tick
 * gap, the ping, the lags that STARTED in it and the seconds it left out. The plugin makes one
 * every {@value #SECONDS} sampler steps from the last {@value #SECONDS} complete seconds ({@link #of}),
 * {@link MinuteLog} keeps the last hour of them, and the report prints them under "Last 60 minutes":
 *
 * <pre>
 * 21:47  fps 48/50/51  tick 601/952 ms  ping 40-43 ms  lags 1  masked 0 s
 * </pre>
 *
 * <p>Every reading is -1 when the minute has no data for it, and prints "-". Reads the session's rings and event
 * log and writes nothing, on the sampler thread, as {@link SnapshotBuilder} does.
 *
 * <p>
 * Choice: the frame rate is the frames of each UNMASKED second ({@link Flags#masked}) - a load, a hop or the login
 * screen is not a slow client - and its mean is rounded to the nearest whole frame.
 * <br>
 * Choice: the tick gap is the mean and the worst of every unmasked tick of the window, every gap, no trim (the
 * event's own rule, {@link LagEvent}); a tick is unmasked when neither its flags nor its second's are masked.
 * <br>
 * Choice: the ping is the lowest and the highest FRESH round trip ({@link Thresholds#RTT_STALE_S}), masked seconds
 * included: a load does use the line.
 * <br>
 * Choice: the minute's lags are the events the log holds (an open one included) that started in its seconds, so
 * every lag is counted in exactly one line; the seconds left out are the masked ones.
 * <br>
 * Choice: the window is {@code nowSec - 60 .. nowSec - 1}, the seconds that can be complete, and a second no ring
 * holds (before its tail, or not written yet) is no data.
 */
public final class MinuteLine
{
	/** The seconds one line covers: a minute. */
	public static final int SECONDS = 60;
	/** A reading with no data. */
	public static final int NONE = -1;

	private static final long MS_PER_SECOND = 1000L;

	/** Wall time of the minute's first second. */
	public final long startWallMs;
	/** The lowest, the mean and the highest frame rate; {@link #NONE} when no second counted. */
	public final int fpsMin, fpsMean, fpsMax;
	/** The mean and the worst tick gap, in ms; {@link #NONE} when no tick counted. */
	public final int tickMeanMs, tickWorstMs;
	/** The lowest and the highest fresh ping, in ms; {@link #NONE} when there was none. */
	public final int pingMinMs, pingMaxMs;
	/** The lags that started in the minute. */
	public final int lags;
	/** The seconds the frame rate and the ticks left out. */
	public final int maskedSeconds;

	public MinuteLine(long startWallMs, int fpsMin, int fpsMean, int fpsMax, int tickMeanMs, int tickWorstMs,
		int pingMinMs, int pingMaxMs, int lags, int maskedSeconds)
	{
		this.startWallMs = startWallMs;
		this.fpsMin = fpsMin;
		this.fpsMean = fpsMean;
		this.fpsMax = fpsMax;
		this.tickMeanMs = tickMeanMs;
		this.tickWorstMs = tickWorstMs;
		this.pingMinMs = pingMinMs;
		this.pingMaxMs = pingMaxMs;
		this.lags = lags;
		this.maskedSeconds = maskedSeconds;
	}

	/**
	 * The line of the {@value #SECONDS} seconds that end just before {@code nowSec}. Sampler thread; never throws for
	 * a session with no data, which yields a line of dashes.
	 *
	 * @param s      the session to read
	 * @param nowSec the session second of the step, {@code Session.secOf(nanos)}
	 */
	public static MinuteLine of(Session s, long nowSec)
	{
		final long end = nowSec - 1;
		final long first = Math.max(0, end - SECONDS + 1);
		final SecondRing r = s.seconds;

		int frames = 0;
		long frameSum = 0;
		int fpsMin = Integer.MAX_VALUE;
		int fpsMax = -1;
		int pings = 0;
		int pingMin = Integer.MAX_VALUE;
		int pingMax = -1;
		int masked = 0;
		final long from = Math.max(first, r.tail());
		final long to = Math.min(end, r.head());
		for (long k = from; k <= to; k++)
		{
			if (!r.valid(k))
			{
				continue;
			}
			final int flags = r.flags(k);
			final int fps = r.frames(k);
			final int rtt = r.rttMs(k);
			final int age = r.rttAgeS(k);
			if (!r.valid(k))
			{
				continue;
			}
			final boolean isMasked = Flags.masked(flags);
			if (isMasked)
			{
				masked++;
			}
			else if (fps >= 0)
			{
				frames++;
				frameSum += fps;
				fpsMin = Math.min(fpsMin, fps);
				fpsMax = Math.max(fpsMax, fps);
			}
			if (rtt >= 0 && age >= 0 && age <= Thresholds.RTT_STALE_S)
			{
				pings++;
				pingMin = Math.min(pingMin, rtt);
				pingMax = Math.max(pingMax, rtt);
			}
		}

		int ticks = 0;
		long gapSum = 0;
		int gapWorst = -1;
		final long startMs = s.msOfSec(first);
		final long endMs = s.msOfSec(end + 1);
		final TickRing t = s.ticks;
		for (long seq = t.head(); seq >= 0; seq--)
		{
			if (!t.valid(seq))
			{
				break;
			}
			final long at = t.atMs(seq);
			final int gap = Math.max(0, t.gapMs(seq));
			final int flags = t.flags(seq);
			if (!t.valid(seq) || at < startMs)
			{
				break;   // ticks arrive in order: this one and every older one ended before the window
			}
			if (at >= endMs || Flags.masked(flags) || secondMasked(r, Math.floorDiv(at, MS_PER_SECOND)))
			{
				continue;
			}
			ticks++;
			gapSum += gap;
			gapWorst = Math.max(gapWorst, gap);
		}

		int lags = 0;
		for (LagEvent e : s.events.copy())
		{
			if (e.startSec >= first && e.startSec <= end)
			{
				lags++;
			}
		}

		return new MinuteLine(s.wallMsOf(first),
			frames == 0 ? NONE : fpsMin, frames == 0 ? NONE : mean(frameSum, frames), frames == 0 ? NONE : fpsMax,
			ticks == 0 ? NONE : mean(gapSum, ticks), gapWorst,
			pings == 0 ? NONE : pingMin, pings == 0 ? NONE : pingMax, lags, masked);
	}

	/**
	 * The line as the report prints it, the clock in {@code zone}:
	 * {@code 21:47  fps 48/50/51  tick 601/952 ms  ping 40-43 ms  lags 1  masked 0 s}.
	 * A reading with no data prints "-" in place of its numbers and its unit.
	 */
	public String text(ZoneId zone)
	{
		final StringBuilder b = new StringBuilder(96).append(Fmt.clock(startWallMs, zone)).append("  fps ");
		if (fpsMin < 0)
		{
			b.append('-');
		}
		else
		{
			b.append(Fmt.thousands(fpsMin)).append('/').append(Fmt.thousands(fpsMean)).append('/')
				.append(Fmt.thousands(fpsMax));
		}
		b.append("  tick ");
		if (tickMeanMs < 0)
		{
			b.append('-');
		}
		else
		{
			b.append(Fmt.thousands(tickMeanMs)).append('/').append(Fmt.thousands(tickWorstMs)).append(" ms");
		}
		b.append("  ping ");
		if (pingMinMs < 0)
		{
			b.append('-');
		}
		else
		{
			b.append(Fmt.thousands(pingMinMs)).append('-').append(Fmt.thousands(pingMaxMs)).append(" ms");
		}
		b.append("  lags ").append(Fmt.thousands(lags));
		b.append("  masked ").append(Fmt.thousands(maskedSeconds)).append(" s");
		return b.toString();
	}

	/** The second a tick arrived in is readable and masked by its flags ({@link Flags#masked}). */
	private static boolean secondMasked(SecondRing r, long sec)
	{
		if (!r.valid(sec))
		{
			return false;
		}
		final int flags = r.flags(sec);
		return r.valid(sec) && Flags.masked(flags);
	}

	/** {@code sum / n} rounded to the nearest whole number. */
	private static int mean(long sum, int n)
	{
		return (int) ((sum + n / 2) / n);
	}
}
