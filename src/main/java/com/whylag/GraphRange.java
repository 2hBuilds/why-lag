package com.whylag;

/**
 * How much history the graphs show (contract 3.9): 1, 10 or 60 minutes.
 *
 * <p>Not named {@code Range}: {@code net.runelite.client.config.Range} is an annotation config interfaces import.
 * {@link #toString()} is the chip's LABEL, which RuneLite's config panel shows in its combo box, while
 * {@code ConfigManager} stores the constant's {@code name()} - so the label can change without touching a stored
 * setting.
 */
public enum GraphRange
{
	ONE_MIN(1, "1 min"),
	TEN_MIN(10, "10 min"),
	SIXTY_MIN(60, "60 min");

	private final int minutes;
	private final String label;

	GraphRange(int minutes, String label)
	{
		this.minutes = minutes;
		this.label = label;
	}

	/** 1, 10 or 60. */
	public int minutes()
	{
		return minutes;
	}

	/** "1 min", "10 min", "60 min". */
	@Override
	public String toString()
	{
		return label;
	}

	/**
	 * The range of that many minutes. Any other number answers {@link #TEN_MIN}, the config default, so a caller
	 * never holds null.
	 */
	public static GraphRange of(int minutes)
	{
		for (GraphRange r : values())
		{
			if (r.minutes == minutes)
			{
				return r;
			}
		}
		return TEN_MIN;
	}
}
