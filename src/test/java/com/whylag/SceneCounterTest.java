package com.whylag;

import org.junit.Test;
import static org.junit.Assert.assertEquals;

/**
 * The scene counter (contract 3.11, 7 L7): spawns count up, despawns count down and never below 0, and a reset -
 * which the plugin makes on {@code LOGIN_SCREEN} and {@code HOPPING} only - starts the next scene from 0, however
 * late the old scene's despawns arrive.
 */
public class SceneCounterTest
{
	@Test
	public void countsSpawnsAndDespawns()
	{
		final SceneCounter c = new SceneCounter();
		assertEquals(0, c.players());
		assertEquals(0, c.npcs());
		spawn(c, 12, 30);
		assertEquals(12, c.players());
		assertEquals(30, c.npcs());
		for (int i = 0; i < 5; i++)
		{
			c.playerDespawned();
		}
		for (int i = 0; i < 7; i++)
		{
			c.npcDespawned();
		}
		assertEquals("players and NPCs are counted apart", 7, c.players());
		assertEquals(23, c.npcs());
	}

	@Test
	public void neverBelowZero()
	{
		final SceneCounter c = new SceneCounter();
		c.playerDespawned();
		c.npcDespawned();
		assertEquals(0, c.players());
		assertEquals(0, c.npcs());
		spawn(c, 2, 1);
		for (int i = 0; i < 10; i++)
		{
			c.playerDespawned();
			c.npcDespawned();
		}
		assertEquals(0, c.players());
		assertEquals(0, c.npcs());
		spawn(c, 1, 1);
		assertEquals("a despawn at 0 left no debt: the next spawn counts one", 1, c.players());
		assertEquals(1, c.npcs());
	}

	@Test
	public void resetOnHop()
	{
		final SceneCounter c = new SceneCounter();
		spawn(c, 40, 120);
		c.reset();
		assertEquals("the hop: the old scene is gone", 0, c.players());
		assertEquals(0, c.npcs());
		for (int i = 0; i < 25; i++)
		{
			c.playerDespawned();
			c.npcDespawned();
		}
		assertEquals("old despawns arriving after the reset leave 0", 0, c.players());
		assertEquals(0, c.npcs());
		spawn(c, 3, 9);
		assertEquals("the new world's scene counts from 0", 3, c.players());
		assertEquals(9, c.npcs());
	}

	private static void spawn(SceneCounter c, int players, int npcs)
	{
		for (int i = 0; i < players; i++)
		{
			c.playerSpawned();
		}
		for (int i = 0; i < npcs; i++)
		{
			c.npcSpawned();
		}
	}
}
