package com.whylag.core;

import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * The five cells of the panel of picture 18 (contract 3.8, 5.2): the picture's quiet cells and its selected lag, with
 * the WORST tick in the Tick cell and the culprit marked at the verdict's level; no culprit for a "Not sure" event;
 * a dash with the reason in the tip; numbers held to 9,999; the CPU cell with one half, both and neither, and while
 * not logged in (the snapshot's world 0: "CPU: Not logged in", never "No CPU data on this PC"); every tip of 5.2's
 * two tables; and the five in {@link Lane} order.
 */
public class CellsTest
{
	/** The plus-minus sign the Server ticks tile's small line starts with, as in "plus-minus 13 ms" (contract 5.2). */
	private static final String PLUS_MINUS = "\u00b1";

	/** The picture's quiet numbers: 50 fps, 600 ms, 41 ms (was 39), 51 %, the game at 42 % of a PC at 24 %. */
	static PanelSnapshot quietSnapshot()
	{
		return snapshot(new Tile[] {
			new Tile(Lane.FRAME_RATE, Level.OK, "50 fps", "worst 35 ms", NoData.NONE),
			new Tile(Lane.TICKS, Level.OK, "600 ms", PLUS_MINUS + "13 ms", NoData.NONE),
			new Tile(Lane.PING, Level.OK, "41 ms", "was 39 ms", NoData.NONE),
			new Tile(Lane.MEMORY, Level.OK, "51 %", "pause 23 ms", NoData.NONE)}, 24, 42);
	}

	@Test
	public void nowOfThePicturesQuietSnapshot()
	{
		final Cell[] c = Cells.now(quietSnapshot());
		assertCell(c[0], Lane.FRAME_RATE, "FPS", "50", "fps", Level.OK, false);
		assertCell(c[1], Lane.TICKS, "Tick", "600", "ms", Level.OK, false);
		assertCell(c[2], Lane.PING, "Ping", "41", "ms", Level.OK, false);
		assertCell(c[3], Lane.MEMORY, "Mem", "51%", "used", Level.OK, false);
		assertCell(c[4], Lane.CPU, "CPU", "42%", "PC 24", Level.OK, false);
	}

	/** A cell takes its tile's level; a busy PC colours the CPU cell by the whole PC, never by the game's share. */
	@Test
	public void nowTakesTheTilesLevels()
	{
		final Cell[] c = Cells.now(snapshot(new Tile[] {
			new Tile(Lane.FRAME_RATE, Level.BAD, "12 fps", "worst 480 ms", NoData.NONE),
			new Tile(Lane.TICKS, Level.WARN, "672 ms", PLUS_MINUS + "72 ms", NoData.NONE),
			new Tile(Lane.PING, Level.BAD, "310 ms", "was 41 ms", NoData.NONE),
			new Tile(Lane.MEMORY, Level.WARN, "88 %", "pause 140 ms", NoData.NONE)}, 96, 10));
		assertEquals(Level.BAD, c[0].level);
		assertEquals(Level.WARN, c[1].level);
		assertEquals(Level.BAD, c[2].level);
		assertEquals(Level.WARN, c[3].level);
		assertEquals("PC 96", Level.BAD, c[4].level);
		assertEquals(Level.OK, Cells.now(snapshot(quietSnapshot().tiles, 20, 100))[4].level);
		for (Cell cell : c)
		{
			assertFalse("no cell of now is a culprit", cell.culprit);
		}
	}

	/** The picture's selected lag: the Tick cell is the WORST tick, 1,240, and it is the one culprit, at BAD. */
	@Test
	public void ofThePicturesEvent()
	{
		final Cell[] c = Cells.of(EventViewTest.picturesEvent().withVerdict(verdict(Cause.SLOW_WORLD)));
		assertCell(c[0], Lane.FRAME_RATE, "FPS", "50", "fps", Level.OK, false);
		assertCell(c[1], Lane.TICKS, "Tick", "1,240", "ms", Level.BAD, true);
		assertCell(c[2], Lane.PING, "Ping", "41", "ms", Level.OK, false);
		assertCell(c[3], Lane.MEMORY, "Mem", "79%", "used", Level.OK, false);
		assertCell(c[4], Lane.CPU, "CPU", "95%", "PC 37", Level.OK, false);
		assertEquals("one culprit", 1, culprits(c));
	}

	/**
	 * The culprit is the cell of the verdict's group's lane, at the verdict's level whatever its own number says, so
	 * its edge, its ground and its shape agree - also when its number is missing.
	 */
	@Test
	public void theCulpritTakesTheVerdictsLevel()
	{
		final LagEvent e = EventViewTest.picturesEvent();
		final Cell[] memory = Cells.of(e.withVerdict(verdict(Cause.GC_PAUSE)));
		assertCell(memory[3], Lane.MEMORY, "Mem", "79%", "used", Level.BAD, true);
		assertEquals("the memory tile alone says OK", Level.OK, EventView.tiles(e)[3].level);
		assertEquals(1, culprits(memory));
		assertEquals("the Tick cell keeps its own level", Level.BAD, memory[1].level);
		assertFalse(memory[1].culprit);

		final Cell[] line = Cells.of(e.withVerdict(verdict(Cause.UPLOAD_LOSS)));
		assertCell(line[2], Lane.PING, "Ping", "41", "ms", Level.BAD, true);
		assertEquals(1, culprits(line));

		final Cell[] frames = Cells.of(e.withVerdict(verdict(Cause.CLIENT_BUSY)));
		assertCell(frames[0], Lane.FRAME_RATE, "FPS", "50", "fps", Level.BAD, true);
		assertEquals(1, culprits(frames));

		final LagEvent noPing = event(50, 34, 952, 1240, 640, -1, 41, 607, 768, 22, 37, 95);
		final Cell[] blind = Cells.of(noPing.withVerdict(verdict(Cause.UPLOAD_LOSS)));
		assertCell(blind[2], Lane.PING, "Ping", "-", "", Level.BAD, true);
	}

	@Test
	public void aNotSureEventHasNoCulprit()
	{
		final LagEvent e = EventViewTest.picturesEvent();
		assertEquals("X: the group UNSURE has no lane", 0, culprits(Cells.of(e.withVerdict(verdict(Cause.NOT_SURE)))));
		assertEquals("N6 is not sure too", 0, culprits(Cells.of(e.withVerdict(verdict(Cause.DELIVERY_GAP)))));
		assertEquals("an event not judged yet counts as not sure", 0, culprits(Cells.of(e)));
		assertEquals("the group NONE has no lane", 0, culprits(Cells.of(e.withVerdict(verdict(Cause.ALL_CLEAR)))));
		assertEquals("its Tick cell keeps its own level", Level.BAD,
			Cells.of(e.withVerdict(verdict(Cause.NOT_SURE)))[1].level);
	}

	/** A tile's "-" is a dash cell: no unit, the hollow ring, and the reason in the tip, never painted in the cell. */
	@Test
	public void noDataIsADashWithTheReasonInTheTip()
	{
		final Tile[] quiet = quietSnapshot().tiles;
		final Cell stale = Cells.now(snapshot(new Tile[] {quiet[0], quiet[1],
			new Tile(Lane.PING, Level.NO_DATA, "-", NoData.STALE.reason(), NoData.STALE), quiet[3]}, 24, 42))[2];
		assertCell(stale, Lane.PING, "Ping", "-", "", Level.NO_DATA, false);
		assertEquals("Ping: Nothing sent", stale.tip);

		// Not logged in, as the snapshot builder writes it: world 0, every tile "Not logged in", both CPU numbers -1.
		final Tile[] out = new Tile[Lane.TILES];
		for (int i = 0; i < out.length; i++)
		{
			out[i] = new Tile(Lane.values()[i], Level.NO_DATA, "-", NoData.NOT_LOGGED_IN.reason(),
				NoData.NOT_LOGGED_IN);
		}
		final Cell[] loggedOut = Cells.now(snapshot(0, out, -1, -1));
		for (Cell c : loggedOut)
		{
			assertEquals(c.name, "-", c.value);
			assertEquals(c.name, "", c.unit);
			assertEquals(c.name, Level.NO_DATA, c.level);
		}
		assertEquals("Frame rate: Not logged in", loggedOut[0].tip);
		assertEquals("Ticks: Not logged in", loggedOut[1].tip);
		assertEquals("Ping: Not logged in", loggedOut[2].tip);
		assertEquals("Memory: Not logged in", loggedOut[3].tip);
		assertEquals("the CPU says why, as the other four do", "CPU: Not logged in", loggedOut[4].tip);

		final Cell forced = Cells.now(snapshot(new Tile[] {quiet[0], quiet[1],
			new Tile(Lane.PING, Level.BAD, "-", "Could not read it", NoData.ERROR), quiet[3]}, 24, 42))[2];
		assertEquals("a dash is always the hollow ring", Level.NO_DATA, forced.level);
		assertEquals("Ping: Could not read it", forced.tip);

		final Cell[] event = Cells.of(event(-1, -1, 952, 1240, 640, 41, 41, 607, 768, 22, 37, 95));
		assertCell(event[0], Lane.FRAME_RATE, "FPS", "-", "", Level.NO_DATA, false);
		assertEquals("an event's dash has no reason to give", "Frame rate: -", event[0].tip);
	}

	@Test
	public void numbersAreHeldToNineThousandNineHundredNinetyNine()
	{
		final Cell tick = Cells.of(event(50, 34, 952, 12400, 11800, 41, 41, 607, 768, 22, 37, 95))[1];
		assertEquals("9,999", tick.value);
		assertEquals("the tip keeps the true number", "Ticks: worst 12,400 ms, mean 952 ms", tick.tip);

		final Tile[] quiet = quietSnapshot().tiles;
		final Cell[] big = Cells.now(snapshot(new Tile[] {
			new Tile(Lane.FRAME_RATE, Level.OK, "12000 fps", "worst 35 ms", NoData.NONE),
			new Tile(Lane.TICKS, Level.BAD, "12,400 ms", "worst 12,400", NoData.NONE),
			new Tile(Lane.PING, Level.BAD, "9,999 ms", "was 39 ms", NoData.NONE), quiet[3]}, 24, 42));
		assertEquals("9,999", big[0].value);
		assertEquals("9,999", big[1].value);
		assertEquals("9,999", big[2].value);
		assertEquals("Ticks: 12,400 ms, worst 12,400", big[1].tip);

		final Cell[] small = Cells.now(snapshot(new Tile[] {
			new Tile(Lane.FRAME_RATE, Level.BAD, "0 fps", "worst 1,000 ms", NoData.NONE),
			new Tile(Lane.TICKS, Level.OK, "1,000 ms", "worst 1,000", NoData.NONE),
			new Tile(Lane.PING, Level.OK, "999 ms", "", NoData.NONE),
			new Tile(Lane.MEMORY, Level.OK, "100 %", "pause 0 ms", NoData.NONE)}, 100, 100));
		assertEquals("0", small[0].value);
		assertEquals("the commas are taken out and put back", "1,000", small[1].value);
		assertEquals("999", small[2].value);
		assertEquals("100%", small[3].value);
		assertEquals("100%", small[4].value);
		assertEquals("PC 100", small[4].unit);
	}

	/** The CPU cell: the game's share big, the whole PC under it; a half at -1 is left out, value and tip alike. */
	@Test
	public void theCpuCellWithOneHalf()
	{
		final Tile[] quiet = quietSnapshot().tiles;
		final Cell pcOnly = Cells.now(snapshot(quiet, 24, -1))[4];
		assertCell(pcOnly, Lane.CPU, "CPU", "-", "PC 24", Level.OK, false);
		assertEquals("CPU: PC 24 %", pcOnly.tip);

		final Cell gameOnly = Cells.now(snapshot(quiet, -1, 42))[4];
		assertCell(gameOnly, Lane.CPU, "CPU", "42%", "", Level.NO_DATA, false);
		assertEquals("CPU: game 42 %", gameOnly.tip);

		final Cell neither = Cells.now(snapshot(quiet, -1, -1))[4];
		assertCell(neither, Lane.CPU, "CPU", "-", "", Level.NO_DATA, false);
		assertEquals("CPU: No CPU data on this PC", neither.tip);

		final Cell eventPcOnly = Cells.of(event(50, 34, 952, 1240, 640, 41, 41, 607, 768, 22, 37, -1))[4];
		assertCell(eventPcOnly, Lane.CPU, "CPU", "-", "PC 37", Level.OK, false);
		final Cell eventNeither = Cells.of(event(50, 34, 952, 1240, 640, 41, 41, 607, 768, 22, -1, -1))[4];
		assertCell(eventNeither, Lane.CPU, "CPU", "-", "", Level.NO_DATA, false);
		assertEquals("CPU: No CPU data on this PC", eventNeither.tip);
		assertEquals("a busy PC is BAD", Level.BAD,
			Cells.of(event(50, 34, 952, 1240, 640, 41, 41, 607, 768, 22, 96, 10))[4].level);
	}

	/**
	 * Not logged in (the snapshot's world is 0, contract 5.2): the CPU cell is a dash like the four tile cells, with
	 * "CPU: Not logged in", whatever its two numbers hold - a -1 alone cannot say why. "No CPU data on this PC" is
	 * said only while logged in, and the same numbers while logged in give their values.
	 */
	@Test
	public void theCpuCellWhileNotLoggedIn()
	{
		final Tile[] quiet = quietSnapshot().tiles;
		for (int[] cpu : new int[][] {{-1, -1}, {24, 42}, {24, -1}, {-1, 42}})
		{
			final Cell c = Cells.now(snapshot(0, quiet, cpu[0], cpu[1]))[4];
			assertCell(c, Lane.CPU, "CPU", "-", "", Level.NO_DATA, false);
			assertEquals("PC " + cpu[0] + ", game " + cpu[1], "CPU: Not logged in", c.tip);
		}

		final Cell loggedIn = Cells.now(snapshot(416, quiet, 24, 42))[4];
		assertCell(loggedIn, Lane.CPU, "CPU", "42%", "PC 24", Level.OK, false);
		assertEquals("CPU: game 42 %, PC 24 %", loggedIn.tip);
		final Cell noCpuData = Cells.now(snapshot(416, quiet, -1, -1))[4];
		assertCell(noCpuData, Lane.CPU, "CPU", "-", "", Level.NO_DATA, false);
		assertEquals("logged in with no CPU data", "CPU: No CPU data on this PC", noCpuData.tip);
	}

	/** Each tip of 5.2's two tables, and the parts left out when there is nothing to say. */
	@Test
	public void tips()
	{
		final Cell[] now = Cells.now(quietSnapshot());
		assertEquals("Frame rate: 50 fps, worst 35 ms", now[0].tip);
		assertEquals("Ticks: 600 ms, " + PLUS_MINUS + "13 ms", now[1].tip);
		assertEquals("Ping: 41 ms, was 39 ms", now[2].tip);
		assertEquals("Memory: 51 %, pause 23 ms", now[3].tip);
		assertEquals("CPU: game 42 %, PC 24 %", now[4].tip);

		final Cell[] of = Cells.of(EventViewTest.picturesEvent().withVerdict(verdict(Cause.SLOW_WORLD)));
		assertEquals("Frame rate: 50 fps, worst 34 ms", of[0].tip);
		assertEquals("Ticks: worst 1,240 ms, mean 952 ms", of[1].tip);
		assertEquals("Ping: 41 ms, was 41 ms", of[2].tip);
		assertEquals("Memory: 79 %, pause 22 ms", of[3].tip);
		assertEquals("CPU: game 95 %, PC 37 %", of[4].tip);

		final Tile[] quiet = quietSnapshot().tiles;
		final Cell noUsual = Cells.now(snapshot(new Tile[] {quiet[0], quiet[1],
			new Tile(Lane.PING, Level.OK, "41 ms", "", NoData.NONE), quiet[3]}, 24, 42))[2];
		assertEquals("no small line: no comma", "Ping: 41 ms", noUsual.tip);

		assertEquals("Ticks: worst 1,240 ms",
			Cells.of(event(50, 34, -1, 1240, 640, 41, 41, 607, 768, 22, 37, 95))[1].tip);
		final Cell meanOnly = Cells.of(event(50, 34, 952, -1, -1, 41, 41, 607, 768, 22, 37, 95))[1];
		assertEquals("Ticks: mean 952 ms", meanOnly.tip);
		assertEquals("no worst tick: a dash", "-", meanOnly.value);
		assertEquals("", meanOnly.unit);
		assertEquals(Level.NO_DATA, meanOnly.level);
		assertEquals("Ticks: -", Cells.of(event(50, 34, -1, -1, -1, 41, 41, 607, 768, 22, 37, 95))[1].tip);
		assertEquals("Memory: 79 %, pause n/a",
			Cells.of(event(50, 34, 952, 1240, 640, 41, 41, 607, 768, -1, 37, 95))[3].tip);
	}

	/** An event's Tick cell prints the worst gap, at the level of its worst CORRECTED deviation (C5). */
	@Test
	public void theEventsTickCellIsTheWorstGapAtItsCorrectedLevel()
	{
		final Cell paced = Cells.of(event(50, 34, 610, 640, 20, 41, 41, 607, 768, 22, 37, 95))[1];
		assertEquals("640", paced.value);
		assertEquals("20 ms after the frame time is taken off", Level.OK, paced.level);
		// TICK_WARN_MS corrected (200 since the first live look) is WARN; 80 is OK now.
		assertEquals(Level.WARN, Cells.of(event(50, 34, 610, 600 + Thresholds.TICK_WARN_MS + 22,
			Thresholds.TICK_WARN_MS, 41, 41, 607, 768, 22, 37, 95))[1].level);
		assertEquals(Level.OK, Cells.of(event(50, 34, 610, 700, 80, 41, 41, 607, 768, 22, 37, 95))[1].level);
	}

	@Test
	public void fiveCellsInLaneOrder()
	{
		final String[] names = {"FPS", "Tick", "Ping", "Mem", "CPU"};
		for (Cell[] cells : new Cell[][] {Cells.now(quietSnapshot()), Cells.of(EventViewTest.picturesEvent()),
			Cells.now(snapshot(new Tile[0], -1, -1))})
		{
			assertEquals(5, cells.length);
			assertEquals(Lane.values().length, cells.length);
			for (int i = 0; i < cells.length; i++)
			{
				assertEquals(Lane.values()[i], cells[i].lane);
				assertEquals(names[i], cells[i].name);
			}
		}
	}

	/** A snapshot with no tiles (only a test builds one) gives four dashes and never throws. */
	@Test
	public void missingTilesAreDashes()
	{
		final Cell[] c = Cells.now(snapshot(null, 24, 42));
		for (int i = 0; i < Lane.TILES; i++)
		{
			assertEquals("-", c[i].value);
			assertEquals(Level.NO_DATA, c[i].level);
			assertEquals(Lane.values()[i].label() + ": -", c[i].tip);
		}
		assertEquals("42%", c[4].value);
	}

	@Test
	public void nullTextsAreEmpty()
	{
		final Cell c = new Cell(Lane.PING, null, null, null, Level.NO_DATA, false, null);
		assertEquals("", c.name);
		assertEquals("", c.value);
		assertEquals("", c.unit);
		assertEquals("", c.tip);
		assertTrue(new Cell(Lane.CPU, "CPU", "42%", "PC 24", Level.OK, true, "t").culprit);
	}

	// ---------------------------------------------------------------- helpers

	private static void assertCell(Cell c, Lane lane, String name, String value, String unit, Level level,
		boolean culprit)
	{
		assertEquals(name + ": lane", lane, c.lane);
		assertEquals(name + ": name", name, c.name);
		assertEquals(name + ": value", value, c.value);
		assertEquals(name + ": unit", unit, c.unit);
		assertEquals(name + ": level", level, c.level);
		assertEquals(name + ": culprit", culprit, c.culprit);
	}

	private static int culprits(Cell[] cells)
	{
		int n = 0;
		for (Cell c : cells)
		{
			n += c.culprit ? 1 : 0;
		}
		return n;
	}

	/** A logged-in snapshot, on world 416. */
	private static PanelSnapshot snapshot(Tile[] tiles, int sysCpuPct, int gameBusyPct)
	{
		return snapshot(416, tiles, sysCpuPct, gameBusyPct);
	}

	/** A snapshot on that world; 0 = not logged in (contract 3.8). */
	private static PanelSnapshot snapshot(int world, Tile[] tiles, int sysCpuPct, int gameBusyPct)
	{
		return new PanelSnapshot(0, null, world, null, tiles, 10, 0, 0, new Strip[0], null, null,
			new int[Group.values().length], 0, 0, sysCpuPct, gameBusyPct, null, "");
	}

	/** An event's verdict of that cause: BAD, as every event rule's is (contract 6.4). */
	private static Verdict verdict(Cause c)
	{
		return new Verdict(c, Confidence.LIKELY, Level.BAD, "headline", "proof", "fix", "", 1, 14, 416, 7, null,
			null);
	}

	private static LagEvent event(int fps, int worstFrameMs, int meanTickGapMs, int worstTickGapMs,
		int worstCorrectedTickMs, int rttMs, int rttBeforeMs, int heapUsedMb, int heapMaxMb, int gcPauseMs,
		int sysCpuPct, int gameBusyPct)
	{
		return new LagEvent(7, 100, 113, 0, Trigger.TICK_OFF.bit(), Trigger.TICK_OFF, 416, 0, 0, 0, fps,
			worstFrameMs, meanTickGapMs, worstTickGapMs, worstCorrectedTickMs, rttMs, rttMs, rttBeforeMs, 900, 0,
			gcPauseMs, heapUsedMb, heapMaxMb, sysCpuPct, gameBusyPct, false, false, null);
	}
}
