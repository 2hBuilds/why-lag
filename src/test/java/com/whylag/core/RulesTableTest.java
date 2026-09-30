package com.whylag.core;

import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;

/**
 * The rules table of contract 6.3, pinned by literal: rank, id, cause, kind, base, ceiling and the number of needs,
 * excludes and supports of every row, in rank order; and the scoring rule that every row follows. The rows of the
 * memory (G1, G2) and of the client that was kept waiting (S4) are gone (1.0.0, the Hub's rule), and S3 judges by the
 * frame gap alone.
 */
public class RulesTableTest
{
	/** rank, id, cause, kind, base, ceiling, needs, excludes, supports - typed from the table of contract 6.3. */
	private static final Object[][] TABLE = {
		{1, "D1", Cause.DISCONNECT, Rule.Kind.EVENT, 200, Confidence.SURE, 1, 0, 0},
		{2, "S1", Cause.MAP_LOAD, Rule.Kind.EVENT, 105, Confidence.SURE, 1, 0, 0},
		{3, "N3", Cause.UPLOAD_LOSS, Rule.Kind.EVENT, 90, Confidence.LIKELY, 1, 1, 2},
		{4, "N2", Cause.PING_JUMPY, Rule.Kind.EVENT, 85, Confidence.LIKELY, 4, 2, 1},
		{5, "W1", Cause.SLOW_WORLD, Rule.Kind.EVENT, 85, Confidence.LIKELY, 9, 1, 1},
		{6, "S3", Cause.CLIENT_BUSY, Rule.Kind.EVENT, 80, Confidence.HINT, 1, 3, 1},
		{7, "N6", Cause.DELIVERY_GAP, Rule.Kind.EVENT, 75, Confidence.CANT_TELL, 3, 2, 0},
		{8, "F1", Cause.FRAME_CAP, Rule.Kind.CONDITION, 120, Confidence.SURE, 2, 0, 0},
		{9, "N1", Cause.PING_HIGH, Rule.Kind.CONDITION, 60, Confidence.SURE, 3, 0, 0},
		{10, "F2", Cause.SLOW_DRAWING, Rule.Kind.CONDITION, 60, Confidence.LIKELY, 1, 1, 1},
		{11, "W1c", Cause.SLOW_WORLD, Rule.Kind.CONDITION, 55, Confidence.HINT, 5, 0, 1},
		{12, "V2", Cause.ALL_CLEAR, Rule.Kind.ALWAYS, 10, Confidence.SURE, 0, 0, 0},
		{13, "X", Cause.NOT_SURE, Rule.Kind.ENGINE, 0, Confidence.CANT_TELL, 0, 0, 0},
	};

	@Test
	public void everyRowIsPinned()
	{
		assertEquals("one rule per row of 6.3", 13, Rules.ALL.length);
		assertEquals(13, Rules.count());
		for (int i = 0; i < TABLE.length; i++)
		{
			final Object[] row = TABLE[i];
			final Rule r = Rules.ALL[i];
			final String id = (String) row[1];
			assertSame(id, r, Rules.at(i));
			assertEquals(id + " rank", row[0], r.rank);
			assertEquals("rank order", i + 1, r.rank);
			assertEquals(id + " id", id, r.id);
			assertEquals(id + " cause", row[2], r.cause);
			assertEquals(id + " kind", row[3], r.kind);
			assertEquals(id + " base", row[4], r.base);
			assertEquals(id + " ceiling", row[5], r.ceiling);
			assertEquals(id + " needs", row[6], r.needs());
			assertEquals(id + " excludes", row[7], r.excludes());
			assertEquals(id + " supports", row[8], r.supports());
			assertSame(id, r, Rules.byId(id));
		}
		for (String gone : new String[] {"G1", "G2", "G3", "S4", "S5"})
		{
			assertNull(gone + " is not a row any more", Rules.byId(gone));
		}
	}

	@Test
	public void theTwelveRulesAreTheWaveOneCauses()
	{
		int events = 0;
		int conditions = 0;
		for (Rule r : Rules.ALL)
		{
			assertEquals(r.id + " is a wave-one cause", true, r.cause.inWaveOne());
			// W1c reuses W1's cause; every other row's id is its cause's.
			assertEquals(r.id, r.id.equals("W1c") ? "W1" : r.id, r.cause.id());
			events += r.kind == Rule.Kind.EVENT ? 1 : 0;
			conditions += r.kind == Rule.Kind.CONDITION ? 1 : 0;
		}
		assertEquals(7, events);
		assertEquals(4, conditions);
	}

	@Test
	public void aRuleScoresItsBaseAndTenForEachSupport()
	{
		final Evidence e = lostPackets();
		assertEquals("no support", 90, Rules.N3.score(e));
		e.tickLate = 1;
		assertEquals("one support", 100, Rules.N3.score(e));
		e.rttSpike = Evidence.YES;
		assertEquals("both supports", 110, Rules.N3.score(e));
		assertEquals(Confidence.LIKELY, Rules.N3.confidence(e));
	}

	@Test
	public void aFailedNeedOrAnExcludeThatHoldsScoresNothing()
	{
		final Evidence e = lostPackets();
		e.resentCounts = false;
		assertEquals("the need fails", 0, Rules.N3.score(e));
		e.resentCounts = true;
		e.disconnect = true;
		assertEquals("the exclude holds", 0, Rules.N3.score(e));
	}

	@Test
	public void aNeedWithNoDataFailsAndASkippedSignalLowersTheCeiling()
	{
		final Evidence e = lostPackets();
		e.rttSpike = Evidence.NO_DATA;
		assertEquals("a support with no data is skipped", 90, Rules.N3.score(e));
		assertEquals("and costs one step", Confidence.HINT, Rules.N3.confidence(e));

		final Evidence w = new Evidence();
		assertEquals("a rule whose need has no data does not answer", 0, Rules.F2.score(w));
		assertEquals("F1's need has no data either", 0, Rules.F1.score(w));

		final Evidence stall = new Evidence();
		stall.frameGapMs = 480;
		stall.frameLimitMs = 200;
		stall.rttSpike = Evidence.NO_DATA;
		assertEquals("S3 answers on the frame gap alone", 80, Rules.S3.score(stall));
		assertEquals("one signal with no data, the ping: one step down", Confidence.CANT_TELL,
			Rules.S3.confidence(stall));
		stall.rttKnown = true;
		stall.rttSpike = Evidence.NO;
		assertEquals("a calm ping supports it", 90, Rules.S3.score(stall));
		assertEquals("and keeps its ceiling", Confidence.HINT, Rules.S3.confidence(stall));
		stall.loadMs = 900;
		assertEquals("a map load excludes it", 0, Rules.S3.score(stall));
		stall.loadMs = 0;
		stall.capWaits = true;
		stall.capIntervalMs = 250;
		assertEquals("a waiting cap explains a gap of up to two of its intervals: not a freeze", 0,
			Rules.S3.score(stall));
		stall.capIntervalMs = 100;
		assertEquals("but not a gap of nearly five", 90, Rules.S3.score(stall));
	}

	@Test
	public void v2AlwaysScoresTenAndXNothing()
	{
		assertEquals(10, Rules.V2.score(new Evidence()));
		assertEquals(0, Rules.X.score(lostPackets()));
		for (Rule r : Rules.ALL)
		{
			if (r.kind == Rule.Kind.EVENT || r.kind == Rule.Kind.CONDITION)
			{
				assertEquals(r.id + " on no data", 0, r.score(new Evidence()));
			}
		}
	}

	@Test
	public void theBestConditionIsTheHighestScoreAndTheLowerRankOnATie()
	{
		final Evidence e = new Evidence();
		assertNull("no condition scores", Rules.bestCondition(e));
		// N1 and F2 both at 60: rank 9 before rank 10.
		e.rttKnown = true;
		e.rtt = 180;
		e.rttMin = 170;
		e.rttMax = 190;
		e.lowFpsS = 30;
		e.lowFps = 24;
		assertEquals(60, Rules.N1.score(e));
		assertEquals("F2's ping support fails at 180 ms", 60, Rules.F2.score(e));
		assertSame(Rules.N1, Rules.bestCondition(e));
		e.capSelfSet = true;
		e.capHeldS = 10;
		assertSame(Rules.F1, Rules.bestCondition(e));
		assertEquals("F1's needs hold: F2 is excluded", 0, Rules.F2.score(e));
		assertNotNull(Rules.bestCondition(e));
	}

	private static Evidence lostPackets()
	{
		final Evidence e = new Evidence();
		e.event = true;
		e.resentCounts = true;
		e.rttKnown = true;
		e.rttUsual = 40;
		e.rttSpike = Evidence.NO;
		return e;
	}
}
