package com.whylag.core;

import java.lang.management.ManagementFactory;
import java.time.ZoneOffset;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * T1 (contract section 4): {@link FrameSampler#frame} and {@link TickSampler#tick} allocate 0 bytes. After a
 * warm-up of 100,000 frames, 1,000,000 frames and 10,000 ticks run on this thread, and the bytes the JVM has
 * charged to the thread must not have moved. The run crosses 20,000 second boundaries, passes through state
 * changes, a login mask and focus changes, and holds the FIRST wrap of both rings, so the closing of a second and
 * the reuse of a slot are measured too.
 *
 * <p><b>One measurement, on new objects.</b> The JIT's own first compilations of this loop charge a few hundred
 * bytes to the thread, once (792 bytes in 8 JVMs of 8 when the check of round 1 measured the first run after the
 * warm-up; none under {@code -Xint} or with C2 off). That is the compiler's, not the samplers'. So the CODE is
 * warmed first, on a throwaway session and samplers driven through as many frames as the whole test; then new ones
 * are built, warmed as the contract says and measured ONCE. An allocation on the path shows in that one run
 * whether it is made for every frame, tick or closed second, or only once in a sampler's life after its warm-up
 * (at a ring's first wrap, say). What it cannot see is an allocation made once in a JVM's life (by a static), which
 * the throwaway objects spend.
 *
 * <p>The management bean is TEST code only; main code may not name it outside one file of {@code host}.
 */
public class FramePathAllocationTest
{
	private static final long START = 7_000_000_000_000L;
	private static final long NANOS_PER_MS = 1_000_000L;
	private static final int FRAME_MS = 20;
	private static final int FRAMES_PER_SECOND = 1000 / FRAME_MS;
	private static final int WARM_UP_FRAMES = 100_000;
	private static final int FRAMES = 1_000_000;
	private static final int TICKS = 10_000;
	/** One tick for every this many frames: 1,000,000 frames carry 10,000 ticks. */
	private static final int FRAMES_PER_TICK = FRAMES / TICKS;
	/** Frames that warm the CODE on throwaway objects: the warm-up and the measured run together. */
	private static final int CODE_WARM_UP_FRAMES = WARM_UP_FRAMES + FRAMES;
	private static final long DAY_MS = 86_400_000L;
	/** Slots of the session's tick ring: {@link Session} builds it one slot larger than {@link Thresholds#TICKS}. */
	private static final long TICK_SLOTS = Thresholds.TICKS + 1;

	private com.sun.management.ThreadMXBean bean;
	private long thread;

	private Session session;
	private SelfTimer timer;
	private FrameSampler frames;
	private TickSampler ticks;
	private long ms;
	private long cpu;
	private int ticked;

	@Before
	public void setUp()
	{
		final java.lang.management.ThreadMXBean plain = ManagementFactory.getThreadMXBean();
		Assume.assumeTrue("this JVM does not count a thread's allocation",
			plain instanceof com.sun.management.ThreadMXBean);
		bean = (com.sun.management.ThreadMXBean) plain;
		Assume.assumeTrue(bean.isThreadAllocatedMemorySupported());
		if (!bean.isThreadAllocatedMemoryEnabled())
		{
			bean.setThreadAllocatedMemoryEnabled(true);
		}
		thread = Thread.currentThread().getId();
	}

	/** A new session and new samplers, the clocks at the session's start. */
	private void fresh(boolean timed)
	{
		session = new Session(START, 1_000_000L, Os.WINDOWS, ZoneOffset.UTC);
		timer = new SelfTimer();
		timer.on(timed);
		frames = new FrameSampler(session, timer);
		ticks = new TickSampler(session, frames, timer);
		ms = 0;
		cpu = 0;
		ticked = 0;
	}

	/** Runs the code on throwaway objects, so that the JIT is done with it before the measured objects exist. */
	private void warmTheCode(boolean timed)
	{
		fresh(timed);
		drive(CODE_WARM_UP_FRAMES);
	}

	/** {@code n} frames 20 ms apart with all that goes with them; a tick before every hundredth. */
	private void drive(int n)
	{
		for (int i = 0; i < n; i++)
		{
			ms += FRAME_MS;
			final long nanos = START + ms * NANOS_PER_MS;
			if (i % FRAMES_PER_TICK == 0)
			{
				ticks.tick(nanos, (int) (ms / FRAME_MS), 40);
				ticked++;
			}
			if (i % 5000 == 0)
			{
				// a hop now and then: pending state changes, flags, loading time and a login mask
				final int step = (i / 5000) % 4;
				final int code = step == 0 ? State.HOPPING : step == 1 ? State.LOADING : State.LOGGED_IN;
				frames.state(nanos, code);
				ticks.state(nanos, code);
				frames.focus(step != 2);
				frames.world(300 + step);
				frames.scene(step, step * 2, 12_000 + step);
			}
			cpu += 7_000_000L;
			frames.frame(nanos, (int) (ms / FRAME_MS), cpu);
		}
	}

	/** One frame a whole day after the last: 86,400 missed seconds to fill. */
	private void aDayLater()
	{
		ms += DAY_MS;
		frames.frame(START + ms * NANOS_PER_MS, 0, -1);
	}

	private long allocated()
	{
		return bean.getThreadAllocatedBytes(thread);
	}

	/** What reading the counter itself costs, taken off every measurement. */
	private long probeCost()
	{
		allocated();
		final long probeBefore = allocated();
		return allocated() - probeBefore;
	}

	/** The bytes charged to this thread by {@code n} driven frames: ONE measurement. */
	private long bytesOf(int n)
	{
		final long probeCost = probeCost();
		final long before = allocated();
		drive(n);
		return allocated() - before - probeCost;
	}

	@Test
	public void theFramePathAllocatesNothing()
	{
		warmTheCode(false);
		fresh(false);
		drive(WARM_UP_FRAMES);
		ticked = 0;
		final long secondsBefore = session.seconds.frameHead();
		final long ticksBefore = session.ticks.head();

		final long delta = bytesOf(FRAMES);

		assertEquals("bytes allocated by 1,000,000 frames and 10,000 ticks", 0, delta);
		assertEquals(TICKS, ticked);
		assertEquals("20 ms frames: 50 a second, every second closed", FRAMES / FRAMES_PER_SECOND,
			session.seconds.frameHead() - secondsBefore);
		assertEquals(TICKS, session.ticks.head() - ticksBefore);
		assertTrue("the second ring's first wrap is inside the measured run",
			secondsBefore < session.seconds.capacity() && session.seconds.frameHead() >= session.seconds.capacity());
		assertTrue("so is the tick ring's", ticksBefore < TICK_SLOTS && session.ticks.head() >= TICK_SLOTS);
	}

	@Test
	public void theFramePathAllocatesNothingWhileItIsTimed()
	{
		warmTheCode(true);
		fresh(true);
		drive(WARM_UP_FRAMES);
		final long framesBefore = timer.count(SelfTimer.FRAME);
		final long ticksBefore = timer.count(SelfTimer.TICK);

		final long delta = bytesOf(FRAMES);

		assertEquals("bytes allocated with the self timer on", 0, delta);
		assertEquals("every frame was timed", FRAMES, timer.count(SelfTimer.FRAME) - framesBefore);
		assertEquals("every tick was timed", TICKS, timer.count(SelfTimer.TICK) - ticksBefore);
	}

	@Test
	public void aDayOfMissedSecondsAllocatesNothing()
	{
		// the filling loop is warmed on the throwaway objects too
		warmTheCode(false);
		for (int i = 0; i < 3; i++)
		{
			aDayLater();
		}
		fresh(false);
		drive(WARM_UP_FRAMES);
		final long headBefore = session.seconds.frameHead();

		final long probeCost = probeCost();
		final long before = allocated();
		aDayLater();
		final long delta = allocated() - before - probeCost;

		assertEquals("bytes allocated by filling 86,400 missed seconds", 0, delta);
		assertEquals(86_400, session.seconds.frameHead() - headBefore);
		assertTrue("the ring's first wrap is among them",
			headBefore < session.seconds.capacity() && session.seconds.frameHead() >= session.seconds.capacity());
	}

	/** The measurement can fail: an allocation on this thread is seen, to the byte array's size at least. */
	@Test
	public void theMeasurementSeesAnAllocation()
	{
		allocated();
		final long before = allocated();
		final byte[] block = new byte[64 * 1024];
		final long delta = allocated() - before;
		assertTrue("64 KB allocated, " + delta + " bytes seen", delta >= block.length);
	}
}
