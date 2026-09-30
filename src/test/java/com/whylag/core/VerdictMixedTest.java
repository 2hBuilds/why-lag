package com.whylag.core;

import org.junit.Test;
import static com.whylag.core.VerdictCauseTest.USUAL;
import static com.whylag.core.VerdictCauseTest.event;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

/**
 * Two causes in one event (contract 6.3, "Designed near misses"; skeptic C22): a long map load during packet loss.
 * S1 and N3 are the pair that can come within the margin in wave one once the memory rule is gone (1.0.0, the Hub's
 * rule). More evidence for the rival can never turn "Can't tell" into "Sure" - it does the opposite.
 */
public class VerdictMixedTest
{
	/** A late tick that is off after the 22 ms frame: TICK_OFF_MS + 150, 400 since the first live look (it was 250). */
	private static final int LATE = Thresholds.TICK_OFF_MS + 150;

	private final VerdictEngine judge = new VerdictEngine();

	/** A long load (seconds 100 to 102, LONG_LOAD in 103) and re-sends in the same second. */
	private static Trace loadDuringLoss()
	{
		return Trace.steady(200).usual(USUAL).loading(100, 1000).loading(101, 1000).loading(102, 400)
			.resent(103, 90);
	}

	/** S1 (105) against N3 with both supports (110): five apart, under the margin. */
	@Test
	public void loadDuringLossWithBothSupportsIsCantTell()
	{
		final Trace t = loadDuringLoss().rtt(104, 104, 400).tickLate(104, LATE, true);
		final Session s = t.build();
		final LagEvent e = event(s, 0, 103, 104, Trigger.LONG_LOAD, Trigger.RESENT, Trigger.RTT_SPIKE,
			Trigger.TICK_OFF);
		final Evidence ev = EvidenceBuilder.forEvent(s, e, t.settings());
		assertEquals(105, Rules.S1.score(ev));
		assertEquals("a late tick and a spike: both supports", 110, Rules.N3.score(ev));

		final Verdict v = judge.judgeEvent(s, e, t.settings());
		assertCantTell(v);
		assertEquals("It was map loading or lost packets.", v.proof);
	}

	/** S1 (105) against N3 with one support (100): five apart, under the margin. */
	@Test
	public void loadDuringLossWithOneSupportIsCantTellNamingBoth()
	{
		final Trace t = loadDuringLoss().tickLate(104, LATE, true);
		final Session s = t.build();
		final LagEvent e = event(s, 0, 103, 104, Trigger.LONG_LOAD, Trigger.RESENT, Trigger.TICK_OFF);
		final Evidence ev = EvidenceBuilder.forEvent(s, e, t.settings());
		assertEquals(105, Rules.S1.score(ev));
		assertEquals("the late tick alone", 100, Rules.N3.score(ev));

		final Verdict v = judge.judgeEvent(s, e, t.settings());
		assertCantTell(v);
		assertEquals("It was map loading or lost packets.", v.proof);
	}

	/** With no support N3 is 15 behind, and the load is named; each support for the rival takes that away. */
	@Test
	public void moreEvidenceForTheRivalNeverMakesItSure()
	{
		final Trace t = loadDuringLoss();
		final Session s = t.build();
		final LagEvent e = event(s, 0, 103, 103, Trigger.LONG_LOAD, Trigger.RESENT);
		final Evidence ev = EvidenceBuilder.forEvent(s, e, t.settings());
		assertEquals(105, Rules.S1.score(ev));
		assertEquals(90, Rules.N3.score(ev));

		final Verdict v = judge.judgeEvent(s, e, t.settings());
		assertEquals(Cause.MAP_LOAD, v.cause);
		assertEquals(Confidence.SURE, v.confidence);
		assertNull(v.alsoA);
		assertNull(v.alsoB);
	}

	/** The margin is on the SCORES: the rank orders the two names and never picks a winner. */
	@Test
	public void rankOnlyOrdersTheNames()
	{
		// A long load (seconds 100 to 102, LONG_LOAD in 103) and lost packets in the same second, with a late tick
		// and a ping spike. S1 (rank 2) scores its 105; N3 (rank 3) scores 90 and both supports, 110. The top SCORER
		// is the higher rank number, so a judge that named the top scorer first would put lost packets first.
		final Trace t = Trace.steady(200).usual(USUAL).loading(100, 1000).loading(101, 1000).loading(102, 400)
			.resent(103, 90).rtt(103, 103, 400).tickLate(103, LATE, true);
		final Session s = t.build();
		final LagEvent e = event(s, 0, 103, 103, Trigger.LONG_LOAD, Trigger.RESENT, Trigger.RTT_SPIKE,
			Trigger.TICK_OFF);
		final Evidence ev = EvidenceBuilder.forEvent(s, e, t.settings());
		assertEquals(105, Rules.S1.score(ev));
		assertEquals(110, Rules.N3.score(ev));
		for (Rule other : Rules.ALL)
		{
			if (other.kind == Rule.Kind.EVENT && other != Rules.S1 && other != Rules.N3)
			{
				assertEquals(other.id, 0, other.score(ev));
			}
		}

		final Verdict v = judge.judgeEvent(s, e, t.settings());
		assertEquals("five apart, under the margin", Cause.NOT_SURE, v.cause);
		assertEquals("the lower rank first, though it scored less", Cause.MAP_LOAD, v.alsoA);
		assertEquals(Cause.UPLOAD_LOSS, v.alsoB);
		assertEquals("It was map loading or lost packets.", v.proof);

		// Where the lower rank is also the top scorer the order is the same: W1 (rank 5, 85) before N6 (rank 7, 75).
		// N6 needs the NO_TICK trigger since the first live look, so this event carries one.
		final Trace world = Trace.steady(200).usual(USUAL).ticksEvery(120, 125, 900);
		final Session ws = world.build();
		final Verdict w = judge.judgeEvent(ws, event(ws, 0, 120, 125, Trigger.TICK_OFF, Trigger.NO_TICK),
			world.settings());
		assertEquals(Cause.NOT_SURE, w.cause);
		assertEquals(Cause.SLOW_WORLD, w.alsoA);
		assertEquals(Cause.DELIVERY_GAP, w.alsoB);
	}

	private static void assertCantTell(Verdict v)
	{
		assertEquals(Cause.NOT_SURE, v.cause);
		assertEquals(Confidence.CANT_TELL, v.confidence);
		assertEquals("can't tell is never a red lag (the first live look)", Level.WARN, v.level);
		assertEquals("the lower rank first", Cause.MAP_LOAD, v.alsoA);
		assertEquals(Cause.UPLOAD_LOSS, v.alsoB);
		assertEquals("Can't tell yet", v.headline);
		assertEquals("Wait for it to happen again.", v.fix);
		assertEquals(0, v.eventId);
	}
}
