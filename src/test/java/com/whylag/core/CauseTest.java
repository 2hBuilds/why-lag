package com.whylag.core;

import java.util.EnumSet;
import java.util.HashSet;
import java.util.Set;
import java.util.TreeSet;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * The causes (contract 3.2): ids are unique, exactly the sixteen named there are in wave one (F2 is one), and the
 * groups and short names of wave one's causes. The other small types of 3.2 are {@code EnumsTest}'s.
 */
public class CauseTest
{
	@Test
	public void idsAreUnique()
	{
		final Set<String> ids = new HashSet<>();
		for (Cause c : Cause.values())
		{
			assertTrue("id " + c.id() + " twice", ids.add(c.id()));
		}
		assertEquals(29, ids.size());
	}

	@Test
	public void exactlyTheSixteenOfWaveOne()
	{
		final Set<Cause> expected = EnumSet.of(Cause.DISCONNECT, Cause.GC_PAUSE, Cause.MAP_LOAD, Cause.UPLOAD_LOSS,
			Cause.PING_JUMPY, Cause.SLOW_WORLD, Cause.CLIENT_BUSY, Cause.CLIENT_WAITING, Cause.DELIVERY_GAP,
			Cause.FRAME_CAP, Cause.PING_HIGH, Cause.SLOW_DRAWING, Cause.HEAP_CAP_LOW, Cause.ALL_CLEAR, Cause.NOT_SURE,
			Cause.WARMING_UP);
		final Set<Cause> actual = EnumSet.noneOf(Cause.class);
		for (Cause c : Cause.values())
		{
			if (c.inWaveOne())
			{
				actual.add(c);
			}
		}
		assertEquals(expected, actual);
		assertEquals(16, actual.size());
		assertTrue("F2 is one of them", Cause.SLOW_DRAWING.inWaveOne());
	}

	@Test
	public void theFifteenRuleIdsAreInWaveOne()
	{
		final Set<String> ids = new TreeSet<>();
		for (Cause c : Cause.values())
		{
			if (c.inWaveOne())
			{
				ids.add(c.id());
			}
		}
		assertEquals(new TreeSet<>(java.util.Arrays.asList("D1", "G1", "S1", "N3", "N2", "W1", "S3", "S4", "N6", "F1",
			"N1", "F2", "G2", "V2", "X", "-")), ids);
	}

	@Test
	public void groupsAndShortNamesOfWaveOne()
	{
		assertEquals(Group.UNSURE, Cause.DELIVERY_GAP.group());
		assertEquals(Group.UNSURE, Cause.NOT_SURE.group());
		assertEquals(Group.WORLD, Cause.SLOW_WORLD.group());
		assertEquals(Group.CONNECTION, Cause.DISCONNECT.group());
		assertEquals(Group.MEMORY, Cause.GC_PAUSE.group());
		assertEquals(Group.FRAME_RATE, Cause.CLIENT_BUSY.group());
		assertEquals(Group.NONE, Cause.ALL_CLEAR.group());
		assertEquals("memory clean-up", Cause.GC_PAUSE.shortName());
		assertEquals("lost packets", Cause.UPLOAD_LOSS.shortName());
		assertEquals("", Cause.WARMING_UP.shortName());
	}
}
