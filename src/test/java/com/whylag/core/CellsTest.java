package com.whylag.core;

import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * The three cells of the panel of picture 18 (contract 3.8, 5.2): the picture's quiet cells and its selected lag,
 * with the WORST tick in the Tick cell and the culprit marked at the verdict's level; no culprit for a "Not sure"
 * event; a dash with the reason in the tip; numbers held to 9,999; every tip of 5.2's two tables; and the three in
 * {@link Lane} order. The memory and CPU cells are gone (1.0.0, the Hub's rule).
 */
public class CellsTest
{
	/** The plus-minus sign the Server ticks tile's small line starts with, as in "plus-minus 13 ms" (contract 5.2). */
	private static final String PLUS_MINUS = "±";

	/** The picture's quiet numbers: 50 fps, 600 ms, 41 ms (was 39). */
	static PanelSnapshot quietSnapshot()
	{
		return snapshot(new Tile[] {
			new Tile(Lane.FRAME_RATE, Level.OK, "50 fps", "worst 35 ms", NoData.NONE),
			new Tile(Lane.TICKS, Level.OK, "600 ms", PLUS_MINUS + "13 ms", NoData.NONE),
			new Tile(Lane.PING, Level.OK, "41 ms", "was 39 ms", NoData.NONE)});
	}

	@Test
	public void nowOfThePicturesQuietSnapshot()
	{
		final Cell[] c = Cells.now(quietSnapshot());
		assertEquals(3, c.length);
		assertCell(c[0], Lane.FRAME_RATE, "FPS", "50", "fps", Level.OK, false);
		assertCell(c[1], Lane.TICKS, "Tick", "600", "ms", Level.OK, false);
		assertCell(c[2], Lane.PING, "Ping", "41", "ms", Level.OK, false);
	}

	/** A cell takes its tile's level. */
	@Test
	public void nowTakesTheTilesLevels()
	{
		final Cell[] c = Cells.now(snapshot(new Tile[] {
			new Tile(Lane.FRAME_RATE, Level.BAD, "12 fps", "worst 480 ms", NoData.NONE),
			new Tile(Lane.TICKS, Level.WARN, "672 ms", PLUS_MINUS + "72 ms", NoData.NONE),
			new Tile(Lane.PING, Level.BAD, "310 ms", "was 41 ms", NoData.NONE)}));
		assertEquals(Level.BAD, c[0].level);
		assertEquals(Level.WARN, c[1].level);
		assertEquals(Level.BAD, c[2].level);
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
		assertEquals(3, c.length);
		assertCell(c[0], Lane.FRAME_RATE, "FPS", "50", "fps", Level.OK, false);
		assertCell(c[1], Lane.TICKS, "Tick", "1,240", "ms", Level.BAD, true);
		assertCell(c[2], Lane.PING, "Ping", "41", "ms", Level.OK, false);
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
		final Cell[] line = Cells.of(e.withVerdict(verdict(Cause.UPLOAD_LOSS)));
		assertCell(line[2], Lane.PING, "Ping", "41", "ms", Level.BAD, true);
		assertEquals(1, culprits(line));
		assertEquals("the Tick cell keeps its own level", Level.BAD, line[1].level);
		assertFalse(line[1].culprit);

		final Cell[] frames = Cells.of(e.withVerdict(verdict(Cause.CLIENT_BUSY)));
		assertCell(frames[0], Lane.FRAME_RATE, "FPS", "50", "fps", Level.BAD, true);
		assertEquals("the frame rate tile alone says OK", Level.OK, EventView.tiles(e)[0].level);
		assertEquals(1, culprits(frames));

		final LagEvent noPing = event(50, 34, 952, 1240, 640, -1, 41);
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
			new Tile(Lane.PING, Level.NO_DATA, "-", NoData.STALE.reason(), NoData.STALE)}))[2];
		assertCell(stale, Lane.PING, "Ping", "-", "", Level.NO_DATA, false);
		assertEquals("Ping: Nothing sent", stale.tip);

		// Not logged in, as the snapshot builder writes it: world 0 and every tile "Not logged in".
		final Tile[] out = new Tile[Lane.TILES];
		for (int i = 0; i < out.length; i++)
		{
			out[i] = new Tile(Lane.values()[i], Level.NO_DATA, "-", NoData.NOT_LOGGED_IN.reason(),
				NoData.NOT_LOGGED_IN);
		}
		final Cell[] loggedOut = Cells.now(snapshot(0, out));
		for (Cell c : loggedOut)
		{
			assertEquals(c.name, "-", c.value);
			assertEquals(c.name, "", c.unit);
			assertEquals(c.name, Level.NO_DATA, c.level);
		}
		assertEquals("Frame rate: Not logged in", loggedOut[0].tip);
		assertEquals("Ticks: Not logged in", loggedOut[1].tip);
		assertEquals("Ping: Not logged in", loggedOut[2].tip);

		final Cell forced = Cells.now(snapshot(new Tile[] {quiet[0], quiet[1],
			new Tile(Lane.PING, Level.BAD, "-", "Could not read it", NoData.ERROR)}))[2];
		assertEquals("a dash is always the hollow ring", Level.NO_DATA, forced.level);
		assertEquals("Ping: Could not read it", forced.tip);

		final Cell[] event = Cells.of(event(-1, -1, 952, 1240, 640, 41, 41));
		assertCell(event[0], Lane.FRAME_RATE, "FPS", "-", "", Level.NO_DATA, false);
		assertEquals("an event's dash has no reason to give", "Frame rate: -", event[0].tip);
	}

	@Test
	public void numbersAreHeldToNineThousandNineHundredNinetyNine()
	{
		final Cell tick = Cells.of(event(50, 34, 952, 12400, 11800, 41, 41))[1];
		assertEquals("9,999", tick.value);
		assertEquals("the tip keeps the true number", "Ticks: worst 12,400 ms, mean 952 ms", tick.tip);

		final Cell[] big = Cells.now(snapshot(new Tile[] {
			new Tile(Lane.FRAME_RATE, Level.OK, "12000 fps", "worst 35 ms", NoData.NONE),
			new Tile(Lane.TICKS, Level.BAD, "12,400 ms", "worst 12,400", NoData.NONE),
			new Tile(Lane.PING, Level.BAD, "9,999 ms", "was 39 ms", NoData.NONE)}));
		assertEquals("9,999", big[0].value);
		assertEquals("9,999", big[1].value);
		assertEquals("9,999", big[2].value);
		assertEquals("Ticks: 12,400 ms, worst 12,400", big[1].tip);

		final Cell[] small = Cells.now(snapshot(new Tile[] {
			new Tile(Lane.FRAME_RATE, Level.BAD, "0 fps", "worst 1,000 ms", NoData.NONE),
			new Tile(Lane.TICKS, Level.OK, "1,000 ms", "worst 1,000", NoData.NONE),
			new Tile(Lane.PING, Level.OK, "999 ms", "", NoData.NONE)}));
		assertEquals("0", small[0].value);
		assertEquals("the commas are taken out and put back", "1,000", small[1].value);
		assertEquals("999", small[2].value);
	}

	/** Each tip of 5.2's two tables, and the parts left out when there is nothing to say. */
	@Test
	public void tips()
	{
		final Cell[] now = Cells.now(quietSnapshot());
		assertEquals("Frame rate: 50 fps, worst 35 ms", now[0].tip);
		assertEquals("Ticks: 600 ms, " + PLUS_MINUS + "13 ms", now[1].tip);
		assertEquals("Ping: 41 ms, was 39 ms", now[2].tip);

		final Cell[] of = Cells.of(EventViewTest.picturesEvent().withVerdict(verdict(Cause.SLOW_WORLD)));
		assertEquals("Frame rate: 50 fps, worst 34 ms", of[0].tip);
		assertEquals("Ticks: worst 1,240 ms, mean 952 ms", of[1].tip);
		assertEquals("Ping: 41 ms, was 41 ms", of[2].tip);

		final Tile[] quiet = quietSnapshot().tiles;
		final Cell noUsual = Cells.now(snapshot(new Tile[] {quiet[0], quiet[1],
			new Tile(Lane.PING, Level.OK, "41 ms", "", NoData.NONE)}))[2];
		assertEquals("no small line: no comma", "Ping: 41 ms", noUsual.tip);

		assertEquals("Ticks: worst 1,240 ms", Cells.of(event(50, 34, -1, 1240, 640, 41, 41))[1].tip);
		final Cell meanOnly = Cells.of(event(50, 34, 952, -1, -1, 41, 41))[1];
		assertEquals("Ticks: mean 952 ms", meanOnly.tip);
		assertEquals("no worst tick: a dash", "-", meanOnly.value);
		assertEquals("", meanOnly.unit);
		assertEquals(Level.NO_DATA, meanOnly.level);
		assertEquals("Ticks: -", Cells.of(event(50, 34, -1, -1, -1, 41, 41))[1].tip);
	}

	/** An event's Tick cell prints the worst gap, at the level of its worst CORRECTED deviation (C5). */
	@Test
	public void theEventsTickCellIsTheWorstGapAtItsCorrectedLevel()
	{
		final Cell paced = Cells.of(event(50, 34, 610, 640, 20, 41, 41))[1];
		assertEquals("640", paced.value);
		assertEquals("20 ms after the frame time is taken off", Level.OK, paced.level);
		// TICK_WARN_MS corrected (200 since the first live look) is WARN; 80 is OK now.
		assertEquals(Level.WARN, Cells.of(event(50, 34, 610, 600 + Thresholds.TICK_WARN_MS + 22,
			Thresholds.TICK_WARN_MS, 41, 41))[1].level);
		assertEquals(Level.OK, Cells.of(event(50, 34, 610, 700, 80, 41, 41))[1].level);
	}

	@Test
	public void threeCellsInLaneOrder()
	{
		final String[] names = {"FPS", "Tick", "Ping"};
		for (Cell[] cells : new Cell[][] {Cells.now(quietSnapshot()), Cells.of(EventViewTest.picturesEvent()),
			Cells.now(snapshot(new Tile[0]))})
		{
			assertEquals(3, cells.length);
			assertEquals(Lane.values().length, cells.length);
			for (int i = 0; i < cells.length; i++)
			{
				assertEquals(Lane.values()[i], cells[i].lane);
				assertEquals(names[i], cells[i].name);
			}
		}
	}

	/** A snapshot with no tiles (only a test builds one) gives three dashes and never throws. */
	@Test
	public void missingTilesAreDashes()
	{
		final Cell[] c = Cells.now(snapshot(null));
		for (int i = 0; i < Lane.TILES; i++)
		{
			assertEquals("-", c[i].value);
			assertEquals(Level.NO_DATA, c[i].level);
			assertEquals(Lane.values()[i].label() + ": -", c[i].tip);
		}
	}

	@Test
	public void nullTextsAreEmpty()
	{
		final Cell c = new Cell(Lane.PING, null, null, null, Level.NO_DATA, false, null);
		assertEquals("", c.name);
		assertEquals("", c.value);
		assertEquals("", c.unit);
		assertEquals("", c.tip);
		assertTrue(new Cell(Lane.PING, "Ping", "41", "ms", Level.OK, true, "t").culprit);
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
	private static PanelSnapshot snapshot(Tile[] tiles)
	{
		return snapshot(416, tiles);
	}

	/** A snapshot on that world; 0 = not logged in (contract 3.8). */
	private static PanelSnapshot snapshot(int world, Tile[] tiles)
	{
		return new PanelSnapshot(0, null, world, null, tiles, 10, 0, 0, new Strip[0], null, null,
			new int[Group.values().length], 0, 0, null, "");
	}

	/** An event's verdict of that cause: BAD, as every event rule's is (contract 6.4). */
	private static Verdict verdict(Cause c)
	{
		return new Verdict(c, Confidence.LIKELY, Level.BAD, "headline", "proof", "fix", "", 1, 14, 416, 7, null,
			null);
	}

	private static LagEvent event(int fps, int worstFrameMs, int meanTickGapMs, int worstTickGapMs,
		int worstCorrectedTickMs, int rttMs, int rttBeforeMs)
	{
		return new LagEvent(7, 100, 113, 0, Trigger.TICK_OFF.bit(), Trigger.TICK_OFF, 416, 0, 0, 0, fps,
			worstFrameMs, meanTickGapMs, worstTickGapMs, worstCorrectedTickMs, rttMs, rttMs, rttBeforeMs, 900, 0,
			false, false, null);
	}
}
