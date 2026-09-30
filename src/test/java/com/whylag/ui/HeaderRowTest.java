package com.whylag.ui;

import java.awt.Rectangle;
import java.awt.event.InputEvent;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.util.List;
import org.junit.Test;
import static com.whylag.ui.PanelFixtures.Drawn;
import static com.whylag.ui.PanelFixtures.edt;
import static com.whylag.ui.PanelFixtures.onEdt;
import static com.whylag.ui.PanelFixtures.press;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * The header row with its settings gear (1.0.1, lot C, C6 item 1): the gear is the last thing on the row, 12 x 12 at
 * the right edge, with the tooltip "Settings"; a press on it calls the panel's open-menu hook; and a press within
 * {@code MENU_REOPEN_MS} of the menu closing opens nothing (the bank tracker's rule for a gear that cannot see its own
 * menu).
 */
public class HeaderRowTest
{
	/** A header of {@code width} px whose open-menu hook counts its calls in {@code calls}. */
	private static HeaderRow header(int width, int[] calls)
	{
		return onEdt(() ->
		{
			final HeaderRow row = new HeaderRow(() -> calls[0]++);
			row.set(416);
			row.setSize(width, HeaderRow.HEIGHT);
			return row;
		});
	}

	/** The gear is 12 x 12, centred on the row, and its right edge is the row's at every width the panel gives. */
	@Test
	public void theGearIsTwelveBySquareAtTheRightEdge()
	{
		for (int width : new int[] {213, 223, 230})
		{
			final HeaderRow row = header(width, new int[1]);
			final Rectangle gear = row.gearBounds();
			assertEquals(12, gear.width);
			assertEquals(12, gear.height);
			assertEquals("at the right edge at " + width, width, gear.x + gear.width);
			assertEquals("centred on the 20 px row", 4, gear.y);
			assertEquals(HeaderRow.HEIGHT, gear.y * 2 + gear.height);
		}
		assertEquals("the row's own width while it has no size", 213 - 12,
			onEdt(() -> new HeaderRow(() -> { })).gearBounds().x);
	}

	/** The gear is the LAST thing on the row: the title and the world words end left of it, and only it is inked there. */
	@Test
	public void theGearIsTheLastThingOnTheRow()
	{
		for (int width : new int[] {213})
		{
			// (the recorder and the painter size a block to its preferred 213; PanelFillTest covers 230)
			final HeaderRow row = header(width, new int[1]);
			final Rectangle gear = row.gearBounds();
			final List<Drawn> drawn = PanelFixtures.record(row);
			assertEquals("the title and the world words", 2, drawn.size());
			assertEquals("Why Lag", drawn.get(0).text);
			assertEquals("World 416", drawn.get(1).text);
			assertEquals("the world words end 6 px left of the gear", gear.x - 6, drawn.get(1).right());
			assertTrue("clear of the title", drawn.get(1).x > drawn.get(0).right());

			final BufferedImage img = PanelFixtures.paint(row);
			int ink = 0;
			for (int y = 0; y < img.getHeight(); y++)
			{
				for (int x = gear.x - 6 + 1; x < img.getWidth(); x++)
				{
					final boolean inked = (img.getRGB(x, y) & 0xFFFFFF) != (Ui.GROUND.getRGB() & 0xFFFFFF);
					if (inked)
					{
						assertTrue("ink at " + x + "," + y + " is inside the gear's box", gear.contains(x, y));
						ink++;
					}
				}
			}
			assertTrue("the gear is inked: " + ink, ink >= 25);
		}
	}

	/** Under the mouse the gear turns orange, and back to the light grey when the mouse leaves. */
	@Test
	public void theGearIsOrangeUnderTheMouse()
	{
		final HeaderRow row = header(213, new int[1]);
		final Rectangle gear = row.gearBounds();
		assertEquals("grey at rest", 0, count(PanelFixtures.paint(row), gear, Ui.ORANGE));
		assertTrue(count(PanelFixtures.paint(row), gear, Ui.LABEL) >= 20);

		edt(() -> row.dispatchEvent(new MouseEvent(row, MouseEvent.MOUSE_MOVED, 0, 0, gear.x + 6, 10, 0, false)));
		assertTrue(row.hot());
		assertTrue("orange under the mouse", count(PanelFixtures.paint(row), gear, Ui.ORANGE) >= 20);
		assertEquals(0, count(PanelFixtures.paint(row), gear, Ui.LABEL));

		edt(() -> row.dispatchEvent(new MouseEvent(row, MouseEvent.MOUSE_MOVED, 0, 0, 20, 10, 0, false)));
		assertFalse("off the gear: grey again", row.hot());
		edt(() -> row.dispatchEvent(new MouseEvent(row, MouseEvent.MOUSE_MOVED, 0, 0, gear.x + 6, 10, 0, false)));
		assertTrue(row.hot());
		edt(() -> row.dispatchEvent(new MouseEvent(row, MouseEvent.MOUSE_EXITED, 0, 0, -1, 10, 0, false)));
		assertFalse("the mouse left the row", row.hot());
	}

	/** The tooltip over the gear is "Settings"; anywhere else on the row there is none. */
	@Test
	public void theTooltipIsSettings()
	{
		final HeaderRow row = header(213, new int[1]);
		final Rectangle gear = row.gearBounds();
		assertEquals("Settings", HeaderRow.GEAR_TIP);
		assertEquals("Settings", PanelFixtures.tip(row, gear.x + 6, 10));
		assertEquals("over its hit area too", "Settings", PanelFixtures.tip(row, gear.x - 4, 0));
		assertNull(PanelFixtures.tip(row, gear.x - 5, 10));
		assertNull("over the title", PanelFixtures.tip(row, 10, 10));
		assertNull("over the world words", PanelFixtures.tip(row, gear.x - 20, 10));
	}

	/** A left press on the gear calls the hook; a press elsewhere, or with another button, does not. */
	@Test
	public void aPressOnTheGearCallsTheOpenMenuHook()
	{
		final int[] calls = new int[1];
		final HeaderRow row = header(213, calls);
		final Rectangle gear = row.gearBounds();

		press(row, gear.x + 6, 10);
		assertEquals(1, calls[0]);
		press(row, gear.x, 4);
		press(row, 212, 19);
		assertEquals("the gear's box and the far corner of its hit area", 3, calls[0]);
		press(row, gear.x - 4, 0);
		assertEquals("the hit area starts 4 px left of the gear", 4, calls[0]);

		press(row, gear.x - 5, 10);
		press(row, 10, 10);
		assertEquals("a press elsewhere on the row opens nothing", 4, calls[0]);

		edt(() -> row.dispatchEvent(new MouseEvent(row, MouseEvent.MOUSE_PRESSED, System.currentTimeMillis(),
			InputEvent.BUTTON3_DOWN_MASK, gear.x + 6, 10, 1, true, MouseEvent.BUTTON3)));
		assertEquals("a right press is a menu gesture, not a press", 4, calls[0]);
	}

	/** A press within 300 ms of the menu closing is the close it really was, and opens nothing. */
	@Test
	public void aPressWithinTheReopenGuardOfAClosingOpensNothing()
	{
		final int[] calls = new int[1];
		final HeaderRow row = header(213, calls);
		final long[] now = {50_000};
		edt(() -> row.clock = () -> now[0]);
		final Rectangle gear = row.gearBounds();
		assertEquals(300, HeaderRow.MENU_REOPEN_MS);
		assertTrue("nothing has closed yet: the first press opens", row.gearPressOpens());

		edt(row::menuClosed);
		for (long after : new long[] {0, 1, 100, HeaderRow.MENU_REOPEN_MS - 1})
		{
			now[0] = 50_000 + after;
			assertFalse(after + " ms after the close", row.gearPressOpens());
			press(row, gear.x + 6, 10);
		}
		assertEquals("every one of those was the close", 0, calls[0]);

		now[0] = 50_000 + HeaderRow.MENU_REOPEN_MS;
		assertTrue("the guard is over at exactly 300 ms", row.gearPressOpens());
		press(row, gear.x + 6, 10);
		assertEquals(1, calls[0]);

		now[0] += 10_000;
		edt(row::menuClosed);
		now[0] += 100;
		press(row, gear.x + 6, 10);
		assertEquals("a second close guards again", 1, calls[0]);
		now[0] += 200;
		press(row, gear.x + 6, 10);
		assertEquals(2, calls[0]);
	}

	/** How many pixels of {@code colour} an image holds inside {@code box}. */
	private static int count(BufferedImage img, Rectangle box, java.awt.Color colour)
	{
		int n = 0;
		for (int y = box.y; y < box.y + box.height; y++)
		{
			for (int x = box.x; x < box.x + box.width; x++)
			{
				n += (img.getRGB(x, y) & 0xFFFFFF) == (colour.getRGB() & 0xFFFFFF) ? 1 : 0;
			}
		}
		return n;
	}
}
