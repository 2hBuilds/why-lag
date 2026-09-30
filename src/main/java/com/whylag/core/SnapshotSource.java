package com.whylag.core;

/**
 * Builds what the panel reads (contract 3.9, 5). Called on the sampler thread, under the engine's step lock.
 */
public interface SnapshotSource
{
	PanelSnapshot build(Session s, Verdict shown, int rangeMinutes, long nowSec, long wallMs,
		SettingsView settings, String footer);
}
