package com.whylag.core;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

/**
 * The small enums and codes of contract 3.2: the three lanes, each with a tile; the operating systems and their
 * units (C1); the cap sources and the renderers' words; the confidence steps; one bit per trigger; the state codes;
 * the flag bits and the mask of the flags alone; the groups' lanes and labels; the ordinals that are stored; and
 * the game badge's enums - its two settings and its four pictures. {@link Cause} has its own test, and the stored
 * NAMES of the badge's settings are pinned in {@code WhyLagConfigTest}.
 */
public class EnumsTest
{
	@Test
	public void lanesAreThreeAndEachHasATile()
	{
		assertEquals(3, Lane.values().length);
		assertEquals(3, Lane.TILES);
		assertEquals(Lane.values().length, Lane.TILES);
		assertEquals(Arrays.asList(Lane.FRAME_RATE, Lane.TICKS, Lane.PING), Arrays.asList(Lane.values()));
		assertEquals("Frame rate", Lane.FRAME_RATE.label());
		assertEquals("Ticks", Lane.TICKS.label());
		assertEquals("Ping", Lane.PING.label());
	}

	@Test
	public void operatingSystems()
	{
		assertEquals(Os.WINDOWS, Os.of("Windows 11"));
		assertEquals(Os.WINDOWS, Os.of("Windows 10"));
		assertEquals(Os.MAC, Os.of("Mac OS X"));
		assertEquals("Darwin holds 'win' but is a Mac", Os.MAC, Os.of("Darwin"));
		assertEquals(Os.LINUX, Os.of("Linux"));
		assertEquals(Os.OTHER, Os.of("FreeBSD"));
		assertEquals(Os.OTHER, Os.of(null));
		assertTrue(Os.WINDOWS.countsBytes());
		assertFalse(Os.MAC.countsBytes());
		assertFalse(Os.LINUX.countsBytes());
		assertFalse(Os.OTHER.countsBytes());
		assertEquals(Thresholds.RESENT_MIN_BYTES, Os.WINDOWS.resentMinSent());
		assertEquals(Thresholds.RESENT_MIN_UNITS, Os.LINUX.resentMinSent());
		assertEquals(Thresholds.CLICK_SENT_BYTES, Os.WINDOWS.clickSent());
		assertEquals(Thresholds.CLICK_SENT_UNITS, Os.MAC.clickSent());
	}

	@Test
	public void capSources()
	{
		assertFalse(CapSource.NONE.selfSet());
		assertFalse(CapSource.CLIENT_50.selfSet());
		assertFalse(CapSource.CLIENT_50.waits());
		for (CapSource c : new CapSource[] {CapSource.FPS_CONTROL, CapSource.FPS_CONTROL_UNFOCUSED,
			CapSource.GPU_TARGET, CapSource.GPU_VSYNC, CapSource.HD_TARGET, CapSource.HD_VSYNC})
		{
			assertTrue(c + " is set by the player", c.selfSet());
			assertTrue(c + " paces by waiting", c.waits());
		}
		assertEquals("the client", CapSource.CLIENT_50.label());
		assertEquals("FPS Control (unfocused)", CapSource.FPS_CONTROL_UNFOCUSED.label());
		assertEquals("117 HD: V-Sync", CapSource.HD_VSYNC.label());
		assertEquals("117 HD", Renderer.HD.label());
		assertEquals("unknown", Renderer.UNKNOWN.label());
	}

	@Test
	public void confidenceSteps()
	{
		assertEquals(Confidence.LIKELY, Confidence.SURE.lower());
		assertEquals(Confidence.HINT, Confidence.LIKELY.lower());
		assertEquals(Confidence.CANT_TELL, Confidence.HINT.lower());
		assertEquals("Can't tell stays", Confidence.CANT_TELL, Confidence.CANT_TELL.lower());
		assertEquals(Confidence.HINT, Confidence.weaker(Confidence.SURE, Confidence.HINT));
		assertEquals(Confidence.HINT, Confidence.weaker(Confidence.HINT, Confidence.LIKELY));
		assertSame(Confidence.SURE, Confidence.weaker(Confidence.SURE, Confidence.SURE));
		assertEquals("Sure", Confidence.SURE.word());
		assertEquals("Likely", Confidence.LIKELY.word());
		assertEquals("Hint", Confidence.HINT.word());
		assertEquals("Can't tell", Confidence.CANT_TELL.word());
	}

	@Test
	public void triggerBits()
	{
		final Set<Integer> bits = new HashSet<>();
		int all = 0;
		for (Trigger t : Trigger.values())
		{
			assertEquals(1 << t.ordinal(), t.bit());
			assertTrue(bits.add(t.bit()));
			all |= t.bit();
		}
		assertEquals("seven triggers, one bit each", 127, all);
	}

	@Test
	public void stateCodes()
	{
		assertEquals(0, State.OTHER);
		assertEquals(1, State.LOGIN_SCREEN);
		assertEquals(2, State.LOGGING_IN);
		assertEquals(3, State.LOADING);
		assertEquals(4, State.LOGGED_IN);
		assertEquals(5, State.CONNECTION_LOST);
		assertEquals(6, State.HOPPING);
		assertTrue(State.inGame(State.LOGGED_IN));
		assertTrue(State.inGame(State.LOADING));
		assertFalse(State.inGame(State.OTHER));
		assertFalse(State.inGame(State.LOGIN_SCREEN));
		assertFalse(State.inGame(State.LOGGING_IN));
		assertFalse(State.inGame(State.HOPPING));
		assertFalse(State.inGame(State.CONNECTION_LOST));
	}

	@Test
	public void flagBits()
	{
		assertEquals(1, Flags.FOCUSED);
		assertEquals(2, Flags.LOADING);
		assertEquals(4, Flags.HOP);
		assertEquals(8, Flags.LOGIN_MASK);
		assertEquals(16, Flags.NOT_LOGGED_IN);
		assertEquals(32, Flags.NO_FRAMES);
		assertEquals(64, Flags.DISCONNECT);
		assertTrue(Flags.has(Flags.FOCUSED | Flags.HOP, Flags.HOP));
		assertFalse(Flags.has(Flags.FOCUSED, Flags.HOP));
		final int every = Flags.FOCUSED | Flags.LOADING | Flags.HOP | Flags.LOGIN_MASK | Flags.NOT_LOGGED_IN
			| Flags.NO_FRAMES | Flags.DISCONNECT;
		assertEquals("seven bits, one byte, never negative", 127, every);
	}

	/** The mask every lot can read: the four flags that mask a second by themselves, and no other. */
	@Test
	public void maskedIsTheFourFlags()
	{
		assertEquals(30, Flags.MASKED);
		assertEquals(Flags.LOADING | Flags.HOP | Flags.LOGIN_MASK | Flags.NOT_LOGGED_IN, Flags.MASKED);
		for (int bit : new int[] {Flags.LOADING, Flags.HOP, Flags.LOGIN_MASK, Flags.NOT_LOGGED_IN})
		{
			assertTrue("flag " + bit + " masks its second", Flags.masked(bit));
			assertTrue("flag " + bit + " masks beside the focus", Flags.masked(bit | Flags.FOCUSED));
		}
		for (int bit : new int[] {Flags.FOCUSED, Flags.NO_FRAMES, Flags.DISCONNECT})
		{
			assertFalse("flag " + bit + " alone masks nothing", Flags.masked(bit));
		}
		assertFalse("a steady second", Flags.masked(0));
		assertFalse(Flags.masked(Flags.FOCUSED | Flags.NO_FRAMES | Flags.DISCONNECT));
		assertTrue("every flag", Flags.masked(127));
	}

	/** The badge's two settings: the names the settings list shows, and what each style draws (contract P2.3). */
	@Test
	public void badgeStylesAndWhenSmooth()
	{
		assertEquals(Arrays.asList(BadgeStyle.ICON, BadgeStyle.ICON_AND_WORDS, BadgeStyle.SHAPE_AND_WORDS,
			BadgeStyle.SHAPE_ONLY), Arrays.asList(BadgeStyle.values()));
		assertEquals("Icon", BadgeStyle.ICON.toString());
		assertEquals("Icon and words", BadgeStyle.ICON_AND_WORDS.toString());
		assertEquals("Shape and words", BadgeStyle.SHAPE_AND_WORDS.toString());
		assertEquals("Shape only", BadgeStyle.SHAPE_ONLY.toString());

		assertTrue(BadgeStyle.ICON.icon());
		assertFalse(BadgeStyle.ICON.words());
		assertTrue(BadgeStyle.ICON_AND_WORDS.icon());
		assertTrue(BadgeStyle.ICON_AND_WORDS.words());
		assertFalse("never an icon", BadgeStyle.SHAPE_AND_WORDS.icon());
		assertTrue(BadgeStyle.SHAPE_AND_WORDS.words());
		assertFalse(BadgeStyle.SHAPE_ONLY.icon());
		assertFalse(BadgeStyle.SHAPE_ONLY.words());

		assertEquals(Arrays.asList(WhenSmooth.SHOW, WhenSmooth.HIDE), Arrays.asList(WhenSmooth.values()));
		assertEquals("Show", WhenSmooth.SHOW.toString());
		assertEquals("Hide", WhenSmooth.HIDE.toString());
	}

	/** The badge's four pictures, and each one's word in the icon files' names; NONE has no file. */
	@Test
	public void icons()
	{
		assertEquals(Arrays.asList(Icon.NONE, Icon.WORLD, Icon.LINE, Icon.PC, Icon.UNKNOWN),
			Arrays.asList(Icon.values()));
		assertEquals("", Icon.NONE.file());
		assertEquals("world", Icon.WORLD.file());
		assertEquals("line", Icon.LINE.file());
		assertEquals("pc", Icon.PC.file());
		assertEquals("unknown", Icon.UNKNOWN.file());
	}

	@Test
	public void groupsAndLanes()
	{
		assertEquals(Lane.PING, Group.CONNECTION.lane());
		assertEquals(Lane.FRAME_RATE, Group.FRAME_RATE.lane());
		assertEquals(Lane.TICKS, Group.WORLD.lane());
		assertNull(Group.UNSURE.lane());
		assertNull(Group.NONE.lane());
		assertEquals("Conn", Group.CONNECTION.shortLabel());
		assertEquals("Frame", Group.FRAME_RATE.shortLabel());
		assertEquals("World", Group.WORLD.shortLabel());
		assertEquals("?", Group.UNSURE.shortLabel());
		assertEquals("Connection", Group.CONNECTION.label());
		assertEquals("Not sure", Group.UNSURE.label());
	}

	@Test
	public void storedOrdinalsKeepTheirPlaces()
	{
		// Levels are stored per strip column, groups index the session counts, NoData is a ring column, and the
		// lanes index the snapshot's strips.
		assertEquals(0, Level.OK.ordinal());
		assertEquals(1, Level.WARN.ordinal());
		assertEquals(2, Level.BAD.ordinal());
		assertEquals(3, Level.NO_DATA.ordinal());
		assertEquals(0, Group.CONNECTION.ordinal());
		assertEquals(1, Group.FRAME_RATE.ordinal());
		assertEquals(2, Group.WORLD.ordinal());
		assertEquals(3, Group.UNSURE.ordinal());
		assertEquals(4, Group.NONE.ordinal());
		assertEquals(0, NoData.NONE.ordinal());
		assertEquals(9, NoData.values().length);
		assertEquals(3, Lane.values().length);
		assertEquals(2, Lane.PING.ordinal());
	}
}
