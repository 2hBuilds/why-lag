package com.whylag.ui;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import javax.swing.JComponent;
import org.junit.Test;
import static com.whylag.ui.PanelFixtures.Drawn;
import static com.whylag.ui.PanelFixtures.Fixture;
import static com.whylag.ui.PanelFixtures.contrast;
import static com.whylag.ui.PanelFixtures.record;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * Contrast (contract 5): every TEXT colour of an enabled control is 4.5:1 or better on its ground - measured on
 * every string every block draws, in every fixture, both fold states, with the developer-mode footer, the colour
 * as it lands (its alpha folded in) against the ground the recorder found under it. There is no button and no
 * dimmed text left since 1.0.1, lot C. A lane value's 1 px black shadow is part of that value, not a text of its
 * own. Stock red (230, 30, 30) is never used by drawString.
 */
public class ContrastTest
{
	private static final Color STOCK_RED = new Color(230, 30, 30);

	@Test
	public void everyTextOfAnEnabledControlIsFourAndAHalfToOne()
	{
		final List<String> failures = new ArrayList<>();
		int checked = 0;
		for (Fixture f : PanelFixtures.all())
		{
			for (boolean open : new boolean[] {false, true})
			{
				final Fixture withFooter = new Fixture(f.name, PanelFixtures.withFooter(f.snapshot,
					"self: frame 180 ns, tick 2 us, step 40 us"), f.selected);
				final WhyLagPanel p = PanelFixtures.panel(withFooter, open, open, true,
					new PanelFixtures.StubActions());
				for (JComponent block : PanelFixtures.blocks(p))
				{
					for (Drawn d : texts(record(block)))
					{
						checked++;
						final double ratio = contrast(d.seen(), d.ground);
						if (ratio < 4.5)
						{
							failures.add(f + (open ? " open " : " folded ") + block.getClass().getSimpleName() + ": "
								+ d + " " + d.seen() + " on " + d.ground + " = " + String.format("%.2f", ratio));
						}
					}
				}
			}
		}
		assertTrue("every string of every block was measured: " + checked, checked > 2000);
		if (!failures.isEmpty())
		{
			fail(failures.size() + " texts under 4.5:1\n  " + String.join("\n  ", failures.subList(0,
				Math.min(20, failures.size()))));
		}
	}

	/** The check reaches the culprit cell's three colours on its ground, and the footer's grey on the panel. */
	@Test
	public void theCulpritCellAndTheFooterAreChecked()
	{
		final WhyLagPanel p = PanelFixtures.panel(new Fixture("lag", PanelFixtures.withFooter(PanelFixtures.lag()
			.snapshot, "self: frame 180 ns, tick 2 us, step 40 us"), 3), false, false, true,
			new PanelFixtures.StubActions());
		boolean value = false;
		boolean label = false;
		for (Drawn d : texts(record(p.cells())))
		{
			if (d.ground.getRGB() == Ui.CULPRIT.getRGB())
			{
				value |= d.colour.getRGB() == Ui.BAD_TEXT.getRGB();
				label |= d.colour.getRGB() == Ui.CULPRIT_LABEL.getRGB();
				assertTrue(d.toString(), contrast(d.seen(), d.ground) >= 4.5);
			}
		}
		assertTrue("the culprit's red value on its ground", value);
		assertTrue("the culprit's name and unit on its ground", label);
		assertTrue(contrast(Ui.BAD_TEXT, Ui.CULPRIT) >= 4.5);
		assertTrue(contrast(Ui.CULPRIT_LABEL, Ui.CULPRIT) >= 4.5);

		final List<Drawn> footer = texts(record(p.footer()));
		assertEquals(1, footer.size());
		assertEquals(Ui.LABEL.getRGB(), footer.get(0).colour.getRGB());
		assertEquals(Ui.GROUND.getRGB(), footer.get(0).ground.getRGB());
		assertEquals("LABEL on the ground: 5.98:1", 5.98, contrast(Ui.LABEL, Ui.GROUND), 0.01);
	}

	@Test
	public void badIsNeverUsedByDrawString()
	{
		int red = 0;
		for (Fixture f : PanelFixtures.all())
		{
			final WhyLagPanel p = PanelFixtures.panel(f, true);
			for (JComponent block : PanelFixtures.blocks(p))
			{
				for (Drawn d : record(block))
				{
					assertFalse(f + " " + block.getClass().getSimpleName() + ": " + d,
						(d.colour.getRGB() & 0xFFFFFF) == (STOCK_RED.getRGB() & 0xFFFFFF));
					red += (d.colour.getRGB() & 0xFFFFFF) == (Ui.BAD_TEXT.getRGB() & 0xFFFFFF) ? 1 : 0;
				}
			}
		}
		assertTrue("red text is drawn, in 255,90,90", red > 10);
	}

	/** The texts to measure: every string but a lane value's black shadow (drawn one pixel right and down of the same string). */
	private static List<Drawn> texts(List<Drawn> drawn)
	{
		final List<Drawn> out = new ArrayList<>();
		for (int i = 0; i < drawn.size(); i++)
		{
			final Drawn d = drawn.get(i);
			final Drawn next = i + 1 < drawn.size() ? drawn.get(i + 1) : null;
			final boolean shadow = (d.colour.getRGB() & 0xFFFFFF) == 0 && next != null && next.text.equals(d.text)
				&& next.x == d.x - 1 && next.y == d.y - 1;
			if (!shadow)
			{
				out.add(d);
			}
		}
		return out;
	}
}
