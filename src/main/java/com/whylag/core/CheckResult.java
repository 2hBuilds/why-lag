package com.whylag.core;

/**
 * The answer of one check (1.0.1, lot B): its id ({@code C4}), its name ({@code Ping readable}), whether it passed
 * and one line of detail. Immutable; {@link Checks#run} makes ten of them and the report prints each as
 * {@link #line()}.
 *
 * <p>The detail of a PASS is what was found ({@code 41 ms, 1 s old}); the detail of a FAIL is the plain words of what
 * is wrong, which {@link Checks#verdict} puts first, so a detail starts in lower case and means a sentence.
 *
 * <p>Choice: a check that could not run for a stated reason (not logged in) is a
 * PASS whose detail says so, never a FAIL: a FAIL means the plugin itself is short of something it needs.
 * <br>
 * Choice: a null text is kept as "".
 */
public final class CheckResult
{
	/** {@code C1} to {@code C10}. */
	public final String id;
	/** {@code Ping readable}. */
	public final String name;
	public final boolean pass;
	/** One line, no line break. */
	public final String detail;

	public CheckResult(String id, String name, boolean pass, String detail)
	{
		this.id = id == null ? "" : id;
		this.name = name == null ? "" : name;
		this.pass = pass;
		this.detail = detail == null ? "" : detail;
	}

	/** The report's line: {@code PASS  C4 Ping readable  41 ms, 1 s old}; the detail is left off when it is "". */
	public String line()
	{
		final String head = (pass ? "PASS" : "FAIL") + "  " + id + " " + name;
		return detail.isEmpty() ? head : head + "  " + detail;
	}
}
