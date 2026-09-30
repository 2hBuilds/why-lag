package com.whylag.core;

import org.junit.Assume;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * The plugin's own cost meter (contract 3.9): the switch, the count, mean and longest of each path, the footer's
 * words, and an {@code add} that allocates nothing (it runs on the frame path). The allocation counter is
 * {@code SecondRingTest}'s.
 */
public class SelfTimerTest
{
	@Test
	public void theSwitch()
	{
		final SelfTimer t = new SelfTimer();
		assertFalse(t.on());
		t.on(true);
		assertTrue(t.on());
		t.on(false);
		assertFalse(t.on());
	}

	@Test
	public void countMeanAndMax()
	{
		final SelfTimer t = new SelfTimer();
		t.add(SelfTimer.FRAME, 100);
		t.add(SelfTimer.FRAME, 300);
		t.add(SelfTimer.TICK, 2_000);
		assertEquals(2, t.count(SelfTimer.FRAME));
		assertEquals(200, t.meanNanos(SelfTimer.FRAME));
		assertEquals(300, t.maxNanos(SelfTimer.FRAME));
		assertEquals(1, t.count(SelfTimer.TICK));
		assertEquals(0, t.count(SelfTimer.STEP));
		assertEquals(0, t.meanNanos(SelfTimer.STEP));
		t.add(7, 999);
		assertEquals("an unknown path is ignored", 0, t.count(7));
		t.reset();
		assertEquals(0, t.count(SelfTimer.FRAME));
		assertEquals(0, t.maxNanos(SelfTimer.FRAME));
		assertEquals(0, t.meanNanos(SelfTimer.TICK));
	}

	@Test
	public void footer()
	{
		final SelfTimer t = new SelfTimer();
		assertEquals("self: frame -, tick -, step -", t.footer());
		t.add(SelfTimer.FRAME, 180);
		t.add(SelfTimer.TICK, 2_400);
		t.add(SelfTimer.STEP, 40_000);
		assertEquals("self: frame 180 ns, tick 2 us, step 40 us", t.footer());
		t.add(SelfTimer.STEP, 3_960_000);
		assertEquals("self: frame 180 ns, tick 2 us, step 2 ms", t.footer());
	}

	@Test
	public void addAllocatesNothing()
	{
		final com.sun.management.ThreadMXBean bean = SecondRingTest.allocationBean();
		Assume.assumeTrue("this JVM does not count allocated bytes", bean != null);
		final SelfTimer t = new SelfTimer();
		t.on(true);
		for (int i = 0; i < 100_000; i++)
		{
			t.add(i % 3, i);
		}
		final long thread = Thread.currentThread().getId();
		bean.getThreadAllocatedBytes(thread);
		final long before = bean.getThreadAllocatedBytes(thread);
		for (int i = 0; i < 1_000_000; i++)
		{
			if (t.on())
			{
				t.add(i % 3, i & 1023);
			}
		}
		final long after = bean.getThreadAllocatedBytes(thread);
		assertEquals(0, after - before);
	}
}
