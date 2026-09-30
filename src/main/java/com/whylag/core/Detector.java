package com.whylag.core;

/**
 * Finds lag events in the rings (contract 3.9, 6.1, 6.2). Called on the sampler thread only.
 *
 * <p>It alone adds to and resets the three usuals of the {@link Session}; it never touches {@link EventLog} (the
 * engine writes that, from the listener).
 *
 * <p><b>The id.</b> It numbers the events it opens 0, 1, 2 ... in the order they open, from one counter of its own
 * that nothing restarts (not a hop, a world change, a login or a lost connection); a new detector starts again at
 * 0. An event keeps its id from
 * {@link DetectorListener#opened} through every {@link #open()} to {@link DetectorListener#closed}, and no id is
 * reused (contract 3.4, "The id").
 */
public interface Detector
{
	/** Reads every second up to and including {@code throughSec}, telling {@code out} of each event opened or closed. */
	void advance(Session s, long throughSec, SettingsView settings, DetectorListener out);

	/** The open event, or null. It keeps the id that {@link DetectorListener#opened} was handed. */
	LagEvent open();

	/** True when no trigger fired in {@code sec} (asked after {@link #advance} has read it). */
	boolean quiet(long sec);
}
