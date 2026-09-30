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
import org.junit.Test;
import static com.whylag.ui.PanelFixtures.Fixture;
import static org.junit.Assert.assertEquals;
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
					PanelFixtures.paintPanel(p)));
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
			assertTrue(name, back.getHeight() > 300);
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
