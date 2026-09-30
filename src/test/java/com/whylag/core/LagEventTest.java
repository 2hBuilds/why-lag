package com.whylag.core;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.HashSet;
import java.util.Set;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

/**
 * One lag event (contract 3.4): its length, its triggers and its group, and {@code withVerdict} copying every
 * field - the two CPU fields included - into the judged event.
 */
public class LagEventTest
{
	@Test
	public void lengthHasAndGroup()
	{
		final LagEvent e = anEvent(null, Trigger.TICK_OFF.bit() | Trigger.RTT_SPIKE.bit());
		assertEquals(14, e.lengthS());
		assertTrue(e.has(Trigger.TICK_OFF));
		assertTrue(e.has(Trigger.RTT_SPIKE));
		assertFalse(e.has(Trigger.FRAME_GAP));
		assertEquals("unjudged: not sure", Group.UNSURE, e.group());
		assertEquals(Group.WORLD, e.withVerdict(aVerdict(Cause.SLOW_WORLD, "h")).group());
		assertEquals(Group.UNSURE, e.withVerdict(aVerdict(Cause.DELIVERY_GAP, "h")).group());
	}

	/**
	 * Every number of the event is a DIFFERENT number, and the two switches differ, so a field copied into the wrong
	 * place (a swap in the constructor call of {@code withVerdict}) is caught, not only a field left out.
	 */
	@Test
	public void withVerdictCopiesEveryField() throws IllegalAccessException
	{
		final LagEvent e = new LagEvent(1, 2, 3, 4, 5, Trigger.TICK_OFF, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17,
			18, 19, 20, 21, 22, 23, 24, true, false, null);
		// The constructor puts each number where its name says.
		assertEquals(1, e.id);
		assertEquals(2, e.startSec);
		assertEquals(3, e.endSec);
		assertEquals(4, e.startWallMs);
		assertEquals(5, e.triggers);
		assertSame(Trigger.TICK_OFF, e.first);
		assertEquals(6, e.world);
		assertEquals(7, e.region);
		assertEquals(8, e.players);
		assertEquals(9, e.npcs);
		assertEquals(10, e.fps);
		assertEquals(11, e.worstFrameMs);
		assertEquals(12, e.meanTickGapMs);
		assertEquals(13, e.worstTickGapMs);
		assertEquals(14, e.worstCorrectedTickMs);
		assertEquals(15, e.rttMs);
		assertEquals(16, e.rttMaxMs);
		assertEquals(17, e.rttBeforeMs);
		assertEquals(18, e.sentUnits);
		assertEquals(19, e.resentUnits);
		assertEquals(20, e.gcPauseMs);
		assertEquals(21, e.heapUsedMb);
		assertEquals(22, e.heapMaxMb);
		assertEquals(23, e.sysCpuPct);
		assertEquals(24, e.gameBusyPct);
		assertTrue(e.open);
		assertFalse(e.becameCondition);

		// The premise: every number differs from every other, so no swap can hide.
		final Set<Long> numbers = new HashSet<>();
		int numberFields = 0;
		for (Field f : fields())
		{
			if (f.getType() == int.class || f.getType() == long.class)
			{
				numberFields++;
				assertTrue(f.getName() + " repeats another field's number",
					numbers.add(((Number) f.get(e)).longValue()));
			}
		}
		assertEquals("the event's numbers, the two CPU fields included", 24, numberFields);
		assertNotEquals(e.open, e.becameCondition);

		final Verdict v = aVerdict(Cause.CLIENT_BUSY, "The client itself stalled");
		final LagEvent judged = e.withVerdict(v);
		assertNotSame(e, judged);
		assertNull("the original is untouched", e.verdict);
		assertSame(v, judged.verdict);
		for (Field f : fields())
		{
			if (!f.getName().equals("verdict"))
			{
				assertEquals(f.getName(), f.get(e), f.get(judged));
			}
		}
		assertEquals(23, judged.sysCpuPct);
		assertEquals(24, judged.gameBusyPct);
	}

	/** The instance fields of {@link LagEvent}. */
	private static Set<Field> fields()
	{
		final Set<Field> out = new HashSet<>();
		for (Field f : LagEvent.class.getDeclaredFields())
		{
			if (!Modifier.isStatic(f.getModifiers()) && !f.isSynthetic())
			{
				out.add(f);
			}
		}
		return out;
	}

	/** An event of 14 s on world 416 with every number known; judged when {@code v} is not null. */
	private static LagEvent anEvent(Verdict v, int triggers)
	{
		return new LagEvent(11, 100, 113, 1_790_000_100_000L, triggers, Trigger.TICK_OFF, 416, 12850, 14, 30, 50, 34,
			952, 1240, 640, 41, 44, 41, 12_600, 0, 22, 607, 768, 37, 95, false, false, v);
	}

	private static Verdict aVerdict(Cause cause, String headline)
	{
		return new Verdict(cause, Confidence.LIKELY, Level.BAD, headline, "Ticks 600 to 900+ ms for 14 s.",
			"Hop to a quieter world.", "Frames and ping were fine.", 1_790_000_100_000L, 14, 416, 11, null, null);
	}
}
