package com.whylag;

import com.whylag.core.State;
import java.util.EnumMap;
import java.util.Map;
import net.runelite.api.GameState;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * RuneLite's game states as the codes of {@link State} (contract 3.2, 3.11): every state of the client on the class
 * path has its row, the two authenticator and login screens are both the login screen, null and the states with no
 * code are OTHER, and only LOADING and LOGGED_IN are in the game.
 */
public class StateCodesTest
{
	@Test
	public void everyGameStateHasItsCode()
	{
		final Map<GameState, Integer> expected = new EnumMap<>(GameState.class);
		expected.put(GameState.UNKNOWN, State.OTHER);
		expected.put(GameState.STARTING, State.OTHER);
		expected.put(GameState.LOGIN_SCREEN, State.LOGIN_SCREEN);
		expected.put(GameState.LOGIN_SCREEN_AUTHENTICATOR, State.LOGIN_SCREEN);
		expected.put(GameState.LOGGING_IN, State.LOGGING_IN);
		expected.put(GameState.LOADING, State.LOADING);
		expected.put(GameState.LOGGED_IN, State.LOGGED_IN);
		expected.put(GameState.CONNECTION_LOST, State.CONNECTION_LOST);
		expected.put(GameState.HOPPING, State.HOPPING);
		assertEquals("a state the client added is a row to write here", GameState.values().length, expected.size());
		for (GameState s : GameState.values())
		{
			assertEquals(s.name(), (int) expected.get(s), StateCodes.of(s));
		}
	}

	@Test
	public void nullIsOther()
	{
		assertEquals(State.OTHER, StateCodes.of(null));
	}

	@Test
	public void onlyLoadingAndLoggedInAreInTheGame()
	{
		for (GameState s : GameState.values())
		{
			final boolean inGame = s == GameState.LOADING || s == GameState.LOGGED_IN;
			assertEquals(s.name(), inGame, State.inGame(StateCodes.of(s)));
		}
	}

	@Test
	public void everyCodeIsAStateCode()
	{
		for (GameState s : GameState.values())
		{
			final int code = StateCodes.of(s);
			assertTrue(s + " -> " + code, code >= State.OTHER && code <= State.HOPPING);
		}
	}
}
