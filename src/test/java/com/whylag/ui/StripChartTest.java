package com.whylag.ui;

import com.whylag.core.EventView;
import com.whylag.core.Fmt;
import com.whylag.core.LagEvent;
import com.whylag.core.Lane;
import com.whylag.core.Level;
import com.whylag.core.PanelSnapshot;
import com.whylag.core.Strip;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.junit.Test;
import static com.whylag.ui.PanelFixtures.D_START;
import static com.whylag.ui.PanelFixtures.Drawn;
import static com.whylag.ui.PanelFixtures.Fixture;
import static com.whylag.ui.PanelFixtures.Lanes;
import static com.whylag.ui.PanelFixtures.WORLD;
import static com.whylag.ui.PanelFixtures.ZONE;
import static com.whylag.ui.PanelFixtures.is;
import static com.whylag.ui.PanelFixtures.over;
import static com.whylag.ui.PanelFixtures.panel;
import static com.whylag.ui.PanelFixtures.pixel;
import static com.whylag.ui.PanelFixtures.record;
import static com.whylag.ui.PanelFixtures.tip;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * The five lanes behind "Graphs" (contract 5.3): 213 x 166 with lanes at y 0, 30, 60, 90 and 120 and the axis at
 * y 150; a band over the columns of its event's seconds, redder in its cause's lane, orange-framed when selected;
 * a gap where a column has no data; the axis labels of each range; a selected event's five lane values; the grey
 * game line under the PC line; the CPU lane's two halves; one "-" for a lane with no value; and the tooltip, which
 * names a column's clock and its six numbers, or says why the CPU lane is empty.
 */
public class StripChartTest
{
	@Test
	public void fiveLanes()
	{
		final StripChart chart = panel(PanelFixtures.quiet(), true).strips();
		assertEquals(213, chart.getPreferredSize().width);
		assertEquals(166, chart.getPreferredSize().height);
		final BufferedImage img = PanelFixtures.paint(chart);
		for (int lane = 0; lane < 5; lane++)
		{
			final int y = 30 * lane;
			assertTrue("lane " + lane + " starts at y " + y, is(img, 100, y, Ui.CARD));
			assertTrue("lane " + lane + " is 27 high", is(img, 100, y + 26, Ui.CARD));
			if (lane < 4)
			{
				for (int gap = 27; gap < 30; gap++)
				{
					assertTrue("the ground between lanes, y " + (y + gap), is(img, 100, y + gap, Ui.GROUND));
				}
			}
			assertTrue("a line in the lane's rows 13 .. 25", lineRow(img, 100, y) >= y + 13);
		}
		for (int y = 147; y < 150; y++)
		{
			assertTrue(is(img, 100, y, Ui.GROUND));
		}
		for (int x = 0; x < 213; x++)
		{
			assertTrue("the axis line at y 150, x " + x, is(img, x, 150, Ui.RULE));
		}
		for (int x : new int[] {0, 71, 142, 212})
		{
			assertTrue("a 3 px tick at x " + x, is(img, x, 151, Ui.RULE) && is(img, x, 152, Ui.RULE));
			assertFalse(is(img, x, 153, Ui.RULE));
		}
		assertTrue("no tick at x 72", is(img, 72, 151, Ui.GROUND));
		final List<Drawn> drawn = record(chart);
		for (Lane lane : Lane.values())
		{
			final Drawn label = find(drawn, lane.label());
			assertEquals(3, label.x);
			assertEquals(30 * lane.ordinal() + 11, label.y);
			assertEquals(Ui.RSS, label.font);
			assertEquals(Ui.LABEL.getRGB(), label.colour.getRGB());
		}
	}

	/** A band runs from the column of its first millisecond to that of its last, across all five lanes. */
	@Test
	public void bandCoversItsSeconds()
	{
		final WhyLagPanel p = panel(PanelFixtures.clearHere(), true);
		final PanelSnapshot s = p.last();
		final int a = Strip.columnOf(s.rangeStartWallMs, s.rangeEndWallMs, D_START);
		final int b = Strip.columnOf(s.rangeStartWallMs, s.rangeEndWallMs, D_START + 14_000 - 1);
		assertEquals(117, a);
		assertEquals(122, b);
		final BufferedImage img = PanelFixtures.paint(p.strips());
		for (int lane = 0; lane < 5; lane++)
		{
			final int y = 30 * lane + 26;
			final Color band = over(lane == Lane.TICKS.ordinal() ? Ui.BAND_CULPRIT : Ui.BAND, Ui.CARD);
			for (int x = a; x <= b; x++)
			{
				assertEquals("lane " + lane + " at " + x, band, pixel(img, x, y));
			}
			assertTrue("lane " + lane + ": nothing before", is(img, a - 1, y, Ui.CARD));
			assertTrue("lane " + lane + ": nothing after", is(img, b + 1, y, Ui.CARD));
		}
		assertTrue("the gaps between lanes carry no band", is(img, 119, 28, Ui.GROUND));

		// At 60 min a 3 s event is one column; its band is widened to 2 px.
		final WhyLagPanel seven = panel(PanelFixtures.sevenEvents(), true);
		final LagEvent first = seven.last().rangeEvents.get(0);
		final int[] cols = seven.strips().bandOf(first.id);
		final int c = Strip.columnOf(seven.last().rangeStartWallMs, seven.last().rangeEndWallMs, first.startWallMs);
		assertEquals(c, Strip.columnOf(seven.last().rangeStartWallMs, seven.last().rangeEndWallMs,
			first.startWallMs + first.lengthS() * 1000L - 1));
		assertEquals(c, cols[0]);
		assertEquals("at least 2 px wide", c + 1, cols[1]);
		final BufferedImage sevenImg = PanelFixtures.paint(seven.strips());
		assertEquals(over(Ui.BAND, Ui.CARD), pixel(sevenImg, c + 1, 26));
	}

	/**
	 * Bands that share a column are painted once there: in a busy hour every banded pixel of a lane is one band's
	 * colour over the card - the culprit's where one of the events blames that lane - never a darker stack.
	 */
	@Test
	public void bandsThatShareAColumnDoNotStack()
	{
		final BufferedImage img = PanelFixtures.paint(panel(PanelFixtures.fiveHundred(), true).strips());
		final Color plain = over(Ui.BAND, Ui.CARD);
		final Color culprit = over(Ui.BAND_CULPRIT, Ui.CARD);
		int plains = 0;
		int culprits = 0;
		for (int lane = 0; lane < 5; lane++)
		{
			for (int x = 0; x < 213; x++)
			{
				final Color c = pixel(img, x, 30 * lane + 26);
				assertTrue("lane " + lane + " at " + x + ": " + c,
					c.equals(Ui.CARD) || c.equals(plain) || c.equals(culprit));
				plains += c.equals(plain) ? 1 : 0;
				culprits += c.equals(culprit) ? 1 : 0;
			}
		}
		assertTrue("plain bands: " + plains, plains > 100);
		assertTrue("culprit bands in the four blamed lanes: " + culprits, culprits > 100);
		for (int x = 0; x < 213; x++)
		{
			assertFalse("no wave-one cause blames the CPU lane", pixel(img, x, 120 + 26).equals(culprit));
		}
	}

	@Test
	public void culpritLaneIsRedder()
	{
		final BufferedImage img = PanelFixtures.paint(panel(PanelFixtures.clearHere(), true).strips());
		final Color culprit = pixel(img, 119, 30 + 26);
		final Color other = pixel(img, 119, 26);
		assertEquals("the World event's band in the ticks lane", over(Ui.BAND_CULPRIT, Ui.CARD), culprit);
		assertEquals(over(Ui.BAND, Ui.CARD), other);
		assertTrue(culprit + " is redder than " + other,
			culprit.getRed() - culprit.getGreen() > other.getRed() - other.getGreen() + 30);
		for (int lane : new int[] {0, 2, 3, 4})
		{
			assertEquals("not the culprit, lane " + lane, other, pixel(img, 119, 30 * lane + 26));
		}
	}

	@Test
	public void selectedBandHasAFrame()
	{
		final WhyLagPanel p = panel(PanelFixtures.lag(), true);
		final BufferedImage img = PanelFixtures.paint(p.strips());
		final List<Drawn> texts = record(p.strips());
		final int[] cols = p.strips().bandOf(3);
		assertEquals(117, cols[0]);
		assertEquals(122, cols[1]);
		int rows = 0;
		for (int y = 0; y <= 147; y++)
		{
			// The lane texts are drawn over the frame: its sides show wherever no text lies on them.
			if (!underText(texts, 117, y) && !underText(texts, 122, y))
			{
				assertTrue("left side at y " + y, is(img, 117, y, Ui.ORANGE));
				assertTrue("right side at y " + y, is(img, 122, y, Ui.ORANGE));
				rows++;
			}
		}
		assertTrue("the sides were seen on most rows: " + rows, rows > 130);
		for (int x = 117; x <= 122; x++)
		{
			assertTrue("top at " + x, is(img, x, 0, Ui.ORANGE));
			assertTrue("bottom at " + x, is(img, x, 147, Ui.ORANGE));
			assertFalse("the frame stops at y 147", is(img, x, 148, Ui.ORANGE));
		}
		assertFalse("the frame is 1 px: the column left of it is not orange", is(img, 116, 70, Ui.ORANGE));
		assertFalse(is(img, 123, 70, Ui.ORANGE));
		assertEquals("inside, the selected band", over(Ui.BAND_SELECTED, Ui.CARD), pixel(img, 119, 26));
		assertEquals("its cause's lane stays the culprit's", over(Ui.BAND_CULPRIT, Ui.CARD), pixel(img, 119, 56));

		final BufferedImage unselected = PanelFixtures.paint(panel(PanelFixtures.clearHere(), true).strips());
		for (int y = 0; y < 150; y++)
		{
			assertFalse("no frame without a selection, y " + y, is(unselected, 117, y, Ui.ORANGE));
		}
	}

	/** A column with no data draws nothing: a gap in the line. */
	@Test
	public void gapWhereNoData()
	{
		final StripChart chart = chartOf(gappy());
		final BufferedImage img = PanelFixtures.paint(chart);
		for (int x = 50; x < 60; x++)
		{
			for (int y = 13; y <= 25; y++)
			{
				assertTrue("no line at " + x + "," + y, is(img, x, y, Ui.CARD));
			}
		}
		assertTrue("the line before the gap", lineRow(img, 45, 0) >= 13);
		assertTrue("the line after the gap", lineRow(img, 65, 0) >= 13);

		final BufferedImage none = PanelFixtures.paint(panel(PanelFixtures.cpuNone(), true).strips());
		for (int x = 0; x < 213; x++)
		{
			for (int y = 120 + 13; y <= 120 + 25; y++)
			{
				assertTrue("the empty CPU lane at " + x + "," + y, is(none, x, y, Ui.CARD));
			}
		}
	}

	@Test
	public void axisLabelsPerRange()
	{
		final List<Drawn> one = record(panel(PanelFixtures.quiet(), true).strips());
		assertAxis(one, "-60 s", "-40 s", "-20 s");
		assertEquals("the first label starts at x 0", 0, find(one, "-60 s").x);
		final Drawn forty = find(one, "-40 s");
		assertEquals("centred on its tick", 71, forty.x + forty.width / 2);
		final Drawn now = find(one, "now");
		assertEquals(213, now.right());
		assertEquals(Ui.TEXT.getRGB(), now.colour.getRGB());

		assertAxis(record(panel(PanelFixtures.lag(), true).strips()), "21:42", "21:45", "21:48");
		assertAxis(record(panel(PanelFixtures.clearElsewhere(), true).strips()), "20:52", "21:12", "21:32");
	}

	/**
	 * A session shorter than the range: the snapshot's span is the data held, and the axis prints the real clock of
	 * it, with seconds, the first label the session's first second; the range row says how much is held.
	 */
	@Test
	public void aStretchedGraphLabelsItsOwnSpan()
	{
		final PanelSnapshot q = PanelFixtures.quiet().snapshot;
		final PanelSnapshot held = new PanelSnapshot(q.wallMs, q.zone, q.world, q.verdict, q.tiles, 1,
			q.rangeEndWallMs - 30_000, q.rangeEndWallMs, q.strips, q.rangeEvents, q.sessionEvents, q.sessionCounts,
			q.sessionTotal, q.sessionStartWallMs, q.sysCpuPct, q.gameBusyPct, q.settings, q.footer);
		assertTrue(held.stretched());
		assertFalse(q.stretched());
		final WhyLagPanel p = panel(new Fixture("stretched", held, -1), true);
		assertAxis(record(p.strips()), "21:51:30", "21:51:40", "21:51:50");
		assertEquals("30 s", p.rangeRow().held());
		assertEquals("", panel(PanelFixtures.quiet(), true).rangeRow().held());
	}

	private static void assertAxis(List<Drawn> drawn, String... labels)
	{
		for (String label : labels)
		{
			final Drawn d = find(drawn, label);
			assertEquals(164, d.y);
			assertEquals(Ui.RSS, d.font);
			assertEquals(Ui.LABEL.getRGB(), d.colour.getRGB());
		}
		assertEquals(164, find(drawn, "now").y);
	}

	/** With an event selected the five values are its own, the Game half too; the tiles' levels colour them. */
	@Test
	public void selectedEventLaneValues()
	{
		final WhyLagPanel p = panel(PanelFixtures.lag(), true);
		final LagEvent d = PanelFixtures.pictureEvent();
		final String[] values = EventView.laneValues(d);
		final Level[] levels = EventView.laneLevels(d);
		final List<Drawn> drawn = record(p.strips());
		for (int i = 0; i < 5; i++)
		{
			assertEquals(values[i], p.strips().value(i));
			final Drawn v = find(drawn, values[i], Ui.textColour(levels[i]));
			assertEquals(30 * i + 11, v.y);
		}
		assertEquals("50 fps", values[0]);
		assertEquals("952 ms", values[1]);
		assertEquals("PC 37 %", values[4]);
		assertEquals(EventView.cpuGameValue(d), p.strips().gameValue());
		assertEquals("Game 95 %", find(drawn, "Game 95 %", Ui.LABEL).text);
		assertEquals("the ticks value is red text", Ui.BAD_TEXT.getRGB(), find(drawn, "952 ms", Ui.BAD_TEXT).colour
			.getRGB());

		edt(p, -1);
		assertEquals("cleared: the snapshot's own values", "600 ms", p.strips().value(1));
		assertEquals("Game 45 %", p.strips().gameValue());
	}

	/** The grey game line is drawn first: where the two lines meet, the pixel is the PC line's. */
	@Test
	public void secondSeriesIsDrawnUnderTheFirst()
	{
		final Lanes same = new Lanes(10).cpu(42, Level.OK, "PC 42 %").game(42, "Game 42 %");
		final BufferedImage img = PanelFixtures.paint(chartOf(snapshotOf(same)));
		final int y = StripChart.yOf(42, 120, 0, 100);
		assertTrue("the PC line's colour on top", is(img, 100, y, Ui.OK));

		final Lanes apart = new Lanes(10).cpu(20, Level.OK, "PC 20 %").game(80, "Game 80 %");
		final BufferedImage two = PanelFixtures.paint(chartOf(snapshotOf(apart)));
		assertTrue("the game line in grey", is(two, 100, StripChart.yOf(80, 120, 0, 100), Ui.LABEL));
		assertTrue("the PC line in its level's colour", is(two, 100, StripChart.yOf(20, 120, 0, 100), Ui.OK));
	}

	@Test
	public void cpuValueFitsBesideItsLabel()
	{
		final List<Drawn> drawn = record(panel(PanelFixtures.cpuFull(), true).strips());
		final Drawn pc = find(drawn, "PC 100 %", Ui.BAD_TEXT);
		final Drawn game = find(drawn, "Game 100 %", Ui.LABEL);
		final Drawn label = find(drawn, "CPU");
		assertEquals(210, pc.right());
		assertEquals("11 px left of the PC half", pc.x - 11, game.right());
		assertTrue("clear of the label: " + game.x + " > " + label.right(), game.x > label.right());
		assertEquals(pc.y, game.y);
	}

	@Test
	public void cpuHalvesAreLeftOutWhenUnknown()
	{
		final List<Drawn> pcOnly = cpuTexts(PanelFixtures.cpuPcOnly());
		assertEquals(1, pcOnly.size());
		assertEquals("PC 37 %", pcOnly.get(0).text);
		assertEquals(210, pcOnly.get(0).right());

		final List<Drawn> gameOnly = cpuTexts(PanelFixtures.cpuGameOnly());
		assertEquals(1, gameOnly.size());
		assertEquals("Game 42 %", gameOnly.get(0).text);
		assertEquals("a Game half alone is right-aligned at x 210", 210, gameOnly.get(0).right());

		final List<Drawn> none = cpuTexts(PanelFixtures.cpuNone());
		assertEquals(1, none.size());
		assertEquals("-", none.get(0).text);
		assertEquals(210, none.get(0).right());
		assertEquals(Ui.LABEL.getRGB(), none.get(0).colour.getRGB());
	}

	/** Not logged in: every lane's value is "", and each draws one "-" in LABEL, right-aligned at x 210. */
	@Test
	public void aLaneWithNoValueShowsOneDash()
	{
		final List<Drawn> drawn = texts(record(panel(PanelFixtures.notLoggedIn(), true).strips()));
		int dashes = 0;
		for (Drawn d : drawn)
		{
			if (d.text.equals("-"))
			{
				dashes++;
				assertEquals(210, d.right());
				assertEquals(Ui.LABEL.getRGB(), d.colour.getRGB());
				assertEquals(0, (d.y - 11) % 30);
			}
		}
		assertEquals("one dash on each of the five lanes", 5, dashes);
	}

	@Test
	public void emptyCpuLaneSaysWhy()
	{
		final StripChart none = panel(PanelFixtures.cpuNone(), true).strips();
		assertEquals("No CPU data on this PC", tip(none, 100, 130));
		assertEquals("No CPU data on this PC", tip(none, 5, 146));
		assertTrue(tip(none, 100, 10), tip(none, 100, 10).endsWith("PC -, game -"));
		assertNull("no tooltip on the axis", tip(none, 100, 147));
		assertNull(tip(none, 100, 160));

		final StripChart out = panel(PanelFixtures.notLoggedIn(), true).strips();
		assertEquals("every column of every strip is empty", "Not logged in", tip(out, 100, 130));
		assertTrue(tip(out, 100, 40).endsWith("- fps -, ticks -, ping -, memory -, PC -, game -"));
	}

	@Test
	public void tooltipNamesTheColumnAndItsFiveValues()
	{
		final WhyLagPanel p = panel(PanelFixtures.lag(), true);
		final PanelSnapshot s = p.last();
		final long at = Strip.columnStart(s.rangeStartWallMs, s.rangeEndWallMs, 119);
		assertEquals("21:47:35", Fmt.clockSeconds(at, ZONE));
		final String expected = "21:47:35 - 50 fps, ticks 1,240 ms, ping 41 ms, memory 51 %, PC 24 %, game 45 %";
		assertEquals(expected, tip(p.strips(), 119, 40));
		assertEquals("between the lanes too", expected, tip(p.strips(), 119, 28));
		assertEquals("over the CPU lane when it has data", expected, tip(p.strips(), 119, 140));
		edt(p, -1);
		assertEquals("a selection does not change it", expected, tip(p.strips(), 119, 40));

		final StripChart gappy = chartOf(gappy());
		final String hole = tip(gappy, 55, 10);
		assertTrue(hole, hole.contains(" - fps -, ticks 600 ms, ping 41 ms, memory 51 %, PC 24 %, game 42 %"));
		assertEquals("the column under the mouse, x held to 0 .. 212", tip(gappy, 212, 10), tip(gappy, 250, 10));
	}

	// ------------------------------------------------------------------ helpers

	/** The quiet 10 min lanes with no frame rate in columns 50 .. 59. */
	private static PanelSnapshot gappy()
	{
		final Lanes lanes = new Lanes(10);
		for (int c = 50; c < 60; c++)
		{
			lanes.values[Lane.FRAME_RATE.ordinal()][c] = Strip.NONE;
		}
		return snapshotOf(lanes);
	}

	private static PanelSnapshot snapshotOf(Lanes lanes)
	{
		return PanelFixtures.snapshot(WORLD, PanelFixtures.clearAfter(WORLD), PanelFixtures.quietTiles(), 10,
			lanes.build(), Collections.emptyList(), 24, 42);
	}

	private static StripChart chartOf(PanelSnapshot s)
	{
		return panel(new Fixture("chart", s, -1), true).strips();
	}

	/**
	 * True when a lane text's backing covers (x, y): the text's glyph rows in RuneScape Small and the shadow's row
	 * under them, a pixel either side of the text and its shadow.
	 */
	private static boolean underText(List<Drawn> drawn, int x, int y)
	{
		final int ascent = Ui.metrics(Ui.RSS).getAscent();
		final int descent = Ui.metrics(Ui.RSS).getDescent();
		for (Drawn d : drawn)
		{
			if (x >= d.x - 1 && x < d.right() + 2 && y >= d.y - ascent && y < d.y + descent + 1)
			{
				return true;
			}
		}
		return false;
	}

	/** The topmost row of a lane that is not its ground or a band, in one column: where its line is. */
	private static int lineRow(BufferedImage img, int x, int laneY)
	{
		for (int y = laneY + 13; y <= laneY + 25; y++)
		{
			if (!is(img, x, y, Ui.CARD))
			{
				return y;
			}
		}
		return -1;
	}

	private static void edt(WhyLagPanel p, long select)
	{
		PanelFixtures.edt(() -> p.select(select));
	}

	/** The CPU lane's value texts (no label, no shadows). */
	private static List<Drawn> cpuTexts(Fixture f)
	{
		final List<Drawn> out = new ArrayList<>();
		for (Drawn d : texts(record(panel(f, true).strips())))
		{
			if (d.y == 120 + 11 && !d.text.equals("CPU"))
			{
				out.add(d);
			}
		}
		return out;
	}

	/** The strings drawn, less the black shadows. */
	private static List<Drawn> texts(List<Drawn> drawn)
	{
		final List<Drawn> out = new ArrayList<>();
		for (Drawn d : drawn)
		{
			if ((d.colour.getRGB() & 0xFFFFFF) != 0)
			{
				out.add(d);
			}
		}
		return out;
	}

	private static Drawn find(List<Drawn> drawn, String text)
	{
		for (Drawn d : texts(drawn))
		{
			if (d.text.equals(text))
			{
				return d;
			}
		}
		fail("not drawn: " + text + " in " + drawn);
		return null;
	}

	private static Drawn find(List<Drawn> drawn, String text, Color colour)
	{
		for (Drawn d : texts(drawn))
		{
			if (d.text.equals(text) && d.colour.getRGB() == colour.getRGB())
			{
				return d;
			}
		}
		fail("not drawn in " + colour + ": " + text + " in " + drawn);
		return null;
	}
}
