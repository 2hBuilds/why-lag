package com.whylag.host;

import com.whylag.core.MemorySource;

/**
 * Memory, CPU and collection pauses of this JVM, behind one interface (contract 3.9) so the source can be swapped
 * - for a reviewer, or by the {@code systemStats} switch - by changing one line of {@code HostProbes}.
 * Implementations never throw.
 */
public interface HostProbe
{
	MemorySource source();

	/**
	 * Adds collection listeners if this probe has them; never throws; false = none added. {@code sessionStartNanos}
	 * is {@code Session.startNanos}: the probe turns each pause's JVM-uptime start into session ms. Wall time is
	 * never used.
	 */
	boolean start(GcSink sink, long sessionStartNanos);

	/** Removes every listener it added; never throws; safe to call twice. */
	void stop();

	int heapUsedMb();

	int heapMaxMb();

	/** The process's share of ONE core, 0 .. 100 x cores; -1 = no data (C14). */
	int processCpuPct();

	/** The whole system, 0 .. 100; -1 = no data. The first reading after start is thrown away. */
	int systemCpuPct();

	int cores();

	/** The calling thread's CPU time; -1 = unsupported. Called on the client thread once per frame: must not allocate. */
	long currentThreadCpuNanos();
}
