package com.whylag.ui;

import com.whylag.core.PanelSnapshot;

/**
 * Reads the panel's newest snapshot for tests outside this package (the plugin's wiring test): {@code last} is
 * package-private, a test seam, and this helper is the one public door to it, kept in the test tree.
 */
public final class PanelPeek
{
	private PanelPeek()
	{
	}

	/** The newest snapshot handed to {@code panel.show}; null before the first. Swing thread only. */
	public static PanelSnapshot last(WhyLagPanel panel)
	{
		return panel.last;
	}
}
