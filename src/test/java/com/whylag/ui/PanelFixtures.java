package com.whylag.ui;

import com.whylag.GraphRange;
import com.whylag.PanelActions;
import com.whylag.core.Cause;
import com.whylag.core.Confidence;
import com.whylag.core.Group;
import com.whylag.core.LagEvent;
import com.whylag.core.Lane;
import com.whylag.core.Level;
import com.whylag.core.MemorySource;
import com.whylag.core.NoData;
import com.whylag.core.Os;
import com.whylag.core.PanelSnapshot;
import com.whylag.core.Renderer;
import com.whylag.core.SettingsView;
import com.whylag.core.Strip;
import com.whylag.core.Thresholds;
import com.whylag.core.Tile;
import com.whylag.core.Trigger;
import com.whylag.core.Verdict;
import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Component;
import java.awt.Composite;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GraphicsConfiguration;
import java.awt.Image;
import java.awt.Paint;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.Stroke;
import java.awt.event.InputEvent;
import java.awt.event.MouseEvent;
import java.awt.font.FontRenderContext;
import java.awt.font.GlyphVector;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.awt.image.BufferedImageOp;
import java.awt.image.ImageObserver;
import java.awt.image.RenderedImage;
import java.awt.image.renderable.RenderableImage;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.AttributedCharacterIterator;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import javax.swing.JComponent;
import javax.swing.RepaintManager;
import javax.swing.SwingUtilities;

/**
 * The shared fixtures and tools of the panel's tests (contract 7, L6).
 *
 * <p><b>Fixtures.</b> Every {@link PanelSnapshot} here is built BY CONSTRUCTOR, with FIVE strips and FOUR tiles:
 * the picture's quiet panel and its lag (with its event selected), the warm-up, the login screen, every no-data
 * tile, waiting for the game, one fixture for each row of the answer table (contract 3.6) with that cause's longest
 * proof and fix, a condition, the longest when line, all clear after a lag here and on another world, all clear
 * with no lag, seven events, 500 events, five counts over 99, the CPU lane full, empty and with one half, a selected
 * event whose worst tick is 12,400 ms, and a selected "Not sure" event. The clock is UTC; now is 21:52:00 on
 * 2026-09-28 and the session began at 20:52:00, as in picture 18.
 *
 * <p><b>Tools.</b> {@link StubActions} (its report is a fixed text; the panel never names {@code ReportText}),
 * {@link #panel} (a panel on the Swing thread, activated, shown, selected, folded and laid out), {@link Recorder}
 * (a {@link Graphics2D} that records every string drawn, its font, its colour and the ground under it),
 * {@link Counting} (a repaint manager that counts repaints and layouts per component), {@link #press} and
 * {@link #tip} (a mouse press and a tooltip at a point), and the contrast ratio of WCAG 2.
 *
 * <p>Choice: V2's longest proof is a day with no lag, "No lag for 1,440 min." (6.4 names no largest minutes).
 * <p>Choice: G1's heap figures are 8,192 of 8,192 MB, a large memory limit (6.4 names no largest MB).
 * <p>Choice: each row of the answer table pairs its cause's longest proof with its longest fix, as D1 never does.
 * <p>Choice: every fixture's clock is UTC; now is 2026-09-28 21:52:00, the moment of picture 18.
 */
final class PanelFixtures
{
	static final ZoneId ZONE = ZoneOffset.UTC;
	static final long MIN = 60_000L;
	/** Now: 2026-09-28 21:52:00 UTC. */
	static final long NOW = Instant.parse("2026-09-28T21:52:00Z").toEpochMilli();
	/** The session began an hour ago, at 20:52:00. */
	static final long SESSION_START = NOW - 60 * MIN;
	static final int WORLD = 416;
	/** The picture's lag, event (d): 21:47:30, 14 s, world 416. */
	static final long D_START = Instant.parse("2026-09-28T21:47:30Z").toEpochMilli();
	/** The sentinel colour the unclipped paints start from. */
	static final Color SENTINEL = new Color(255, 0, 255);
	private static final String PM = "\u00b1";
	private static final int COLUMNS = Thresholds.STRIP_COLUMNS;

	private PanelFixtures()
	{
	}

	// =================================================================================== fixtures

	/** A snapshot, its name, and the id of the list row to select (-1 none). */
	static final class Fixture
	{
		final String name;
		final PanelSnapshot snapshot;
		final long selected;

		Fixture(String name, PanelSnapshot snapshot, long selected)
		{
			this.name = name;
			this.snapshot = snapshot;
			this.selected = selected;
		}

		@Override
		public String toString()
		{
			return name;
		}
	}

	/** Every fixture, the answer table's rows included. */
	static List<Fixture> all()
	{
		final List<Fixture> out = new ArrayList<>(Arrays.asList(quiet(), lag(), warmingUp(), notLoggedIn(),
			waiting(), condition(), longestWhen(), clearHere(), clearElsewhere(), clearNone(), sevenEvents(),
			fiveHundred(), countsOver99(), cpuFull(), cpuNone(), cpuGameOnly(), cpuPcOnly(), worstTick(),
			notSure()));
		out.addAll(noDataTiles());
		out.addAll(answerRows());
		return out;
	}

	static Fixture named(String name)
	{
		for (Fixture f : all())
		{
			if (f.name.equals(name))
			{
				return f;
			}
		}
		throw new AssertionError("no fixture " + name);
	}

	/** Picture 18, frame 1: quiet at 21:52, the 1 min range, "Smooth", "Last lag 21:47, this world". */
	static Fixture quiet()
	{
		final Lanes lanes = new Lanes(1);
		return new Fixture("quiet", snapshot(WORLD, clearAfter(WORLD), quietTiles(), 1, lanes.build(),
			Collections.emptyList(), 24, 42), -1);
	}

	/** Picture 18, frames 2 and 3: the 10 min range with the picture's lag, event (d), SELECTED. */
	static Fixture lag()
	{
		final LagEvent d = pictureEvent();
		final Lanes lanes = new Lanes(10).during(D_START, 14, Lane.TICKS, 1240, Level.BAD).scale(Lane.TICKS, 450, 1290)
			.game(45, "Game 45 %");
		return new Fixture("lag", snapshot(WORLD, clearAfter(WORLD), quietTiles(), 10, lanes.build(),
			Collections.singletonList(d), 24, 45), d.id);
	}

	/** The card's warm-up; the four tiles and the five lane values WITH numbers (contract 5.1). */
	static Fixture warmingUp()
	{
		final Verdict v = state(com.whylag.core.Answer.HEAD_MEASURING, "Ready in 40 s.");
		return new Fixture("warming-up", snapshot(WORLD, v, quietTiles(), 10, new Lanes(10).build(),
			Collections.emptyList(), 24, 42, new int[Group.values().length], 0), -1);
	}

	/** The login screen: world 0, every tile "-", every lane value "", every column of every strip NONE. */
	static Fixture notLoggedIn()
	{
		final Verdict v = state(com.whylag.core.Answer.HEAD_NOT_LOGGED_IN, "Log in to start measuring.");
		final Tile[] tiles = new Tile[Lane.TILES];
		for (int i = 0; i < tiles.length; i++)
		{
			tiles[i] = new Tile(Lane.values()[i], Level.NO_DATA, "-", NoData.NOT_LOGGED_IN.reason(),
				NoData.NOT_LOGGED_IN);
		}
		return new Fixture("not-logged-in", snapshot(0, v, tiles, 10, new Lanes(10).empty().build(),
			Collections.emptyList(), -1, -1), -1);
	}

	/** Frames stopped while logged in: "Waiting for the game". */
	static Fixture waiting()
	{
		final Verdict v = state(com.whylag.core.Answer.HEAD_WAITING, "No frames are being drawn.");
		final Tile[] tiles = quietTiles();
		tiles[0] = noData(Lane.FRAME_RATE, NoData.NO_FRAMES);
		return new Fixture("waiting", snapshot(WORLD, v, tiles, 10, new Lanes(10).none(Lane.FRAME_RATE).build(),
			Collections.singletonList(pictureEvent()), 24, 42), -1);
	}

	/** Every no-data tile: frames stopped, no ticks yet, each of the four ping reasons, an unknown heap limit. */
	static List<Fixture> noDataTiles()
	{
		final List<Fixture> out = new ArrayList<>();
		final Tile[] first = {noData(Lane.FRAME_RATE, NoData.NO_FRAMES), noData(Lane.TICKS, NoData.NO_TICKS),
			noData(Lane.PING, NoData.NOT_CONNECTED), noData(Lane.MEMORY, NoData.ERROR)};
		out.add(new Fixture("no-data-frames-ticks-heap", snapshot(WORLD,
			state(com.whylag.core.Answer.HEAD_WAITING, "No frames are being drawn."), first, 10,
			new Lanes(10).none(Lane.FRAME_RATE).none(Lane.TICKS).none(Lane.PING).none(Lane.MEMORY).build(),
			Collections.emptyList(), 24, 42), -1));
		for (NoData why : new NoData[] {NoData.UNSUPPORTED, NoData.ERROR, NoData.STALE})
		{
			final Tile[] tiles = quietTiles();
			tiles[2] = noData(Lane.PING, why);
			out.add(new Fixture("no-data-ping-" + why.name().toLowerCase(), snapshot(WORLD, clearAfter(WORLD), tiles,
				10, new Lanes(10).none(Lane.PING).build(), Collections.singletonList(pictureEvent()), 24, 42), -1));
		}
		return out;
	}

	/** A condition: N1, WARN, "since 21:40". */
	static Fixture condition()
	{
		final Verdict v = new Verdict(Cause.PING_HIGH, Confidence.SURE, Level.WARN, "Ping is high but steady",
			"180 ms. Your usual is 45 ms.", "Try a world closer to you.", "", at("21:40:00"), 0, WORLD, -1, null, null);
		final Tile[] tiles = quietTiles();
		tiles[2] = new Tile(Lane.PING, Level.BAD, "180 ms", "was 45 ms", NoData.NONE);
		final Lanes lanes = new Lanes(10).during(NOW - 10 * MIN, 600, Lane.PING, 180, Level.BAD)
			.scale(Lane.PING, 0, 216).value(Lane.PING, "180 ms", Level.BAD);
		return new Fixture("condition", snapshot(WORLD, v, tiles, 10, lanes.build(), Collections.emptyList(), 24,
			42), -1);
	}

	/** The longest when line: "23:59:59, world 999, 59 min ago" with "Can't tell", a selected X event. */
	static Fixture longestWhen()
	{
		final long when = Instant.parse("2026-09-28T23:59:59Z").toEpochMilli();
		final long now = when + 59 * MIN + 30_000L;
		final Verdict v = new Verdict(Cause.NOT_SURE, Confidence.CANT_TELL, Level.BAD, "Can't tell yet",
			"A 170 ms freeze. Its cause was not measured.", "Wait for it to happen again.", "", when, 5, 999, 7,
			null, null);
		final LagEvent e = event(7, when, 5, 999, v, 50, 170, 610, 700, 30, 41, 40, 400, 768, 0, 20, 40);
		final Lanes lanes = new Lanes(60, now);
		final PanelSnapshot s = new PanelSnapshot(now, ZONE, 999, clearAfter(999), quietTiles(), 60,
			now - 60 * MIN, now, lanes.build(), Collections.singletonList(e), Collections.singletonList(e),
			counts(0, 0, 0, 0, 1), 1, now - 90 * MIN, 24, 42, settings(), "");
		return new Fixture("longest-when", s, 7);
	}

	/** All clear after a lag on this world: "Last lag 21:47, this world". */
	static Fixture clearHere()
	{
		return new Fixture("clear-here", snapshot(WORLD, clearAfter(WORLD), quietTiles(), 10, new Lanes(10)
			.during(D_START, 14, Lane.TICKS, 1240, Level.BAD).scale(Lane.TICKS, 450, 1290).build(),
			Collections.singletonList(pictureEvent()), 24, 42), -1);
	}

	/** All clear after a lag on another world: "Last lag 21:47, world 302". */
	static Fixture clearElsewhere()
	{
		return new Fixture("clear-elsewhere", snapshot(WORLD, clearAfter(302), quietTiles(), 60,
			new Lanes(60).build(), session(), 24, 42), -1);
	}

	/** All clear with no lag this session: "No lag this session.", no when line, 73 px. */
	static Fixture clearNone()
	{
		final Verdict v = new Verdict(Cause.ALL_CLEAR, Confidence.SURE, Level.OK, "Smooth", "No lag this session.",
			"", "", 0, 0, 0, -1, null, null);
		return new Fixture("clear-none", snapshot(WORLD, v, quietTiles(), 60, new Lanes(60).build(),
			Collections.emptyList(), 24, 42, new int[Group.values().length], 0), -1);
	}

	/** Seven events in the 60 min range: six rows and "and 1 more". */
	static Fixture sevenEvents()
	{
		final List<LagEvent> events = new ArrayList<>();
		for (int i = 0; i < 7; i++)
		{
			final Cause cause = i % 2 == 0 ? Cause.SLOW_WORLD : Cause.CLIENT_BUSY;
			final long start = SESSION_START + (5 + i * 7) * MIN;
			events.add(event(i, start, 3 + i, WORLD, eventVerdict(cause, i, start, 3 + i), 50, 34, 700, 1100, 450,
				41, 41, 500, 768, 10, 30, 50));
		}
		return new Fixture("seven-events", snapshot(WORLD, clearAfter(WORLD), quietTiles(), 60,
			new Lanes(60).build(), events, events, counts(0, 3, 0, 4, 0), 7, 24, 42, ""), -1);
	}

	/** 500 closed events in the 60 min range: "Lags (500)". */
	static Fixture fiveHundred()
	{
		final List<LagEvent> events = new ArrayList<>();
		final Cause[] causes = {Cause.UPLOAD_LOSS, Cause.CLIENT_BUSY, Cause.GC_PAUSE, Cause.SLOW_WORLD};
		for (int i = 0; i < 500; i++)
		{
			final long start = SESSION_START + 60_000L + i * 7_000L;
			final Cause cause = causes[i % causes.length];
			events.add(event(i, start, 3, WORLD, eventVerdict(cause, i, start, 3), 50, 34, 640, 900, 300, 41, 41, 500,
				768, 10, 30, 50));
		}
		return new Fixture("five-hundred", snapshot(WORLD, clearAfter(WORLD), quietTiles(), 60, new Lanes(60).build(),
			events, events, counts(125, 125, 125, 125, 0), 500, 24, 42, ""), -1);
	}

	/** Five counts, each over 99: "Conn 99+ .. ? 99+", two lines. */
	static Fixture countsOver99()
	{
		return new Fixture("counts-over-99", snapshot(WORLD, clearAfter(WORLD), quietTiles(), 60,
			new Lanes(60).build(), session(), session(), counts(120, 150, 100, 999, 130), 1499, 24, 42, ""), -1);
	}

	/** The CPU lane at its widest: "Game 100 %" and "PC 100 %". */
	static Fixture cpuFull()
	{
		final Lanes lanes = new Lanes(10).cpu(100, Level.BAD, "PC 100 %").game(100, "Game 100 %");
		return new Fixture("cpu-full", snapshot(WORLD, clearAfter(WORLD), quietTiles(), 10, lanes.build(),
			Collections.emptyList(), 100, 100), -1);
	}

	/** A Runtime-only PC: logged in, four lanes with values, the CPU lane empty in both series. */
	static Fixture cpuNone()
	{
		final Tile[] tiles = quietTiles();
		tiles[3] = new Tile(Lane.MEMORY, Level.OK, "51 %", "pause n/a", NoData.NONE);
		final Lanes lanes = new Lanes(10).none(Lane.CPU).noGame();
		return new Fixture("cpu-none", snapshot(WORLD, clearAfter(WORLD), tiles, 10, lanes.build(),
			Collections.emptyList(), -1, -1), -1);
	}

	/** The CPU lane with the Game half alone: no PC figure, the game at 42 %. */
	static Fixture cpuGameOnly()
	{
		final Lanes lanes = new Lanes(10).none(Lane.CPU).game(42, "Game 42 %");
		return new Fixture("cpu-game-only", snapshot(WORLD, clearAfter(WORLD), quietTiles(), 10, lanes.build(),
			Collections.emptyList(), -1, 42), -1);
	}

	/** The CPU lane with the PC half alone: PC 37 %, no game figure. */
	static Fixture cpuPcOnly()
	{
		final Lanes lanes = new Lanes(10).cpu(37, Level.OK, "PC 37 %").noGame();
		return new Fixture("cpu-pc-only", snapshot(WORLD, clearAfter(WORLD), quietTiles(), 10, lanes.build(),
			Collections.emptyList(), 37, -1), -1);
	}

	/** A selected event whose worst tick is 12,400 ms: its Tick cell prints "9,999". */
	static Fixture worstTick()
	{
		final long start = D_START;
		final Verdict v = eventVerdict(Cause.DELIVERY_GAP, 3, start, 14);
		final LagEvent e = event(3, start, 14, WORLD, v, 50, 34, 1800, 12400, 11800, 41, 41, 607, 768, 22, 37, 95);
		return new Fixture("worst-tick", snapshot(WORLD, clearAfter(WORLD), quietTiles(), 10, new Lanes(10)
			.during(start, 14, Lane.TICKS, 12400, Level.BAD).scale(Lane.TICKS, 450, 12450).build(),
			Collections.singletonList(e), 24, 42), 3);
	}

	/** A selected "Not sure" event: X marks no cell. */
	static Fixture notSure()
	{
		final Verdict v = new Verdict(Cause.NOT_SURE, Confidence.CANT_TELL, Level.BAD, "Can't tell yet",
			"It was memory clean-up or lost packets.", "Wait for it to happen again.", "", D_START, 14, WORLD, 3,
			Cause.GC_PAUSE, Cause.UPLOAD_LOSS);
		final LagEvent e = event(3, D_START, 14, WORLD, v, 50, 34, 952, 1240, 640, 41, 41, 607, 768, 22, 37, 95);
		return new Fixture("not-sure", snapshot(WORLD, clearAfter(WORLD), quietTiles(), 10, new Lanes(10).build(),
			Collections.singletonList(e), 24, 42), 3);
	}

	/**
	 * ONE fixture for each row of the answer table of contract 3.6: each wave-one cause with its longest proof and
	 * fix of contract 6.4 (the largest values filled in), and the three card states. Event causes are BAD and held
	 * on the card (their event in the range, not selected); conditions are WARN, "since 21:40"; V2 is OK.
	 */
	static List<Fixture> answerRows()
	{
		final List<Fixture> out = new ArrayList<>();
		out.add(row("answer-v2", Cause.ALL_CLEAR, Level.OK, Confidence.SURE, "Smooth", "No lag for 1,440 min.", ""));
		out.add(row("answer-w1", Cause.SLOW_WORLD, Level.BAD, Confidence.LIKELY, "World 999 is struggling, not you",
			"Ticks 600 to 9,990+ ms for 120 s. Ping stayed 999 ms, 999 fps.", "Hop to a quieter world."));
		out.add(row("answer-w1c", Cause.SLOW_WORLD, Level.WARN, Confidence.HINT, "This world is running slow",
			"Ticks take 9,999 ms here. Ping and frames are fine.", "Hop to a quieter world."));
		out.add(row("answer-n2", Cause.PING_JUMPY, Level.BAD, Confidence.LIKELY, "Your connection is unsteady",
			"Ping swung 999-999 ms. Ticks came early and late.", "Use a cable, not Wi-Fi. Pause downloads."));
		out.add(row("answer-n1", Cause.PING_HIGH, Level.WARN, Confidence.SURE, "Ping is high but steady",
			"999 ms. Your usual is 999 ms.", "Try a world closer to you."));
		out.add(row("answer-n3", Cause.UPLOAD_LOSS, Level.BAD, Confidence.LIKELY, "Packets are being lost",
			"100 in 100 were re-sent. Ping can look fine.", "If every world does it, check cable or Wi-Fi."));
		out.add(row("answer-d1", Cause.DISCONNECT, Level.BAD, Confidence.SURE, "Connection lost at 23:59",
			"Ping and re-sends were fine just before.", "If every world does it, check cable or Wi-Fi."));
		out.add(row("answer-n6", Cause.DELIVERY_GAP, Level.BAD, Confidence.CANT_TELL, "The game stopped answering",
			"No ticks for 10.0 s. Frames and ping were fine.", "Hop worlds. If it follows you, it is your line."));
		out.add(row("answer-f2", Cause.SLOW_DRAWING, Level.WARN, Confidence.LIKELY, "The game is drawing slowly",
			"39 fps. Draw distance 90, MSAA_16.", "Lower draw distance or anti-aliasing."));
		out.add(row("answer-f1", Cause.FRAME_CAP, Level.WARN, Confidence.SURE, "Frame rate is capped at 999",
			"Set by FPS Control (unfocused). Not lag.", "Raise or turn off that cap."));
		out.add(row("answer-s3", Cause.CLIENT_BUSY, Level.BAD, Confidence.HINT, "The client itself stalled",
			"A 9,999 ms freeze. Connection and world were fine.", "Turn plugins off one at a time."));
		out.add(row("answer-s4", Cause.CLIENT_WAITING, Level.BAD, Confidence.HINT, "The client was kept waiting",
			"A 9,999 ms freeze, but the client was not busy.", "Close overlays and recorders."));
		out.add(row("answer-s1", Cause.MAP_LOAD, Level.BAD, Confidence.SURE, "Map loading took 10.0 s",
			"Extended map loading is set to 5.", "Nothing to fix. It is the map."));
		out.add(row("answer-g1", Cause.GC_PAUSE, Level.BAD, Confidence.SURE, "Memory clean-up froze the game",
			"A 9,999 ms pause. Memory 8,192 of 8,192 MB.", "Close the world map. Restart if it repeats."));
		out.add(row("answer-g2", Cause.HEAP_CAP_LOW, Level.WARN, Confidence.SURE, "Memory limit is set too low",
			"The client may use only 699 MB. Default is 768.", "Remove the Java memory limit."));
		out.add(row("answer-x", Cause.NOT_SURE, Level.BAD, Confidence.CANT_TELL, "Can't tell yet",
			"A 9,999 ms freeze. Its cause was not measured. No ping data.", "Wait for it to happen again."));
		out.add(new Fixture("answer-measuring", warmingUp().snapshot, -1));
		out.add(new Fixture("answer-not-logged-in", notLoggedIn().snapshot, -1));
		out.add(new Fixture("answer-waiting", waiting().snapshot, -1));
		return out;
	}

	private static Fixture row(String name, Cause cause, Level level, Confidence confidence, String headline,
		String proof, String fix)
	{
		final boolean event = level == Level.BAD;
		final long when = cause == Cause.ALL_CLEAR ? D_START : event ? D_START : at("21:40:00");
		final Verdict v = new Verdict(cause, confidence, level, headline, proof, fix,
			event ? "Frames and ping were fine." : "", when, event ? 14 : 0, WORLD, event ? 3 : -1,
			cause == Cause.NOT_SURE ? Cause.GC_PAUSE : null, cause == Cause.NOT_SURE ? Cause.UPLOAD_LOSS : null);
		final List<LagEvent> range = new ArrayList<>();
		range.add(event ? event(3, D_START, 14, WORLD, v, 50, 34, 952, 1240, 640, 41, 41, 607, 768, 22, 37, 95)
			: pictureEvent());
		return new Fixture(name, snapshot(WORLD, v, quietTiles(), 10, new Lanes(10).build(), range, 24, 42), -1);
	}

	// ----------------------------------------------------------------------------- the pieces

	/** The picture's quiet tiles: 50 fps / 600 ms / 41 ms, was 39 / 51 %. */
	static Tile[] quietTiles()
	{
		return new Tile[] {
			new Tile(Lane.FRAME_RATE, Level.OK, "50 fps", "worst 35 ms", NoData.NONE),
			new Tile(Lane.TICKS, Level.OK, "600 ms", PM + "13 ms", NoData.NONE),
			new Tile(Lane.PING, Level.OK, "41 ms", "was 39 ms", NoData.NONE),
			new Tile(Lane.MEMORY, Level.OK, "51 %", "pause 23 ms", NoData.NONE)};
	}

	static Tile noData(Lane lane, NoData why)
	{
		return new Tile(lane, Level.NO_DATA, "-", why.reason(), why);
	}

	/** The picture's lag, event (d): W1 at 21:47:30 for 14 s; its numbers are those of picture 18. */
	static LagEvent pictureEvent()
	{
		final Verdict v = new Verdict(Cause.SLOW_WORLD, Confidence.LIKELY, Level.BAD,
			"World 416 is struggling, not you", "Ticks 600 to 900+ ms for 14 s. Ping stayed 41 ms, 50 fps.",
			"Hop to a quieter world.", "Frames and ping were fine.", D_START, 14, WORLD, 3, null, null);
		return event(3, D_START, 14, WORLD, v, 50, 34, 952, 1240, 640, 41, 41, 607, 768, 22, 37, 95);
	}

	/** The picture's session: four lags, one of each group, (a) to (d). */
	static List<LagEvent> session()
	{
		final List<LagEvent> out = new ArrayList<>();
		final long a = at("21:09:12");
		final long b = at("21:19:05");
		final long c = at("21:31:40");
		out.add(event(0, a, 6, WORLD, eventVerdict(Cause.UPLOAD_LOSS, 0, a, 6), 50, 30, 700, 1100, 460, 41, 41, 500,
			768, 12, 30, 45));
		out.add(event(1, b, 3, WORLD, eventVerdict(Cause.CLIENT_BUSY, 1, b, 3), 2, 480, 640, 1080, 120, 41, 41, 520,
			768, 0, 31, 100));
		out.add(event(2, c, 2, WORLD, eventVerdict(Cause.GC_PAUSE, 2, c, 2), 40, 340, 610, 900, 60, 41, 41, 742, 768,
			340, 29, 88));
		out.add(pictureEvent());
		return out;
	}

	/** An event verdict of a cause, BAD, at the event's start. */
	static Verdict eventVerdict(Cause cause, long id, long startWallMs, int lengthS)
	{
		return new Verdict(cause, Confidence.LIKELY, Level.BAD, headline(cause), "The proof of " + cause.id() + ".",
			"The fix of " + cause.id() + ".", "", startWallMs, lengthS, WORLD, id, null, null);
	}

	private static String headline(Cause cause)
	{
		switch (cause)
		{
			case UPLOAD_LOSS:
				return "Packets are being lost";
			case CLIENT_BUSY:
				return "The client itself stalled";
			case GC_PAUSE:
				return "Memory clean-up froze the game";
			case DELIVERY_GAP:
				return "The game stopped answering";
			default:
				return "World 416 is struggling, not you";
		}
	}

	/** All clear after a lag at 21:47 on that world: "Smooth", "No lag for 4 min.". */
	static Verdict clearAfter(int world)
	{
		return new Verdict(Cause.ALL_CLEAR, Confidence.SURE, Level.OK, "Smooth", "No lag for 4 min.", "", "", D_START,
			0, world, -1, null, null);
	}

	/** A card state that is not a verdict (contract 5.1). */
	static Verdict state(String headline, String proof)
	{
		return new Verdict(Cause.WARMING_UP, Confidence.CANT_TELL, Level.NO_DATA, headline, proof, "", "", 0, 0, 0, -1,
			null, null);
	}

	static LagEvent event(long id, long startWallMs, int lengthS, int world, Verdict v, int fps, int worstFrameMs,
		int meanTickGapMs, int worstTickGapMs, int worstCorrectedTickMs, int rttMs, int rttBeforeMs, int heapUsedMb,
		int heapMaxMb, int gcPauseMs, int sysCpuPct, int gameBusyPct)
	{
		final long startSec = (startWallMs - SESSION_START) / 1000;
		return new LagEvent(id, startSec, startSec + lengthS - 1, startWallMs, Trigger.TICK_OFF.bit(),
			Trigger.TICK_OFF, world, 0, 0, 0, fps, worstFrameMs, meanTickGapMs, worstTickGapMs, worstCorrectedTickMs,
			rttMs, rttMs, rttBeforeMs, 900, 0, gcPauseMs, heapUsedMb, heapMaxMb, sysCpuPct, gameBusyPct, false, false,
			v);
	}

	/** The picture's counts: Conn 1, Frame 1, Mem 1, World 1. */
	static int[] pictureCounts()
	{
		return counts(1, 1, 1, 1, 0);
	}

	static int[] counts(int connection, int frame, int memory, int world, int unsure)
	{
		final int[] c = new int[Group.values().length];
		c[Group.CONNECTION.ordinal()] = connection;
		c[Group.FRAME_RATE.ordinal()] = frame;
		c[Group.MEMORY.ordinal()] = memory;
		c[Group.WORLD.ordinal()] = world;
		c[Group.UNSURE.ordinal()] = unsure;
		return c;
	}

	/** A clock of 2026-09-28, UTC, "21:40:00". */
	static long at(String clock)
	{
		return Instant.parse("2026-09-28T" + clock + "Z").toEpochMilli();
	}

	static SettingsView settings()
	{
		return new SettingsView(Renderer.CPU, false, false, 0, false, 0, false, "", 0, 0, "", 0, 60, 768,
			MemorySource.MANAGEMENT, Os.WINDOWS, "1.12.37");
	}

	/** A snapshot at 21:52 with the picture's session: four lags, one of each group. */
	static PanelSnapshot snapshot(int world, Verdict v, Tile[] tiles, int rangeMinutes, Strip[] strips,
		List<LagEvent> rangeEvents, int sysCpuPct, int gameBusyPct)
	{
		return snapshot(world, v, tiles, rangeMinutes, strips, rangeEvents, sysCpuPct, gameBusyPct, pictureCounts(), 4);
	}

	static PanelSnapshot snapshot(int world, Verdict v, Tile[] tiles, int rangeMinutes, Strip[] strips,
		List<LagEvent> rangeEvents, int sysCpuPct, int gameBusyPct, int[] counts, int total)
	{
		return snapshot(world, v, tiles, rangeMinutes, strips, rangeEvents, total == 0 ? Collections.emptyList()
			: session(), counts, total, sysCpuPct, gameBusyPct, "");
	}

	static PanelSnapshot snapshot(int world, Verdict v, Tile[] tiles, int rangeMinutes, Strip[] strips,
		List<LagEvent> rangeEvents, List<LagEvent> sessionEvents, int[] counts, int total, int sysCpuPct,
		int gameBusyPct, String footer)
	{
		return new PanelSnapshot(NOW, ZONE, world, v, tiles, rangeMinutes, NOW - rangeMinutes * MIN, NOW, strips,
			rangeEvents, sessionEvents, counts, total, SESSION_START, sysCpuPct, gameBusyPct, settings(), footer);
	}

	/** The same snapshot with another footer. */
	static PanelSnapshot withFooter(PanelSnapshot s, String footer)
	{
		return new PanelSnapshot(s.wallMs, s.zone, s.world, s.verdict, s.tiles, s.rangeMinutes, s.rangeStartWallMs,
			s.rangeEndWallMs, s.strips, s.rangeEvents, s.sessionEvents, s.sessionCounts, s.sessionTotal,
			s.sessionStartWallMs, s.sysCpuPct, s.gameBusyPct, s.settings, footer);
	}

	/**
	 * The five lanes of a range ending now: quiet by default - 50 fps, 600 ms, 41 ms, heap after collection 392 of
	 * 768 MB ("51 %"), PC 24 % under a game at 42 % - with the builders the fixtures need.
	 */
	static final class Lanes
	{
		final long start;
		final long end;
		final int[][] values = new int[Lane.values().length][COLUMNS];
		final byte[][] levels = new byte[Lane.values().length][COLUMNS];
		final int[] game = new int[COLUMNS];
		final int[] min = {0, 450, 0, 0, 0};
		final int[] max = {60, 900, 100, 768, 100};
		final String[] now = {"50 fps", "600 ms", "41 ms", "51 %", "PC 24 %"};
		final Level[] nowLevel = {Level.OK, Level.OK, Level.OK, Level.OK, Level.OK};
		String now2 = "Game 42 %";

		Lanes(int minutes)
		{
			this(minutes, NOW);
		}

		Lanes(int minutes, long endWallMs)
		{
			end = endWallMs;
			start = endWallMs - minutes * MIN;
			final int[] quiet = {50, 600, 41, 392, 24};
			for (int lane = 0; lane < quiet.length; lane++)
			{
				Arrays.fill(values[lane], quiet[lane]);
				Arrays.fill(levels[lane], (byte) Level.OK.ordinal());
			}
			Arrays.fill(game, 42);
		}

		/** One lane holds {@code value} at {@code level} over the columns of those seconds. */
		Lanes during(long fromWallMs, int seconds, Lane lane, int value, Level level)
		{
			final int a = Math.max(0, Strip.columnOf(start, end, fromWallMs));
			final int b = Math.min(COLUMNS - 1, Strip.columnOf(start, end, fromWallMs + seconds * 1000L - 1));
			for (int c = a; c <= b; c++)
			{
				values[lane.ordinal()][c] = value;
				levels[lane.ordinal()][c] = (byte) level.ordinal();
			}
			return this;
		}

		Lanes scale(Lane lane, int lo, int hi)
		{
			min[lane.ordinal()] = lo;
			max[lane.ordinal()] = hi;
			return this;
		}

		Lanes value(Lane lane, String text, Level level)
		{
			now[lane.ordinal()] = text;
			nowLevel[lane.ordinal()] = level;
			return this;
		}

		/** A lane with no data: every column NONE, no value now. */
		Lanes none(Lane lane)
		{
			Arrays.fill(values[lane.ordinal()], Strip.NONE);
			Arrays.fill(levels[lane.ordinal()], (byte) Level.NO_DATA.ordinal());
			return value(lane, "", Level.NO_DATA);
		}

		/** The CPU lane's PC series at a steady value. */
		Lanes cpu(int pct, Level level, String text)
		{
			Arrays.fill(values[Lane.CPU.ordinal()], pct);
			Arrays.fill(levels[Lane.CPU.ordinal()], (byte) level.ordinal());
			return value(Lane.CPU, text, level);
		}

		Lanes game(int pct, String text)
		{
			Arrays.fill(game, pct);
			now2 = text;
			return this;
		}

		Lanes noGame()
		{
			Arrays.fill(game, Strip.NONE);
			now2 = "";
			return this;
		}

		/** The login screen: every column of every strip NONE, every value "". */
		Lanes empty()
		{
			for (Lane lane : Lane.values())
			{
				none(lane);
			}
			return noGame();
		}

		Strip[] build()
		{
			final Strip[] out = new Strip[Lane.values().length];
			for (Lane lane : Lane.values())
			{
				final int i = lane.ordinal();
				out[i] = lane == Lane.CPU
					? new Strip(lane, values[i].clone(), levels[i].clone(), min[i], max[i], now[i], nowLevel[i],
					game.clone(), now2)
					: new Strip(lane, values[i].clone(), levels[i].clone(), min[i], max[i], now[i], nowLevel[i]);
			}
			return out;
		}
	}

	// ================================================================================ the stub

	/** The panel's actions in every test: the report is a fixed text; every call is recorded. */
	static final class StubActions implements PanelActions
	{
		static final String REPORT = "2h Why Lag report - the stub's fixed text";
		final List<Integer> ranges = new ArrayList<>();
		final List<PanelSnapshot> reported = new ArrayList<>();
		int activated;

		@Override
		public void rangeChanged(int minutes)
		{
			ranges.add(minutes);
		}

		@Override
		public void activated()
		{
			activated++;
		}

		@Override
		public String report(PanelSnapshot s)
		{
			reported.add(s);
			return REPORT;
		}
	}

	// ============================================================================ the panel

	/** A panel of the fixture, both rows folded or both open, outside developer mode. */
	static WhyLagPanel panel(Fixture f, boolean open)
	{
		return panel(f, open, open, false, new StubActions());
	}

	/**
	 * A panel on the Swing thread: built at the fixture's range, activated, shown the fixture, its row selected,
	 * folded as asked and laid out. The clipboard is a fake that takes every text.
	 */
	static WhyLagPanel panel(Fixture f, boolean graphsOpen, boolean lagsOpen, boolean developerMode,
		StubActions actions)
	{
		return onEdt(() ->
		{
			final WhyLagPanel p = new WhyLagPanel(actions, GraphRange.of(f.snapshot.rangeMinutes), developerMode);
			p.clipboard = text -> true;
			p.onActivate();
			p.show(f.snapshot);
			if (f.selected >= 0)
			{
				p.select(f.selected);
			}
			p.fold(graphsOpen, lagsOpen);
			layOut(p);
			return p;
		});
	}

	/** Sizes the panel to its preferred size and places its blocks (a panel never shown has no peer to validate). */
	static void layOut(WhyLagPanel p)
	{
		p.setSize(p.getPreferredSize());
		p.doLayout();
	}

	/** The blocks of the panel's layout, top down. */
	static List<JComponent> blocks(WhyLagPanel p)
	{
		final List<JComponent> out = new ArrayList<>();
		for (Component c : p.getComponents())
		{
			out.add((JComponent) c);
		}
		return out;
	}

	/** The content height: the panel's preferred height less its border. */
	static int contentHeight(WhyLagPanel p)
	{
		return p.getPreferredSize().height - p.getInsets().top - p.getInsets().bottom;
	}

	/** A block's y in the content (under the panel's border). */
	static int yOf(WhyLagPanel p, JComponent block)
	{
		return block.getY() - p.getInsets().top;
	}

	// ============================================================================ Swing

	interface EdtAction
	{
		void run() throws Exception;
	}

	/** Runs on the Swing thread and waits; a failure there is thrown here. */
	static void edt(EdtAction action)
	{
		onEdt(() ->
		{
			action.run();
			return null;
		});
	}

	/** Answers {@code call} run on the Swing thread; a failure there is thrown here. */
	static <T> T onEdt(Callable<T> call)
	{
		if (SwingUtilities.isEventDispatchThread())
		{
			return callNow(call);
		}
		final List<T> result = new ArrayList<>();
		final Throwable[] failure = new Throwable[1];
		try
		{
			SwingUtilities.invokeAndWait(() ->
			{
				try
				{
					result.add(call.call());
				}
				catch (Throwable t)
				{
					failure[0] = t;
				}
			});
		}
		catch (InterruptedException e)
		{
			Thread.currentThread().interrupt();
			throw new AssertionError(e);
		}
		catch (InvocationTargetException e)
		{
			throw new AssertionError(e.getCause());
		}
		if (failure[0] instanceof Error)
		{
			throw (Error) failure[0];
		}
		if (failure[0] instanceof RuntimeException)
		{
			throw (RuntimeException) failure[0];
		}
		if (failure[0] != null)
		{
			throw new AssertionError(failure[0]);
		}
		return result.get(0);
	}

	private static <T> T callNow(Callable<T> call)
	{
		try
		{
			return call.call();
		}
		catch (RuntimeException e)
		{
			throw e;
		}
		catch (Exception e)
		{
			throw new AssertionError(e);
		}
	}

	/** A left-button press at (x, y) of a component, on the Swing thread. */
	static void press(Component c, int x, int y)
	{
		edt(() -> c.dispatchEvent(new MouseEvent(c, MouseEvent.MOUSE_PRESSED, System.currentTimeMillis(),
			InputEvent.BUTTON1_DOWN_MASK, x, y, 1, false, MouseEvent.BUTTON1)));
	}

	/** The tooltip a component gives at (x, y). */
	static String tip(JComponent c, int x, int y)
	{
		return onEdt(() -> c.getToolTipText(new MouseEvent(c, MouseEvent.MOUSE_MOVED, 0, 0, x, y, 0, false)));
	}

	// ============================================================================ painting

	/** A block painted as Swing paints it, at its preferred size. */
	static BufferedImage paint(JComponent c)
	{
		return onEdt(() ->
		{
			final Dimension d = c.getPreferredSize();
			c.setSize(d);
			final BufferedImage img = new BufferedImage(d.width, d.height, BufferedImage.TYPE_INT_ARGB);
			final Graphics2D g = img.createGraphics();
			try
			{
				c.paint(g);
			}
			finally
			{
				g.dispose();
			}
			return img;
		});
	}

	/** A whole panel painted as Swing paints it, blocks and all. */
	static BufferedImage paintPanel(WhyLagPanel p)
	{
		return onEdt(() ->
		{
			layOut(p);
			final BufferedImage img = new BufferedImage(p.getWidth(), p.getHeight(), BufferedImage.TYPE_INT_ARGB);
			final Graphics2D g = img.createGraphics();
			try
			{
				p.paint(g);
			}
			finally
			{
				g.dispose();
			}
			return img;
		});
	}

	/**
	 * Sizes the panel to {@code width} and to its preferred height at that width, and places its blocks. The panel is
	 * sized twice: the first size makes the blocks measure at the new width, the second gives it the height they
	 * then need (a wrapping block may be shorter in a wider panel).
	 */
	static void layOutAt(WhyLagPanel p, int width)
	{
		p.setSize(width, 0);
		p.setSize(width, p.getPreferredSize().height);
		p.doLayout();
	}

	/** A whole panel {@code width} wide, painted as Swing paints it, blocks and all. */
	static BufferedImage paintPanelAt(WhyLagPanel p, int width)
	{
		return onEdt(() ->
		{
			layOutAt(p, width);
			final BufferedImage img = new BufferedImage(p.getWidth(), p.getHeight(), BufferedImage.TYPE_INT_ARGB);
			final Graphics2D g = img.createGraphics();
			try
			{
				p.paint(g);
			}
			finally
			{
				g.dispose();
			}
			return img;
		});
	}

	/**
	 * A block's own painting with NO clip, into an image {@code width} wide and 20 px taller than the block that
	 * starts as {@link #SENTINEL}: whatever the block paints past its bounds shows.
	 */
	static BufferedImage paintUnclipped(JComponent c, int width)
	{
		return onEdt(() ->
		{
			final Dimension d = c.getPreferredSize();
			c.setSize(d);
			final BufferedImage img = new BufferedImage(width, d.height + 20, BufferedImage.TYPE_INT_ARGB);
			final Graphics2D g = img.createGraphics();
			try
			{
				g.setColor(SENTINEL);
				g.fillRect(0, 0, img.getWidth(), img.getHeight());
				paintComponent(c, g);
			}
			finally
			{
				g.dispose();
			}
			return img;
		});
	}

	/** The strings a block draws, each with its font, colour and the ground under it, painted with no clip. */
	static List<Drawn> record(JComponent c)
	{
		return onEdt(() ->
		{
			final Dimension d = c.getPreferredSize();
			c.setSize(d);
			final BufferedImage img = new BufferedImage(Math.max(1, d.width + 40), Math.max(1, d.height + 20),
				BufferedImage.TYPE_INT_ARGB);
			final Recorder r = new Recorder(img);
			try
			{
				paintComponent(c, r);
			}
			finally
			{
				r.dispose();
			}
			return r.drawn;
		});
	}

	/** Calls the block's own {@code paintComponent} (protected; reached by reflection from this package). */
	static void paintComponent(JComponent c, Graphics g) throws Exception
	{
		Class<?> k = c.getClass();
		while (k != null)
		{
			try
			{
				final Method m = k.getDeclaredMethod("paintComponent", Graphics.class);
				m.setAccessible(true);
				m.invoke(c, g);
				return;
			}
			catch (NoSuchMethodException e)
			{
				k = k.getSuperclass();
			}
			catch (InvocationTargetException e)
			{
				if (e.getCause() instanceof Error)
				{
					throw (Error) e.getCause();
				}
				throw (Exception) e.getCause();
			}
		}
		throw new AssertionError("no paintComponent on " + c.getClass());
	}

	static Color pixel(BufferedImage img, int x, int y)
	{
		return new Color(img.getRGB(x, y), true);
	}

	/** True when the pixel is exactly that colour, alpha included. */
	static boolean is(BufferedImage img, int x, int y, Color c)
	{
		return img.getRGB(x, y) == c.getRGB();
	}

	/** {@code top} laid over an opaque {@code ground}, as SrcOver paints it. */
	static Color over(Color top, Color ground)
	{
		final BufferedImage img = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
		final Graphics2D g = img.createGraphics();
		try
		{
			g.setColor(ground);
			g.fillRect(0, 0, 1, 1);
			g.setColor(top);
			g.fillRect(0, 0, 1, 1);
		}
		finally
		{
			g.dispose();
		}
		return pixel(img, 0, 0);
	}

	// ============================================================================ contrast

	/** The WCAG 2 contrast ratio of two opaque colours, 1 .. 21. */
	static double contrast(Color a, Color b)
	{
		final double la = luminance(a);
		final double lb = luminance(b);
		return (Math.max(la, lb) + 0.05) / (Math.min(la, lb) + 0.05);
	}

	static double luminance(Color c)
	{
		return 0.2126 * channel(c.getRed()) + 0.7152 * channel(c.getGreen()) + 0.0722 * channel(c.getBlue());
	}

	private static double channel(int v)
	{
		final double s = v / 255.0;
		return s <= 0.03928 ? s / 12.92 : Math.pow((s + 0.055) / 1.055, 2.4);
	}

	// ============================================================================ files

	/** The project folder: the first folder above the working directory that holds {@code src/main/java/com/whylag}. */
	static Path projectRoot()
	{
		for (Path dir = Paths.get("").toAbsolutePath(); dir != null; dir = dir.getParent())
		{
			if (Files.isDirectory(dir.resolve(Paths.get("src", "main", "java", "com", "whylag"))))
			{
				return dir;
			}
		}
		throw new AssertionError("no src/main/java/com/whylag above " + Paths.get("").toAbsolutePath());
	}

	// ============================================================================ counting repaints

	/** A repaint manager that counts, per component, repaints asked and layouts asked (revalidate). */
	static final class Counting extends RepaintManager
	{
		private final Map<Component, Integer> dirty = new IdentityHashMap<>();
		private final Map<Component, Integer> invalid = new IdentityHashMap<>();
		private RepaintManager before;

		/** Installs a counter for the Swing thread's context; {@link #close} puts the old manager back. */
		static Counting install()
		{
			return onEdt(() ->
			{
				final Counting c = new Counting();
				c.before = RepaintManager.currentManager((Component) null);
				RepaintManager.setCurrentManager(c);
				return c;
			});
		}

		void close()
		{
			edt(() -> RepaintManager.setCurrentManager(before));
		}

		@Override
		public synchronized void addDirtyRegion(JComponent c, int x, int y, int w, int h)
		{
			dirty.merge(c, 1, Integer::sum);
			super.addDirtyRegion(c, x, y, w, h);
		}

		@Override
		public synchronized void addInvalidComponent(JComponent c)
		{
			invalid.merge(c, 1, Integer::sum);
			super.addInvalidComponent(c);
		}

		synchronized int repaints(Component c)
		{
			return dirty.getOrDefault(c, 0);
		}

		/**
		 * Every repaint asked of one panel: of the panel itself and of each of its blocks, the folded ones included.
		 * Components of other panels (a timer another test left running) do not count.
		 */
		synchronized int repaintsOf(WhyLagPanel p)
		{
			return countOf(dirty, p);
		}

		/** Every layout (revalidate) asked of one panel or of any of its blocks. */
		synchronized int layoutsOf(WhyLagPanel p)
		{
			return countOf(invalid, p);
		}

		private static int countOf(Map<Component, Integer> counts, WhyLagPanel p)
		{
			final List<Component> mine = new ArrayList<>(Arrays.asList(p, p.header(), p.card(), p.cells(),
				p.rangeRow(), p.graphsRow(), p.strips(), p.lagsRow(), p.eventList(), p.sessionHeader(), p.counts(),
				p.buttons()));
			if (p.footer() != null)
			{
				mine.add(p.footer());
			}
			int n = 0;
			for (Component c : mine)
			{
				n += counts.getOrDefault(c, 0);
			}
			return n;
		}
	}

	// ============================================================================ recording text

	/** One string drawn: where, in what font and colour, and the ground under its box when it was drawn. */
	static final class Drawn
	{
		final String text;
		final int x;
		final int y;
		final Font font;
		/** The colour as it lands: the paint's colour with the composite's alpha folded into its alpha. */
		final Color colour;
		final Color ground;
		final int width;

		Drawn(String text, int x, int y, Font font, Color colour, Color ground, int width)
		{
			this.text = text;
			this.x = x;
			this.y = y;
			this.font = font;
			this.colour = colour;
			this.ground = ground;
			this.width = width;
		}

		/** The first column right of the text: its box is x .. right - 1. */
		int right()
		{
			return x + width;
		}

		/** The colour as seen: laid over its ground. */
		Color seen()
		{
			return over(colour, ground);
		}

		@Override
		public String toString()
		{
			return "\"" + text + "\" at " + x + "," + y + " in " + font.getFontName() + " " + font.getSize();
		}
	}

	/**
	 * A {@link Graphics2D} over an image that draws everything as the real one does and records every string: its
	 * place, font, colour (with the composite's alpha) and the commonest colour of the image under its box at that
	 * moment - the ground the text is laid on. Only translation is expected in the transform.
	 */
	static final class Recorder extends Graphics2D
	{
		private final BufferedImage image;
		private final Graphics2D g;
		final List<Drawn> drawn;

		Recorder(BufferedImage image)
		{
			this(image, image.createGraphics(), new ArrayList<>());
		}

		private Recorder(BufferedImage image, Graphics2D g, List<Drawn> drawn)
		{
			this.image = image;
			this.g = g;
			this.drawn = drawn;
		}

		private void record(String s, int x, int y)
		{
			if (s == null || s.isEmpty())
			{
				return;
			}
			final Font font = g.getFont();
			final FontMetrics fm = g.getFontMetrics(font);
			final int w = fm.stringWidth(s);
			final Color c = g.getColor();
			float alpha = c.getAlpha() / 255f;
			final Composite comp = g.getComposite();
			if (comp instanceof AlphaComposite)
			{
				alpha *= ((AlphaComposite) comp).getAlpha();
			}
			final Color landed = new Color(c.getRed(), c.getGreen(), c.getBlue(), Math.round(alpha * 255));
			drawn.add(new Drawn(s, x, y, font, landed, groundUnder(x, y - fm.getAscent(), w,
				fm.getAscent() + fm.getDescent()), w));
		}

		private Color groundUnder(int x, int y, int w, int h)
		{
			final AffineTransform t = g.getTransform();
			final int x0 = (int) Math.round(x + t.getTranslateX());
			final int y0 = (int) Math.round(y + t.getTranslateY());
			final Map<Integer, Integer> seen = new HashMap<>();
			for (int yy = Math.max(0, y0); yy < Math.min(image.getHeight(), y0 + h); yy++)
			{
				for (int xx = Math.max(0, x0); xx < Math.min(image.getWidth(), x0 + w); xx++)
				{
					seen.merge(image.getRGB(xx, yy), 1, Integer::sum);
				}
			}
			int best = 0;
			int most = -1;
			for (Map.Entry<Integer, Integer> e : seen.entrySet())
			{
				if (e.getValue() > most)
				{
					most = e.getValue();
					best = e.getKey();
				}
			}
			return new Color(best, true);
		}

		@Override
		public void drawString(String str, int x, int y)
		{
			record(str, x, y);
			g.drawString(str, x, y);
		}

		@Override
		public void drawString(String str, float x, float y)
		{
			record(str, Math.round(x), Math.round(y));
			g.drawString(str, x, y);
		}

		@Override
		public void drawString(AttributedCharacterIterator iterator, int x, int y)
		{
			throw new AssertionError("the panel draws plain strings only");
		}

		@Override
		public void drawString(AttributedCharacterIterator iterator, float x, float y)
		{
			throw new AssertionError("the panel draws plain strings only");
		}

		@Override
		public void drawGlyphVector(GlyphVector gv, float x, float y)
		{
			throw new AssertionError("the panel draws plain strings only");
		}

		@Override
		public Graphics create()
		{
			return new Recorder(image, (Graphics2D) g.create(), drawn);
		}

		@Override
		public void draw(java.awt.Shape s)
		{
			g.draw(s);
		}

		@Override
		public boolean drawImage(Image img, AffineTransform xform, ImageObserver obs)
		{
			return g.drawImage(img, xform, obs);
		}

		@Override
		public void drawImage(BufferedImage img, BufferedImageOp op, int x, int y)
		{
			g.drawImage(img, op, x, y);
		}

		@Override
		public void drawRenderedImage(RenderedImage img, AffineTransform xform)
		{
			g.drawRenderedImage(img, xform);
		}

		@Override
		public void drawRenderableImage(RenderableImage img, AffineTransform xform)
		{
			g.drawRenderableImage(img, xform);
		}

		@Override
		public void fill(java.awt.Shape s)
		{
			g.fill(s);
		}

		@Override
		public boolean hit(Rectangle rect, java.awt.Shape s, boolean onStroke)
		{
			return g.hit(rect, s, onStroke);
		}

		@Override
		public GraphicsConfiguration getDeviceConfiguration()
		{
			return g.getDeviceConfiguration();
		}

		@Override
		public void setComposite(Composite comp)
		{
			g.setComposite(comp);
		}

		@Override
		public void setPaint(Paint paint)
		{
			g.setPaint(paint);
		}

		@Override
		public void setStroke(Stroke s)
		{
			g.setStroke(s);
		}

		@Override
		public void setRenderingHint(RenderingHints.Key hintKey, Object hintValue)
		{
			g.setRenderingHint(hintKey, hintValue);
		}

		@Override
		public Object getRenderingHint(RenderingHints.Key hintKey)
		{
			return g.getRenderingHint(hintKey);
		}

		@Override
		public void setRenderingHints(Map<?, ?> hints)
		{
			g.setRenderingHints(hints);
		}

		@Override
		public void addRenderingHints(Map<?, ?> hints)
		{
			g.addRenderingHints(hints);
		}

		@Override
		public RenderingHints getRenderingHints()
		{
			return g.getRenderingHints();
		}

		@Override
		public void translate(int x, int y)
		{
			g.translate(x, y);
		}

		@Override
		public void translate(double tx, double ty)
		{
			g.translate(tx, ty);
		}

		@Override
		public void rotate(double theta)
		{
			g.rotate(theta);
		}

		@Override
		public void rotate(double theta, double x, double y)
		{
			g.rotate(theta, x, y);
		}

		@Override
		public void scale(double sx, double sy)
		{
			g.scale(sx, sy);
		}

		@Override
		public void shear(double shx, double shy)
		{
			g.shear(shx, shy);
		}

		@Override
		public void transform(AffineTransform tx)
		{
			g.transform(tx);
		}

		@Override
		public void setTransform(AffineTransform tx)
		{
			g.setTransform(tx);
		}

		@Override
		public AffineTransform getTransform()
		{
			return g.getTransform();
		}

		@Override
		public Paint getPaint()
		{
			return g.getPaint();
		}

		@Override
		public Composite getComposite()
		{
			return g.getComposite();
		}

		@Override
		public void setBackground(Color color)
		{
			g.setBackground(color);
		}

		@Override
		public Color getBackground()
		{
			return g.getBackground();
		}

		@Override
		public Stroke getStroke()
		{
			return g.getStroke();
		}

		@Override
		public void clip(java.awt.Shape s)
		{
			g.clip(s);
		}

		@Override
		public FontRenderContext getFontRenderContext()
		{
			return g.getFontRenderContext();
		}

		@Override
		public Color getColor()
		{
			return g.getColor();
		}

		@Override
		public void setColor(Color c)
		{
			g.setColor(c);
		}

		@Override
		public void setPaintMode()
		{
			g.setPaintMode();
		}

		@Override
		public void setXORMode(Color c1)
		{
			g.setXORMode(c1);
		}

		@Override
		public Font getFont()
		{
			return g.getFont();
		}

		@Override
		public void setFont(Font font)
		{
			g.setFont(font);
		}

		@Override
		public FontMetrics getFontMetrics(Font f)
		{
			return g.getFontMetrics(f);
		}

		@Override
		public Rectangle getClipBounds()
		{
			return g.getClipBounds();
		}

		@Override
		public void clipRect(int x, int y, int width, int height)
		{
			g.clipRect(x, y, width, height);
		}

		@Override
		public void setClip(int x, int y, int width, int height)
		{
			g.setClip(x, y, width, height);
		}

		@Override
		public java.awt.Shape getClip()
		{
			return g.getClip();
		}

		@Override
		public void setClip(java.awt.Shape clip)
		{
			g.setClip(clip);
		}

		@Override
		public void copyArea(int x, int y, int width, int height, int dx, int dy)
		{
			g.copyArea(x, y, width, height, dx, dy);
		}

		@Override
		public void drawLine(int x1, int y1, int x2, int y2)
		{
			g.drawLine(x1, y1, x2, y2);
		}

		@Override
		public void fillRect(int x, int y, int width, int height)
		{
			g.fillRect(x, y, width, height);
		}

		@Override
		public void clearRect(int x, int y, int width, int height)
		{
			g.clearRect(x, y, width, height);
		}

		@Override
		public void drawRoundRect(int x, int y, int width, int height, int arcWidth, int arcHeight)
		{
			g.drawRoundRect(x, y, width, height, arcWidth, arcHeight);
		}

		@Override
		public void fillRoundRect(int x, int y, int width, int height, int arcWidth, int arcHeight)
		{
			g.fillRoundRect(x, y, width, height, arcWidth, arcHeight);
		}

		@Override
		public void drawOval(int x, int y, int width, int height)
		{
			g.drawOval(x, y, width, height);
		}

		@Override
		public void fillOval(int x, int y, int width, int height)
		{
			g.fillOval(x, y, width, height);
		}

		@Override
		public void drawArc(int x, int y, int width, int height, int startAngle, int arcAngle)
		{
			g.drawArc(x, y, width, height, startAngle, arcAngle);
		}

		@Override
		public void fillArc(int x, int y, int width, int height, int startAngle, int arcAngle)
		{
			g.fillArc(x, y, width, height, startAngle, arcAngle);
		}

		@Override
		public void drawPolyline(int[] xPoints, int[] yPoints, int nPoints)
		{
			g.drawPolyline(xPoints, yPoints, nPoints);
		}

		@Override
		public void drawPolygon(int[] xPoints, int[] yPoints, int nPoints)
		{
			g.drawPolygon(xPoints, yPoints, nPoints);
		}

		@Override
		public void fillPolygon(int[] xPoints, int[] yPoints, int nPoints)
		{
			g.fillPolygon(xPoints, yPoints, nPoints);
		}

		@Override
		public boolean drawImage(Image img, int x, int y, ImageObserver observer)
		{
			return g.drawImage(img, x, y, observer);
		}

		@Override
		public boolean drawImage(Image img, int x, int y, int width, int height, ImageObserver observer)
		{
			return g.drawImage(img, x, y, width, height, observer);
		}

		@Override
		public boolean drawImage(Image img, int x, int y, Color bgcolor, ImageObserver observer)
		{
			return g.drawImage(img, x, y, bgcolor, observer);
		}

		@Override
		public boolean drawImage(Image img, int x, int y, int width, int height, Color bgcolor,
			ImageObserver observer)
		{
			return g.drawImage(img, x, y, width, height, bgcolor, observer);
		}

		@Override
		public boolean drawImage(Image img, int dx1, int dy1, int dx2, int dy2, int sx1, int sy1, int sx2, int sy2,
			ImageObserver observer)
		{
			return g.drawImage(img, dx1, dy1, dx2, dy2, sx1, sy1, sx2, sy2, observer);
		}

		@Override
		public boolean drawImage(Image img, int dx1, int dy1, int dx2, int dy2, int sx1, int sy1, int sx2, int sy2,
			Color bgcolor, ImageObserver observer)
		{
			return g.drawImage(img, dx1, dy1, dx2, dy2, sx1, sy1, sx2, sy2, bgcolor, observer);
		}

		@Override
		public void dispose()
		{
			g.dispose();
		}
	}
}
