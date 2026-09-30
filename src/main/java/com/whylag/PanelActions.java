package com.whylag;

import com.whylag.core.BadgeStyle;
import com.whylag.core.PanelSnapshot;
import com.whylag.core.WhenSmooth;
import java.util.function.Consumer;

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
	 * "Troubleshoot..." was chosen in the gear menu (the window of 1.0.1, lot C): run the ten checks and build the
	 * report of {@code s}. Returns at once,
	 * whatever the plugin does: the work is handed to the plugin's own thread, and {@code back} is called later on
	 * the Swing thread with the report and the verdict. The plugin answers with the checks and
	 * {@code ReportText.of(s, checks)}; the panel never names either, and its tests pass a stub that calls
	 * {@code back} at once.
	 *
	 * <p>Choice: {@code back} is not called when the plugin is stopping and has no thread to run on.
	 */
	void testAndReport(PanelSnapshot s, Consumer<Report> back);

	/**
	 * The gear menu's "Badge on the game screen" was ticked (1.0.1, lot C): the plugin stores {@code on} under the
	 * config item {@code badgeShow}, on the thread it is called on (the Swing thread, where RuneLite's own settings
	 * page writes too).
	 */
	void badgeShow(boolean on);

	/** The gear menu's "Badge style" was chosen: the plugin stores the constant's NAME under {@code badgeStyle}. */
	void badgeStyle(BadgeStyle style);

	/** The gear menu's "Show badge when smooth" was ticked: the plugin stores the constant's NAME under {@code badgeWhenSmooth}. */
	void badgeWhenSmooth(WhenSmooth choice);

	/** The gear menu's "Chat line on a lag" was ticked: the plugin stores {@code on} under {@code badgeChatLine}. */
	void badgeChatLine(boolean on);
}
