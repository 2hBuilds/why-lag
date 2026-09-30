package com.whylag.core;

/**
 * One of the four tiles, as the panel draws it (contract 3.8, 5.2): {@code value} such as "50 fps" ("-" when
 * {@code noData} is not {@link NoData#NONE}), {@code sub} such as "worst 35 ms" (the reason when there is no data).
 * Null texts are kept as "", a null {@code noData} as {@link NoData#NONE}.
 */
public final class Tile
{
	public final Lane lane;
	public final Level level;
	public final String value;
	public final String sub;
	public final NoData noData;

	public Tile(Lane lane, Level level, String value, String sub, NoData noData)
	{
		this.lane = lane;
		this.level = level;
		this.value = value == null ? "" : value;
		this.sub = sub == null ? "" : sub;
		this.noData = noData == null ? NoData.NONE : noData;
	}
}
