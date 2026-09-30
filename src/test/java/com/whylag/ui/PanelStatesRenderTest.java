package com.whylag.ui;

import com.whylag.GraphRange;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import javax.imageio.ImageIO;
import javax.swing.JComponent;
import org.junit.Test;
import static com.whylag.ui.PanelFixtures.Fixture;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Pictures for the checker's eye (contract 7, L6; 8): every fixture painted FOLDED and OPENED to
 * {@code build/whylag/panel-<state>-folded.png} and {@code panel-<state>-open.png}, each with a greyscale copy
 * ({@code ...-grey.png}), plus the panel before its first snapshot; the three frames of picture 18 are among them
 * ({@code panel-quiet-folded}, {@code panel-lag-folded}, {@code panel-lag-open}). The files are read back: each
 * exists, has the panel's size and is not blank.
 *
 * <p><b>The folder is shared</b> with the badge's render test: this test makes {@code build/whylag/} when it is
 * missing, names every file it writes {@code panel-...}, replaces only those, and deletes nothing.
 */
public class PanelStatesRenderTest
{
	@Test
	public void everyStateIsPaintedFoldedAndOpened() throws IOException
	{
		final Path dir = PanelFixtures.projectRoot().resolve("build").resolve("whylag");
		Files.createDirectories(dir);
		final List<String> written = new ArrayList<>();
		for (Fixture f : PanelFixtures.all())
		{
			for (boolean open : new boolean[] {false, true})
			{
				final WhyLagPanel p = PanelFixtures.panel(f, open);
				written.addAll(write(dir, "panel-" + f.name + (open ? "-open" : "-folded"),
					PanelFixtures.paintPanel(f, p)));
			}
		}
		final WhyLagPanel first = PanelFixtures.onEdt(() ->
		{
			final WhyLagPanel p = new WhyLagPanel(new PanelFixtures.StubActions(), GraphRange.TEN_MIN, false);
			p.onActivate();
			return p;
		});
		written.addAll(write(dir, "panel-first-folded", PanelFixtures.paintPanel(first)));

		for (String name : new String[] {"panel-quiet-folded", "panel-lag-folded", "panel-lag-open"})
		{
			assertTrue("a frame of picture 18: " + name, written.contains(name + ".png"));
			assertTrue(written.contains(name + "-grey.png"));
		}
		for (String name : written)
		{
			assertTrue("every file this test writes is a panel-... file: " + name, name.startsWith("panel-"));
			final File file = dir.resolve(name).toFile();
			assertTrue("written: " + file, file.isFile());
			final BufferedImage back = ImageIO.read(file);
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
	 * the blocks are 230 wide, 6 px in from each edge. The quiet and the lag state, both folds open, are written to
	 * {@code build/whylag/panel-quiet-242.png} and {@code panel-lag-242.png} for the eye, and read back.
	 */
	@Test
	public void theWidePanelIsPaintedAt242() throws IOException
	{
		final Path dir = PanelFixtures.projectRoot().resolve("build").resolve("whylag");
		Files.createDirectories(dir);
		for (Fixture f : new Fixture[] {PanelFixtures.quiet(), PanelFixtures.lag()})
		{
			final BufferedImage img = PanelFixtures.paintPanelAt(PanelFixtures.panel(f, true), 242);
			final File file = dir.resolve("panel-" + f.name + "-242.png").toFile();
			assertTrue("wrote " + file, ImageIO.write(img, "png", file));
			final BufferedImage back = ImageIO.read(file);
			assertEquals(f.name, 242, back.getWidth());
			assertTrue(f.name, back.getHeight() > 300);
			assertTrue(f.name + " is not blank", colours(back) > 8);
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

	/** Writes the picture and its greyscale copy; answers the two file names. */
	private static List<String> write(Path dir, String name, BufferedImage img) throws IOException
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
		final List<String> names = new ArrayList<>();
		for (String file : new String[] {name + ".png", name + "-grey.png"})
		{
			assertTrue("wrote " + file, ImageIO.write(file.endsWith("-grey.png") ? grey : img, "png",
				dir.resolve(file).toFile()));
			names.add(file);
		}
		return names;
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
