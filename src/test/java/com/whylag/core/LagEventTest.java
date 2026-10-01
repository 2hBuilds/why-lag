package com.whylag.core;

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
	 * place (a swap in the constructor call of {@code withVerdict}) is caught, not only a field left out. The probe's
	 * {@code LagEventStructureTest} does the same over the class's own field list.
	 */
	@Test
	public void withVerdictCopiesEveryField()
	{
		final LagEvent e = new LagEvent(1, 2, 3, 4, 5, Trigger.TICK_OFF, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17,
			18, 19, true, false, null);
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
		assertTrue(e.open);
		assertFalse(e.becameCondition);

		assertNotEquals(e.open, e.becameCondition);

		final Verdict v = aVerdict(Cause.CLIENT_BUSY, "The client itself stalled");
		final LagEvent judged = e.withVerdict(v);
		assertNotSame(e, judged);
		assertNull("the original is untouched", e.verdict);
		assertSame(v, judged.verdict);
		assertEquals("id", e.id, judged.id);
		assertEquals("startSec", e.startSec, judged.startSec);
		assertEquals("endSec", e.endSec, judged.endSec);
		assertEquals("startWallMs", e.startWallMs, judged.startWallMs);
		assertEquals("triggers", e.triggers, judged.triggers);
		assertEquals("first", e.first, judged.first);
		assertEquals("world", e.world, judged.world);
		assertEquals("region", e.region, judged.region);
		assertEquals("players", e.players, judged.players);
		assertEquals("npcs", e.npcs, judged.npcs);
		assertEquals("fps", e.fps, judged.fps);
		assertEquals("worstFrameMs", e.worstFrameMs, judged.worstFrameMs);
		assertEquals("meanTickGapMs", e.meanTickGapMs, judged.meanTickGapMs);
		assertEquals("worstTickGapMs", e.worstTickGapMs, judged.worstTickGapMs);
		assertEquals("worstCorrectedTickMs", e.worstCorrectedTickMs, judged.worstCorrectedTickMs);
		assertEquals("rttMs", e.rttMs, judged.rttMs);
		assertEquals("rttMaxMs", e.rttMaxMs, judged.rttMaxMs);
		assertEquals("rttBeforeMs", e.rttBeforeMs, judged.rttBeforeMs);
		assertEquals("sentUnits", e.sentUnits, judged.sentUnits);
		assertEquals("resentUnits", e.resentUnits, judged.resentUnits);
		assertEquals("open", e.open, judged.open);
		assertEquals("becameCondition", e.becameCondition, judged.becameCondition);
	}

	/** An event of 14 s on world 416 with every number known; judged when {@code v} is not null. */
	private static LagEvent anEvent(Verdict v, int triggers)
	{
		return new LagEvent(11, 100, 113, 1_790_000_100_000L, triggers, Trigger.TICK_OFF, 416, 12850, 14, 30, 50,
			34, 952, 1240, 640, 41, 44, 41, 12_600, 0, false, false, v);
	}

	private static Verdict aVerdict(Cause cause, String headline)
	{
		return new Verdict(cause, Confidence.LIKELY, Level.BAD, headline, "Ticks 600 to 900+ ms for 14 s.",
			"Hop to a quieter world.", "Frames and ping were fine.", 1_790_000_100_000L, 14, 416, 11, null, null);
	}
}
