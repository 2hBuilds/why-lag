package com.whylag;

import com.whylag.core.BadgeStyle;
import com.whylag.core.BadgeView;
import com.whylag.core.Icon;
import com.whylag.core.Level;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.function.Supplier;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.ui.overlay.infobox.InfoBoxManager;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * The badge as a RuneLite infobox (addendum D): it renders only a visible view of a style without words, paints a
 * view once and tells the manager once, keeps the overlay's tooltip text, and shows no words. {@link BadgePainter}
 * is final, so the counting painter is a function that counts and hands the view to a real painter (the infobox's
 * package-private constructor), as {@code BadgeOverlayTest} does.
 */
public class BadgeInfoBoxTest
{
	private static final BadgePainter PAINTER = new BadgePainter(new BadgeIcons());

	private static final BadgeView LAG = new BadgeView(true, BadgeStyle.ICON, Level.WARN, Icon.WORLD, false,
		"World lag", "Not you", "World lag - not you", "Ticks 1,240 ms, ping 41 ms");
	private static final BadgeView SHAPE = new BadgeView(true, BadgeStyle.SHAPE_ONLY, Level.BAD, Icon.PC, false,
		"FPS low", "Your PC", "FPS low - your PC", "Frames 12 a second");

	/** A painter that counts its calls, keeps what it answered, and paints with the real one. */
	private static final class CountingPainter implements Function<BadgeView, BufferedImage>
	{
		int calls;
		BufferedImage last;

		@Override
		public BufferedImage apply(BadgeView v)
		{
			calls++;
			last = PAINTER.bare(v);
			return last;
		}
	}

	private static BadgeInfoBox box(Supplier<BadgeView> view, CountingPainter painter,
		InfoBoxManager manager)
	{
		return new BadgeInfoBox(mock(Plugin.class), view, painter, manager);
	}

	@Test
	public void aSmallStyleViewRendersItsPicture()
	{
		final InfoBoxManager manager = mock(InfoBoxManager.class);
		final CountingPainter painter = new CountingPainter();
		final BadgeInfoBox box = box(() -> LAG, painter, manager);

		assertTrue(box.render());
		assertEquals(1, painter.calls);
		assertSame("the image is the painter's picture", painter.last, box.getImage());
		assertEquals("the 24 x 24 icon", 24, box.getImage().getWidth());
		assertEquals(24, box.getImage().getHeight());
		verify(manager, times(1)).updateInfoBoxImage(box);
		verify(manager, times(1)).updateInfoBoxImage(any());

		// The real painter's picture, pixel for pixel.
		final BufferedImage want = PAINTER.bare(LAG);
		for (int y = 0; y < want.getHeight(); y++)
		{
			for (int x = 0; x < want.getWidth(); x++)
			{
				assertEquals("(" + x + ", " + y + ")", want.getRGB(x, y), box.getImage().getRGB(x, y));
			}
		}
	}

	@Test
	public void aShapeOnlyViewRendersToo()
	{
		final InfoBoxManager manager = mock(InfoBoxManager.class);
		final CountingPainter painter = new CountingPainter();
		final BadgeInfoBox box = box(() -> SHAPE, painter, manager);

		assertTrue(box.render());
		assertEquals(1, painter.calls);
		assertSame(painter.last, box.getImage());
		assertEquals("the 14 px shape doubled", 28, box.getImage().getWidth());
		assertEquals(28, box.getImage().getHeight());
		verify(manager, times(1)).updateInfoBoxImage(box);
	}

	@Test
	public void aWordStyleViewDoesNotRender()
	{
		for (BadgeStyle style : new BadgeStyle[] {BadgeStyle.ICON_AND_WORDS, BadgeStyle.SHAPE_AND_WORDS})
		{
			final BadgeView words = new BadgeView(true, style, Level.BAD, Icon.WORLD, false, "World lag", "Not you",
				"World lag - not you", "Ticks 1,240 ms, ping 41 ms");
			final InfoBoxManager manager = mock(InfoBoxManager.class);
			final CountingPainter painter = new CountingPainter();
			final BadgeInfoBox box = box(() -> words, painter, manager);
			for (int i = 0; i < 3; i++)
			{
				assertFalse(style + " belongs to the overlay", box.render());
			}
			assertEquals("no paint for " + style, 0, painter.calls);
			assertNull("no image for " + style, box.getImage());
			verifyNoInteractions(manager);
		}
	}

	@Test
	public void aHiddenViewDoesNotRender()
	{
		final InfoBoxManager manager = mock(InfoBoxManager.class);
		final CountingPainter painter = new CountingPainter();
		final AtomicReference<BadgeView> now = new AtomicReference<>(BadgeView.HIDDEN);
		final BadgeInfoBox box = box(now::get, painter, manager);
		assertFalse(box.render());
		assertEquals("", box.getTooltip());
		assertNull(box.getImage());

		now.set(null);
		assertFalse("a null view is drawn as nothing", box.render());
		assertEquals("", box.getTooltip());

		now.set(new BadgeView(false, BadgeStyle.ICON, Level.BAD, Icon.WORLD, false, "World lag", "Not you",
			"World lag - not you", "Ticks 1,240 ms, ping 41 ms"));
		assertFalse("not visible, whatever else it holds", box.render());
		assertEquals("", box.getTooltip());

		assertEquals("never handed to the painter", 0, painter.calls);
		verify(manager, never()).updateInfoBoxImage(any());

		// A picture that goes away again: the infobox takes no room, and keeps no tooltip.
		now.set(LAG);
		assertTrue(box.render());
		assertNotNull(box.getImage());
		assertFalse("kept text", box.getTooltip().isEmpty());
		now.set(BadgeView.HIDDEN);
		assertFalse(box.render());
		assertNull(box.getImage());
		assertEquals("", box.getTooltip());
	}

	/** T18: three frames of one view paint it once and tell the manager once. */
	@Test
	public void anUnchangedViewIsPaintedOnce()
	{
		final InfoBoxManager manager = mock(InfoBoxManager.class);
		final CountingPainter painter = new CountingPainter();
		final AtomicInteger reads = new AtomicInteger();
		final BadgeInfoBox box = box(() ->
		{
			reads.incrementAndGet();
			return LAG;
		}, painter, manager);
		for (int frame = 1; frame <= 3; frame++)
		{
			assertTrue(box.render());
			assertEquals("the supplier is read once a frame", frame, reads.get());
		}
		assertEquals("painted once in three frames", 1, painter.calls);
		verify(manager, times(1)).updateInfoBoxImage(box);
		verify(manager, times(1)).updateInfoBoxImage(any());
	}

	@Test
	public void aNewViewIsPaintedAgain()
	{
		final InfoBoxManager manager = mock(InfoBoxManager.class);
		final CountingPainter painter = new CountingPainter();
		final AtomicReference<BadgeView> now = new AtomicReference<>(LAG);
		final BadgeInfoBox box = box(now::get, painter, manager);
		assertTrue(box.render());
		final BufferedImage first = box.getImage();

		now.set(SHAPE);
		assertTrue(box.render());
		assertEquals(2, painter.calls);
		assertNotSame(first, box.getImage());
		assertEquals(28, box.getImage().getWidth());
		verify(manager, times(2)).updateInfoBoxImage(box);

		final BadgeView again = new BadgeView(true, BadgeStyle.SHAPE_ONLY, Level.BAD, Icon.PC, false, "FPS low",
			"Your PC", "FPS low - your PC", "Frames 12 a second");
		assertTrue(again.sameAs(SHAPE));
		now.set(again);
		assertTrue(box.render());
		assertEquals("a new object is a new view (the model answers the SAME object while nothing changed)", 3,
			painter.calls);
		verify(manager, times(3)).updateInfoBoxImage(box);
	}

	@Test
	public void theTooltipIsTheOverlaysText()
	{
		final AtomicReference<BadgeView> now = new AtomicReference<>(LAG);
		final BadgeInfoBox box = box(now::get, new CountingPainter(), mock(InfoBoxManager.class));
		assertTrue(box.render());
		assertEquals("<col=f0a028>World lag - not you</col></br>Ticks 1,240 ms, ping 41 ms", box.getTooltip());
		assertEquals("the same text as the overlay's hover", BadgeOverlay.tooltipText(LAG), box.getTooltip());

		final BadgeView smooth = new BadgeView(true, BadgeStyle.ICON, Level.OK, Icon.NONE, false, "Smooth", "",
			"Smooth. No lag for 40 s.", "");
		now.set(smooth);
		assertTrue(box.render());
		assertEquals("no break without tip2", "<col=37f046>Smooth. No lag for 40 s.</col>", box.getTooltip());
		assertFalse(box.getTooltip().contains("</br>"));

		final BadgeView noTip = new BadgeView(true, BadgeStyle.SHAPE_ONLY, Level.BAD, Icon.WORLD, false, "World lag",
			"Not you", "", "Ticks 1,240 ms, ping 41 ms");
		now.set(noTip);
		assertTrue("the badge itself is still drawn", box.render());
		assertEquals("no tip1, no tooltip", "", box.getTooltip());

		now.set(SHAPE);
		assertTrue(box.render());
		assertEquals("<col=ff5a5a>FPS low - your PC</col></br>Frames 12 a second", box.getTooltip());
	}

	@Test
	public void noWords()
	{
		final AtomicReference<BadgeView> now = new AtomicReference<>(LAG);
		final BadgeInfoBox box = box(now::get, new CountingPainter(), mock(InfoBoxManager.class));
		assertEquals("before the first frame", "", box.getText());
		assertTrue(box.render());
		assertEquals("a visible view", "", box.getText());
		now.set(BadgeView.HIDDEN);
		assertFalse(box.render());
		assertEquals("a hidden view", "", box.getText());
	}

	@Test
	public void theTextColourIsLineOnes()
	{
		final AtomicReference<BadgeView> now = new AtomicReference<>(null);
		final BadgeInfoBox box = box(now::get, new CountingPainter(), mock(InfoBoxManager.class));
		assertEquals("NO_DATA's before the first frame", new Color(170, 170, 170), box.getTextColor());

		final Color[] colours = {new Color(55, 240, 70), new Color(240, 160, 40), new Color(255, 90, 90),
			new Color(170, 170, 170)};
		for (Level level : Level.values())
		{
			now.set(new BadgeView(true, BadgeStyle.SHAPE_ONLY, level, Icon.NONE, false, "x", "", "x", ""));
			box.render();
			assertEquals(level.toString(), colours[level.ordinal()], box.getTextColor());
			assertEquals(BadgePainter.lineOneColour(level), box.getTextColor());
		}
	}

	@Test
	public void itBelongsToThePlugin()
	{
		final Plugin plugin = mock(Plugin.class);
		final BadgeInfoBox box = new BadgeInfoBox(plugin, () -> LAG, PAINTER, mock(InfoBoxManager.class));
		assertNull("no picture before the first frame", box.getImage());
		assertTrue("RuneLite gets it into the row with no priority of ours", box.getMenuEntries().isEmpty());
		assertTrue(box.render());
		assertNotNull(box.getImage());
	}
}
