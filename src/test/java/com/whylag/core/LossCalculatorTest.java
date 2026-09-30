package com.whylag.core;

import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * {@link LossCalculator} (contract section 7, L2): cumulative counters in, one second out. A test's samples are one
 * second apart unless it says otherwise; the first sample is the base.
 */
public class LossCalculatorTest
{
	private static final long START = 3_000_000_000_000L;
	private static final long NANOS_PER_SECOND = 1_000_000_000L;
	private static final long RTT_40_MS = 40_000L;

	private LossCalculator loss;
	private HostSecond out;
	private long now;

	@Before
	public void setUp()
	{
		loss = new LossCalculator(Os.WINDOWS);
		out = new HostSecond();
		now = START;
	}

	/** One sample, one second after the one before. */
	private void sample(long rttMicros, long sent, long resent, NoData conn)
	{
		now += NANOS_PER_SECOND;
		out.clear();
		loss.sample(now, rttMicros, sent, resent, conn, out);
	}

	private void sample(long sent, long resent)
	{
		sample(RTT_40_MS, sent, resent, NoData.NONE);
	}

	@Test
	public void countersBecomeDifferences()
	{
		sample(50_000, 700);
		assertEquals("the first sample is the base", 0, out.sentUnits);
		assertEquals(0, out.resentUnits);
		sample(53_000, 700);
		assertEquals(3000, out.sentUnits);
		assertEquals(0, out.resentUnits);
		sample(54_000, 760);
		assertEquals(1000, out.sentUnits);
		assertEquals(60, out.resentUnits);
		assertEquals(4000, loss.sentInWindow());
		assertEquals(60, loss.resentInWindow());
		assertEquals(15, loss.perMille());
	}

	@Test
	public void theWindowIsTheLastSixteenSamples()
	{
		sample(0, 0);
		sample(5000, 500);
		for (int i = 1; i < Thresholds.RESENT_WINDOW_S; i++)
		{
			sample(5000 + i * 1000L, 500);
			assertEquals(5000 + i * 1000, loss.sentInWindow());
			assertEquals(500, loss.resentInWindow());
		}
		// the sample that carried the 500 re-sent leaves the window
		sample(5000 + Thresholds.RESENT_WINDOW_S * 1000L, 500);
		assertEquals(Thresholds.RESENT_WINDOW_S * 1000, loss.sentInWindow());
		assertEquals(0, loss.resentInWindow());
		assertEquals(0, loss.perMille());
	}

	@Test
	public void shareIsNeverOver1000()
	{
		sample(10_000, 0);
		// more re-sent than sent: the two counters are read a moment apart
		sample(13_000, 9000);
		assertEquals(3000, loss.sentInWindow());
		assertEquals(9000, loss.resentInWindow());
		assertEquals(1000, loss.perMille());
		sample(16_000, 9000);
		assertEquals(1000, loss.perMille());
		for (int i = 0; i < 40; i++)
		{
			sample(16_000 + i * 977L, 9000 + i * 3L);
			assertTrue(loss.perMille() >= 0 && loss.perMille() <= 1000);
		}
	}

	@Test
	public void underTheMinimumIsZero()
	{
		sample(0, 0);
		sample(Thresholds.RESENT_MIN_BYTES - 1, 1000);
		assertEquals(Thresholds.RESENT_MIN_BYTES - 1, loss.sentInWindow());
		assertEquals(1000, loss.resentInWindow());
		assertEquals("too little was sent for a share to mean anything", 0, loss.perMille());
		sample(Thresholds.RESENT_MIN_BYTES, 1000);
		assertEquals(Thresholds.RESENT_MIN_BYTES, loss.sentInWindow());
		assertEquals(1000 * 1000 / Thresholds.RESENT_MIN_BYTES, loss.perMille());
	}

	@Test
	public void aFallingCounterResets()
	{
		sample(90_000, 100);
		sample(95_000, 400);
		assertEquals(5000, loss.sentInWindow());
		assertEquals(300, loss.resentInWindow());
		// a new socket counts from 0 again (C1)
		sample(1200, 0);
		assertEquals("no negative and no huge difference", 0, out.sentUnits);
		assertEquals(0, out.resentUnits);
		assertEquals("the old socket's window is gone", 0, loss.sentInWindow());
		assertEquals(0, loss.resentInWindow());
		assertEquals(0, loss.perMille());
		sample(4200, 30);
		assertEquals(3000, out.sentUnits);
		assertEquals(30, out.resentUnits);
		assertEquals(3000, loss.sentInWindow());
		assertEquals(10, loss.perMille());

		// the re-sent counter alone falling is a new socket too
		sample(9000, 10);
		assertEquals(0, out.sentUnits);
		assertEquals(0, loss.sentInWindow());
	}

	@Test
	public void segmentsOnLinuxUseTheUnitMinimum()
	{
		loss = new LossCalculator(Os.LINUX);
		sample(100, 0);
		sample(100 + Thresholds.RESENT_MIN_UNITS - 1, 2);
		assertEquals(0, loss.perMille());
		sample(100 + Thresholds.RESENT_MIN_UNITS, 2);
		assertEquals("8 segments are enough where segments are counted", 250, loss.perMille());

		// the same numbers as bytes are far under the minimum
		setUp();
		sample(100, 0);
		sample(100 + Thresholds.RESENT_MIN_UNITS, 2);
		assertEquals(0, loss.perMille());
	}

	@Test
	public void rttAgeGrowsWhileNothingIsSent()
	{
		sample(1000, 0);
		sample(2000, 0);
		assertEquals(40, out.rttMs);
		assertEquals(0, out.rttAgeS);
		assertEquals(NoData.NONE, out.conn);
		for (int age = 1; age <= Thresholds.RTT_STALE_S + 3; age++)
		{
			sample(2000, 0);
			assertEquals("the number is kept", 40, out.rttMs);
			assertEquals(age, out.rttAgeS);
			assertEquals(0, out.sentUnits);
		}
		sample(2600, 0);
		assertEquals("new data went out: the RTT is a new one", 0, out.rttAgeS);
		assertEquals(NoData.NONE, out.conn);
	}

	@Test
	public void rttAgeGrowsWhileOnlyResendsGoOut()
	{
		sample(1000, 0);
		sample(2000, 0);
		assertEquals(0, out.rttAgeS);
		for (int age = 1; age <= 4; age++)
		{
			// sent moves, but every unit of it is a re-send
			sample(2000 + age * 500L, age * 500L);
			assertEquals(500, out.sentUnits);
			assertEquals(500, out.resentUnits);
			assertEquals(age, out.rttAgeS);
		}
		sample(4600, 2100);
		assertEquals("600 sent, 100 of them re-sent: new data went out", 0, out.rttAgeS);
	}

	@Test
	public void theAgeCountsSecondsThoughASampleComesAFewMsEarlyOrLate()
	{
		sample(1000, 0);
		sample(2000, 0);
		final long rise = now;
		out.clear();
		loss.sample(rise + NANOS_PER_SECOND - 6_000_000L, RTT_40_MS, 2000, 0, NoData.NONE, out);
		assertEquals(1, out.rttAgeS);
		loss.sample(rise + 2 * NANOS_PER_SECOND + 9_000_000L, RTT_40_MS, 2000, 0, NoData.NONE, out);
		assertEquals(2, out.rttAgeS);
	}

	@Test
	public void resetIsAppliedOnTheSamplingThread()
	{
		sample(10_000, 0);
		sample(14_000, 200);
		loss.requestReset();
		assertEquals("the request alone changes nothing", 4000, loss.sentInWindow());
		assertEquals(200, loss.resentInWindow());
		assertEquals(50, loss.perMille());
		// the next sample applies it first: it is the base of what follows
		sample(19_000, 900);
		assertEquals(0, out.sentUnits);
		assertEquals(0, out.resentUnits);
		assertEquals(0, loss.sentInWindow());
		assertEquals(0, loss.resentInWindow());
		sample(20_000, 900);
		assertEquals("one request is one reset", 1000, out.sentUnits);
		assertEquals(1000, loss.sentInWindow());
	}

	@Test
	public void resetOnHop()
	{
		sample(800_000, 5000);
		sample(803_000, 5600);
		assertEquals(40, out.rttMs);
		assertEquals(200, loss.perMille());
		loss.requestReset();
		// the new world's socket has counted past the old one already: without the reset this would read as sent
		sample(RTT_40_MS, 900_000, 6000, NoData.NONE);
		assertEquals(0, out.sentUnits);
		assertEquals(0, out.resentUnits);
		assertEquals(0, loss.perMille());
		assertEquals("the old world's RTT is not this world's", -1, out.rttMs);
		assertEquals(NoData.STALE, out.conn);
		sample(55_000L, 903_000, 6000, NoData.NONE);
		assertEquals(3000, out.sentUnits);
		assertEquals(55, out.rttMs);
		assertEquals(0, out.rttAgeS);
		assertEquals(NoData.NONE, out.conn);
	}

	@Test
	public void anRttThatIsNotFreshIsWrittenStale()
	{
		// no RTT yet: the base sample, and a probe that has no number
		sample(1000, 0);
		assertEquals(-1, out.rttMs);
		assertEquals(-1, out.rttAgeS);
		assertEquals(NoData.STALE, out.conn);
		sample(-1, 2000, 0, NoData.NONE);
		assertEquals(-1, out.rttMs);
		assertEquals(NoData.STALE, out.conn);

		sample(3000, 0);
		assertEquals(40, out.rttMs);
		assertEquals(NoData.NONE, out.conn);
		for (int age = 1; age <= Thresholds.RTT_STALE_S; age++)
		{
			sample(3000, 0);
			assertEquals("fresh up to RTT_STALE_S", NoData.NONE, out.conn);
		}
		sample(3000, 0);
		assertEquals(Thresholds.RTT_STALE_S + 1, out.rttAgeS);
		assertEquals("older than RTT_STALE_S: the number is kept", 40, out.rttMs);
		assertEquals(NoData.STALE, out.conn);

		// a probe's reason passes through, with no number
		for (NoData why : new NoData[] {NoData.NOT_LOGGED_IN, NoData.NOT_CONNECTED, NoData.UNSUPPORTED, NoData.ERROR})
		{
			sample(RTT_40_MS, 4000, 0, why);
			assertEquals(why, out.conn);
			assertEquals(-1, out.rttMs);
			assertEquals(-1, out.rttAgeS);
			assertEquals(0, out.sentUnits);
		}
		// a null reason is no reason
		sample(RTT_40_MS, 5000, 0, null);
		assertEquals(NoData.NONE, out.conn);
		assertEquals(40, out.rttMs);
		assertEquals("what went out while the probe had no data is not lost", 2000, out.sentUnits);
	}

	@Test
	public void sampleLeavesTheHostHalfAlone()
	{
		out.heapUsedMb = 412;
		out.procCpuPct = 33;
		out.sysCpuPct = 21;
		loss.sample(START, RTT_40_MS, 100, 0, NoData.NONE, out);
		assertEquals(412, out.heapUsedMb);
		assertEquals(33, out.procCpuPct);
		assertEquals(21, out.sysCpuPct);
	}
}
