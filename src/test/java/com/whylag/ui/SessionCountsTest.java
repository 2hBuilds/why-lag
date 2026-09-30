package com.whylag.ui;

import com.whylag.core.Group;
import java.util.Arrays;
import java.util.List;
import org.junit.Test;
import static com.whylag.ui.PanelFixtures.Drawn;
import static com.whylag.ui.PanelFixtures.counts;
import static com.whylag.ui.PanelFixtures.edt;
import static com.whylag.ui.PanelFixtures.record;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * The session's counts (contract 5, block 8): the three counts on one line, 11 px apart; a fourth, "? n", only above
 * 0, with the tooltip "Can't tell"; "99+" over 99; items that do not fit go to a second line, and the block is then
 * 32 high; an item is never split and never clipped.
 */
public class SessionCountsTest
{
	@Test
	public void threeCountsAreOneLine()
	{
		final SessionCounts block = counted(counts(1, 1, 1, 0));
		assertEquals(Arrays.asList("Conn 1", "Frame 1", "World 1"), block.items());
		assertEquals(1, block.lines());
		assertEquals(16, block.getPreferredSize().height);
		final List<Drawn> drawn = record(block);
		assertEquals(3, drawn.size());
		int x = 0;
		for (Drawn d : drawn)
		{
			assertEquals(d.toString(), x, d.x);
			assertEquals(12, d.y);
			assertEquals(Ui.RSS, d.font);
			assertEquals(Ui.TEXT.getRGB(), d.colour.getRGB());
			x = d.right() + 11;
		}
		assertNull("no '?' item, no tooltip", PanelFixtures.tip(block, 5, 5));
	}

	/** Four items at "99+" are wider than the block: the ones that do not fit go to a second 16 px line. */
	@Test
	public void wideCountsWrapToASecondLine()
	{
		final SessionCounts block = counted(counts(120, 150, 999, 130));
		assertEquals(Arrays.asList("Conn 99+", "Frame 99+", "World 99+", "? 99+"), block.items());
		assertEquals(2, block.lines());
		assertEquals(32, block.getPreferredSize().height);
		final List<Drawn> drawn = record(block);
		assertEquals(4, drawn.size());
		int second = 0;
		for (Drawn d : drawn)
		{
			assertTrue(d.toString(), d.y == 12 || d.y == 28);
			second += d.y == 28 ? 1 : 0;
		}
		assertTrue(second >= 1);
		Drawn firstOfSecond = null;
		for (Drawn d : drawn)
		{
			if (d.y == 28)
			{
				firstOfSecond = d;
				break;
			}
		}
		assertEquals("a new line starts at x 0", 0, firstOfSecond.x);
		final int index = drawn.indexOf(firstOfSecond);
		assertTrue("it did not fit after the one before it", drawn.get(index - 1).right() + 11 + firstOfSecond.width
			> 213);
		assertEquals("Can't tell", tipOver(block, drawn.get(3)));
		assertNull(tipOver(block, drawn.get(0)));

		final SessionCounts twoDigits = counted(counts(99, 99, 99, 99));
		assertEquals("four two-digit items fit one line or do not: measured", 1, twoDigits.lines());
	}

	/** Every item is drawn whole and inside x 0 .. 213, whatever the counts. */
	@Test
	public void noItemIsSplitOrClipped()
	{
		final int[] samples = {0, 1, 9, 10, 99, 100, 1000};
		for (int a : samples)
		{
			for (int b : samples)
			{
				for (int u : new int[] {0, 1, 100})
				{
					final SessionCounts block = counted(counts(a, b, b, u));
					final List<Drawn> drawn = record(block);
					assertEquals(block.items().size(), drawn.size());
					for (int i = 0; i < drawn.size(); i++)
					{
						final Drawn d = drawn.get(i);
						assertEquals("drawn whole", block.items().get(i), d.text);
						assertTrue(d.toString(), d.x >= 0 && d.right() <= 213);
						assertTrue(d.toString(), d.y == 12 || d.y == 28);
					}
					for (int i = 1; i < drawn.size(); i++)
					{
						final Drawn before = drawn.get(i - 1);
						final Drawn d = drawn.get(i);
						if (d.y == before.y)
						{
							assertEquals("11 px between items", before.right() + 11, d.x);
						}
					}
					assertEquals(u > 0 ? 4 : 3, block.items().size());
					assertEquals(block.lines() * 16, block.getPreferredSize().height);
				}
			}
		}
		assertEquals("Conn 99", SessionCounts.item(Group.CONNECTION, 99));
		assertEquals("Conn 99+", SessionCounts.item(Group.CONNECTION, 100));
		assertEquals("? 3", SessionCounts.item(Group.UNSURE, 3));
		assertFalse(counted(null).items().contains("? 0"));
		assertEquals(Arrays.asList("Conn 0", "Frame 0", "World 0"), counted(null).items());
	}

	private static SessionCounts counted(int[] counts)
	{
		final SessionCounts block = PanelFixtures.onEdt(SessionCounts::new);
		edt(() -> block.set(counts));
		return block;
	}

	private static String tipOver(SessionCounts block, Drawn d)
	{
		return PanelFixtures.tip(block, d.x + 2, d.y - 4);
	}
}
