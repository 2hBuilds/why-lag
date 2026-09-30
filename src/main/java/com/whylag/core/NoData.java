package com.whylag.core;

/**
 * Why a reading is missing, in the words a tile prints under its dash (contract 3.2, 5.2). Every reason fits a
 * 105 px tile drawn from x 9 in the RuneScape small font (the widest, "Could not read it", is 84 px), and none is
 * longer than 17 characters ({@code NoDataTest}).
 *
 * <p>The ORDINAL is stored in {@link SecondRing}'s {@code conn} column. Do not reorder.
 *
 * <p>{@link #WARMING_UP}'s word is the card's warm-up headline (contract 5.1). No tile shows it in wave one: the
 * warm-up is the card's alone, and the tiles show their numbers through it (5.2).
 */
public enum NoData
{
	NONE(""),
	WARMING_UP("Still measuring"),
	NOT_LOGGED_IN("Not logged in"),
	NOT_CONNECTED("Not connected"),
	UNSUPPORTED("Not on this PC"),
	ERROR("Could not read it"),
	STALE("Nothing sent"),
	NO_FRAMES("No frames drawn"),
	NO_TICKS("No ticks yet");

	private final String reason;

	NoData(String reason)
	{
		this.reason = reason;
	}

	/** The tile's small line; "" for {@link #NONE}. */
	public String reason()
	{
		return reason;
	}
}
