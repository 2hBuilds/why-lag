package com.whylag.core;

/**
 * Every text of a verdict (contract 6.4): headline, proof, fix and {@code ruledOut}. The ONLY class that holds
 * verdict text. The three card states' headlines are {@link Answer}'s constants, never literals of this class.
 *
 * <p>Every headline is at most {@link #HEADLINE_MAX} characters, every proof {@link #PROOF_MAX}, every fix
 * {@link #FIX_MAX}, WITH the largest values filled in: each filler is clamped to its largest printed value before
 * it is printed. Plain ASCII; the word "to" stands where the picture has an arrow.
 *
 * <p>Choice: a filler printed in ms is held in 0 .. 9,999 (a ping in 0 .. 999); one printed in SECONDS with a
 * decimal (S1's load, N6's stretch without a tick) is held in 0 .. 120,000 ms, "120.0", the largest length of 6.4.
 * <p>Choice: memory figures are held in 0 .. 99,999 MB, counts (loads, the map loading setting, the draw distance)
 * in 0 .. 999, V2's minutes in 1 .. 9,999.
 * <p>S1's proof is "{n} loads in the last 10 min." for every count, one included, as contract 6.4 writes it.
 * <p>N6's proof names the ping ("Frames and ping were fine.") only when the spike signal was judged and clean
 * ({@link Evidence#NO}); with no fresh RTT or no usual it has no data (contract 6.3), and the proof is the "ping no
 * data" form of 6.4, "Frames were fine.".
 * <p>Choice: F2's settings proof leaves out the anti-aliasing name when it is unknown ("") or would make the proof
 * longer than {@link #PROOF_MAX}; any text that is still too long is cut at its limit.
 * <p>Choice: X with no candidate and a first trigger that names nothing that moved by itself (none, DISCONNECT,
 * LONG_LOAD) uses the connection sentence for DISCONNECT and the freeze sentence otherwise.
 * <p>Choice: " No ping data." is added to ANY X proof when the span had no fresh RTT and the result still fits.
 * <p>Choice: {@code ruledOut} is written for an event's verdict only (X included): it names, of memory, frames,
 * ping and ticks, those that were measured and clean and are not the verdict's own group; on the RUNTIME memory
 * source it ends with "Memory pauses not measured.". A condition, V2 and the card states carry "".
 */
public final class Words
{
	public static final int HEADLINE_MAX = 32;
	public static final int PROOF_MAX = 64;
	public static final int FIX_MAX = 50;

	private static final int MAX_WORLD = 999;
	private static final int MAX_MS = 9_999;
	private static final int MAX_PING_MS = 999;
	private static final int MAX_S = 120;
	private static final int MAX_FPS = 999;
	private static final int MAX_MB = 99_999;
	private static final int MAX_COUNT = 999;
	private static final int MAX_MINUTES = 9_999;
	private static final int MS_PER_SECOND = 1000;
	private static final int SECONDS_PER_MINUTE = 60;
	private static final int SHARE_OF = 100;
	private static final int PER_MILLE = 1000;
	private static final int PER_MILLE_PER_SHARE = PER_MILLE / SHARE_OF;
	private static final int PLUS_STEP_MS = 10;
	private static final int DEFAULT_HEAP_MB = 768;

	private static final String NO_PING = " No ping data.";
	private static final String NOT_MEASURED = "Memory pauses not measured.";

	private Words()
	{
	}

	// ------------------------------------------------------------------ the rules

	/** The headline of a rule's verdict. {@code s} gives D1 its clock. */
	public static String headline(Rule r, Evidence e, Session s)
	{
		return fit(headlineOf(r, e, s), HEADLINE_MAX);
	}

	/** The proof of a rule's verdict; X's and V2's have their own methods. */
	public static String proof(Rule r, Evidence e, SettingsView settings)
	{
		return fit(proofOf(r, e, settings), PROOF_MAX);
	}

	/** The fix of a rule's verdict; "" = none. */
	public static String fix(Rule r, Evidence e, SettingsView settings)
	{
		return fit(fixOf(r, e, settings), FIX_MAX);
	}

	private static String headlineOf(Rule r, Evidence e, Session s)
	{
		switch (r.id)
		{
			case "D1":
				return "Connection lost at " + Fmt.clock(s.wallMsOf(Math.max(0, e.disconnectSec)), s.zone);
			case "G1":
				return "Memory clean-up froze the game";
			case "S1":
				return "Map loading took " + seconds(e.loadMs) + " s";
			case "N3":
				return "Packets are being lost";
			case "N2":
				return "Your connection is unsteady";
			case "W1":
				return "World " + Fmt.clamp(e.world, 0, MAX_WORLD) + " is struggling, not you";
			case "S3":
				return "The client itself stalled";
			case "S4":
				return "The client was kept waiting";
			case "N6":
				return "The game stopped answering";
			case "F1":
				return "Frame rate is capped at " + Fmt.clamp(e.capFps, 0, MAX_FPS);
			case "N1":
				return "Ping is high but steady";
			case "F2":
				return "The game is drawing slowly";
			case "W1c":
				return "This world is running slow";
			case "G2":
				return "Memory limit is set too low";
			case "V2":
				return "Smooth";
			default:
				return "Can't tell yet";
		}
	}

	private static String proofOf(Rule r, Evidence e, SettingsView settings)
	{
		switch (r.id)
		{
			case "D1":
				switch (beforeTheDisconnect(e))
				{
					case FINE:
						return "Ping and re-sends were fine just before.";
					case BAD_PING:
						return "Ping was " + ping(e.beforeRttMax) + " ms just before.";
					case BAD_RESENDS:
						return share(e.beforeResentPm) + " were re-sent just before.";
					default:
						return "No ping data just before.";
				}
			case "G1":
				return "A " + ms(e.gcMs) + " ms pause."
					+ (e.heapUsedMb < 0 || e.heapMaxMb < 0 ? ""
					: " Memory " + mb(e.heapUsedMb) + " of " + mb(e.heapMaxMb) + " MB.");
			case "S1":
				if (gpu(settings))
				{
					return "Extended map loading is set to " + count(settings.expandedMapLoading) + ".";
				}
				return count(e.loads) + " loads in the last " + Thresholds.LOADS_LOOK_S / SECONDS_PER_MINUTE + " min.";
			case "N3":
				return share(e.resentPm) + " were re-sent. Ping can look fine.";
			case "N2":
				return "Ping swung " + ping(e.rttMin) + "-" + ping(e.rttMax) + " ms. Ticks came early and late.";
			case "W1":
				return "Ticks " + Thresholds.TICK_MS + " to "
					+ Fmt.thousands(Fmt.clamp(e.tickMedianMs, 0, MAX_MS) / PLUS_STEP_MS * PLUS_STEP_MS) + "+ ms for "
					+ Fmt.clamp(e.durationS, 0, MAX_S) + " s. Ping stayed " + ping(e.rtt) + " ms, " + fps(e.fps)
					+ " fps.";
			case "S3":
				if (e.gcOverlapMs > 0)
				{
					return "A " + ms(e.frameGapMs) + " ms freeze. " + ms(e.gcOverlapMs) + " ms was memory clean-up.";
				}
				return "A " + ms(e.frameGapMs) + " ms freeze. Connection and world were fine.";
			case "S4":
				return "A " + ms(e.frameGapMs) + " ms freeze, but the client was not busy.";
			case "N6":
				// The ping part only when the spike was judged and was clean. With no fresh RTT OR no usual the spike
				// has no data (6.3), and then the words say nothing of the ping, as ruledOut does.
				return "No ticks for " + seconds(e.noTickMs) + " s. "
					+ (e.rttSpike == Evidence.NO ? "Frames and ping were fine." : "Frames were fine.");
			case "F1":
				return "Set by " + e.capLabel + ". Not lag.";
			case "N1":
				if (e.rttUsual >= 0 && e.rtt - e.rttUsual >= Thresholds.PING_USUAL_LOWER_MS)
				{
					return ping(e.rtt) + " ms. Your usual is " + ping(e.rttUsual) + " ms.";
				}
				return ping(e.rtt) + " ms, and steady.";
			case "F2":
				if (gpu(settings))
				{
					final String head = fps(e.lowFps) + " fps. Draw distance " + count(settings.drawDistance);
					final String full = head + ", " + settings.antiAliasing + ".";
					return settings.antiAliasing == null || settings.antiAliasing.isEmpty()
						|| full.length() > PROOF_MAX ? head + "." : full;
				}
				return fps(e.lowFps) + " fps for " + Fmt.clamp(e.lowFpsS, 0, MAX_S) + " s.";
			case "W1c":
				return "Ticks take " + ms(e.tickWindowMedianMs) + " ms here. Ping and frames are fine.";
			case "G2":
				return "The client may use only " + mb(e.heapMaxMb) + " MB. Default is " + DEFAULT_HEAP_MB + ".";
			default:
				return "";
		}
	}

	private static String fixOf(Rule r, Evidence e, SettingsView settings)
	{
		switch (r.id)
		{
			case "D1":
				switch (beforeTheDisconnect(e))
				{
					case FINE:
						return "Wait a minute, then log in again.";
					case BAD_PING:
					case BAD_RESENDS:
						return "If every world does it, check cable or Wi-Fi.";
					default:
						return "Log in again. Note if it repeats.";
				}
			case "G1":
				return "Close the world map. Restart if it repeats.";
			case "S1":
				return gpu(settings) ? "Lower Extended map loading." : "Nothing to fix. It is the map.";
			case "N3":
				return "If every world does it, check cable or Wi-Fi.";
			case "N2":
				return "Use a cable, not Wi-Fi. Pause downloads.";
			case "W1":
			case "W1c":
				return "Hop to a quieter world.";
			case "S3":
				return "Turn plugins off one at a time.";
			case "S4":
				return "Close overlays and recorders.";
			case "N6":
				return "Hop worlds. If it follows you, it is your line.";
			case "F1":
				return "Raise or turn off that cap.";
			case "N1":
				return "Try a world closer to you.";
			case "F2":
				return settings.renderer == Renderer.CPU ? "Turn the GPU plugin on."
					: "Lower draw distance or anti-aliasing.";
			case "G2":
				return "Remove the Java memory limit.";
			case "X":
				return "Wait for it to happen again.";
			default:
				return "";
		}
	}

	// ------------------------------------------------------------------ X, V2 and the card states

	/**
	 * X's proof. Two candidates: it names both, {@code a} first. None: it names WHAT MOVED, by the event's first
	 * trigger. X never says that nothing moved.
	 */
	public static String cantTellProof(Evidence e, Cause a, Cause b)
	{
		final String said;
		if (a != null && b != null)
		{
			said = "It was " + a.shortName() + " or " + b.shortName() + ".";
		}
		else if (e.first == Trigger.TICK_OFF || e.first == Trigger.NO_TICK)
		{
			said = "Ticks ran " + ms(e.tickOffMs) + " ms off. Cause not measured.";
		}
		else if (e.first == Trigger.RESENT || e.first == Trigger.RTT_SPIKE || e.first == Trigger.DISCONNECT)
		{
			said = "The connection wobbled. Cause not measured.";
		}
		else
		{
			said = "A " + ms(e.frameGapMs) + " ms freeze. Its cause was not measured.";
		}
		final boolean fits = said.length() + NO_PING.length() <= PROOF_MAX;
		return fit(!e.rttKnown && fits ? said + NO_PING : said, PROOF_MAX);
	}

	/**
	 * V2's proof, by the seconds since the newest event's end ({@code secondsSinceEnd < 0}: no lag yet, "No lag this
	 * session."). Under a minute: "No lag for 10 s." to "No lag for 50 s.", the seconds rounded DOWN to a multiple of
	 * {@link Thresholds#TEXT_STEP_S} and at least one step, so the counting text steps every 10 s. From a minute:
	 * "No lag for {m} min.", the whole minutes, at least 1, capped at 9,999.
	 * <p>Choice: the parameter was whole minutes until 2026-09-29; the card now goes back to Smooth 15 s after a lag
	 * ({@link Thresholds#EVENT_SHOW_S} 10), so the proof has to count seconds first.
	 */
	public static String allClearProof(long secondsSinceEnd)
	{
		if (secondsSinceEnd < 0)
		{
			return "No lag this session.";
		}
		if (secondsSinceEnd < SECONDS_PER_MINUTE)
		{
			final long step = Thresholds.TEXT_STEP_S;
			return "No lag for " + Math.max(step, secondsSinceEnd / step * step) + " s.";
		}
		final long minutes = secondsSinceEnd / SECONDS_PER_MINUTE;
		return "No lag for " + Fmt.thousands((int) Math.min(MAX_MINUTES, minutes)) + " min.";
	}

	public static String notLoggedInProof()
	{
		return "Log in to start measuring.";
	}

	/**
	 * "Ready in {40} s.": the seconds left, rounded UP to a multiple of {@link Thresholds#TEXT_STEP_S}.
	 * <p>Choice: with no warm-up ({@link Thresholds#WARMUP_S} 0, 2026-09-29) the seconds are held at one step at
	 * most, so the words never read "Ready in 0 s."; the card shows them only while no login is seen.
	 */
	public static String measuringProof(int leftS)
	{
		final int step = Thresholds.TEXT_STEP_S;
		final int held = Fmt.clamp(leftS, 1, Math.max(1, Thresholds.WARMUP_S));
		return "Ready in " + (held + step - 1) / step * step + " s.";
	}

	public static String waitingProof()
	{
		return "No frames are being drawn.";
	}

	// ------------------------------------------------------------------ ruled out

	/** What was measured and clean, for the report and the tooltip; "" = nothing to say. */
	public static String ruledOut(Cause cause, Evidence e, SettingsView settings)
	{
		if (!e.event)
		{
			return "";
		}
		final Group own = cause.group();
		final boolean measured = settings.memorySource == MemorySource.MANAGEMENT;
		final String[] clean = new String[4];
		int n = 0;
		if (measured && e.gcMs >= 0 && e.gcMs < Thresholds.GC_PAUSE_MS && own != Group.MEMORY)
		{
			clean[n++] = "memory";
		}
		if (e.framesClean && own != Group.FRAME_RATE)
		{
			clean[n++] = "frames";
		}
		if (e.rttSpike == Evidence.NO && own != Group.CONNECTION)
		{
			clean[n++] = "ping";
		}
		if (e.ticks > 0 && e.tickLate == 0 && e.tickEarly == 0 && e.noTickMs < Thresholds.NO_TICK_MS
			&& own != Group.WORLD)
		{
			clean[n++] = "ticks";
		}
		final StringBuilder out = new StringBuilder();
		for (int i = 0; i < n; i++)
		{
			if (i > 0)
			{
				out.append(i == n - 1 ? " and " : ", ");
			}
			out.append(clean[i]);
		}
		if (n > 0)
		{
			out.setCharAt(0, Character.toUpperCase(out.charAt(0)));
			out.append(n == 1 ? " was fine." : " were fine.");
		}
		if (!measured)
		{
			out.append(n > 0 ? " " : "").append(NOT_MEASURED);
		}
		return out.toString();
	}

	// ------------------------------------------------------------------ fillers

	private enum Before
	{
		FINE, BAD_PING, BAD_RESENDS, NO_DATA
	}

	/** D1: how the link looked in the seconds that end at the last tick. With both bad it is the ping. */
	private static Before beforeTheDisconnect(Evidence e)
	{
		if (e.beforeRttSpike == Evidence.YES)
		{
			return Before.BAD_PING;
		}
		if (e.beforeResentPm >= Thresholds.RESENT_PER_MILLE)
		{
			return Before.BAD_RESENDS;
		}
		if (e.beforeRttSpike == Evidence.NO && e.beforeResentPm >= 0)
		{
			return Before.FINE;
		}
		return Before.NO_DATA;
	}

	/** "{2} in 100": per mille to a share of 100, rounded half up and at least 1. */
	static String share(int perMille)
	{
		final int pm = Fmt.clamp(perMille, 0, PER_MILLE);
		return Math.max(1, (pm + PER_MILLE_PER_SHARE / 2) / PER_MILLE_PER_SHARE) + " in " + SHARE_OF;
	}

	private static boolean gpu(SettingsView settings)
	{
		return settings.renderer == Renderer.GPU || settings.renderer == Renderer.HD;
	}

	private static String ms(int v)
	{
		return Fmt.thousands(Fmt.clamp(v, 0, MAX_MS));
	}

	private static String ping(int v)
	{
		return Integer.toString(Fmt.clamp(v, 0, MAX_PING_MS));
	}

	private static String fps(int v)
	{
		return Integer.toString(Fmt.clamp(v, 0, MAX_FPS));
	}

	private static String mb(int v)
	{
		return Fmt.thousands(Fmt.clamp(v, 0, MAX_MB));
	}

	private static String count(int v)
	{
		return Integer.toString(Fmt.clamp(v, 0, MAX_COUNT));
	}

	/** A length in ms as seconds with one decimal, held in 0 .. 120 s. */
	private static String seconds(int ms)
	{
		return Fmt.tenths(Fmt.clamp(ms, 0, MAX_S * MS_PER_SECOND));
	}

	private static String fit(String text, int max)
	{
		return text.length() <= max ? text : text.substring(0, max);
	}
}
