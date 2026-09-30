package com.whylag.host;

import com.sun.management.GarbageCollectionNotificationInfo;
import com.sun.management.GcInfo;
import com.whylag.core.MemorySource;
import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryPoolMXBean;
import java.lang.management.MemoryType;
import java.lang.management.MemoryUsage;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import javax.management.ListenerNotFoundException;
import javax.management.Notification;
import javax.management.NotificationEmitter;
import javax.management.openmbean.CompositeData;
import org.junit.After;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * The management probe (contract 7, L7) against this JVM's real beans: real heap, CPU and thread-clock numbers; a
 * real collection reported to the sink, its start in SESSION ms, with the gap between the {@code GcInfo} clock and
 * the uptime learned (the probe's "two clocks"); the action allow-list (skeptic C8); heap after as the sum of
 * {@code getMemoryUsageAfterGc()} used over the HEAP pools only; the listeners removed by {@code stop}; and a thread
 * clock that allocates nothing (T3). Test code may sleep, spin and call {@code System.gc()}.
 *
 * <p>Choice: threadCpuDoesNotAllocate counts 1,000,000 reads after 100,000, the counts of T1's frame-path test.
 * <p>Choice: a spinning thread must read 25 % of ONE core or more (a share of all 20 cores here would read about 5).
 * <p>Choice: work must move the thread clock by 30 ms, within 5 s; CPU readings are polled for up to 5 s.
 * <p>Choice: pauseStartIsSessionMs puts the session's start 3 s before the call, so an age-blind start is 3 s off.
 * <p>Choice: readsRealNumbers holds 16 MB live while it reads the heap (a bare JVM reads 0 whole MB after a collection).
 * <p>Choice: hand-fed tests take a real GcInfo, set the sink by {@code arm} with no listener, and pick each receipt uptime.
 * <p>Choice: theLearnedOffsetCoversTheJvmsStartUp allows 2 ms for the rounding of the three ms clocks it compares.
 *
 * <p>{@code arm} adds no listener to this JVM's collectors, so no collection of the test JVM can reach a hand-fed
 * test's sink or teach its probe's clock offset: the hand-fed tests are exact under any load.
 */
public class ManagementHostProbeTest
{
	private static final long MB = 1024L * 1024L;
	private static final long NANOS_PER_MS = 1_000_000L;
	/** How long a test waits for a notification (contract 7, L7: "waits up to 5 s for the sink"). */
	private static final long WAIT_MS = 5000;
	/** What readsRealNumbers holds live while it reads the heap, in MB. */
	private static final int HELD_MB = 16;
	/** What the ms clocks' rounding can take off a comparison of the uptime with a GcInfo time. */
	private static final long ROUNDING_MS = 2;

	private ManagementHostProbe probe;
	private ManagementHostProbe other;
	/** A block held live in a field while readsRealNumbers reads the heap; dropped after each test. */
	private byte[] held;

	@After
	public void stopTheProbes()
	{
		held = null;
		if (probe != null)
		{
			probe.stop();
		}
		if (other != null)
		{
			other.stop();
		}
	}

	@Test
	public void readsRealNumbers() throws InterruptedException
	{
		probe = new ManagementHostProbe();
		assertEquals(MemorySource.MANAGEMENT, probe.source());

		final MemoryUsage heap = ManagementFactory.getMemoryMXBean().getHeapMemoryUsage();
		assertEquals("the limit is the memory bean's", heap.getMax() / MB, probe.heapMaxMb());
		held = new byte[HELD_MB * (int) MB];
		final int used = probe.heapUsedMb();
		assertTrue("used " + used + " MB, with " + held.length / MB + " MB held live",
			used >= HELD_MB && used <= probe.heapMaxMb());
		held = null;
		assertEquals(Runtime.getRuntime().availableProcessors(), probe.cores());

		final long before = probe.currentThreadCpuNanos();
		assertTrue("the thread clock runs: " + before, before > 0);
		final long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(WAIT_MS);
		long after = before;
		while (after - before < 30 * NANOS_PER_MS && System.nanoTime() < deadline)
		{
			spin(20);
			after = probe.currentThreadCpuNanos();
		}
		assertTrue("work on this thread moves its clock by 30 ms: " + (after - before), after - before >= 30 * NANOS_PER_MS);

		assertTrue(probe.start(new Pauses(), System.nanoTime()));
		assertEquals("the first system reading after start is thrown away", -1, probe.systemCpuPct());
		int system = -1;
		int process = -1;
		final long end = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(WAIT_MS);
		while ((system < 0 || process < 0) && System.nanoTime() < end)
		{
			spin(100);
			system = system < 0 ? probe.systemCpuPct() : system;
			process = process < 0 ? probe.processCpuPct() : process;
		}
		assertTrue("the whole PC, 0 .. 100: " + system, system >= 0 && system <= 100);
		assertTrue("the process, 0 .. 100 x cores: " + process, process >= 0 && process <= 100 * probe.cores());
	}

	/**
	 * A thread that spins between two readings uses about one core; the process reads as a share of ONE core
	 * (load x cores x 100, C14), so it reads far more than the 100 / cores that a share of all cores would give.
	 * The bean may answer -1 for a while (on Windows it finds this process's counter by a "java#N" instance that
	 * moves when other JVMs start or stop), so the test takes the highest of up to ten readings, each after 300 ms
	 * of spinning.
	 */
	@Test
	public void processCpuIsAShareOfOneCore()
	{
		probe = new ManagementHostProbe();
		probe.processCpuPct();
		int highest = -1;
		for (int i = 0; i < 10 && highest < 25; i++)
		{
			spin(300);
			highest = Math.max(highest, probe.processCpuPct());
		}
		assertTrue("one spinning thread is a good part of ONE core, not of the whole PC: " + highest + " % ("
			+ probe.cores() + " cores)", highest >= 25 && highest <= 100 * probe.cores());
	}

	@Test
	public void cpuPercentagesAreRoundedAndHeld()
	{
		assertEquals("one full core of 20 is 100 % of a core, not 5", 100, ManagementHostProbe.pct(0.05, 20));
		assertEquals("all 20 cores", 2000, ManagementHostProbe.pct(1.0, 20));
		assertEquals("held at 100 x cores", 2000, ManagementHostProbe.pct(1.3, 20));
		assertEquals("the whole PC: 0.374 is 37 %", 37, ManagementHostProbe.pct(0.374, 1));
		assertEquals("rounded to the nearest", 38, ManagementHostProbe.pct(0.375, 1));
		assertEquals(100, ManagementHostProbe.pct(1.0, 1));
		assertEquals(0, ManagementHostProbe.pct(0.0, 1));
		assertEquals("the bean's 'not available'", -1, ManagementHostProbe.pct(-1.0, 1));
		assertEquals(-1, ManagementHostProbe.pct(Double.NaN, 20));
	}

	@Test
	public void theFirstSystemReadingAfterStartIsThrownAway() throws InterruptedException
	{
		probe = new ManagementHostProbe();
		assertEquals("a new probe throws its first reading away", -1, probe.systemCpuPct());
		assertTrue("the bean then answers", realSystemReading(probe) >= 0);

		assertTrue(probe.start(new Pauses(), System.nanoTime()));
		assertEquals("a new start throws the next reading away, though the bean now answers", -1,
			probe.systemCpuPct());
		assertTrue("and then it reads again", realSystemReading(probe) >= 0);
	}

	@Test
	public void aGcIsReported() throws InterruptedException
	{
		probe = new ManagementHostProbe();
		final Pauses pauses = new Pauses();
		assertTrue(probe.start(pauses, System.nanoTime() - TimeUnit.SECONDS.toNanos(2)));
		System.gc();
		assertTrue("a pause reaches the sink within 5 s", pauses.await(1));
		final long[] row = pauses.rows.get(0);
		assertTrue("start " + row[0], row[0] >= 0);
		assertTrue("duration " + row[1], row[1] >= 0);
		assertTrue("heap after " + row[2] + " MB, the heap alone: 0 .. the limit " + probe.heapMaxMb(),
			row[2] >= 0 && row[2] <= probe.heapMaxMb());
		assertTrue("reported on the JVM's notification thread, not this one", pauses.threads.stream()
			.noneMatch(t -> t == Thread.currentThread()));
	}

	/**
	 * The session began 3 s before the call, so a start taken from the JVM's uptime alone would read about 3 s too
	 * early; the reported start lies between 0 and the session's age, within 50 ms of when {@code System.gc()} was
	 * called. The difference is printed. With the clock offset learned (the probe's "two clocks") it is the
	 * collection's own wait for a safepoint plus its notification's delivery delay, a few ms, however long the JVM
	 * took to start. Before the offset it was the JVM's start-up time, EARLY: -20 to -45 ms on an idle PC, and far
	 * more in a JVM started on a busy one (-173 to -287 ms measured, each a failure of this test).
	 */
	@Test
	public void pauseStartIsSessionMs() throws InterruptedException
	{
		probe = new ManagementHostProbe();
		final Pauses pauses = new Pauses();
		final long sessionStart = System.nanoTime() - TimeUnit.SECONDS.toNanos(3);
		assertTrue(probe.start(pauses, sessionStart));
		pauses.rows.clear();
		final long calledAtMs = (System.nanoTime() - sessionStart) / NANOS_PER_MS;
		System.gc();
		assertTrue(pauses.await(1));
		final long ageMs = (System.nanoTime() - sessionStart) / NANOS_PER_MS;
		long nearest = Long.MAX_VALUE;
		long nearestStart = -1;
		for (long[] row : pauses.rows)
		{
			assertTrue("between 0 and the session's age " + ageMs + ": " + row[0], row[0] >= 0 && row[0] <= ageMs);
			if (Math.abs(row[0] - calledAtMs) < nearest)
			{
				nearest = Math.abs(row[0] - calledAtMs);
				nearestStart = row[0];
			}
		}
		System.out.println("pauseStartIsSessionMs: System.gc() called at session ms " + calledAtMs
			+ ", the pause reported at " + nearestStart + " (" + (nearestStart - calledAtMs) + " ms), clock offset "
			+ probe.gcClockOffsetMs() + " ms");
		assertTrue("within 50 ms of the call: " + nearest + " ms", nearest <= 50);
	}

	/**
	 * {@code GcInfo} counts from the END of the JVM's start-up, the uptime from its first instant (the probe's "two
	 * clocks"). The collection that {@code System.gc()} runs begins after the call, so when it began the uptime had
	 * already run at least (the call's uptime - the collection's GcInfo start) past GcInfo's zero. The offset the
	 * probe learns is never less than that, less the clocks' rounding: so a pause is never reported before it began.
	 * A start taken from GcInfo with no offset reads the whole of it early (20 to 45 ms on an idle PC; the figure is
	 * printed). On a busy PC the collection can begin well after the call, which only makes the bound easier, so the
	 * test holds under any load.
	 */
	@Test
	public void theLearnedOffsetCoversTheJvmsStartUp() throws InterruptedException
	{
		probe = new ManagementHostProbe();
		final Pauses pauses = new Pauses();
		assertTrue(probe.start(pauses, System.nanoTime() - TimeUnit.SECONDS.toNanos(1)));
		final long calledAtUptimeMs = ManagementFactory.getRuntimeMXBean().getUptime();
		System.gc();
		final GcInfo gc = newestGcInfo();
		assertNotNull("System.gc() ran a collection", gc);
		assertTrue("a pause reaches the sink within 5 s", pauses.await(1));
		final long aheadAtTheCallMs = calledAtUptimeMs - gc.getStartTime();
		final long offsetMs = probe.gcClockOffsetMs();
		System.out.println("theLearnedOffsetCoversTheJvmsStartUp: at the call the uptime was " + aheadAtTheCallMs
			+ " ms ahead of the GcInfo clock; the probe's offset is " + offsetMs + " ms");
		assertNotEquals("learned from a pause", ManagementHostProbe.NO_OFFSET, offsetMs);
		assertTrue("the offset " + offsetMs + " ms covers the " + aheadAtTheCallMs + " ms the uptime had run ahead",
			offsetMs >= aheadAtTheCallMs - ROUNDING_MS);
	}

	/**
	 * The clock offset through the real decoding path, each pause received at an uptime the test picks: the offset
	 * is the least receipt - end of the pauses seen; each start adds the offset of its moment; a pause that took
	 * longer to arrive does not raise it, and a notification off the allow-list teaches it nothing; a new session
	 * keeps it; and the pre-session rule reads the corrected start (0 kept, -1 dropped).
	 */
	@Test
	public void aPauseStartAddsTheLeastClockGapSeen()
	{
		System.gc();
		final GcInfo real = newestGcInfo();
		assertNotNull("this JVM has collected at least once", real);
		probe = new ManagementHostProbe();
		assertEquals("nothing learned before the first pause", ManagementHostProbe.NO_OFFSET, probe.gcClockOffsetMs());
		final Pauses pauses = new Pauses();
		probe.arm(pauses, 0);
		final long start = real.getStartTime();
		final long end = real.getEndTime();

		probe.onNotification(gcNotification(ManagementHostProbe.MAJOR_GC, real), end + 30);
		assertEquals(30, probe.gcClockOffsetMs());
		assertEquals("the GcInfo start + 30, less the session's origin 0", start + 30, pauses.rows.get(0)[0]);
		assertEquals("its length", real.getDuration(), pauses.rows.get(0)[1]);
		probe.onNotification(gcNotification(ManagementHostProbe.MINOR_GC, real), end + 25);
		assertEquals("a quicker delivery lowers it", 25, probe.gcClockOffsetMs());
		assertEquals(start + 25, pauses.rows.get(1)[0]);
		probe.onNotification(gcNotification(ManagementHostProbe.MAJOR_GC, real), end + 40);
		assertEquals("a slower delivery does not raise it", 25, probe.gcClockOffsetMs());
		assertEquals(start + 25, pauses.rows.get(2)[0]);
		probe.onNotification(gcNotification("end of GC cycle", real), end + 5);
		assertEquals("a notification off the allow-list teaches nothing", 25, probe.gcClockOffsetMs());
		assertEquals(3, pauses.rows.size());

		probe.stop();
		probe.arm(pauses, start + 25);
		probe.onNotification(gcNotification(ManagementHostProbe.MAJOR_GC, real), end + 60);
		assertEquals("a new session keeps what was learned", 25, probe.gcClockOffsetMs());
		assertEquals("began at the session's first ms: kept", 4, pauses.rows.size());
		assertEquals("at 0", 0, pauses.rows.get(3)[0]);
		probe.arm(pauses, start + 26);
		probe.onNotification(gcNotification(ManagementHostProbe.MAJOR_GC, real), end + 60);
		assertEquals("began 1 ms before the session: dropped", 4, pauses.rows.size());
	}

	/** One collection, two probes: the one whose session began before it reports it; the other drops it. */
	@Test
	public void aPauseBeforeTheSessionIsDropped() throws InterruptedException
	{
		probe = new ManagementHostProbe();
		other = new ManagementHostProbe();
		final Pauses started = new Pauses();
		final Pauses notYet = new Pauses();
		assertTrue(probe.start(started, System.nanoTime() - TimeUnit.SECONDS.toNanos(1)));
		assertTrue("listening, with a session that starts in a minute",
			other.start(notYet, System.nanoTime() + TimeUnit.SECONDS.toNanos(60)));
		System.gc();
		assertTrue("the collection was sent", started.await(1));
		Thread.sleep(300);
		assertTrue("it began before the other session: dropped", notYet.rows.isEmpty());
	}

	/**
	 * Heap after is the sum of {@code getMemoryUsageAfterGc()} used over the HEAP pools (the probe's "heap after"),
	 * through the real decoding path with a real collection's map. That map holds the non-heap pools too (Metaspace,
	 * the code heaps): the test checks that the two sums differ, so a probe that summed the whole map fails it. The
	 * probe names the same heap pools as the memory pool beans do.
	 */
	@Test
	public void heapAfterIsTheHeapPoolsOfARealCollection()
	{
		System.gc();
		final GcInfo real = newestGcInfo();
		assertNotNull("this JVM has collected at least once", real);
		probe = new ManagementHostProbe();
		final Set<String> heapPools = heapPoolNames();
		assertFalse("this JVM names its heap pools", heapPools.isEmpty());
		assertEquals("the probe named the same heap pools", heapPools, probe.heapPools());

		long heapBytes = 0;
		long allBytes = 0;
		for (Map.Entry<String, MemoryUsage> e : real.getMemoryUsageAfterGc().entrySet())
		{
			allBytes += e.getValue().getUsed();
			heapBytes += heapPools.contains(e.getKey()) ? e.getValue().getUsed() : 0;
		}
		System.out.println("heapAfterIsTheHeapPoolsOfARealCollection: the whole map " + allBytes / MB
			+ " MB, of which the heap pools " + heapBytes / MB + " MB and the non-heap pools "
			+ (allBytes - heapBytes) / MB + " MB");
		assertTrue("the map holds non-heap pools too, so the two sums differ: " + (allBytes - heapBytes),
			allBytes / MB > heapBytes / MB);

		final Pauses pauses = new Pauses();
		probe.arm(pauses, 0);
		probe.onNotification(gcNotification(ManagementHostProbe.MAJOR_GC, real), real.getEndTime() + 30);
		assertEquals(1, pauses.rows.size());
		final long[] row = pauses.rows.get(0);
		assertEquals("the heap pools' sum", heapBytes / MB, row[2]);
		assertNotEquals("not the whole map's", allBytes / MB, row[2]);
		assertTrue("under the heap limit " + probe.heapMaxMb() + ": " + row[2], row[2] <= probe.heapMaxMb());
		assertEquals("its length", real.getDuration(), row[1]);
	}

	@Test
	public void heapAfterSumsTheHeapPoolsOnly()
	{
		final Set<String> heap = Set.of("G1 Eden Space", "G1 Old Gen", "G1 Survivor Space");
		final Map<String, MemoryUsage> after = new HashMap<>();
		after.put("G1 Eden Space", usage(0));
		after.put("G1 Old Gen", usage(300 * MB + MB / 2));
		after.put("G1 Survivor Space", usage(8 * MB));
		after.put("Metaspace", usage(120 * MB));
		after.put("CodeHeap 'non-profiled nmethods'", usage(40 * MB));
		after.put("Compressed Class Space", usage(15 * MB));
		assertEquals("308.5 MB of heap, rounded down; the 175 MB of non-heap pools are left out", 308,
			ManagementHostProbe.heapAfterMb(after, heap));
		after.put("G1 Eden Space", null);
		assertEquals("a heap pool with no usage is skipped", 308, ManagementHostProbe.heapAfterMb(after, heap));
		after.remove("G1 Survivor Space");
		assertEquals("a heap pool the map does not hold is skipped", 300, ManagementHostProbe.heapAfterMb(after, heap));
		assertEquals("non-heap pools alone: no figure", -1,
			ManagementHostProbe.heapAfterMb(Map.of("Metaspace", usage(120 * MB)), heap));
		final Map<String, MemoryUsage> noUsage = new HashMap<>();
		noUsage.put("G1 Old Gen", null);
		assertEquals("no heap pool with a usage: no figure", -1, ManagementHostProbe.heapAfterMb(noUsage, heap));
		assertEquals("an empty map: no figure", -1, ManagementHostProbe.heapAfterMb(Map.of(), heap));
		assertEquals("a JVM that names no heap pool: no figure", -1, ManagementHostProbe.heapAfterMb(after, Set.of()));
		assertEquals(-1, ManagementHostProbe.heapAfterMb(null, heap));
		assertEquals(-1, ManagementHostProbe.heapAfterMb(after, null));
	}

	/**
	 * The allow-list (C8), through the real decoding path: a real GcInfo wrapped in notifications that differ by
	 * their action alone. Only "end of minor GC" and "end of major GC" reach the sink; a concurrent cycle, an unknown
	 * action, another notification type and a notification with no data do not.
	 */
	@Test
	public void onlyTheTwoPauseActionsCount()
	{
		System.gc();
		final GcInfo real = newestGcInfo();
		assertNotNull("this JVM has collected at least once", real);
		probe = new ManagementHostProbe();
		final Pauses pauses = new Pauses();
		probe.arm(pauses, 0);
		final long receivedAt = real.getEndTime() + 30;
		final long expectedStart = real.getStartTime() + 30;

		int expected = 0;
		for (String action : new String[] {"end of minor GC", "end of major GC"})
		{
			probe.onNotification(gcNotification(action, real), receivedAt);
			expected++;
			assertEquals(action + " is a pause", expected, pauses.startingAt(expectedStart));
		}
		for (String action : new String[] {"end of GC cycle", "end of GC pause", "end of concurrent GC pause",
			"END OF MAJOR GC", "", "end of minor GC "})
		{
			probe.onNotification(gcNotification(action, real), receivedAt);
			assertEquals("'" + action + "' is not on the allow-list", expected, pauses.startingAt(expectedStart));
		}
		final Notification otherType = new Notification("jmx.attribute.change", "test", 1);
		otherType.setUserData(gcNotification(ManagementHostProbe.MAJOR_GC, real).getUserData());
		probe.onNotification(otherType, receivedAt);
		probe.onNotification(new Notification(GarbageCollectionNotificationInfo.GARBAGE_COLLECTION_NOTIFICATION,
			"test", 2), receivedAt);
		assertEquals("another type, or no data: nothing", expected, pauses.startingAt(expectedStart));
		assertEquals("and nothing else either", expected, pauses.rows.size());

		assertTrue(ManagementHostProbe.isPause(ManagementHostProbe.MINOR_GC));
		assertTrue(ManagementHostProbe.isPause(ManagementHostProbe.MAJOR_GC));
		assertFalse(ManagementHostProbe.isPause(null));
	}

	@Test
	public void stopRemovesEveryListener() throws InterruptedException
	{
		probe = new ManagementHostProbe();
		final Pauses pauses = new Pauses();
		assertTrue(probe.start(pauses, System.nanoTime() - TimeUnit.SECONDS.toNanos(1)));
		assertTrue(probe.collectorCount() > 0);
		assertEquals("one listener on every collector that sends", probe.collectorCount(), probe.listenerCount());
		System.gc();
		assertTrue("started, a collection reaches the sink", pauses.await(1));

		probe.stop();
		assertEquals(0, probe.listenerCount());
		int removed = 0;
		for (GarbageCollectorMXBean bean : ManagementFactory.getGarbageCollectorMXBeans())
		{
			if (!(bean instanceof NotificationEmitter))
			{
				continue;
			}
			try
			{
				((NotificationEmitter) bean).removeNotificationListener(probe.listener());
				fail(bean.getName() + " still held the probe's listener after stop");
			}
			catch (ListenerNotFoundException expected)
			{
				removed++;
			}
		}
		assertEquals("gone from every collector", probe.collectorCount(), removed);
		pauses.rows.clear();
		System.gc();
		Thread.sleep(300);
		assertTrue("a collection after stop reaches no sink", pauses.rows.isEmpty());
		probe.stop();
		assertEquals("stop is safe twice", 0, probe.listenerCount());
	}

	@Test
	public void aSecondStartDoesNotDoubleTheListeners() throws InterruptedException
	{
		probe = new ManagementHostProbe();
		final Pauses pauses = new Pauses();
		final long sessionStart = System.nanoTime() - TimeUnit.SECONDS.toNanos(1);
		assertTrue(probe.start(pauses, sessionStart));
		assertTrue(probe.start(pauses, sessionStart));
		assertEquals(probe.collectorCount(), probe.listenerCount());
		pauses.rows.clear();
		System.gc();
		assertTrue(pauses.await(1));
		Thread.sleep(300);
		final Set<String> seen = new HashSet<>();
		for (long[] row : pauses.rows)
		{
			assertTrue("reported once, not twice: " + row[0] + " ms, " + row[1] + " ms", seen.add(row[0] + "/" + row[1]));
		}
	}

	@Test
	public void aNullSinkAddsNothing()
	{
		probe = new ManagementHostProbe();
		assertFalse(probe.start(null, System.nanoTime()));
		assertEquals(0, probe.listenerCount());
	}

	/** T3: the thread clock is read once a frame on the client thread; 1,000,000 reads allocate 0 bytes. */
	@Test
	public void threadCpuDoesNotAllocate()
	{
		probe = new ManagementHostProbe();
		final com.sun.management.ThreadMXBean threads =
			(com.sun.management.ThreadMXBean) ManagementFactory.getThreadMXBean();
		assertTrue(threads.isThreadAllocatedMemorySupported() && threads.isThreadAllocatedMemoryEnabled());
		final long id = Thread.currentThread().getId();
		long sum = 0;
		for (int i = 0; i < 100_000; i++)
		{
			sum += probe.currentThreadCpuNanos();
		}
		threads.getThreadAllocatedBytes(id);
		final long before = threads.getThreadAllocatedBytes(id);
		for (int i = 0; i < 1_000_000; i++)
		{
			sum += probe.currentThreadCpuNanos();
		}
		final long after = threads.getThreadAllocatedBytes(id);
		assertTrue("the clock answered", sum > 0);
		assertEquals("bytes allocated by 1,000,000 reads", 0, after - before);
	}

	// ---------------------------------------------------------------- helpers

	/** Every pause handed to the sink, and the thread that handed it. */
	private static final class Pauses implements GcSink
	{
		final List<long[]> rows = new CopyOnWriteArrayList<>();
		final List<Thread> threads = new CopyOnWriteArrayList<>();

		@Override
		public void gcPause(long startMs, int durationMs, int heapAfterMb)
		{
			threads.add(Thread.currentThread());
			rows.add(new long[] {startMs, durationMs, heapAfterMb});
		}

		boolean await(int n) throws InterruptedException
		{
			final long end = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(WAIT_MS);
			while (rows.size() < n && System.nanoTime() < end)
			{
				Thread.sleep(10);
			}
			return rows.size() >= n;
		}

		int startingAt(long startMs)
		{
			int n = 0;
			for (long[] row : rows)
			{
				n += row[0] == startMs ? 1 : 0;
			}
			return n;
		}
	}

	/** Busy work on this thread for about {@code ms}. */
	private static void spin(long ms)
	{
		final long end = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(ms);
		double x = 1;
		while (System.nanoTime() < end)
		{
			x = Math.sqrt(x + 1.5);
		}
		assertTrue(x > 0);
	}

	/** Polls the system reading for up to 5 s until the bean answers a number. */
	private static int realSystemReading(ManagementHostProbe p) throws InterruptedException
	{
		final long end = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(WAIT_MS);
		int system = -1;
		while (system < 0 && System.nanoTime() < end)
		{
			Thread.sleep(100);
			system = p.systemCpuPct();
		}
		assertTrue("0 .. 100: " + system, system <= 100);
		return system;
	}

	private static Set<String> heapPoolNames()
	{
		final Set<String> names = new HashSet<>();
		for (MemoryPoolMXBean pool : ManagementFactory.getMemoryPoolMXBeans())
		{
			if (pool.getType() == MemoryType.HEAP)
			{
				names.add(pool.getName());
			}
		}
		return names;
	}

	/** The collection that began last, over every collector; null before the first. */
	private static GcInfo newestGcInfo()
	{
		GcInfo newest = null;
		for (GarbageCollectorMXBean bean : ManagementFactory.getGarbageCollectorMXBeans())
		{
			final GcInfo gc = ((com.sun.management.GarbageCollectorMXBean) bean).getLastGcInfo();
			if (gc != null && (newest == null || gc.getStartTime() > newest.getStartTime()))
			{
				newest = gc;
			}
		}
		return newest;
	}

	/** A collection notification as the JVM sends one, with the given action and a real GcInfo. */
	private static Notification gcNotification(String action, GcInfo gc)
	{
		final CompositeData data = new GarbageCollectionNotificationInfo("Test Collector", action, "test", gc)
			.toCompositeData(null);
		final Notification n = new Notification(GarbageCollectionNotificationInfo.GARBAGE_COLLECTION_NOTIFICATION,
			"test", 0);
		n.setUserData(data);
		return n;
	}

	private static MemoryUsage usage(long used)
	{
		return new MemoryUsage(0, used, used, -1);
	}
}
