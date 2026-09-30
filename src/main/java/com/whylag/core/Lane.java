package com.whylag.core;

/**
 * The THREE measured lanes, in the order the panel draws them (contract 3.2, 5.2, 5.3): frame rate, ticks and ping.
 * The strips are all three and so are the tiles ({@link #TILES}), in this order.
 *
 * <p>The label is the strip lane's; the tile over {@link #TICKS} is labelled "Server ticks" by the panel itself.
 */
public enum Lane
{
	FRAME_RATE("Frame rate"),
	TICKS("Ticks"),
	PING("Ping");

	/** How many tiles there are: every lane, in {@link Lane} order. */
	public static final int TILES = 3;

	private final String label;

	Lane(String label)
	{
		this.label = label;
	}

	/** "Frame rate", "Ticks", "Ping". */
	public String label()
	{
		return label;
	}
}
