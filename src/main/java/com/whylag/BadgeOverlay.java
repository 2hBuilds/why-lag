package com.whylag;

import com.whylag.core.BadgeView;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.function.Function;
import java.util.function.Supplier;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.tooltip.Tooltip;
import net.runelite.client.ui.overlay.tooltip.TooltipManager;
import net.runelite.client.util.ColorUtil;

/**
 * The game badge on the game screen (contract P2.5): one small overlay, top left by default, movable with Alt + drag
 * like every overlay (RuneLite remembers its place by this class's name). It measures nothing: it draws the
 * {@link BadgeView} that its supplier answers ({@code BadgeModel.view()}, one volatile read), and holds no
 * {@code Client}, reads no config and no ring, and calls no {@code LagSource}.
 *
 * <p>It draws only the two styles WITH words ({@code ICON_AND_WORDS}, {@code SHAPE_AND_WORDS}). A visible view of a
 * style without words ({@code ICON}, {@code SHAPE_ONLY}) is {@link BadgeInfoBox}'s (addendum D): here it is treated
 * as hidden, so the overlay and the infobox never both show.
 *
 * <p>{@link #render} reads the supplier ONCE. When the view is not the object it painted last (by identity), it
 * asks the painter for the new picture and makes its {@link Dimension} and its {@link Tooltip} once; then it draws
 * the kept picture at (0, 0) and answers the kept {@code Dimension}. While the view is the same object it builds no
 * string, no image, no {@code Dimension} and no {@code Tooltip} (T18). No picture (a hidden view): it answers null,
 * so RuneLite gives it no bounds, no hover and no drag.
 *
 * <p>{@link #onMouseOver}, which RuneLite calls in every frame the mouse is inside the badge, adds the kept tooltip:
 * {@code tip1} in the colour of line 1 for the view's level ({@link BadgePainter#lineOneColour}), then, when
 * {@code tip2} is not "", {@code </br>} and {@code tip2}. With {@code tip1} "" there is no tooltip. Both methods run
 * on the client thread, so the kept fields are plain.
 *
 * <p>Choice: it paints through a Function (the public constructor passes painter::paint), so a test counts paints.
 * <p>Choice: a null view is drawn as nothing, like HIDDEN; a paint that throws keeps nothing and is tried again.
 * <p>Choice: the painter is not asked for a view that {@link BadgeInfoBox#handles} owns; the rule lives in one place.
 */
public final class BadgeOverlay extends Overlay
{
	private final Supplier<BadgeView> view;
	private final Function<BadgeView, BufferedImage> painter;
	private final TooltipManager tooltips;

	/** The view painted last; null before the first render. */
	private BadgeView painted;
	/** Its picture, its size and its tooltip; null when it has none. */
	private BufferedImage image;
	private Dimension size;
	private Tooltip tooltip;

	public BadgeOverlay(Supplier<BadgeView> view, BadgePainter painter, TooltipManager tooltips)
	{
		this(view, painter::paint, tooltips);
	}

	BadgeOverlay(Supplier<BadgeView> view, Function<BadgeView, BufferedImage> painter, TooltipManager tooltips)
	{
		this.view = view;
		this.painter = painter;
		this.tooltips = tooltips;
		setPosition(OverlayPosition.TOP_LEFT);
	}

	@Override
	public Dimension render(Graphics2D graphics)
	{
		final BadgeView v = view.get();
		if (v != painted)
		{
			final BufferedImage next = v == null || BadgeInfoBox.handles(v) ? null : painter.apply(v);
			image = next;
			size = next == null ? null : new Dimension(next.getWidth(), next.getHeight());
			tooltip = next == null || v.tip1.isEmpty() ? null : new Tooltip(tooltipText(v));
			painted = v;
		}
		if (image == null)
		{
			return null;
		}
		graphics.drawImage(image, 0, 0, null);
		return size;
	}

	@Override
	public void onMouseOver()
	{
		if (tooltip != null)
		{
			tooltips.add(tooltip);
		}
	}

	/** {@code tip1} in line 1's colour, then {@code </br>} and {@code tip2} when that is not "". */
	static String tooltipText(BadgeView v)
	{
		final String first = ColorUtil.wrapWithColorTag(v.tip1, BadgePainter.lineOneColour(v.level));
		return v.tip2.isEmpty() ? first : first + "</br>" + v.tip2;
	}
}
