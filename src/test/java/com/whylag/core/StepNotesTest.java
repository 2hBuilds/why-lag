package com.whylag.core;

import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * What changed between one sampler step and the next, as notes (1.0.1, lot A, A4): a login, a hop and a logout from
 * the ring's newest complete second; a lag opened and closed; the card; the ping probe's state; the settings. Each
 * kind is noted when it CHANGES and never twice for a steady state. The sessions are {@link Trace}s, the events
 * are built by hand, and the wall clock of a step is a fixed number.
 */
public class StepNotesTest
{
	private static final long WALL = Instant.parse("2026-09-30T14:05:09Z").toEpochMilli();

	private final Diagnostics diagnostics = new Diagnostics(ZoneOffset.UTC);
	private final StepNotes notes = new StepNotes(diagnostics);
	private final SettingsView cpu = new SettingsView(Renderer.CPU, false, false, 0, false, 0, false, "", 0, 0, "", 0,
		60, Os.WINDOWS, "1.13.0");

	@Test
	public void aLoginAHopAndALogoutAreNotedFromTheNewestSecond()
	{
		final Session atTheLoginScreen = Trace.steady(40).loginAt(39).build();
		step(atTheLoginScreen, 10);
		assertEquals("a player found logged out says nothing", none(), noted("logged"));

		final Session loggedIn = Trace.steady(40).loginAt(5).build();
		step(loggedIn, 10);
		step(loggedIn, 11);
		assertEquals("noted once", list("14:05:09  logged in, world 416"), noted("logged"));

		final Session hopped = Trace.steady(40).loginAt(5).hop(20, 302).build();
		step(hopped, 15);
		step(hopped, 22);
		step(hopped, 23);
		assertEquals(list("14:05:09  hop to world 302"), noted("hop"));

		step(atTheLoginScreen, 10);
		assertEquals(list("14:05:09  logged in, world 416", "14:05:09  logged out"), noted("logged"));
	}

	@Test
	public void aPlayerFoundLoggedInWhenTheFirstStepRunsIsNoted()
	{
		step(Trace.steady(40).build(), 10);

		assertEquals(list("14:05:09  logged in, world 416"), noted("logged"));
	}

	@Test
	public void aWorldThatBecomesKnownWhileLoggedInIsNotAHop()
	{
		final Session noWorld = Trace.steady(40).hop(0, 0).build();
		step(noWorld, 10);
		assertEquals(list("14:05:09  logged in"), noted("logged"));

		step(Trace.steady(40).build(), 10);
		assertEquals(list("14:05:09  world 416"), noted("world"));
		assertEquals(none(), noted("hop"));
	}

	@Test
	public void aNoSecondYetSaysNothing()
	{
		notes.step(WALL, new Session(1_000_000_000L, WALL, Os.WINDOWS, ZoneOffset.UTC), 5, null, null, null,
			NoData.NONE, null);

		assertEquals(none(), noted("logged"));
		assertEquals("the card of a null verdict and the ping are still noted",
			list("14:05:09  card: Measuring"), noted("card"));
	}

	@Test
	public void aLagOpenedAndClosedIsNotedOnceEachWithItsWords()
	{
		final Session s = Trace.steady(40).build();
		final LagEvent open = lag(7, 14, false, null);

		notes.step(WALL, s, 10, null, open, null, NoData.NONE, cpu);
		notes.step(WALL, s, 11, null, open, null, NoData.NONE, cpu);
		assertEquals("an open event is noted at the first step that sees it", list(
			"14:05:09  lag opened: NOT SURE, ticks 1,240 ms, ping 41 ms"), noted("lag"));

		final LagEvent closed = lag(7, 14, true, verdict(Cause.SLOW_WORLD, Confidence.LIKELY));
		notes.step(WALL, s, 12, null, null, closed, NoData.NONE, cpu);
		notes.step(WALL, s, 13, null, null, closed, NoData.NONE, cpu);
		assertEquals(list("14:05:09  lag opened: NOT SURE, ticks 1,240 ms, ping 41 ms",
			"14:05:09  lag closed after 14 s: World lag - not you, Likely"), noted("lag"));
	}

	/** A lag that opened and closed inside one step is given its "opened" first, from its own numbers. */
	@Test
	public void aLagNeverSeenOpenIsNotedOpenedBeforeItIsClosed()
	{
		final LagEvent closed = lag(3, 6, true, verdict(Cause.CLIENT_BUSY, Confidence.SURE));

		notes.step(WALL, Trace.steady(40).build(), 10, null, null, closed, NoData.NONE, cpu);

		assertEquals(list("14:05:09  lag opened: FRAME RATE, ticks 1,240 ms, ping 41 ms",
			"14:05:09  lag closed after 6 s: Client froze - your PC, Sure"), noted("lag"));
	}

	/** Closed ones first: an event closed and a new one open in the same step read in the order they happened. */
	@Test
	public void aClosedLagIsNotedBeforeANewOpenOneInTheSameStep()
	{
		final LagEvent closed = lag(1, 2, true, verdict(Cause.SLOW_WORLD, Confidence.LIKELY));
		final LagEvent open = lag(2, 1, false, null);

		notes.step(WALL, Trace.steady(40).build(), 10, null, open, closed, NoData.NONE, cpu);

		final List<String> lags = noted("lag");
		assertEquals(3, lags.size());
		assertTrue(lags.get(1), lags.get(1).contains("lag closed after 2 s"));
		assertTrue(lags.get(2), lags.get(2).contains("lag opened"));
	}

	@Test
	public void aLagWithNoNumbersSaysDashes()
	{
		final LagEvent e = new LagEvent(0, 0, 4, WALL, Trigger.TICK_OFF.bit(), Trigger.TICK_OFF, 416, 0, 0, 0, -1,
			-1, -1, -1, -1, -1, -1, -1, -1, -1, true, false, null);

		notes.step(WALL, Trace.steady(40).build(), 10, null, e, null, NoData.NONE, cpu);

		assertEquals(list("14:05:09  lag opened: NOT SURE, ticks -, ping -"), noted("lag"));
	}

	@Test
	public void theCardIsNotedWhenItsAnswerChanges()
	{
		final Session s = Trace.steady(40).build();
		final Verdict smooth = new Verdict(Cause.ALL_CLEAR, Confidence.SURE, Level.OK, "Smooth", "No lag.", "", "", 0, 0,
			416, -1, null, null);
		final Verdict world = verdict(Cause.SLOW_WORLD, Confidence.LIKELY);

		notes.step(WALL, s, 10, smooth, null, null, NoData.NONE, cpu);
		notes.step(WALL, s, 11, smooth, null, null, NoData.NONE, cpu);
		notes.step(WALL, s, 12, world, null, null, NoData.NONE, cpu);
		notes.step(WALL, s, 13, world, null, null, NoData.NONE, cpu);
		notes.step(WALL, s, 14, smooth, null, null, NoData.NONE, cpu);

		assertEquals(list("14:05:09  card: Smooth", "14:05:09  card: World lag - not you", "14:05:09  card: Smooth"),
			noted("card"));
	}

	@Test
	public void thePingProbesStateIsNotedWhenItChanges()
	{
		final Session s = Trace.steady(40).build();
		for (NoData state : new NoData[] {NoData.NONE, NoData.NONE, NoData.UNSUPPORTED, NoData.ERROR, NoData.STALE,
			NoData.STALE, NoData.NONE, NoData.NOT_LOGGED_IN, NoData.NOT_CONNECTED, NoData.NO_TICKS})
		{
			notes.step(WALL, s, 10, null, null, null, state, cpu);
		}

		assertEquals(list("14:05:09  ping: reading", "14:05:09  ping: not on this PC", "14:05:09  ping: could not read it",
			"14:05:09  ping: stale", "14:05:09  ping: reading", "14:05:09  ping: not logged in",
			"14:05:09  ping: not connected", "14:05:09  ping: no data"), noted("ping"));
	}

	/** The renderer and the cap: a re-read that changed one is noted; the same again is not. */
	@Test
	public void theSettingsAreNotedWhenTheRendererOrTheCapChanges()
	{
		final Session s = Trace.steady(40).build();
		final SettingsView gpu = new SettingsView(Renderer.GPU, false, false, 0, false, 0, true, "OFF", 144, 50, "MSAA_2",
			0, 60, Os.WINDOWS, "1.13.0");
		final SettingsView fpsControl = new SettingsView(Renderer.GPU, true, true, 30, false, 0, true, "OFF", 144, 50,
			"MSAA_2", 0, 60, Os.WINDOWS, "1.13.0");
		final SettingsView unread = SettingsView.unknown(Os.WINDOWS);

		notes.step(WALL, s, 10, null, null, null, NoData.NONE, unread);
		notes.step(WALL, s, 11, null, null, null, NoData.NONE, cpu);
		notes.step(WALL, s, 12, null, null, null, NoData.NONE, cpu);
		notes.step(WALL, s, 13, null, null, null, NoData.NONE, gpu);
		notes.step(WALL, s, 14, null, null, null, NoData.NONE, fpsControl);
		notes.step(WALL, s, 15, null, null, null, NoData.NONE, null);

		assertEquals(list(
			"14:05:09  settings: renderer unknown, cap none",
			"14:05:09  settings: renderer CPU, cap 50 (the client)",
			"14:05:09  settings: renderer GPU, cap 144 (GPU: FPS target)",
			"14:05:09  settings: renderer GPU, cap 30 (FPS Control)"), noted("settings"));
	}

	// ---------------------------------------------------------------- helpers

	private void step(Session s, long nowSec)
	{
		notes.step(WALL, s, nowSec, null, null, null, NoData.NONE, cpu);
	}

	/** The notes whose text starts with {@code start}, as the report prints them. */
	private List<String> noted(String start)
	{
		final List<String> out = new ArrayList<>();
		boolean in = false;
		for (String line : diagnostics.text().split("\n", -1))
		{
			if (!in)
			{
				in = line.equals("Notes");
			}
			else if (line.isEmpty())
			{
				break;
			}
			else if (line.substring(10).startsWith(start))
			{
				out.add(line);
			}
		}
		return out;
	}

	private static List<String> list(String... lines)
	{
		return Arrays.asList(lines);
	}

	private static List<String> none()
	{
		return Collections.emptyList();
	}

	private static Verdict verdict(Cause cause, Confidence confidence)
	{
		return new Verdict(cause, confidence, Level.BAD, "h", "p", "f", "", 0, 0, 416, -1, null, null);
	}

	/** A lag of {@code lengthS} seconds with the numbers of the wiring test's own: ticks 1,240 ms, ping 41 ms. */
	private static LagEvent lag(long id, int lengthS, boolean closed, Verdict v)
	{
		return new LagEvent(id, 0, lengthS - 1, WALL, Trigger.TICK_OFF.bit(), Trigger.TICK_OFF, 416, 0, 0, 0, 50,
			22, 700, 1240, 640, 41, 45, 40, 900, 0, !closed, false, v);
	}
}
