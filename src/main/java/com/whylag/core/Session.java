package com.whylag.core;

import java.time.ZoneId;

/**
 * The rings and the clocks of one plugin session, as one object (contract 3.5). Everything is allocated here,
 * once: each ring ONE SLOT LARGER than its constant, so the readable span is the constant itself (the one slot of
 * slack, section 3.3).
 *
 * <p><b>One writer for each piece.</b> The frame columns of {@link #seconds} and {@link #ticks}: the samplers, on
 * the client thread. The host columns of {@link #seconds}: the engine, on the sampler thread. {@link #events}: the
 * engine, on the sampler thread. The three usuals: the detector alone, on the sampler thread;
 * {@link #rttUsual} is reset by it when the {@code world} column changes from one second to the next. The login
 * second ({@link #loggedInSince}): {@code LagEngine.gameState} alone, on the client thread (and the test helper
 * {@code Trace}). Everyone else only reads.
 *
 * <p>Session time: second 0 starts at {@link #startNanos} ({@code System.nanoTime()} at start) and at wall time
 * {@link #startWallMs}; session ms count from the same instant. Session ms are {@code long} everywhere, so they
 * never wrap, however long the client runs.
 *
 * <p><b>Where the warm-up counts from.</b> {@link #loggedInSinceSec()} is the session second in which the CURRENT
 * logged-in stretch began: {@link #NEVER} until a login is seen. It is the one mutable field of a session, a
 * volatile long, written once per login by its one writer and read by the verdict and the snapshot through
 * {@link #warm} and {@link #warmupLeftS}, the one rule for both. It is never set back to {@link #NEVER}.
 *
 * <p><b>Now, and the newest second</b> (contract 3.5). {@code nowSec} is the clock's session second at a step,
 * {@code secOf(nanos)}: it runs on while the client is stalled or minimised, and the rings do not. Two helpers turn
 * it into ring terms, so the verdict and the snapshot read the same seconds: {@link #lastSec} is the newest
 * COMPLETE second (every "newest second" and "the window" count back from it), and {@link #framesStopped} is the
 * one rule for "no frame for longer than {@link Thresholds#NO_FRAMES_MS}".
 */
public final class Session
{
	/** {@link #loggedInSinceSec()} before any login is seen. */
	public static final long NEVER = Long.MAX_VALUE;

	/** The highest RTT, in ms, a ping usual keeps; above it a sample is clamped. */
	private static final int RTT_USUAL_MAX_MS = 2000;
	/** The highest frame rate the frame rate usual keeps. */
	private static final int FPS_USUAL_MAX = 1000;
	private static final long NANOS_PER_SECOND = 1_000_000_000L;
	private static final long NANOS_PER_MS = 1_000_000L;
	private static final long MS_PER_SECOND = 1000L;
	/**
	 * How far the frame head must lag the clock before "no frame for longer than NO_FRAMES_MS" is CERTAIN: the two
	 * seconds the newest frame may still lie in (the one it closed and the one after), plus NO_FRAMES_MS rounded up
	 * to whole seconds. Clock arithmetic, not a threshold of its own.
	 */
	private static final long FRAMES_STOPPED_BEHIND_S = 2 + (Thresholds.NO_FRAMES_MS + MS_PER_SECOND - 1) / MS_PER_SECOND;

	public final long startNanos, startWallMs;
	public final Os os;
	public final ZoneId zone;
	/** {@link Thresholds#SECONDS} + 1 slots. */
	public final SecondRing seconds;
	/** {@link Thresholds#TICKS} + 1 slots. */
	public final TickRing ticks;
	/** {@link Thresholds#EVENTS} events. */
	public final EventLog events;
	/** This world's RTT; max 2000; reset when the world COLUMN changes. */
	public final Usual rttUsual;
	/** Every world's RTT this session; max 2000. */
	public final Usual rttSession;
	/** Frames a second; max 1000. */
	public final Usual fpsUsual;

	/** The session second the current logged-in stretch began; {@link #NEVER} until a login is seen. */
	private volatile long loggedInSinceSec = NEVER;

	public Session(long startNanos, long startWallMs, Os os, ZoneId zone)
	{
		this.startNanos = startNanos;
		this.startWallMs = startWallMs;
		this.os = os;
		this.zone = zone;
		seconds = new SecondRing(Thresholds.SECONDS + 1);
		ticks = new TickRing(Thresholds.TICKS + 1);
		events = new EventLog(Thresholds.EVENTS);
		rttUsual = new Usual(RTT_USUAL_MAX_MS);
		rttSession = new Usual(RTT_USUAL_MAX_MS);
		fpsUsual = new Usual(FPS_USUAL_MAX);
	}

	/** The session second of a {@code System.nanoTime()} reading: {@code floor((nanos - startNanos) / 1e9)}. */
	public long secOf(long nanos)
	{
		return Math.floorDiv(nanos - startNanos, NANOS_PER_SECOND);
	}

	/** The wall time at which session second {@code sec} starts: {@code startWallMs + sec * 1000}. */
	public long wallMsOf(long sec)
	{
		return startWallMs + sec * MS_PER_SECOND;
	}

	/** Session ms of a {@code System.nanoTime()} reading, rounded down: {@code floor((nanos - startNanos) / 1e6)}. */
	public long msOf(long nanos)
	{
		return Math.floorDiv(nanos - startNanos, NANOS_PER_MS);
	}

	/** Session ms at which second {@code sec} starts: {@code sec * 1000}. */
	public long msOfSec(long sec)
	{
		return sec * MS_PER_SECOND;
	}

	/** The session second the current logged-in stretch began; {@link #NEVER} in a new session. A volatile read. */
	public long loggedInSinceSec()
	{
		return loggedInSinceSec;
	}

	/**
	 * A logged-in stretch began in session second {@code sec}. ONE writer: {@code LagEngine.gameState}, on the client
	 * thread, by the rule of contract 3.5 (and the test helper {@code Trace}). One volatile write.
	 */
	public void loggedInSince(long sec)
	{
		loggedInSinceSec = sec;
	}

	/** True once {@link Thresholds#WARMUP_S} seconds have passed since the login: never before a login is seen. */
	public boolean warm(long nowSec)
	{
		final long since = loggedInSinceSec;
		return since != NEVER && nowSec - since >= Thresholds.WARMUP_S;
	}

	/**
	 * Seconds of warm-up left at {@code nowSec}: 0 when {@link #warm}; {@link Thresholds#WARMUP_S} while no login is
	 * seen; else {@code WARMUP_S - (nowSec - since)}, held in 1 .. {@code WARMUP_S}.
	 * <p>Choice: with no warm-up ({@code WARMUP_S} 0 since the first live look, 2026-09-29) this is 0 from the login
	 * second on and 1 before it, where {@link #warm} is false.
	 */
	public int warmupLeftS(long nowSec)
	{
		final long since = loggedInSinceSec;
		if (since == NEVER)
		{
			return Thresholds.WARMUP_S;
		}
		final long elapsed = nowSec - since;
		if (elapsed >= Thresholds.WARMUP_S)
		{
			return 0;
		}
		return (int) Math.max(1, Math.min(Thresholds.WARMUP_S, Thresholds.WARMUP_S - elapsed));
	}

	/**
	 * The newest COMPLETE second at {@code nowSec}: {@code min(seconds.head(), nowSec - 1)}; -1 when there is none
	 * yet (or it is no longer readable). Live, {@code nowSec} is the clock's second at the step and this is
	 * {@code seconds.head()}. A test that replays one trace at an earlier {@code nowSec} sees nothing after
	 * {@code nowSec - 1}, as a live step would; "now" at the end of a trace of n seconds is {@code nowSec = n}.
	 */
	public long lastSec(long nowSec)
	{
		if (nowSec < 1)
		{
			return -1;
		}
		final long last = Math.min(seconds.head(), nowSec - 1);
		return last < seconds.tail() ? -1 : last;
	}

	/**
	 * True when no frame has been drawn for CERTAINLY longer than {@link Thresholds#NO_FRAMES_MS} at {@code nowSec}:
	 * the card's "Waiting for the game" and the frame tile's "No frames drawn" both read this. A frame head F means
	 * the newest frame came in [F + 1, F + 2) seconds (the frame that closed F; none since has closed F + 1), so at
	 * {@code nowSec} it is more than {@code nowSec - F - 2} seconds old. True once that alone is past NO_FRAMES_MS:
	 * {@code nowSec - frameHead >= 2 + ceil(NO_FRAMES_MS / 1000)}, 4 seconds at the starting 2,000 ms. While frames
	 * run the head is 1 or 2 seconds behind the clock; after a stall this turns true 2 to 3 s after the last frame.
	 * Before the first frame it turns true at second 3. Only the frame head counts: the host columns run on without
	 * the client.
	 */
	public boolean framesStopped(long nowSec)
	{
		return nowSec - seconds.frameHead() >= FRAMES_STOPPED_BEHIND_S;
	}
}
