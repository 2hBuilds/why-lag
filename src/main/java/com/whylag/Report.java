package com.whylag;

/**
 * What "Troubleshoot..." brings back to the panel (1.0.1, lots B and C): the whole report, which fills the window's
 * box and is what its Copy report puts on the clipboard, and the one-line verdict, which the window shows on top.
 * Immutable; the plugin builds it on the sampler thread and hands it to the Swing thread, where the panel uses it.
 *
 * <p>Choice: a null text or verdict is kept as "", and the window offers nothing to copy when the text is "".
 */
public final class Report
{
	/** The report as {@code ReportText} prints it, with the Verdict and Checks sections in; plain ASCII. */
	public final String text;
	/** The verdict line alone, as {@code Checks.verdict} words it. */
	public final String verdict;

	public Report(String text, String verdict)
	{
		this.text = text == null ? "" : text;
		this.verdict = verdict == null ? "" : verdict;
	}
}
