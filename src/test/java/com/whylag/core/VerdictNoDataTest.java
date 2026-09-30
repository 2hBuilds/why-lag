package com.whylag.core;

import java.util.ArrayList;
import java.util.List;
import org.junit.Test;
import static com.whylag.core.VerdictCauseTest.USUAL;
import static com.whylag.core.VerdictCauseTest.card;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

/**
 * What the judge says when a signal cannot be read (contract 6.3, section 7 L4): no ping, no usual. A missing
 * signal costs reach or a step of confidence, never a wrong cause.
 */
public class VerdictNoDataTest
{
	private static final int NO_USUAL = -1;

	/** One judged event: the trace's session and settings, the event, and the verdict. */
	private static final class Judged
	{
		final String name;
		final Session s;
		final SettingsView settings;
		final LagEvent event;
		final Verdict verdict;
		final Evidence evidence;

		Judged(String name, Trace t, VerdictCauseTest.LagEventSpec spec)
		{
			this.name = name;
			s = t.build();
			settings = t.settings();
			event = spec.on(s);
			verdict = new VerdictEngine().judgeEvent(s, event, settings);
			evidence = EvidenceBuilder.forEvent(s, event, settings);
		}
	}

	private static VerdictCauseTest.LagEventSpec spec(int from, int to, Trigger first, Trigger... more)
	{
		return new VerdictCauseTest.LagEventSpec(0, from, to, first, more);
	}

	// ------------------------------------------------------------------ the traces

	private static Judged lossWithNoPing()
	{
		return new Judged("N3, no ping", VerdictCauseTest.n3Trace().rttNoData(0, 199, NoData.UNSUPPORTED),
			spec(120, 120, Trigger.RESENT));
	}

	private static Judged stallWithNoPing()
	{
		return new Judged("S3, no ping", VerdictCauseTest.s3Trace().rttNoData(0, 199, NoData.ERROR),
			spec(120, 120, Trigger.FRAME_GAP));
	}

	private static Judged slowWorldWithNoPing()
	{
		return new Judged("W1, no ping", VerdictCauseTest.w1Trace().rttNoData(0, 199, NoData.UNSUPPORTED),
			spec(120, 133, Trigger.TICK_OFF));
	}

	private static Judged slowWorldWithAStalePing()
	{
		return new Judged("W1, stale ping", VerdictCauseTest.w1Trace().rttStale(100, 199),
			spec(120, 133, Trigger.TICK_OFF));
	}

	private static Judged stallWithNoUsual()
	{
		return new Judged("S3, no usual", Trace.steady(200).frameGap(120, 600, 480),
			spec(120, 120, Trigger.FRAME_GAP).usual(NO_USUAL));
	}

	private static Judged slowWorldWithNoUsual()
	{
		return new Judged("W1, no usual", Trace.steady(200).ticksEvery(120, 133, 900),
			spec(120, 133, Trigger.TICK_OFF).usual(NO_USUAL));
	}

	/**
	 * Ticks stop for 3 s while a fresh RTT sits at 400 ms, before this world has a usual: the first quiet seconds of
	 * every world, a hop included, since the detector resets the usual when the world changes.
	 */
	private static Trace gapWithAHighPing()
	{
		return Trace.steady(200).noTicks(120, 121).rtt(120, 122, 400);
	}

	private static Judged gapWithAHighPingAndNoUsual()
	{
		return new Judged("N6, known ping, no usual", gapWithAHighPing(),
			spec(120, 122, Trigger.NO_TICK, Trigger.TICK_OFF, Trigger.RTT_SPIKE).usual(NO_USUAL));
	}

	private static List<Judged> everyTraceAbove()
	{
		final List<Judged> out = new ArrayList<>();
		out.add(lossWithNoPing());
		out.add(stallWithNoPing());
		out.add(slowWorldWithNoPing());
		out.add(slowWorldWithAStalePing());
		out.add(stallWithNoUsual());
		out.add(slowWorldWithNoUsual());
		out.add(gapWithAHighPingAndNoUsual());
		return out;
	}

	// ------------------------------------------------------------------ the tests

	@Test
	public void noPingLowersTheCeiling()
	{
		final Judged loss = lossWithNoPing();
		assertFalse(loss.evidence.rttKnown);
		assertEquals(Evidence.NO_DATA, loss.evidence.rttSpike);
		assertEquals("the spike support is skipped", 90, Rules.N3.score(loss.evidence));
		assertEquals(Cause.UPLOAD_LOSS, loss.verdict.cause);
		assertEquals("Likely, one step down", Confidence.HINT, loss.verdict.confidence);
		// With its ping the same trace answers at its full ceiling.
		assertEquals(Confidence.LIKELY, VerdictCauseTest.n3().verdict().confidence);

		final Judged stall = stallWithNoPing();
		assertEquals("the calm-ping support is skipped", 80, Rules.S3.score(stall.evidence));
		assertEquals(Cause.CLIENT_BUSY, stall.verdict.cause);
		assertEquals("Hint, one step down", Confidence.CANT_TELL, stall.verdict.confidence);
		assertEquals(Confidence.HINT, VerdictCauseTest.s3().verdict().confidence);
	}

	/**
	 * W1 needs a known ping. Before the first live look the slow ticks then fell to N6, "No ticks for 0.9 s": a 900 ms
	 * gap is no stop, and N6 needs the NO_TICK trigger since (2026-09-29), so the judge can't tell and names the ticks.
	 */
	@Test
	public void noPingFailsW1AndCannotTell()
	{
		for (Judged j : new Judged[] {slowWorldWithNoPing(), slowWorldWithAStalePing()})
		{
			assertFalse(j.name, j.evidence.rttKnown);
			assertEquals(j.name + ": W1 needs a known ping", 0, Rules.W1.score(j.evidence));
			assertEquals(j.name + ": N6 needs a real stop", 0, Rules.N6.score(j.evidence));
			assertEquals(j.name, Cause.NOT_SURE, j.verdict.cause);
			assertEquals(j.name, Confidence.CANT_TELL, j.verdict.confidence);
			assertTrue(j.name + ": " + j.verdict.proof,
				j.verdict.proof.startsWith("Ticks ran 300 ms off. Cause not measured."));
			assertFalse(j.name, j.verdict.proof.contains("No ticks"));
		}
		assertEquals(Cause.SLOW_WORLD, VerdictCauseTest.w1().verdict().cause);
	}

	@Test
	public void noUsualMakesTheSpikeNoData()
	{
		final Judged stall = stallWithNoUsual();
		assertTrue("the ping itself is there", stall.evidence.rttKnown);
		assertEquals(-1, stall.evidence.rttUsual);
		assertEquals(Evidence.NO_DATA, stall.evidence.rttSpike);
		assertEquals(Cause.CLIENT_BUSY, stall.verdict.cause);
		assertEquals("one step under its ceiling", Confidence.CANT_TELL, stall.verdict.confidence);
		assertEquals("Can't tell", stall.verdict.confidence.word());

		final Judged world = slowWorldWithNoUsual();
		assertEquals(Evidence.NO_DATA, world.evidence.rttSpike);
		assertEquals("a need on the spike fails", 0, Rules.W1.score(world.evidence));
		assertNotEquals(Cause.SLOW_WORLD, world.verdict.cause);
		assertNotEquals(Cause.SLOW_WORLD, world.verdict.alsoA);
		// N6 needs a real stop since the first live look: the 900 ms ticks are X's, not "No ticks for 0.9 s".
		assertEquals(Cause.NOT_SURE, world.verdict.cause);
		assertTrue("the ping itself is there", world.evidence.rttKnown);
		assertEquals("no usual: the ping was not judged, so the proof does not call it fine",
			"Ticks ran 300 ms off. Cause not measured.", world.verdict.proof);

		// The trigger bit does not make data: with no usual the spike has none, whatever the bit says.
		final Trace t = Trace.steady(200).rtt(120, 120, 400);
		final Session s = t.build();
		final LagEvent bit = spec(120, 120, Trigger.TICK_OFF, Trigger.RTT_SPIKE).usual(NO_USUAL).on(s);
		assertEquals(Evidence.NO_DATA, EvidenceBuilder.forEvent(s, bit, t.settings()).rttSpike);
		final LagEvent known = spec(120, 120, Trigger.TICK_OFF, Trigger.RTT_SPIKE).on(s);
		assertEquals(Evidence.YES, EvidenceBuilder.forEvent(s, known, t.settings()).rttSpike);

		// D1 with no usual and clean re-sends.
		final Trace d = Trace.steady(200).disconnect(120);
		final Session ds = d.build();
		final Verdict v = new VerdictEngine().judgeEvent(ds, spec(120, 120, Trigger.DISCONNECT).usual(NO_USUAL)
			.on(ds), d.settings());
		assertEquals(Cause.DISCONNECT, v.cause);
		assertEquals("No ping data just before.", v.proof);
		assertEquals("Log in again. Note if it repeats.", v.fix);
	}

	/**
	 * N6's ping part (6.4) is its "ping no data" form whenever the spike has no data, and with no usual it has none
	 * even though a fresh RTT exists (6.3). The proof then agrees with {@code ruledOut}, which leaves the ping out too.
	 */
	@Test
	public void aKnownPingWithNoUsualIsNotCalledFine()
	{
		final Judged j = gapWithAHighPingAndNoUsual();
		assertTrue("a fresh RTT", j.evidence.rttKnown);
		assertEquals(400, j.evidence.rttMax);
		assertEquals(-1, j.evidence.rttUsual);
		assertEquals("the trigger bit does not make data", Evidence.NO_DATA, j.evidence.rttSpike);
		assertEquals(3000, j.evidence.noTickMs);
		assertEquals("the exclude on the spike is skipped", 75, Rules.N6.score(j.evidence));
		assertEquals(Cause.DELIVERY_GAP, j.verdict.cause);
		assertEquals(Confidence.CANT_TELL, j.verdict.confidence);
		assertEquals("No ticks for 3.0 s. Frames were fine.", j.verdict.proof);
		assertEquals("Frames were fine.", j.verdict.ruledOut);
		assertFalse(j.verdict.proof, j.verdict.proof.contains("ping"));

		// With a usual of 40 the same 400 ms is a spike, which excludes N6: nothing answers, and X names the ticks.
		final Trace known = gapWithAHighPing().usual(USUAL);
		final Session s = known.build();
		final Verdict x = new VerdictEngine().judgeEvent(s,
			spec(120, 122, Trigger.NO_TICK, Trigger.TICK_OFF, Trigger.RTT_SPIKE).on(s), known.settings());
		assertEquals(Cause.NOT_SURE, x.cause);
		assertEquals("Ticks ran 2,400 ms off. Cause not measured.", x.proof);

		// And with the usual and a calm ping, N6 at its full ceiling calls the ping fine.
		final Trace calm = Trace.steady(200).usual(USUAL).noTicks(120, 121);
		final Session cs = calm.build();
		final Verdict n6 = new VerdictEngine().judgeEvent(cs, spec(120, 122, Trigger.NO_TICK, Trigger.TICK_OFF)
			.on(cs), calm.settings());
		assertEquals(Cause.DELIVERY_GAP, n6.cause);
		assertEquals("No ticks for 3.0 s. Frames and ping were fine.", n6.proof);
		assertEquals("Frames and ping were fine.", n6.ruledOut);
	}

	@Test
	public void aVerdictNeverExceedsItsCeiling()
	{
		int checked = 0;
		for (Judged j : everyTraceAbove())
		{
			assertWithinCeiling(j.name, j.verdict);
			checked++;
		}
		for (VerdictCauseTest.Case c : VerdictCauseTest.all())
		{
			assertWithinCeiling(c.id, c.verdict());
			checked++;
		}
		assertEquals(19, checked);
	}

	private static void assertWithinCeiling(String name, Verdict v)
	{
		Rule rule = null;
		for (Rule r : Rules.ALL)
		{
			final boolean condition = r.kind == Rule.Kind.CONDITION;
			if (r.cause == v.cause && (v.cause != Cause.SLOW_WORLD || condition == (v.level == Level.WARN)))
			{
				rule = r;
			}
		}
		assertTrue(name + ": a rule gives " + v.cause, rule != null);
		assertTrue(name + ": " + v.confidence + " is over the ceiling " + rule.ceiling + " of " + rule.id,
			v.confidence.ordinal() >= rule.ceiling.ordinal());
		assertFalse(name + " is a verdict, not a state", v.cause == Cause.WARMING_UP);
	}
}
