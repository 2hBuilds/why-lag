package com.whylag.ui;

import com.whylag.GraphRange;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import javax.swing.JComponent;
import org.junit.Test;
import static com.whylag.ui.PanelFixtures.Fixture;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Pictures for the checker's eye (contract 7, L6; 8): every fixture painted FOLDED and OPENED as
 * {@code panel-<state>-folded} and {@code panel-<state>-open}, each with a greyscale copy ({@code ...-grey}), plus
 * the panel before its first snapshot; the three frames of picture 18 are among them ({@code panel-quiet-folded},
 * {@code panel-lag-folded}, {@code panel-lag-open}). Each is checked in memory: it has the panel's size and is not
 * blank. The local-only probe's {@code PicturesTest} writes them to {@code build/whylag/<name>.png}.
 */
public class PanelStatesRenderTest
{
	/**
	 * The pictures of the first test, by file name without the extension: every fixture folded and open, each with its
	 * greyscale copy ({@code ...-grey}), and the panel before its first snapshot. The probe's {@code PicturesTest}
	 * writes them to {@code build/whylag/<name>.png}.
	 */
	public static Map<String, BufferedImage> pictures()
	{
		final Map<String, BufferedImage> out = new LinkedHashMap<>();
		for (Fixture f : PanelFixtures.all())
		{
			for (boolean open : new boolean[] {false, true})
			{
				final WhyLagPanel p = PanelFixtures.panel(f, open);
				withGrey(out, "panel-" + f.name + (open ? "-open" : "-folded"), PanelFixtures.paintPanel(f, p));
			}
		}
		final WhyLagPanel first = PanelFixtures.onEdt(() ->
		{
			final WhyLagPanel p = new WhyLagPanel(new PanelFixtures.StubActions(), GraphRange.TEN_MIN, false);
			p.onActivate();
			return p;
		});
		withGrey(out, "panel-first-folded", PanelFixtures.paintPanel(first));
		return out;
	}

	@Test
	public void everyStateIsPaintedFoldedAndOpened()
	{
		final Map<String, BufferedImage> written = pictures();

		for (String name : new String[] {"panel-quiet-folded", "panel-lag-folded", "panel-lag-open"})
		{
			assertTrue("a frame of picture 18: " + name, written.containsKey(name));
			assertTrue(written.containsKey(name + "-grey"));
		}
		for (Map.Entry<String, BufferedImage> e : written.entrySet())
		{
			final String name = e.getKey();
			final BufferedImage back = e.getValue();
			assertTrue("every picture this test makes is a panel-... one: " + name, name.startsWith("panel-"));
			assertEquals(name, 225, back.getWidth());
			// (the panel before its first snapshot is 285 high since the button went, 1.0.1, lot C)
			assertTrue(name, back.getHeight() > 250);
			// Even a quiet folded panel in greys holds eight colours or more.
			assertTrue(name + " is not blank", colours(back) > 7);
		}
		assertEquals((PanelFixtures.all().size() * 2 + 1) * 2, written.size());
	}

	/**
	 * The panel as the client shows it: 242 wide (the sidebar's 225 and the 17 px RuneLite keeps for a scroll bar), so
	 * the blocks are 230 wide, 6 px in from each edge: the quiet and the lag state, both folds open, as
	 * {@code panel-quiet-242} and {@code panel-lag-242}, for the eye (the probe's {@code PicturesTest} writes them).
	 */
	public static Map<String, BufferedImage> widePictures()
	{
		final Map<String, BufferedImage> out = new LinkedHashMap<>();
		for (Fixture f : new Fixture[] {PanelFixtures.quiet(), PanelFixtures.lag()})
		{
			out.put("panel-" + f.name + "-242", PanelFixtures.paintPanelAt(PanelFixtures.panel(f, true), 242));
		}
		return out;
	}

	@Test
	public void theWidePanelIsPaintedAt242()
	{
		final Map<String, BufferedImage> wide = widePictures();
		assertEquals(2, wide.size());
		for (Map.Entry<String, BufferedImage> e : wide.entrySet())
		{
			final BufferedImage img = e.getValue();
			assertEquals(e.getKey(), 242, img.getWidth());
			assertTrue(e.getKey(), img.getHeight() > 300);
			assertTrue(e.getKey() + " is not blank", colours(img) > 8);
		}
	}

	/**
	 * 1.0.1, lot C: every state, folded and opened, ends with the session's counts - the version row, the verdict line
	 * and the button are gone - and in developer mode the self line is painted under them, its words in the light
	 * grey.
	 */
	@Test
	public void everyStateEndsWithTheSessionCounts()
	{
		for (Fixture f : PanelFixtures.all())
		{
			for (boolean open : new boolean[] {false, true})
			{
				final WhyLagPanel p = PanelFixtures.panel(f, open);
				final BufferedImage img = PanelFixtures.paintPanel(f, p);
				final SessionCounts counts = p.counts();
				assertEquals(f.name + ": the counts end the content", img.getHeight(),
					counts.getY() + counts.getHeight() + p.getInsets().bottom);
				assertEquals(f.name + ": it is the panel's last block", counts, p.getComponent(p.getComponentCount() - 1));
			}
		}

		final Fixture lag = PanelFixtures.lag();
		final Fixture dev = new Fixture("dev", PanelFixtures.withFooter(lag.snapshot,
			"self: frame 180 ns, tick 2 us, step 40 us"), -1);
		final WhyLagPanel p = PanelFixtures.panel(dev, true, true, true, new PanelFixtures.StubActions());
		final BufferedImage img = PanelFixtures.paintPanel(p);
		final SessionCounts counts = p.counts();
		final JComponent self = p.footer();
		assertTrue("the self line is painted", hasLabelColour(img, self.getY(), self.getHeight()));
		assertTrue("under the counts", self.getY() >= counts.getY() + counts.getHeight());
		assertEquals("and ends the content", img.getHeight(), self.getY() + self.getHeight() + p.getInsets().bottom);
	}

	/**
	 * 1.0.1, lot C: every state draws the gear in the header's right end - ink in its 12 x 12 box, at the row's right
	 * edge - and the world words clear of it; no block draws "Test and copy report" any more, and the verdict line
	 * that used to show under it is gone. Only the gear-open fixture has the menu in its picture: the menu's own
	 * words are painted over the panel there and nowhere else.
	 */
	@Test
	public void everyStateDrawsTheGearAndNoButton()
	{
		int menus = 0;
		for (Fixture f : PanelFixtures.all())
		{
			for (boolean open : new boolean[] {false, true})
			{
				final WhyLagPanel p = PanelFixtures.panel(f, open);
				final BufferedImage img = PanelFixtures.paintPanel(f, p);
				final HeaderRow header = p.header();
				final java.awt.Rectangle gear = header.gearBounds();
				assertEquals(f.name + ": 12 x 12", 12, gear.width);
				assertEquals(f.name + ": 12 x 12", 12, gear.height);
				int ink = 0;
				for (int y = 0; y < gear.height; y++)
				{
					for (int x = 0; x < gear.width; x++)
					{
						final int rgb = img.getRGB(header.getX() + gear.x + x, header.getY() + gear.y + y);
						ink += (rgb & 0xFFFFFF) == (Ui.LABEL.getRGB() & 0xFFFFFF) ? 1 : 0;
					}
				}
				assertTrue(f.name + ": the gear is inked in the light grey: " + ink, ink >= 20);
				for (PanelFixtures.Drawn d : PanelFixtures.record(header))
				{
					assertTrue(f.name + ": " + d + " is clear of the gear", d.right() <= gear.x);
				}
				for (JComponent block : PanelFixtures.blocks(p))
				{
					for (PanelFixtures.Drawn d : PanelFixtures.record(block))
					{
						assertFalse(f.name + ": no button label", d.text.equals("Test and copy report"));
					}
				}
				if (f.menuOpen)
				{
					menus++;
					assertTrue(f.name + ": the menu was built", p.menu() != null);
				}
			}
		}
		assertEquals("the gear-open fixture, folded and open", 2, menus);
	}

	/** True when a pixel of the light grey of the small lines is painted in the rows {@code y .. y + h - 1}. */
	private static boolean hasLabelColour(BufferedImage img, int y, int h)
	{
		for (int yy = y; yy < y + h; yy++)
		{
			for (int x = 0; x < img.getWidth(); x++)
			{
				if ((img.getRGB(x, yy) & 0xFFFFFF) == (Ui.LABEL.getRGB() & 0xFFFFFF))
				{
					return true;
				}
			}
		}
		return false;
	}

	/** Adds the picture and its greyscale copy ({@code name + "-grey"}). */
	private static void withGrey(Map<String, BufferedImage> out, String name, BufferedImage img)
	{
		final BufferedImage grey = new BufferedImage(img.getWidth(), img.getHeight(), BufferedImage.TYPE_BYTE_GRAY);
		final Graphics2D g = grey.createGraphics();
		try
		{
			g.drawImage(img, 0, 0, null);
		}
		finally
		{
			g.dispose();
		}
		out.put(name, img);
		out.put(name + "-grey", grey);
	}

	private static int colours(BufferedImage img)
	{
		final Set<Integer> seen = new HashSet<>();
		for (int y = 0; y < img.getHeight(); y++)
		{
			for (int x = 0; x < img.getWidth(); x++)
			{
				seen.add(img.getRGB(x, y));
			}
		}
		return seen.size();
	}
}
