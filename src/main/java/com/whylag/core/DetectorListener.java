package com.whylag.core;

/**
 * What the detector tells the engine (contract 3.4), on the sampler thread. The engine answers {@code opened} with
 * {@link EventLog#add} and {@code closed} with {@link EventLog#replace} once the verdict is attached.
 */
public interface DetectorListener
{
	void opened(LagEvent e);

	void closed(LagEvent e);
}
