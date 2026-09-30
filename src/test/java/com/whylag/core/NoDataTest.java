package com.whylag.core;

import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * The "no data" words (contract 3.2, 5.2): every reason is at most 17 characters. The pixel check against the
 * real font is the panel's ({@code PanelWidthTest}).
 */
public class NoDataTest
{
	@Test
	public void everyReasonIsAtMostSeventeenCharacters()
	{
		for (NoData d : NoData.values())
		{
			assertTrue(d + " is " + d.reason().length() + " characters: \"" + d.reason() + "\"",
				d.reason().length() <= 17);
		}
	}

	@Test
	public void onlyNoneIsSilent()
	{
		assertEquals("", NoData.NONE.reason());
		for (NoData d : NoData.values())
		{
			if (d != NoData.NONE)
			{
				assertFalse(d + " needs words", d.reason().isEmpty());
			}
		}
	}

	@Test
	public void theWordsOfTheTiles()
	{
		assertEquals("Still measuring", NoData.WARMING_UP.reason());
		assertEquals("Not logged in", NoData.NOT_LOGGED_IN.reason());
		assertEquals("Not connected", NoData.NOT_CONNECTED.reason());
		assertEquals("Not on this PC", NoData.UNSUPPORTED.reason());
		assertEquals("Could not read it", NoData.ERROR.reason());
		assertEquals("Nothing sent", NoData.STALE.reason());
		assertEquals("No frames drawn", NoData.NO_FRAMES.reason());
		assertEquals("No ticks yet", NoData.NO_TICKS.reason());
	}
}
