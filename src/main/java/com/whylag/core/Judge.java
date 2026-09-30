package com.whylag.core;

/**
 * Names the cause (contract 3.9, 6.3 to 6.5). Called on the sampler thread only.
 */
public interface Judge
{
	/** The verdict of one closed event; pure of the clock. */
	Verdict judgeEvent(Session s, LagEvent closed, SettingsView settings);

	/**
	 * What the card shows now, asked once a second. Answers the SAME object while nothing changed, so the engine can
	 * compare by identity and tell its listeners once.
	 */
	Verdict current(Session s, long nowSec, long wallMs, SettingsView settings);
}
