package com.whylag.core;

import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

/**
 * What the panel reads (contract 3.8, 5.2, 5.3; lot L5), built from {@link Trace}s: the four tiles of a steady
 * trace and of every no-data state; the memory tile on its two sources, with an unknown heap limit, and read at the
 * newest second's end; the Server ticks tile's trimmed mean, raw deviation and corrected level; the frame rate tile
 * and columns judged by the cap in force for their own second's focus; the five strips and their time map (the range
 * ends at the end of the newest second, empty columns before the session and the login, a late tick as wide as its
 * gap, no tick after the range's end, the worst of each slice); the memory lane on heap AFTER collection; the CPU
 * lane's two series and its level; the value at the right of each lane; the world and the CPU of now; the range's
 * events, the session's events and the counts that outlive the log's cap; and the snapshot's cost (T16's part).
 */
public class SnapshotBuilderTest
{
	private static final SnapshotBuilder BUILDER = new SnapshotBuilder();
	/** What the card shows in these tests; the builder hands it on untouched. */
	private static final Verdict SMOOTH = new Verdict(Cause.ALL_CLEAR, Confidence.SURE, Level.OK, "Smooth",
		"No lag this session.", "", "", 0, 0, 0, -1, null, null);
	/** The Server ticks tile's small line starts with it: "plus-minus 13 ms" (contract 5.2). */
	private static final String PLUS_MINUS = "\u00b1";
	private static final int NONE = Strip.NONE;
	private static final int COLUMNS = Thresholds.STRIP_COLUMNS;
	private static final int LAST_COLUMN = COLUMNS - 1;
	private static final byte OK = (byte) Level.OK.ordinal();
	private static final byte WARN = (byte) Level.WARN.ordinal();
	private static final byte BAD = (byte) Level.BAD.ordinal();
	private static final byte NO_DATA = (byte) Level.NO_DATA.ordinal();
	private static final int FPS = Lane.FRAME_RATE.ordinal();
	private static final int TICKS = Lane.TICKS.ordinal();
	private static final int PING = Lane.PING.ordinal();
	private static final int MEMORY = Lane.MEMORY.ordinal();
	private static final int CPU = Lane.CPU.ordinal();
	/** T16's generous bound on CI, in ms (contract 4). */
	private static final long T16_BOUND_MS = 20;

	// ---------------------------------------------------------------- the tiles

	/** The picture's quiet numbers: 50 fps, 600 ms, 40 ms, 52 %, the memory small line "pause 0 ms". */
	@Test
	public void tilesOfASteadyTrace()
	{
		final PanelSnapshot p = snapshot(Trace.steady(100), 100, 1);
		assertEquals(Lane.TILES, p.tiles.length);
		assertTile(p.tiles[FPS], Lane.FRAME_RATE, Level.OK, "50 fps", "worst 22 ms", NoData.NONE);
		assertTile(p.tiles[TICKS], Lane.TICKS, Level.OK, "600 ms", PLUS_MINUS + "0 ms", NoData.NONE);
		assertTile(p.tiles[PING], Lane.PING, Level.OK, "40 ms", "", NoData.NONE);
		assertTile(p.tiles[MEMORY], Lane.MEMORY, Level.OK, "52 %", "pause 0 ms", NoData.NONE);

		// The ping tile's small line is this world's usual, once there is one.
		assertTile(snapshot(Trace.steady(100).usual(39), 100, 1).tiles[PING], Lane.PING, Level.OK, "40 ms",
			"was 39 ms", NoData.NONE);

		// The tiles read the window, whatever the range of the graphs.
		for (int minutes : new int[] {10, 60})
		{
			final PanelSnapshot q = snapshot(Trace.steady(100), 100, minutes);
			for (int i = 0; i < Lane.TILES; i++)
			{
				assertEquals(minutes + " min", p.tiles[i].value, q.tiles[i].value);
				assertEquals(minutes + " min", p.tiles[i].sub, q.tiles[i].sub);
				assertEquals(minutes + " min", p.tiles[i].level, q.tiles[i].level);
			}
		}
	}

	/** On the RUNTIME source pauses cannot be known: "pause n/a", and the level leaves the pause out. */
	@Test
	public void memorySubIsNaOnTheFallback()
	{
		final PanelSnapshot runtime = snapshot(Trace.steady(100).gcPause(80, 0, 400, 300).settings(runtime()), 100, 1);
		assertTile(runtime.tiles[MEMORY], Lane.MEMORY, Level.OK, "52 %", "pause n/a", NoData.NONE);

		// The same ring read from the management source knows the 400 ms pause: "pause 400 ms", BAD.
		final PanelSnapshot management = snapshot(Trace.steady(100).gcPause(80, 0, 400, 300), 100, 1);
		assertTile(management.tiles[MEMORY], Lane.MEMORY, Level.BAD, "52 %", "pause 400 ms", NoData.NONE);

		// Measured and none is "pause 0 ms", a different fact from "pause n/a".
		assertEquals("pause 0 ms", snapshot(Trace.steady(100), 100, 1).tiles[MEMORY].sub);
		assertEquals("pause n/a", snapshot(Trace.steady(100).settings(runtime()), 100, 1).tiles[MEMORY].sub);
	}

	/** While logged in, with a known heap limit, the memory tile always has a number; not logged in, all four dash. */
	@Test
	public void memoryTileIsNeverNoData()
	{
		assertMemoryHasANumber("steady", Trace.steady(100), 100);
		assertMemoryHasANumber("warming up", Trace.steady(200).loginAt(30), 50);
		assertMemoryHasANumber("frames stopped", Trace.steady(100), 103);
		assertMemoryHasANumber("no ticks", Trace.steady(100).noTicks(40, 99), 100);
		assertMemoryHasANumber("a stale ping", Trace.steady(100).rttStale(90, 99), 100);
		assertMemoryHasANumber("the fallback source", Trace.steady(100).settings(runtime()), 100);
		assertMemoryHasANumber("no collection yet", Trace.steady(100).noBaselineCollection(), 100);
		assertMemoryHasANumber("after a hop", Trace.steady(200).hop(100, 302), 150);
		assertMemoryHasANumber("a load in the newest second", Trace.steady(100).loading(99, 600), 100);
		assertMemoryHasANumber("after a lost connection", Trace.steady(100).disconnect(90), 100);
		assertMemoryHasANumber("a long freeze", Trace.steady(100).frameGap(98, 500, 1800), 100);
		assertMemoryHasANumber("a Runtime-only PC",
			Trace.steady(100).cpuUnknown().busyUnknown().settings(runtime()), 100);
		assertMemoryHasANumber("a pause of 400 ms", Trace.steady(100).gcPause(95, 0, 400, 700), 100);

		final PanelSnapshot out = snapshot(Trace.steady(100).loginAt(50), 40, 1);
		for (int i = 0; i < Lane.TILES; i++)
		{
			assertDash(out.tiles[i], Lane.values()[i], NoData.NOT_LOGGED_IN);
			assertEquals("Not logged in", out.tiles[i].sub);
		}
	}

	/** A heap limit of 0 or less is unknown (contract 3.7): no memory %, an empty lane with a 0 .. 0 scale. */
	@Test
	public void anUnknownHeapLimitIsNoMemoryPercent()
	{
		for (int limit : new int[] {0, -1})
		{
			final SettingsView unknown = settings(Renderer.CPU, limit, MemorySource.MANAGEMENT);
			final PanelSnapshot p = snapshot(Trace.steady(100).gcPause(80, 0, 150, 400).settings(unknown), 100, 10);
			assertDash(p.tiles[MEMORY], Lane.MEMORY, NoData.ERROR);
			assertEquals("Could not read it", p.tiles[MEMORY].sub);
			final Strip memory = p.strips[MEMORY];
			for (int c = 0; c < COLUMNS; c++)
			{
				assertEquals("column " + c, NONE, memory.values[c]);
				assertEquals("column " + c, NO_DATA, memory.levels[c]);
			}
			assertEquals(0, memory.min);
			assertEquals(0, memory.max);
			assertEquals("", memory.now);
			assertEquals(Level.NO_DATA, memory.nowLevel);
			// Nothing else is touched.
			assertEquals("50 fps", p.tiles[FPS].value);
			assertEquals(50, p.strips[FPS].values[LAST_COLUMN]);
			assertEquals(unknown, p.settings);
		}
	}

	/** An RTT older than RTT_STALE_S is no ping: the tile is "-" and "Nothing sent", and the lane has no value. */
	@Test
	public void staleRttSaysNothingSent()
	{
		final Trace t = Trace.steady(100).rttStale(95, 99);
		final PanelSnapshot p = snapshot(t, 100, 1);
		assertDash(p.tiles[PING], Lane.PING, NoData.STALE);
		assertEquals("Nothing sent", p.tiles[PING].sub);
		assertEquals("", p.strips[PING].now);

		// Before the stale seconds the tile showed the RTT.
		assertTile(snapshot(t, 95, 1).tiles[PING], Lane.PING, Level.OK, "40 ms", "", NoData.NONE);

		// A kept but stale RTT is not fresh, so a column made of stale seconds holds no ping.
		for (int c = 0; c < COLUMNS; c++)
		{
			final boolean stale = columnStartMs(p, c) >= 95_000;
			assertEquals("column " + c, stale ? NONE : 40, p.strips[PING].values[c]);
			assertEquals("the other lanes are drawn there", 50, p.strips[FPS].values[c]);
		}
	}

	/** Every row of 5.2's no-data table: "-" at NO_DATA, with its reason. */
	@Test
	public void everyNoDataState()
	{
		// Not logged in: at the login screen, before any second, in the second of a hop, in a lost connection.
		final PanelSnapshot[] out = {
			snapshot(Trace.steady(100).loginAt(50), 40, 1),
			snapshot(Trace.steady(100), 0, 1),
			snapshot(Trace.steady(100).hop(99, 302), 100, 1),
			snapshot(Trace.steady(100).disconnect(99), 100, 1)};
		for (PanelSnapshot p : out)
		{
			for (int i = 0; i < Lane.TILES; i++)
			{
				assertDash(p.tiles[i], Lane.values()[i], NoData.NOT_LOGGED_IN);
			}
		}

		// No frames drawn: four seconds behind the clock. The other tiles still read the rings.
		final Trace steady = Trace.steady(100);
		assertEquals("50 fps", snapshot(steady, 102, 1).tiles[FPS].value);
		final PanelSnapshot stopped = snapshot(steady, 103, 1);
		assertDash(stopped.tiles[FPS], Lane.FRAME_RATE, NoData.NO_FRAMES);
		assertEquals("No frames drawn", stopped.tiles[FPS].sub);
		assertEquals("600 ms", stopped.tiles[TICKS].value);
		assertEquals("40 ms", stopped.tiles[PING].value);
		assertEquals("52 %", stopped.tiles[MEMORY].value);

		// No ticks yet: none arrived in the window, or every one of them is masked just after a login.
		assertDash(snapshot(Trace.steady(100).noTicks(40, 99), 100, 1).tiles[TICKS], Lane.TICKS, NoData.NO_TICKS);
		final PanelSnapshot justIn = snapshot(Trace.steady(100).loginAt(90), 95, 1);
		assertDash(justIn.tiles[TICKS], Lane.TICKS, NoData.NO_TICKS);
		assertEquals("No ticks yet", justIn.tiles[TICKS].sub);
		assertEquals("logged in: the frame rate has its number", "50 fps", justIn.tiles[FPS].value);

		// The four reasons of the ping tile.
		final NoData[] reasons = {NoData.NOT_CONNECTED, NoData.UNSUPPORTED, NoData.ERROR};
		final String[] words = {"Not connected", "Not on this PC", "Could not read it"};
		for (int i = 0; i < reasons.length; i++)
		{
			final Tile ping = snapshot(Trace.steady(100).rttNoData(90, 99, reasons[i]), 100, 1).tiles[PING];
			assertDash(ping, Lane.PING, reasons[i]);
			assertEquals(words[i], ping.sub);
		}
		final Tile stale = snapshot(Trace.steady(100).rttStale(90, 99), 100, 1).tiles[PING];
		assertDash(stale, Lane.PING, NoData.STALE);
		assertEquals("Nothing sent", stale.sub);

		// Memory: the heap limit is unknown.
		final Tile memory = snapshot(Trace.steady(100).settings(settings(Renderer.CPU, 0, MemorySource.MANAGEMENT)),
			100, 1).tiles[MEMORY];
		assertDash(memory, Lane.MEMORY, NoData.ERROR);
		assertEquals("Could not read it", memory.sub);
	}

	/** At a cap the player set, a frame rate held at it is OK; the client's own 50 is no such cap. */
	@Test
	public void cappedFpsTileIsOk()
	{
		final SettingsView fpsControl30 = new SettingsView(Renderer.CPU, true, true, 30, false, 0, false, "", 0, 0, "",
			0, 60, 768, MemorySource.MANAGEMENT, Os.WINDOWS, "");
		assertEquals("precondition: FPS Control caps at 30", CapSource.FPS_CONTROL, fpsControl30.capSource(true));

		final PanelSnapshot capped = snapshot(Trace.steady(100).fps(0, 99, 30).settings(fpsControl30), 100, 1);
		assertTile(capped.tiles[FPS], Lane.FRAME_RATE, Level.OK, "30 fps", "worst 34 ms", NoData.NONE);
		assertEquals(Level.OK, capped.strips[FPS].nowLevel);
		assertEveryLevel(capped.strips[FPS], OK);

		// The same rate under the client's own cap, which the player did not set, is WARN.
		final PanelSnapshot client = snapshot(Trace.steady(100).fps(0, 99, 30), 100, 1);
		assertEquals(Level.WARN, client.tiles[FPS].level);
		assertEveryLevel(client.strips[FPS], WARN);

		// Within CAP_MATCH_FPS of the cap it is still held there; one frame further it is not.
		assertEquals(Level.OK, snapshot(Trace.steady(100).fps(0, 99, 28).settings(fpsControl30), 100, 1)
			.tiles[FPS].level);
		assertEquals(Level.WARN, snapshot(Trace.steady(100).fps(0, 99, 27).settings(fpsControl30), 100, 1)
			.tiles[FPS].level);

		// A GPU target the player set counts the same.
		final SettingsView gpuTarget30 = new SettingsView(Renderer.GPU, false, false, 0, false, 0, true, "OFF", 30, 50,
			"MSAA_2", 0, 60, 768, MemorySource.MANAGEMENT, Os.WINDOWS, "");
		assertEquals(Level.OK, snapshot(Trace.steady(100).fps(0, 99, 30).settings(gpuTarget30), 100, 1)
			.tiles[FPS].level);
	}

	/**
	 * Contract 3.7's case: alt-tabbed at 10 fps under FPS Control's unfocused limit of 10, its focused limit off, on
	 * the CPU renderer. The newest second is unfocused, so the tile, the lane's value and its last column are OK; read
	 * as focused they would be BAD (the client's 50, not set by the player).
	 */
	@Test
	public void theFrameRateTileReadsTheNewestSecondsFocus()
	{
		final SettingsView unfocusedLimit = unfocusedLimit(Renderer.CPU, 10, 0);
		assertEquals(CapSource.FPS_CONTROL_UNFOCUSED, unfocusedLimit.capSource(false));
		assertEquals(10, unfocusedLimit.capFps(false));
		assertEquals(CapSource.CLIENT_50, unfocusedLimit.capSource(true));

		final PanelSnapshot away = snapshot(Trace.steady(100).fps(40, 99, 10).unfocused(40, 99)
			.settings(unfocusedLimit), 100, 1);
		assertTile(away.tiles[FPS], Lane.FRAME_RATE, Level.OK, "10 fps", "worst 100 ms", NoData.NONE);
		assertEquals("10 fps", away.strips[FPS].now);
		assertEquals(Level.OK, away.strips[FPS].nowLevel);
		assertEquals(10, away.strips[FPS].values[LAST_COLUMN]);
		assertEquals(OK, away.strips[FPS].levels[LAST_COLUMN]);

		// The same seconds, focused: BAD in all three places.
		final PanelSnapshot here = snapshot(Trace.steady(100).fps(40, 99, 10).settings(unfocusedLimit), 100, 1);
		assertEquals(Level.BAD, here.tiles[FPS].level);
		assertEquals(Level.BAD, here.strips[FPS].nowLevel);
		assertEquals(BAD, here.strips[FPS].levels[LAST_COLUMN]);
	}

	/** A column is judged by the cap in force for the second that gave it its lowest rate; of several, the newest. */
	@Test
	public void aColumnIsJudgedByTheCapOfItsLowestSecond()
	{
		// Seconds 40..58 at 10 fps, unfocused under the unfocused limit of 10; second 59 at 10 fps, focused; the rest
		// steady and focused.
		final PanelSnapshot p = snapshot(Trace.steady(100).fps(40, 59, 10).unfocused(40, 58)
			.settings(unfocusedLimit(Renderer.CPU, 10, 0)), 100, 1);
		final Strip fps = p.strips[FPS];
		final int shared = lastColumnOf(p, 58);
		assertEquals("precondition: seconds 58 and 59 share a column at 1 min", shared, firstColumnOf(p, 59));
		for (int c = 0; c < COLUMNS; c++)
		{
			if (c < shared)
			{
				assertEquals("column " + c, 10, fps.values[c]);
				assertEquals("column " + c + ": an unfocused second under its own limit", OK, fps.levels[c]);
			}
			else if (c <= lastColumnOf(p, 59))
			{
				assertEquals("column " + c, 10, fps.values[c]);
				assertEquals("column " + c + ": the newest of the lowest is focused", BAD, fps.levels[c]);
			}
		}
		// The newest second is steady and focused: the tile is OK at 50.
		assertTile(p.tiles[FPS], Lane.FRAME_RATE, Level.OK, "50 fps", "worst 100 ms", NoData.NONE);
	}

	/** The mean gap leaves out gaps over TICK_TRIM_MS (900 stays in, 901 goes); the sub and level still see them. */
	@Test
	public void tickMeanLeavesOutLongGaps()
	{
		// No tick in seconds 70 and 71: one gap of 2,400 ms. With it in, the mean would be 618.
		final PanelSnapshot gap = snapshot(Trace.steady(100).noTicks(70, 71), 100, 1);
		assertTile(gap.tiles[TICKS], Lane.TICKS, Level.BAD, "600 ms", "worst 2,400", NoData.NONE);

		// One gap of exactly 900 stays in the mean, (99 x 600 + 900) / 100 = 603; one of 901 is left out.
		assertEquals("603 ms", snapshot(Trace.steady(100).tickLate(80, 300, false), 100, 1).tiles[TICKS].value);
		assertEquals("600 ms", snapshot(Trace.steady(100).tickLate(80, 301, false), 100, 1).tiles[TICKS].value);

		// A gap of 850 counts: (99 x 600 + 850) / 100 = 602.5, rounded down.
		assertEquals("602 ms", snapshot(Trace.steady(100).tickLate(80, 250, false), 100, 1).tiles[TICKS].value);
	}

	/**
	 * Every gap of the window over the trim: the value is their mean, "1,100 ms", BAD, "worst 1,100" (1,100 - 600 - 22
	 * is 478 corrected, over TICK_BAD_MS 400 since the first live look; the 1,000 of before is 378, only WARN).
	 */
	@Test
	public void tickTileWithEveryGapOverTheTrimIsTheMeanOfAll()
	{
		final PanelSnapshot p = snapshot(Trace.steady(100).ticksEvery(0, 99, 1100), 100, 1);
		assertTile(p.tiles[TICKS], Lane.TICKS, Level.BAD, "1,100 ms", "worst 1,100", NoData.NONE);
		assertEquals("1,100 ms", p.strips[TICKS].now);
	}

	/** The small line is the RAW deviation ("plus-minus 13 ms"); the level is on the CORRECTED one. */
	@Test
	public void tickSubIsTheRawDeviation()
	{
		// 50 fps, gaps of 587 .. 613.
		assertTile(snapshot(Trace.steady(100).tickLate(80, 13, true), 100, 1).tiles[TICKS], Lane.TICKS, Level.OK,
			"600 ms", PLUS_MINUS + "13 ms", NoData.NONE);
		// 80 ms off raw is 58 corrected (the 22 ms frame taken off): still OK. TICK_WARN_MS + 22 raw is TICK_WARN_MS
		// corrected: WARN, and one ms less is OK (200 since the first live look).
		assertTile(snapshot(Trace.steady(100).tickLate(80, 80, true), 100, 1).tiles[TICKS], Lane.TICKS, Level.OK,
			"600 ms", PLUS_MINUS + "80 ms", NoData.NONE);
		final int warnRaw = Thresholds.TICK_WARN_MS + 22;
		assertTile(snapshot(Trace.steady(100).tickLate(80, warnRaw - 1, true), 100, 1).tiles[TICKS], Lane.TICKS,
			Level.OK, "600 ms", PLUS_MINUS + (warnRaw - 1) + " ms", NoData.NONE);
		assertTile(snapshot(Trace.steady(100).tickLate(80, warnRaw, true), 100, 1).tiles[TICKS], Lane.TICKS,
			Level.WARN, "600 ms", PLUS_MINUS + warnRaw + " ms", NoData.NONE);
	}

	/** The level is the worse of heap AFTER collection and the pause; used heap near the top is no sign of trouble. */
	@Test
	public void memoryLevelUsesHeapAfterCollection()
	{
		// 740 of 768 MB used, 96 %, but 300 after the last collection: OK.
		assertTile(snapshot(Trace.steady(100).heap(0, 99, 740), 100, 1).tiles[MEMORY], Lane.MEMORY, Level.OK,
			"96 %", "pause 0 ms", NoData.NONE);
		// 720 after collection is 93 % of the limit: BAD, with 52 % used.
		assertTile(snapshot(Trace.steady(100).gcInferred(90, 720), 100, 1).tiles[MEMORY], Lane.MEMORY, Level.BAD,
			"52 %", "pause 0 ms", NoData.NONE);
		// 653 is 85 %: WARN; 652 is 84 %: OK.
		assertEquals(Level.WARN, snapshot(Trace.steady(100).gcInferred(90, 653), 100, 1).tiles[MEMORY].level);
		assertEquals(Level.OK, snapshot(Trace.steady(100).gcInferred(90, 652), 100, 1).tiles[MEMORY].level);

		// The lane's columns take the same lines: 300 is OK up to the collection at second 90, 720 BAD from it on.
		final PanelSnapshot bad = snapshot(Trace.steady(100).gcInferred(90, 720), 100, 1);
		for (int c = 0; c < COLUMNS; c++)
		{
			final boolean after = columnStartMs(bad, c + 1) - 1 >= 90_000;
			assertEquals("column " + c, after ? 720 : 300, bad.strips[MEMORY].values[c]);
			assertEquals("column " + c, after ? BAD : OK, bad.strips[MEMORY].levels[c]);
		}
		final Strip warn = snapshot(Trace.steady(100).gcInferred(95, 653), 100, 1).strips[MEMORY];
		assertEquals(WARN, warn.levels[LAST_COLUMN]);
		assertEquals(OK, warn.levels[0]);
	}

	/** A collection and a pause after the newest second are not seen, not even one at the next second's first ms. */
	@Test
	public void memoryTileReadsEndWithTheNewestSecond()
	{
		// The checked case of 3.3 on its own: "pause 0 ms" and a heap after of 300.
		final PanelSnapshot checked = snapshot(Trace.steady(100).gcPause(80, 0, 150, 400), 80, 1);
		assertEquals("pause 0 ms", checked.tiles[MEMORY].sub);
		assertEquals(300, checked.strips[MEMORY].values[LAST_COLUMN]);
		assertEquals("39 %", checked.strips[MEMORY].now);

		final Trace t = Trace.steady(100).gcPause(80, 0, 150, 400).gcInferred(90, 700);
		final Session s = t.build();
		assertEquals("precondition: the last row written looks past every second", 700, s.gcs.lastHeapAfterMb());

		// Newest second 79: the pause starts at 80,000, the first ms of second 80.
		final PanelSnapshot at80 = build(s, t.settings(), 80, 1);
		assertTile(at80.tiles[MEMORY], Lane.MEMORY, Level.OK, "52 %", "pause 0 ms", NoData.NONE);
		assertEquals("heap after the baseline collection, 300", "39 %", at80.strips[MEMORY].now);
		assertEquals(300, at80.strips[MEMORY].values[LAST_COLUMN]);

		// Newest second 80: the pause and its heap after, 400, are seen.
		final PanelSnapshot at81 = build(s, t.settings(), 81, 1);
		assertTile(at81.tiles[MEMORY], Lane.MEMORY, Level.WARN, "52 %", "pause 150 ms", NoData.NONE);
		assertEquals("52 %", at81.strips[MEMORY].now);
		assertEquals(400, at81.strips[MEMORY].values[LAST_COLUMN]);

		// The collection at second 90 is not seen at newest second 84, and seen at 90.
		assertEquals("52 %", build(s, t.settings(), 85, 1).strips[MEMORY].now);
		final PanelSnapshot at91 = build(s, t.settings(), 91, 1);
		assertEquals("91 %", at91.strips[MEMORY].now);
		assertEquals(Level.WARN, at91.strips[MEMORY].nowLevel);
		assertEquals(Level.WARN, at91.tiles[MEMORY].level);
	}

	/**
	 * The four tiles show their numbers from the login, past the login's masked ticks. (There is no warm-up since the
	 * first live look, WARMUP_S 0; the tiles never read it.)
	 */
	@Test
	public void tilesShowNumbersFromTheLogin()
	{
		final Trace t = Trace.steady(200).loginAt(30);
		final Session s = t.build();
		assertTrue("precondition: no warm-up since the first live look", s.warm(50));

		final PanelSnapshot p = build(s, t.settings(), 50, 1);
		assertTile(p.tiles[FPS], Lane.FRAME_RATE, Level.OK, "50 fps", "worst 22 ms", NoData.NONE);
		assertTile(p.tiles[TICKS], Lane.TICKS, Level.OK, "600 ms", PLUS_MINUS + "0 ms", NoData.NONE);
		assertTile(p.tiles[PING], Lane.PING, Level.OK, "40 ms", "", NoData.NONE);
		assertTile(p.tiles[MEMORY], Lane.MEMORY, Level.OK, "52 %", "pause 0 ms", NoData.NONE);
		for (Tile tile : p.tiles)
		{
			assertNotEquals(NoData.WARMING_UP, tile.noData);
		}
		assertEquals("50 fps", p.strips[FPS].now);
		assertEquals(416, p.world);
	}

	// ---------------------------------------------------------------- the strips

	@Test
	public void stripsAreFiveInLaneOrder()
	{
		final PanelSnapshot p = snapshot(Trace.steady(100), 100, 1);
		assertEquals(Lane.values().length, p.strips.length);
		for (int i = 0; i < p.strips.length; i++)
		{
			final Strip s = p.strips[i];
			assertEquals(Lane.values()[i], s.lane);
			assertEquals(COLUMNS, s.values.length);
			assertEquals(COLUMNS, s.levels.length);
			if (s.lane == Lane.CPU)
			{
				assertEquals(COLUMNS, s.values2.length);
			}
			else
			{
				assertNull(s.lane + " has no second series", s.values2);
				assertEquals("", s.now2);
			}
		}
		for (int i = 0; i < Lane.TILES; i++)
		{
			assertEquals(Lane.values()[i], p.tiles[i].lane);
		}
	}

	/** A steady trace: the PC line 20 in every column, the game line 40, "PC 20 %" and "Game 40 %", scale 0..100. */
	@Test
	public void cpuLaneIsWholePcWithAGameLine()
	{
		final Trace t = Trace.steady(3600);
		final Session s = t.build();
		for (int minutes : new int[] {1, 10, 60})
		{
			final PanelSnapshot p = build(s, t.settings(), 3600, minutes);
			final Strip cpu = p.strips[CPU];
			assertEquals(Lane.CPU, cpu.lane);
			assertNotNull(cpu.values2);
			for (int c = 0; c < COLUMNS; c++)
			{
				assertEquals(minutes + " min, column " + c, 20, cpu.values[c]);
				assertEquals(minutes + " min, column " + c, 40, cpu.values2[c]);
				assertEquals(minutes + " min, column " + c, OK, cpu.levels[c]);
			}
			assertEquals("PC 20 %", cpu.now);
			assertEquals(Level.OK, cpu.nowLevel);
			assertEquals("Game 40 %", cpu.now2);
			assertEquals(0, cpu.min);
			assertEquals(100, cpu.max);
			assertEquals(20, p.sysCpuPct);
			assertEquals(40, p.gameBusyPct);
		}
	}

	/** Each series takes the HIGHEST of its slice, on its own: the PC's and the game's may come from other seconds. */
	@Test
	public void cpuLaneHoldsTheHighestOfEachSlice()
	{
		final PanelSnapshot p = snapshot(Trace.steady(600).cpu(299, 299, 90, 30).cpu(300, 300, 50, 95), 600, 10);
		final Strip cpu = p.strips[CPU];
		final int c = firstColumnOf(p, 299);
		assertEquals("precondition: seconds 299 and 300 share a column at 10 min", c, lastColumnOf(p, 300));
		assertEquals("the highest PC, of second 299", 90, cpu.values[c]);
		assertEquals("the highest game share, of second 300", 95, cpu.values2[c]);
		assertEquals("PC 90 %", WARN, cpu.levels[c]);
		for (int k = 0; k < COLUMNS; k++)
		{
			if (k != c)
			{
				assertEquals("column " + k, 20, cpu.values[k]);
				assertEquals("column " + k, 40, cpu.values2[k]);
			}
		}
	}

	/** A Runtime-only PC: both series empty in every column (the game series an array, not null), no values. */
	@Test
	public void cpuLaneIsEmptyWithoutCpuData()
	{
		final Trace t = Trace.steady(100).cpuUnknown().busyUnknown().settings(runtime());
		for (int minutes : new int[] {1, 10})
		{
			final PanelSnapshot p = snapshot(t, 100, minutes);
			final Strip cpu = p.strips[CPU];
			assertNotNull("the game series is an array, not null", cpu.values2);
			for (int c = 0; c < COLUMNS; c++)
			{
				assertEquals(NONE, cpu.values[c]);
				assertEquals(NONE, cpu.values2[c]);
				assertEquals(NO_DATA, cpu.levels[c]);
			}
			assertEquals("", cpu.now);
			assertEquals("", cpu.now2);
			assertEquals(Level.NO_DATA, cpu.nowLevel);
			assertEquals(-1, p.sysCpuPct);
			assertEquals(-1, p.gameBusyPct);
			assertEquals("logged in: the frame rate lane is drawn", 50, p.strips[FPS].values[LAST_COLUMN]);
			assertEquals(416, p.world);
		}
	}

	/** The CPU lane's level is the whole PC's: PC 96 with game 10 is BAD, PC 20 with game 100 is OK. */
	@Test
	public void cpuLevelFollowsThePc()
	{
		final Strip busyPc = snapshot(Trace.steady(100).cpu(0, 99, 96, 10), 100, 1).strips[CPU];
		assertEveryLevel(busyPc, BAD);
		assertEquals(Level.BAD, busyPc.nowLevel);
		assertEquals("PC 96 %", busyPc.now);
		assertEquals("Game 10 %", busyPc.now2);

		final Strip busyGame = snapshot(Trace.steady(100).cpu(0, 99, 20, 100), 100, 1).strips[CPU];
		assertEveryLevel(busyGame, OK);
		assertEquals(Level.OK, busyGame.nowLevel);
		assertEquals("Game 100 %", busyGame.now2);

		assertEveryLevel(snapshot(Trace.steady(100).cpu(0, 99, 85, 10), 100, 1).strips[CPU], WARN);
		assertEveryLevel(snapshot(Trace.steady(100).cpu(0, 99, 84, 10), 100, 1).strips[CPU], OK);
	}

	/** A 3 s dip at 60 min: one column of 16.9 s holds it, and it keeps the lowest rate and the highest RTT. */
	@Test
	public void columnKeepsTheWorstValue()
	{
		final PanelSnapshot p = snapshot(Trace.steady(3600).fps(1800, 1802, 10).rtt(1801, 1801, 300), 3600, 60);
		final int c = firstColumnOf(p, 1800);
		assertEquals("precondition: the dip lies in one column", c, lastColumnOf(p, 1802));
		assertEquals(10, p.strips[FPS].values[c]);
		assertEquals(BAD, p.strips[FPS].levels[c]);
		assertEquals(300, p.strips[PING].values[c]);
		assertEquals(BAD, p.strips[PING].levels[c]);
		for (int k = 0; k < COLUMNS; k++)
		{
			assertEquals("the ticks were steady, column " + k, 600, p.strips[TICKS].values[k]);
			if (k != c)
			{
				assertEquals("column " + k, 50, p.strips[FPS].values[k]);
				assertEquals("column " + k, 40, p.strips[PING].values[k]);
			}
		}
	}

	/**
	 * The range ends at the end of the newest second, {@code wallMsOf(last + 1)}, and spans its minutes - or, while the
	 * session is shorter than that, starts at the session's first second (the stretch of the first live look).
	 */
	@Test
	public void rangeOf1And10And60()
	{
		final Trace t = Trace.steady(100);
		final Session s = t.build();
		for (int minutes : new int[] {1, 10, 60})
		{
			final PanelSnapshot now = build(s, t.settings(), 100, minutes);
			assertEquals(minutes, now.rangeMinutes);
			assertEquals(s.wallMsOf(100), now.rangeEndWallMs);
			final boolean shorter = 100 < minutes * 60;
			assertEquals(shorter ? s.wallMsOf(0) : now.rangeEndWallMs - minutes * 60_000L, now.rangeStartWallMs);
			assertEquals(shorter, now.stretched());
			assertEquals("a replay ends at its own newest second", s.wallMsOf(50),
				build(s, t.settings(), 50, minutes).rangeEndWallMs);
			assertEquals("a clock that ran past the rings ends at the newest second", s.wallMsOf(100),
				build(s, t.settings(), 130, minutes).rangeEndWallMs);
			final PanelSnapshot before = build(s, t.settings(), 0, minutes);
			assertEquals("no second yet", s.wallMsOf(0), before.rangeEndWallMs);
			for (Strip strip : before.strips)
			{
				assertEveryValue(strip, NONE);
			}
		}
	}

	/**
	 * A session shorter than the range: the graph stretches the data held, from the session's first second to now,
	 * over the full width, and no column before the newest tick is empty (changes after the first live look).
	 */
	@Test
	public void shortSessionStretchesOverTheFullWidth()
	{
		for (int minutes : new int[] {10, 60})
		{
			final PanelSnapshot p = snapshot(Trace.steady(120), 120, minutes);
			assertTrue(p.stretched());
			assertEquals("the span starts at the session's first second", p.sessionStartWallMs, p.rangeStartWallMs);
			assertEquals(0, columnOfMs(p, 0));
			for (int c = 0; c < COLUMNS; c++)
			{
				assertEquals(minutes + " min, column " + c, 50, p.strips[FPS].values[c]);
				assertEquals(minutes + " min, column " + c + " (the last tick is at 119,400)",
					columnStartMs(p, c) > 119_400 ? NONE : 600, p.strips[TICKS].values[c]);
				assertEquals(minutes + " min, column " + c, 40, p.strips[PING].values[c]);
				assertEquals(minutes + " min, column " + c, 300, p.strips[MEMORY].values[c]);
				assertEquals(minutes + " min, column " + c, 20, p.strips[CPU].values[c]);
				assertEquals(minutes + " min, column " + c, 40, p.strips[CPU].values2[c]);
			}
		}
	}

	/**
	 * At 60 min, every column before a login at second 600 is empty in all five lanes. The login's masked seconds
	 * (600 .. 608, its first LOGIN_MASK_TICKS ticks) are not drawn in the frame rate lane either: a column with no
	 * unmasked second is a gap there (changes after the first live look).
	 */
	@Test
	public void beforeTheLoginTheLanesAreEmpty()
	{
		final PanelSnapshot p = snapshot(Trace.steady(3600).loginAt(600), 3600, 60);
		final long maskEndMs = 600_000 + Thresholds.LOGIN_MASK_TICKS * Thresholds.TICK_MS;
		int before = 0;
		for (int c = 0; c < COLUMNS; c++)
		{
			if (columnStartMs(p, c + 1) <= 600_000)
			{
				assertEmptyColumn(p, c);
				before++;
			}
			else
			{
				assertEquals("column " + c, columnStartMs(p, c + 1) <= maskEndMs ? NONE : 50,
					p.strips[FPS].values[c]);
				assertEquals("column " + c, 300, p.strips[MEMORY].values[c]);
			}
		}
		assertTrue("precondition: some columns lie before the login", before > 30);
	}

	/**
	 * At 1 min a late tick's gap of 952 ms stands as a plateau over every column it spans. A column's level is the
	 * largest CORRECTED deviation among its ticks, so the catch-up tick after it, 352 ms early, colours its own columns
	 * too, while their value stays the largest gap.
	 */
	@Test
	public void aLateTickIsAsWideAsItsGap()
	{
		// The first tick of second 80, due at 80,400, comes at 80,752: 952 ms after the one at 79,800. The next one
		// keeps its time, 81,000: a gap of 248 ms.
		final PanelSnapshot p = snapshot(Trace.steady(100).tickLate(80, 352, true), 100, 1);
		final int from = columnOfMs(p, 79_800);
		final int to = columnOfMs(p, 80_752);
		final int caughtUp = columnOfMs(p, 81_000);
		assertTrue("precondition: 952 ms spans four columns of 282 ms", to - from >= 3);
		final Strip ticks = p.strips[TICKS];
		assertEquals("the tick of the running second has not arrived: nothing spans the last column", NONE,
			ticks.values[LAST_COLUMN]);
		for (int c = 0; c < COLUMNS; c++)
		{
			if (columnStartMs(p, c) > 99_600)
			{
				assertEquals("column " + c + " lies after the last tick, 99,600", NONE, ticks.values[c]);
				assertEquals("column " + c, NO_DATA, ticks.levels[c]);
				continue;
			}
			final boolean late = c >= from && c <= to;
			final boolean early = c >= to && c <= caughtUp;
			assertEquals("column " + c, late ? 952 : 600, ticks.values[c]);
			assertEquals("column " + c + ": 952 - 600 - 22 and 600 - 248 - 22 are both 330 corrected, WARN since the"
				+ " first live look (TICK_WARN_MS 200, TICK_BAD_MS 400)", late || early ? WARN : OK, ticks.levels[c]);
		}
	}

	/** Only a tick that arrived BEFORE the range's end is drawn: a replay reads as live did (gap G4). */
	@Test
	public void aTickAfterTheRangesEndIsNotDrawn()
	{
		// The tick due at 60,000 comes at 61,500: a gap of 2,100 ms over 59,400 .. 61,500.
		final Trace t = Trace.steady(100).tickLate(60, 1500, false);
		final Session s = t.build();
		for (int minutes : new int[] {1, 10, 60})
		{
			final PanelSnapshot replay = build(s, t.settings(), 60, minutes);
			for (int c = 0; c < COLUMNS; c++)
			{
				assertTrue(minutes + " min, column " + c, replay.strips[TICKS].values[c] <= 600);
			}
			assertEquals(Thresholds.STRIP_TICK_MAX_MS, replay.strips[TICKS].max);
			assertEquals("600 ms", replay.tiles[TICKS].value);
			assertEquals(PLUS_MINUS + "0 ms", replay.tiles[TICKS].sub);
		}

		final PanelSnapshot live = build(s, t.settings(), 100, 1);
		for (int c = columnOfMs(live, 59_400); c <= columnOfMs(live, 61_500); c++)
		{
			assertEquals("column " + c, 2100, live.strips[TICKS].values[c]);
		}
		assertEquals(2100 + Thresholds.STRIP_TICK_PAD_MS, live.strips[TICKS].max);
		assertEquals("worst 2,100", live.tiles[TICKS].sub);
	}

	/**
	 * The first tick after a lost connection spans the outage and carries LOGIN_MASK: the lane does not draw its gap
	 * and the tile does not count it. The columns only masked ticks span stay empty in the ticks lane; since the first
	 * live look the columns that lie wholly in the masked seconds (65 .. 73) are empty in the frame rate lane too.
	 */
	@Test
	public void aMaskedTickIsLeftOut()
	{
		// No tick in seconds 60 .. 64 and the connection lost in 64: the tick at 65,400 is 6,000 ms after the one at
		// 59,400, and it and the 14 after it (to 73,800) are masked.
		final Trace t = Trace.steady(100).noTicks(60, 64).disconnect(64);
		final Session s = t.build();
		boolean seen = false;
		for (long q = s.ticks.tail(); q <= s.ticks.head(); q++)
		{
			if (s.ticks.atMs(q) == 65_400)
			{
				assertEquals("precondition: the gap over the outage", 6000, s.ticks.gapMs(q));
				assertTrue("precondition: masked", Flags.has(s.ticks.flags(q), Flags.LOGIN_MASK));
				seen = true;
			}
		}
		assertTrue(seen);

		final PanelSnapshot p = build(s, t.settings(), 100, 1);
		for (int c = 0; c < COLUMNS; c++)
		{
			final boolean onlyMasked = columnStartMs(p, c) > 59_400 && columnStartMs(p, c + 1) - 1 < 73_800;
			final boolean inMaskedSeconds = columnStartMs(p, c) >= 65_000 && columnStartMs(p, c + 1) - 1 < 74_000;
			final boolean afterTheLastTick = columnStartMs(p, c) > 99_600;
			assertEquals("column " + c, onlyMasked || inMaskedSeconds || afterTheLastTick ? NONE : 600,
				p.strips[TICKS].values[c]);
			assertEquals("column " + c + " counts in the frame rate lane unless it is masked",
				inMaskedSeconds ? NONE : 50, p.strips[FPS].values[c]);
			assertEquals("column " + c + " counts in the ping lane", 40, p.strips[PING].values[c]);
		}
		assertTile(p.tiles[TICKS], Lane.TICKS, Level.OK, "600 ms", PLUS_MINUS + "0 ms", NoData.NONE);
	}

	/**
	 * A column with no counting second is empty in all five lanes, the ticks lane included: an unmasked tick whose gap
	 * reaches back over the login screen draws nothing there. No sampler writes such a tick (the first after a login
	 * is masked), so this session is written by hand.
	 */
	@Test
	public void aColumnWithNoCountingSecondIsEmptyInTheTicksLaneToo()
	{
		final Session s = new Session(1_000_000_000L, 1_790_000_000_000L, Os.WINDOWS, ZoneOffset.UTC);
		final FrameSecond f = new FrameSecond();
		final HostSecond h = new HostSecond();
		for (int sec = 0; sec < 100; sec++)
		{
			final boolean in = sec >= 50;
			f.frames = 50;
			f.worstFrameMs = 22;
			f.worstFrameEndMs = 500;
			f.busyPm = 400;
			f.worstBusyPm = 300;
			f.state = in ? State.LOGGED_IN : State.LOGIN_SCREEN;
			f.flags = Flags.FOCUSED | (in ? 0 : Flags.NOT_LOGGED_IN);
			f.world = 416;
			s.seconds.putFrame(sec, f);
			h.rttMs = in ? 40 : -1;
			h.rttAgeS = in ? 0 : -1;
			h.conn = in ? NoData.NONE : NoData.NOT_LOGGED_IN;
			h.sentUnits = 900;
			h.heapUsedMb = 400;
			h.procCpuPct = 40;
			h.sysCpuPct = 20;
			s.seconds.putHost(sec, h);
		}
		// Ticks from 52,000 on, 600 apart; the first reaches 5,000 ms back, into the login screen, with no mask.
		final TickRow row = new TickRow();
		long previous = 47_000;
		for (long at = 52_000; at < 100_000; at += 600)
		{
			row.atMs = at;
			row.gapMs = (int) (at - previous);
			row.frameMs = 22;
			row.cycleJump = 30;
			row.rttMs = 40;
			row.flags = Flags.FOCUSED;
			s.ticks.put(row);
			previous = at;
		}
		s.loggedInSince(50);

		final PanelSnapshot p = build(s, settings(Renderer.CPU, 768, MemorySource.MANAGEMENT), 100, 1);
		int empty = 0;
		for (int c = 0; c < COLUMNS; c++)
		{
			if (columnStartMs(p, c + 1) <= 50_000)
			{
				assertEmptyColumn(p, c);
				empty++;
			}
			else if (columnStartMs(p, c) <= 52_000)
			{
				assertEquals("column " + c + ": logged in, and spanned by the long gap", 5000,
					p.strips[TICKS].values[c]);
			}
		}
		assertTrue("precondition: the login screen fills columns", empty > 30);
	}

	/** The memory lane plots heap AFTER collection: used heap swinging 400 .. 700, 300 after each, reads 300 and OK. */
	@Test
	public void memoryLaneIsHeapAfterCollection()
	{
		final Trace t = Trace.steady(600);
		for (int k = 0; k < 600; k++)
		{
			t.heap(k, k, 400 + (k % 16) * 20);
			if (k % 16 == 0)
			{
				t.gcInferred(k, 300);
			}
		}
		final PanelSnapshot p = snapshot(t, 600, 10);
		final Strip memory = p.strips[MEMORY];
		for (int c = 0; c < COLUMNS; c++)
		{
			assertEquals("column " + c, 300, memory.values[c]);
			assertEquals("column " + c, OK, memory.levels[c]);
		}
		assertEquals(0, memory.min);
		assertEquals(768, memory.max);
		assertEquals("39 %", memory.now);
		// Used heap stays on the tile: second 599 uses 400 + 7 x 20 = 540 of 768.
		assertEquals("70 %", p.tiles[MEMORY].value);
	}

	/** Before the first collection the memory lane is empty; the other lanes are drawn there. */
	@Test
	public void memoryLaneIsEmptyBeforeTheFirstCollection()
	{
		final Trace t = Trace.steady(120).noBaselineCollection().gcInferred(60, 300);
		final PanelSnapshot p = snapshot(t, 120, 10);
		for (int c = 0; c < COLUMNS; c++)
		{
			final long lastMs = columnStartMs(p, c + 1) - 1;
			if (lastMs < 0)
			{
				assertEmptyColumn(p, c);
			}
			else if (lastMs < 60_000)
			{
				assertEquals("column " + c, NONE, p.strips[MEMORY].values[c]);
				assertEquals("column " + c, NO_DATA, p.strips[MEMORY].levels[c]);
				assertEquals("column " + c, 50, p.strips[FPS].values[c]);
			}
			else
			{
				assertEquals("column " + c, 300, p.strips[MEMORY].values[c]);
			}
		}

		// Newest second 59: no collection yet. The tile has its number, at the pause's level; the lane no value.
		final PanelSnapshot early = snapshot(t, 60, 1);
		assertTile(early.tiles[MEMORY], Lane.MEMORY, Level.OK, "52 %", "pause 0 ms", NoData.NONE);
		assertEquals("", early.strips[MEMORY].now);
		assertEquals(Level.NO_DATA, early.strips[MEMORY].nowLevel);
		assertEveryValue(early.strips[MEMORY], NONE);
	}

	/** The scales of 5.3: fps, ticks and ping grow with their data and the cap; memory is the limit; CPU is fixed. */
	@Test
	public void scalesFollowTheirRules()
	{
		final PanelSnapshot steady = snapshot(Trace.steady(100), 100, 1);
		assertScale(steady.strips[FPS], 0, Thresholds.STRIP_FPS_MAX);
		assertScale(steady.strips[TICKS], Thresholds.STRIP_TICK_MIN_MS, Thresholds.STRIP_TICK_MAX_MS);
		assertScale(steady.strips[PING], 0, Thresholds.STRIP_PING_MAX_MS);
		assertScale(steady.strips[MEMORY], 0, 768);
		assertScale(steady.strips[CPU], 0, Thresholds.STRIP_CPU_MAX_PCT);

		// fps: the highest column drawn, and the cap of the newest second's focus.
		assertEquals(90, snapshot(Trace.steady(100).fps(95, 99, 90), 100, 1).strips[FPS].max);
		final SettingsView limits = unfocusedLimit(Renderer.GPU, 100, 144);
		assertEquals(CapSource.GPU_TARGET, limits.capSource(true));
		assertEquals(CapSource.FPS_CONTROL_UNFOCUSED, limits.capSource(false));
		assertEquals(144, snapshot(Trace.steady(100).settings(limits), 100, 1).strips[FPS].max);
		assertEquals(100, snapshot(Trace.steady(100).unfocused(99, 99).settings(limits), 100, 1).strips[FPS].max);

		// ticks: the longest gap drawn plus STRIP_TICK_PAD_MS.
		assertEquals(2400 + Thresholds.STRIP_TICK_PAD_MS, snapshot(Trace.steady(100).noTicks(70, 71), 100, 1)
			.strips[TICKS].max);

		// ping: at least STRIP_PING_PAD_PCT % of the highest RTT, rounded up.
		assertEquals(360, snapshot(Trace.steady(100).rtt(90, 90, 300), 100, 1).strips[PING].max);
		assertEquals(122, snapshot(Trace.steady(100).rtt(90, 90, 101), 100, 1).strips[PING].max);
	}

	// ---------------------------------------------------------------- the values of now

	/** With no event selected the frame rate, ticks and ping lanes print their tiles' values, at their levels. */
	@Test
	public void laneValuesAreTheTilesValues()
	{
		final PanelSnapshot p = snapshot(Trace.steady(100), 100, 1);
		assertLaneValue(p.strips[FPS], "50 fps", Level.OK, p.tiles[FPS]);
		assertLaneValue(p.strips[TICKS], "600 ms", Level.OK, p.tiles[TICKS]);
		assertLaneValue(p.strips[PING], "40 ms", Level.OK, p.tiles[PING]);
		assertEquals("heap after collection, 300 of 768", "39 %", p.strips[MEMORY].now);
		assertEquals(Level.OK, p.strips[MEMORY].nowLevel);
		assertEquals("PC 20 %", p.strips[CPU].now);
		assertEquals("Game 40 %", p.strips[CPU].now2);

		// The same when they are not OK.
		final PanelSnapshot bad = snapshot(Trace.steady(100).fps(95, 99, 20).rtt(95, 99, 200).gcInferred(90, 660),
			100, 1);
		assertLaneValue(bad.strips[FPS], "20 fps", Level.BAD, bad.tiles[FPS]);
		assertLaneValue(bad.strips[PING], "200 ms", Level.BAD, bad.tiles[PING]);
		assertEquals("85 %", bad.strips[MEMORY].now);
		assertEquals(Level.WARN, bad.strips[MEMORY].nowLevel);
		final PanelSnapshot gap = snapshot(Trace.steady(100).noTicks(70, 71), 100, 1);
		assertLaneValue(gap.strips[TICKS], "600 ms", Level.BAD, gap.tiles[TICKS]);
	}

	/** No value now is "" at NO_DATA: while not logged in in every lane, else in the lane that has none. */
	@Test
	public void noValueNowIsEmpty()
	{
		final PanelSnapshot[] out = {
			snapshot(Trace.steady(100).loginAt(50), 40, 1),
			snapshot(Trace.steady(100).hop(99, 302), 100, 1),
			snapshot(Trace.steady(100), 0, 1)};
		for (PanelSnapshot p : out)
		{
			for (Strip s : p.strips)
			{
				assertEquals(s.lane.label(), "", s.now);
				assertEquals(s.lane.label(), Level.NO_DATA, s.nowLevel);
				assertEquals(s.lane.label(), "", s.now2);
			}
			assertEquals(-1, p.sysCpuPct);
			assertEquals(-1, p.gameBusyPct);
			assertEquals(0, p.world);
		}

		final PanelSnapshot stale = snapshot(Trace.steady(100).rttStale(95, 99), 100, 1);
		assertEquals("", stale.strips[PING].now);
		assertEquals(Level.NO_DATA, stale.strips[PING].nowLevel);
		assertEquals("50 fps", stale.strips[FPS].now);

		final PanelSnapshot noCollection = snapshot(Trace.steady(100).noBaselineCollection(), 100, 1);
		assertEquals("", noCollection.strips[MEMORY].now);
		assertEquals(Level.NO_DATA, noCollection.strips[MEMORY].nowLevel);
		assertEquals("52 %", noCollection.tiles[MEMORY].value);

		assertEquals("frames stopped", "", snapshot(Trace.steady(100), 103, 1).strips[FPS].now);
		assertEquals("no ticks", "", snapshot(Trace.steady(100).noTicks(40, 99), 100, 1).strips[TICKS].now);
	}

	/** The newest second's world while logged in; 0 while not, whatever the world column holds. */
	@Test
	public void worldIsTheNewestSecondsAndZeroWhileNotLoggedIn()
	{
		final Trace hop = Trace.steady(200).hop(100, 302);
		final Session s = hop.build();
		assertEquals("precondition: the hop's second holds the new world", 302, s.seconds.world(100));
		assertEquals(416, build(s, hop.settings(), 100, 1).world);
		assertEquals(0, build(s, hop.settings(), 101, 1).world);
		assertEquals(302, build(s, hop.settings(), 102, 1).world);
		assertEquals(0, snapshot(Trace.steady(100).loginAt(30), 20, 1).world);
		assertEquals(0, snapshot(Trace.steady(100), 0, 1).world);
	}

	/** The CPU of now is the newest second's: the PC's share and the game's through {@link Fmt#busyPct}. */
	@Test
	public void theCpuOfNowIsTheNewestSeconds()
	{
		final PanelSnapshot p = snapshot(Trace.steady(100).cpu(99, 99, 37, 95), 100, 1);
		assertEquals(37, p.sysCpuPct);
		assertEquals(95, p.gameBusyPct);
		assertEquals("PC 37 %", p.strips[CPU].now);
		assertEquals("Game 95 %", p.strips[CPU].now2);
		final PanelSnapshot earlier = snapshot(Trace.steady(100).cpu(99, 99, 37, 95), 99, 1);
		assertEquals(20, earlier.sysCpuPct);
		assertEquals(40, earlier.gameBusyPct);
	}

	// ---------------------------------------------------------------- events and the rest

	/** The range's CLOSED events, oldest first; the session's closed events; an open event is in neither. */
	@Test
	public void rangeEventsAreTheClosedEventsOfTheRange()
	{
		final Trace t = Trace.steady(700);
		final Session s = t.build();
		s.events.add(closed(s, 0, 10, 15, Cause.SLOW_WORLD));
		s.events.add(closed(s, 1, 95, 101, Cause.GC_PAUSE));
		s.events.add(closed(s, 2, 640, 650, Cause.UPLOAD_LOSS));
		s.events.add(open(s, 3, 690, 695));

		// 10 min: seconds 100 .. 699. Event 1 straddles the start; event 0 lies before it.
		final PanelSnapshot ten = build(s, t.settings(), 700, 10);
		assertIds(ten.rangeEvents, 1, 2);
		assertIds(ten.sessionEvents, 0, 1, 2);
		assertEquals(3, ten.sessionTotal);
		assertIds(build(s, t.settings(), 700, 1).rangeEvents, 2);
		assertIds(build(s, t.settings(), 700, 60).rangeEvents, 0, 1, 2);
		assertEquals(1, ten.sessionCounts[Group.WORLD.ordinal()]);
		assertEquals(1, ten.sessionCounts[Group.MEMORY.ordinal()]);
		assertEquals(1, ten.sessionCounts[Group.CONNECTION.ordinal()]);
	}

	/** The log holds 500; the session counts and the total keep every closed event, dropped ones included. */
	@Test
	public void sessionCountsSurviveTheCap()
	{
		final Trace t = Trace.steady(100);
		final Session s = t.build();
		final Cause[] causes = {Cause.UPLOAD_LOSS, Cause.CLIENT_BUSY, Cause.GC_PAUSE, Cause.SLOW_WORLD, Cause.NOT_SURE};
		for (int i = 0; i < 505; i++)
		{
			s.events.add(closed(s, i, 10, 10, causes[i % causes.length]));
		}
		final PanelSnapshot p = build(s, t.settings(), 100, 1);
		assertEquals(505, p.sessionTotal);
		assertEquals(Thresholds.EVENTS, p.sessionEvents.size());
		assertEquals("oldest first, the five oldest dropped", 5, p.sessionEvents.get(0).id);
		assertEquals(504, p.sessionEvents.get(Thresholds.EVENTS - 1).id);
		assertEquals(Group.values().length, p.sessionCounts.length);
		int sum = 0;
		for (Group g : Group.values())
		{
			final int expected = g == Group.NONE ? 0 : 101;
			assertEquals(g.label(), expected, p.sessionCounts[g.ordinal()]);
			sum += p.sessionCounts[g.ordinal()];
		}
		assertEquals(505, sum);
	}

	/** The snapshot's own fields: the clock, the zone, the card's verdict, the settings, the footer. */
	@Test
	public void snapshotCarriesWhatItWasGiven()
	{
		final Trace t = Trace.steady(100);
		final Session s = t.build();
		final SettingsView settings = t.settings();
		final PanelSnapshot p = BUILDER.build(s, SMOOTH, 10, 100, s.wallMsOf(100) + 345, settings, "self: step 40 us");
		assertEquals(s.wallMsOf(100) + 345, p.wallMs);
		assertEquals(ZoneOffset.UTC, p.zone);
		assertSame(SMOOTH, p.verdict);
		assertSame(settings, p.settings);
		assertEquals("self: step 40 us", p.footer);
		assertEquals(s.startWallMs, p.sessionStartWallMs);
		assertEquals(10, p.rangeMinutes);
		assertEquals("", BUILDER.build(s, SMOOTH, 10, 100, s.wallMsOf(100), settings, null).footer);
	}

	/** Choice: a null settings view is read as unknown settings on the RUNTIME source; nothing throws. */
	@Test
	public void aNullSettingsViewIsUnknown()
	{
		final Session s = Trace.steady(100).build();
		final PanelSnapshot p = BUILDER.build(s, SMOOTH, 1, 100, s.wallMsOf(100), null, "");
		assertNotNull(p.settings);
		assertEquals(Renderer.UNKNOWN, p.settings.renderer);
		assertEquals(MemorySource.RUNTIME, p.settings.memorySource);
		assertDash(p.tiles[MEMORY], Lane.MEMORY, NoData.ERROR);
		assertEquals("50 fps", p.tiles[FPS].value);
	}

	/** Choice: a range of less than one minute is built as one minute. */
	@Test
	public void aRangeUnderOneMinuteIsOneMinute()
	{
		final Trace t = Trace.steady(100);
		final Session s = t.build();
		for (int minutes : new int[] {0, -5})
		{
			final PanelSnapshot p = build(s, t.settings(), 100, minutes);
			assertEquals(1, p.rangeMinutes);
			assertEquals(60_000L, p.rangeEndWallMs - p.rangeStartWallMs);
			assertEquals(50, p.strips[FPS].values[0]);
		}
	}

	/** T16, the snapshot's part alone: the 60-minute snapshot of a full 3,600-second session, mean under 20 ms. */
	@Test
	public void fullSessionBuildsFast()
	{
		final Trace t = Trace.steady(3600).fps(1800, 1802, 10).rtt(2000, 2010, 250).usual(40);
		for (int k = 7; k < 3600; k += 14)
		{
			t.gcPause(k, 100, 20, 300 + k % 200);
		}
		final Session s = t.build();
		for (int i = 0; i < Thresholds.EVENTS; i++)
		{
			s.events.add(closed(s, i, 7 * i, 7 * i + 2, i % 2 == 0 ? Cause.CLIENT_BUSY : Cause.SLOW_WORLD));
		}
		final SettingsView settings = t.settings();
		for (int i = 0; i < 30; i++)
		{
			build(s, settings, 3600, 60);
		}
		final int runs = 100;
		PanelSnapshot p = null;
		final long start = System.nanoTime();
		for (int i = 0; i < runs; i++)
		{
			p = build(s, settings, 3600, 60);
		}
		final long meanNanos = (System.nanoTime() - start) / runs;
		System.out.println("SnapshotBuilder, 60-minute snapshot of a full session: mean " + meanNanos / 1_000 + " us");
		assertTrue("mean " + meanNanos / 1_000 + " us", meanNanos < T16_BOUND_MS * 1_000_000L);
		assertEquals(Thresholds.EVENTS, p.sessionEvents.size());
		assertEquals(10, p.strips[FPS].values[firstColumnOf(p, 1800)]);
		assertEquals(50, p.strips[FPS].values[0]);
	}

	// ---------------------------------------------------------------- helpers

	private static PanelSnapshot snapshot(Trace t, long nowSec, int minutes)
	{
		return build(t.build(), t.settings(), nowSec, minutes);
	}

	private static PanelSnapshot build(Session s, SettingsView settings, long nowSec, int minutes)
	{
		return BUILDER.build(s, SMOOTH, minutes, nowSec, s.wallMsOf(nowSec), settings, "");
	}

	/** A trace's default settings with another renderer, heap limit and memory source. */
	private static SettingsView settings(Renderer renderer, int heapMaxMb, MemorySource source)
	{
		return new SettingsView(renderer, false, false, 0, false, 0, false, "", 0, 0, "", 0, 60, heapMaxMb, source,
			Os.WINDOWS, "");
	}

	private static SettingsView runtime()
	{
		return settings(Renderer.CPU, 768, MemorySource.RUNTIME);
	}

	/** FPS Control on with only its unfocused limit, at {@code unfocusedFps}; a GPU renderer unlocked at a target. */
	private static SettingsView unfocusedLimit(Renderer renderer, int unfocusedFps, int gpuTarget)
	{
		return new SettingsView(renderer, true, false, 0, true, unfocusedFps, gpuTarget > 0, "OFF", gpuTarget, 50,
			"MSAA_2", 0, 60, 768, MemorySource.MANAGEMENT, Os.WINDOWS, "");
	}

	/** A closed event of seconds {@code from .. to}, judged with a verdict of {@code cause}. */
	private static LagEvent closed(Session s, long id, long from, long to, Cause cause)
	{
		final Verdict v = new Verdict(cause, Confidence.LIKELY, Level.BAD, "A lag", "It was measured.", "", "",
			s.wallMsOf(from), (int) (to - from + 1), 416, id, null, null);
		return new LagEvent(id, from, to, s.wallMsOf(from), Trigger.FRAME_GAP.bit(), Trigger.FRAME_GAP, 416, 0, 0, 0,
			50, 480, 600, 600, 0, 40, 40, -1, 900, 0, 0, 400, 768, 20, 40, false, false, v);
	}

	/** An event still open: in the log, listed nowhere. */
	private static LagEvent open(Session s, long id, long from, long to)
	{
		return new LagEvent(id, from, to, s.wallMsOf(from), Trigger.FRAME_GAP.bit(), Trigger.FRAME_GAP, 416, 0, 0, 0,
			50, 480, 600, 600, 0, 40, 40, -1, 900, 0, 0, 400, 768, 20, 40, true, false, null);
	}

	/** The range of {@code p} in session ms: {@code [start, end)}. */
	private static long startMs(PanelSnapshot p)
	{
		return p.rangeStartWallMs - p.sessionStartWallMs;
	}

	private static long endMs(PanelSnapshot p)
	{
		return p.rangeEndWallMs - p.sessionStartWallMs;
	}

	/** The first session ms of column {@code c} (0 .. 213) of the snapshot's range. */
	private static long columnStartMs(PanelSnapshot p, int c)
	{
		return Strip.columnStart(startMs(p), endMs(p), c);
	}

	/** The column that holds session ms {@code t}. */
	private static int columnOfMs(PanelSnapshot p, long t)
	{
		return Strip.columnOf(startMs(p), endMs(p), t);
	}

	private static int firstColumnOf(PanelSnapshot p, long sec)
	{
		return columnOfMs(p, sec * 1000);
	}

	private static int lastColumnOf(PanelSnapshot p, long sec)
	{
		return columnOfMs(p, sec * 1000 + 999);
	}

	private static void assertTile(Tile t, Lane lane, Level level, String value, String sub, NoData noData)
	{
		assertEquals(lane, t.lane);
		assertEquals(lane.label() + " value", value, t.value);
		assertEquals(lane.label() + " sub", sub, t.sub);
		assertEquals(lane.label() + " level", level, t.level);
		assertEquals(lane.label() + " no data", noData, t.noData);
	}

	private static void assertDash(Tile t, Lane lane, NoData why)
	{
		assertTile(t, lane, Level.NO_DATA, "-", why.reason(), why);
	}

	private static void assertMemoryHasANumber(String what, Trace t, long nowSec)
	{
		final Tile memory = snapshot(t, nowSec, 1).tiles[MEMORY];
		assertNotEquals(what, Level.NO_DATA, memory.level);
		assertNotEquals(what, "-", memory.value);
		assertEquals(what, NoData.NONE, memory.noData);
		assertTrue(what + ": " + memory.value, memory.value.endsWith(" %"));
	}

	private static void assertLaneValue(Strip s, String now, Level level, Tile tile)
	{
		assertEquals(s.lane.label(), now, s.now);
		assertEquals(s.lane.label(), level, s.nowLevel);
		assertEquals("the tile's value", tile.value, s.now);
		assertEquals("the tile's level", tile.level, s.nowLevel);
	}

	private static void assertEmptyColumn(PanelSnapshot p, int c)
	{
		for (Strip s : p.strips)
		{
			assertEquals(s.lane.label() + ", column " + c, NONE, s.values[c]);
			assertEquals(s.lane.label() + ", column " + c, NO_DATA, s.levels[c]);
			if (s.values2 != null)
			{
				assertEquals(s.lane.label() + ", column " + c, NONE, s.values2[c]);
			}
		}
	}

	private static void assertEveryValue(Strip s, int value)
	{
		for (int c = 0; c < COLUMNS; c++)
		{
			assertEquals(s.lane.label() + ", column " + c, value, s.values[c]);
		}
	}

	private static void assertEveryLevel(Strip s, byte level)
	{
		for (int c = 0; c < COLUMNS; c++)
		{
			assertEquals(s.lane.label() + ", column " + c, level, s.levels[c]);
		}
	}

	private static void assertScale(Strip s, int min, int max)
	{
		assertEquals(s.lane.label() + " min", min, s.min);
		assertEquals(s.lane.label() + " max", max, s.max);
	}

	private static void assertIds(List<LagEvent> events, long... ids)
	{
		final List<Long> got = new ArrayList<>();
		for (LagEvent e : events)
		{
			got.add(e.id);
		}
		final List<Long> want = new ArrayList<>();
		for (long id : ids)
		{
			want.add(id);
		}
		assertEquals(want, got);
	}
}
