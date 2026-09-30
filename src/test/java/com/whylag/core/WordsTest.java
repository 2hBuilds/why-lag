package com.whylag.core;

import java.util.ArrayList;
import java.util.List;
import org.junit.Test;
import static com.whylag.core.VerdictCauseTest.USUAL;
import static com.whylag.core.VerdictCauseTest.event;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * The words of contract 6.4: every text fits with the largest values filled in, no filler is left open, plain
 * ASCII (so no arrow), no fix sends the player to the router outright, re-sends are printed as a share, and each
 * row of the fillers table prints the field it names.
 */
public class WordsTest
{
	private static final Session SESSION = Trace.steady(1).build();

	private static SettingsView settings(Renderer renderer, String antiAliasing, int drawDistance, int mapLoading)
	{
		return new SettingsView(renderer, false, false, 0, false, 0, false, "", 0, drawDistance, antiAliasing,
			mapLoading, 60, 768, MemorySource.MANAGEMENT, Os.WINDOWS, "");
	}

	private static SettingsView[] everySettings()
	{
		return new SettingsView[] {
			settings(Renderer.CPU, "", 0, 0),
			settings(Renderer.GPU, "MSAA_16", 90, 3),
			settings(Renderer.HD, "MSAA_16", 184, 5),
			settings(Renderer.GPU, "", 32_767, 32_767),
			settings(Renderer.HD, "AN_ANTI_ALIASING_NAME_FAR_TOO_LONG_TO_PRINT_ON_THE_CARD", 999, 999),
			settings(Renderer.UNKNOWN, "", 0, 0),
			VerdictCauseTest.runtimeMemory(),
		};
	}

	/** An evidence with every number far over its largest printed value. */
	private static Evidence largest()
	{
		final Evidence e = new Evidence();
		e.event = true;
		e.first = Trigger.FRAME_GAP;
		e.disconnect = true;
		e.disconnectSec = 0;
		e.lastTickSec = 0;
		e.frameGapMs = 32_767;
		e.frameLimitMs = 200;
		e.gcMs = 2_000_000;
		e.gcCoverPct = 100;
		e.gcOverlapMs = 2_000_000;
		e.loadMs = Integer.MAX_VALUE;
		e.loads = 32_767;
		e.busyPm = 1000;
		e.capFps = 32_767;
		e.capIntervalMs = 1;
		e.capSelfSet = true;
		e.capWaits = true;
		e.capLabel = CapSource.FPS_CONTROL_UNFOCUSED.label();
		e.fps = 32_767;
		e.lowFpsS = 32_767;
		e.lowFps = 32_767;
		e.capHeldS = 60;
		e.frameMedianMs = 1;
		e.rttKnown = true;
		e.rtt = 32_767;
		e.rttMin = 32_767;
		e.rttMax = 32_767;
		e.rttUsual = 2000;
		e.rttSpike = Evidence.YES;
		e.sent = Integer.MAX_VALUE;
		e.resent = Integer.MAX_VALUE;
		e.resentCounts = true;
		e.resentPm = 1000;
		e.ticks = 6000;
		e.tickLate = 6000;
		e.tickEarly = 6000;
		e.tickMedianMs = Integer.MAX_VALUE;
		e.tickWorstMs = Integer.MAX_VALUE;
		e.noTickMs = Integer.MAX_VALUE;
		e.tickOffMs = Integer.MAX_VALUE;
		e.tickWindowMedianMs = Integer.MAX_VALUE;
		e.ticksInWindow = 6000;
		e.heapUsedMb = 32_767;
		e.heapMaxMb = Integer.MAX_VALUE;
		e.durationS = 100_000;
		e.world = 32_767;
		e.beforeRtt = 32_767;
		e.beforeRttMax = 32_767;
		e.beforeRttSpike = Evidence.YES;
		e.beforeResentPm = 1000;
		return e;
	}

	/** The largest evidence in every variant that picks another sentence. */
	private static List<Evidence> variants()
	{
		final List<Evidence> out = new ArrayList<>();
		out.add(largest());
		// D1: bad re-sends, fine, no data.
		final Evidence resends = largest();
		resends.beforeRttSpike = Evidence.NO;
		out.add(resends);
		final Evidence fine = largest();
		fine.beforeRttSpike = Evidence.NO;
		fine.beforeResentPm = 0;
		out.add(fine);
		final Evidence blind = largest();
		blind.beforeRttSpike = Evidence.NO_DATA;
		blind.beforeResentPm = 0;
		blind.rttKnown = false;
		blind.rttSpike = Evidence.NO_DATA;
		out.add(blind);
		// G1 without its memory part, S3 without a clean-up part, N1 without a usual.
		final Evidence bare = largest();
		bare.heapMaxMb = -1;
		bare.gcOverlapMs = 0;
		bare.rttUsual = -1;
		out.add(bare);
		// A ping judged and clean: N6's longer sentence, "Frames and ping were fine.".
		final Evidence calm = largest();
		calm.rttSpike = Evidence.NO;
		out.add(calm);
		// No number at all: every filler at "no data".
		final Evidence none = new Evidence();
		none.event = true;
		out.add(none);
		for (Trigger first : Trigger.values())
		{
			final Evidence e = largest();
			e.first = first;
			out.add(e);
		}
		return out;
	}

	/** Every text the judge can make from these evidences, as {headline, proof, fix, ruledOut}. */
	private static List<String[]> everyText()
	{
		final List<String[]> out = new ArrayList<>();
		for (Evidence e : variants())
		{
			for (SettingsView s : everySettings())
			{
				for (Rule r : Rules.ALL)
				{
					out.add(new String[] {r.id, Words.headline(r, e, SESSION), Words.proof(r, e, s),
						Words.fix(r, e, s), Words.ruledOut(r.cause, e, s)});
				}
				out.add(new String[] {"X", Words.headline(Rules.X, e, SESSION), Words.cantTellProof(e, null, null),
					Words.fix(Rules.X, e, s), Words.ruledOut(Cause.NOT_SURE, e, s)});
				for (Rule a : Rules.ALL)
				{
					for (Rule b : Rules.ALL)
					{
						if (a.kind == Rule.Kind.EVENT && b.kind == Rule.Kind.EVENT && a.rank < b.rank)
						{
							out.add(new String[] {"X " + a.id + " " + b.id, Words.headline(Rules.X, e, SESSION),
								Words.cantTellProof(e, a.cause, b.cause), Words.fix(Rules.X, e, s), ""});
						}
					}
				}
			}
		}
		// V2's proof counts SECONDS since the newest event's end: under a minute in steps of ten, then whole minutes.
		for (long seconds : new long[] {-1, 0, 9, 10, 59, 60, 119, 120, 86_400, 9_999L * 60, 10_000L * 60,
			Long.MAX_VALUE})
		{
			out.add(new String[] {"V2", Words.headline(Rules.V2, null, SESSION), Words.allClearProof(seconds), "",
				""});
		}
		for (int left : new int[] {-5, 0, 1, 9, 10, 11, 59, 60, 61, 100_000})
		{
			out.add(new String[] {"measuring", Answer.HEAD_MEASURING, Words.measuringProof(left), "", ""});
		}
		out.add(new String[] {"not logged in", Answer.HEAD_NOT_LOGGED_IN, Words.notLoggedInProof(), "", ""});
		out.add(new String[] {"waiting", Answer.HEAD_WAITING, Words.waitingProof(), "", ""});
		return out;
	}

	@Test
	public void everyTextFitsWithTheLargestValues()
	{
		final List<String[]> texts = everyText();
		assertTrue("the texts of every rule, settings and variant: " + texts.size(), texts.size() > 1000);
		int longestHeadline = 0;
		int longestProof = 0;
		int longestFix = 0;
		for (String[] t : texts)
		{
			assertTrue(t[0] + " headline: " + t[1], t[1].length() <= 32);
			assertTrue(t[0] + " proof: " + t[2], t[2].length() <= 64);
			assertTrue(t[0] + " fix: " + t[3], t[3].length() <= 50);
			longestHeadline = Math.max(longestHeadline, t[1].length());
			longestProof = Math.max(longestProof, t[2].length());
			longestFix = Math.max(longestFix, t[3].length());
		}
		assertEquals(32, Words.HEADLINE_MAX);
		assertEquals(64, Words.PROOF_MAX);
		assertEquals(50, Words.FIX_MAX);
		// The limits are met by the words as written, not by cutting: the longest of each kind is a whole sentence.
		assertEquals("World 999 is struggling, not you", Words.headline(Rules.W1, largest(), SESSION));
		assertEquals(32, longestHeadline);
		assertEquals("Ticks 600 to 9,990+ ms for 120 s. Ping stayed 999 ms, 999 fps.",
			Words.proof(Rules.W1, largest(), everySettings()[0]));
		assertTrue(longestProof <= 64 && longestProof >= 62);
		assertEquals("Hop worlds. If it follows you, it is your line.".length(), longestFix);

		// The two fillers printed in seconds with a decimal are clamped to the largest length of 6.4, 120 s.
		assertEquals("Map loading took 120.0 s", Words.headline(Rules.S1, largest(), SESSION));
		final Evidence calm = largest();
		calm.rttSpike = Evidence.NO;
		final String n6 = Words.proof(Rules.N6, calm, everySettings()[0]);
		assertTrue(n6, n6.startsWith("No ticks for 120.0 s."));
		assertEquals("No ticks for 120.0 s. Frames and ping were fine.", n6);
		assertEquals("a spiking ping is never called fine", "No ticks for 120.0 s. Frames were fine.",
			Words.proof(Rules.N6, largest(), everySettings()[0]));
		final Evidence justOver = largest();
		justOver.loadMs = 120_050;
		justOver.noTickMs = 150_000;
		assertEquals("Map loading took 120.0 s", Words.headline(Rules.S1, justOver, SESSION));
		assertTrue(Words.proof(Rules.N6, justOver, everySettings()[0]).startsWith("No ticks for 120.0 s."));
		justOver.loadMs = 119_900;
		justOver.noTickMs = 2_400;
		assertEquals("under the clamp the value prints as it is", "Map loading took 119.9 s",
			Words.headline(Rules.S1, justOver, SESSION));
		assertTrue(Words.proof(Rules.N6, justOver, everySettings()[0]).startsWith("No ticks for 2.4 s."));
	}

	@Test
	public void noFillerIsLeftOpenAndEveryTextIsAscii()
	{
		for (String[] t : everyText())
		{
			for (int i = 1; i < t.length; i++)
			{
				final String text = t[i];
				assertFalse(t[0] + ": " + text, text.contains("{") || text.contains("}"));
				assertFalse(t[0] + ": a no-data filler printed as -1: " + text, text.contains("-1"));
				for (int c = 0; c < text.length(); c++)
				{
					final char ch = text.charAt(c);
					assertTrue(t[0] + ": not plain ASCII at " + c + ": " + text, ch >= 0x20 && ch < 0x7f);
				}
				assertFalse(t[0] + ": an arrow: " + text, text.contains("->") || text.contains("=>"));
			}
			if (!t[2].isEmpty())
			{
				assertTrue(t[0] + ": a proof ends its sentence: " + t[2], t[2].endsWith("."));
			}
		}
		assertTrue("the word, where the picture has an arrow",
			Words.proof(Rules.W1, largest(), everySettings()[0]).startsWith("Ticks 600 to "));
	}

	@Test
	public void everyEventAndConditionRuleHasItsThreeTexts()
	{
		final Evidence e = largest();
		final SettingsView s = everySettings()[0];
		for (Rule r : Rules.ALL)
		{
			assertFalse(r.id, Words.headline(r, e, SESSION).isEmpty());
			if (r.kind == Rule.Kind.EVENT || r.kind == Rule.Kind.CONDITION)
			{
				assertFalse(r.id + " proof", Words.proof(r, e, s).isEmpty());
				assertFalse(r.id + " fix", Words.fix(r, e, s).isEmpty());
			}
		}
		assertEquals("V2 has no fix", "", Words.fix(Rules.V2, e, s));
		assertEquals("Wait for it to happen again.", Words.fix(Rules.X, e, s));
		assertEquals("Can't tell yet", Words.headline(Rules.X, e, SESSION));
		assertEquals("Smooth", Words.headline(Rules.V2, e, SESSION));
	}

	@Test
	public void noFixSendsThePlayerToTheRouterOutright()
	{
		int cableOrWifi = 0;
		for (String[] t : everyText())
		{
			final String fix = t[3];
			assertFalse(t[0] + ": " + fix, fix.toLowerCase().contains("router"));
			assertFalse(t[0] + ": " + t[2], t[2].toLowerCase().contains("router"));
			if (fix.contains("check cable") || fix.contains("check Wi-Fi"))
			{
				assertTrue(t[0] + ": " + fix, fix.startsWith("If every world does it"));
				assertTrue(t[0], t[0].equals("D1") || t[0].equals("N3"));
				cableOrWifi++;
			}
		}
		assertTrue("D1's bad case and N3 were seen", cableOrWifi > 0);
		final SettingsView s = everySettings()[0];
		assertEquals("If every world does it, check cable or Wi-Fi.", Words.fix(Rules.N3, largest(), s));
		assertEquals("If every world does it, check cable or Wi-Fi.", Words.fix(Rules.D1, largest(), s));
	}

	@Test
	public void resendsArePrintedAsAShare()
	{
		final SettingsView s = everySettings()[0];
		final Evidence e = largest();
		e.resent = 123_456;
		e.sent = 654_321;
		e.resentPm = 188;
		final String proof = Words.proof(Rules.N3, e, s);
		assertEquals("19 in 100 were re-sent. Ping can look fine.", proof);
		assertFalse("never a count: on Windows the counter is bytes", proof.contains("123") || proof.contains("654"));

		e.beforeRttSpike = Evidence.NO;
		e.beforeResentPm = 188;
		assertEquals("19 in 100 were re-sent just before.", Words.proof(Rules.D1, e, s));

		assertEquals("1 in 100", Words.share(0));
		assertEquals("1 in 100", Words.share(1));
		assertEquals("1 in 100", Words.share(14));
		assertEquals("2 in 100", Words.share(15));
		assertEquals("2 in 100", Words.share(24));
		assertEquals("3 in 100", Words.share(25));
		assertEquals("100 in 100", Words.share(1000));
		assertEquals("100 in 100", Words.share(5000));
	}

	/** Each row of the fillers table of 6.4, from traces and from evidence set by hand. */
	@Test
	public void fillers()
	{
		final SettingsView cpu = everySettings()[0];
		final SettingsView gpu = everySettings()[1];
		final VerdictEngine judge = new VerdictEngine();

		// D1's clock is the FIRST second of the span with the DISCONNECT flag.
		final Trace two = Trace.steady(300).usual(USUAL).disconnect(185).disconnect(250);
		final Session s = two.build();
		final Verdict d1 = judge.judgeEvent(s, event(s, 0, 180, 250, Trigger.TICK_OFF, Trigger.DISCONNECT),
			two.settings());
		assertEquals("20:52:00 and 185 s", "Connection lost at 20:55", d1.headline);
		final Evidence at = new Evidence();
		at.disconnectSec = 3600 + 22 * 60;
		assertEquals("Connection lost at 22:14", Words.headline(Rules.D1, at, SESSION));

		// D1, a bad ping AND bad re-sends: the ping sentence, with the RTT the spike was judged on.
		final Evidence both = new Evidence();
		both.beforeRtt = 120;
		both.beforeRttMax = 310;
		both.beforeRttSpike = Evidence.YES;
		both.beforeResentPm = 15;
		assertEquals("Ping was 310 ms just before.", Words.proof(Rules.D1, both, cpu));
		assertEquals("If every world does it, check cable or Wi-Fi.", Words.fix(Rules.D1, both, cpu));
		both.beforeRttSpike = Evidence.NO;
		// Per mille 15 prints 2, 14 prints 1.
		assertEquals("2 in 100 were re-sent just before.", Words.proof(Rules.D1, both, cpu));
		both.beforeResentPm = 14;
		assertEquals("1 in 100 were re-sent just before.", Words.proof(Rules.D1, both, cpu));
		both.beforeResentPm = 9;
		assertEquals("under RESENT_PER_MILLE: fine", "Ping and re-sends were fine just before.",
			Words.proof(Rules.D1, both, cpu));
		assertEquals("Wait a minute, then log in again.", Words.fix(Rules.D1, both, cpu));
		both.beforeRttSpike = Evidence.NO_DATA;
		assertEquals("No ping data just before.", Words.proof(Rules.D1, both, cpu));
		assertEquals("Log in again. Note if it repeats.", Words.fix(Rules.D1, both, cpu));
		both.beforeResentPm = 15;
		assertEquals("no ping, bad re-sends", "2 in 100 were re-sent just before.", Words.proof(Rules.D1, both, cpu));

		final Evidence e = new Evidence();
		e.resentPm = 15;
		assertEquals("2 in 100 were re-sent. Ping can look fine.", Words.proof(Rules.N3, e, cpu));
		e.resentPm = 14;
		assertEquals("1 in 100 were re-sent. Ping can look fine.", Words.proof(Rules.N3, e, cpu));

		// W1's "{n}+" is the median rounded DOWN to a multiple of 10.
		e.tickMedianMs = 934;
		e.durationS = 14;
		e.rtt = 41;
		e.fps = 50;
		e.world = 416;
		assertEquals("Ticks 600 to 930+ ms for 14 s. Ping stayed 41 ms, 50 fps.", Words.proof(Rules.W1, e, cpu));
		e.tickMedianMs = 939;
		assertTrue(Words.proof(Rules.W1, e, cpu).startsWith("Ticks 600 to 930+ ms"));
		e.tickMedianMs = 940;
		assertTrue(Words.proof(Rules.W1, e, cpu).startsWith("Ticks 600 to 940+ ms"));
		assertEquals("World 416 is struggling, not you", Words.headline(Rules.W1, e, SESSION));

		// S3's clean-up part is gcOverlapMs, the part of the freeze that was covered - not the pause's length.
		e.frameGapMs = 950;
		e.gcMs = 400;
		e.gcOverlapMs = 150;
		assertEquals("A 950 ms freeze. 150 ms was memory clean-up.", Words.proof(Rules.S3, e, cpu));
		e.gcOverlapMs = 0;
		assertEquals("A 950 ms freeze. Connection and world were fine.", Words.proof(Rules.S3, e, cpu));
		e.gcOverlapMs = -1;
		assertEquals("A 950 ms freeze. Connection and world were fine.", Words.proof(Rules.S3, e, cpu));
		assertEquals("A 950 ms freeze, but the client was not busy.", Words.proof(Rules.S4, e, cpu));

		// S1: Fmt.tenths of the whole run; the loads are counted back from the event's END.
		e.loadMs = 2400;
		e.loads = 6;
		assertEquals("Map loading took 2.4 s", Words.headline(Rules.S1, e, SESSION));
		assertEquals("6 loads in the last 10 min.", Words.proof(Rules.S1, e, cpu));
		assertEquals("Nothing to fix. It is the map.", Words.fix(Rules.S1, e, cpu));
		assertEquals("Extended map loading is set to 3.", Words.proof(Rules.S1, e, gpu));
		assertEquals("Lower Extended map loading.", Words.fix(Rules.S1, e, gpu));
		final Trace loads = Trace.steady(1000).usual(USUAL).loading(100, 900).loading(101, 900)
			.loading(350, 500).loading(698, 1000).loading(699, 1000).loading(700, 400);
		final Session ls = loads.build();
		final LagEvent load = event(ls, 0, 701, 701, Trigger.LONG_LOAD);
		final Evidence le = EvidenceBuilder.forEvent(ls, load, loads.settings());
		assertEquals("the run at 100 ended more than LOADS_LOOK_S before the event's end", 2, le.loads);
		assertEquals(2400, le.loadMs);
		final Verdict s1 = judge.judgeEvent(ls, load, loads.settings());
		assertEquals("Map loading took 2.4 s", s1.headline);
		assertEquals("2 loads in the last 10 min.", s1.proof);

		// N6's "{1.8}" is Fmt.tenths(noTickMs). The ping part only when the spike was judged and clean (6.3).
		e.noTickMs = 1840;
		e.rttKnown = true;
		e.rttUsual = USUAL;
		e.rttSpike = Evidence.NO;
		assertEquals("No ticks for " + Fmt.tenths(1840) + " s. Frames and ping were fine.",
			Words.proof(Rules.N6, e, cpu));
		assertEquals("No ticks for 1.8 s. Frames and ping were fine.", Words.proof(Rules.N6, e, cpu));
		// A fresh RTT with no usual yet (the first quiet seconds of every world): the spike has no data.
		e.rttUsual = -1;
		e.rttSpike = Evidence.NO_DATA;
		assertEquals("known RTT, no usual: ping no data", "No ticks for 1.8 s. Frames were fine.",
			Words.proof(Rules.N6, e, cpu));
		e.rttKnown = false;
		assertEquals("No ticks for 1.8 s. Frames were fine.", Words.proof(Rules.N6, e, cpu));
		e.rttKnown = true;
		e.rttUsual = USUAL;
		e.rttSpike = Evidence.YES;
		assertEquals("a spike excludes N6; its words still never call that ping fine",
			"No ticks for 1.8 s. Frames were fine.", Words.proof(Rules.N6, e, cpu));
		e.rttUsual = -1;
		e.rttSpike = Evidence.NO_DATA;

		// G1: the pause, then used and limit; the memory part is left out when a heap figure is -1.
		e.gcMs = 340;
		e.heapUsedMb = 742;
		e.heapMaxMb = 768;
		assertEquals("A 340 ms pause. Memory 742 of 768 MB.", Words.proof(Rules.G1, e, cpu));
		e.heapMaxMb = -1;
		assertEquals("A 340 ms pause.", Words.proof(Rules.G1, e, cpu));
		e.heapMaxMb = 768;
		e.heapUsedMb = -1;
		assertEquals("A 340 ms pause.", Words.proof(Rules.G1, e, cpu));

		// F1: the cap and who set it.
		e.capFps = 50;
		e.capLabel = CapSource.FPS_CONTROL.label();
		assertEquals("Frame rate is capped at 50", Words.headline(Rules.F1, e, SESSION));
		assertEquals("Set by FPS Control. Not lag.", Words.proof(Rules.F1, e, cpu));
		e.capLabel = CapSource.FPS_CONTROL_UNFOCUSED.label();
		assertEquals("Set by FPS Control (unfocused). Not lag.", Words.proof(Rules.F1, e, cpu));

		// F2: the rate and length of the slow run; the settings as read.
		e.lowFps = 24;
		e.lowFpsS = 40;
		e.fps = 50;
		assertEquals("24 fps for 40 s.", Words.proof(Rules.F2, e, cpu));
		assertEquals("Turn the GPU plugin on.", Words.fix(Rules.F2, e, cpu));
		assertEquals("24 fps. Draw distance 90, MSAA_16.", Words.proof(Rules.F2, e, gpu));
		assertEquals("Lower draw distance or anti-aliasing.", Words.fix(Rules.F2, e, gpu));
		assertEquals("24 fps. Draw distance 32.", Words.proof(Rules.F2, e, settings(Renderer.HD, "", 32, 0)));

		// N1: the usual only when it is PING_USUAL_LOWER_MS lower, or more.
		e.rtt = 180;
		e.rttUsual = 45;
		assertEquals("180 ms. Your usual is 45 ms.", Words.proof(Rules.N1, e, cpu));
		e.rttUsual = 150;
		assertEquals("exactly 30 lower", "180 ms. Your usual is 150 ms.", Words.proof(Rules.N1, e, cpu));
		e.rttUsual = 151;
		assertEquals("180 ms, and steady.", Words.proof(Rules.N1, e, cpu));
		e.rttUsual = -1;
		assertEquals("180 ms, and steady.", Words.proof(Rules.N1, e, cpu));

		// X: "Ticks ran {240} ms off" is tickOffMs.
		e.first = Trigger.NO_TICK;
		e.tickOffMs = 240;
		e.rttKnown = true;
		assertEquals("Ticks ran 240 ms off. Cause not measured.", Words.cantTellProof(e, null, null));
		e.first = Trigger.GC_PAUSE;
		e.frameGapMs = 170;
		assertEquals("A 170 ms freeze. Its cause was not measured.", Words.cantTellProof(e, null, null));
		e.first = Trigger.RESENT;
		assertEquals("The connection wobbled. Cause not measured.", Words.cantTellProof(e, null, null));
		e.rttKnown = false;
		assertEquals("The connection wobbled. Cause not measured. No ping data.",
			Words.cantTellProof(e, null, null));
		assertEquals("It was memory clean-up or lost packets. No ping data.",
			Words.cantTellProof(e, Cause.GC_PAUSE, Cause.UPLOAD_LOSS));
		assertEquals("too long with it: left out", "It was your line or the server or the client waiting.",
			Words.cantTellProof(e, Cause.DELIVERY_GAP, Cause.CLIENT_WAITING));

		// W1c and G2.
		e.tickWindowMedianMs = 620;
		assertEquals("Ticks take 620 ms here. Ping and frames are fine.", Words.proof(Rules.W1C, e, cpu));
		e.heapMaxMb = 256;
		assertEquals("The client may use only 256 MB. Default is 768.", Words.proof(Rules.G2, e, cpu));
	}

	/**
	 * V2's proof counts the seconds since the newest event's end (addendum D, 2026-09-29): none yet is "this
	 * session"; under a minute it steps every TEXT_STEP_S seconds, never under one step; from a minute it counts whole
	 * minutes, at least 1, capped at 9,999.
	 */
	@Test
	public void allClearProofCountsSecondsThenMinutes()
	{
		assertEquals("No lag this session.", Words.allClearProof(-1));
		assertEquals("at least one step", "No lag for 10 s.", Words.allClearProof(0));
		assertEquals("No lag for 10 s.", Words.allClearProof(9));
		assertEquals("No lag for 10 s.", Words.allClearProof(10));
		assertEquals("rounded DOWN to a step", "No lag for 10 s.", Words.allClearProof(19));
		assertEquals("No lag for 20 s.", Words.allClearProof(20));
		assertEquals("No lag for 50 s.", Words.allClearProof(59));
		assertEquals("No lag for 1 min.", Words.allClearProof(60));
		assertEquals("whole minutes, rounded down", "No lag for 1 min.", Words.allClearProof(119));
		assertEquals("No lag for 2 min.", Words.allClearProof(120));
		assertEquals("No lag for 1,440 min.", Words.allClearProof(86_400));
		assertEquals("capped as it always was", "No lag for 9,999 min.", Words.allClearProof(Long.MAX_VALUE));
		assertEquals("No lag for 9,999 min.", Words.allClearProof(10_000L * 60));
	}

	@Test
	public void theCountingTexts()
	{
		// No warm-up since the first live look (WARMUP_S 0): the seconds are held at one step, never "Ready in 0 s.".
		assertEquals(0, Thresholds.WARMUP_S);
		assertEquals("Ready in 10 s.", Words.measuringProof(60));
		assertEquals("Ready in 10 s.", Words.measuringProof(10));
		assertEquals("Ready in 10 s.", Words.measuringProof(1));
		assertEquals("Ready in 10 s.", Words.measuringProof(0));
		assertEquals("Log in to start measuring.", Words.notLoggedInProof());
		assertEquals("No frames are being drawn.", Words.waitingProof());
	}

	@Test
	public void ruledOutNamesWhatWasMeasuredAndClean()
	{
		final VerdictCauseTest.Case stall = VerdictCauseTest.s3();
		assertEquals("Memory, ping and ticks were fine.", stall.verdict().ruledOut);
		final VerdictCauseTest.Case world = VerdictCauseTest.w1();
		assertEquals("its own group, the ticks, is not named", "Memory, frames and ping were fine.",
			world.verdict().ruledOut);
		final VerdictCauseTest.Case loss = VerdictCauseTest.n3();
		assertEquals("Memory, frames and ticks were fine.", loss.verdict().ruledOut);

		final Evidence e = new Evidence();
		e.event = true;
		assertEquals("nothing known, nothing said", "", Words.ruledOut(Cause.CLIENT_BUSY, e, everySettings()[0]));
		assertEquals("Memory pauses not measured.", Words.ruledOut(Cause.CLIENT_BUSY, e,
			VerdictCauseTest.runtimeMemory()));
		e.rttSpike = Evidence.NO;
		assertEquals("Ping was fine.", Words.ruledOut(Cause.CLIENT_BUSY, e, everySettings()[0]));
		assertEquals("Ping was fine. Memory pauses not measured.", Words.ruledOut(Cause.CLIENT_BUSY, e,
			VerdictCauseTest.runtimeMemory()));
		e.framesClean = true;
		assertEquals("Frames and ping were fine.", Words.ruledOut(Cause.NOT_SURE, e, everySettings()[0]));

		final Evidence window = new Evidence();
		window.framesClean = true;
		window.rttSpike = Evidence.NO;
		assertEquals("a condition carries none", "", Words.ruledOut(Cause.PING_HIGH, window, everySettings()[0]));
	}
}
