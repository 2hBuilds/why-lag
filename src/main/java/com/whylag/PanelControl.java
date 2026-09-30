package com.whylag;

import javax.swing.JComponent;

/**
 * What the M0 probe drives on the panel (contract 3.9).
 */
public interface PanelControl
{
	/** Presses the chip of that range. Swing thread. */
	void setRange(int minutes);

	/** The range shown, 1, 10 or 60. */
	int range();

	/**
	 * Chooses "Troubleshoot..." in the gear menu (1.0.1, lot C): the window opens on "Testing...", the checks run and
	 * the report fills the window when it comes back. Answers the report's text only when the actions brought it back
	 * before this returned (a stub does; the plugin does not and answers ""), and "" before the first snapshot.
	 * Swing thread.
	 */
	String copyReport();

	/** Selects the list row of that event; -1 = none. Swing thread. */
	void select(long eventId);

	/** Sets both fold rows (contract 5, blocks 5 and 6): "Graphs" and "Lags", each open or folded. Swing thread. */
	void fold(boolean graphsOpen, boolean lagsOpen);

	/** True while the "Graphs" row is open. Any thread; false in a new panel. */
	boolean graphsOpen();

	/** True while the "Lags" row is open. Any thread; false in a new panel. */
	boolean lagsOpen();

	/** True while the panel is showing. Any thread. */
	boolean isActive();

	int activations();

	int deactivations();

	JComponent component();
}
