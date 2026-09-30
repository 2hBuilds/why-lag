package com.whylag;

import com.whylag.core.PanelSnapshot;

/**
 * What the panel asks of the plugin (contract 3.9). Called on the Swing thread.
 */
public interface PanelActions
{
	/** A range chip was pressed: 1, 10 or 60. */
	void rangeChanged(int minutes);

	/** The panel was opened: it wants one snapshot at once. */
	void activated();

	/**
	 * The text for "Copy report". The plugin answers {@code ReportText.of(s)}; the panel never names
	 * {@code ReportText}, and its tests pass a stub.
	 */
	String report(PanelSnapshot s);
}
