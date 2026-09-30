package com.whylag.core;

import java.util.List;
import java.util.Locale;

/**
 * The text of "Copy report" (contract 6.6; lot L5): plain ASCII, every line ended with {@code \n}, made from the
 * {@link PanelSnapshot} ALONE - it reads no ring and no second, and names no player, no address and no plugin.
 * The plugin answers {@code PanelActions.report} with it; the panel never names this class.
 *
 * <pre>
 * 2h Why Lag report - world 416 - 20:52 to 21:52 (60 min)
 * Now: Smooth. 50 fps, ticks 600 ms, ping 41 ms, memory 51 %, CPU 37 %
 * 4 lags this session: 1 connection, 1 frame rate, 1 memory, 1 world
 * 21:47:30  14 s  WORLD  Likely  World 416 is struggling, not you. Ticks 600 to 900+ ms for 14 s. ...
 * (one line per session event, newest first)
 * Client 1.12.38, renderer GPU, draw distance 50, MSAA_2, cap 50 (the client), memory limit 768 MB, ...
 * </pre>
 *
 * <ol>
 * <li>The world ({@code " - not logged in"} at world 0), the session's start and now as clocks, and the whole
 * minutes between them, rounded down.</li>
 * <li>The verdict's headline, the four tiles' values in {@link Lane} order - the frame rate's alone, the others after
 * their word; a "-" tile prints its word and "-" - and the whole PC's CPU ("CPU -" at -1). The game's share is not
 * in the report.</li>
 * <li>{@link Fmt#lags} of the session total, then each group of CONNECTION, FRAME_RATE, MEMORY, WORLD and UNSURE
 * whose count is above 0, as the count and its label in lower case.</li>
 * <li>One line per session event, NEWEST first: the start clock, the length, the group's label in upper case, the
 * confidence word and the verdict's text ({@code headline. proof Fix: fix}), joined by two spaces.</li>
 * <li>The settings: the client's version, the renderer, the draw distance and the anti-aliasing (GPU and 117 HD
 * only), the FOCUSED cap (the report reads no second, contract 3.7), the memory limit and the memory source.</li>
 * </ol>
 * Every number prints through {@link Fmt#thousands}; every clock through {@link Fmt#clock} or
 * {@link Fmt#clockSeconds} in the snapshot's zone.
 *
 * <p>Choice: any character outside printable ASCII, a control character too, prints as '?'; the text is plain ASCII.
 * <p>Choice: a null verdict prints the headline "Still measuring" ({@link Answer#HEAD_MEASURING}).
 * <p>Choice: an empty proof leaves no trailing space: the text is then the headline and "." alone.
 * <p>Choice: an event with no verdict (the log never holds one closed) prints "Can't tell" and no text.
 * <p>Choice: when no group of line 3 is above 0, the line has no colon, as with no events.
 * <p>Choice: the minutes of line 1 are held at 0 or more, for a wall clock set back.
 * <p>Choice: an empty anti-aliasing name on GPU or 117 HD prints "anti-aliasing unknown".
 */
public final class ReportText
{
	private static final char NEW_LINE = '\n';
	/** What joins the parts of an event line. */
	private static final String GAP = "  ";
	private static final String DASH = "-";
	private static final long MS_PER_MINUTE = 60_000L;
	private static final char FIRST_PRINTABLE = ' ';
	private static final char LAST_PRINTABLE = '~';
	private static final char NOT_ASCII = '?';
	/** The groups of line 3, in the order printed. */
	private static final Group[] COUNTED = {Group.CONNECTION, Group.FRAME_RATE, Group.MEMORY, Group.WORLD,
		Group.UNSURE};

	private ReportText()
	{
	}

	/** The report of {@code s}: the lines of the class notes, each ended with {@code 
}. */
	public static String of(PanelSnapshot s)
	{
		final List<LagEvent> events = s.sessionEvents;
		final StringBuilder out = new StringBuilder(512 + 192 * events.size());
		line(out, header(s));
		line(out, now(s));
		line(out, session(s));
		for (int i = events.size() - 1; i >= 0; i--)
		{
			line(out, event(events.get(i), s));
		}
		line(out, settings(s.settings));
		return out.toString();
	}

	/** Line 1: "2h Why Lag report - world 416 - 20:52 to 21:52 (60 min)". */
	private static String header(PanelSnapshot s)
	{
		final long minutes = Math.max(0, (s.wallMs - s.sessionStartWallMs) / MS_PER_MINUTE);
		return "2h Why Lag report" + (s.world == 0 ? " - not logged in" : " - world " + Fmt.thousands(s.world))
			+ " - " + Fmt.clock(s.sessionStartWallMs, s.zone) + " to " + Fmt.clock(s.wallMs, s.zone)
			+ " (" + Fmt.thousands((int) Math.min(Integer.MAX_VALUE, minutes)) + " min)";
	}

	/** Line 2: "Now: Smooth. 50 fps, ticks 600 ms, ping 41 ms, memory 51 %, CPU 37 %". */
	private static String now(PanelSnapshot s)
	{
		final String headline = s.verdict == null ? Answer.HEAD_MEASURING : s.verdict.headline;
		return "Now: " + headline + ". "
			+ tile(s, Lane.FRAME_RATE, "fps", true) + ", "
			+ tile(s, Lane.TICKS, "ticks", false) + ", "
			+ tile(s, Lane.PING, "ping", false) + ", "
			+ tile(s, Lane.MEMORY, "memory", false) + ", "
			+ (s.sysCpuPct < 0 ? "CPU " + DASH : Fmt.pct("CPU", s.sysCpuPct));
	}

	/** A tile's value: alone or after its word; "word -" when the tile has no value. */
	private static String tile(PanelSnapshot s, Lane lane, String word, boolean valueAlone)
	{
		final String value = s.tiles[lane.ordinal()].value;
		if (value.isEmpty() || DASH.equals(value))
		{
			return word + " " + DASH;
		}
		return valueAlone ? value : word + " " + value;
	}

	/** Line 3: "4 lags this session: 1 connection, 1 frame rate, 1 memory, 1 world". */
	private static String session(PanelSnapshot s)
	{
		final StringBuilder b = new StringBuilder(Fmt.lags(s.sessionTotal)).append(" this session");
		String between = ": ";
		for (Group g : COUNTED)
		{
			final int n = s.sessionCounts[g.ordinal()];
			if (n > 0)
			{
				b.append(between).append(Fmt.thousands(n)).append(' ').append(g.label().toLowerCase(Locale.ROOT));
				between = ", ";
			}
		}
		return b.toString();
	}

	/** An event line: "21:47:30  14 s  WORLD  Likely  headline. proof Fix: fix". */
	private static String event(LagEvent e, PanelSnapshot s)
	{
		final StringBuilder b = new StringBuilder(192)
			.append(Fmt.clockSeconds(e.startWallMs, s.zone)).append(GAP)
			.append(Fmt.thousands(e.lengthS())).append(" s").append(GAP)
			.append(e.group().label().toUpperCase(Locale.ROOT)).append(GAP);
		final Verdict v = e.verdict;
		if (v == null)
		{
			return b.append(Confidence.CANT_TELL.word()).toString();
		}
		b.append(v.confidence.word()).append(GAP).append(v.headline).append('.');
		if (!v.proof.isEmpty())
		{
			b.append(' ').append(v.proof);
		}
		if (!v.fix.isEmpty())
		{
			b.append(" Fix: ").append(v.fix);
		}
		return b.toString();
	}

	/** The last line: "Client 1.12.38, renderer GPU, draw distance 50, MSAA_2, cap 50 (the client), ...". */
	private static String settings(SettingsView v)
	{
		final StringBuilder b = new StringBuilder(192)
			.append("Client ").append(v.clientVersion.isEmpty() ? "unknown" : v.clientVersion)
			.append(", renderer ").append(v.renderer.label());
		if (v.renderer == Renderer.GPU || v.renderer == Renderer.HD)
		{
			b.append(", draw distance ").append(Fmt.thousands(v.drawDistance))
				.append(", ").append(v.antiAliasing.isEmpty() ? "anti-aliasing unknown" : v.antiAliasing);
		}
		final int cap = v.capFps(true);
		b.append(", cap ").append(cap > 0 ? Fmt.thousands(cap) + " (" + v.capSource(true).label() + ")" : "none");
		b.append(", memory limit ").append(v.heapMaxMb > 0 ? Fmt.thousands(v.heapMaxMb) + " MB" : "unknown");
		b.append(", memory source ").append(v.memorySource.name().toLowerCase(Locale.ROOT));
		return b.toString();
	}

	/** Appends {@code text} as one line of plain ASCII: any other character, a control one included, is '?'. */
	private static void line(StringBuilder out, String text)
	{
		for (int i = 0; i < text.length(); i++)
		{
			final char c = text.charAt(i);
			out.append(c >= FIRST_PRINTABLE && c <= LAST_PRINTABLE ? c : NOT_ASCII);
		}
		out.append(NEW_LINE);
	}
}
