package com.whylag.core;

import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * The ten checks (1.0.1, lot B; ten since 1.0.0, the Hub's rule): each one passes on the healthy facts with the
 * detail it names, fails on its own failing states with the plain words it then says and nothing else failing, and the verdict is the first failure
 * in order; the notes the plan fixes (nothing yet, not offered, hidden by setting) pass. The failing states are
 * {@link CheckFixtures#failing()}, shared with the panel tests.
 */
public class ChecksTest
{
	private static final String[] NAMES = {"Logged in", "Frames arrive", "Ticks arrive", "Ping readable",
		"The sampler runs", "Settings read", "The badge is up", "Client fits", "Clock sane", "Not stuck measuring"};

	private static List<CheckResult> run(Consumer<CheckFacts.Builder> setUp)
	{
		final CheckFacts.Builder b = CheckFixtures.healthy();
		setUp.accept(b);
		return Checks.run(b.build());
	}

	private static CheckResult one(int check, Consumer<CheckFacts.Builder> setUp)
	{
		return run(setUp).get(check - 1);
	}

	/** Ten results, C1 to C10 in the proposal's order and words; each passes on the healthy facts. */
	@Test
	public void healthyFactsPassAllTenInOrder()
	{
		final List<CheckResult> results = Checks.run(CheckFixtures.healthy().build());
		assertEquals(10, results.size());
		final String[] details = {"world 416", "50 fps, 100 frames in 2 s",
			"16 in 10 s, last 350 ms ago, mean gap 601 ms", "41 ms, 1 s old", "last step 340 ms ago, work 120 us",
			"renderer GPU, cap known", "registered, Show is on", "client 1.13.0, Java 17.0.18, Windows 11",
			"never went back, zone UTC", "card: Smooth"};
		for (int i = 0; i < 10; i++)
		{
			final CheckResult r = results.get(i);
			assertEquals("C" + (i + 1), r.id);
			assertEquals(NAMES[i], r.name);
			assertTrue(r.id + " " + r.detail, r.pass);
			assertEquals(r.id, details[i], r.detail);
		}
	}

	/** Every failing state fails its own check with its own words, and no other check. */
	@Test
	public void eachCheckFailsWithItsOwnWordsAndNoOther()
	{
		final boolean[] seen = new boolean[10];
		for (CheckFixtures.Failing f : CheckFixtures.failing())
		{
			final List<CheckResult> results = Checks.run(f.facts());
			seen[f.check - 1] = true;
			for (int i = 0; i < 10; i++)
			{
				final CheckResult r = results.get(i);
				if (i == f.check - 1)
				{
					assertFalse(f + " must fail: " + r.line(), r.pass);
					assertEquals(f.toString(), f.detail, r.detail);
				}
				else
				{
					assertTrue(f + " must leave " + r.id + " passing: " + r.line(), r.pass);
				}
			}
		}
		for (int i = 0; i < 10; i++)
		{
			assertTrue("C" + (i + 1) + " has a failing state", seen[i]);
		}
	}

	/** The plan's notes: nothing yet, hidden by setting - each a PASS. */
	@Test
	public void theNotesPass()
	{
		final CheckResult hidden = one(7, b ->
		{
			b.badgeShow = false;
			b.badgeRegistered = false;
		});
		assertTrue(hidden.line(), hidden.pass);
		assertEquals("hidden by setting", hidden.detail);

		final CheckResult noStep = one(5, b ->
		{
			b.lastStepAgoMs = -1;
			b.stepCostUs = -1;
		});
		assertTrue(noStep.line(), noStep.pass);
		assertEquals("no step yet, the plugin has just started", noStep.detail);
		assertEquals("last step 340 ms ago", one(5, b -> b.stepCostUs = -1).detail);

		assertEquals("client dev, Java 17.0.18, Windows 11", one(8, b -> b.clientVersion = "dev").detail);
		assertEquals("client 1.13.1-SNAPSHOT, Java 17.0.18, Windows 11",
			one(8, b -> b.clientVersion = "1.13.1-SNAPSHOT").detail);
		assertEquals("client unknown, Java 17.0.18, Windows 11", one(8, b -> b.clientVersion = "").detail);
		assertTrue(one(8, b -> b.clientVersion = "2.0.0").pass);
		assertTrue("Java 11 is the floor", one(8, b -> b.javaVersion = "11.0.2").pass);
		assertEquals("mean gap left out when nothing could be averaged", "16 in 10 s, last 350 ms ago",
			one(3, b -> b.meanGapMs = -1).detail);
	}

	/** Nobody logged in: C1 fails and is the verdict; the three checks that need a player say so and pass. */
	@Test
	public void notLoggedInFailsOnlyTheFirst()
	{
		final List<CheckResult> r = run(b ->
		{
			b.loggedIn = false;
			b.world = 0;
			b.lastTickAgoMs = -1;
			b.pingState = CheckFacts.Ping.NONE;
			b.rttMs = -1;
			b.cardState = Answer.MEASURING;
		});
		for (int i = 0; i < 10; i++)
		{
			assertEquals(r.get(i).line(), i != 0, r.get(i).pass);
		}
		for (int c : new int[] {3, 4, 10})
		{
			assertEquals("not logged in, not checked", r.get(c - 1).detail);
		}
		assertEquals("Not logged in: nothing is measured yet", Checks.verdict(r));
	}

	/** The edges of each number: where a check turns from pass to fail. */
	@Test
	public void theEdges()
	{
		assertTrue(one(2, b -> b.fps = 1).pass);
		assertTrue(one(2, b -> b.fps = 1000).pass);
		assertFalse(one(2, b -> b.fps = 1001).pass);
		assertTrue(one(3, b -> b.lastTickAgoMs = 1199).pass);
		assertFalse(one(3, b -> b.lastTickAgoMs = 1200).pass);
		assertTrue(one(3, b -> b.ticksLast10s = 5).pass);
		assertFalse(one(3, b -> b.ticksLast10s = 4).pass);
		assertTrue(one(3, b -> b.meanGapMs = 500).pass);
		assertTrue(one(3, b -> b.meanGapMs = 700).pass);
		assertFalse(one(3, b -> b.meanGapMs = 499).pass);
		assertFalse(one(3, b -> b.meanGapMs = 701).pass);
		assertTrue("a ping is fresh at 5 s, as the rings count it", one(4, b -> b.pingAgeS = 5).pass);
		assertFalse(one(4, b -> b.pingAgeS = 6).pass);
		assertTrue(one(4, b -> b.pingAgeS = -1).pass);
		assertEquals("41 ms", one(4, b -> b.pingAgeS = -1).detail);
		assertTrue(one(5, b -> b.lastStepAgoMs = 2000).pass);
		assertFalse(one(5, b -> b.lastStepAgoMs = 2001).pass);
		assertTrue(one(5, b -> b.stepCostUs = 1999).pass);
		assertFalse(one(5, b -> b.stepCostUs = 2000).pass);
		assertTrue("a minute of measuring is allowed", one(10, b ->
		{
			b.loggedInS = 60;
			b.cardState = Answer.MEASURING;
		}).pass);
		assertFalse(one(10, b ->
		{
			b.loggedInS = 61;
			b.cardState = Answer.MEASURING;
		}).pass);
		assertTrue("any other card state is out of measuring", one(10, b ->
		{
			b.loggedInS = 600;
			b.cardState = Answer.WAITING;
		}).pass);
	}

	/** The verdict is the detail of the FIRST failure in C1..C10 order with its first letter raised. */
	@Test
	public void theVerdictIsTheFirstFailureInOrder()
	{
		final List<CheckResult> two = run(b ->
		{
			b.clockJumpedBackS = 5;
			b.clockJumpAtWallMs = CheckFixtures.JUMP_AT;
			b.pingState = CheckFacts.Ping.UNSUPPORTED;
		});
		assertEquals("C4 comes before C9", "Ping cannot be read on this PC", Checks.verdict(two));

		final List<CheckResult> late = run(b ->
		{
			b.lastTickAgoMs = 4000;
			b.framesLast2s = 0;
		});
		assertEquals("No frames counted for 2 s", Checks.verdict(late));

		for (CheckFixtures.Failing f : CheckFixtures.failing())
		{
			final String verdict = Checks.verdict(Checks.run(f.facts()));
			assertEquals(f.toString(), Character.toUpperCase(f.detail.charAt(0)) + f.detail.substring(1), verdict);
		}
	}

	@Test
	public void allPassingGivesTheMeasuredLine()
	{
		assertEquals("Everything is being measured.", Checks.ALL_GOOD);
		assertEquals("Everything is being measured.", Checks.verdict(Checks.run(CheckFixtures.healthy().build())));
		assertEquals("an empty list has no failure", Checks.ALL_GOOD, Checks.verdict(Arrays.asList()));
	}

	/** {@code tests run: 9 pass, 1 fail: C4 Ping readable}; several failures are comma separated. */
	@Test
	public void theNoteCountsAndNamesTheFailures()
	{
		assertEquals("tests run: 10 pass, 0 fail", Checks.summary(Checks.run(CheckFixtures.healthy().build())));
		assertEquals("tests run: 9 pass, 1 fail: C4 Ping readable",
			Checks.summary(run(b -> b.pingState = CheckFacts.Ping.UNSUPPORTED)));
		assertEquals("tests run: 8 pass, 2 fail: C2 Frames arrive, C9 Clock sane", Checks.summary(run(b ->
		{
			b.framesLast2s = 0;
			b.clockJumpedBackS = 3;
		})));
	}

	/** The report's two sections: the verdict line, a blank line, "Checks:", ten lines, a blank line. */
	@Test
	public void theSectionIsTheVerdictAndTenLines()
	{
		final String ok = Checks.section(Checks.run(CheckFixtures.healthy().build()));
		final String[] lines = ok.split("\n", -1);
		assertEquals("Verdict: Everything is being measured.", lines[0]);
		assertEquals("", lines[1]);
		assertEquals("Checks:", lines[2]);
		assertEquals("PASS  C1 Logged in  world 416", lines[3]);
		assertEquals("PASS  C4 Ping readable  41 ms, 1 s old", lines[6]);
		assertEquals("PASS  C10 Not stuck measuring  card: Smooth", lines[12]);
		assertEquals("", lines[13]);
		assertEquals("ends with a new line after the blank one", "", lines[14]);
		assertEquals(15, lines.length);

		final String bad = Checks.section(run(b -> b.lastTickAgoMs = 4000));
		assertTrue(bad, bad.startsWith("Verdict: No ticks for 4 s\n\nChecks:\n"));
		assertTrue(bad, bad.contains("\nFAIL  C3 Ticks arrive  no ticks for 4 s\n"));
		assertTrue(bad, bad.endsWith("card: Smooth\n\n"));
	}

	@Test
	public void aResultPrintsItsLine()
	{
		assertEquals("PASS  C4 Ping readable  41 ms, 1 s old", new CheckResult("C4", "Ping readable", true,
			"41 ms, 1 s old").line());
		assertEquals("FAIL  C3 Ticks arrive  no ticks for 4 s", new CheckResult("C3", "Ticks arrive", false,
			"no ticks for 4 s").line());
		assertEquals("no detail, no trailing spaces", "PASS  C1 Logged in", new CheckResult("C1", "Logged in", true,
			null).line());
	}
}
