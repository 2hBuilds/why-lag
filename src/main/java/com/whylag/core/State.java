package com.whylag.core;

/**
 * Game state codes (contract 3.2). {@code core} never sees RuneLite's {@code GameState}: the plugin maps it to one
 * of these ints ({@code StateCodes}), and the second ring stores the int.
 */
public final class State
{
	public static final int OTHER = 0, LOGIN_SCREEN = 1, LOGGING_IN = 2, LOADING = 3, LOGGED_IN = 4,
		CONNECTION_LOST = 5, HOPPING = 6;

	private State()
	{
	}

	/** True for {@link #LOADING} and {@link #LOGGED_IN}: the player is in a world. */
	public static boolean inGame(int code)
	{
		return code == LOADING || code == LOGGED_IN;
	}
}
