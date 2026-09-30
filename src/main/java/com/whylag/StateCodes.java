package com.whylag;

import com.whylag.core.State;
import net.runelite.api.GameState;

/**
 * RuneLite's {@link GameState} as the int codes of {@link State} (contract 3.2, 3.11): {@code core} never sees
 * {@code GameState}, and the second ring stores the int. Pure; any thread.
 *
 * <table>
 * <caption>The map</caption>
 * <tr><th>GameState</th><th>code</th></tr>
 * <tr><td>LOGIN_SCREEN, LOGIN_SCREEN_AUTHENTICATOR</td><td>{@link State#LOGIN_SCREEN}</td></tr>
 * <tr><td>LOGGING_IN</td><td>{@link State#LOGGING_IN}</td></tr>
 * <tr><td>LOADING</td><td>{@link State#LOADING}</td></tr>
 * <tr><td>LOGGED_IN</td><td>{@link State#LOGGED_IN}</td></tr>
 * <tr><td>CONNECTION_LOST</td><td>{@link State#CONNECTION_LOST}</td></tr>
 * <tr><td>HOPPING</td><td>{@link State#HOPPING}</td></tr>
 * <tr><td>UNKNOWN, STARTING, null, and any state a later client adds</td><td>{@link State#OTHER}</td></tr>
 * </table>
 *
 * <p>Choice: LOGIN_SCREEN_AUTHENTICATOR is the login screen (its authenticator page); UNKNOWN, STARTING and null are OTHER.
 */
public final class StateCodes
{
	private StateCodes()
	{
	}

	/** The {@link State} code of a game state; never throws. */
	public static int of(GameState s)
	{
		if (s == null)
		{
			return State.OTHER;
		}
		switch (s)
		{
			case LOGIN_SCREEN:
			case LOGIN_SCREEN_AUTHENTICATOR:
				return State.LOGIN_SCREEN;
			case LOGGING_IN:
				return State.LOGGING_IN;
			case LOADING:
				return State.LOADING;
			case LOGGED_IN:
				return State.LOGGED_IN;
			case CONNECTION_LOST:
				return State.CONNECTION_LOST;
			case HOPPING:
				return State.HOPPING;
			default:
				return State.OTHER;
		}
	}
}
