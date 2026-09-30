package com.whylag;

import com.whylag.core.BadgeView;
import com.whylag.core.Level;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.util.function.Function;
import java.util.function.Supplier;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.ui.overlay.infobox.InfoBox;
import net.runelite.client.ui.overlay.infobox.InfoBoxManager;

/**
 * The game badge of the two styles WITHOUT words ({@code ICON}, {@code SHAPE_ONLY}) as a real RuneLite infobox
 * (addendum D of the contract): it sits in the infobox row with everyone else's, so no other plugin's infobox is
 * drawn over it, and the player moves, flips and detaches it with RuneLite's own menu entries (RuneLite adds them).
 * The two styles with words stay {@link BadgeOverlay}. Like the overlay it measures nothing: it shows the
 * {@link BadgeView} that its supplier answers, and holds no {@code Client}, reads no config and calls no
 * {@code LagSource}.
 *
 * <p>RuneLite calls {@link #render} once in every frame, on the client thread, BEFORE it reads the picture, so the
 * lazy repaint lives there. {@code render} reads the supplier ONCE. When the view is not the object it painted last
 * (by identity) it asks the painter for {@link BadgePainter#bare} (no box: RuneLite draws its own background and
 * centres a picture smaller than the infobox), hands it to {@link #setImage} and to
 * {@link InfoBoxManager#updateInfoBoxImage} (which makes the scaled picture that RuneLite draws), and keeps the
 * tooltip: {@code tip1} in the colour of line 1, {@code </br>} and {@code tip2} ({@link BadgeOverlay#tooltipText}),
 * "" when {@code tip1} is "". While the view is the same object it builds no picture, no string and makes no manager
 * call (T18). It answers true only while it has a picture, so a hidden view, a null view and a view of a word style
 * take no room in the row.
 *
 * <p>There are no words in an infobox of ours: {@link #getText} answers "". {@link #getTextColor} answers line 1's
 * colour for the last view's level, which RuneLite uses for nothing while the text is "". It has no priority
 * ({@code InfoBoxPriority.NONE}) and no menu entries of ours.
 *
 * <p>Choice: it paints through a Function (the public constructor passes {@code painter::bare}), so a test counts
 * paints, as {@link BadgeOverlay} does.
 * <p>Choice: the tooltip is kept in the inherited {@code tooltip} field ({@link #setTooltip}), so RuneLite's own
 * {@code getTooltip} answers it.
 * <p>Choice: a paint that throws keeps nothing and is tried again; RuneLite catches it, logs it once and skips the
 * frame.
 * <p>Choice: a null style reads as ICON, which has no words, as the painter reads it.
 */
public final class BadgeInfoBox extends InfoBox
{
	private final Supplier<BadgeView> view;
	private final Function<BadgeView, BufferedImage> painter;
	private final InfoBoxManager manager;

	/** The view painted last; null before the first render. */
	private BadgeView painted;
	/** Its level, for the colour of {@link #getTextColor}; null before the first render. */
	private Level level;

	public BadgeInfoBox(Plugin plugin, Supplier<BadgeView> view, BadgePainter painter, InfoBoxManager manager)
	{
		this(plugin, view, painter::bare, manager);
	}

	BadgeInfoBox(Plugin plugin, Supplier<BadgeView> view, Function<BadgeView, BufferedImage> painter,
		InfoBoxManager manager)
	{
		super(null, plugin);
		this.view = view;
		this.painter = painter;
		this.manager = manager;
	}

	/**
	 * True when this view is the infobox's: visible, and of a style without words. A hidden view, and a view of a
	 * word style (the overlay's), are not. {@link BadgeOverlay} asks the same question, so exactly one of the two
	 * shows a visible view.
	 */
	static boolean handles(BadgeView v)
	{
		return v != null && v.visible && (v.style == null || !v.style.words());
	}

	@Override
	public boolean render()
	{
		final BadgeView v = view.get();
		if (v != painted)
		{
			final BufferedImage next = handles(v) ? painter.apply(v) : null;
			setImage(next);
			if (next != null)
			{
				manager.updateInfoBoxImage(this);
			}
			setTooltip(next == null || v.tip1.isEmpty() ? "" : BadgeOverlay.tooltipText(v));
			level = v == null ? null : v.level;
			painted = v;
		}
		return getImage() != null;
	}

	/** "": the styles of this infobox show no words. */
	@Override
	public String getText()
	{
		return "";
	}

	/** Line 1's colour for the last view's level ({@code NO_DATA}'s before the first view). */
	@Override
	public Color getTextColor()
	{
		return BadgePainter.lineOneColour(level);
	}
}
