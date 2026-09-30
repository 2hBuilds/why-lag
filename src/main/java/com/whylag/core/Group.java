package com.whylag.core;

/**
 * What an event is counted under (contract 3.2): the list's label, the session counts' short label, and the lane
 * whose band is drawn redder. {@link #UNSURE} and {@link #NONE} have no lane.
 *
 * <p>The ORDINAL indexes {@link PanelSnapshot#sessionCounts}. Do not reorder.
 */
public enum Group
{
	CONNECTION("Conn", "Connection", Lane.PING),
	FRAME_RATE("Frame", "Frame rate", Lane.FRAME_RATE),
	WORLD("World", "World", Lane.TICKS),
	UNSURE("?", "Not sure", null),
	NONE("", "", null);

	private final String shortLabel;
	private final String label;
	private final Lane lane;

	Group(String shortLabel, String label, Lane lane)
	{
		this.shortLabel = shortLabel;
		this.label = label;
		this.lane = lane;
	}

	/** The session counts' word: "Conn", "Frame", "World", "?". */
	public String shortLabel()
	{
		return shortLabel;
	}

	/** The event list's word: "Connection", "Frame rate", "World", "Not sure". */
	public String label()
	{
		return label;
	}

	/** The culprit lane, or null for {@link #UNSURE} and {@link #NONE}. */
	public Lane lane()
	{
		return lane;
	}
}
