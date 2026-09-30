package com.whylag;

import com.whylag.core.BadgeStyle;
import com.whylag.core.BadgeView;
import com.whylag.core.Icon;
import com.whylag.core.Level;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.image.BufferedImage;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.function.Supplier;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.tooltip.Tooltip;
import net.runelite.client.ui.overlay.tooltip.TooltipManager;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;

/**
 * The badge's overlay (contract P2.5, T18): it sits top left with RuneLite's defaults; an unchanged view is painted
 * ONCE and answers one {@link Dimension} object; a new view is painted once; the supplier is read once a frame; a
 * hidden view renders nothing; the hover adds one kept {@link Tooltip} whose text is {@code tip1} in line 1's colour,
 * {@code </br>} and {@code tip2}; a visible view of a style without words is the infobox's and renders null
 * (addendum D), so the views here are of the word styles. {@link BadgePainter} is final, so the counting painter is
 * a function that counts and hands the view to a real painter (the overlay's package-private constructor).
 */
public class BadgeOverlayTest
{
	private static final BadgePainter PAINTER = new BadgePainter(new BadgeIcons());

	/** The overlay draws the styles with words only (addendum D): the two without words are the infobox's. */
	private static final BadgeView LAG = new BadgeView(true, BadgeStyle.ICON_AND_WORDS, Level.BAD, Icon.WORLD, false,
		"World lag", "Not you", "World lag - not you", "Ticks 1,240 ms, ping 41 ms");
	private static final BadgeView SMOOTH = new BadgeView(true, BadgeStyle.ICON_AND_WORDS, Level.OK, Icon.NONE, false,
		"Smooth", "", "Smooth. No lag for 4 min.", "");

	/** A painter that counts its calls and paints with the real one. */
	private static final class CountingPainter implements Function<BadgeView, BufferedImage>
	{
		int calls;

		@Override
		public BufferedImage apply(BadgeView v)
		{
			calls++;
			return PAINTER.paint(v);
		}
	}

	@Test
	public void itSitsTopLeft()
	{
		final BadgeOverlay overlay = new BadgeOverlay(() -> LAG, PAINTER, mock(TooltipManager.class));
		assertSame(OverlayPosition.TOP_LEFT, overlay.getPosition());
		assertSame("RuneLite's default layer", OverlayLayer.UNDER_WIDGETS, overlay.getLayer());
		assertEquals("RuneLite's default priority", Overlay.PRIORITY_DEFAULT, overlay.getPriority(), 0f);
		assertTrue("Alt + drag moves it", overlay.isMovable());
		assertTrue(overlay.isSnappable());
		assertEquals("its place is remembered by the class name", "BadgeOverlay", overlay.getName());
	}

	/** T18: 100 frames of one view paint it once and answer one {@code Dimension} object, the picture's size. */
	@Test
	public void anUnchangedViewIsPaintedOnce()
	{
		final CountingPainter painter = new CountingPainter();
		final BadgeOverlay overlay = new BadgeOverlay(() -> LAG, painter, mock(TooltipManager.class));
		final Canvas canvas = new Canvas();
		final Dimension first = overlay.render(canvas.g);
		assertNotNull(first);
		assertEquals("the picture's size", sizeOf(LAG), first);
		for (int i = 0; i < 99; i++)
		{
			assertSame("the kept Dimension, frame " + i, first, overlay.render(canvas.g));
		}
		assertEquals("painted once in 100 frames", 1, painter.calls);
		canvas.dispose();

		final BufferedImage a = PAINTER.paint(LAG);
		final BufferedImage b = PAINTER.paint(LAG);
		assertNotSame("the painter makes a new picture on every call, so only the overlay's keeping saves it", a, b);
	}

	/** Every new view object is painted once, in the first frame that sees it; one that is sameAs is still new. */
	@Test
	public void aNewViewIsPaintedOnce()
	{
		final CountingPainter painter = new CountingPainter();
		final AtomicReference<BadgeView> now = new AtomicReference<>(SMOOTH);
		final BadgeOverlay overlay = new BadgeOverlay(now::get, painter, mock(TooltipManager.class));
		final Canvas canvas = new Canvas();

		final Dimension smooth = render(overlay, canvas, 10);
		assertEquals(1, painter.calls);
		assertEquals(sizeOf(SMOOTH), smooth);

		now.set(LAG);
		final Dimension lag = render(overlay, canvas, 10);
		assertEquals("the lag, painted in its first frame and kept", 2, painter.calls);
		assertEquals(sizeOf(LAG), lag);
		assertNotSame(smooth, lag);

		final BadgeView again = new BadgeView(true, BadgeStyle.ICON_AND_WORDS, Level.BAD, Icon.WORLD, false,
			"World lag", "Not you", "World lag - not you", "Ticks 1,240 ms, ping 41 ms");
		assertTrue(again.sameAs(LAG));
		now.set(again);
		render(overlay, canvas, 10);
		assertEquals("a new object is a new view (the model answers the SAME object while nothing changed)", 3,
			painter.calls);

		now.set(SMOOTH);
		assertEquals(sizeOf(SMOOTH), render(overlay, canvas, 10));
		assertEquals(4, painter.calls);
		canvas.dispose();
	}

	@Test
	public void renderReadsTheSupplierOncePerFrame()
	{
		final AtomicInteger reads = new AtomicInteger();
		final Supplier<BadgeView> view = () ->
		{
			reads.incrementAndGet();
			return LAG;
		};
		final BadgeOverlay overlay = new BadgeOverlay(view, PAINTER, mock(TooltipManager.class));
		final Canvas canvas = new Canvas();
		for (int frame = 1; frame <= 50; frame++)
		{
			overlay.render(canvas.g);
			assertEquals("frame " + frame, frame, reads.get());
		}
		for (int i = 0; i < 20; i++)
		{
			overlay.onMouseOver();
		}
		assertEquals("the hover reads nothing", 50, reads.get());
		canvas.dispose();
	}

	/**
	 * Each frame draws the ONE kept picture at (0, 0) and does nothing else with the graphics: the picture is the
	 * painter's picture of the view, pixel for pixel.
	 */
	@Test
	public void theKeptPictureIsDrawnAtTheOrigin()
	{
		final BadgeOverlay overlay = new BadgeOverlay(() -> LAG, PAINTER, mock(TooltipManager.class));
		final Graphics2D g = mock(Graphics2D.class);
		for (int i = 0; i < 5; i++)
		{
			overlay.render(g);
		}
		final ArgumentCaptor<Image> drawn = ArgumentCaptor.forClass(Image.class);
		verify(g, times(5)).drawImage(drawn.capture(), eq(0), eq(0), isNull());
		verifyNoMoreInteractions(g);
		final List<Image> all = drawn.getAllValues();
		for (Image image : all)
		{
			assertSame("one kept picture", all.get(0), image);
		}
		final BufferedImage kept = (BufferedImage) all.get(0);
		final BufferedImage fresh = PAINTER.paint(LAG);
		assertEquals(fresh.getWidth(), kept.getWidth());
		assertEquals(fresh.getHeight(), kept.getHeight());
		for (int y = 0; y < fresh.getHeight(); y++)
		{
			for (int x = 0; x < fresh.getWidth(); x++)
			{
				assertEquals("(" + x + ", " + y + ")", fresh.getRGB(x, y), kept.getRGB(x, y));
			}
		}
	}

	/** A hidden view renders null and draws nothing: RuneLite then gives the badge no bounds, no hover, no drag. */
	@Test
	public void aHiddenViewRendersNull()
	{
		final CountingPainter painter = new CountingPainter();
		final AtomicReference<BadgeView> now = new AtomicReference<>(LAG);
		final BadgeOverlay overlay = new BadgeOverlay(now::get, painter, mock(TooltipManager.class));
		final Canvas canvas = new Canvas();
		assertNotNull(overlay.render(canvas.g));
		canvas.dispose();

		now.set(BadgeView.HIDDEN);
		final Graphics2D g = mock(Graphics2D.class);
		for (int i = 0; i < 10; i++)
		{
			assertNull(overlay.render(g));
		}
		verifyNoInteractions(g);
		assertEquals("HIDDEN is asked once, then kept", 2, painter.calls);

		final Canvas hidden = new Canvas();
		assertNull(overlay.render(hidden.g));
		assertTrue("nothing is drawn", hidden.blank());
		hidden.dispose();

		now.set(null);
		assertNull("a null view is drawn as nothing", overlay.render(g));
		verifyNoInteractions(g);
		assertEquals("and never handed to the painter", 2, painter.calls);
	}

	@Test
	public void theHoverAddsTheTooltip()
	{
		final TooltipManager tooltips = mock(TooltipManager.class);
		final AtomicReference<BadgeView> now = new AtomicReference<>(LAG);
		final BadgeOverlay overlay = new BadgeOverlay(now::get, PAINTER, tooltips);
		final Canvas canvas = new Canvas();
		overlay.onMouseOver();
		verifyNoInteractions(tooltips);

		render(overlay, canvas, 5);
		for (int i = 0; i < 3; i++)
		{
			overlay.onMouseOver();
		}
		final ArgumentCaptor<Tooltip> added = ArgumentCaptor.forClass(Tooltip.class);
		verify(tooltips, times(3)).add(added.capture());
		final List<Tooltip> all = added.getAllValues();
		assertSame("one kept Tooltip while the view is the same", all.get(0), all.get(1));
		assertSame(all.get(0), all.get(2));
		assertEquals("<col=ff5a5a>World lag - not you</col></br>Ticks 1,240 ms, ping 41 ms", all.get(0).getText());

		// tip1 takes the colour of line 1 for each level
		assertEquals("<col=f0a028>FPS capped - your setting</col></br>Set by FPS Control. Not lag.",
			hoverText(new BadgeView(true, BadgeStyle.ICON_AND_WORDS, Level.WARN, Icon.PC, false, "FPS capped",
				"Your setting",
				"FPS capped - your setting", "Set by FPS Control. Not lag.")));
		assertEquals("<col=aaaaaa>Still measuring</col></br>Ready in 40 s.",
			hoverText(new BadgeView(true, BadgeStyle.SHAPE_AND_WORDS, Level.NO_DATA, Icon.NONE, false, "Measuring", "",
				"Still measuring", "Ready in 40 s.")));
		assertEquals("<col=37f046>Smooth.</col></br>Last lag: World lag - not you",
			hoverText(new BadgeView(true, BadgeStyle.ICON_AND_WORDS, Level.OK, Icon.NONE, false, "Smooth", "",
				"Smooth.", "Last lag: World lag - not you")));

		// a new view makes a new Tooltip once, then keeps it
		now.set(SMOOTH);
		render(overlay, canvas, 3);
		overlay.onMouseOver();
		overlay.onMouseOver();
		final ArgumentCaptor<Tooltip> later = ArgumentCaptor.forClass(Tooltip.class);
		verify(tooltips, times(5)).add(later.capture());
		final List<Tooltip> five = later.getAllValues();
		assertNotSame(all.get(0), five.get(3));
		assertSame(five.get(3), five.get(4));
		assertEquals("<col=37f046>Smooth. No lag for 4 min.</col>", five.get(3).getText());
		canvas.dispose();
	}

	@Test
	public void noSecondLineNoBreak()
	{
		final String text = hoverText(SMOOTH);
		assertEquals("<col=37f046>Smooth. No lag for 4 min.</col>", text);
		assertFalse(text.contains("</br>"));
	}

	@Test
	public void noTooltipForAHiddenView()
	{
		final TooltipManager tooltips = mock(TooltipManager.class);
		final AtomicReference<BadgeView> now = new AtomicReference<>(BadgeView.HIDDEN);
		final BadgeOverlay overlay = new BadgeOverlay(now::get, PAINTER, tooltips);
		final Canvas canvas = new Canvas();
		render(overlay, canvas, 3);
		overlay.onMouseOver();
		verifyNoInteractions(tooltips);

		now.set(new BadgeView(false, BadgeStyle.ICON, Level.BAD, Icon.WORLD, false, "World lag", "Not you",
			"World lag - not you", "Ticks 1,240 ms, ping 41 ms"));
		render(overlay, canvas, 3);
		overlay.onMouseOver();
		verifyNoInteractions(tooltips);

		now.set(LAG);
		render(overlay, canvas, 1);
		overlay.onMouseOver();
		verify(tooltips, times(1)).add(any(Tooltip.class));
		now.set(BadgeView.HIDDEN);
		render(overlay, canvas, 1);
		overlay.onMouseOver();
		overlay.onMouseOver();
		verify(tooltips, times(1)).add(any(Tooltip.class));
		canvas.dispose();
	}

	@Test
	public void noTooltipWithoutText()
	{
		final TooltipManager tooltips = mock(TooltipManager.class);
		final BadgeView noTip = new BadgeView(true, BadgeStyle.ICON_AND_WORDS, Level.BAD, Icon.WORLD, false,
			"World lag", "Not you", "", "Ticks 1,240 ms, ping 41 ms");
		final BadgeOverlay overlay = new BadgeOverlay(() -> noTip, PAINTER, tooltips);
		final Canvas canvas = new Canvas();
		assertEquals("the badge itself is drawn", sizeOf(noTip), overlay.render(canvas.g));
		overlay.onMouseOver();
		verifyNoInteractions(tooltips);
		canvas.dispose();
	}

	/**
	 * The two styles without words (ICON, SHAPE_ONLY) are the infobox's (addendum D): a visible view of either is
	 * treated as hidden here. Nothing is painted, null is answered, the painter is not asked, and a hover adds no
	 * tooltip; the view of a word style right after it is drawn as usual.
	 */
	@Test
	public void aSmallStyleViewRendersNull()
	{
		final TooltipManager tooltips = mock(TooltipManager.class);
		final CountingPainter painter = new CountingPainter();
		final AtomicReference<BadgeView> now = new AtomicReference<>();
		final BadgeOverlay overlay = new BadgeOverlay(now::get, painter, tooltips);
		final Canvas canvas = new Canvas();
		for (BadgeStyle style : new BadgeStyle[] {BadgeStyle.ICON, BadgeStyle.SHAPE_ONLY})
		{
			now.set(new BadgeView(true, style, Level.BAD, Icon.WORLD, false, "World lag", "Not you",
				"World lag - not you", "Ticks 1,240 ms, ping 41 ms"));
			for (int i = 0; i < 3; i++)
			{
				assertNull(style + " is the infobox's", overlay.render(canvas.g));
				overlay.onMouseOver();
			}
			assertTrue(style + ": nothing is drawn", canvas.blank());
		}
		assertEquals("the painter was never asked", 0, painter.calls);
		verifyNoInteractions(tooltips);

		now.set(LAG);
		assertNotNull("a word style is drawn", overlay.render(canvas.g));
		assertEquals(1, painter.calls);
		overlay.onMouseOver();
		verify(tooltips, times(1)).add(any(Tooltip.class));
		canvas.dispose();
	}

	// ---------------------------------------------------------------- helpers

	/** The size of the picture the painter makes of a view. */
	private static Dimension sizeOf(BadgeView v)
	{
		final BufferedImage image = PAINTER.paint(v);
		return new Dimension(image.getWidth(), image.getHeight());
	}

	/** The text of the one Tooltip that a hover over this view adds. */
	private static String hoverText(BadgeView v)
	{
		final TooltipManager tooltips = mock(TooltipManager.class);
		final BadgeOverlay overlay = new BadgeOverlay(() -> v, PAINTER, tooltips);
		final Canvas canvas = new Canvas();
		overlay.render(canvas.g);
		canvas.dispose();
		overlay.onMouseOver();
		final ArgumentCaptor<Tooltip> added = ArgumentCaptor.forClass(Tooltip.class);
		verify(tooltips, times(1)).add(added.capture());
		return added.getValue().getText();
	}

	/** {@code frames} renders; answers the last one's Dimension. */
	private static Dimension render(BadgeOverlay overlay, Canvas canvas, int frames)
	{
		Dimension d = null;
		for (int i = 0; i < frames; i++)
		{
			d = overlay.render(canvas.g);
		}
		return d;
	}

	/** A clear 64 x 64 ARGB game screen to render on. */
	private static final class Canvas
	{
		final BufferedImage image = new BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB);
		final Graphics2D g = image.createGraphics();

		boolean blank()
		{
			for (int y = 0; y < image.getHeight(); y++)
			{
				for (int x = 0; x < image.getWidth(); x++)
				{
					if (image.getRGB(x, y) != 0)
					{
						return false;
					}
				}
			}
			return true;
		}

		void dispose()
		{
			g.dispose();
		}
	}
}
