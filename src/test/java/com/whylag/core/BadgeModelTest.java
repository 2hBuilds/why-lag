package com.whylag.core;

import java.lang.management.ManagementFactory;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import org.junit.Assume;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

/**
 * The game badge's state machine and its chat line (contract P2.2, P2.6; lot L10), without a client: every state of
 * picture 22, the hold, the tooltip's words, an event's numbers, one chat line per lag and the chat gap. Every verdict
 * and every event is built by constructor (L10 cannot run the detector or the judge). The picture's event runs from
 * 21:47:30 to 21:47:43, session seconds 30 to 43 (fourteen seconds) on world 416: worst tick 1,240 ms (mean 952),
 * ping 41 ms (highest 310), worst frame 480 ms at 50 fps, a 340 ms pause with 742 MB used.
 */
public class BadgeModelTest
{
	/** 2026-09-28 21:47:00 UTC: session second 0 of the picture's story (the model itself reads no clock). */
	private static final long WALL0 = 1_790_632_020_000L;
	/** The picture's event: its first second and its last trigger second, 21:47:30 and 21:47:43. */
	private static final long START = 30;
	private static final long END = 43;
	/** A second long after any lag, for the quiet states. */
	private static final long LATER = 500;
	/** Every event's MEAN tick: it is never printed, the badge prints the worst. */
	private static final int MEAN_TICK = 952;

	private static final String FOUR_MIN = "No lag for 4 min.";
	private static final String WORLD_NUMBERS = "Ticks 1,240 ms, ping 41 ms";
	private static final String NOT_SURE_NUMBERS = "Ticks 1,240 ms, worst frame 480 ms";
	private static final String W1_LINE = "[Why Lag] World lag - not you (14 s). Ticks 1,240 ms, ping 41 ms.";

	// ---------------------------------------------------------------- hidden

	@Test
	public void hiddenBeforeTheFirstUpdate()
	{
		final BadgeModel m = new BadgeModel();
		assertSame(BadgeView.HIDDEN, m.view());
		assertSame("reading changes nothing", BadgeView.HIDDEN, m.view());
		assertNull("no chat line before the first update", m.takeChatLine());
	}

	@Test
	public void showOffIsHidden()
	{
		final BadgeModel m = new BadgeModel();
		final LagEvent open = new Ev().open(true).verdict(w1(0)).build();
		assertSame("a lag going on", BadgeView.HIDDEN,
			m.update(v2(FOUR_MIN), open, null, END, false, BadgeStyle.ICON, WhenSmooth.SHOW, true));
		assertSame("a condition", BadgeView.HIDDEN,
			m.update(f1(), null, null, LATER, false, BadgeStyle.ICON, WhenSmooth.SHOW, true));
		assertSame("smooth", BadgeView.HIDDEN,
			m.update(v2(FOUR_MIN), null, null, LATER, false, BadgeStyle.ICON, WhenSmooth.SHOW, true));
		assertSame("measuring", BadgeView.HIDDEN,
			m.update(measuring(), null, null, LATER, false, BadgeStyle.ICON, WhenSmooth.SHOW, true));
		assertSame(BadgeView.HIDDEN, m.view());
		assertTrue("the same lag with the setting on is shown",
			m.update(v2(FOUR_MIN), open, null, END, true, BadgeStyle.ICON, WhenSmooth.SHOW, true).visible);
	}

	@Test
	public void notLoggedInIsHidden()
	{
		final BadgeModel m = new BadgeModel();
		final LagEvent open = new Ev().open(true).verdict(w1(0)).build();
		final LagEvent closed = new Ev().verdict(w1(0)).build();
		assertSame("the login screen", BadgeView.HIDDEN, step(m, notLoggedIn(), null, null, LATER));
		assertSame("a lag open in a hop's seconds that are not in-game", BadgeView.HIDDEN,
			step(m, notLoggedIn(), open, null, END));
		assertSame("a lag closed a moment ago", BadgeView.HIDDEN, step(m, notLoggedIn(), null, closed, END + 2));
		assertTrue("the headline tells the states apart: measuring, of the same cause, is shown",
			step(m, measuring(), null, null, LATER).visible);
	}

	@Test
	public void aNullCardIsHidden()
	{
		final BadgeModel m = new BadgeModel();
		assertSame(BadgeView.HIDDEN, step(m, null, null, null, LATER));
		assertSame("even with a lag going on", BadgeView.HIDDEN,
			step(m, null, new Ev().open(true).verdict(w1(0)).build(), null, END));
		assertSame(BadgeView.HIDDEN, m.view());
	}

	/** Every hidden case of P2.2 answers the ONE hidden view, entered from a visible view, in each of the styles. */
	@Test
	public void aHiddenViewIsTheConstant()
	{
		final LagEvent open = new Ev().open(true).verdict(w1(0)).build();
		final LagEvent closed = new Ev().verdict(w1(0)).build();
		final long afterHold = END + Thresholds.BADGE_HOLD_S + 1;
		for (BadgeStyle s : BadgeStyle.values())
		{
			final BadgeModel m = new BadgeModel();
			assertHiddenAfterALag(m, s, "show off",
				() -> m.update(v2(FOUR_MIN), open, null, END, false, s, WhenSmooth.SHOW, true));
			assertHiddenAfterALag(m, s, "a null card",
				() -> m.update(null, open, null, END, true, s, WhenSmooth.SHOW, true));
			assertHiddenAfterALag(m, s, "not logged in",
				() -> m.update(notLoggedIn(), open, null, END, true, s, WhenSmooth.SHOW, true));
			assertHiddenAfterALag(m, s, "smooth under Hide",
				() -> m.update(v2(FOUR_MIN), null, null, LATER, true, s, WhenSmooth.HIDE, true));
			assertHiddenAfterALag(m, s, "a past lag after the hold, under Hide",
				() -> m.update(w1(0), null, closed, afterHold, true, s, WhenSmooth.HIDE, true));
			assertHiddenAfterALag(m, s, "measuring under Hide",
				() -> m.update(measuring(), null, null, LATER, true, s, WhenSmooth.HIDE, true));
			assertHiddenAfterALag(m, s, "waiting under Hide",
				() -> m.update(waiting(), null, null, LATER, true, s, WhenSmooth.HIDE, true));
			assertSame(s + ": hidden twice in a row", BadgeView.HIDDEN,
				m.update(null, null, null, LATER, true, s, WhenSmooth.SHOW, true));
			assertSame(s + ": from a new model", BadgeView.HIDDEN,
				new BadgeModel().update(v2(FOUR_MIN), open, null, END, false, s, WhenSmooth.SHOW, true));
		}
	}

	// ---------------------------------------------------------------- smooth and the ring

	@Test
	public void smoothIsTheGreenCircle()
	{
		final BadgeModel m = new BadgeModel();
		assertView("V2", step(m, v2(FOUR_MIN), null, null, LATER), BadgeStyle.ICON, Level.OK, Icon.NONE, false,
			"Smooth", "", "Smooth. No lag for 4 min.", "");
		assertView("V2 before the first lag", step(m, v2("No lag this session."), null, null, LATER),
			BadgeStyle.ICON, Level.OK, Icon.NONE, false, "Smooth", "", "Smooth. No lag this session.", "");
		assertView("the card still holding a past lag", step(m, w1(0), null, null, LATER), BadgeStyle.ICON, Level.OK,
			Icon.NONE, false, "Smooth", "", "Smooth.", "Last lag: World lag - not you");
	}

	@Test
	public void smoothIsHiddenWhenHideIsChosen()
	{
		final BadgeModel m = new BadgeModel();
		assertSame(BadgeView.HIDDEN,
			m.update(v2(FOUR_MIN), null, null, LATER, true, BadgeStyle.ICON, WhenSmooth.HIDE, true));
		final LagEvent closed = new Ev().verdict(w1(0)).build();
		assertSame("a past lag on the card, after the badge's hold", BadgeView.HIDDEN,
			m.update(w1(0), null, closed, END + Thresholds.BADGE_HOLD_S + 1, true, BadgeStyle.ICON, WhenSmooth.HIDE,
				true));
		assertTrue("Show brings the circle back", step(m, v2(FOUR_MIN), null, null, LATER).visible);
	}

	@Test
	public void measuringIsTheRing()
	{
		final BadgeModel m = new BadgeModel();
		assertView("measuring", step(m, measuring(), null, null, LATER), BadgeStyle.ICON, Level.NO_DATA, Icon.NONE,
			false, "Measuring", "", "Still measuring", "Ready in 40 s.");
	}

	@Test
	public void measuringIsHiddenWhenHideIsChosen()
	{
		final BadgeModel m = new BadgeModel();
		assertSame(BadgeView.HIDDEN,
			m.update(measuring(), null, null, LATER, true, BadgeStyle.ICON, WhenSmooth.HIDE, true));
		assertTrue("Show brings the ring back", step(m, measuring(), null, null, LATER).visible);
	}

	@Test
	public void waitingIsTheRing()
	{
		final BadgeModel m = new BadgeModel();
		assertView("waiting", step(m, waiting(), null, null, LATER), BadgeStyle.ICON, Level.NO_DATA, Icon.NONE, false,
			"Waiting", "", "Waiting for the game", "No frames are being drawn.");
		assertSame("hidden under Hide, as measuring is", BadgeView.HIDDEN,
			m.update(waiting(), null, null, LATER, true, BadgeStyle.ICON, WhenSmooth.HIDE, true));
	}

	// ---------------------------------------------------------------- a lag

	@Test
	public void anOpenEventShowsItsProvisionalAnswer()
	{
		final BadgeModel m = new BadgeModel();
		// the sixth second of the lag, judged W1 so far; the card still says what it said before the lag
		final LagEvent open = new Ev().span(START, START + 5).open(true).verdict(w1(0)).build();
		assertView("W1 while it happens", step(m, v2(FOUR_MIN), open, null, START + 5), BadgeStyle.ICON, Level.BAD,
			Icon.WORLD, false, "World lag", "Not you", "World lag - not you", WORLD_NUMBERS);
		assertView("over a condition on the card too", step(m, f1(), open, null, START + 5), BadgeStyle.ICON,
			Level.BAD, Icon.WORLD, false, "World lag", "Not you", "World lag - not you", WORLD_NUMBERS);
		// the level is the verdict's own, not a fixed BAD
		final Verdict warn = new Verdict(Cause.SLOW_WORLD, Confidence.HINT, Level.WARN, "This world is running slow",
			"p", "f", "", WALL0, 6, 416, 0, null, null);
		assertSame(Level.WARN, step(m, v2(FOUR_MIN), new Ev().span(START, START + 5).open(true).verdict(warn).build(),
			null, START + 5).level);
	}

	@Test
	public void anOpenEventWithoutAVerdictIsNotSure()
	{
		final BadgeModel m = new BadgeModel();
		final LagEvent open = new Ev().span(START, START).open(true).build();
		assertView("not judged yet", step(m, v2(FOUR_MIN), open, null, START), BadgeStyle.ICON, Level.BAD,
			Icon.UNKNOWN, false, "Lag", "Can't tell why", "Lag - can't tell why", NOT_SURE_NUMBERS);
	}

	@Test
	public void dimmedFromTheSecondQuietSecond()
	{
		final BadgeModel m = new BadgeModel();
		final LagEvent open = new Ev().open(true).verdict(w1(0)).build();
		assertFalse("q 0: going on", step(m, v2(FOUR_MIN), open, null, END).dimmed);
		assertFalse("q 1: going on", step(m, v2(FOUR_MIN), open, null, END + 1).dimmed);
		assertView("q 2: the second quiet second", step(m, v2(FOUR_MIN), open, null, END + 2), BadgeStyle.ICON,
			Level.BAD, Icon.WORLD, true, "World lag", "Not you", "World lag - not you", WORLD_NUMBERS);
		for (long q = 3; q <= Thresholds.BADGE_HOLD_S; q++)
		{
			assertTrue("q " + q, step(m, v2(FOUR_MIN), open, null, END + q).dimmed);
		}
	}

	@Test
	public void aClosedEventIsDimmedForTheHold()
	{
		final BadgeModel m = new BadgeModel();
		final LagEvent closed = new Ev().verdict(w1(0)).build();
		// the card names the closed event for EVENT_SHOW_S (10 s since 2026-09-29; 6.5, rule 3) and the badge is
		// dimmed for its own, longer BADGE_HOLD_S; this model is handed the verdict, so the two clocks are separate
		for (long q = 0; q <= Thresholds.BADGE_HOLD_S; q++)
		{
			assertView("q " + q, step(m, w1(0), null, closed, END + q), BadgeStyle.ICON, Level.BAD, Icon.WORLD, true,
				"World lag", "Not you", "World lag - not you", WORLD_NUMBERS);
		}
		assertView("q " + (Thresholds.BADGE_HOLD_S + 1),
			step(m, w1(0), null, closed, END + Thresholds.BADGE_HOLD_S + 1), BadgeStyle.ICON, Level.OK, Icon.NONE,
			false, "Smooth", "", "Smooth.", "Last lag: World lag - not you");
	}

	@Test
	public void afterTheHoldThePastLagOnTheCardIsSmooth()
	{
		final BadgeModel m = new BadgeModel();
		final LagEvent closed = new Ev().verdict(w1(0)).build();
		final long later = END + Thresholds.BADGE_HOLD_S + 30;
		assertView("event 0 on the card", step(m, w1(0), null, closed, later), BadgeStyle.ICON, Level.OK, Icon.NONE,
			false, "Smooth", "", "Smooth.", "Last lag: World lag - not you");
		assertView("no closed event handed in", step(m, w1(0), null, null, later), BadgeStyle.ICON, Level.OK,
			Icon.NONE, false, "Smooth", "", "Smooth.", "Last lag: World lag - not you");
		assertEquals("Last lag: Packet loss - line or world", step(m, judged(Cause.UPLOAD_LOSS, 4), null, null,
			later).tip2);
		assertEquals("Last lag: Lag - can't tell why", step(m, x(5), null, null, later).tip2);
		assertSame("a verdict that is no event's (-1) is not a past lag", Level.WARN,
			step(m, f1(), null, null, later).level);
	}

	@Test
	public void aLagIsShownWhenHideIsChosen()
	{
		final BadgeModel m = new BadgeModel();
		final LagEvent open = new Ev().open(true).verdict(w1(0)).build();
		final LagEvent closed = new Ev().verdict(w1(0)).build();
		assertView("going on, the card smooth",
			m.update(v2(FOUR_MIN), open, null, END, true, BadgeStyle.ICON, WhenSmooth.HIDE, true), BadgeStyle.ICON,
			Level.BAD, Icon.WORLD, false, "World lag", "Not you", "World lag - not you", WORLD_NUMBERS);
		assertView("over, in its hold, the card holding it",
			m.update(w1(0), null, closed, END + 5, true, BadgeStyle.ICON, WhenSmooth.HIDE, true), BadgeStyle.ICON,
			Level.BAD, Icon.WORLD, true, "World lag", "Not you", "World lag - not you", WORLD_NUMBERS);
		assertView("going on while the card still measures",
			m.update(measuring(), open, null, END, true, BadgeStyle.ICON, WhenSmooth.HIDE, true), BadgeStyle.ICON,
			Level.BAD, Icon.WORLD, false, "World lag", "Not you", "World lag - not you", WORLD_NUMBERS);
	}

	@Test
	public void aConditionIsAmberAndIsShownWhenHideIsChosen()
	{
		final BadgeModel m = new BadgeModel();
		final Verdict w1c = condition(Cause.SLOW_WORLD, "Ticks take 620 ms here. Ping and frames are fine.");
		for (WhenSmooth w : WhenSmooth.values())
		{
			assertView("F1, " + w, m.update(f1(), null, null, LATER, true, BadgeStyle.ICON, w, true), BadgeStyle.ICON,
				Level.WARN, Icon.PC, false, "FPS capped", "Your setting", "FPS capped - your setting",
				"Set by FPS Control. Not lag.");
			assertView("W1c, " + w, m.update(w1c, null, null, LATER, true, BadgeStyle.ICON, w, true), BadgeStyle.ICON,
				Level.WARN, Icon.WORLD, false, "World lag", "Not you", "World lag - not you",
				"Ticks take 620 ms here. Ping and frames are fine.");
		}
	}

	@Test
	public void theOpenEventWinsOverTheLastClosed()
	{
		final BadgeModel m = new BadgeModel();
		final long now = END + 7;
		final LagEvent closed = new Ev().id(0).verdict(w1(0)).build();
		assertSame("alone, the closed one shows: its hold still runs", Icon.WORLD,
			step(m, w1(0), null, closed, now).icon);
		final LagEvent open = new Ev().id(1).span(now, now).open(true).verdict(x(1)).worstFrame(170).build();
		assertView("a new lag beside it", step(m, w1(0), open, closed, now), BadgeStyle.ICON, Level.BAD, Icon.UNKNOWN,
			false, "Lag", "Can't tell why", "Lag - can't tell why", "Ticks 1,240 ms, worst frame 170 ms");
	}

	/** An event whose last trigger second lies AFTER the newest second handed in: q is 0, never a count back. */
	@Test
	public void aNegativeQuietTimeCountsAsZero()
	{
		final BadgeModel m = new BadgeModel();
		final LagEvent open = new Ev().open(true).verdict(w1(0)).build();
		final LagEvent closed = new Ev().verdict(w1(0)).build();
		for (long ahead : new long[] {1, Thresholds.BADGE_HOLD_S + 5, 1000})
		{
			assertView("open, " + ahead + " s ahead", step(m, v2(FOUR_MIN), open, null, END - ahead),
				BadgeStyle.ICON, Level.BAD, Icon.WORLD, false, "World lag", "Not you", "World lag - not you",
				WORLD_NUMBERS);
			assertView("closed, " + ahead + " s ahead", step(m, w1(0), null, closed, END - ahead), BadgeStyle.ICON,
				Level.BAD, Icon.WORLD, true, "World lag", "Not you", "World lag - not you", WORLD_NUMBERS);
		}
	}

	// ---------------------------------------------------------------- styles and picture 22

	@Test
	public void theViewCarriesTheStyle()
	{
		final LagEvent open = new Ev().open(true).verdict(w1(0)).build();
		final LagEvent closed = new Ev().verdict(w1(0)).build();
		for (BadgeStyle s : BadgeStyle.values())
		{
			final BadgeModel m = new BadgeModel();
			final BadgeView smooth = m.update(v2(FOUR_MIN), null, null, LATER, true, s, WhenSmooth.SHOW, true);
			final BadgeView lag = m.update(v2(FOUR_MIN), open, null, END, true, s, WhenSmooth.SHOW, true);
			final BadgeView over = m.update(w1(0), null, closed, END + 5, true, s, WhenSmooth.SHOW, true);
			final BadgeView slow = m.update(f1(), null, null, LATER, true, s, WhenSmooth.SHOW, true);
			final BadgeView ring = m.update(measuring(), null, null, LATER, true, s, WhenSmooth.SHOW, true);
			for (BadgeView v : new BadgeView[] {smooth, lag, over, slow, ring})
			{
				assertTrue(s + ": visible", v.visible);
				assertSame(s + ": the style as given", s, v.style);
			}
			// whatever the style draws, the view carries both lines: the painter leaves out what it does not show
			assertEquals(s + ": line 1", "World lag", lag.line1);
			assertEquals(s + ": line 2", "Not you", lag.line2);
			assertEquals(s + ": line 1", "FPS capped", slow.line1);
			assertEquals(s + ": line 2", "Your setting", slow.line2);
		}
		final BadgeModel m = new BadgeModel();
		final BadgeView icon = m.update(v2(FOUR_MIN), open, null, END, true, BadgeStyle.ICON, WhenSmooth.SHOW, true);
		final BadgeView words = m.update(v2(FOUR_MIN), open, null, END, true, BadgeStyle.ICON_AND_WORDS,
			WhenSmooth.SHOW, true);
		assertNotSame("a new style alone is a new view", icon, words);
		assertSame(BadgeStyle.ICON_AND_WORDS, m.view().style);
	}

	/**
	 * The seven rows of picture 22, part A, in each style's column: level, icon and both lines of each, with "Memory
	 * stall" for the picture's "Memory pause" (contract 10.3). Row 4 is a frame rate LAG with F2's words (wave one's
	 * F2 is a condition, so the verdict is built at BAD by hand); row 5 is "slow, not lag", a condition with G1's
	 * words.
	 */
	@Test
	public void everyStateOfPictureTwentyTwo()
	{
		for (BadgeStyle s : BadgeStyle.values())
		{
			final BadgeModel m = new BadgeModel();
			assertRow(s, "1 Smooth", m.update(v2(FOUR_MIN), null, null, LATER, true, s, WhenSmooth.SHOW, true),
				Level.OK, Icon.NONE, "Smooth", "");
			assertRow(s, "2 World lag", m.update(v2(FOUR_MIN), openLag(w1(0)), null, END, true, s, WhenSmooth.SHOW,
				true), Level.BAD, Icon.WORLD, "World lag", "Not you");
			assertRow(s, "3 Connection lag", m.update(v2(FOUR_MIN), openLag(judged(Cause.PING_JUMPY, 1)), null, END,
				true, s, WhenSmooth.SHOW, true), Level.BAD, Icon.LINE, "Ping lag", "Your internet");
			assertRow(s, "4 Frame rate lag", m.update(v2(FOUR_MIN), openLag(judged(Cause.SLOW_DRAWING, 2)), null, END,
				true, s, WhenSmooth.SHOW, true), Level.BAD, Icon.PC, "Low FPS", "Your PC");
			assertRow(s, "5 Memory pause, now Memory stall", m.update(condition(Cause.GC_PAUSE, "p"), null, null,
				LATER, true, s, WhenSmooth.SHOW, true), Level.WARN, Icon.MEMORY, "Memory stall", "The client");
			assertRow(s, "6 Not sure", m.update(v2(FOUR_MIN), openLag(x(3)), null, END, true, s, WhenSmooth.SHOW,
				true), Level.BAD, Icon.UNKNOWN, "Lag", "Can't tell why");
			assertRow(s, "7 Still measuring", m.update(measuring(), null, null, LATER, true, s, WhenSmooth.SHOW,
				true), Level.NO_DATA, Icon.NONE, "Measuring", "");
		}
	}

	/** Picture 22, part D: the badge through the picture's event, second by second, and its one chat line. */
	@Test
	public void theStoryOfPictureTwentyTwoPartD()
	{
		final BadgeModel m = new BadgeModel();
		final Verdict before = v2("No lag this session.");
		final long closes = END + Thresholds.EVENT_QUIET_S;
		final Map<Long, BadgeView> at = new HashMap<>();
		final List<BadgeView> pictures = new ArrayList<>();
		final List<String> lines = new ArrayList<>();
		for (long clock = 20; clock <= 70; clock++)
		{
			// the step at clock second c reads the newest complete second, c - 1 (contract 3.5)
			final long lastSec = clock - 1;
			final boolean isClosed = lastSec >= closes;
			final LagEvent open = lastSec < START || isClosed ? null : new Ev().span(START, Math.min(lastSec, END))
				.open(true).verdict(lastSec < START + 3 ? x(0) : w1(0)).build();
			final LagEvent closed = isClosed ? new Ev().verdict(w1(0)).build() : null;
			final BadgeView v = step(m, isClosed ? w1(0) : before, open, closed, lastSec);
			at.put(clock, v);
			if (pictures.isEmpty() || pictures.get(pictures.size() - 1) != v)
			{
				pictures.add(v);
			}
			final String line = m.takeChatLine();
			if (line != null)
			{
				lines.add(clock + " " + line);
			}
		}
		assertView("21:47:28, rule 5", at.get(28L), BadgeStyle.ICON, Level.OK, Icon.NONE, false, "Smooth", "",
			"Smooth. No lag this session.", "");
		assertView("21:47:31, open, judged X so far", at.get(31L), BadgeStyle.ICON, Level.BAD, Icon.UNKNOWN, false,
			"Lag", "Can't tell why", "Lag - can't tell why", NOT_SURE_NUMBERS);
		assertView("21:47:36, open, judged W1", at.get(36L), BadgeStyle.ICON, Level.BAD, Icon.WORLD, false,
			"World lag", "Not you", "World lag - not you", WORLD_NUMBERS);
		assertView("21:47:46, open, q 2", at.get(46L), BadgeStyle.ICON, Level.BAD, Icon.WORLD, true, "World lag",
			"Not you", "World lag - not you", WORLD_NUMBERS);
		assertView("21:47:49, closed", at.get(49L), BadgeStyle.ICON, Level.BAD, Icon.WORLD, true, "World lag",
			"Not you", "World lag - not you", WORLD_NUMBERS);
		assertView("21:48:00, q 16", at.get(60L), BadgeStyle.ICON, Level.OK, Icon.NONE, false, "Smooth", "",
			"Smooth.", "Last lag: World lag - not you");
		assertEquals("five pictures, each one object: circle, question mark, globe, globe dimmed, circle", 5,
			pictures.size());
		assertEquals("one chat line, in the step that saw the event closed", 1, lines.size());
		assertEquals("49 " + W1_LINE, lines.get(0));
	}

	// ---------------------------------------------------------------- the same object (T19)

	@Test
	public void theSameObjectWhileNothingChanged()
	{
		final BadgeModel m = new BadgeModel();
		final BadgeView lag = step(m, v2(FOUR_MIN), new Ev().open(true).verdict(w1(0)).build(), null, END);
		assertSame("equal inputs, new objects: one view", lag,
			step(m, v2(FOUR_MIN), new Ev().open(true).verdict(w1(0)).build(), null, END));
		assertSame("q 1 still goes on: nothing it shows changed", lag,
			step(m, v2(FOUR_MIN), new Ev().open(true).verdict(w1(0)).build(), null, END + 1));
		assertSame("a card that the lag hides changed", lag,
			step(m, v2("No lag for 5 min."), new Ev().open(true).verdict(w1(0)).build(), null, END + 1));
		final BadgeView changed = step(m, v2(FOUR_MIN), new Ev().open(true).verdict(w1(0)).worstTick(1300).build(),
			null, END + 1);
		assertNotSame("a changed tip 2 is a new view", lag, changed);
		assertTrue("and only tip 2 changed", changed.sameAs(new BadgeView(true, BadgeStyle.ICON, Level.BAD, Icon.WORLD,
			false, "World lag", "Not you", "World lag - not you", "Ticks 1,300 ms, ping 41 ms")));
		assertSame("the new view is kept in turn", changed,
			step(m, v2(FOUR_MIN), new Ev().open(true).verdict(w1(0)).worstTick(1300).build(), null, END + 1));
		assertSame(changed, m.view());

		final BadgeView smooth = step(m, v2(FOUR_MIN), null, null, LATER);
		assertSame("smooth, a second later", smooth, step(m, v2(FOUR_MIN), null, null, LATER + 1));
		final BadgeView fiveMin = step(m, v2("No lag for 5 min."), null, null, LATER + 60);
		assertNotSame("the counting text is in tip 1", smooth, fiveMin);
		assertEquals("Smooth. No lag for 5 min.", fiveMin.tip1);
	}

	@Test
	public void viewIsWhatUpdateAnswered()
	{
		final BadgeModel m = new BadgeModel();
		final LagEvent closed = new Ev().verdict(w1(0)).build();
		assertAnswered(m, step(m, measuring(), null, null, 10));
		assertAnswered(m, step(m, v2("No lag this session."), null, null, 20));
		assertAnswered(m, step(m, v2(FOUR_MIN), new Ev().span(START, START).open(true).verdict(x(0)).build(), null,
			START));
		assertAnswered(m, step(m, v2(FOUR_MIN), new Ev().open(true).verdict(w1(0)).build(), null, END));
		assertAnswered(m, step(m, v2(FOUR_MIN), new Ev().open(true).verdict(w1(0)).build(), null, END + 2));
		assertAnswered(m, step(m, w1(0), null, closed, END + 5));
		assertAnswered(m, step(m, w1(0), null, closed, END + Thresholds.BADGE_HOLD_S + 1));
		assertAnswered(m, step(m, f1(), null, null, LATER));
		assertAnswered(m, m.update(f1(), null, null, LATER, false, BadgeStyle.ICON, WhenSmooth.SHOW, true));
		assertAnswered(m, step(m, notLoggedIn(), null, null, LATER + 1));
		assertAnswered(m, step(m, waiting(), null, null, LATER + 2));
	}

	// ---------------------------------------------------------------- the tooltip and the numbers

	/** Each row of P2.2's tooltip table. */
	@Test
	public void tooltipLines()
	{
		final BadgeModel m = new BadgeModel();
		assertTips("a lag", step(m, v2(FOUR_MIN), new Ev().open(true).verdict(w1(0)).build(), null, END),
			"World lag - not you", WORLD_NUMBERS);
		assertTips("a lag, over", step(m, w1(0), null, new Ev().verdict(w1(0)).build(), END + 3),
			"World lag - not you", WORLD_NUMBERS);
		assertTips("slow", step(m, f1(), null, null, LATER), "FPS capped - your setting",
			"Set by FPS Control. Not lag.");
		assertTips("smooth, the card V2", step(m, v2(FOUR_MIN), null, null, LATER), "Smooth. No lag for 4 min.", "");
		assertTips("smooth, the card holding a past lag", step(m, w1(0), null, null, LATER), "Smooth.",
			"Last lag: World lag - not you");
		assertTips("the ring", step(m, measuring(), null, null, LATER), "Still measuring", "Ready in 40 s.");
		assertTips("the ring, waiting", step(m, waiting(), null, null, LATER), "Waiting for the game",
			"No frames are being drawn.");
		assertTips("hidden", m.update(v2(FOUR_MIN), null, null, LATER, false, BadgeStyle.ICON, WhenSmooth.SHOW, true),
			"", "");
	}

	/** Each row of P2.2's table of numbers, in the tooltip and the chat line; the tick is the WORST, never the mean. */
	@Test
	public void numbersByIcon()
	{
		assertNumbers("W1", w1(0), Icon.WORLD, new Ev(), "Ticks 1,240 ms, ping 41 ms");
		for (Cause c : new Cause[] {Cause.PING_JUMPY, Cause.UPLOAD_LOSS, Cause.DISCONNECT})
		{
			assertNumbers(c.id(), judged(c, 0), Icon.LINE, new Ev(), "Ping 310 ms, ticks 1,240 ms");
		}
		for (Cause c : new Cause[] {Cause.CLIENT_BUSY, Cause.CLIENT_WAITING, Cause.MAP_LOAD})
		{
			assertNumbers(c.id(), judged(c, 0), Icon.PC, new Ev(), "Worst frame 480 ms, 50 fps");
		}
		assertNumbers("G1", judged(Cause.GC_PAUSE, 0), Icon.MEMORY, new Ev(), "Pause 340 ms, memory 742 MB");
		for (Cause c : new Cause[] {Cause.NOT_SURE, Cause.DELIVERY_GAP})
		{
			assertNumbers(c.id(), judged(c, 0), Icon.UNKNOWN, new Ev().worstFrame(170),
				"Ticks 1,240 ms, worst frame 170 ms");
		}
		assertNumbers("every number through Fmt.thousands, whole", w1(0), Icon.WORLD,
			new Ev().worstTick(12400).rtt(1041), "Ticks 12,400 ms, ping 1,041 ms");
	}

	@Test
	public void aMissingNumberIsLeftOut()
	{
		final Verdict n3 = judged(Cause.UPLOAD_LOSS, 0);
		final Verdict s3 = judged(Cause.CLIENT_BUSY, 0);
		final Verdict g1 = judged(Cause.GC_PAUSE, 0);
		assertNumbers("WORLD, no tick", w1(0), Icon.WORLD, new Ev().worstTick(-1), "Ping 41 ms");
		assertNumbers("WORLD, no ping", w1(0), Icon.WORLD, new Ev().rtt(-1), "Ticks 1,240 ms");
		assertNumbers("WORLD, neither", w1(0), Icon.WORLD, new Ev().worstTick(-1).rtt(-1), "");
		assertNumbers("LINE, no ping", n3, Icon.LINE, new Ev().rttMax(-1), "Ticks 1,240 ms");
		assertNumbers("LINE, no tick", n3, Icon.LINE, new Ev().worstTick(-1), "Ping 310 ms");
		assertNumbers("LINE, neither", n3, Icon.LINE, new Ev().rttMax(-1).worstTick(-1), "");
		assertNumbers("PC, no worst frame", s3, Icon.PC, new Ev().worstFrame(-1), "50 fps");
		assertNumbers("PC, no fps", s3, Icon.PC, new Ev().fps(-1), "Worst frame 480 ms");
		assertNumbers("PC, neither", s3, Icon.PC, new Ev().worstFrame(-1).fps(-1), "");
		assertNumbers("MEMORY, pauses not measured", g1, Icon.MEMORY, new Ev().pause(-1), "Memory 742 MB");
		assertNumbers("MEMORY, no heap", g1, Icon.MEMORY, new Ev().heap(-1), "Pause 340 ms");
		assertNumbers("MEMORY, measured and none: 0 prints", g1, Icon.MEMORY, new Ev().pause(0),
			"Pause 0 ms, memory 742 MB");
		assertNumbers("MEMORY, neither", g1, Icon.MEMORY, new Ev().pause(-1).heap(-1), "");
		assertNumbers("UNKNOWN, no tick", x(0), Icon.UNKNOWN, new Ev().worstTick(-1).worstFrame(170),
			"Worst frame 170 ms");
		assertNumbers("UNKNOWN, no worst frame", x(0), Icon.UNKNOWN, new Ev().worstFrame(-1), "Ticks 1,240 ms");
		assertNumbers("UNKNOWN, neither", x(0), Icon.UNKNOWN, new Ev().worstTick(-1).worstFrame(-1), "");
		assertNumbers("a number below 0 is no number, as -1 is (choice)", w1(0), Icon.WORLD, new Ev().worstTick(-5),
			"Ping 41 ms");
	}

	/** Choice: an answer with no picture (no event verdict of wave one has one) has no numbers. */
	@Test
	public void anAnswerWithNoIconHasNoNumbers()
	{
		final Verdict none = new Verdict(Cause.ALL_CLEAR, Confidence.SURE, Level.BAD, "h", "p", "", "", WALL0, 14,
			416, 0, null, null);
		assertNumbers("an event judged V2 by hand", none, Icon.NONE, new Ev(), "");
	}

	// ---------------------------------------------------------------- the chat line

	@Test
	public void oneChatLinePerClosedEvent()
	{
		final BadgeModel m = new BadgeModel();
		final LagEvent closed = new Ev().verdict(w1(0)).build();
		step(m, w1(0), null, closed, END + 5);
		assertEquals(W1_LINE, m.takeChatLine());
		assertNull("handed out once", m.takeChatLine());
		step(m, w1(0), null, closed, END + 6);
		assertNull("the same event at the next update makes none", m.takeChatLine());
		step(m, w1(0), null, new Ev().verdict(w1(0)).build(), END + 300);
		assertNull("an event is known by its id: an equal copy, long after, makes none", m.takeChatLine());
	}

	@Test
	public void noChatLineWhileTheSettingIsOff()
	{
		final BadgeModel m = new BadgeModel();
		final LagEvent first = new Ev().id(0).verdict(w1(0)).build();
		m.update(w1(0), null, first, END + 5, true, BadgeStyle.ICON, WhenSmooth.SHOW, false);
		assertNull(m.takeChatLine());
		step(m, w1(0), null, first, END + 6);
		assertNull("switched on later: the event was seen, so no line for it", m.takeChatLine());
		step(m, w1(0), null, first, END + 300);
		assertNull("ever", m.takeChatLine());
		// the next lag, with the setting on, gets its line: no line was made yet, so no chat gap holds it back
		final LagEvent next = new Ev().id(1).span(60, 62).verdict(x(1)).build();
		step(m, x(1), null, next, 67);
		assertEquals("[Why Lag] Lag - can't tell why (3 s). " + NOT_SURE_NUMBERS + ".", m.takeChatLine());
	}

	@Test
	public void noSecondLineInsideTheChatGap()
	{
		final long first = 100;
		final Verdict n3 = judged(Cause.UPLOAD_LOSS, 1);
		final String n3Line = "[Why Lag] Packet loss - line or world (5 s). Ping 310 ms, ticks 1,240 ms.";

		// closed 29 s after the first line: no line, not even once the gap is over
		BadgeModel m = aLineAt(first);
		final LagEvent at29 = new Ev().id(1).span(first + 20, first + 24).verdict(n3).build();
		for (long s = first + Thresholds.CHAT_GAP_S - 1; s <= first + 300; s++)
		{
			step(m, n3, null, at29, s);
			assertNull("second " + s, m.takeChatLine());
		}

		// closed 30 s after the first line: its line
		m = aLineAt(first);
		final LagEvent at30 = new Ev().id(1).span(first + 21, first + 25).verdict(n3).build();
		step(m, n3, null, at30, first + Thresholds.CHAT_GAP_S);
		assertEquals(n3Line, m.takeChatLine());

		// the gap counts from the last line MADE: a lag that got none starts no gap of its own
		m = aLineAt(first);
		step(m, n3, null, at29, first + Thresholds.CHAT_GAP_S - 1);
		assertNull(m.takeChatLine());
		final LagEvent then = new Ev().id(2).span(first + 30, first + 30).verdict(x(2)).build();
		step(m, x(2), null, then, first + 35);
		assertEquals("[Why Lag] Lag - can't tell why (1 s). " + NOT_SURE_NUMBERS + ".", m.takeChatLine());
	}

	@Test
	public void anOpenEventMakesNoChatLine()
	{
		final BadgeModel m = new BadgeModel();
		final Ev lag = new Ev().id(3).verdict(w1(3));
		for (long s = START; s < END + Thresholds.EVENT_QUIET_S; s++)
		{
			step(m, v2(FOUR_MIN), lag.span(START, Math.min(s, END)).open(true).build(), null, s);
			assertNull("open at " + s, m.takeChatLine());
		}
		// it closes: a lag that is over and judged, and it gets its one line; seeing it open did not use it up
		step(m, w1(3), null, lag.span(START, END).open(false).build(), END + Thresholds.EVENT_QUIET_S);
		assertEquals(W1_LINE, m.takeChatLine());
	}

	@Test
	public void aChatLineWithoutNumbersEndsAfterTheLength()
	{
		final BadgeModel m = new BadgeModel();
		final LagEvent closed = new Ev().span(START, START + 2).verdict(x(0)).worstTick(-1).worstFrame(-1).build();
		step(m, x(0), null, closed, START + 7);
		assertEquals("[Why Lag] Lag - can't tell why (3 s).", m.takeChatLine());
	}

	/** The words are the closed event's own: not the card's, not the open event's. */
	@Test
	public void theLineNamesTheClosedEventOnly()
	{
		final BadgeModel m = new BadgeModel();
		final LagEvent closed = new Ev().id(0).verdict(w1(0)).build();
		final LagEvent open = new Ev().id(1).span(END + 5, END + 5).open(true).verdict(judged(Cause.GC_PAUSE, 1))
			.build();
		step(m, f1(), open, closed, END + 5);
		assertEquals(W1_LINE, m.takeChatLine());
	}

	/** P2.6: the chat line is handled before the view, so no rule of the view holds it back. */
	@Test
	public void theChatLineIsMadeWhateverTheBadgeShows()
	{
		final LagEvent closed = new Ev().verdict(w1(0)).build();
		BadgeModel m = new BadgeModel();
		assertSame(BadgeView.HIDDEN,
			m.update(w1(0), null, closed, END + 5, false, BadgeStyle.ICON, WhenSmooth.SHOW, true));
		assertEquals("the badge switched off", W1_LINE, m.takeChatLine());
		m = new BadgeModel();
		assertSame(BadgeView.HIDDEN, step(m, notLoggedIn(), null, closed, END + 5));
		assertEquals("the lag ended at the login screen", W1_LINE, m.takeChatLine());
		m = new BadgeModel();
		assertSame(BadgeView.HIDDEN, m.update(w1(0), null, closed, END + Thresholds.BADGE_HOLD_S + 1, true,
			BadgeStyle.ICON, WhenSmooth.HIDE, true));
		assertEquals("smooth under Hide", W1_LINE, m.takeChatLine());
		for (BadgeStyle s : BadgeStyle.values())
		{
			m = new BadgeModel();
			m.update(w1(0), null, closed, END + 5, true, s, WhenSmooth.SHOW, true);
			assertEquals(s.name(), W1_LINE, m.takeChatLine());
		}
	}

	/** Choice: a line not taken is gone after the next update, which answers its own (P2.6: the LAST update's line). */
	@Test
	public void theLineIsTheLastUpdatesOnly()
	{
		final BadgeModel m = new BadgeModel();
		final LagEvent closed = new Ev().verdict(w1(0)).build();
		step(m, w1(0), null, closed, END + 5);
		step(m, w1(0), null, closed, END + 6);
		assertNull(m.takeChatLine());
		step(m, w1(0), null, closed, END + 300);
		assertNull("and it is not made again", m.takeChatLine());
	}

	/** Choice: a closed event with no verdict (the log never holds one) is a lag of unknown cause, not "Measuring". */
	@Test
	public void aClosedEventWithoutAVerdictIsALagOfNoKnownCause()
	{
		final BadgeModel m = new BadgeModel();
		final LagEvent closed = new Ev().build();
		assertView("the view", step(m, v2(FOUR_MIN), null, closed, END + 5), BadgeStyle.ICON, Level.BAD,
			Icon.UNKNOWN, true, "Lag", "Can't tell why", "Lag - can't tell why", NOT_SURE_NUMBERS);
		assertEquals("[Why Lag] Lag - can't tell why (14 s). " + NOT_SURE_NUMBERS + ".", m.takeChatLine());
	}

	// ---------------------------------------------------------------- the seam

	/** {@code view()} is one volatile read that allocates nothing; the model keeps four things between calls. */
	@Test
	public void viewIsOneVolatileReadThatAllocatesNothing()
	{
		int kept = 0;
		int views = 0;
		for (Field f : BadgeModel.class.getDeclaredFields())
		{
			if (Modifier.isStatic(f.getModifiers()) || f.isSynthetic())
			{
				continue;
			}
			kept++;
			if (f.getType() == BadgeView.class)
			{
				views++;
				assertTrue(f.getName() + " is volatile", Modifier.isVolatile(f.getModifiers()));
			}
		}
		assertEquals("one field holds the view", 1, views);
		assertEquals("four things kept between calls (contract 7, L10)", 4, kept);

		final com.sun.management.ThreadMXBean bean = allocationBean();
		Assume.assumeTrue("this JVM does not count allocated bytes", bean != null);
		final BadgeModel m = new BadgeModel();
		step(m, v2(FOUR_MIN), new Ev().open(true).verdict(w1(0)).build(), null, END);
		// The JIT's first compile of the loop may count a few bytes on this thread once (measured: 96 bytes in the
		// first run, then 0), so the million reads are repeated until a run is clean. Every read is stored where
		// it escapes, so a view() that allocated would allocate in EVERY run and could never read 0.
		long least = Long.MAX_VALUE;
		for (int run = 0; run < 10 && least != 0; run++)
		{
			least = Math.min(least, allocatedByAMillionReads(bean, m));
		}
		assertEquals("a million reads of view()", 0, least);
	}

	// ---------------------------------------------------------------- helpers

	/** One step with the badge on, style ICON, "When smooth: Show" and the chat line on. */
	private static BadgeView step(BadgeModel m, Verdict card, LagEvent open, LagEvent lastClosed, long lastSec)
	{
		return m.update(card, open, lastClosed, lastSec, true, BadgeStyle.ICON, WhenSmooth.SHOW, true);
	}

	/** A model that made its first chat line at {@code sec}, for a W1 lag of 14 s; the line is taken. */
	private static BadgeModel aLineAt(long sec)
	{
		final BadgeModel m = new BadgeModel();
		step(m, w1(0), null, new Ev().id(0).span(sec - 18, sec - 5).verdict(w1(0)).build(), sec);
		assertEquals("the first line", W1_LINE, m.takeChatLine());
		return m;
	}

	/** An open event of the picture's span with {@code v} as its provisional verdict, q 0 at {@link #END}. */
	private static LagEvent openLag(Verdict v)
	{
		return new Ev().id(v.eventId).open(true).verdict(v).build();
	}

	/**
	 * The event's numbers are the open lag's tip 2 and the tail of the closed lag's chat line, and the answer's icon
	 * is the one named.
	 */
	private static void assertNumbers(String what, Verdict v, Icon icon, Ev ev, String numbers)
	{
		final BadgeModel m = new BadgeModel();
		final BadgeView open = step(m, v2(FOUR_MIN), ev.open(true).verdict(v).build(), null, END);
		assertSame(what + ": the icon", icon, open.icon);
		assertEquals(what + ": tip 2", numbers, open.tip2);
		step(m, v, null, ev.open(false).build(), END + 5);
		final String line = m.takeChatLine();
		assertNotNull(what + ": a chat line", line);
		assertTrue(what + ": " + line, line.endsWith(numbers.isEmpty() ? " (14 s)." : " (14 s). " + numbers + "."));
	}

	/** Shows a lag first, so the hidden case is entered from a VISIBLE view; then it must answer HIDDEN itself. */
	private static void assertHiddenAfterALag(BadgeModel m, BadgeStyle s, String what, Supplier<BadgeView> hidden)
	{
		assertTrue(s + ", " + what + ": a lag showed first", m.update(v2(FOUR_MIN),
			new Ev().open(true).verdict(w1(0)).build(), null, END, true, s, WhenSmooth.SHOW, true).visible);
		assertSame(s + ", " + what, BadgeView.HIDDEN, hidden.get());
		assertSame(s + ", " + what + ": view()", BadgeView.HIDDEN, m.view());
	}

	private static void assertAnswered(BadgeModel m, BadgeView answered)
	{
		assertSame(answered, m.view());
		assertSame("read twice", answered, m.view());
	}

	private static void assertRow(BadgeStyle s, String row, BadgeView v, Level level, Icon icon, String line1,
		String line2)
	{
		final String what = s + ", row " + row;
		assertTrue(what + ": visible", v.visible);
		assertSame(what + ": style", s, v.style);
		assertSame(what + ": level", level, v.level);
		assertSame(what + ": icon", icon, v.icon);
		assertFalse(what + ": not dimmed", v.dimmed);
		assertEquals(what + ": line 1", line1, v.line1);
		assertEquals(what + ": line 2", line2, v.line2);
	}

	private static void assertTips(String what, BadgeView v, String tip1, String tip2)
	{
		assertEquals(what + ": tip 1", tip1, v.tip1);
		assertEquals(what + ": tip 2", tip2, v.tip2);
	}

	private static void assertView(String what, BadgeView v, BadgeStyle style, Level level, Icon icon,
		boolean dimmed, String line1, String line2, String tip1, String tip2)
	{
		assertTrue(what + ": visible", v.visible);
		assertSame(what + ": style", style, v.style);
		assertSame(what + ": level", level, v.level);
		assertSame(what + ": icon", icon, v.icon);
		assertEquals(what + ": dimmed", dimmed, v.dimmed);
		assertEquals(what + ": line 1", line1, v.line1);
		assertEquals(what + ": line 2", line2, v.line2);
		assertEquals(what + ": tip 1", tip1, v.tip1);
		assertEquals(what + ": tip 2", tip2, v.tip2);
	}

	/** The picture's lag judged W1, as the judge writes an event's verdict: level BAD, the event's id. */
	private static Verdict w1(long eventId)
	{
		return new Verdict(Cause.SLOW_WORLD, Confidence.LIKELY, Level.BAD, "World 416 is struggling, not you",
			"Ticks 600 to 900+ ms for 14 s. Ping stayed 41 ms, 50 fps.", "Hop to a quieter world.",
			"Frames and ping were fine.", WALL0 + START * 1000, 14, 416, eventId, null, null);
	}

	/** X, "Can't tell yet", with no candidate. */
	private static Verdict x(long eventId)
	{
		return new Verdict(Cause.NOT_SURE, Confidence.CANT_TELL, Level.BAD, "Can't tell yet",
			"A 480 ms freeze. Its cause was not measured.", "Wait for it to happen again.", "", WALL0 + START * 1000,
			14, 416, eventId, null, null);
	}

	/** An event's verdict of any cause: level BAD, the event's id (the badge never prints the judge's words). */
	private static Verdict judged(Cause cause, long eventId)
	{
		return new Verdict(cause, Confidence.LIKELY, Level.BAD, "headline", "proof", "fix", "", WALL0 + START * 1000,
			14, 416, eventId, null, null);
	}

	/** V2, all clear. */
	private static Verdict v2(String proof)
	{
		return new Verdict(Cause.ALL_CLEAR, Confidence.SURE, Level.OK, "Smooth", proof, "", "", 0, 0, 0, -1, null,
			null);
	}

	/** F1, a condition: the frame rate is capped at 50 by FPS Control. */
	private static Verdict f1()
	{
		return condition(Cause.FRAME_CAP, "Set by FPS Control. Not lag.");
	}

	/** A condition: level WARN, not an event (-1), since the second it began winning. */
	private static Verdict condition(Cause cause, String proof)
	{
		return new Verdict(cause, Confidence.LIKELY, Level.WARN, "headline", proof, "fix", "", WALL0 + 400_000L, 0,
			416, -1, null, null);
	}

	private static Verdict measuring()
	{
		return state(Answer.HEAD_MEASURING, "Ready in 40 s.");
	}

	private static Verdict waiting()
	{
		return state(Answer.HEAD_WAITING, "No frames are being drawn.");
	}

	private static Verdict notLoggedIn()
	{
		return state(Answer.HEAD_NOT_LOGGED_IN, "Log in to start measuring.");
	}

	/** A card state of 5.1: cause WARMING_UP, CANT_TELL, NO_DATA, told apart by its headline (contract 3.6). */
	private static Verdict state(String headline, String proof)
	{
		return new Verdict(Cause.WARMING_UP, Confidence.CANT_TELL, Level.NO_DATA, headline, proof, "", "", 0, 0, 0,
			-1, null, null);
	}

	/** The bytes this thread allocates over a million reads of {@code view()}, each kept where it escapes. */
	private static long allocatedByAMillionReads(com.sun.management.ThreadMXBean bean, BadgeModel m)
	{
		final BadgeView[] kept = new BadgeView[1];
		long sink = 0;
		final long thread = Thread.currentThread().getId();
		bean.getThreadAllocatedBytes(thread);
		final long before = bean.getThreadAllocatedBytes(thread);
		for (int i = 0; i < 1_000_000; i++)
		{
			kept[0] = m.view();
			sink += kept[0].line1.length();
		}
		final long after = bean.getThreadAllocatedBytes(thread);
		assertEquals("every read is the view", 9_000_000L, sink);
		return after - before;
	}

	/** The allocation counter of this JVM, or null when it has none (test code only). */
	private static com.sun.management.ThreadMXBean allocationBean()
	{
		final java.lang.management.ThreadMXBean bean = ManagementFactory.getThreadMXBean();
		if (!(bean instanceof com.sun.management.ThreadMXBean))
		{
			return null;
		}
		final com.sun.management.ThreadMXBean sun = (com.sun.management.ThreadMXBean) bean;
		if (!sun.isThreadAllocatedMemorySupported())
		{
			return null;
		}
		if (!sun.isThreadAllocatedMemoryEnabled())
		{
			sun.setThreadAllocatedMemoryEnabled(true);
		}
		return sun;
	}

	/**
	 * A lag event, built by constructor (contract 7, L10). Its numbers start at the picture's: 50 fps, worst frame
	 * 480 ms, worst tick 1,240 ms (the mean {@link #MEAN_TICK}), ping 41 ms (the highest 310 ms), a 340 ms pause and
	 * 742 of 768 MB; closed, no verdict, id 0, the picture's span.
	 */
	private static final class Ev
	{
		private long id;
		private long startSec = START;
		private long endSec = END;
		private boolean isOpen;
		private Verdict verdict;
		private int fps = 50;
		private int worstFrameMs = 480;
		private int worstTickGapMs = 1240;
		private int rttMs = 41;
		private int rttMaxMs = 310;
		private int gcPauseMs = 340;
		private int heapUsedMb = 742;

		Ev id(long v)
		{
			id = v;
			return this;
		}

		Ev span(long from, long to)
		{
			startSec = from;
			endSec = to;
			return this;
		}

		Ev open(boolean v)
		{
			isOpen = v;
			return this;
		}

		Ev verdict(Verdict v)
		{
			verdict = v;
			return this;
		}

		Ev fps(int v)
		{
			fps = v;
			return this;
		}

		Ev worstFrame(int v)
		{
			worstFrameMs = v;
			return this;
		}

		Ev worstTick(int v)
		{
			worstTickGapMs = v;
			return this;
		}

		Ev rtt(int v)
		{
			rttMs = v;
			return this;
		}

		Ev rttMax(int v)
		{
			rttMaxMs = v;
			return this;
		}

		Ev pause(int v)
		{
			gcPauseMs = v;
			return this;
		}

		Ev heap(int v)
		{
			heapUsedMb = v;
			return this;
		}

		LagEvent build()
		{
			return new LagEvent(id, startSec, endSec, WALL0 + startSec * 1000, Trigger.TICK_OFF.bit(),
				Trigger.TICK_OFF, 416, 0, 0, 0, fps, worstFrameMs, MEAN_TICK, worstTickGapMs, 640, rttMs, rttMaxMs,
				41, 900, 0, gcPauseMs, heapUsedMb, 768, 37, 95, isOpen, false, verdict);
		}
	}
}
