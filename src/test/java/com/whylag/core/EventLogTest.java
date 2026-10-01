package com.whylag.core;

import java.util.List;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * The event log (contract 3.4): the cap of 500 with the session counts kept, {@code replace} re-counting the
 * group, {@code between}, and every reader of closed events skipping the open one. With it, the verdict (3.6):
 * what makes two verdicts the same and its texts never null; and every public field of the carriers is final. The
 * event itself is {@code LagEventTest}'s; the carriers the panel reads are {@code PanelTypesTest}'s.
 */
public class EventLogTest
{
	@Test
	public void capAtFiveHundredKeepsTheSessionCounts()
	{
		final EventLog log = new EventLog(Thresholds.EVENTS);
		for (int i = 0; i < 520; i++)
		{
			log.add(closed(i, 10L * i, 10L * i + 2, i % 2 == 0 ? Cause.SLOW_WORLD : Cause.CLIENT_BUSY));
		}
		assertEquals(500, log.size());
		assertEquals("the oldest twenty were dropped", 20, log.get(0).id);
		assertEquals(519, log.get(499).id);
		assertEquals("dropped events stay counted", 520, log.sessionTotal());
		assertEquals(260, log.sessionCount(Group.WORLD));
		assertEquals(260, log.sessionCount(Group.FRAME_RATE));
		assertEquals(0, log.sessionCount(Group.CONNECTION));
		assertNull("a dropped event is not held", log.byId(3));
		assertEquals(500, log.copy().size());
	}

	@Test
	public void replaceRecountsTheGroup()
	{
		final EventLog log = new EventLog(10);
		log.add(open(1, 100));
		assertEquals("an open event is not counted", 0, log.sessionTotal());
		assertTrue(log.replace(closed(1, 100, 113, Cause.SLOW_WORLD)));
		assertEquals(1, log.sessionTotal());
		assertEquals(1, log.sessionCount(Group.WORLD));
		assertEquals("held once", 1, log.size());
		assertTrue(log.replace(closed(1, 100, 113, Cause.UPLOAD_LOSS)));
		assertEquals(1, log.sessionTotal());
		assertEquals(0, log.sessionCount(Group.WORLD));
		assertEquals(1, log.sessionCount(Group.CONNECTION));
		assertFalse("no event 2 is held", log.replace(closed(2, 200, 201, Cause.CLIENT_BUSY)));
		assertEquals(1, log.size());
		assertEquals(1, log.sessionTotal());
	}

	@Test
	public void anUnjudgedClosedEventCountsAsNotSure()
	{
		final EventLog log = new EventLog(10);
		log.add(event(1, 100, 101, false, null));
		assertEquals(1, log.sessionCount(Group.UNSURE));
		log.add(closed(2, 110, 112, Cause.DELIVERY_GAP));
		assertEquals("N6 is counted as not sure", 2, log.sessionCount(Group.UNSURE));
	}

	@Test
	public void between()
	{
		final EventLog log = new EventLog(10);
		log.add(closed(1, 100, 105, Cause.SLOW_WORLD));
		log.add(closed(2, 200, 200, Cause.CLIENT_BUSY));
		log.add(closed(3, 300, 320, Cause.CLIENT_BUSY));
		log.add(open(4, 400));
		assertIds(log.between(0, 1000), 1, 2, 3);
		assertIds(log.between(105, 200), 1, 2);
		assertIds(log.between(106, 199));
		assertIds(log.between(310, 310), 3);
		assertIds("the open event is not listed", log.between(400, 500));
		try
		{
			log.between(0, 1000).add(closed(9, 1, 1, Cause.CLIENT_BUSY));
			fail("unmodifiable");
		}
		catch (UnsupportedOperationException expected)
		{
			// as the contract says
		}
	}

	@Test
	public void lastSkipsTheOpenEvent()
	{
		final EventLog log = new EventLog(10);
		assertNull(log.last());
		log.add(closed(1, 100, 105, Cause.SLOW_WORLD));
		log.add(open(2, 200));
		assertEquals(1, log.last().id);
		log.replace(closed(2, 200, 204, Cause.CLIENT_BUSY));
		assertEquals(2, log.last().id);
	}

	@Test
	public void copyHoldsEverythingOldestFirst()
	{
		final EventLog log = new EventLog(3);
		for (int i = 1; i <= 4; i++)
		{
			log.add(closed(i, i * 10L, i * 10L, Cause.CLIENT_BUSY));
		}
		log.add(open(5, 60));
		assertIds(log.copy(), 3, 4, 5);
		assertSame(log.get(2), log.byId(5));
		assertEquals(4, log.sessionTotal());
		try
		{
			log.get(3);
			fail("only three are held");
		}
		catch (IndexOutOfBoundsException expected)
		{
			// as it should
		}
	}

	@Test
	public void sameVerdict()
	{
		final Verdict a = aVerdict(Cause.SLOW_WORLD, "World 416 is struggling, not you");
		final Verdict b = new Verdict(Cause.SLOW_WORLD, Confidence.HINT, Level.WARN, a.headline, a.proof, a.fix,
			"other ruled out", 99, 7, 302, a.eventId, null, null);
		assertTrue("confidence, level, when and ruled-out do not make it different", a.sameAs(b));
		assertFalse(a.sameAs(aVerdict(Cause.SLOW_WORLD, "another headline")));
		assertFalse(a.sameAs(aVerdict(Cause.DELIVERY_GAP, a.headline)));
		assertFalse(a.sameAs(new Verdict(a.cause, a.confidence, a.level, a.headline, a.proof, a.fix, "", 0, 0, 0,
			a.eventId + 1, null, null)));
		assertFalse(a.sameAs(new Verdict(a.cause, a.confidence, a.level, a.headline, "other proof", a.fix, "", 0, 0,
			0, a.eventId, null, null)));
		assertFalse(a.sameAs(new Verdict(a.cause, a.confidence, a.level, a.headline, a.proof, "", "", 0, 0, 0,
			a.eventId, null, null)));
		assertFalse(a.sameAs(null));
	}

	@Test
	public void verdictTextsAreNeverNull()
	{
		final Verdict v = new Verdict(Cause.ALL_CLEAR, Confidence.SURE, Level.OK, null, null, null, null, 0, 0, 0,
			-1, null, null);
		assertEquals("", v.headline);
		assertEquals("", v.proof);
		assertEquals("", v.fix);
		assertEquals("", v.ruledOut);
		assertTrue(v.sameAs(new Verdict(Cause.ALL_CLEAR, Confidence.SURE, Level.OK, "", "", "", "", 0, 0, 0, -1,
			null, null)));
	}

	private static void assertIds(List<LagEvent> events, long... ids)
	{
		assertIds("", events, ids);
	}

	private static void assertIds(String why, List<LagEvent> events, long... ids)
	{
		assertEquals(why, ids.length, events.size());
		for (int i = 0; i < ids.length; i++)
		{
			assertEquals(why, ids[i], events.get(i).id);
		}
	}

	private static LagEvent open(long id, long startSec)
	{
		return event(id, startSec, startSec, true, null);
	}

	private static LagEvent closed(long id, long startSec, long endSec, Cause cause)
	{
		final Verdict v = new Verdict(cause, Confidence.LIKELY, Level.BAD, "h", "p", "f", "", 0, 0, 416, id, null,
			null);
		return event(id, startSec, endSec, false, v);
	}

	private static LagEvent event(long id, long startSec, long endSec, boolean open, Verdict v)
	{
		return new LagEvent(id, startSec, endSec, 0, Trigger.FRAME_GAP.bit(), Trigger.FRAME_GAP, 416, 0, 0, 0, 50,
			300, 600, 610, 0, 40, 41, 40, 900, 0, open, false, v);
	}

	private static Verdict aVerdict(Cause cause, String headline)
	{
		return new Verdict(cause, Confidence.LIKELY, Level.BAD, headline, "Ticks 600 to 900+ ms for 14 s.",
			"Hop to a quieter world.", "Frames and ping were fine.", 1_790_000_100_000L, 14, 416, 11, null, null);
	}
}
