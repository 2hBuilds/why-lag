package com.whylag.core;

import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * The tick ring (contract 3.3): wrap, the one slot of slack, the corrected deviation that takes the frame interval
 * out of a tick's lateness (skeptic C5), its row of the clamp table, and tick times in session ms as a {@code long}
 * that holds more than 24.8 days.
 */
public class TickRingTest
{
	@Test
	public void wrap()
	{
		final TickRing ring = new TickRing(8);
		assertEquals(-1, ring.head());
		assertFalse(ring.valid(0));
		final TickRow row = new TickRow();
		for (int i = 0; i < 20; i++)
		{
			row.atMs = 600 * i;
			row.gapMs = 600 + i;
			row.frameMs = i;
			row.cycleJump = 30 + i;
			row.rttMs = 40 + i;
			row.flags = i % 128;
			ring.put(row);
		}
		assertEquals(19, ring.head());
		assertEquals("capacity - 1 ticks stay readable", 13, ring.tail());
		assertFalse(ring.valid(12));
		for (long q = 13; q <= 19; q++)
		{
			assertTrue(ring.valid(q));
			assertEquals(600 * q, ring.atMs(q));
			assertEquals(600 + q, ring.gapMs(q));
			assertEquals(q, ring.frameMs(q));
			assertEquals(30 + q, ring.cycleJump(q));
			assertEquals(40 + q, ring.rttMs(q));
			assertEquals(q, ring.flags(q));
		}
	}

	@Test
	public void tailKeepsOneSlotOfSlack()
	{
		final TickRing ring = new TickRing(5);
		final TickRow row = new TickRow();
		for (int i = 0; i < 5; i++)
		{
			ring.put(row);
		}
		// Five ticks in five slots: tick 0 is still in its slot, but the next put reuses it, so it is not readable.
		assertEquals(4, ring.head());
		assertEquals(1, ring.tail());
		assertFalse(ring.valid(0));
		assertTrue(ring.valid(1));
	}

	@Test
	public void aSessionsRingHoldsSixThousandTicks()
	{
		final TickRing ring = new Session(0, 0, Os.WINDOWS, java.time.ZoneOffset.UTC).ticks;
		final TickRow row = new TickRow();
		for (int i = 0; i < 10_000; i++)
		{
			ring.put(row);
		}
		assertEquals(Thresholds.TICKS, ring.head() - ring.tail() + 1);
	}

	@Test
	public void correctedSubtractsTheFrameInterval()
	{
		final TickRing ring = new TickRing(16);
		final long steady = put(ring, 600, 22);
		final long lateByAFrame = put(ring, 725, 125);
		final long lateByMore = put(ring, 900, 250);
		final long earlyAfterAStall = put(ring, 450, 20);
		final long frameLongerThanTheGap = put(ring, 610, 400);
		final long noFrame = put(ring, 900, 0);
		assertEquals("600 on the dot", 0, ring.corrected(steady));
		assertEquals("late by exactly one frame at 8 fps is the client's pacing (C5)", 0, ring.corrected(lateByAFrame));
		assertEquals("300 late, 250 of it one frame", 50, ring.corrected(lateByMore));
		assertEquals("early counts too: |450 - 600| - 20", 130, ring.corrected(earlyAfterAStall));
		assertEquals("never below 0", 0, ring.corrected(frameLongerThanTheGap));
		assertEquals(300, ring.corrected(noFrame));
	}

	@Test
	public void lateAndEarly()
	{
		final TickRing ring = new TickRing(16);
		final long late = put(ring, 601, 22);
		final long onTime = put(ring, 600, 22);
		final long early = put(ring, 599, 22);
		final long veryEarly = put(ring, 300, 22);
		assertTrue(ring.late(late));
		assertFalse("600 is not late", ring.late(onTime));
		assertFalse(ring.late(early));
		assertFalse(ring.late(veryEarly));
		assertFalse("a late tick is not early", ring.early(late));
		assertFalse("600 is neither late nor early", ring.early(onTime));
		assertTrue("599 is early", ring.early(early));
		assertTrue(ring.early(veryEarly));
		assertEquals("the line of both is TICK_MS", 600, Thresholds.TICK_MS);
		assertEquals("an early tick is off by its distance from 600", 278, ring.corrected(veryEarly));
	}

	@Test
	public void valuesAreClamped()
	{
		final TickRing ring = new TickRing(4);
		final TickRow row = new TickRow();
		row.atMs = 3_000_000;
		row.gapMs = 90_000;
		row.frameMs = 70_000;
		row.cycleJump = -9;
		row.rttMs = 40_000;
		row.flags = 300;
		ring.put(row);
		assertEquals("atMs is whole", 3_000_000, ring.atMs(0));
		assertEquals("gapMs is whole", 90_000, ring.gapMs(0));
		assertEquals(32767, ring.frameMs(0));
		assertEquals(-1, ring.cycleJump(0));
		assertEquals(32767, ring.rttMs(0));
		assertEquals(127, ring.flags(0));

		// The other ends: every negative short is -1 (far below it too), a negative flag byte is 0.
		row.gapMs = Integer.MAX_VALUE;
		row.frameMs = -32_769;
		row.cycleJump = 40_000;
		row.rttMs = -5;
		row.flags = -3;
		ring.put(row);
		assertEquals("gapMs is whole", Integer.MAX_VALUE, ring.gapMs(1));
		assertEquals(-1, ring.frameMs(1));
		assertEquals(32767, ring.cycleJump(1));
		assertEquals(-1, ring.rttMs(1));
		assertEquals(0, ring.flags(1));
	}

	/** 3,000,000,000 ms (about 34.7 days) goes in and comes out: an int would have wrapped at 24.8 days. */
	@Test
	public void atMsHoldsMoreThanTwentyFiveDays()
	{
		final TickRing ring = new TickRing(4);
		final TickRow row = new TickRow();
		row.atMs = 3_000_000_000L;
		row.gapMs = 600;
		ring.put(row);
		row.atMs = 3_000_000_600L;
		ring.put(row);
		assertEquals(3_000_000_000L, ring.atMs(0));
		assertEquals(3_000_000_600L, ring.atMs(1));
		assertTrue(ring.atMs(0) > Integer.MAX_VALUE);
		row.atMs = Long.MAX_VALUE;
		ring.put(row);
		assertEquals("kept whole", Long.MAX_VALUE, ring.atMs(2));
	}

	private static long put(TickRing ring, int gapMs, int frameMs)
	{
		final TickRow row = new TickRow();
		row.gapMs = gapMs;
		row.frameMs = frameMs;
		ring.put(row);
		return ring.head();
	}
}
