package com.whylag.core;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

/**
 * The big two-line answer (contract 3.6), the ONE source of the words the card, the badge, its tooltip and the chat
 * line print: every row of 3.6's table as literals, the three card states told apart by their headline, a null
 * verdict, the group's pair for every cause outside wave one, the one-line form, constants that a caller may compare
 * by identity, the length and ASCII limits, and the three headlines the verdict engine writes from here.
 */
public class AnswerTest
{
	/** Each wave-one row of 3.6's table, as literals: line 1, line 2 ("" for "-"), the icon, and the one line. */
	private static final Map<Cause, Object[]> ROWS = new LinkedHashMap<>();

	static
	{
		ROWS.put(Cause.ALL_CLEAR, row("Smooth", "", Icon.NONE, "Smooth"));
		ROWS.put(Cause.SLOW_WORLD, row("World lag", "Not you", Icon.WORLD, "World lag - not you"));
		ROWS.put(Cause.PING_JUMPY, row("Ping lag", "Your internet", Icon.LINE, "Ping lag - your internet"));
		ROWS.put(Cause.PING_HIGH, row("High ping", "Your internet", Icon.LINE, "High ping - your internet"));
		ROWS.put(Cause.UPLOAD_LOSS, row("Packet loss", "Line or world", Icon.LINE, "Packet loss - line or world"));
		ROWS.put(Cause.DISCONNECT, row("Disconnected", "Line or world", Icon.LINE, "Disconnected - line or world"));
		ROWS.put(Cause.DELIVERY_GAP, row("No ticks", "Line or world", Icon.UNKNOWN, "No ticks - line or world"));
		ROWS.put(Cause.SLOW_DRAWING, row("Low FPS", "Your PC", Icon.PC, "Low FPS - your PC"));
		ROWS.put(Cause.FRAME_CAP, row("FPS capped", "Your setting", Icon.PC, "FPS capped - your setting"));
		ROWS.put(Cause.CLIENT_BUSY, row("Client froze", "Your PC", Icon.PC, "Client froze - your PC"));
		ROWS.put(Cause.MAP_LOAD, row("Map loading", "Just the map", Icon.PC, "Map loading - just the map"));
		ROWS.put(Cause.NOT_SURE, row("Lag", "Can't tell why", Icon.UNKNOWN, "Lag - can't tell why"));
	}

	/** Each row of the table, words and icon, as literals; the three card states are the last three rows. */
	@Test
	public void everyWaveOneCauseHasItsPair()
	{
		for (Cause c : Cause.values())
		{
			if (!c.inWaveOne() || c == Cause.WARMING_UP)
			{
				continue;
			}
			final Object[] row = ROWS.get(c);
			assertNotNull(c + " is in wave one and needs its row of 3.6's table", row);
			assertAnswer(c.name(), row, Answer.of(c));
			assertSame(c + ": a verdict of the cause answers the same", Answer.of(c), Answer.of(verdict(c, "h")));
		}
		assertEquals("the twelve causes of the rules (W1c shares W1's)", 12, ROWS.size());
		assertAnswer("measuring", row("Measuring", "", Icon.NONE, "Measuring"),
			Answer.of(state(Answer.HEAD_MEASURING)));
		assertAnswer("not logged in", row("Not logged in", "", Icon.NONE, "Not logged in"),
			Answer.of(state(Answer.HEAD_NOT_LOGGED_IN)));
		assertAnswer("waiting", row("Waiting", "", Icon.NONE, "Waiting"), Answer.of(state(Answer.HEAD_WAITING)));
	}

	/** W1 (an event, BAD) and W1c (a condition, WARN) share one pair: the level is the verdict's, not the answer's. */
	@Test
	public void aConditionSharesItsEventsPair()
	{
		final Verdict w1 = new Verdict(Cause.SLOW_WORLD, Confidence.LIKELY, Level.BAD,
			"World 416 is struggling, not you", "p", "f", "", 1, 14, 416, 7, null, null);
		final Verdict w1c = new Verdict(Cause.SLOW_WORLD, Confidence.HINT, Level.WARN, "This world is running slow",
			"p", "f", "", 1, 0, 416, -1, null, null);
		assertSame(Answer.of(w1), Answer.of(w1c));
	}

	/** The three states of the card share the cause WARMING_UP; their headline alone tells them apart. */
	@Test
	public void theThreeStatesAreToldApartByTheirHeadline()
	{
		assertSame(Answer.NOT_LOGGED_IN, Answer.of(state(Answer.HEAD_NOT_LOGGED_IN)));
		assertSame(Answer.MEASURING, Answer.of(state(Answer.HEAD_MEASURING)));
		assertSame(Answer.WAITING, Answer.of(state(Answer.HEAD_WAITING)));
		assertSame("any other headline of the cause is measuring", Answer.MEASURING, Answer.of(state("Ready soon")));
		assertSame(Answer.MEASURING, Answer.of(state("")));
		assertSame("the cause alone has no headline", Answer.MEASURING, Answer.of(Cause.WARMING_UP));
		assertNotSame(Answer.NOT_LOGGED_IN, Answer.MEASURING);
		assertNotSame(Answer.WAITING, Answer.MEASURING);
		assertNotSame(Answer.NOT_LOGGED_IN, Answer.WAITING);
		assertSame("the headline decides only for WARMING_UP", Answer.of(Cause.SLOW_WORLD),
			Answer.of(verdict(Cause.SLOW_WORLD, Answer.HEAD_NOT_LOGGED_IN)));
		assertSame(Answer.SMOOTH, Answer.of(verdict(Cause.ALL_CLEAR, Answer.HEAD_WAITING)));
	}

	@Test
	public void aNullVerdictIsMeasuring()
	{
		assertSame(Answer.MEASURING, Answer.of((Verdict) null));
		assertSame("a null cause too", Answer.MEASURING, Answer.of((Cause) null));
		assertSame("and a verdict with no cause", Answer.MEASURING, Answer.of(verdict(null, "h")));
	}

	/** Every cause outside wave one answers its group's pair, never null, never a throw; all five groups are seen. */
	@Test
	public void aLaterCauseAnswersItsGroupsPair()
	{
		final Map<Group, Object[]> pairs = new LinkedHashMap<>();
		pairs.put(Group.CONNECTION, row("Ping lag", "Your internet", Icon.LINE, "Ping lag - your internet"));
		pairs.put(Group.FRAME_RATE, row("Low FPS", "Your PC", Icon.PC, "Low FPS - your PC"));
		pairs.put(Group.WORLD, row("World lag", "Not you", Icon.WORLD, "World lag - not you"));
		pairs.put(Group.UNSURE, row("Lag", "Can't tell why", Icon.UNKNOWN, "Lag - can't tell why"));
		pairs.put(Group.NONE, row("Smooth", "", Icon.NONE, "Smooth"));
		final Set<Group> seen = EnumSet.noneOf(Group.class);
		for (Cause c : Cause.values())
		{
			if (c.inWaveOne())
			{
				continue;
			}
			final Answer a = Answer.of(c);
			assertNotNull(c.name(), a);
			assertAnswer(c.name() + " (" + c.group() + ")", pairs.get(c.group()), a);
			assertSame(c + ": the verdict answers the same", a, Answer.of(verdict(c, "h")));
			seen.add(c.group());
		}
		assertEquals("every group's pair is reached by a later cause", EnumSet.allOf(Group.class), seen);
		assertSame("the same constant as the wave-one cause of the pair", Answer.of(Cause.PING_JUMPY),
			Answer.of(Cause.MAC_WIFI));
		assertSame(Answer.SMOOTH, Answer.of(Cause.VSYNC_HINT));
	}

	@Test
	public void oneLineLowersTheFirstLetterOfLineTwo()
	{
		assertEquals("World lag - not you", Answer.of(Cause.SLOW_WORLD).oneLine);
		assertEquals("Low FPS - your PC", Answer.of(Cause.SLOW_DRAWING).oneLine);
		assertEquals("Smooth", Answer.SMOOTH.oneLine);
		assertEquals("Client froze - your PC", Answer.of(Cause.CLIENT_BUSY).oneLine);
		assertEquals("Lag - can't tell why", Answer.of(Cause.NOT_SURE).oneLine);
		assertEquals("Measuring", Answer.MEASURING.oneLine);
		for (Answer a : everyAnswer())
		{
			if (a.line2.isEmpty())
			{
				assertEquals("line 1 alone", a.line1, a.oneLine);
			}
			else
			{
				assertEquals(a.line1 + " - " + Character.toLowerCase(a.line2.charAt(0)) + a.line2.substring(1),
					a.oneLine);
			}
		}
	}

	/** The same object for the same cause, and every answer is a constant of the class: compare by identity. */
	@Test
	public void ofAnswersConstants() throws IllegalAccessException
	{
		final List<Object> constants = new ArrayList<>();
		for (Field f : Answer.class.getDeclaredFields())
		{
			final int m = f.getModifiers();
			if (f.getType() == Answer.class && Modifier.isStatic(m) && Modifier.isFinal(m))
			{
				f.setAccessible(true);
				constants.add(f.get(null));
			}
		}
		for (Answer a : everyAnswer())
		{
			assertTrue(a.oneLine + " is a constant of the class", containsSame(constants, a));
		}
		for (Cause c : Cause.values())
		{
			assertSame(c + ": the same object every time", Answer.of(c), Answer.of(c));
		}
		assertSame(Answer.SMOOTH, Answer.of(Cause.ALL_CLEAR));
		assertSame(Answer.MEASURING, Answer.of((Verdict) null));
		assertEquals("no public way to build one", 0, Answer.class.getConstructors().length);
		for (String name : Arrays.asList("SMOOTH", "MEASURING", "NOT_LOGGED_IN", "WAITING"))
		{
			final Field f = field(name);
			assertTrue(name + " is public", Modifier.isPublic(f.getModifiers()));
			final int m = f.getModifiers();
			assertTrue(name + " is a constant", Modifier.isStatic(m) && Modifier.isFinal(m));
		}
	}

	/** At most 14 characters a line (the card's big line and the badge's word line are measured in L6 and L11). */
	@Test
	public void noLineIsLongerThanFourteenCharacters()
	{
		int longest = 0;
		for (Answer a : everyAnswer())
		{
			assertFalse("line 1 is never empty", a.line1.isEmpty());
			assertTrue(a.line1, a.line1.length() <= 14);
			assertTrue(a.line2, a.line2.length() <= 14);
			longest = Math.max(longest, Math.max(a.line1.length(), a.line2.length()));
		}
		assertEquals("\"Can't tell why\" is the longest", 14, longest);
	}

	@Test
	public void asciiOnly()
	{
		final List<String> texts = new ArrayList<>(Arrays.asList(Answer.HEAD_NOT_LOGGED_IN, Answer.HEAD_MEASURING,
			Answer.HEAD_WAITING));
		for (Answer a : everyAnswer())
		{
			texts.add(a.line1);
			texts.add(a.line2);
			texts.add(a.oneLine);
		}
		for (String t : texts)
		{
			for (int i = 0; i < t.length(); i++)
			{
				final char c = t.charAt(i);
				assertTrue("plain ASCII in \"" + t + "\"", c >= 32 && c <= 126);
			}
		}
	}

	/** The headlines of the three card states, which the verdict engine (L4) writes FROM these constants. */
	@Test
	public void theHeadlinesAreTheCardsWords()
	{
		assertEquals("Not logged in", Answer.HEAD_NOT_LOGGED_IN);
		assertEquals("Still measuring", Answer.HEAD_MEASURING);
		assertEquals("Waiting for the game", Answer.HEAD_WAITING);
		assertEquals("the warm-up headline is NoData's word", NoData.WARMING_UP.reason(), Answer.HEAD_MEASURING);
		assertEquals(NoData.NOT_LOGGED_IN.reason(), Answer.HEAD_NOT_LOGGED_IN);
		for (String h : Arrays.asList(Answer.HEAD_NOT_LOGGED_IN, Answer.HEAD_MEASURING, Answer.HEAD_WAITING))
		{
			assertTrue("a headline is at most 32 characters (3.6)", h.length() <= 32);
		}
	}

	// ---------------------------------------------------------------- helpers

	private static Object[] row(String line1, String line2, Icon icon, String oneLine)
	{
		return new Object[] {line1, line2, icon, oneLine};
	}

	private static void assertAnswer(String what, Object[] row, Answer a)
	{
		assertEquals(what + ": line 1", row[0], a.line1);
		assertEquals(what + ": line 2", row[1], a.line2);
		assertSame(what + ": icon", row[2], a.icon);
		assertEquals(what + ": one line", row[3], a.oneLine);
	}

	/** The answer of every cause and of the three card states. */
	private static List<Answer> everyAnswer()
	{
		final List<Answer> out = new ArrayList<>();
		for (Cause c : Cause.values())
		{
			out.add(Answer.of(c));
		}
		out.add(Answer.of(state(Answer.HEAD_NOT_LOGGED_IN)));
		out.add(Answer.of(state(Answer.HEAD_MEASURING)));
		out.add(Answer.of(state(Answer.HEAD_WAITING)));
		return out;
	}

	private static boolean containsSame(List<Object> list, Object o)
	{
		for (Object x : list)
		{
			if (x == o)
			{
				return true;
			}
		}
		return false;
	}

	private static Field field(String name)
	{
		try
		{
			return Answer.class.getDeclaredField(name);
		}
		catch (NoSuchFieldException e)
		{
			throw new AssertionError("Answer." + name + " is missing", e);
		}
	}

	/** A verdict of that cause with that headline; nothing else of it matters to the answer. */
	private static Verdict verdict(Cause c, String headline)
	{
		return new Verdict(c, Confidence.LIKELY, Level.BAD, headline, "p", "f", "", 0, 0, 0, -1, null, null);
	}

	/** A card state of 5.1: cause WARMING_UP, confidence CANT_TELL, level NO_DATA, told apart by its headline. */
	private static Verdict state(String headline)
	{
		return new Verdict(Cause.WARMING_UP, Confidence.CANT_TELL, Level.NO_DATA, headline,
			"Log in to start measuring.", "", "", 0, 0, 0, -1, null, null);
	}
}
