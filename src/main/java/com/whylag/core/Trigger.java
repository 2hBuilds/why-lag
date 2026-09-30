package com.whylag.core;

/**
 * What can open or extend a lag event (contract 6.2). An event keeps the triggers that fired in it as a bit set,
 * {@link LagEvent#triggers}.
 */
public enum Trigger
{
	FRAME_GAP,
	TICK_OFF,
	NO_TICK,
	RTT_SPIKE,
	RESENT,
	DISCONNECT,
	LONG_LOAD;

	/** {@code 1 << ordinal()}. */
	public int bit()
	{
		return 1 << ordinal();
	}
}
