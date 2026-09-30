package com.whylag.host;

/**
 * Where a {@link HostProbe} reports each memory pause (contract 3.9). {@code startMs} is SESSION ms (since
 * {@code Session.startNanos}), never wall time; called on the JVM's notification thread.
 */
public interface GcSink
{
	void gcPause(long startMs, int durationMs, int heapAfterMb);
}
