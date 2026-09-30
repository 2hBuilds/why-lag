package com.whylag.core;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The ten checks of "Troubleshoot..." (1.0.1, lots B and C), each a pure function of the {@link CheckFacts} the
 * sampler thread gathered, and the plain-words verdict made of their answers. Nothing here reads a ring, a clock or
 * the game, so a test feeds every check a state that passes and a state that fails.
 *
 * <table>
 * <caption>The ten checks, in the order they are run and listed</caption>
 * <tr><th>Id</th><th>Name</th><th>Passes when</th></tr>
 * <tr><td>C1</td><td>Logged in</td><td>a world is known and the newest second is in-game</td></tr>
 * <tr><td>C2</td><td>Frames arrive</td><td>frames were counted in the last 2 s, at 1 to 1,000 a second</td></tr>
 * <tr><td>C3</td><td>Ticks arrive</td><td>the last tick is under 1.2 s old, 5 or more came in 10 s, their mean gap is
 * 500 to 700 ms</td></tr>
 * <tr><td>C4</td><td>Ping readable</td><td>a fresh round trip time was read from the game's own socket</td></tr>
 * <tr><td>C5</td><td>The sampler runs</td>
 * <td>the last step started under 2 s ago and its work took under 2 ms</td></tr>
 * <tr><td>C6</td><td>Settings read</td><td>the renderer and the cap are known</td></tr>
 * <tr><td>C7</td><td>The badge is up</td><td>it is registered with the client; hidden by the setting passes
 * (a note)</td></tr>
 * <tr><td>C8</td><td>Client fits</td><td>RuneLite 1.13.0 or newer, Java 11 or newer, the system named</td></tr>
 * <tr><td>C9</td><td>Clock sane</td><td>the wall clock never jumped back</td></tr>
 * <tr><td>C10</td><td>Not stuck measuring</td><td>logged in for over 60 s means the card left Measuring</td></tr>
 * </table>
 *
 * <p>Choice (1.0.0, the Hub's rule): the memory and CPU checks (C5 and C6 of the twelve) went with the readings
 * they stood on, and the rest kept their order, renumbered; the verdict still names the first check that fails.
 *
 * <p>
 * Choice: a check that cannot apply because nobody is logged in (C3, C4, C10) PASSES with "not logged in, not
 * checked": C1 already fails and is the verdict, and three more FAILs would only repeat it.
 * <br>
 * Choice: the detail of a FAIL is the plain-words sentence, in lower case, short enough to fit the panel's verdict
 * line once its first letter is raised ({@link #verdict}); the longer story is in the report's other lines.
 * <br>
 * Choice: a reading no check can rule on (the step not timed yet, a client version this class cannot parse) is left
 * out of that check: it neither fails nor counts as found.
 * <br>
 * Choice: a ping is fresh when it is at most {@link Thresholds#RTT_STALE_S} s old, as the rings and every tile
 * count it, not "under" it.
 * <br>
 * Choice: the verdict is the detail of the FIRST failing check, in the order above, with its first letter raised,
 * and no full stop; when every check passes it is {@value #ALL_GOOD}.
 * <br>
 * Choice: the verdict passes {@link Diagnostics#scrub}, as every line of the report does, because the panel shows it
 * without going through the report; no word a check prints is a path today, and this keeps it so.
 */
public final class Checks
{
	/** The verdict when every check passes. */
	public static final String ALL_GOOD = "Everything is being measured.";

	/** The fewest ticks in the last 10 s. */
	static final int MIN_TICKS = 5;
	/** The mean tick gap must lie in this range, in ms. */
	static final int GAP_LOW_MS = 500, GAP_HIGH_MS = 700;
	/** The frame rate must lie in this range. */
	static final int FPS_LOW = 1, FPS_HIGH = 1000;
	/** The last step must have started no longer ago than this, in ms. */
	static final long STEP_STALE_MS = 2000;
	/** The last step's work must have taken less than this, in microseconds. */
	static final long STEP_COST_US = 2000;
	/** The oldest RuneLite this plugin was built for: 1.13.0. */
	static final int MIN_CLIENT_MAJOR = 1, MIN_CLIENT_MINOR = 13;
	private static final String MIN_CLIENT = "1.13.0";
	/** The oldest Java this plugin runs on. */
	static final int MIN_JAVA = 11;
	/** Logged in for longer than this, the card must have left Measuring. */
	static final long MEASURING_S = 60;

	private static final long MS_PER_SECOND = 1000L;
	private static final long US_PER_MS = 1000L;
	private static final String NOT_LOGGED_IN = "not logged in, not checked";
	private static final Pattern NUMBERS = Pattern.compile("^(\\d+)(?:\\.(\\d+))?");

	private Checks()
	{
	}

	/** The ten results of {@code f}, C1 first. */
	public static List<CheckResult> run(CheckFacts f)
	{
		final List<CheckResult> out = new ArrayList<>(10);
		out.add(loggedIn(f));
		out.add(frames(f));
		out.add(ticks(f));
		out.add(ping(f));
		out.add(sampler(f));
		out.add(settings(f));
		out.add(badge(f));
		out.add(client(f));
		out.add(clock(f));
		out.add(measuring(f));
		return out;
	}

	/**
	 * The plain-words verdict: the detail of the first failing result with its first letter raised, else
	 * {@value #ALL_GOOD}.
	 */
	public static String verdict(List<CheckResult> results)
	{
		for (CheckResult r : results)
		{
			if (!r.pass)
			{
				return Diagnostics.scrub(raise(r.detail));
			}
		}
		return ALL_GOOD;
	}

	/**
	 * The note the diagnostics keep of a run: {@code tests run: 9 pass, 1 fail: C4 Ping readable}; the failures are
	 * listed by id and name, comma separated; a run with none ends at the count.
	 */
	public static String summary(List<CheckResult> results)
	{
		int pass = 0;
		final StringBuilder failed = new StringBuilder();
		for (CheckResult r : results)
		{
			if (r.pass)
			{
				pass++;
			}
			else
			{
				failed.append(failed.length() == 0 ? ": " : ", ").append(r.id).append(' ').append(r.name);
			}
		}
		return "tests run: " + pass + " pass, " + (results.size() - pass) + " fail" + failed;
	}

	/**
	 * The report's {@code Verdict} and {@code Checks} sections, for {@code ReportText.of(s, checks)}: the verdict
	 * line, a blank line, {@code Checks:} with one line per result, and a blank line; each line ended with a new
	 * line.
	 */
	public static String section(List<CheckResult> results)
	{
		final StringBuilder b = new StringBuilder(1024);
		b.append("Verdict: ").append(verdict(results)).append("\n\nChecks:\n");
		for (CheckResult r : results)
		{
			b.append(r.line()).append('\n');
		}
		return b.append('\n').toString();
	}

	// ------------------------------------------------------------------ the ten

	private static CheckResult loggedIn(CheckFacts f)
	{
		if (!f.loggedIn)
		{
			return result(1, "Logged in", false, "not logged in: nothing is measured yet");
		}
		if (f.world <= 0)
		{
			return result(1, "Logged in", false, "logged in, but the world is not known yet");
		}
		return result(1, "Logged in", true, "world " + Fmt.thousands(f.world));
	}

	private static CheckResult frames(CheckFacts f)
	{
		if (f.framesLast2s <= 0)
		{
			return result(2, "Frames arrive", false, "no frames counted for " + CheckFacts.FRAMES_S + " s");
		}
		if (f.fps < FPS_LOW || f.fps > FPS_HIGH)
		{
			return result(2, "Frames arrive", false, "the frame rate reads " + Fmt.thousands(f.fps));
		}
		return result(2, "Frames arrive", true, Fmt.thousands(f.fps) + " fps, " + Fmt.thousands(f.framesLast2s)
			+ " frames in " + CheckFacts.FRAMES_S + " s");
	}

	private static CheckResult ticks(CheckFacts f)
	{
		if (!f.loggedIn)
		{
			return result(3, "Ticks arrive", true, NOT_LOGGED_IN);
		}
		if (f.lastTickAgoMs < 0)
		{
			return result(3, "Ticks arrive", false, "no tick has arrived yet");
		}
		if (f.lastTickAgoMs >= Thresholds.NO_TICK_MS)
		{
			return result(3, "Ticks arrive", false, "no ticks for " + seconds(f.lastTickAgoMs) + " s");
		}
		if (f.ticksLast10s < MIN_TICKS)
		{
			return result(3, "Ticks arrive", false, "only " + f.ticksLast10s + " ticks in " + CheckFacts.TICKS_S
				+ " s");
		}
		if (f.meanGapMs >= 0 && (f.meanGapMs < GAP_LOW_MS || f.meanGapMs > GAP_HIGH_MS))
		{
			return result(3, "Ticks arrive", false, "ticks average " + Fmt.thousands(f.meanGapMs) + " ms");
		}
		return result(3, "Ticks arrive", true, Fmt.thousands(f.ticksLast10s) + " in " + CheckFacts.TICKS_S
			+ " s, last " + Fmt.thousands((int) Math.min(Integer.MAX_VALUE, f.lastTickAgoMs)) + " ms ago"
			+ (f.meanGapMs >= 0 ? ", mean gap " + Fmt.thousands(f.meanGapMs) + " ms" : ""));
	}

	private static CheckResult ping(CheckFacts f)
	{
		if (!f.loggedIn)
		{
			return result(4, "Ping readable", true, NOT_LOGGED_IN);
		}
		switch (f.pingState)
		{
			case UNSUPPORTED:
				return result(4, "Ping readable", false, "ping cannot be read on this PC");
			case ERROR:
				return result(4, "Ping readable", false, "ping cannot be read from this client");
			case STALE:
				return result(4, "Ping readable", false, "ping is stale: nothing sent lately");
			case READING:
				if (f.rttMs < 0)
				{
					return result(4, "Ping readable", false, "no ping reading yet");
				}
				if (f.pingAgeS > Thresholds.RTT_STALE_S)
				{
					return result(4, "Ping readable", false, "ping reading is " + f.pingAgeS + " s old");
				}
				return result(4, "Ping readable", true, Fmt.thousands(f.rttMs) + " ms"
					+ (f.pingAgeS >= 0 ? ", " + f.pingAgeS + " s old" : ""));
			default:
				return result(4, "Ping readable", false, "no ping reading yet");
		}
	}

	private static CheckResult sampler(CheckFacts f)
	{
		if (f.lastStepAgoMs > STEP_STALE_MS)
		{
			return result(5, "The sampler runs", false, "the sampler has not run for " + seconds(f.lastStepAgoMs)
				+ " s");
		}
		if (f.stepCostUs >= STEP_COST_US)
		{
			return result(5, "The sampler runs", false, "sampler steps are slow: "
				+ millis(f.stepCostUs) + " ms");
		}
		if (f.lastStepAgoMs < 0)
		{
			return result(5, "The sampler runs", true, "no step yet, the plugin has just started");
		}
		return result(5, "The sampler runs", true, "last step " + Fmt.thousands((int) f.lastStepAgoMs) + " ms ago"
			+ (f.stepCostUs >= 0 ? ", work " + Fmt.thousands((int) Math.min(Integer.MAX_VALUE, f.stepCostUs))
				+ " us" : ""));
	}

	private static CheckResult settings(CheckFacts f)
	{
		final List<String> missing = new ArrayList<>(2);
		if (f.renderer == Renderer.UNKNOWN)
		{
			missing.add("renderer");
		}
		if (!f.capKnown)
		{
			missing.add("cap");
		}
		if (!missing.isEmpty())
		{
			return result(6, "Settings read", false, "settings not read: " + String.join(", ", missing));
		}
		return result(6, "Settings read", true, "renderer " + f.renderer.label() + ", cap known");
	}

	private static CheckResult badge(CheckFacts f)
	{
		if (!f.badgeShow)
		{
			return result(7, "The badge is up", true, "hidden by setting");
		}
		if (!f.badgeRegistered)
		{
			return result(7, "The badge is up", false, "the badge is not registered with the client");
		}
		return result(7, "The badge is up", true, "registered, Show is on");
	}

	private static CheckResult client(CheckFacts f)
	{
		if (f.os.isEmpty())
		{
			return result(8, "Client fits", false, "the operating system is not named");
		}
		final int[] version = numbers(f.clientVersion);
		if (version != null && (version[0] < MIN_CLIENT_MAJOR
			|| version[0] == MIN_CLIENT_MAJOR && version[1] < MIN_CLIENT_MINOR))
		{
			return result(8, "Client fits", false, "client " + f.clientVersion + " is older than " + MIN_CLIENT);
		}
		final int java = javaMajor(f.javaVersion);
		if (java >= 0 && java < MIN_JAVA)
		{
			return result(8, "Client fits", false, "Java " + f.javaVersion + " is older than " + MIN_JAVA);
		}
		return result(8, "Client fits", true, "client " + (f.clientVersion.isEmpty() ? "unknown" : f.clientVersion)
			+ ", Java " + (f.javaVersion.isEmpty() ? "unknown" : f.javaVersion) + ", " + f.os);
	}

	private static CheckResult clock(CheckFacts f)
	{
		if (f.clockJumpedBackS > 0)
		{
			return result(9, "Clock sane", false, "the clock jumped back " + Fmt.thousands(f.clockJumpedBackS)
				+ " s" + (f.clockJumpAtWallMs > 0 ? " at " + Fmt.clock(f.clockJumpAtWallMs, f.zone) : ""));
		}
		return result(9, "Clock sane", true, "never went back, zone " + f.zone.getId());
	}

	private static CheckResult measuring(CheckFacts f)
	{
		if (!f.loggedIn)
		{
			return result(10, "Not stuck measuring", true, NOT_LOGGED_IN);
		}
		if (f.loggedInS > MEASURING_S && f.cardState == Answer.MEASURING)
		{
			return result(10, "Not stuck measuring", false, "measuring for " + Fmt.thousands((int) Math.min(
				Integer.MAX_VALUE, f.loggedInS)) + " s: no readings arrive");
		}
		return result(10, "Not stuck measuring", true, "card: " + f.cardState.oneLine);
	}

	// ------------------------------------------------------------------ helpers

	private static CheckResult result(int n, String name, boolean pass, String detail)
	{
		return new CheckResult("C" + n, name, pass, detail);
	}

	/** Whole seconds of a span in ms, rounded to the nearest, at least 1. */
	private static int seconds(long ms)
	{
		return (int) Math.max(1, Math.min(Integer.MAX_VALUE, (ms + MS_PER_SECOND / 2) / MS_PER_SECOND));
	}

	/** Microseconds as milliseconds with one decimal, rounded down: 2,350 is "2.3". */
	private static String millis(long us)
	{
		final long tenths = Math.max(0, us) * 10 / US_PER_MS;
		return (tenths / 10) + "." + (tenths % 10);
	}

	/** {@code text} with its first letter in upper case. */
	private static String raise(String text)
	{
		return text.isEmpty() ? text : text.substring(0, 1).toUpperCase(Locale.ROOT) + text.substring(1);
	}

	/** The first two numbers of a version such as {@code 1.13.0} or {@code 1.13.1-SNAPSHOT}; null when it has none. */
	private static int[] numbers(String version)
	{
		final Matcher m = NUMBERS.matcher(version);
		if (!m.find())
		{
			return null;
		}
		try
		{
			return new int[] {Integer.parseInt(m.group(1)), m.group(2) == null ? 0 : Integer.parseInt(m.group(2))};
		}
		catch (NumberFormatException e)
		{
			return null;
		}
	}

	/** The major number of a Java version: {@code 17.0.18} is 17, {@code 1.8.0_352} is 8; -1 when it cannot be read. */
	private static int javaMajor(String version)
	{
		final int[] n = numbers(version);
		if (n == null)
		{
			return -1;
		}
		return n[0] == 1 ? n[1] : n[0];
	}
}
