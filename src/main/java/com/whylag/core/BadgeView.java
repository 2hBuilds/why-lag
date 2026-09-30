package com.whylag.core;

/**
 * What the game badge shows, immutable (contract 3.8, P2): built by the badge's state machine on the sampler thread,
 * read by the overlay inside {@code render} on the client thread through one volatile read. The overlay paints it
 * and holds no rule of its own.
 *
 * <p>{@code visible} false: the overlay draws nothing and has no tooltip. A hidden view is always {@link #HIDDEN}
 * itself (contract P2.2): the state machine builds no other view with {@code visible} false, so a reader may compare
 * by identity. {@code style} is the setting as given in every VISIBLE view (HIDDEN's is ICON, whatever the setting);
 * the painter leaves out what the style does not show. {@code level} is the status shape and the icon's state;
 * {@code icon} the cause's picture ({@link Icon#NONE}: the shape alone); {@code dimmed} true once the lag is over,
 * while the hold of {@link Thresholds#BADGE_HOLD_S} runs. {@code line1} and {@code line2} are the answer's two lines
 * ({@link Answer}), {@code tip1} and {@code tip2} the tooltip's two lines; "" = none, and a null text is kept as "".
 *
 * <p>The state machine answers the SAME object while nothing the badge shows changed ({@link #sameAs}), so the
 * overlay repaints only a new view (contract 4, T18 and T19).
 */
public final class BadgeView
{
	/**
	 * Nothing to show: not visible, style ICON, level NO_DATA, icon NONE, not dimmed, every text "". The ONE hidden
	 * view: the state machine answers this object in every hidden case, whatever the style setting (contract P2.2).
	 */
	public static final BadgeView HIDDEN = new BadgeView(false, BadgeStyle.ICON, Level.NO_DATA, Icon.NONE, false,
		"", "", "", "");

	/** False: the overlay draws nothing and has no tooltip. */
	public final boolean visible;
	public final BadgeStyle style;
	/** The status shape, and the icon's state. */
	public final Level level;
	/** The cause's picture; {@link Icon#NONE} = the shape alone. */
	public final Icon icon;
	/** The lag is over; the hold of {@link Thresholds#BADGE_HOLD_S} runs. */
	public final boolean dimmed;
	/** The answer's two lines; "" = none. */
	public final String line1, line2;
	/** The tooltip's two lines; "" = none. */
	public final String tip1, tip2;

	public BadgeView(boolean visible, BadgeStyle style, Level level, Icon icon, boolean dimmed, String line1,
		String line2, String tip1, String tip2)
	{
		this.visible = visible;
		this.style = style;
		this.level = level;
		this.icon = icon;
		this.dimmed = dimmed;
		this.line1 = line1 == null ? "" : line1;
		this.line2 = line2 == null ? "" : line2;
		this.tip1 = tip1 == null ? "" : tip1;
		this.tip2 = tip2 == null ? "" : tip2;
	}

	/** True when {@code o} shows the same badge: every field is equal. */
	public boolean sameAs(BadgeView o)
	{
		return o != null
			&& visible == o.visible
			&& style == o.style
			&& level == o.level
			&& icon == o.icon
			&& dimmed == o.dimmed
			&& line1.equals(o.line1)
			&& line2.equals(o.line2)
			&& tip1.equals(o.tip1)
			&& tip2.equals(o.tip2);
	}
}
