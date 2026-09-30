package com.whylag.core;

/**
 * How sure a verdict is (contract 3.2, 6.3). Strongest first: the ORDINAL grows as confidence falls, which is what
 * {@link #lower()} and {@link #weaker(Confidence, Confidence)} rely on.
 */
public enum Confidence
{
	SURE("Sure"),
	LIKELY("Likely"),
	HINT("Hint"),
	CANT_TELL("Can't tell");

	private final String word;

	Confidence(String word)
	{
		this.word = word;
	}

	/** The word the card and the report print: "Sure", "Likely", "Hint", "Can't tell". */
	public String word()
	{
		return word;
	}

	/** One step weaker; {@link #CANT_TELL} stays {@link #CANT_TELL}. */
	public Confidence lower()
	{
		switch (this)
		{
			case SURE:
				return LIKELY;
			case LIKELY:
				return HINT;
			default:
				return CANT_TELL;
		}
	}

	/** The weaker of the two, for a ceiling laid over a score; two equal ones answer {@code a}. */
	public static Confidence weaker(Confidence a, Confidence b)
	{
		return b.ordinal() > a.ordinal() ? b : a;
	}
}
