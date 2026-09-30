package com.whylag.core;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * The second ring (contract 3.3): wrap, the two writers' heads, the one slot of slack, every column of the clamp
 * table, the reader's re-check under a live writer and its size (T15).
 */
public class SecondRingTest
{
	private static final NoData[] CONN = NoData.values();

	@Test
	public void wrapLosesOnlyTheOldest()
	{
		final SecondRing ring = new SecondRing(10);
		final FrameSecond f = new FrameSecond();
		final HostSecond h = new HostSecond();
		for (int sec = 0; sec < 25; sec++)
		{
			fill(f, h, sec);
			ring.putFrame(sec, f);
			ring.putHost(sec, h);
		}
		assertEquals(24, ring.head());
		assertEquals("capacity - 1 seconds stay readable", 16, ring.tail());
		assertFalse(ring.valid(15));
		for (long sec = 16; sec <= 24; sec++)
		{
			assertTrue(ring.valid(sec));
			assertConsistent(ring, sec);
		}
		assertFalse("not written yet", ring.valid(25));
	}

	@Test
	public void headIsTheSlowerWriter()
	{
		final SecondRing ring = new SecondRing(100);
		assertEquals(-1, ring.head());
		assertFalse(ring.valid(0));
		final FrameSecond f = new FrameSecond();
		final HostSecond h = new HostSecond();
		for (int sec = 0; sec <= 10; sec++)
		{
			ring.putFrame(sec, f);
		}
		for (int sec = 0; sec <= 7; sec++)
		{
			ring.putHost(sec, h);
		}
		assertEquals(10, ring.frameHead());
		assertEquals(7, ring.hostHead());
		assertEquals("a second is readable once BOTH halves are in", 7, ring.head());
		assertTrue(ring.valid(7));
		assertFalse(ring.valid(8));
		for (int sec = 8; sec <= 12; sec++)
		{
			ring.putHost(sec, h);
		}
		assertEquals(10, ring.head());
	}

	/**
	 * Contract 3.3: a put out of order throws and writes NOTHING. The refused puts aim at slots that still hold
	 * readable seconds, and those seconds read back unchanged in every column - so a put that stored its columns
	 * and then threw would fail here.
	 */
	@Test
	public void aPutOutOfOrderIsRefusedAndWritesNothing()
	{
		// An empty ring takes second 0 first, in each half.
		final SecondRing empty = new SecondRing(10);
		refused(() -> empty.putFrame(1, new FrameSecond()));
		assertEquals(-1, empty.frameHead());
		empty.putFrame(0, new FrameSecond());
		refused(() -> empty.putHost(3, new HostSecond()));
		assertEquals(-1, empty.hostHead());

		// Seconds 0 .. 9 in ten slots, every column from its second: frames = sec, rttMs = sec.
		final SecondRing ring = new SecondRing(10);
		final FrameSecond f = new FrameSecond();
		final HostSecond h = new HostSecond();
		for (int sec = 0; sec <= 9; sec++)
		{
			fill(f, h, sec);
			ring.putFrame(sec, f);
			ring.putHost(sec, h);
		}
		// Second 99's columns: frames 99, rttMs 99, and every other column unlike those of the seconds held.
		fill(f, h, 99);
		assertEquals(99, f.frames);
		assertEquals(99, h.rttMs);
		// Ahead (15 aims at slot 5, which holds second 5), behind (5) and again (9): each half is refused.
		for (long sec : new long[] {15, 5, 9})
		{
			refused(() -> ring.putFrame(sec, f));
			refused(() -> ring.putHost(sec, h));
			assertEquals("the frame head stays", 9, ring.frameHead());
			assertEquals("the host head stays", 9, ring.hostHead());
		}
		assertTrue("second 5 is still readable", ring.valid(5));
		assertEquals(5, ring.frames(5));
		assertEquals(5, ring.rttMs(5));
		for (long sec = ring.tail(); sec <= ring.head(); sec++)
		{
			assertConsistent(ring, sec);
		}
	}

	private static void refused(Runnable put)
	{
		try
		{
			put.run();
			fail("a put out of order must throw");
		}
		catch (IllegalArgumentException expected)
		{
			// nothing was written: the caller checks the heads and the columns
		}
	}

	@Test
	public void tailKeepsOneSlotOfSlack()
	{
		final SecondRing ring = new SecondRing(10);
		final FrameSecond f = new FrameSecond();
		final HostSecond h = new HostSecond();
		for (int sec = 0; sec < 10; sec++)
		{
			ring.putFrame(sec, f);
			ring.putHost(sec, h);
		}
		// Ten seconds in ten slots: second 0 is still in its slot, but the NEXT put reuses that slot, so it is
		// already below the tail.
		assertEquals(9, ring.head());
		assertEquals(1, ring.tail());
		assertFalse(ring.valid(0));
		assertTrue(ring.valid(1));
		// The tail follows the FASTER writer: a slot the frame writer is about to reuse is not readable even while
		// the host half of that second is still old.
		ring.putFrame(10, f);
		assertEquals(2, ring.tail());
		assertFalse(ring.valid(1));
	}

	@Test
	public void readableSpanIsTheConstant()
	{
		final Session s = new Session(0, 0, Os.WINDOWS, ZoneOffset.UTC);
		assertEquals(Thresholds.SECONDS + 1, s.seconds.capacity());
		final FrameSecond f = new FrameSecond();
		final HostSecond h = new HostSecond();
		for (int sec = 0; sec < 5000; sec++)
		{
			s.seconds.putFrame(sec, f);
			s.seconds.putHost(sec, h);
		}
		int valid = 0;
		for (long sec = 0; sec < 5000; sec++)
		{
			valid += s.seconds.valid(sec) ? 1 : 0;
		}
		assertEquals(3600, valid);
		assertEquals(3600, s.seconds.head() - s.seconds.tail() + 1);
	}

	@Test
	public void valuesAreClamped()
	{
		final SecondRing ring = new SecondRing(4);
		final FrameSecond f = new FrameSecond();
		f.frames = 40_000;
		f.worstFrameMs = -5;
		f.worstFrameEndMs = 1500;
		f.slowFrames = 300;
		f.state = -3;
		f.flags = 255;
		f.world = 70_000;
		f.players = -1;
		f.region = 70_000;
		ring.putFrame(0, f);
		final HostSecond h = new HostSecond();
		h.rttMs = 99_999;
		h.sentUnits = 5_000_000;
		h.conn = null;
		ring.putHost(0, h);
		assertEquals(32767, ring.frames(0));
		assertEquals("-1 is kept, below it is -1", -1, ring.worstFrameMs(0));
		assertEquals(999, ring.worstFrameEndMs(0));
		assertEquals(127, ring.slowFrames(0));
		assertEquals(0, ring.state(0));
		assertEquals(127, ring.flags(0));
		assertEquals(32767, ring.world(0));
		assertEquals(-1, ring.players(0));
		assertEquals(65535, ring.region(0));
		assertEquals(32767, ring.rttMs(0));
		assertEquals("the unit counters are whole ints", 5_000_000, ring.sentUnits(0));
		assertEquals(NoData.NONE, ring.conn(0));

		f.worstFrameEndMs = -20;
		f.region = -4;
		f.worstFrameMs = -32769;
		ring.putFrame(1, f);
		assertEquals(0, ring.worstFrameEndMs(1));
		assertEquals(0, ring.region(1));
		assertEquals("far below -1 is -1 too, never a wrapped short", -1, ring.worstFrameMs(1));

		// Every column of the clamp table of contract 3.3, three ways: a value over its range, one just under 0
		// (-5), and one far under it (-32769, which a plain cast to short would wrap to 32767).
		final SecondRing all = new SecondRing(8);
		all.putFrame(0, frameOf(99_999));
		all.putHost(0, hostOf(99_999));
		all.putFrame(1, frameOf(-5));
		all.putHost(1, hostOf(-5));
		all.putFrame(2, frameOf(-32_769));
		all.putHost(2, hostOf(-32_769));
		assertEveryColumnClamped(all);
	}

	private static void assertEveryColumnClamped(SecondRing ring)
	{
		for (long sec = 0; sec <= 2; sec++)
		{
			final boolean over = sec == 0;
			// short columns: -1 .. 32767, every negative is -1
			final int shortWant = over ? 32767 : -1;
			assertEquals("frames @" + sec, shortWant, ring.frames(sec));
			assertEquals("worstFrameMs @" + sec, shortWant, ring.worstFrameMs(sec));
			assertEquals("loadingMs @" + sec, shortWant, ring.loadingMs(sec));
			assertEquals("world @" + sec, shortWant, ring.world(sec));
			assertEquals("players @" + sec, shortWant, ring.players(sec));
			assertEquals("npcs @" + sec, shortWant, ring.npcs(sec));
			assertEquals("rttMs @" + sec, shortWant, ring.rttMs(sec));
			assertEquals("rttAgeS @" + sec, shortWant, ring.rttAgeS(sec));
			// worstFrameEndMs: 0 .. 999
			assertEquals("worstFrameEndMs @" + sec, over ? 999 : 0, ring.worstFrameEndMs(sec));
			// byte columns: 0 .. 127, a negative is 0
			assertEquals("slowFrames @" + sec, over ? 127 : 0, ring.slowFrames(sec));
			assertEquals("state @" + sec, over ? 127 : 0, ring.state(sec));
			assertEquals("flags @" + sec, over ? 127 : 0, ring.flags(sec));
			// region: 0 .. 65535, a negative is 0
			assertEquals("region @" + sec, over ? 65_535 : 0, ring.region(sec));
			// conn: the NoData ordinal, null is NONE
			assertEquals("conn @" + sec, NoData.NONE, ring.conn(sec));
		}
		// sentUnits and resentUnits: kept whole
		assertEquals(2_000_000_000, ring.sentUnits(0));
		assertEquals(1_500_000_000, ring.resentUnits(0));
		assertEquals(70_000, ring.sentUnits(1));
		assertEquals(40_000, ring.resentUnits(1));
	}

	/** A frame half with every column set to {@code v}. */
	private static FrameSecond frameOf(int v)
	{
		final FrameSecond f = new FrameSecond();
		f.frames = v;
		f.worstFrameMs = v;
		f.worstFrameEndMs = v;
		f.slowFrames = v;
		f.loadingMs = v;
		f.state = v;
		f.flags = v;
		f.world = v;
		f.players = v;
		f.npcs = v;
		f.region = v;
		return f;
	}

	/** A host half with every short column set to {@code v}; the unit counters whole and past a short. */
	private static HostSecond hostOf(int v)
	{
		final HostSecond h = new HostSecond();
		h.rttMs = v;
		h.rttAgeS = v;
		h.sentUnits = v > 0 ? 2_000_000_000 : 70_000;
		h.resentUnits = v > 0 ? 1_500_000_000 : 40_000;
		h.conn = null;
		return h;
	}

	@Test
	public void regionHoldsSixtyFiveThousand()
	{
		final SecondRing ring = new SecondRing(4);
		final FrameSecond f = new FrameSecond();
		f.region = 65_000;
		ring.putFrame(0, f);
		f.region = 65_535;
		ring.putFrame(1, f);
		ring.putHost(0, new HostSecond());
		ring.putHost(1, new HostSecond());
		assertEquals("more than a short holds: the column is a char", 65_000, ring.region(0));
		assertEquals(65_535, ring.region(1));
	}

	@Test
	public void worstFrameEndIsKept()
	{
		final SecondRing ring = new SecondRing(4);
		final FrameSecond f = new FrameSecond();
		f.worstFrameMs = 450;
		f.worstFrameEndMs = 737;
		ring.putFrame(0, f);
		ring.putHost(0, new HostSecond());
		assertEquals(450, ring.worstFrameMs(0));
		assertEquals(737, ring.worstFrameEndMs(0));
	}

	/**
	 * {@code HostSecond.clear()} sets every int to -1 but {@code sentUnits = resentUnits = 0}, and {@code conn} to
	 * NONE. Every field is set to a value that is neither 0 nor -1 first, then cleared and checked one by one - a
	 * clear() that left {@code rttMs} at 0 would turn every gap-filled second into "ping 0 ms". A new carrier is a
	 * cleared one. The field counts keep this test whole: a new field needs its own lines here.
	 */
	@Test
	public void aNewCarrierIsCleared()
	{
		assertEquals("FrameSecond's fields, each named below", 11, instanceFields(FrameSecond.class));
		assertEquals("HostSecond's fields, each named below", 5, instanceFields(HostSecond.class));

		assertFrameCleared(new FrameSecond());
		final FrameSecond f = new FrameSecond();
		f.frames = 1;
		f.worstFrameMs = 2;
		f.worstFrameEndMs = 3;
		f.slowFrames = 4;
		f.loadingMs = 7;
		f.state = 8;
		f.flags = 9;
		f.world = 10;
		f.players = 11;
		f.npcs = 12;
		f.region = 13;
		f.clear();
		assertFrameCleared(f);

		assertHostCleared(new HostSecond());
		final HostSecond h = new HostSecond();
		h.rttMs = 1;
		h.rttAgeS = 2;
		h.sentUnits = 3;
		h.resentUnits = 4;
		h.conn = NoData.ERROR;
		h.clear();
		assertHostCleared(h);
	}

	private static void assertFrameCleared(FrameSecond f)
	{
		assertEquals("frames", 0, f.frames);
		assertEquals("worstFrameMs", 0, f.worstFrameMs);
		assertEquals("worstFrameEndMs", 0, f.worstFrameEndMs);
		assertEquals("slowFrames", 0, f.slowFrames);
		assertEquals("loadingMs", 0, f.loadingMs);
		assertEquals("state", 0, f.state);
		assertEquals("flags", 0, f.flags);
		assertEquals("world", 0, f.world);
		assertEquals("players", 0, f.players);
		assertEquals("npcs", 0, f.npcs);
		assertEquals("region", 0, f.region);
	}

	private static void assertHostCleared(HostSecond h)
	{
		assertEquals("rttMs", -1, h.rttMs);
		assertEquals("rttAgeS", -1, h.rttAgeS);
		assertEquals("sentUnits", 0, h.sentUnits);
		assertEquals("resentUnits", 0, h.resentUnits);
		assertEquals("conn", NoData.NONE, h.conn);
	}

	/** The instance (non-static) fields a carrier declares. */
	private static int instanceFields(Class<?> type)
	{
		int n = 0;
		for (Field field : type.getDeclaredFields())
		{
			n += Modifier.isStatic(field.getModifiers()) ? 0 : 1;
		}
		return n;
	}

	/**
	 * A writer and a reader, 200,000 seconds through a 16-slot ring. The reader reads AT {@code tail()} on
	 * purpose - the second whose slot is next to be overwritten - and every read that passes the re-check must be
	 * one second, whole: every column agrees on which second it came from.
	 */
	@Test(timeout = 120_000)
	public void readerNeverSeesAHalfWrittenSecond() throws Exception
	{
		final SecondRing ring = new SecondRing(16);
		final int total = 200_000;
		final CountDownLatch start = new CountDownLatch(2);
		final AtomicBoolean done = new AtomicBoolean();
		final AtomicLong accepted = new AtomicLong();
		final AtomicLong thrownAway = new AtomicLong();
		final List<String> torn = Collections.synchronizedList(new ArrayList<>());
		final Thread writer = new Thread(() ->
		{
			final FrameSecond f = new FrameSecond();
			final HostSecond h = new HostSecond();
			await(start);
			for (int sec = 0; sec < total; sec++)
			{
				fill(f, h, sec);
				ring.putFrame(sec, f);
				ring.putHost(sec, h);
			}
			done.set(true);
		}, "ring-writer");
		final Thread reader = new Thread(() ->
		{
			await(start);
			long reads = 0;
			long readsAfterTheWriter = 0;
			// Read while the writer runs, then 1,000 more once it is done, so a read always passes the re-check.
			while (readsAfterTheWriter < 1000)
			{
				if (done.get())
				{
					readsAfterTheWriter++;
				}
				final long sec = (reads & 7) == 7 ? ring.head() : ring.tail();
				reads++;
				if (!ring.valid(sec))
				{
					continue;
				}
				final int[] got = readAll(ring, sec);
				if (!ring.valid(sec))
				{
					thrownAway.incrementAndGet();
					continue;
				}
				accepted.incrementAndGet();
				final int[] want = expected(sec);
				for (int c = 0; c < want.length; c++)
				{
					if (got[c] != want[c] && torn.size() < 10)
					{
						torn.add("second " + sec + " column " + c + ": " + got[c] + " but " + want[c]);
					}
				}
			}
		}, "ring-reader");
		writer.start();
		reader.start();
		writer.join();
		reader.join();
		assertTrue("the reader must have read something", accepted.get() > 0);
		assertEquals("a read that passed the re-check was half written: " + torn, 0, torn.size());
		assertEquals(total - 1, ring.head());
	}

	/** T15: the two rings and the three usuals of a {@link Session} are under 400 KB, in final arrays. */
	@Test
	public void sizes() throws Exception
	{
		final Session s = new Session(0, 0, Os.WINDOWS, ZoneOffset.UTC);
		final long rings = arrayBytes(s.seconds) + arrayBytes(s.ticks);
		final long usuals = arrayBytes(s.rttUsual) + arrayBytes(s.rttSession) + arrayBytes(s.fpsUsual);
		assertTrue("rings are " + rings + " bytes", rings > 200_000);
		assertTrue("rings and usuals are " + (rings + usuals) + " bytes, over 400 KB", rings + usuals < 400_000);
		assertEquals("seconds + 1 slots", Thresholds.SECONDS + 1, arrayLength(s.seconds, "frames"));
		assertEquals("ticks + 1 slots", Thresholds.TICKS + 1, arrayLength(s.ticks, "atMs"));
	}

	// ---------------------------------------------------------------- helpers

	/** Every column of second {@code sec} derived from {@code sec}, inside each column's range. */
	private static void fill(FrameSecond f, HostSecond h, long sec)
	{
		final int[] v = expected(sec);
		f.frames = v[0];
		f.worstFrameMs = v[1];
		f.worstFrameEndMs = v[2];
		f.slowFrames = v[3];
		f.loadingMs = v[4];
		f.state = v[5];
		f.flags = v[6];
		f.world = v[7];
		f.players = v[8];
		f.npcs = v[9];
		f.region = v[10];
		h.rttMs = v[11];
		h.rttAgeS = v[12];
		h.sentUnits = v[13];
		h.resentUnits = v[14];
		h.conn = CONN[v[15]];
	}

	private static int[] expected(long sec)
	{
		final int s = (int) sec;
		return new int[] {
			s % 1000, s % 30_000, s % 1000, s % 128, s % 997, s % 7, s % 128, s % 600, s % 2000, s % 3000,
			s % 65_536, s % 2000, s % 100, s, s ^ 0x5555, s % CONN.length};
	}

	private static int[] readAll(SecondRing r, long sec)
	{
		return new int[] {
			r.frames(sec), r.worstFrameMs(sec), r.worstFrameEndMs(sec), r.slowFrames(sec), r.loadingMs(sec),
			r.state(sec), r.flags(sec), r.world(sec), r.players(sec), r.npcs(sec), r.region(sec), r.rttMs(sec),
			r.rttAgeS(sec), r.sentUnits(sec), r.resentUnits(sec), r.conn(sec).ordinal()};
	}

	private static void assertConsistent(SecondRing ring, long sec)
	{
		final int[] got = readAll(ring, sec);
		final int[] want = expected(sec);
		for (int c = 0; c < want.length; c++)
		{
			assertEquals("second " + sec + " column " + c, want[c], got[c]);
		}
	}

	private static void await(CountDownLatch latch)
	{
		latch.countDown();
		try
		{
			latch.await();
		}
		catch (InterruptedException e)
		{
			Thread.currentThread().interrupt();
		}
	}

	/** Bytes held by every array field of {@code o}; each such field must be final (allocated once). */
	static long arrayBytes(Object o) throws IllegalAccessException
	{
		long bytes = 0;
		for (Field field : o.getClass().getDeclaredFields())
		{
			if (!field.getType().isArray() || Modifier.isStatic(field.getModifiers()))
			{
				continue;
			}
			assertTrue(o.getClass().getSimpleName() + "." + field.getName() + " must be final",
				Modifier.isFinal(field.getModifiers()));
			field.setAccessible(true);
			final Object array = field.get(o);
			final Class<?> type = field.getType().getComponentType();
			final int length = java.lang.reflect.Array.getLength(array);
			bytes += (long) length * elementSize(type);
		}
		return bytes;
	}

	private static int arrayLength(Object o, String name) throws ReflectiveOperationException
	{
		final Field field = o.getClass().getDeclaredField(name);
		field.setAccessible(true);
		return java.lang.reflect.Array.getLength(field.get(o));
	}

	private static int elementSize(Class<?> type)
	{
		if (type == byte.class || type == boolean.class)
		{
			return 1;
		}
		if (type == short.class || type == char.class)
		{
			return 2;
		}
		if (type == int.class || type == float.class)
		{
			return 4;
		}
		return 8;
	}
}
