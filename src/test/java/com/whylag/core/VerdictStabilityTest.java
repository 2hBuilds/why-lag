package com.whylag.core;

import org.junit.Test;
import static com.whylag.core.VerdictCauseTest.USUAL;
import static com.whylag.core.VerdictCauseTest.event;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

/**
 * What the card shows from second to second (contract 6.5): no flicker, the holds, the three states, the counting
 * texts, and what each kind of verdict carries (3.6). "Now" at the end of n seconds is {@code nowSec = n} (3.5).
 */
public class VerdictStabilityTest
{
	private static Verdict step(VerdictEngine engine, Session s, SettingsView settings, int nowSec)
	{
		return engine.current(s, nowSec, s.wallMsOf(nowSec), settings);
	}

	/** A closed one-second event at {@code sec} of a stall trace, judged, ready for the log. */
	private static LagEvent stallAt(Session s, SettingsView settings, long id, int sec)
	{
		return lagOver(s, settings, id, sec, sec);
	}

	/**
	 * A closed event over {@code from .. to}, judged, ready for the log. A span of several seconds tells the event's
	 * start from its end, which a one-second event cannot.
	 */
	private static LagEvent lagOver(Session s, SettingsView settings, long id, int from, int to)
	{
		final LagEvent e = event(s, id, from, to, Trigger.FRAME_GAP);
		return e.withVerdict(new VerdictEngine().judgeEvent(s, e, settings));
	}

	private static Trace stallTrace(int seconds, int... stallSeconds)
	{
		final Trace t = Trace.steady(seconds).usual(USUAL);
		for (int sec : stallSeconds)
		{
			t.frameGap(sec, 600, 480).busy(sec, 700);
		}
		return t;
	}

	/**
	 * Ten minutes of a frame rate that hovers on the FPS_WARN line: 25 slow seconds, 5 fast ones, over and over. The
	 * slow-drawing condition comes and goes every 30 s; without the hold the card would drop it 6 s after showing it.
	 */
	@Test
	public void aTraceHoveringOnALineChangesTheCardAtMostOncePerTenSeconds()
	{
		final Trace t = Trace.steady(600).usual(USUAL);
		for (int from = 0; from < 600; from += 30)
		{
			t.fps(from, from + 24, 38);
		}
		final Session s = t.build();
		final VerdictEngine engine = new VerdictEngine();
		Verdict before = null;
		int lastChange = -1;
		int changes = 0;
		int causeChanges = 0;
		for (int now = 1; now <= 600; now++)
		{
			final Verdict v = step(engine, s, t.settings(), now);
			if (before != null && v != before)
			{
				assertTrue("changed at " + lastChange + " and again at " + now,
					lastChange < 0 || now - lastChange >= Thresholds.VERDICT_HOLD_S);
				lastChange = now;
				changes++;
				causeChanges += v.cause != before.cause ? 1 : 0;
			}
			before = v;
		}
		assertTrue("the trace must make the card move, or this test proves nothing: " + changes, changes >= 30);
		assertTrue("slow drawing came and went: " + causeChanges, causeChanges >= 30);
		assertTrue("at most once per VERDICT_HOLD_S", changes <= 600 / Thresholds.VERDICT_HOLD_S);
	}

	/**
	 * Rule 3: an event's verdict stays EVENT_SHOW_S (10 s since 2026-09-29) after the event's END, and rule 5's hold
	 * decides when the card may change. The lag lasts fourteen seconds, 100 to 113, so its start and its end tell
	 * apart. The card first names it at 119, when the detector's close is in the log; the show ended at 123, but the
	 * card stood only since 119 and changes once it has stood VERDICT_HOLD_S: the first V2 is at 129 and reads
	 * "No lag for 10 s.", counts from the END and carries the event's START (3.6).
	 */
	@Test
	public void anEventShowsForTenSecondsAndTheHoldAllowsTheChange()
	{
		final int start = 100;
		final int end = 113;
		final Trace t = stallTrace(400, start, end);
		final Session s = t.build();
		final LagEvent lag = lagOver(s, t.settings(), 0, start, end);
		assertEquals(14, lag.lengthS());
		final int closedAt = end + Thresholds.EVENT_QUIET_S + 1;
		final int firstV2 = Math.max(end + Thresholds.EVENT_SHOW_S, closedAt + Thresholds.VERDICT_HOLD_S);
		final VerdictEngine engine = new VerdictEngine();
		int lastShown = -1;
		int firstClearAt = -1;
		Verdict firstClear = null;
		for (int now = 1; now <= 400; now++)
		{
			if (now == closedAt)
			{
				// The detector closes it EVENT_QUIET_S quiet seconds after its last trigger second.
				s.events.add(lag);
			}
			final Verdict v = step(engine, s, t.settings(), now);
			if (now < closedAt)
			{
				assertEquals("at " + now, Cause.ALL_CLEAR, v.cause);
				assertEquals("No lag this session.", v.proof);
			}
			else if (now < firstV2)
			{
				assertSame("the event's own verdict, at " + now, lag.verdict, v);
				lastShown = now;
			}
			else
			{
				assertEquals("at " + now, Cause.ALL_CLEAR, v.cause);
				assertEquals("counted from the END, at " + now, allClearWords(now - end), v.proof);
				assertEquals("the START of the newest event, at " + now, s.wallMsOf(start), v.whenWallMs);
				if (firstClear == null)
				{
					firstClear = v;
					firstClearAt = now;
				}
			}
		}
		assertEquals("the last second it shows: the hold, not the show, keeps it", 128, lastShown);
		assertEquals("the first V2 is its end + 16", 129, firstClearAt);
		assertEquals("No lag for 10 s.", firstClear.proof);
		assertEquals(lag.startWallMs, firstClear.whenWallMs);
		assertNotEquals("not the end's clock", s.wallMsOf(end), firstClear.whenWallMs);
		assertEquals(416, firstClear.world);
		assertEquals(-1, firstClear.eventId);
		assertEquals(0, firstClear.durationS);
	}

	/**
	 * The card is Smooth 15 s after the last bad tick (addendum D, the user 2026-09-29): a lag whose last trigger
	 * second is T closes at T + EVENT_QUIET_S, the card names it from then through T + 14, and at T + 15 - the show
	 * (EVENT_SHOW_S 10) has ended and the hold (VERDICT_HOLD_S 10) allows the change - it reads ALL_CLEAR "No lag for
	 * 10 s.". The text then steps every ten seconds, "20 s." at T + 25, and counts minutes from "1 min." at T + 65;
	 * between steps it is the SAME object.
	 *
	 * <p>Choice: the plan's timeline counts from the last bad tick with the close at T + 5; the event is put in the
	 * log at that step. (The older tests here add it one step later, at end + EVENT_QUIET_S + 1, the detector's
	 * close as the sampler sees it, and read V2 one step later too.)
	 */
	@Test
	public void theCardIsSmoothFifteenSecondsAfterTheLastBadTick()
	{
		final int last = 100;
		final Trace t = stallTrace(300, 97, last);
		final Session s = t.build();
		final LagEvent lag = lagOver(s, t.settings(), 0, 97, last);
		assertEquals("the last bad tick is T", last, lag.endSec);
		final VerdictEngine engine = new VerdictEngine();
		Verdict smooth = null;
		Verdict before = null;
		Verdict minute = null;
		for (int now = 1; now <= 200; now++)
		{
			if (now == last + Thresholds.EVENT_QUIET_S)
			{
				s.events.add(lag);
			}
			final Verdict v = step(engine, s, t.settings(), now);
			final String at = "at T + " + (now - last);
			if (now < last + Thresholds.EVENT_QUIET_S)
			{
				assertEquals("nothing to name yet, " + at, "No lag this session.", v.proof);
			}
			else if (now <= last + 14)
			{
				assertSame("the card names the lag, " + at, lag.verdict, v);
				assertNotEquals(Cause.ALL_CLEAR, v.cause);
			}
			else
			{
				assertEquals("Smooth, " + at, Cause.ALL_CLEAR, v.cause);
				assertEquals("Smooth", v.headline);
				assertEquals(at, allClearWords(now - last), v.proof);
				if (now == last + 15)
				{
					assertEquals("No lag for 10 s.", v.proof);
					assertSame("the lag was on the card the second before", lag.verdict, before);
					smooth = v;
				}
				else if (now < last + 20)
				{
					assertSame("the same object between steps, " + at, smooth, v);
				}
				if (now == last + 25)
				{
					assertEquals("No lag for 20 s.", v.proof);
				}
				if (now == last + 65)
				{
					assertEquals("No lag for 1 min.", v.proof);
					minute = v;
				}
				else if (now > last + 65 && now < last + 120)
				{
					assertSame("the same object between steps, " + at, minute, v);
				}
			}
			before = v;
		}
		assertNotNull(smooth);
		assertNotNull(minute);
	}

	/** The words of V2's counting text, worked out here: steps of ten seconds under a minute, then whole minutes. */
	private static String allClearWords(long sinceEndS)
	{
		return sinceEndS < 60 ? "No lag for " + Math.max(10, sinceEndS / 10 * 10) + " s."
			: "No lag for " + sinceEndS / 60 + " min.";
	}

	/** Rule 2: inside the hold an event waits, and the newest waiting event wins. */
	@Test
	public void anEventWaitsOutTheHoldAndTheNewestWaitingOneWins()
	{
		final Trace t = stallTrace(400, 100, 108, 110);
		final Session s = t.build();
		final LagEvent first = stallAt(s, t.settings(), 0, 100);
		final LagEvent second = event(s, 1, 108, 108, Trigger.FRAME_GAP);
		final LagEvent third = stallAt(s, t.settings(), 2, 110);
		final VerdictEngine engine = new VerdictEngine();
		for (int now = 1; now <= 130; now++)
		{
			if (now == 106)
			{
				s.events.add(first);
			}
			if (now == 110)
			{
				// It came with no verdict: the judge makes one, once.
				s.events.add(second);
			}
			if (now == 114)
			{
				s.events.add(third);
			}
			final Verdict v = step(engine, s, t.settings(), now);
			if (now >= 106 && now <= 115)
			{
				assertSame("the card changed at 106 and holds, at " + now, first.verdict, v);
			}
			if (now >= 116 && now <= 125)
			{
				assertSame("the second event never showed; the newest did, at " + now, third.verdict, v);
			}
			if (now >= 126)
			{
				// The third one's show ended at 120 (110 + EVENT_SHOW_S), but the card changed at 116: the hold
				// lets it go at 126.
				assertEquals("at " + now, Cause.ALL_CLEAR, v.cause);
			}
		}

		// Alone, the event that came without a verdict is judged by the card, and the object is kept.
		final Session alone = t.build();
		final VerdictEngine other = new VerdictEngine();
		alone.events.add(event(alone, 1, 108, 108, Trigger.FRAME_GAP));
		final Verdict v = step(other, alone, t.settings(), 114);
		assertEquals(Cause.CLIENT_BUSY, v.cause);
		assertEquals(1, v.eventId);
		assertSame(v, step(other, alone, t.settings(), 115));
	}

	@Test
	public void currentReturnsTheSameObject()
	{
		final Trace t = Trace.steady(200).usual(USUAL);
		final Session s = t.build();
		final VerdictEngine engine = new VerdictEngine();
		final Verdict first = step(engine, s, t.settings(), 1);
		for (int now = 2; now <= 200; now++)
		{
			assertSame("nothing changed, at " + now, first, step(engine, s, t.settings(), now));
		}
		assertSame("asked twice in one second", first, step(engine, s, t.settings(), 200));

		// A condition that stays the same: G2 has no number that moves.
		final SettingsView low = VerdictCauseTest.heapLimit(512);
		final VerdictEngine lowEngine = new VerdictEngine();
		Verdict shown = null;
		for (int now = 1; now <= 200; now++)
		{
			final Verdict v = step(lowEngine, s, low, now);
			if (v.cause == Cause.HEAP_CAP_LOW)
			{
				if (shown != null)
				{
					assertSame("at " + now, shown, v);
				}
				shown = v;
			}
		}
		assertTrue(shown != null);

		// The states too.
		final Session loggedOut = Trace.steady(100).loginAt(50).build();
		final VerdictEngine stateEngine = new VerdictEngine();
		final Verdict out = step(stateEngine, loggedOut, t.settings(), 10);
		assertEquals(Answer.HEAD_NOT_LOGGED_IN, out.headline);
		assertSame(out, step(stateEngine, loggedOut, t.settings(), 11));
	}

	@Test
	public void countingTextsStep()
	{
		// No warm-up since the first live look (WARMUP_S 0, 2026-09-29): no "Ready in" counting after a login, the
		// card is V2 from the first step, and V2's own counting text is the only one that steps.
		final Trace t = Trace.steady(200).usual(USUAL).loginAt(0);
		final Session s = t.build();
		final VerdictEngine engine = new VerdictEngine();
		Verdict before = null;
		for (int now = 1; now <= 60; now++)
		{
			final Verdict v = step(engine, s, t.settings(), now);
			assertNotEquals("at " + now, Answer.HEAD_MEASURING, v.headline);
			assertEquals("at " + now, Cause.ALL_CLEAR, v.cause);
			if (before != null)
			{
				assertSame("nothing steps, at " + now, before, v);
			}
			before = v;
		}

		// V2: ten-second steps under a minute, then whole minutes, from the newest event's END. The lag lasts 100 to
		// 113, so text counted from its start would step 13 s early. The card is V2 from 129 (see
		// anEventShowsForTenSecondsAndTheHoldAllowsTheChange), "No lag for 10 s." there.
		final Trace lag = stallTrace(500, 100, 113);
		final Session ls = lag.build();
		final LagEvent stall = lagOver(ls, lag.settings(), 0, 100, 113);
		final VerdictEngine card = new VerdictEngine();
		before = null;
		int textSteps = 0;
		for (int now = 1; now <= 500; now++)
		{
			if (now == 119)
			{
				ls.events.add(stall);
			}
			final Verdict v = step(card, ls, lag.settings(), now);
			if (now >= 129)
			{
				assertEquals(Cause.ALL_CLEAR, v.cause);
				assertEquals("at " + now, allClearWords(now - 113), v.proof);
				if (before.proof.equals(v.proof))
				{
					assertSame("between steps, at " + now, before, v);
				}
				else if (before.cause == Cause.ALL_CLEAR)
				{
					assertEquals("a step lands on a multiple of ten seconds from the END, at " + now, 0,
						(now - 113) % 10);
					textSteps++;
				}
			}
			before = v;
		}
		assertEquals("No lag for 6 min.", before.proof);
		assertEquals("20 to 50 s (four), 1 min, then 2 to 6 min (five)", 10, textSteps);
	}

	/** Rule 6: a step of the counting text alone does not restart the hold. */
	@Test
	public void aCountingStepDoesNotRestartTheHold()
	{
		final Trace t = stallTrace(400, 100, 277);
		final Session s = t.build();
		final LagEvent first = stallAt(s, t.settings(), 0, 100);
		final LagEvent second = stallAt(s, t.settings(), 1, 277);
		final VerdictEngine engine = new VerdictEngine();
		for (int now = 1; now <= 290; now++)
		{
			if (now == 106)
			{
				s.events.add(first);
			}
			if (now == 283)
			{
				s.events.add(second);
			}
			final Verdict v = step(engine, s, t.settings(), now);
			if (now == 279)
			{
				assertEquals("No lag for 2 min.", v.proof);
			}
			if (now == 280)
			{
				assertEquals("the text stepped three seconds before the event closed", "No lag for 3 min.", v.proof);
			}
			if (now >= 283)
			{
				// The card last CHANGED at 116, when the first event gave way to V2; the counting steps at 280 and
				// after (the text steps every ten seconds) are not changes.
				assertSame("at " + now, second.verdict, v);
			}
		}
	}

	/** Green until proven otherwise (WARMUP_S 0 since the first live look): "Smooth" from the first step in game. */
	@Test
	public void noWarmUpAfterTheLogin()
	{
		final Trace t = Trace.steady(200).usual(USUAL).loginAt(30);
		final Session s = t.build();
		final Verdict at31 = step(new VerdictEngine(), s, t.settings(), 31);
		assertEquals("Smooth", at31.headline);
		assertEquals(Cause.ALL_CLEAR, at31.cause);
		final Verdict at90 = step(new VerdictEngine(), s, t.settings(), 90);
		assertNotEquals(Cause.WARMING_UP, at90.cause);
		assertEquals(Cause.ALL_CLEAR, at90.cause);

		final VerdictEngine engine = new VerdictEngine();
		for (int now = 1; now <= 200; now++)
		{
			final Verdict v = step(engine, s, t.settings(), now);
			// Second 29 is the last of the login screen; it is the newest second at nowSec 30.
			final String headline = now <= 30 ? Answer.HEAD_NOT_LOGGED_IN : "Smooth";
			assertEquals("at " + now, headline, v.headline);
		}
	}

	@Test
	public void aSteadyTraceIsNeverWarmingUp()
	{
		final Trace t = Trace.steady(10);
		final Session s = t.build();
		final VerdictEngine engine = new VerdictEngine();
		final Verdict at0 = step(engine, s, t.settings(), 0);
		assertEquals("no second is complete", Answer.HEAD_NOT_LOGGED_IN, at0.headline);
		assertEquals("Log in to start measuring.", at0.proof);
		for (int now = 1; now <= 10; now++)
		{
			final Verdict v = step(engine, s, t.settings(), now);
			assertEquals("at " + now, Cause.ALL_CLEAR, v.cause);
			assertEquals(Level.OK, v.level);
			assertEquals(Confidence.SURE, v.confidence);
		}
	}

	/** Ruling R1: the empty session at second 0, with settings nobody has read yet. */
	@Test
	public void anEmptySessionIsNotLoggedIn()
	{
		final Session s = new Session(1L, 0L, Os.OTHER, java.time.ZoneOffset.UTC);
		final SettingsView unknown = SettingsView.unknown(0, Os.OTHER, MemorySource.RUNTIME);
		final VerdictEngine engine = new VerdictEngine();
		final Verdict v = engine.current(s, 0, 0, unknown);
		assertEquals(Answer.HEAD_NOT_LOGGED_IN, v.headline);
		assertSame(Answer.NOT_LOGGED_IN, Answer.of(v));
		assertSame(v, engine.current(s, 5, 5000, unknown));
	}

	@Test
	public void framesStoppedIsWaitingForTheGame()
	{
		final Trace t = Trace.steady(100).usual(USUAL);
		final Session s = t.build();
		final VerdictEngine engine = new VerdictEngine();
		for (int now = 1; now <= 102; now++)
		{
			assertEquals("at " + now, Cause.ALL_CLEAR, step(engine, s, t.settings(), now).cause);
		}
		final Verdict v = step(engine, s, t.settings(), 103);
		assertEquals("Waiting for the game", v.headline);
		assertEquals("No frames are being drawn.", v.proof);
		assertEquals(Cause.WARMING_UP, v.cause);
	}

	@Test
	public void statesAreEnteredAndLeftAtOnce()
	{
		final Trace t = Trace.steady(200).usual(USUAL).hop(100, 302);
		final Session s = t.build();
		final VerdictEngine engine = new VerdictEngine();
		for (int now = 1; now <= 200; now++)
		{
			final Verdict v = step(engine, s, t.settings(), now);
			if (now == 101)
			{
				assertEquals("the hop's second is not in-game", Answer.HEAD_NOT_LOGGED_IN, v.headline);
				assertEquals(Level.NO_DATA, v.level);
			}
			else
			{
				assertEquals("no hold either way, at " + now, Cause.ALL_CLEAR, v.cause);
			}
		}

		// Inside a hold: the card changed to an event at 96, inside its show (EVENT_SHOW_S 10: it ends at 100), and
		// the state still comes and goes at once. Leaving the state is a change, so the event is held from 99.
		final Trace lag = stallTrace(200, 90).hop(97, 302);
		final Session ls = lag.build();
		final LagEvent stall = stallAt(ls, lag.settings(), 0, 90);
		final VerdictEngine held = new VerdictEngine();
		for (int now = 1; now <= 105; now++)
		{
			if (now == 96)
			{
				ls.events.add(stall);
			}
			final Verdict v = step(held, ls, lag.settings(), now);
			if (now >= 96 && now != 98)
			{
				assertSame("at " + now, stall.verdict, v);
			}
			if (now == 98)
			{
				assertEquals(Answer.HEAD_NOT_LOGGED_IN, v.headline);
			}
		}

		// A frame stop is ENTERED at once (its leaving cannot be built with a Trace, 3.12).
		final Trace stop = Trace.steady(100).usual(USUAL);
		final Session ss = stop.build();
		final VerdictEngine stopped = new VerdictEngine();
		for (int now = 93; now <= 102; now++)
		{
			assertEquals(Cause.ALL_CLEAR, step(stopped, ss, stop.settings(), now).cause);
		}
		assertEquals("the card changed at 93, nine seconds ago", Answer.HEAD_WAITING,
			step(stopped, ss, stop.settings(), 103).headline);
	}

	/**
	 * The seconds of a hop or a lost connection that are not in-game show "Not logged in" (rule 1) and neither follow
	 * nor end a condition's streak: a condition that still wins shows again at once when the state is left, dated
	 * from when it began winning, so neither the card nor the badge gives a false all-clear after a hop. F1 too: its
	 * capped run skips the hop's masked seconds, so it is still held.
	 */
	@Test
	public void aHopDoesNotRestartAConditionsStreak()
	{
		final SettingsView low = VerdictCauseTest.heapLimit(512);
		for (Trace t : new Trace[] {Trace.steady(200).usual(USUAL).hop(100, 302),
			Trace.steady(200).usual(USUAL).disconnect(100)})
		{
			final Session s = t.build();
			final VerdictEngine engine = new VerdictEngine();
			Verdict back = null;
			for (int now = 1; now <= 200; now++)
			{
				final Verdict v = step(engine, s, low, now);
				if (now <= Thresholds.CONDITION_HOLD_S)
				{
					assertEquals("at " + now, Cause.ALL_CLEAR, v.cause);
				}
				else if (now == 101)
				{
					assertEquals("the one second that is not in-game", Answer.HEAD_NOT_LOGGED_IN, v.headline);
				}
				else
				{
					assertEquals("at " + now, Cause.HEAP_CAP_LOW, v.cause);
					assertEquals("dated from when it began winning, at " + now, s.wallMsOf(0), v.whenWallMs);
					if (now == 102)
					{
						back = v;
						assertEquals("the newest second's world", s.seconds.world(101), v.world);
					}
					else if (now > 102)
					{
						assertSame("nothing changed since it came back, at " + now, back, v);
					}
				}
			}
		}

		final Trace capped = Trace.steady(200).usual(USUAL).fps(50, 199, 30)
			.settings(VerdictCauseTest.fpsControl(30)).hop(100, 302);
		final Session cs = capped.build();
		final VerdictEngine engine = new VerdictEngine();
		for (int now = 1; now <= 200; now++)
		{
			final Verdict v = step(engine, cs, capped.settings(), now);
			// Ten capped seconds, 50 to 59, make the run: F1 begins winning at 60 and shows at 70.
			if (now == 101)
			{
				assertEquals(Answer.HEAD_NOT_LOGGED_IN, v.headline);
			}
			else if (now >= 60 + Thresholds.CONDITION_HOLD_S)
			{
				assertEquals("at " + now, Cause.FRAME_CAP, v.cause);
				assertEquals("at " + now, cs.wallMsOf(59), v.whenWallMs);
			}
			else
			{
				assertEquals("at " + now, Cause.ALL_CLEAR, v.cause);
			}
		}
	}

	/**
	 * A new login forgets a condition's streak. The same lost connection as above, but the reconnect failed and the
	 * player logged in again from the login screen: the streak starts again (no warm-up since the first live look),
	 * the condition shows CONDITION_HOLD_S steps later, and it is dated from the new
	 * stretch. A Trace cannot log out in its middle, so the test moves the login as {@code LagEngine.gameState} would
	 * (3.5).
	 */
	@Test
	public void aNewLoginForgetsAConditionsStreak()
	{
		final SettingsView low = VerdictCauseTest.heapLimit(512);
		final Trace t = Trace.steady(300).usual(USUAL).disconnect(150);
		final Session s = t.build();
		final VerdictEngine engine = new VerdictEngine();
		for (int now = 1; now <= 150; now++)
		{
			step(engine, s, low, now);
		}
		final Verdict before = step(engine, s, low, 150);
		assertEquals(Cause.HEAP_CAP_LOW, before.cause);
		assertEquals(s.wallMsOf(0), before.whenWallMs);
		assertEquals(Answer.HEAD_NOT_LOGGED_IN, step(engine, s, low, 151).headline);

		s.loggedInSince(151);
		final int ready = 152 + Thresholds.WARMUP_S + Thresholds.CONDITION_HOLD_S;
		for (int now = 152; now < ready; now++)
		{
			final Verdict v = step(engine, s, low, now);
			assertNotEquals("no warm-up, at " + now, Answer.HEAD_MEASURING, v.headline);
			assertNotEquals("the streak starts again with the new login, at " + now, Cause.HEAP_CAP_LOW, v.cause);
		}
		final Verdict after = step(engine, s, low, ready);
		assertEquals(Cause.HEAP_CAP_LOW, after.cause);
		assertEquals("dated from the first step of the new login", s.wallMsOf(151), after.whenWallMs);
		assertNotEquals("not from the stretch before it", s.wallMsOf(0), after.whenWallMs);
	}

	@Test
	public void allClearNamesTheNewestEvent()
	{
		final Trace t = stallTrace(600, 100).hop(200, 302).frameGap(240, 600, 480).busy(240, 700)
			.frameGap(245, 600, 480).busy(245, 700);
		final Session s = t.build();
		final VerdictEngine none = new VerdictEngine();
		final Verdict quiet = step(none, s, t.settings(), 50);
		assertEquals(Cause.ALL_CLEAR, quiet.cause);
		assertEquals(0, quiet.whenWallMs);
		assertEquals(0, quiet.world);
		assertEquals(-1, quiet.eventId);
		assertEquals(0, quiet.durationS);

		final LagEvent first = stallAt(s, t.settings(), 0, 100);
		// The newest lasts 240 to 245: its start and its end tell apart.
		final LagEvent second = lagOver(s, t.settings(), 1, 240, 245);
		assertEquals(416, first.world);
		assertEquals(302, second.world);
		s.events.add(first);
		s.events.add(second);
		// An OPEN event is not "the newest closed event".
		s.events.add(new VerdictCauseTest.LagEventSpec(2, 590, 591, Trigger.TICK_OFF).open(true).on(s));

		final Verdict v = step(new VerdictEngine(), s, t.settings(), 600);
		assertEquals(Cause.ALL_CLEAR, v.cause);
		assertEquals("the newest closed event's START", second.startWallMs, v.whenWallMs);
		assertEquals(s.wallMsOf(240), v.whenWallMs);
		assertNotEquals("not its end", s.wallMsOf(245), v.whenWallMs);
		assertEquals(302, v.world);
		assertEquals(-1, v.eventId);
		assertEquals(0, v.durationS);
		assertEquals("(600 - 245) / 60 from its END; from its start it would be 6", "No lag for 5 min.", v.proof);
		assertEquals("", v.fix);
		assertNull(v.alsoA);
	}

	@Test
	public void aConditionIsDatedFromWhenItBeganWinning()
	{
		final Trace t = Trace.steady(200).usual(USUAL).fps(100, 199, 24);
		final Session s = t.build();
		final VerdictEngine engine = new VerdictEngine();
		int firstShown = -1;
		Verdict shown = null;
		for (int now = 1; now <= 200; now++)
		{
			final Verdict v = step(engine, s, t.settings(), now);
			if (v.cause == Cause.SLOW_DRAWING && firstShown < 0)
			{
				firstShown = now;
				shown = v;
			}
			if (v.cause == Cause.SLOW_DRAWING)
			{
				assertEquals("the date does not move while it wins, at " + now, s.wallMsOf(109), v.whenWallMs);
				assertEquals(416, v.world);
				assertEquals(-1, v.eventId);
				assertEquals(0, v.durationS);
			}
		}
		// Ten slow seconds, 100 to 109, make the run; F2 begins winning at the step whose newest second is 109.
		assertEquals("CONDITION_HOLD_S steps later", 120, firstShown);
		assertEquals(s.wallMsOf(109), shown.whenWallMs);
		assertNotEquals("not the second it began to show", s.wallMsOf(119), shown.whenWallMs);
	}

	@Test
	public void theStateHeadlinesAreAnswersConstants()
	{
		final Verdict[] states = states();
		assertSame(Answer.HEAD_NOT_LOGGED_IN, states[0].headline);
		assertSame(Answer.HEAD_MEASURING, states[1].headline);
		assertSame(Answer.HEAD_WAITING, states[2].headline);
		assertSame(Answer.NOT_LOGGED_IN, Answer.of(states[0]));
		assertSame(Answer.MEASURING, Answer.of(states[1]));
		assertSame(Answer.WAITING, Answer.of(states[2]));
		assertTrue("sameAs tells them apart by the headline", !states[0].sameAs(states[1])
			&& !states[1].sameAs(states[2]) && !states[0].sameAs(states[2]));
	}

	@Test
	public void theStatesOfTheCardCarryWarmingUp()
	{
		final String[] headlines = {"Not logged in", "Still measuring", "Waiting for the game"};
		final Verdict[] states = states();
		for (int i = 0; i < states.length; i++)
		{
			final Verdict v = states[i];
			assertEquals(headlines[i], v.headline);
			assertEquals(Cause.WARMING_UP, v.cause);
			assertEquals(Confidence.CANT_TELL, v.confidence);
			assertEquals(Level.NO_DATA, v.level);
			assertEquals("", v.fix);
			assertEquals("", v.ruledOut);
			assertEquals(0, v.whenWallMs);
			assertEquals(0, v.durationS);
			assertEquals(0, v.world);
			assertEquals(-1, v.eventId);
			assertNull(v.alsoA);
			assertNull(v.alsoB);
			assertTrue(v.proof, !v.proof.isEmpty() && v.proof.length() <= Words.PROOF_MAX);
		}
	}

	/**
	 * Not logged in, still measuring, waiting for the game: each from the trace that makes it. Since the first live
	 * look (WARMUP_S 0) "Still measuring" is left only for in-game seconds with no login seen, which the test makes by
	 * hand.
	 */
	private static Verdict[] states()
	{
		final SettingsView settings = Trace.steady(1).settings();
		final Session login = Trace.steady(100).loginAt(30).build();
		final Session steady = Trace.steady(100).build();
		final Session unseen = Trace.steady(100).build();
		unseen.loggedInSince(Session.NEVER);
		return new Verdict[] {
			step(new VerdictEngine(), login, settings, 20),
			step(new VerdictEngine(), unseen, settings, 50),
			step(new VerdictEngine(), steady, settings, 103),
		};
	}
}
