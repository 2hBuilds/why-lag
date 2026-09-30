package com.whylag.core;

/**
 * Told once each time the verdict changes (contract 3.6). Called on the sampler thread; it must not block.
 */
public interface VerdictListener
{
	void verdictChanged(Verdict now);
}
