package com.whylag.core;

/**
 * The FIVE measured lanes, in the order the panel draws them (contract 3.2, 5.2, 5.3). The strips are all five;
 * the tiles are the FIRST FOUR ({@link #TILES}), in this order. {@link #CPU} has no tile: it is the fifth strip,
 * under Memory, with the whole PC's use and the game thread's share.
 *
 * <p>The label is the strip lane's; the tile over {@link #TICKS} is labelled "Server ticks" by the panel itself.
 * No wave-one {@link Group} has {@link #CPU} as its lane.
 */
public enum Lane
{
	FRAME_RATE("Frame rate"),
	TICKS("Ticks"),
	PING("Ping"),
	MEMORY("Memory"),
	CPU("CPU");

	/** How many tiles there are: the first four lanes, in {@link Lane} order. */
	public static final int TILES = 4;

	private final String label;

	Lane(String label)
	{
		this.label = label;
	}

	/** "Frame rate", "Ticks", "Ping", "Memory", "CPU". */
	public String label()
	{
		return label;
	}
}
