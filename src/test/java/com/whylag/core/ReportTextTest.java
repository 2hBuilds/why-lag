package com.whylag.core;

import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * The text of "Copy report" (contract 6.6; lot L5): the picture's report line for line; one line per session event,
 * newest first; the report with no events; plain ASCII whatever the snapshot holds; a dash for a tile with no value;
 * nothing but the five kinds of line, so no player, no address and no plugin list; the "Now:" line ending with the
 * whole PC's CPU; every corner of 6.6; and the corners this lot chose (its "Choice:" lines).
 */
public class ReportTextTest
{
	/** Second 0 of every snapshot here: 2026-09-28 20:52:00 UTC. */
	private static final long START = Instant.parse("2026-09-28T20:52:00Z").toEpochMilli();
	private static final long MINUTE = 60_000L;

	private static final String NUMBER = "\\d{1,3}(,\\d{3})*";
	private static final String PRINTABLE = "[ -~]+";
	private static final String GROUP = "(connection|frame rate|memory|world|not sure)";
	/** The five kinds of line, each matched whole. */
	private static final Pattern HEADER = Pattern.compile("2h Why Lag report - (world " + NUMBER
		+ "|not logged in) - \\d\\d:\\d\\d to \\d\\d:\\d\\d \\(" + NUMBER + " min\\)");
	private static final Pattern NOW = Pattern.compile("Now: " + PRINTABLE + "\\. (fps -|" + NUMBER + " fps), ticks (-|"
		+ NUMBER + " ms), ping (-|" + NUMBER + " ms), memory (-|" + NUMBER + " %), CPU (-|\\d+ %)");
	private static final Pattern COUNTS = Pattern.compile(NUMBER + " lags? this session(: " + NUMBER + " " + GROUP
		+ "(, " + NUMBER + " " + GROUP + ")*)?");
	private static final Pattern EVENT = Pattern.compile("\\d\\d:\\d\\d:\\d\\d  " + NUMBER
		+ " s  (CONNECTION|FRAME RATE|MEMORY|WORLD|NOT SURE)  (Sure|Likely|Hint|Can't tell)(  " + PRINTABLE + ")?");
	private static final Pattern SETTINGS = Pattern.compile("Client " + PRINTABLE
		+ ", renderer (CPU|GPU|117 HD|unknown)(, draw distance " + NUMBER + ", " + PRINTABLE + ")?, cap (none|" + NUMBER
		+ " \\(" + PRINTABLE + "\\)), memory limit (unknown|" + NUMBER + " MB), memory source (management|runtime)");
	private static final Pattern IPV4 = Pattern.compile("\\b\\d{1,3}(\\.\\d{1,3}){3}\\b");

	/** Contract 6.6's report, every line of it. */
	@Test
	public void thePicturesReport()
	{
		assertEquals("2h Why Lag report - world 416 - 20:52 to 21:52 (60 min)\n"
			+ "Now: Smooth. 50 fps, ticks 600 ms, ping 41 ms, memory 51 %, CPU 37 %\n"
			+ "4 lags this session: 1 connection, 1 frame rate, 1 memory, 1 world\n"
			+ "21:47:30  14 s  WORLD  Likely  World 416 is struggling, not you. Ticks 600 to 900+ ms for 14 s."
			+ " Ping stayed 41 ms, 50 fps. Fix: Hop to a quieter world.\n"
			+ "21:33:05  2 s  MEMORY  Sure  Memory clean-up froze the game. A 340 ms pause. Memory 742 of 768 MB."
			+ " Fix: Close the world map. Restart if it repeats.\n"
			+ "21:20:40  1 s  FRAME RATE  Hint  The client itself stalled. A 480 ms freeze. Connection and world were"
			+ " fine. Fix: Turn plugins off one at a time.\n"
			+ "21:05:12  6 s  CONNECTION  Likely  Packets are being lost. 2 in 100 were re-sent. Ping can look fine."
			+ " Fix: If every world does it, check cable or Wi-Fi.\n"
			+ "Client 1.12.38, renderer GPU, draw distance 50, MSAA_2, cap 50 (the client), memory limit 768 MB,"
			+ " memory source management\n", new Snap().report());
	}

	/** One line per session event, newest first; its text is the headline, the proof and the fix. */
	@Test
	public void oneLinePerEventNewestFirst()
	{
		final String[] lines = lines(new Snap().report());
		assertEquals("the header, now, the counts, four events, the settings", 8, lines.length);
		assertTrue(lines[3].startsWith("21:47:30  14 s  WORLD  Likely  World 416 is struggling, not you. "));
		assertTrue(lines[4].startsWith("21:33:05  2 s  MEMORY  Sure  "));
		assertTrue(lines[5].startsWith("21:20:40  1 s  FRAME RATE  Hint  "));
		assertTrue(lines[6].startsWith("21:05:12  6 s  CONNECTION  Likely  "));

		// A verdict with no fix ends with its proof; "Can't tell" is the confidence word of X.
		final Snap s = new Snap();
		s.events = new ArrayList<>();
		s.events.add(event(0, 100, 3, verdict(Cause.NOT_SURE, Confidence.CANT_TELL, "Can't tell yet",
			"A 170 ms freeze. Its cause was not measured.", "Wait for it to happen again.")));
		s.events.add(event(1, 200, 4, verdict(Cause.DELIVERY_GAP, Confidence.CANT_TELL, "The game stopped answering",
			"No ticks for 1.8 s. Frames and ping were fine.", "")));
		final String[] two = lines(s.report());
		assertEquals(6, two.length);
		assertEquals("20:55:20  4 s  NOT SURE  Can't tell  The game stopped answering. No ticks for 1.8 s. Frames and"
			+ " ping were fine.", two[3]);
		assertEquals("20:53:40  3 s  NOT SURE  Can't tell  Can't tell yet. A 170 ms freeze. Its cause was not"
			+ " measured. Fix: Wait for it to happen again.", two[4]);

		// The log's whole 500: 500 lines, the newest first and the oldest last.
		final Snap full = new Snap();
		full.events = new ArrayList<>();
		for (int i = 0; i < Thresholds.EVENTS; i++)
		{
			full.events.add(event(i, 7L * i, 2, verdict(Cause.CLIENT_BUSY, Confidence.HINT, "The client itself stalled",
				"A 480 ms freeze. Connection and world were fine.", "Turn plugins off one at a time.")));
		}
		final String[] many = lines(full.report());
		assertEquals(Thresholds.EVENTS + 4, many.length);
		assertTrue(many[3].startsWith(Fmt.clockSeconds(START + 7_000L * (Thresholds.EVENTS - 1), ZoneOffset.UTC)));
		assertTrue(many[Thresholds.EVENTS + 2].startsWith("20:52:00  2 s  FRAME RATE  Hint  "));
	}

	/** No events this session: "0 lags this session" with no colon, and no event line. */
	@Test
	public void noEvents()
	{
		final Snap s = new Snap();
		s.events = Collections.emptyList();
		s.counts = counts(0, 0, 0, 0, 0);
		s.total = 0;
		s.verdict = verdict(Cause.ALL_CLEAR, Confidence.SURE, "Smooth", "No lag this session.", "");
		final String[] lines = lines(s.report());
		assertEquals(4, lines.length);
		assertEquals("Now: Smooth. 50 fps, ticks 600 ms, ping 41 ms, memory 51 %, CPU 37 %", lines[1]);
		assertEquals("0 lags this session", lines[2]);
		assertTrue(lines[3].startsWith("Client 1.12.38, "));
	}

	/** The world test is parked (2026-09-30): the report is the five kinds of line, no "World test" and no W line. */
	@Test
	public void theReportHasNoWorldTestLines()
	{
		final Snap s = new Snap();
		final String plain = s.report();
		assertFalse(plain.contains("World test"));
		assertEquals("the five kinds of line", 8, lines(plain).length);
		assertOnlyTheFiveKindsOfLine(plain, 4);
		for (String line : lines(plain))
		{
			assertFalse(line, line.matches("W[0-9]+  .*"));
		}
	}

	/** Plain ASCII: a character from outside that is not printable ASCII prints as '?', and never splits a line. */
	@Test
	public void asciiOnly()
	{
		assertAscii(new Snap().report());

		final Snap s = new Snap();
		s.settings = gpu("1.12.38-\u00e9\r\nx\t", 50, "MSAA\u00b12");
		s.verdict = verdict(Cause.SLOW_WORLD, Confidence.LIKELY, "Ticks 600 \u2192 900+ ms", "", "");
		final String report = s.report();
		assertAscii(report);
		final String[] lines = lines(report);
		assertEquals("a new line in a field is a '?', not a new line", 8, lines.length);
		assertEquals("Client 1.12.38-???x?, renderer GPU, draw distance 50, MSAA?2, cap 50 (the client), memory limit"
			+ " 768 MB, memory source management", lines[7]);
		assertTrue(lines[1].startsWith("Now: Ticks 600 ? 900+ ms. 50 fps, "));

		// The snapshot builder's own snapshot: its Server ticks tile's small line holds a plus-minus sign, which the
		// report never prints.
		final PanelSnapshot built = built();
		assertTrue("precondition", built.tiles[Lane.TICKS.ordinal()].sub.startsWith("\u00b1"));
		assertAscii(ReportText.of(built));
	}

	/** A tile with no value prints its word and "-"; not logged in, all four and the CPU do. */
	@Test
	public void noDataPrintsADash()
	{
		final Snap out = new Snap();
		out.world = 0;
		out.verdict = verdict(Cause.WARMING_UP, Confidence.CANT_TELL, "Not logged in", "Log in to start measuring.",
			"");
		out.tiles = new Tile[Lane.TILES];
		for (int i = 0; i < Lane.TILES; i++)
		{
			out.tiles[i] = dash(Lane.values()[i], NoData.NOT_LOGGED_IN);
		}
		out.sysCpuPct = -1;
		out.gameBusyPct = -1;
		assertEquals("Now: Not logged in. fps -, ticks -, ping -, memory -, CPU -", lines(out.report())[1]);

		final String[] expected = {
			"Now: Smooth. fps -, ticks 600 ms, ping 41 ms, memory 51 %, CPU 37 %",
			"Now: Smooth. 50 fps, ticks -, ping 41 ms, memory 51 %, CPU 37 %",
			"Now: Smooth. 50 fps, ticks 600 ms, ping -, memory 51 %, CPU 37 %",
			"Now: Smooth. 50 fps, ticks 600 ms, ping 41 ms, memory -, CPU 37 %"};
		for (int i = 0; i < Lane.TILES; i++)
		{
			final Snap s = new Snap();
			s.tiles[i] = dash(Lane.values()[i], NoData.STALE);
			assertEquals(expected[i], lines(s.report())[1]);
		}
	}

	/**
	 * The report is made of the five kinds of line and nothing else - so no player's name, no address and no plugin
	 * list can be in it - and the developer footer is not in it either.
	 */
	@Test
	public void neverNamesAPlayer()
	{
		final PanelSnapshot built = built();
		final String report = ReportText.of(built);
		assertOnlyTheFiveKindsOfLine(report, built.sessionEvents.size());
		assertOnlyTheFiveKindsOfLine(new Snap().report(), 4);
		assertFalse("the developer footer", report.contains(built.footer));
		assertFalse(report.contains("@"));
		assertFalse("an address", IPV4.matcher(report).find());
		final String lower = report.toLowerCase(Locale.ROOT);
		assertFalse(lower.contains("http"));
		assertFalse(lower.contains("player"));
		assertFalse(lower.contains("name"));
	}

	/** Line 2 ends with the whole PC's CPU, "CPU 37 %", or "CPU -"; the game's share is never in the report. */
	@Test
	public void nowLineEndsWithCpu()
	{
		final Snap s = new Snap();
		final String now = lines(s.report())[1];
		assertTrue(now, now.endsWith(", memory 51 %, CPU 37 %"));
		assertFalse("the game's share, 95 %, is not on the line", now.toLowerCase(Locale.ROOT).contains("game"));
		assertFalse(now.contains("95"));
		assertFalse("nor anywhere in the report", s.report().contains("Game") || s.report().contains("95 %"));

		s.sysCpuPct = -1;
		assertTrue(lines(s.report())[1].endsWith(", memory 51 %, CPU -"));
		s.sysCpuPct = 100;
		assertTrue(lines(s.report())[1].endsWith(", memory 51 %, CPU 100 %"));
	}

	/** Each corner of 6.6. */
	@Test
	public void reportCorners()
	{
		// " - not logged in" on line 1 at world 0.
		final Snap out = new Snap();
		out.world = 0;
		assertEquals("2h Why Lag report - not logged in - 20:52 to 21:52 (60 min)", lines(out.report())[0]);

		// "(0 min)" in the first minute; whole minutes, rounded down, after it.
		final Snap first = new Snap();
		first.wallMs = START + MINUTE - 1;
		assertEquals("2h Why Lag report - world 416 - 20:52 to 20:52 (0 min)", lines(first.report())[0]);
		first.wallMs = START + 2 * MINUTE - 1;
		assertEquals("2h Why Lag report - world 416 - 20:52 to 20:53 (1 min)", lines(first.report())[0]);
		first.wallMs = START + 24 * 60 * MINUTE;
		assertEquals("2h Why Lag report - world 416 - 20:52 to 20:52 (1,440 min)", lines(first.report())[0]);

		// A group at 0 is left out of line 3; "1 not sure" is named.
		final Snap groups = new Snap();
		groups.counts = counts(0, 2, 0, 1, 1);
		groups.total = 4;
		assertEquals("4 lags this session: 2 frame rate, 1 world, 1 not sure", lines(groups.report())[2]);
		final Snap one = new Snap();
		one.counts = counts(0, 0, 0, 1, 0);
		one.total = 1;
		assertEquals("1 lag this session: 1 world", lines(one.report())[2]);

		// The group words in upper case: "FRAME RATE", "NOT SURE".
		final Snap words = new Snap();
		words.events = new ArrayList<>();
		words.events.add(event(0, 60, 1, verdict(Cause.CLIENT_BUSY, Confidence.HINT, "The client itself stalled",
			"A 480 ms freeze. Connection and world were fine.", "Turn plugins off one at a time.")));
		words.events.add(event(1, 120, 2, verdict(Cause.NOT_SURE, Confidence.CANT_TELL, "Can't tell yet",
			"It was memory clean-up or lost packets.", "Wait for it to happen again.")));
		final String[] w = lines(words.report());
		assertTrue(w[3], w[3].startsWith("20:54:00  2 s  NOT SURE  Can't tell  "));
		assertTrue(w[4], w[4].startsWith("20:53:00  1 s  FRAME RATE  Hint  "));

		// "cap none": no cap known (an unknown renderer, FPS Control off).
		final Snap none = new Snap();
		none.settings = new SettingsView(Renderer.UNKNOWN, false, false, 0, false, 0, false, "", 0, 0, "", 0, 0, 768,
			MemorySource.MANAGEMENT, Os.WINDOWS, "1.12.38");
		assertEquals("Client 1.12.38, renderer unknown, cap none, memory limit 768 MB, memory source management",
			last(none.report()));

		// No draw distance and no anti-aliasing on the CPU renderer.
		final Snap cpu = new Snap();
		cpu.settings = new SettingsView(Renderer.CPU, false, false, 0, false, 0, false, "", 0, 0, "", 0, 60, 768,
			MemorySource.MANAGEMENT, Os.WINDOWS, "1.12.38");
		assertEquals("Client 1.12.38, renderer CPU, cap 50 (the client), memory limit 768 MB, memory source"
			+ " management", last(cpu.report()));

		// "fps -".
		final Snap noFps = new Snap();
		noFps.tiles[Lane.FRAME_RATE.ordinal()] = dash(Lane.FRAME_RATE, NoData.NO_FRAMES);
		assertTrue(lines(noFps.report())[1].startsWith("Now: Smooth. fps -, ticks 600 ms, "));

		// "Client unknown".
		final Snap noVersion = new Snap();
		noVersion.settings = gpu("", 50, "MSAA_2");
		assertTrue(last(noVersion.report()).startsWith("Client unknown, renderer GPU, "));

		// "memory limit unknown", at 0 and below.
		for (int limit : new int[] {0, -1})
		{
			final Snap unknown = new Snap();
			unknown.settings = new SettingsView(Renderer.GPU, false, false, 0, false, 0, false, "", 0, 50, "MSAA_2", 0,
				60, limit, MemorySource.RUNTIME, Os.WINDOWS, "1.12.38");
			assertEquals("Client 1.12.38, renderer GPU, draw distance 50, MSAA_2, cap 50 (the client), memory limit"
				+ " unknown, memory source runtime", last(unknown.report()));
		}
	}

	/** 117 HD prints its name and its settings; the cap is the FOCUSED one, with who set it; numbers get commas. */
	@Test
	public void theSettingsLineOnOtherSettings()
	{
		final Snap hd = new Snap();
		hd.settings = new SettingsView(Renderer.HD, true, true, 30, true, 10, true, "OFF", 144, 90, "MSAA_16", 3, 144,
			1024, MemorySource.MANAGEMENT, Os.LINUX, "1.12.38");
		assertEquals("the unfocused limit is lower, and not printed", 10, hd.settings.capFps(false));
		assertEquals("Client 1.12.38, renderer 117 HD, draw distance 90, MSAA_16, cap 30 (FPS Control), memory limit"
			+ " 1,024 MB, memory source management", last(hd.report()));

		final Snap vsync = new Snap();
		vsync.settings = new SettingsView(Renderer.GPU, false, false, 0, false, 0, true, "ON", 0, 50, "DISABLED", 0,
			144, 768, MemorySource.MANAGEMENT, Os.WINDOWS, "1.12.38");
		assertTrue(last(vsync.report()).contains(", cap 144 (GPU: V-Sync), "));
	}

	/** Choice: a null verdict prints the headline "Still measuring". */
	@Test
	public void aNullVerdictIsStillMeasuring()
	{
		final Snap s = new Snap();
		s.verdict = null;
		assertEquals("Now: Still measuring. 50 fps, ticks 600 ms, ping 41 ms, memory 51 %, CPU 37 %",
			lines(s.report())[1]);
	}

	/** Choice: an empty proof leaves no trailing space, before a fix or at the end of the line. */
	@Test
	public void anEmptyProofLeavesNoTrailingSpace()
	{
		final Snap s = new Snap();
		s.events = new ArrayList<>();
		s.events.add(event(0, 60, 2, verdict(Cause.CLIENT_BUSY, Confidence.HINT, "The client itself stalled", "",
			"Turn plugins off one at a time.")));
		s.events.add(event(1, 120, 2, verdict(Cause.CLIENT_BUSY, Confidence.HINT, "The client itself stalled", "",
			"")));
		final String[] lines = lines(s.report());
		assertEquals("20:54:00  2 s  FRAME RATE  Hint  The client itself stalled.", lines[3]);
		assertEquals("20:53:00  2 s  FRAME RATE  Hint  The client itself stalled. Fix: Turn plugins off one at a time.",
			lines[4]);
	}

	/** Choice: an event with no verdict prints "Can't tell" and no text. */
	@Test
	public void anUnjudgedEventSaysCantTell()
	{
		final Snap s = new Snap();
		s.events = Collections.singletonList(event(0, 60, 5, null));
		final String[] lines = lines(s.report());
		assertEquals("20:53:00  5 s  NOT SURE  Can't tell", lines[3]);
		assertOnlyTheFiveKindsOfLine(s.report(), 1);
	}

	/** Choice: a session counted under no group of line 3 prints no colon. */
	@Test
	public void noGroupListedMeansNoColon()
	{
		final Snap s = new Snap();
		s.counts = counts(0, 0, 0, 0, 0);
		s.counts[Group.NONE.ordinal()] = 1;
		s.total = 1;
		assertEquals("1 lag this session", lines(s.report())[2]);
	}

	/** Choice: a wall clock set back before the session's start prints "(0 min)". */
	@Test
	public void aWallClockSetBackIsZeroMinutes()
	{
		final Snap s = new Snap();
		s.wallMs = START - 3 * MINUTE;
		assertEquals("2h Why Lag report - world 416 - 20:52 to 20:49 (0 min)", lines(s.report())[0]);
	}

	/** Choice: an empty anti-aliasing name on GPU or 117 HD prints "anti-aliasing unknown". */
	@Test
	public void anUnknownAntiAliasingIsSaid()
	{
		final Snap s = new Snap();
		s.settings = gpu("1.12.38", 50, "");
		assertEquals("Client 1.12.38, renderer GPU, draw distance 50, anti-aliasing unknown, cap 50 (the client),"
			+ " memory limit 768 MB, memory source management", last(s.report()));
	}

	// ---------------------------------------------------------------- helpers

	/** A snapshot to change one field at a time. It starts as the picture's: world 416, an hour, four lags. */
	private static final class Snap
	{
		long wallMs = START + 60 * MINUTE;
		int world = 416;
		Verdict verdict = verdict(Cause.ALL_CLEAR, Confidence.SURE, "Smooth", "No lag for 4 min.", "");
		Tile[] tiles = {
			new Tile(Lane.FRAME_RATE, Level.OK, "50 fps", "worst 35 ms", NoData.NONE),
			new Tile(Lane.TICKS, Level.OK, "600 ms", "\u00b113 ms", NoData.NONE),
			new Tile(Lane.PING, Level.OK, "41 ms", "was 39 ms", NoData.NONE),
			new Tile(Lane.MEMORY, Level.OK, "51 %", "pause 23 ms", NoData.NONE)};
		List<LagEvent> events = picturesEvents();
		int[] counts = counts(1, 1, 1, 1, 0);
		int total = 4;
		int sysCpuPct = 37;
		int gameBusyPct = 95;
		SettingsView settings = gpu("1.12.38", 50, "MSAA_2");

		/** The snapshot; its range events and strips are left empty: the report reads neither. */
		PanelSnapshot build()
		{
			return new PanelSnapshot(wallMs, ZoneOffset.UTC, world, verdict, tiles, 60, wallMs - 60 * MINUTE, wallMs,
				null, Collections.emptyList(), events, counts, total, START, sysCpuPct, gameBusyPct, settings, "");
		}

		String report()
		{
			return ReportText.of(build());
		}
	}

	/** The picture's four lags, oldest first: one of each group, the newest a slow world at 21:47:30. */
	private static List<LagEvent> picturesEvents()
	{
		final List<LagEvent> out = new ArrayList<>();
		out.add(event(0, 13 * 60 + 12, 6, verdict(Cause.UPLOAD_LOSS, Confidence.LIKELY, "Packets are being lost",
			"2 in 100 were re-sent. Ping can look fine.", "If every world does it, check cable or Wi-Fi.")));
		out.add(event(1, 28 * 60 + 40, 1, verdict(Cause.CLIENT_BUSY, Confidence.HINT, "The client itself stalled",
			"A 480 ms freeze. Connection and world were fine.", "Turn plugins off one at a time.")));
		out.add(event(2, 41 * 60 + 5, 2, verdict(Cause.GC_PAUSE, Confidence.SURE, "Memory clean-up froze the game",
			"A 340 ms pause. Memory 742 of 768 MB.", "Close the world map. Restart if it repeats.")));
		out.add(event(3, 55 * 60 + 30, 14, verdict(Cause.SLOW_WORLD, Confidence.LIKELY,
			"World 416 is struggling, not you", "Ticks 600 to 900+ ms for 14 s. Ping stayed 41 ms, 50 fps.",
			"Hop to a quieter world.")));
		return out;
	}

	/** A closed event of {@code lengthS} seconds from session second {@code startSec}. */
	private static LagEvent event(long id, long startSec, int lengthS, Verdict v)
	{
		return new LagEvent(id, startSec, startSec + lengthS - 1, START + startSec * 1000, Trigger.TICK_OFF.bit(),
			Trigger.TICK_OFF, 416, 0, 0, 0, 50, 34, 952, 1240, 640, 41, 44, 41, 900, 0, 22, 607, 768, 37, 95, false,
			false, v);
	}

	private static Verdict verdict(Cause cause, Confidence confidence, String headline, String proof, String fix)
	{
		return new Verdict(cause, confidence, Level.BAD, headline, proof, fix, "", 0, 0, 416, -1, null, null);
	}

	/** The session counts by group: connection, frame rate, memory, world, not sure; NONE at 0. */
	private static int[] counts(int connection, int frameRate, int memory, int world, int unsure)
	{
		final int[] out = new int[Group.values().length];
		out[Group.CONNECTION.ordinal()] = connection;
		out[Group.FRAME_RATE.ordinal()] = frameRate;
		out[Group.MEMORY.ordinal()] = memory;
		out[Group.WORLD.ordinal()] = world;
		out[Group.UNSURE.ordinal()] = unsure;
		return out;
	}

	/** The GPU plugin at its defaults (the client's cap of 50), with this client version and anti-aliasing. */
	private static SettingsView gpu(String clientVersion, int drawDistance, String antiAliasing)
	{
		return new SettingsView(Renderer.GPU, false, false, 0, false, 0, false, "", 0, drawDistance, antiAliasing, 0,
			60, 768, MemorySource.MANAGEMENT, Os.WINDOWS, clientVersion);
	}

	private static Tile dash(Lane lane, NoData why)
	{
		return new Tile(lane, Level.NO_DATA, "-", why.reason(), why);
	}

	/** The snapshot builder's snapshot of a steady trace with a usual, two lags in the log and a developer footer. */
	private static PanelSnapshot built()
	{
		final Trace t = Trace.steady(600).usual(40).tickLate(300, 352, true);
		final Session s = t.build();
		s.events.add(event(s, 0, 300, 305, verdict(Cause.SLOW_WORLD, Confidence.LIKELY,
			"World 416 is struggling, not you", "Ticks 600 to 950+ ms for 6 s. Ping stayed 40 ms, 50 fps.",
			"Hop to a quieter world.")));
		s.events.add(event(s, 1, 500, 500, verdict(Cause.NOT_SURE, Confidence.CANT_TELL, "Can't tell yet",
			"A 170 ms freeze. Its cause was not measured.", "Wait for it to happen again.")));
		final Verdict smooth = new Verdict(Cause.ALL_CLEAR, Confidence.SURE, Level.OK, "Smooth", "No lag for 1 min.",
			"", "", s.wallMsOf(500), 0, 416, -1, null, null);
		return new SnapshotBuilder().build(s, smooth, 10, 600, s.wallMsOf(600), t.settings(), "self: step 40 us");
	}

	private static LagEvent event(Session s, long id, long from, long to, Verdict v)
	{
		return new LagEvent(id, from, to, s.wallMsOf(from), Trigger.TICK_OFF.bit(), Trigger.TICK_OFF, 416, 0, 0, 0, 50,
			22, 700, 952, 330, 40, 40, 40, 5400, 0, 0, 400, 768, 20, 40, false, false, v);
	}

	/** The lines of a report, each of which ended with a new line. */
	private static String[] lines(String report)
	{
		assertTrue("every line ends with a new line", report.endsWith("\n"));
		return report.substring(0, report.length() - 1).split("\n", -1);
	}

	private static String last(String report)
	{
		final String[] lines = lines(report);
		return lines[lines.length - 1];
	}

	private static void assertAscii(String report)
	{
		for (int i = 0; i < report.length(); i++)
		{
			final char c = report.charAt(i);
			assertTrue("character " + i + " is " + (int) c, c == '\n' || c >= ' ' && c <= '~');
		}
	}

	/** The header, now, the counts, one line per event and the settings, each matched whole, and nothing else. */
	private static void assertOnlyTheFiveKindsOfLine(String report, int events)
	{
		final String[] lines = lines(report);
		assertEquals(events + 4, lines.length);
		assertTrue(lines[0], HEADER.matcher(lines[0]).matches());
		assertTrue(lines[1], NOW.matcher(lines[1]).matches());
		assertTrue(lines[2], COUNTS.matcher(lines[2]).matches());
		for (int i = 3; i < lines.length - 1; i++)
		{
			assertTrue(lines[i], EVENT.matcher(lines[i]).matches());
		}
		assertTrue(lines[lines.length - 1], SETTINGS.matcher(lines[lines.length - 1]).matches());
	}
}
