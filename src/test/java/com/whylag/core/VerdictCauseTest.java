package com.whylag.core;

import java.util.ArrayList;
import java.util.List;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

/**
 * One synthetic trace per rule (contract section 7, L4; section 8): each of the fifteen rules answers its own
 * trace, at its full ceiling, and no other rule is within the margin. Every trace carries a usual of 40
 * ({@code Trace.usual(40)}, and {@code rttBeforeMs} 40 on its event), so no rule loses a step for a missing usual.
 *
 * <p>The judge cannot run the detector (L3), so each closed event is built by constructor over the trace's span.
 * The helpers here ({@link #event}, {@link Case}, {@link #card}) are shared by the other verdict tests.
 */
public class VerdictCauseTest
{
	static final int USUAL = 40;

	/** One trace with what is judged on it: an event, or the card at {@code nowSec} for a condition. */
	static final class Case
	{
		final String id;
		final Session s;
		final SettingsView settings;
		final LagEvent event;
		final int nowSec;

		Case(String id, Trace t, LagEventSpec spec, int nowSec)
		{
			this.id = id;
			this.s = t.build();
			this.settings = t.settings();
			this.event = spec == null ? null : spec.on(s);
			this.nowSec = nowSec;
		}

		Rule rule()
		{
			return Rules.byId(id);
		}

		/** The verdict of this case: the event's, or the card's at the end of the trace. */
		Verdict verdict()
		{
			if (event != null)
			{
				return new VerdictEngine().judgeEvent(s, event, settings);
			}
			return card(s, settings, nowSec);
		}

		Evidence evidence()
		{
			return event != null ? EvidenceBuilder.forEvent(s, event, settings)
				: EvidenceBuilder.forCondition(s, nowSec, settings);
		}
	}

	/** What a hand-built event is made of, before it meets its session. */
	static final class LagEventSpec
	{
		private final long id;
		private final int from, to;
		private final Trigger first;
		private int triggers;
		private int rttBefore = USUAL;
		private boolean open;

		LagEventSpec(long id, int from, int to, Trigger first, Trigger... more)
		{
			this.id = id;
			this.from = from;
			this.to = to;
			this.first = first;
			triggers = first.bit();
			for (Trigger t : more)
			{
				triggers |= t.bit();
			}
		}

		LagEventSpec usual(int rttBeforeMs)
		{
			rttBefore = rttBeforeMs;
			return this;
		}

		LagEventSpec open(boolean isOpen)
		{
			open = isOpen;
			return this;
		}

		/**
		 * The event over the trace's span, as the detector would hand it in. Beside the span, the id, the trigger
		 * bits, {@code first} and {@code rttBeforeMs} the judge reads nothing from it, so the event's own numbers
		 * are given values no ring holds (7777): a judge that read them would print them.
		 */
		LagEvent on(Session s)
		{
			return new LagEvent(id, from, to, s.wallMsOf(from), triggers, first, s.seconds.world(from), 0, 0, 0,
				7777, 7777, 7777, 7777, 7777, 7777, 7777, rttBefore, 7777, 7777, 7777, 7777, 7777, 77, 77, open,
				false, null);
		}
	}

	static LagEvent event(Session s, long id, int from, int to, Trigger first, Trigger... more)
	{
		return new LagEventSpec(id, from, to, first, more).on(s);
	}

	/** The card after one step a second from {@code nowSec} 1 to {@code toNowSec}, with a fresh engine. */
	static Verdict card(Session s, SettingsView settings, int toNowSec)
	{
		final VerdictEngine engine = new VerdictEngine();
		Verdict v = null;
		for (int now = 1; now <= toNowSec; now++)
		{
			v = engine.current(s, now, s.wallMsOf(now), settings);
		}
		return v;
	}

	static SettingsView fpsControl(int maxFps)
	{
		return new SettingsView(Renderer.CPU, true, true, maxFps, false, 0, false, "", 0, 0, "", 0, 60, 768,
			MemorySource.MANAGEMENT, Os.WINDOWS, "");
	}

	static SettingsView heapLimit(int heapMaxMb)
	{
		return new SettingsView(Renderer.CPU, false, false, 0, false, 0, false, "", 0, 0, "", 0, 60, heapMaxMb,
			MemorySource.MANAGEMENT, Os.WINDOWS, "");
	}

	static SettingsView runtimeMemory()
	{
		return new SettingsView(Renderer.CPU, false, false, 0, false, 0, false, "", 0, 0, "", 0, 60, 768,
			MemorySource.RUNTIME, Os.WINDOWS, "");
	}

	// ------------------------------------------------------------------ the fifteen traces

	static Trace d1Trace()
	{
		return Trace.steady(200).usual(USUAL).disconnect(120);
	}

	static Case d1()
	{
		return new Case("D1", d1Trace(), new LagEventSpec(0, 120, 120, Trigger.DISCONNECT), 200);
	}

	static Trace g1Trace()
	{
		return Trace.steady(200).usual(USUAL).heap(120, 120, 742).gcPause(120, 100, 340, 400)
			.frameGap(120, 450, 350).busy(120, 700);
	}

	static Case g1()
	{
		return new Case("G1", g1Trace(), new LagEventSpec(1, 120, 120, Trigger.GC_PAUSE, Trigger.FRAME_GAP), 200);
	}

	static Trace s1Trace()
	{
		return Trace.steady(200).usual(USUAL).loading(100, 1000).loading(101, 1000).loading(102, 400);
	}

	static Case s1()
	{
		return new Case("S1", s1Trace(), new LagEventSpec(2, 103, 103, Trigger.LONG_LOAD), 200);
	}

	static Trace n3Trace()
	{
		return Trace.steady(200).usual(USUAL).resent(120, 90);
	}

	static Case n3()
	{
		return new Case("N3", n3Trace(), new LagEventSpec(3, 120, 120, Trigger.RESENT), 200);
	}

	static Trace n2Trace()
	{
		// A tick TICK_OFF_MS + 150 late (400 since the first live look; it was 250): off by 378 after the 22 ms frame,
		// and the catch-up tick as early.
		return Trace.steady(200).usual(USUAL).rtt(120, 120, 400).rtt(121, 121, 30)
			.tickLate(120, Thresholds.TICK_OFF_MS + 150, true);
	}

	static Case n2()
	{
		return new Case("N2", n2Trace(), new LagEventSpec(4, 120, 121, Trigger.TICK_OFF, Trigger.RTT_SPIKE), 200);
	}

	static Trace w1Trace()
	{
		return Trace.steady(200).usual(USUAL).ticksEvery(120, 133, 900);
	}

	static Case w1()
	{
		return new Case("W1", w1Trace(), new LagEventSpec(5, 120, 133, Trigger.TICK_OFF), 200);
	}

	static Trace s3Trace()
	{
		return Trace.steady(200).usual(USUAL).frameGap(120, 600, 480).busy(120, 700);
	}

	static Case s3()
	{
		return new Case("S3", s3Trace(), new LagEventSpec(6, 120, 120, Trigger.FRAME_GAP), 200);
	}

	static Trace s4Trace()
	{
		return Trace.steady(200).usual(USUAL).frameGap(120, 600, 480).busy(120, 150);
	}

	static Case s4()
	{
		return new Case("S4", s4Trace(), new LagEventSpec(7, 120, 120, Trigger.FRAME_GAP), 200);
	}

	static Trace n6Trace()
	{
		return Trace.steady(200).usual(USUAL).noTicks(120, 121);
	}

	static Case n6()
	{
		return new Case("N6", n6Trace(), new LagEventSpec(8, 120, 122, Trigger.NO_TICK, Trigger.TICK_OFF), 200);
	}

	static Case f1()
	{
		return new Case("F1", Trace.steady(200).usual(USUAL).fps(100, 199, 30).settings(fpsControl(30)), null, 200);
	}

	static Case n1()
	{
		return new Case("N1", Trace.steady(200).usual(USUAL).rtt(100, 199, 180), null, 200);
	}

	static Case f2()
	{
		return new Case("F2", Trace.steady(200).usual(USUAL).fps(100, 199, 24), null, 200);
	}

	static Case w1c()
	{
		return new Case("W1c", Trace.steady(200).usual(USUAL).ticksEvery(100, 199, 640), null, 200);
	}

	static Case g2()
	{
		return new Case("G2", Trace.steady(200).usual(USUAL).settings(heapLimit(512)), null, 200);
	}

	static Case v2()
	{
		return new Case("V2", Trace.steady(200).usual(USUAL), null, 200);
	}

	static List<Case> all()
	{
		final List<Case> out = new ArrayList<>();
		out.add(d1());
		out.add(g1());
		out.add(s1());
		out.add(n3());
		out.add(n2());
		out.add(w1());
		out.add(s3());
		out.add(s4());
		out.add(n6());
		out.add(f1());
		out.add(n1());
		out.add(f2());
		out.add(w1c());
		out.add(g2());
		out.add(v2());
		return out;
	}

	// ------------------------------------------------------------------ the tests

	@Test
	public void thereIsOneTraceForEachOfTheFifteenRules()
	{
		final List<Case> cases = all();
		assertEquals(15, cases.size());
		for (int i = 0; i < cases.size(); i++)
		{
			assertSame("in rank order", Rules.ALL[i], cases.get(i).rule());
		}
	}

	@Test
	public void d1AnswersADisconnect()
	{
		final Verdict v = judged(d1());
		assertWords(v, "Connection lost at 20:54", "Ping and re-sends were fine just before.",
			"Wait a minute, then log in again.");
	}

	@Test
	public void g1AnswersAMemoryPause()
	{
		final Verdict v = judged(g1());
		assertWords(v, "Memory clean-up froze the game", "A 340 ms pause. Memory 742 of 768 MB.",
			"Close the world map. Restart if it repeats.");
	}

	@Test
	public void s1AnswersALongLoad()
	{
		final Verdict v = judged(s1());
		// 6.4 writes "{6} loads in the last 10 min." for every count, one included.
		assertWords(v, "Map loading took 2.4 s", "1 loads in the last 10 min.", "Nothing to fix. It is the map.");
	}

	@Test
	public void n3AnswersLostPackets()
	{
		final Verdict v = judged(n3());
		// 90 re-sent of the 4,500 sent in seconds 118 to 122: 20 per mille.
		assertWords(v, "Packets are being lost", "2 in 100 were re-sent. Ping can look fine.",
			"If every world does it, check cable or Wi-Fi.");
	}

	@Test
	public void n2AnswersAnUnsteadyConnection()
	{
		final Case c = n2();
		final Verdict v = judged(c);
		assertEquals("with its support", 95, Rules.N2.score(c.evidence()));
		assertWords(v, "Your connection is unsteady", "Ping swung 30-400 ms. Ticks came early and late.",
			"Use a cable, not Wi-Fi. Pause downloads.");
	}

	@Test
	public void w1AnswersASlowWorld()
	{
		final Case c = w1();
		final Verdict v = judged(c);
		assertEquals("with its support, the length", 95, Rules.W1.score(c.evidence()));
		assertEquals("N6 needs the NO_TICK trigger since the first live look: slow ticks are no stop", 0,
			Rules.N6.score(c.evidence()));
		assertWords(v, "World 416 is struggling, not you",
			"Ticks 600 to 870+ ms for 14 s. Ping stayed 40 ms, 50 fps.", "Hop to a quieter world.");
	}

	@Test
	public void s3AnswersABusyStall()
	{
		final Case c = s3();
		final Verdict v = judged(c);
		assertEquals("both supports", 100, Rules.S3.score(c.evidence()));
		assertWords(v, "The client itself stalled", "A 480 ms freeze. Connection and world were fine.",
			"Turn plugins off one at a time.");
	}

	@Test
	public void s4AnswersAnIdleStall()
	{
		final Verdict v = judged(s4());
		assertWords(v, "The client was kept waiting", "A 480 ms freeze, but the client was not busy.",
			"Close overlays and recorders.");
	}

	@Test
	public void n6AnswersADeliveryGap()
	{
		final Verdict v = judged(n6());
		assertWords(v, "The game stopped answering", "No ticks for 3.0 s. Frames and ping were fine.",
			"Hop worlds. If it follows you, it is your line.");
	}

	@Test
	public void f1AnswersASelfSetCap()
	{
		final Verdict v = judged(f1());
		assertWords(v, "Frame rate is capped at 30", "Set by FPS Control. Not lag.", "Raise or turn off that cap.");
	}

	@Test
	public void n1AnswersAHighSteadyPing()
	{
		final Verdict v = judged(n1());
		assertWords(v, "Ping is high but steady", "180 ms. Your usual is 40 ms.", "Try a world closer to you.");
	}

	@Test
	public void f2AnswersSlowDrawing()
	{
		final Case c = f2();
		final Verdict v = judged(c);
		assertEquals("with its support, a low ping", 70, Rules.F2.score(c.evidence()));
		assertEquals("The game is drawing slowly", v.headline);
		assertTrue(v.proof, v.proof.startsWith("24 fps for "));
		assertEquals("Turn the GPU plugin on.", v.fix);
	}

	@Test
	public void w1cAnswersASlowWorldAsACondition()
	{
		final Case c = w1c();
		final Verdict v = judged(c);
		assertEquals("with its support", 65, Rules.W1C.score(c.evidence()));
		assertWords(v, "This world is running slow", "Ticks take 640 ms here. Ping and frames are fine.",
			"Hop to a quieter world.");
	}

	@Test
	public void g2AnswersALowMemoryLimit()
	{
		final Verdict v = judged(g2());
		assertWords(v, "Memory limit is set too low", "The client may use only 512 MB. Default is 768.",
			"Remove the Java memory limit.");
	}

	@Test
	public void v2AnswersASteadyTrace()
	{
		final Case c = v2();
		final Verdict v = c.verdict();
		assertEquals(Cause.ALL_CLEAR, v.cause);
		assertEquals(Confidence.SURE, v.confidence);
		assertEquals(Level.OK, v.level);
		assertWords(v, "Smooth", "No lag this session.", "");
		assertNull("no condition scores on a steady trace", Rules.bestCondition(c.evidence()));
		assertNull(new VerdictEngine().conditionNow(c.s, c.nowSec, c.settings));
		assertEquals(-1, v.eventId);
		assertEquals(0, v.durationS);
	}

	// ------------------------------------------------------------------ what every case is held to

	/**
	 * The case's verdict, held to what contract section 7 asks of each trace: its own cause, its FULL ceiling, the
	 * level of its kind, what its kind carries (3.6), and no other rule within the margin.
	 */
	private static Verdict judged(Case c)
	{
		final Rule own = c.rule();
		final Verdict v = c.verdict();
		assertNotNull(c.id, v);
		assertEquals(c.id + " cause", own.cause, v.cause);
		assertEquals(c.id + " answers at its full ceiling", own.ceiling, v.confidence);
		assertNull(v.alsoA);
		assertNull(v.alsoB);

		final Evidence e = c.evidence();
		final int score = own.score(e);
		assertTrue(c.id + " scores", score >= own.base);
		for (Rule other : Rules.ALL)
		{
			if (other == own || other.kind != own.kind)
			{
				continue;
			}
			assertTrue(c.id + ": " + other.id + " at " + other.score(e) + " is within the margin of " + score,
				score - other.score(e) >= Thresholds.SCORE_MARGIN);
		}

		if (c.event != null)
		{
			assertEquals("an event is BAD, and at most WARN when it can't tell (the first live look)",
				v.confidence == Confidence.CANT_TELL ? Level.WARN : Level.BAD, v.level);
			assertEquals(c.event.id, v.eventId);
			assertEquals(c.event.startWallMs, v.whenWallMs);
			assertEquals(c.event.lengthS(), v.durationS);
			assertEquals(416, v.world);
		}
		else
		{
			assertEquals(Level.WARN, v.level);
			assertEquals(-1, v.eventId);
			assertEquals(0, v.durationS);
			assertEquals(416, v.world);
			assertTrue("a condition is dated", v.whenWallMs > 0);
			final Verdict now = new VerdictEngine().conditionNow(c.s, c.nowSec, c.settings);
			assertNotNull(now);
			assertEquals("the card shows the condition that wins now", own.cause, now.cause);
		}
		return v;
	}

	private static void assertWords(Verdict v, String headline, String proof, String fix)
	{
		assertEquals(headline, v.headline);
		assertEquals(proof, v.proof);
		assertEquals(fix, v.fix);
	}
}
