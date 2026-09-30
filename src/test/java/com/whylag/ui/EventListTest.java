package com.whylag.ui;

import com.whylag.core.Fmt;
import com.whylag.core.LagEvent;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import org.junit.Test;
import static com.whylag.ui.PanelFixtures.Drawn;
import static com.whylag.ui.PanelFixtures.ZONE;
import static com.whylag.ui.PanelFixtures.is;
import static com.whylag.ui.PanelFixtures.panel;
import static com.whylag.ui.PanelFixtures.press;
import static com.whylag.ui.PanelFixtures.record;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * The lag event list (contract 5.4): newest first; at most six rows, then "and 1 more"; the empty words of each
 * range; a press on a row selects its event and a second press clears it; the row's look, place and tooltip.
 */
public class EventListTest
{
	@Test
	public void newestFirst()
	{
		final WhyLagPanel p = panel(PanelFixtures.fiveHundred(), true);
		final List<LagEvent> rows = p.eventList().rows();
		for (int i = 1; i < rows.size(); i++)
		{
			assertTrue("row " + i + " is older than the one above it", rows.get(i).startWallMs < rows.get(i - 1)
				.startWallMs);
		}
		final List<LagEvent> range = p.last().rangeEvents;
		assertEquals("the first row is the newest closed event", range.get(range.size() - 1).id, rows.get(0).id);

		final List<Drawn> drawn = record(p.eventList());
		final List<Drawn> times = new ArrayList<>();
		for (Drawn d : drawn)
		{
			if (d.x == 23)
			{
				times.add(d);
			}
		}
		assertEquals(6, times.size());
		for (int i = 0; i < times.size(); i++)
		{
			assertEquals(Fmt.clock(rows.get(i).startWallMs, ZONE), times.get(i).text);
			assertEquals("baseline 17 of row " + i, EventList.rowY(i) + 17, times.get(i).y);
		}
	}

	/** Seven events: six rows of 24 px, 2 px apart, then "and 1 more" in RuneScape Small, 14 px. */
	@Test
	public void sixRowsThenAndMore()
	{
		final WhyLagPanel p = panel(PanelFixtures.sevenEvents(), true);
		final EventList list = p.eventList();
		assertEquals(6, list.rows().size());
		assertEquals(1, list.more());
		assertEquals(6 * 24 + 5 * 2 + 2 + 14, list.getPreferredSize().height);
		final Drawn more = find(record(list), "and 1 more");
		assertEquals(Ui.RSS, more.font);
		assertEquals(Ui.LABEL.getRGB(), more.colour.getRGB());
		assertTrue("under the sixth row", more.y > EventList.rowY(5) + 24);

		final WhyLagPanel many = panel(PanelFixtures.fiveHundred(), true);
		assertEquals(494, many.eventList().more());
		find(record(many.eventList()), "and 494 more");

		final WhyLagPanel one = panel(PanelFixtures.lag(), true);
		assertEquals(1, one.eventList().rows().size());
		assertEquals(0, one.eventList().more());
		assertEquals(24, one.eventList().getPreferredSize().height);
		for (Drawn d : record(one.eventList()))
		{
			assertTrue("no 'more' line with one event: " + d, !d.text.startsWith("and "));
		}
	}

	@Test
	public void emptyWordsPerRange()
	{
		assertEmpty(PanelFixtures.quiet(), 1, "Nothing in the last minute.");
		assertEmpty(PanelFixtures.warmingUp(), 10, "Nothing in the last 10 minutes.");
		assertEmpty(PanelFixtures.clearNone(), 60, "Nothing in the last 60 minutes.");
	}

	private static void assertEmpty(PanelFixtures.Fixture f, int minutes, String words)
	{
		assertEquals(minutes, f.snapshot.rangeMinutes);
		final EventList list = panel(f, true).eventList();
		assertEquals(0, list.rows().size());
		assertEquals(18, list.getPreferredSize().height);
		final List<Drawn> drawn = record(list);
		assertEquals(1, drawn.size());
		assertEquals(words, drawn.get(0).text);
		assertEquals(Ui.RSS, drawn.get(0).font);
		assertEquals(Ui.LABEL.getRGB(), drawn.get(0).colour.getRGB());
	}

	/** A press on a row selects its event; a second press on the same row clears the selection. */
	@Test
	public void clickSelectsAndClickAgainClears()
	{
		final WhyLagPanel p = panel(PanelFixtures.clearHere(), true);
		assertEquals(-1, p.selectedId());
		assertEquals("Smooth", p.card().line1());
		press(p.eventList(), 100, 12);
		assertEquals(3, p.selectedId());
		assertEquals("the card shows the event's verdict", "World lag", p.card().line1());
		assertTrue(p.cells().cells()[1].culprit);
		final BufferedImage selected = PanelFixtures.paint(p.eventList());
		assertTrue("an orange edge", is(selected, 1, 12, Ui.ORANGE));
		assertTrue("the selected ground", is(selected, 100, 2, Ui.CARD_SELECTED));
		for (Drawn d : record(p.eventList()))
		{
			assertEquals("white text on a selected row: " + d, Ui.WHITE.getRGB(), d.colour.getRGB());
		}

		press(p.eventList(), 150, 20);
		assertEquals("pressed again: cleared", -1, p.selectedId());
		assertEquals("Smooth", p.card().line1());
		final BufferedImage cleared = PanelFixtures.paint(p.eventList());
		assertTrue(is(cleared, 1, 12, Ui.CARD));
		assertTrue(is(cleared, 100, 2, Ui.CARD));

		final WhyLagPanel seven = panel(PanelFixtures.sevenEvents(), true);
		press(seven.eventList(), 100, 25);
		assertEquals("the 2 px between rows is no row", -1, seven.selectedId());
		press(seven.eventList(), 100, 26 + 5);
		assertEquals(seven.eventList().rows().get(1).id, seven.selectedId());
		press(seven.eventList(), 100, 26 * 3 + 5);
		assertEquals("another row moves the selection", seven.eventList().rows().get(3).id, seven.selectedId());
	}

	/** A row: the shape at x 9, the clock at x 23 in LABEL, the group at x 63 in TEXT, the length at x 207. */
	@Test
	public void aRowShowsItsEvent()
	{
		final WhyLagPanel p = panel(PanelFixtures.clearHere(), true);
		final List<Drawn> drawn = record(p.eventList());
		final Drawn time = find(drawn, "21:47");
		final Drawn group = find(drawn, "World");
		final Drawn length = find(drawn, "14 s");
		assertEquals(23, time.x);
		assertEquals(Ui.RS, time.font);
		assertEquals(Ui.LABEL.getRGB(), time.colour.getRGB());
		assertEquals(63, group.x);
		assertEquals(Ui.TEXT.getRGB(), group.colour.getRGB());
		assertEquals(207, length.right());
		assertEquals(17, time.y);
		final BufferedImage img = PanelFixtures.paint(p.eventList());
		assertTrue("the verdict's square", is(img, 9, 8, Ui.BAD) && is(img, 16, 15, Ui.BAD));
		assertTrue("the row's ground", is(img, 100, 1, Ui.CARD));
		assertEquals("World 416 is struggling, not you", PanelFixtures.tip(p.eventList(), 100, 12));
		assertNull("below the rows: no tooltip", PanelFixtures.tip(p.eventList(), 100, 40));
	}

	private static Drawn find(List<Drawn> drawn, String text)
	{
		for (Drawn d : drawn)
		{
			if (d.text.equals(text))
			{
				return d;
			}
		}
		throw new AssertionError("not drawn: " + text + " in " + drawn);
	}
}
