package com.whylag.core;

/**
 * One row of the rules table of contract 6.3: needs, excludes and supports as small named predicates on
 * {@link Evidence}, with the row's rank, base score and ceiling.
 *
 * <p><b>Scoring (skeptic C22).</b> A rule scores 0 unless ALL its needs hold and NONE of its excludes holds; then
 * it scores {@code base + SUPPORT_POINTS x (supports that hold)}. A need on a signal with no data FAILS. An exclude
 * or a support on a signal with no data is skipped, and the rule's ceiling drops ONE step for each such signal.
 *
 * <p>Choice: a verdict's confidence IS its rule's ceiling after those drops ({@link #confidence}); the score picks
 * the winner and never raises or lowers the word.
 * <p>Choice: V2 (kind ALWAYS) scores its base, 10, on any evidence; X (kind ENGINE) scores 0: the engine makes it.
 */
public final class Rule
{
	/** What a row is: an event rule, a condition rule, V2 ("always") or X ("made by the engine"). */
	public enum Kind
	{
		EVENT, CONDITION, ALWAYS, ENGINE
	}

	/** One named predicate: {@link Evidence#YES}, {@link Evidence#NO} or {@link Evidence#NO_DATA}. */
	public interface Signal
	{
		int test(Evidence e);
	}

	private static final Signal[] NONE = new Signal[0];

	public final int rank;
	/** The row's id: "D1" .. "X"; W1's condition form is "W1c". */
	public final String id;
	public final Cause cause;
	public final Kind kind;
	public final int base;
	public final Confidence ceiling;
	private final Signal[] needs;
	private final Signal[] excludes;
	private final Signal[] supports;

	Rule(int rank, String id, Cause cause, Kind kind, int base, Confidence ceiling, Signal[] needs,
		Signal[] excludes, Signal[] supports)
	{
		this.rank = rank;
		this.id = id;
		this.cause = cause;
		this.kind = kind;
		this.base = base;
		this.ceiling = ceiling;
		this.needs = needs == null ? NONE : needs;
		this.excludes = excludes == null ? NONE : excludes;
		this.supports = supports == null ? NONE : supports;
	}

	/** The row's score on this evidence; 0 = it does not answer. */
	public int score(Evidence e)
	{
		if (kind == Kind.ENGINE)
		{
			return 0;
		}
		for (Signal need : needs)
		{
			if (need.test(e) != Evidence.YES)
			{
				return 0;
			}
		}
		for (Signal exclude : excludes)
		{
			if (exclude.test(e) == Evidence.YES)
			{
				return 0;
			}
		}
		int score = base;
		for (Signal support : supports)
		{
			if (support.test(e) == Evidence.YES)
			{
				score += Thresholds.SUPPORT_POINTS;
			}
		}
		return score;
	}

	/** The ceiling, one step lower for each exclude and each support whose signal has no data. */
	public Confidence confidence(Evidence e)
	{
		Confidence c = ceiling;
		for (Signal exclude : excludes)
		{
			if (exclude.test(e) == Evidence.NO_DATA)
			{
				c = c.lower();
			}
		}
		for (Signal support : supports)
		{
			if (support.test(e) == Evidence.NO_DATA)
			{
				c = c.lower();
			}
		}
		return c;
	}

	/** How many needs, excludes and supports the row has (the table test counts them). */
	public int needs()
	{
		return needs.length;
	}

	public int excludes()
	{
		return excludes.length;
	}

	public int supports()
	{
		return supports.length;
	}
}
