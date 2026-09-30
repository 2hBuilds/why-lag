package com.whylag.core;

/**
 * The seam for the game badge (contract 3.6): the plugin's measurements and verdict, with no panel in the way. Once a
 * step, on the sampler thread, the plugin hands {@link #verdict()}, {@link #openEvent()} and the session's newest
 * closed event to the badge's state machine; the overlay reads only what that machine answers (one volatile read in
 * {@code render}), never this interface and never a ring. Nothing in {@code ui} is needed to produce a verdict or a
 * badge: with the panel never opened, both still change.
 */
public interface LagSource
{
	/** The verdict now; never null. A volatile read that allocates nothing; any thread. */
	Verdict verdict();

	void addVerdictListener(VerdictListener l);

	void removeVerdictListener(VerdictListener l);

	Session session();

	/** A snapshot for the panel. Any thread EXCEPT the client thread: it takes the step lock. */
	PanelSnapshot snapshot(int rangeMinutes, long wallMs);

	SelfTimer selfTimer();

	/**
	 * The OPEN event as the last step saw it, with its PROVISIONAL verdict attached: judged as if it closed now, so the
	 * badge can name a lag while it happens (contract 3.6). Null = none is open. A volatile read that allocates
	 * nothing; any thread. The provisional verdict goes nowhere else: not into the event log, not to the card, not to
	 * the verdict listeners, and {@link #verdict()} does not change because an event is open.
	 */
	LagEvent openEvent();
}
