package com.whylag.host;

import com.sun.management.GarbageCollectionNotificationInfo;
import com.sun.management.GcInfo;
import com.whylag.core.MemorySource;
import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryPoolMXBean;
import java.lang.management.MemoryType;
import java.lang.management.MemoryUsage;
import java.lang.management.OperatingSystemMXBean;
import java.lang.management.RuntimeMXBean;
import java.lang.management.ThreadMXBean;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import javax.management.ListenerNotFoundException;
import javax.management.Notification;
import javax.management.NotificationEmitter;
import javax.management.NotificationListener;
import javax.management.openmbean.CompositeData;

/**
 * Memory, CPU and collection pauses from Java's management beans (contract 7, L7). This is the ONE file of the
 * plugin that names {@code java.lang.management}, {@code javax.management} or {@code com.sun.management} (contract
 * 2): a reviewer who refuses them deletes this file and one line of {@code HostProbes}, and the plugin runs on
 * {@link RuntimeHostProbe}. Read-only: no {@code HotSpotDiagnosticMXBean}, no MBean server call, no setting written.
 *
 * <ul>
 * <li><b>Heap</b> from the {@link MemoryMXBean}: used and limit, in whole MB.</li>
 * <li><b>CPU</b> from {@code com.sun.management.OperatingSystemMXBean}, by the JAVA 11 names
 * ({@code getProcessCpuLoad}, {@code getSystemCpuLoad}). The process's load is a share of ALL cores, so
 * {@link #processCpuPct} is load x cores x 100: a share of ONE core, 0 .. 100 x cores (skeptic C14). The first
 * system reading after {@link #start} is thrown away (C14: the bean's first answer has nothing to compare with).</li>
 * <li><b>Pauses</b> from the collector beans' notifications, whose action is "end of minor GC" or "end of major GC"
 * ONLY - an allow-list (skeptic C8): a concurrent cycle ("end of GC cycle") or an unknown action is no pause data.
 * Each pause is handed to the {@link GcSink} on the JVM's notification thread, its start in SESSION ms with no wall
 * clock in it (the two clocks, below). A pause that began before the session is dropped. Its heap after counts the
 * heap alone (heap after, below).</li>
 * <li><b>The thread clock</b>, {@code getCurrentThreadCpuTime()}, or -1 where the JVM cannot measure it. It is
 * called on the client thread once a frame and allocates nothing.</li>
 * </ul>
 *
 * <p><b>The two clocks</b> (lot L7's check, round 2, D1). {@link #start} reads {@code RuntimeMXBean.getUptime()} and
 * {@code System.nanoTime()} side by side, so {@code uptimeAtSessionStart = uptimeNow - (nanoNow - sessionStartNanos)
 * / 1,000,000}. {@code GcInfo} times do NOT count from the JVM's first instant, as the uptime does: they count from
 * the END of the JVM's own start-up, some 20 to 45 ms later on an idle PC and hundreds of ms on a busy one, so
 * {@code GcInfo.getStartTime() - uptimeAtSessionStart} would report every pause that much early. That gap is fixed for
 * the JVM's life, and every pause measures it from above: the notification reads the uptime FIRST, and a collection
 * is always received after it ended, so {@code uptimeAtReceipt - GcInfo.getEndTime()} is the gap plus that
 * notification's delivery delay, never less than the gap. The probe keeps the least of them seen,
 * {@code gcClockOffsetMs}, and a pause's start is {@code GcInfo.getStartTime() + gcClockOffsetMs -
 * uptimeAtSessionStart}: never early, and late only by the smallest delivery delay seen so far (the first pause by
 * its own). A network time correction cannot shift it.
 *
 * <p><b>Heap after</b> (lot L7's check, round 2, D2) is the sum of {@code getMemoryUsageAfterGc()} used over the
 * pools whose {@code MemoryPoolMXBean.getType()} is {@code HEAP}. The map also holds the non-heap pools (Metaspace,
 * the code heaps, Compressed Class Space); they are left out, because every reader measures this figure against the
 * heap limit (contract 3.1, 5.2, 5.3, 10.3, and {@code GcRing}: "the heap left after it"), and the Runtime fallback
 * counts the heap alone.
 *
 * <p>Both readings differ from the words of contract 7 (L7): its premise that "GcInfo times are ms since the JVM
 * started" was measured false, and its "sum of getMemoryUsageAfterGc() used" took in the non-heap pools. The checker
 * of round 2 asked for these two readings; the contract's L7 sentences are the lead's to reword.
 *
 * <p>Choice: a JVM whose collectors send no notifications cannot measure a pause, so this probe refuses to be made.
 * <p>Choice: listeners go on every collector bean that sends notifications; the action allow-list filters what counts.
 * <p>Choice: a second {@link #start} first undoes the first, so no pause is ever reported twice.
 * <p>Choice: a GC notification that arrives after {@link #stop} is dropped (the sink is cleared first).
 * <p>Choice: CPU % is rounded to the nearest whole; an answer under 0 or not a number from the bean is -1.
 * <p>Choice: only the SYSTEM reading's first answer after start is thrown away (3.9); the process reading is not.
 * <p>Choice: the cores are counted once, when the probe is made; a non-com.sun OS bean leaves both CPU answers -1.
 * <p>Choice: a heap read the bean refuses, or a heap limit it leaves undefined, is read from the Runtime instead.
 * <p>Choice: a thread clock that throws once answers -1 from then on, so the frame path allocates at most once.
 * <p>Choice: the heap pools are named once, when the probe is made; a JVM's memory pools are fixed at its start.
 * <p>Choice: a map with no HEAP pool that has a usage (never so from a real collector) gives no heap after: -1.
 * <p>Choice: the clock offset is learned from allow-listed pauses only, one per probe, and kept across stop and start.
 * <p>Choice: a notification whose receipt uptime cannot be read is no pause data.
 */
public final class ManagementHostProbe implements HostProbe
{
	/** The two notification actions that are stop-the-world pauses (skeptic C8); every other action is ignored. */
	static final String MINOR_GC = "end of minor GC";
	static final String MAJOR_GC = "end of major GC";

	/** The clock offset before the first pause: none learned yet. */
	static final long NO_OFFSET = Long.MAX_VALUE;

	/** Unit conversions. */
	private static final int PERCENT = 100;
	private static final long NANOS_PER_MS = 1_000_000L;

	private final MemoryMXBean memory;
	private final RuntimeMXBean runtime;
	private final ThreadMXBean threads;
	/** The com.sun kind of OS bean, or null: then both CPU answers are -1. */
	private final com.sun.management.OperatingSystemMXBean os;
	/** The collector beans that send notifications; never empty. */
	private final List<NotificationEmitter> collectors;
	/** The names of the memory pools whose type is HEAP: heap after sums these alone. */
	private final Set<String> heapPools;
	private final int cores;
	private final boolean threadCpuSupported;
	/** Where a heap read goes when the bean refuses it. */
	private final RuntimeHostProbe fallback = new RuntimeHostProbe();
	/** ONE listener object, added to each collector and removed from each by identity. */
	private final NotificationListener listener = (notification, handback) -> onNotification(notification);
	/** The collectors a listener was added to; guarded by {@code this}. */
	private final List<NotificationEmitter> added = new ArrayList<>();
	/**
	 * How far the uptime clock runs ahead of the {@code GcInfo} clock, from above: the least
	 * {@code uptimeAtReceipt - GcInfo.getEndTime()} of the pauses seen; {@link #NO_OFFSET} before the first.
	 */
	private final AtomicLong gcClockOffsetMs = new AtomicLong(NO_OFFSET);

	/** Null while stopped: a late notification then goes nowhere. */
	private volatile GcSink sink;
	/** JVM uptime, in ms, at the session's start; set by {@link #start}. */
	private volatile long uptimeAtSessionStartMs;
	/** False until the first system CPU reading after start has been thrown away. */
	private volatile boolean systemCpuPrimed;
	/** Set on the client thread when the thread clock throws; read there only. */
	private boolean threadCpuFailed;

	/**
	 * Reads the beans once. Throws when the management beans are refused or missing (a {@code LinkageError} when the
	 * {@code jdk.management} module is absent), or when no collector sends notifications: {@code HostProbes} then
	 * answers the Runtime probe, so {@link MemorySource#MANAGEMENT} always means pauses are measured.
	 */
	public ManagementHostProbe()
	{
		memory = ManagementFactory.getMemoryMXBean();
		runtime = ManagementFactory.getRuntimeMXBean();
		threads = ManagementFactory.getThreadMXBean();
		final OperatingSystemMXBean osBean = ManagementFactory.getOperatingSystemMXBean();
		os = osBean instanceof com.sun.management.OperatingSystemMXBean
			? (com.sun.management.OperatingSystemMXBean) osBean : null;
		cores = Math.max(1, Runtime.getRuntime().availableProcessors());
		threadCpuSupported = threads.isCurrentThreadCpuTimeSupported();

		final Set<String> heap = new HashSet<>();
		for (MemoryPoolMXBean pool : ManagementFactory.getMemoryPoolMXBeans())
		{
			if (pool.getType() == MemoryType.HEAP)
			{
				heap.add(pool.getName());
			}
		}
		heapPools = Collections.unmodifiableSet(heap);

		final List<NotificationEmitter> emitters = new ArrayList<>();
		for (GarbageCollectorMXBean bean : ManagementFactory.getGarbageCollectorMXBeans())
		{
			if (bean instanceof NotificationEmitter)
			{
				emitters.add((NotificationEmitter) bean);
			}
		}
		if (emitters.isEmpty())
		{
			throw new IllegalStateException("no garbage collector sends notifications: pauses cannot be measured");
		}
		collectors = Collections.unmodifiableList(emitters);
	}

	@Override
	public MemorySource source()
	{
		return MemorySource.MANAGEMENT;
	}

	/**
	 * Fixes the session's uptime origin, then adds the one listener to every collector that sends notifications.
	 * Never throws; false when no listener could be added (or the sink is null). Re-arms the system CPU reading, whose
	 * next answer is thrown away. The clock offset learned so far is kept: the gap between the clocks never moves.
	 */
	@Override
	public synchronized boolean start(GcSink sink, long sessionStartNanos)
	{
		stop();
		systemCpuPrimed = false;
		if (sink == null)
		{
			return false;
		}
		try
		{
			final long uptimeNowMs = runtime.getUptime();
			final long nanoNow = System.nanoTime();
			arm(sink, uptimeNowMs - (nanoNow - sessionStartNanos) / NANOS_PER_MS);
			for (NotificationEmitter collector : collectors)
			{
				try
				{
					collector.addNotificationListener(listener, null, null);
					added.add(collector);
				}
				catch (RuntimeException | LinkageError e)
				{
					// this collector refused the listener: the others still count
				}
			}
		}
		catch (RuntimeException | LinkageError e)
		{
			// no uptime: nothing is added below
		}
		if (added.isEmpty())
		{
			this.sink = null;
			return false;
		}
		return true;
	}

	/**
	 * Sets the sink and the session's origin on the uptime clock, and adds no listener: {@link #start} adds them
	 * right after. Tests call it alone and hand notifications to {@link #onNotification(Notification, long)}
	 * themselves, so no collection of their own JVM can reach the sink or teach the clock offset.
	 */
	synchronized void arm(GcSink sink, long uptimeAtSessionStartMs)
	{
		this.uptimeAtSessionStartMs = uptimeAtSessionStartMs;
		this.sink = sink;
	}

	/** Clears the sink FIRST, then removes the listener from every collector it was added to. Never throws. */
	@Override
	public synchronized void stop()
	{
		sink = null;
		for (NotificationEmitter collector : added)
		{
			try
			{
				collector.removeNotificationListener(listener);
			}
			catch (ListenerNotFoundException | RuntimeException | LinkageError e)
			{
				// already gone
			}
		}
		added.clear();
	}

	@Override
	public int heapUsedMb()
	{
		try
		{
			return RuntimeHostProbe.mb(memory.getHeapMemoryUsage().getUsed());
		}
		catch (RuntimeException | LinkageError e)
		{
			return fallback.heapUsedMb();
		}
	}

	@Override
	public int heapMaxMb()
	{
		try
		{
			final long max = memory.getHeapMemoryUsage().getMax();
			return max > 0 ? RuntimeHostProbe.limitMb(max) : fallback.heapMaxMb();
		}
		catch (RuntimeException | LinkageError e)
		{
			return fallback.heapMaxMb();
		}
	}

	/** The process's load x cores x 100: its share of ONE core, 0 .. 100 x cores (C14); -1 = no data. */
	@Override
	public int processCpuPct()
	{
		if (os == null)
		{
			return -1;
		}
		try
		{
			return pct(os.getProcessCpuLoad(), cores);
		}
		catch (RuntimeException | LinkageError e)
		{
			return -1;
		}
	}

	/** The whole system, 0 .. 100; -1 = no data, and -1 for the first reading after start (C14). */
	@Override
	public int systemCpuPct()
	{
		if (os == null)
		{
			return -1;
		}
		try
		{
			final double load = os.getSystemCpuLoad();
			if (!systemCpuPrimed)
			{
				systemCpuPrimed = true;
				return -1;
			}
			return pct(load, 1);
		}
		catch (RuntimeException | LinkageError e)
		{
			return -1;
		}
	}

	@Override
	public int cores()
	{
		return cores;
	}

	/** The calling thread's CPU time in ns; -1 = unsupported or switched off. No allocation. */
	@Override
	public long currentThreadCpuNanos()
	{
		if (!threadCpuSupported || threadCpuFailed)
		{
			return -1;
		}
		try
		{
			return threads.getCurrentThreadCpuTime();
		}
		catch (RuntimeException e)
		{
			threadCpuFailed = true;
			return -1;
		}
	}

	/**
	 * One notification, on the JVM's notification thread. The uptime is read FIRST, before anything is decoded, so
	 * the probe's own work is not counted as delivery delay (the two clocks, above).
	 */
	void onNotification(Notification notification)
	{
		final long uptimeAtReceiptMs;
		try
		{
			uptimeAtReceiptMs = runtime.getUptime();
		}
		catch (RuntimeException | LinkageError e)
		{
			return;
		}
		onNotification(notification, uptimeAtReceiptMs);
	}

	/**
	 * One notification received at {@code uptimeAtReceiptMs} of the JVM's uptime: a collection whose action is on the
	 * allow-list teaches the clock offset, and goes to the sink when it began inside the session. Anything else, and
	 * anything that throws, is no pause data.
	 */
	void onNotification(Notification notification, long uptimeAtReceiptMs)
	{
		final GcSink out = sink;
		if (out == null || notification == null)
		{
			return;
		}
		try
		{
			if (!GarbageCollectionNotificationInfo.GARBAGE_COLLECTION_NOTIFICATION.equals(notification.getType()))
			{
				return;
			}
			final Object data = notification.getUserData();
			if (!(data instanceof CompositeData))
			{
				return;
			}
			final GarbageCollectionNotificationInfo info = GarbageCollectionNotificationInfo.from((CompositeData) data);
			if (info == null || !isPause(info.getGcAction()))
			{
				return;
			}
			final GcInfo gc = info.getGcInfo();
			final long offsetMs = gcClockOffsetMs.accumulateAndGet(uptimeAtReceiptMs - gc.getEndTime(), Math::min);
			final long startMs = gc.getStartTime() + offsetMs - uptimeAtSessionStartMs;
			if (startMs < 0)
			{
				return;
			}
			final int durationMs = (int) Math.max(0, Math.min(Integer.MAX_VALUE, gc.getDuration()));
			out.gcPause(startMs, durationMs, heapAfterMb(gc.getMemoryUsageAfterGc(), heapPools));
		}
		catch (RuntimeException | LinkageError e)
		{
			// a notification that cannot be read is no pause data
		}
	}

	/** True for the two stop-the-world actions of the allow-list (C8) only. */
	static boolean isPause(String action)
	{
		return MINOR_GC.equals(action) || MAJOR_GC.equals(action);
	}

	/**
	 * Heap after: the sum of {@code getMemoryUsageAfterGc()} used over the HEAP pools named in {@code heapPools}, in
	 * whole MB, rounded down. A non-heap pool of the map is left out, and a heap pool with no usage is skipped. -1 when
	 * there is no map, or no heap pool of it has a usage.
	 */
	static int heapAfterMb(Map<String, MemoryUsage> afterGc, Set<String> heapPools)
	{
		if (afterGc == null || heapPools == null)
		{
			return -1;
		}
		long bytes = 0;
		boolean any = false;
		for (String pool : heapPools)
		{
			final MemoryUsage usage = afterGc.get(pool);
			if (usage != null)
			{
				bytes += usage.getUsed();
				any = true;
			}
		}
		return any ? RuntimeHostProbe.mb(bytes) : -1;
	}

	/** Load 0.0 .. 1.0 as a percentage of {@code scale} x 100, rounded; under 0 or not a number is -1. */
	static int pct(double load, int scale)
	{
		if (!(load >= 0))
		{
			return -1;
		}
		return (int) Math.min((long) scale * PERCENT, Math.round(load * scale * PERCENT));
	}

	/** How many collectors carry the listener now. For tests. */
	synchronized int listenerCount()
	{
		return added.size();
	}

	/** How many collectors could carry it. For tests. */
	int collectorCount()
	{
		return collectors.size();
	}

	/** The session's origin on the JVM's uptime, as {@link #start} fixed it. For tests. */
	long uptimeAtSessionStartMs()
	{
		return uptimeAtSessionStartMs;
	}

	/** The clock offset learned so far; {@link #NO_OFFSET} before the first pause. For tests. */
	long gcClockOffsetMs()
	{
		return gcClockOffsetMs.get();
	}

	/** The names of the heap pools, as the probe read them when it was made. For tests. */
	Set<String> heapPools()
	{
		return heapPools;
	}

	/** The one listener object this probe adds. For tests. */
	NotificationListener listener()
	{
		return listener;
	}
}
