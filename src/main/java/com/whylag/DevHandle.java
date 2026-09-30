package com.whylag;

import com.whylag.core.BadgeView;
import com.whylag.core.LagSource;
import com.whylag.core.SettingsView;
import com.whylag.host.HostProbe;

/**
 * What a developer-mode client hands the Effect Lab's {@code /whylag} route through {@link WhyLagDevBridge}
 * (contract 3.9): the running plugin's measurements, settings, host probe, sampler thread, panel and game badge.
 */
public interface DevHandle
{
	LagSource source();

	SettingsView settings();

	HostProbe hostProbe();

	long samplerThreadId();

	PanelControl control();

	/** What the game badge shows now: its state machine's {@code view()} (contract P2.8). */
	BadgeView badge();
}
