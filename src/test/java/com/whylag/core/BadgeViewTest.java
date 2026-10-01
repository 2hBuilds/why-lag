package com.whylag.core;

import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

/**
 * What the game badge shows (contract 3.8): the hidden view, null texts kept as "", and {@code sameAs}, which the
 * badge's state machine uses to answer the SAME object while nothing changed (T19) - so every one of the nine fields
 * must count, and a field added later must be counted too.
 */
public class BadgeViewTest
{
	@Test
	public void hiddenIsNotVisible()
	{
		final BadgeView h = BadgeView.HIDDEN;
		assertFalse(h.visible);
		assertSame(BadgeStyle.ICON, h.style);
		assertSame(Level.NO_DATA, h.level);
		assertSame(Icon.NONE, h.icon);
		assertFalse(h.dimmed);
		assertEquals("", h.line1);
		assertEquals("", h.line2);
		assertEquals("", h.tip1);
		assertEquals("", h.tip2);
		assertTrue(h.sameAs(new BadgeView(false, BadgeStyle.ICON, Level.NO_DATA, Icon.NONE, false, "", "", "", "")));
	}

	@Test
	public void nullTextsAreEmpty()
	{
		final BadgeView v = new BadgeView(true, BadgeStyle.SHAPE_ONLY, Level.OK, Icon.NONE, false, null, null, null,
			null);
		assertEquals("", v.line1);
		assertEquals("", v.line2);
		assertEquals("", v.tip1);
		assertEquals("", v.tip2);
		assertTrue("a null text and \"\" are the same view",
			v.sameAs(new BadgeView(true, BadgeStyle.SHAPE_ONLY, Level.OK, Icon.NONE, false, "", "", "", "")));
	}

	/** Every field counts: the same values are the same view, and a change in any ONE field is another view. */
	@Test
	public void sameAsComparesEveryField()
	{
		final BadgeView lag = lag();
		assertTrue(lag.sameAs(lag));
		assertTrue("equal fields, another object", lag.sameAs(lag()));
		assertFalse(lag.sameAs(null));

		assertFalse("visible", lag.sameAs(new BadgeView(false, BadgeStyle.ICON, Level.BAD, Icon.WORLD, false,
			"World lag", "Not you", "World lag - not you", "Ticks 1,240 ms, ping 41 ms")));
		assertFalse("style", lag.sameAs(new BadgeView(true, BadgeStyle.ICON_AND_WORDS, Level.BAD, Icon.WORLD, false,
			"World lag", "Not you", "World lag - not you", "Ticks 1,240 ms, ping 41 ms")));
		assertFalse("level", lag.sameAs(new BadgeView(true, BadgeStyle.ICON, Level.WARN, Icon.WORLD, false,
			"World lag", "Not you", "World lag - not you", "Ticks 1,240 ms, ping 41 ms")));
		assertFalse("icon", lag.sameAs(new BadgeView(true, BadgeStyle.ICON, Level.BAD, Icon.UNKNOWN, false,
			"World lag", "Not you", "World lag - not you", "Ticks 1,240 ms, ping 41 ms")));
		assertFalse("dimmed", lag.sameAs(new BadgeView(true, BadgeStyle.ICON, Level.BAD, Icon.WORLD, true,
			"World lag", "Not you", "World lag - not you", "Ticks 1,240 ms, ping 41 ms")));
		assertFalse("line 1", lag.sameAs(new BadgeView(true, BadgeStyle.ICON, Level.BAD, Icon.WORLD, false,
			"Lag", "Not you", "World lag - not you", "Ticks 1,240 ms, ping 41 ms")));
		assertFalse("line 2", lag.sameAs(new BadgeView(true, BadgeStyle.ICON, Level.BAD, Icon.WORLD, false,
			"World lag", "", "World lag - not you", "Ticks 1,240 ms, ping 41 ms")));
		assertFalse("tip 1", lag.sameAs(new BadgeView(true, BadgeStyle.ICON, Level.BAD, Icon.WORLD, false,
			"World lag", "Not you", "Lag - can't tell why", "Ticks 1,240 ms, ping 41 ms")));
		assertFalse("tip 2", lag.sameAs(new BadgeView(true, BadgeStyle.ICON, Level.BAD, Icon.WORLD, false,
			"World lag", "Not you", "World lag - not you", "Ticks 1,300 ms, ping 41 ms")));
	}

	/** The picture's lag while it happens: the globe with its red square, two lines and the tooltip's two lines. */
	private static BadgeView lag()
	{
		return new BadgeView(true, BadgeStyle.ICON, Level.BAD, Icon.WORLD, false, "World lag", "Not you",
			"World lag - not you", "Ticks 1,240 ms, ping 41 ms");
	}
}
