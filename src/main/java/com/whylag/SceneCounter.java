package com.whylag;

/**
 * How many players and NPCs are in the scene, counted from the spawn and despawn events (contract 3.11, 7 L7). Client
 * thread only: plain ints, no lock. The plugin hands the two counts to the engine once every
 * {@code SCENE_EVERY_TICKS} ticks.
 *
 * <p>The plugin calls {@link #reset} on {@code LOGIN_SCREEN} and {@code HOPPING} ONLY - never on {@code LOADING},
 * which keeps the NPCs (CLAUDE.md, the bundled NPC Indicators idiom). Spawn counters can drift from the true count
 * (contract 10.1, risk 11); the probe's {@code state} prints them beside an iterated count so E2 can see it.
 *
 * <p>Choice: a despawn at 0 is ignored rather than stored as a debt, so the count never goes below 0.
 */
public final class SceneCounter
{
	private int players;
	private int npcs;

	public void playerSpawned()
	{
		players++;
	}

	public void playerDespawned()
	{
		if (players > 0)
		{
			players--;
		}
	}

	public void npcSpawned()
	{
		npcs++;
	}

	public void npcDespawned()
	{
		if (npcs > 0)
		{
			npcs--;
		}
	}

	/** Both counts back to 0: the scene is gone (the login screen, a hop). */
	public void reset()
	{
		players = 0;
		npcs = 0;
	}

	/** Never below 0. */
	public int players()
	{
		return players;
	}

	/** Never below 0. */
	public int npcs()
	{
		return npcs;
	}
}
