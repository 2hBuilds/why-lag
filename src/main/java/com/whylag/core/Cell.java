package com.whylag.core;

/**
 * One of the three small cells of the panel of picture 18 (contract 3.8, 5.2), as data: the panel paints it and
 * holds no rule of its own; {@link Cells} makes the three. Immutable; a null text is kept as "".
 *
 * <p>{@code name} is "FPS", "Tick" or "Ping"; {@code value} the big number, "50", "1,240", "41", or "-" for no
 * data; {@code unit} the small line under it, "fps", "ms", "ms", or "" for none; {@code level} the shape beside
 * the name; {@code culprit} marks the cell of a selected event's cause (a coloured edge and ground); {@code tip} is
 * the tooltip, what the old tile said in full.
 */
public final class Cell
{
	public final Lane lane;
	/** "FPS", "Tick", "Ping". */
	public final String name;
	/** "50", "1,240", "41"; "-" = no data. */
	public final String value;
	/** "fps", "ms", "ms"; "" = none. */
	public final String unit;
	/** The shape beside the name. */
	public final Level level;
	/** The cell of a selected event's cause: coloured edge and ground (contract 5.2). */
	public final boolean culprit;
	/** The tooltip: what the old tile said in full. */
	public final String tip;

	public Cell(Lane lane, String name, String value, String unit, Level level, boolean culprit, String tip)
	{
		this.lane = lane;
		this.name = name == null ? "" : name;
		this.value = value == null ? "" : value;
		this.unit = unit == null ? "" : unit;
		this.level = level;
		this.culprit = culprit;
		this.tip = tip == null ? "" : tip;
	}
}
