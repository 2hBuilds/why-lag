package com.whylag.core;

import java.lang.reflect.Modifier;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * The collection ring (contract 3.3, skeptic C7): pauses are intervals in session ms, joined to a span by overlap;
 * an inferred collection has no length; heap after collection carries forward.
 */
public class GcRingTest
{
	@Test
	public void overlapIsByInterval()
	{
		// 120.950 .. 121.050: it ends in the next second, and each second finds it. A second's span is its first
		// and its last ms (contract 3.3): both ends are included.
		final GcRing ring = new GcRing(8);
		ring.put(120_950, 100, 300);
		assertEquals(100, ring.longestPauseMs(120_000, 120_999));
		assertEquals(100, ring.longestPauseMs(121_000, 121_999));
		assertEquals("the second before is untouched", 0, ring.longestPauseMs(119_000, 119_999));
		// overlapMs is a LENGTH on the time line: half of the pause lies in each second.
		assertEquals(50, ring.overlapMs(120_000, 121_000));
		assertEquals(50, ring.overlapMs(121_000, 122_000));
	}

	/**
	 * Both ends of a span are included, so a span of whole seconds ends at the LAST ms of its last second (contract
	 * 3.3): a pause that starts at the first ms of the next second is not in it; one that starts at the last ms is.
	 */
	@Test
	public void aSecondsSpanEndsAtItsLastMs()
	{
		final GcRing ring = new GcRing(8);
		ring.put(121_000, 150, 400);
		assertEquals("it starts at the first ms of second 121: not in second 120", 0,
			ring.longestPauseMs(120_000, 120_999));
		assertEquals(150, ring.longestPauseMs(121_000, 121_999));
		assertEquals("a span that ended at the next second's first ms would have counted it", 150,
			ring.longestPauseMs(120_000, 121_000));
		ring.put(119_999, 120, 380);
		assertEquals("one that starts at the last ms of second 119 is in it", 120,
			ring.longestPauseMs(119_000, 119_999));
		assertEquals("and, as it runs on into second 120, in that one too", 120, ring.longestPauseMs(120_000, 120_999));
	}

	@Test
	public void overlapMsIsTheCoveredPart()
	{
		// A 150 ms pause, 50 ms of it inside the span.
		final GcRing ring = new GcRing(8);
		ring.put(1_000, 150, 300);
		assertEquals(50, ring.overlapMs(1_100, 1_400));
		assertEquals(150, ring.longestPauseMs(1_100, 1_400));
		assertEquals("a span inside the pause is covered whole", 40, ring.overlapMs(1_020, 1_060));
		// The most covered by ONE pause, not the sum of two.
		ring.put(1_300, 60, 300);
		assertEquals(60, ring.overlapMs(1_100, 1_400));
		assertEquals(150, ring.longestPauseMs(1_100, 1_400));
	}

	@Test
	public void aPauseBesideTheSpanIsNoOverlap()
	{
		// The worked case of contract 6.3: a 150 ms pause 119.900 .. 120.050, then a freeze 120.500 .. 120.740.
		final GcRing ring = new GcRing(8);
		ring.put(119_900, 150, 300);
		assertEquals(0, ring.longestPauseMs(120_500, 120_740));
		assertEquals(0, ring.overlapMs(120_500, 120_740));
		ring.put(120_800, 200, 300);
		assertEquals("a pause just after the span is beside it too", 0, ring.longestPauseMs(120_500, 120_740));
		assertEquals(0, ring.overlapMs(120_500, 120_740));
	}

	@Test
	public void inferredHasNoLength()
	{
		final GcRing ring = new GcRing(8);
		ring.put(120_500, -1, 400);
		assertEquals(-1, ring.durationMs(0));
		assertEquals("it adds nothing to the longest pause", 0, ring.longestPauseMs(120_000, 121_000));
		assertEquals("nor to the overlap", 0, ring.overlapMs(120_000, 121_000));
		assertTrue(ring.inferredIn(120_000, 121_000));
		assertTrue("both ends are included", ring.inferredIn(120_500, 120_500));
		assertFalse(ring.inferredIn(120_501, 121_000));
		assertEquals("its heap after still counts", 400, ring.heapAfterAt(120_500));
		ring.put(130_000, -7, 380);
		assertEquals("any negative length is stored as inferred", -1, ring.durationMs(1));
	}

	@Test
	public void aKnownPauseIsNoInferredCollection()
	{
		final GcRing ring = new GcRing(8);
		ring.put(120_500, 150, 400);
		assertFalse(ring.inferredIn(120_000, 121_000));
	}

	@Test
	public void heapAfterAtCarriesForward()
	{
		final GcRing ring = new GcRing(8);
		assertEquals(-1, ring.heapAfterAt(10_000));
		ring.put(10_000, 5, 300);
		ring.put(20_000, -1, 350);
		assertEquals("none had started", -1, ring.heapAfterAt(9_999));
		assertEquals("at its start", 300, ring.heapAfterAt(10_000));
		assertEquals("carried forward", 300, ring.heapAfterAt(15_000));
		assertEquals(350, ring.heapAfterAt(20_000));
		assertEquals(350, ring.heapAfterAt(99_999));
		// A row written late lands at its start time, not at its place in the ring.
		ring.put(15_000, 8, 320);
		assertEquals(320, ring.heapAfterAt(16_000));
		assertEquals(350, ring.heapAfterAt(21_000));
	}

	@Test
	public void lastHeapAfter()
	{
		final GcRing ring = new GcRing(8);
		assertEquals(-1, ring.lastHeapAfterMb());
		ring.put(10_000, 5, 300);
		assertEquals(300, ring.lastHeapAfterMb());
		ring.put(20_000, -1, 350);
		assertEquals("inferred ones count", 350, ring.lastHeapAfterMb());
	}

	/**
	 * The memory tile's two reads (contract 5.2) end with the newest second: heap after collection is
	 * {@code heapAfterAt} at that second's last ms, and the pause is {@code longestPauseMs} over the window, which
	 * also ends at that last ms. A collection that started later is not seen, even when it was written first, and
	 * even when it started at the very first ms of the next second; {@code lastHeapAfterMb}, the newest row WRITTEN,
	 * would see it, which is why the tile does not read it.
	 */
	@Test
	public void theTilesReadsStopAtTheNewestSecond()
	{
		final GcRing ring = new GcRing(8);
		ring.put(10_000, 23, 300);
		ring.put(50_000, 12, 350);
		// Replayed with second 29 as the newest: the collection at 50 s is not seen.
		assertEquals(300, ring.heapAfterAt(lastMsOf(29)));
		assertEquals(23, ring.longestPauseMs(windowFrom(29), windowTo(29)));
		assertEquals("the newest row WRITTEN looks past second 29", 350, ring.lastHeapAfterMb());
		// Second 59: both are in the window; the heap after is the newer one's.
		assertEquals(350, ring.heapAfterAt(lastMsOf(59)));
		assertEquals(23, ring.longestPauseMs(windowFrom(59), windowTo(59)));
		assertEquals("second 80: the 23 ms pause ended before the window", 12,
			ring.longestPauseMs(windowFrom(80), windowTo(80)));
		// A row that lands late is read at its start time.
		ring.put(25_000, 40, 330);
		assertEquals(330, ring.heapAfterAt(lastMsOf(29)));
		assertEquals(40, ring.longestPauseMs(windowFrom(29), windowTo(29)));
		assertEquals("the newest row written is now an earlier one", 330, ring.lastHeapAfterMb());
		// No pause in the window and a known 0 ms pause both read 0: measured, none ("pause 0 ms").
		assertEquals(0, ring.longestPauseMs(windowFrom(200), windowTo(200)));
		ring.put(190_000, 0, 360);
		assertEquals(0, ring.longestPauseMs(windowFrom(200), windowTo(200)));
		assertEquals(360, ring.heapAfterAt(lastMsOf(200)));

		// The edge: Trace.steady(100).gcPause(80, 0, 150, 400) replayed with second 79 as the newest. The pause
		// starts at the FIRST ms of second 80, so neither read sees it; from second 80 on, both do.
		final GcRing edge = new GcRing(8);
		edge.put(-1_000, -1, 300);
		edge.put(80_000, 150, 400);
		assertEquals(0, edge.longestPauseMs(windowFrom(79), windowTo(79)));
		assertEquals(300, edge.heapAfterAt(lastMsOf(79)));
		assertEquals(150, edge.longestPauseMs(windowFrom(80), windowTo(80)));
		assertEquals(400, edge.heapAfterAt(lastMsOf(80)));
		// One that starts at the LAST ms of second 79 is seen by both at 79.
		edge.put(79_999, 120, 380);
		assertEquals(120, edge.longestPauseMs(windowFrom(79), windowTo(79)));
		assertEquals(380, edge.heapAfterAt(lastMsOf(79)));
	}

	/** The window's first ms when {@code lastSec} is the newest second (contract 3.5). */
	private static long windowFrom(long lastSec)
	{
		return (lastSec - Thresholds.WINDOW_S + 1) * 1000;
	}

	/** The window's end: the LAST ms of the newest second (contract 3.3, 5.2), as an event's span ends (3.4). */
	private static long windowTo(long lastSec)
	{
		return lastMsOf(lastSec);
	}

	/** The last ms of second {@code sec}. */
	private static long lastMsOf(long sec)
	{
		return (sec + 1) * 1000 - 1;
	}

	@Test
	public void tailKeepsOneSlotOfSlack()
	{
		final GcRing ring = new GcRing(5);
		for (int i = 0; i < 5; i++)
		{
			ring.put(1000L * i, 10 + i, 300);
		}
		assertEquals(4, ring.head());
		assertEquals(1, ring.tail());
		assertFalse(ring.valid(0));
		assertTrue(ring.valid(1));
		assertEquals("the pause in the slack slot is not seen", 0, ring.longestPauseMs(0, 5));
		assertEquals(11, ring.longestPauseMs(1000, 1005));
		final GcRing full = gcsOfASession();
		assertEquals("a session keeps its 256 readable pauses", Thresholds.GC_PAUSES, full.head() - full.tail() + 1);
	}

	/**
	 * The notification thread and the sampler can both write while the memory source is swapped. First the
	 * contract's {@code synchronized} on {@code put} itself, which no timing can hide; then the race: two threads,
	 * 2,000 pauses each, and every one of the 4,000 is there, whole.
	 */
	@Test(timeout = 60_000)
	public void twoWritersNeverLoseAPause() throws Exception
	{
		assertTrue("GcRing.put must be synchronized (contract 3.3)",
			Modifier.isSynchronized(GcRing.class.getMethod("put", long.class, int.class, int.class).getModifiers()));
		final GcRing ring = new GcRing(4001);
		final AtomicInteger ready = new AtomicInteger();
		final Thread even = writer(ring, ready, 0);
		final Thread odd = writer(ring, ready, 1);
		even.start();
		odd.start();
		even.join();
		odd.join();
		assertEquals(3999, ring.head());
		assertEquals(0, ring.tail());
		final Set<Long> starts = new HashSet<>();
		for (long q = 0; q <= ring.head(); q++)
		{
			final long start = ring.startMs(q);
			assertEquals("row " + q + " is whole", (int) (start % 97), ring.durationMs(q));
			assertEquals("row " + q + " is whole", (int) (start % 1000), ring.heapAfterMb(q));
			assertTrue("pause at " + start + " twice", starts.add(start));
		}
		assertEquals(4000, starts.size());
	}

	/**
	 * One writer. Both SPIN until both are running, so their puts overlap: a latch wakes two threads tens of
	 * microseconds apart, longer than 2,000 compiled puts take, and the race then mostly misses an unlocked put.
	 */
	private static Thread writer(GcRing ring, AtomicInteger ready, int parity)
	{
		final Thread t = new Thread(() ->
		{
			ready.incrementAndGet();
			while (ready.get() < 2)
			{
				Thread.onSpinWait();
			}
			for (int i = 0; i < 2000; i++)
			{
				final long start = 2L * i + parity;
				ring.put(start, (int) (start % 97), (int) (start % 1000));
			}
		}, "gc-writer-" + parity);
		t.setDaemon(true);
		return t;
	}

	private static GcRing gcsOfASession()
	{
		final GcRing ring = new Session(0, 0, Os.WINDOWS, java.time.ZoneOffset.UTC).gcs;
		for (int i = 0; i < 1000; i++)
		{
			ring.put(i, 1, 1);
		}
		return ring;
	}
}
