package com.whylag.core;

import org.junit.Test;
import static org.junit.Assert.assertEquals;

/**
 * The usual (contract 3.5): the running median, -1 under thirty samples, reset, and clamping. The session that
 * holds the usuals is {@code SessionTest}'s.
 */
public class UsualTest
{
	@Test
	public void median()
	{
		final Usual u = new Usual(2000);
		for (int v = 31; v >= 1; v--)
		{
			u.add(v);
		}
		assertEquals(31, u.count());
		assertEquals("the middle of 1 .. 31", 16, u.median());
		final Usual even = new Usual(2000);
		for (int v = 1; v <= 30; v++)
		{
			even.add(v);
		}
		assertEquals("the lower middle of an even count", 15, even.median());
		final Usual spiky = new Usual(2000);
		for (int i = 0; i < 29; i++)
		{
			spiky.add(40);
		}
		spiky.add(1900);
		spiky.add(1900);
		assertEquals("two spikes do not move a median", 40, spiky.median());
	}

	@Test
	public void noMedianUnderThirtySamples()
	{
		final Usual u = new Usual(2000);
		for (int i = 0; i < Thresholds.USUAL_MIN_SAMPLES - 1; i++)
		{
			u.add(40);
		}
		assertEquals(-1, u.median());
		u.add(40);
		assertEquals(40, u.median());
	}

	@Test
	public void reset()
	{
		final Usual u = new Usual(2000);
		for (int i = 0; i < 50; i++)
		{
			u.add(40);
		}
		u.reset();
		assertEquals(0, u.count());
		assertEquals(-1, u.median());
		for (int i = 0; i < 30; i++)
		{
			u.add(90);
		}
		assertEquals("nothing from before the reset remains", 90, u.median());
	}

	@Test
	public void samplesAreClamped()
	{
		final Usual u = new Usual(1000);
		for (int i = 0; i < 30; i++)
		{
			u.add(5000);
		}
		assertEquals(1000, u.median());
		u.reset();
		for (int i = 0; i < 30; i++)
		{
			u.add(-8);
		}
		assertEquals(0, u.median());
	}
}
