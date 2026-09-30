package com.whylag.host;

import com.whylag.core.MemorySource;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * The fallback memory source (contract 7, L7): {@link Runtime} only. It reports {@link MemorySource#RUNTIME}, adds no
 * collection listener, reads the heap from the Runtime, and has no CPU reading and no thread clock (each -1).
 *
 * <p>Choice: 32 MB held after a collection must read as 30 MB more or above; 300 ms is the wait for a stray pause.
 * <p>Choice: "a real number" is checked with the 32 MB block held (32 MB or more), not on the reading before it.
 *
 * <p>Right after a collection a bare JVM holds about 0.5 MB, which whole MB round to 0: the reading before the block
 * is only sure to lie between 0 and the limit.
 */
public class RuntimeHostProbeTest
{
	private static final long MB = 1024L * 1024L;

	@Test
	public void sourceIsRuntime()
	{
		assertEquals(MemorySource.RUNTIME, new RuntimeHostProbe().source());
	}

	@Test
	public void startAddsNothing() throws InterruptedException
	{
		final RuntimeHostProbe probe = new RuntimeHostProbe();
		final AtomicInteger calls = new AtomicInteger();
		assertFalse("the Runtime has no collection listener to add",
			probe.start((startMs, durationMs, heapAfterMb) -> calls.incrementAndGet(), System.nanoTime()));
		System.gc();
		Thread.sleep(300);
		assertEquals("a collection reaches no sink", 0, calls.get());
		probe.stop();
		probe.stop();
		assertEquals("stop is safe twice, and adds nothing either", 0, calls.get());
	}

	@Test
	public void readsTheHeapFromTheRuntime()
	{
		final RuntimeHostProbe probe = new RuntimeHostProbe();
		final long runtimeMax = Runtime.getRuntime().maxMemory();
		assertTrue("the test JVM has a heap limit", runtimeMax < Long.MAX_VALUE);
		assertEquals(runtimeMax / MB, probe.heapMaxMb());

		System.gc();
		final int before = probe.heapUsedMb();
		assertTrue("used " + before, before >= 0 && before <= probe.heapMaxMb());
		final byte[] held = new byte[32 * (int) MB];
		final int after = probe.heapUsedMb();
		assertTrue("with 32 MB held, used reads 32 MB or more: " + after, after >= 32 && after <= probe.heapMaxMb());
		assertTrue("32 MB more in use reads as about 32 MB more: " + before + " -> " + after, after - before >= 30);
		assertEquals("kept alive until read", 32 * MB, held.length);
	}

	@Test
	public void cpuAndTheThreadClockAreNoData()
	{
		final RuntimeHostProbe probe = new RuntimeHostProbe();
		assertEquals(-1, probe.processCpuPct());
		assertEquals(-1, probe.systemCpuPct());
		assertEquals(-1, probe.currentThreadCpuNanos());
	}

	@Test
	public void coresAreTheJvmsProcessors()
	{
		assertEquals(Runtime.getRuntime().availableProcessors(), new RuntimeHostProbe().cores());
	}

	@Test
	public void sizesAreWholeMbRoundedDown()
	{
		assertEquals(0, RuntimeHostProbe.mb(0));
		assertEquals(0, RuntimeHostProbe.mb(MB - 1));
		assertEquals(1, RuntimeHostProbe.mb(MB));
		assertEquals(768, RuntimeHostProbe.mb(768 * MB + MB - 1));
		assertEquals("never wraps", Integer.MAX_VALUE, RuntimeHostProbe.mb(Long.MAX_VALUE));
		assertEquals(-1, RuntimeHostProbe.mb(-5));
	}

	@Test
	public void anUnlimitedOrMissingLimitIsUnknown()
	{
		assertEquals(768, RuntimeHostProbe.limitMb(768 * MB));
		assertEquals("unlimited", -1, RuntimeHostProbe.limitMb(Long.MAX_VALUE));
		assertEquals(-1, RuntimeHostProbe.limitMb(0));
		assertEquals(-1, RuntimeHostProbe.limitMb(-1));
		assertEquals("under 1 MB is a limit of 0, which is unknown (contract 3.7)", -1, RuntimeHostProbe.limitMb(MB - 1));
	}
}
