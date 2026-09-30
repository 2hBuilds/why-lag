package com.whylag.core;

import java.util.Locale;

/**
 * What changed between one sampler step and the next, written as notes into {@link Diagnostics}: a login, a hop or
 * a logout; a lag that opened and one that closed; the card's state; the ping probe's state; and the settings that
 * decide a verdict. The plugin calls {@link #step} once a second on the sampler thread, after the engine's step and
 * the badge, with what it already holds; this class keeps only what it noted last, so a steady state says nothing
 * twice. Pure: it reads the session's second ring (like {@link SnapshotBuilder}) and never the game.
 *
 * <p><b>The notes:</b>
 * <ul>
 * <li>{@code logged in, world 416}, {@code hop to world 302}, {@code logged out}: read from the newest complete
 * second of the ring - logged out is its {@link Flags#NOT_LOGGED_IN}, a hop is the world column changing while
 * logged in;</li>
 * <li>{@code lag opened: WORLD, ticks 1,240 ms, ping 41 ms} and {@code lag closed after 14 s: World lag - not you,
 * Likely};</li>
 * <li>{@code card: Measuring}, {@code card: Smooth}, ... - the one line of the card's {@link Answer};</li>
 * <li>{@code ping: reading}, {@code ping: not on this PC}, {@code ping: could not read it}, {@code ping: stale};</li>
 * <li>{@code settings: renderer GPU, cap 50 (the client)} when a re-read changed the renderer or the cap.</li>
 * </ul>
 *
 * <p>
 * Choice: every note of a step is stamped with the step's wall time, not with the second it is about, so the notes
 * are in the order they were written.
 * <br>
 * Choice: the first step notes the state it finds (the card, the ping, the settings, a player already logged in); a
 * player found logged OUT says nothing.
 * <br>
 * Choice: a closed lag that was never seen open (it opened and closed inside one step) is given its "lag opened"
 * note first, from its own numbers, so every "closed" has an "opened" before it; closed ones are noted before a new
 * open one in the same step.
 * <br>
 * Choice: a world that becomes known while logged in with none known is noted as {@code world 416}, not as a hop.
 * <br>
 * Choice: a number with no data reads "-" in the lag notes, as in the minute line.
 */
public final class StepNotes
{
	private static final int UNKNOWN = 0, OUT = 1, IN = 2;
	private static final long NO_EVENT = -1;

	private final Diagnostics diagnostics;

	private int login = UNKNOWN;
	private int worldSeen;
	private long openedId = NO_EVENT;
	private long closedId = NO_EVENT;
	private Answer answer;
	private NoData ping;
	private Renderer renderer;
	private int cap = -1;
	private CapSource capSource;

	/** @param diagnostics where the notes go */
	public StepNotes(Diagnostics diagnostics)
	{
		this.diagnostics = diagnostics;
	}

	/**
	 * One step. Sampler thread only.
	 *
	 * @param wallMs     the step's wall time
	 * @param s          the session whose newest complete second says who is logged in and in which world
	 * @param nowSec     the step's session second
	 * @param card       the card's verdict, {@code LagSource.verdict()}; null = none
	 * @param open       the open event, {@code LagSource.openEvent()}; null = none
	 * @param lastClosed the session's newest closed event; null = none
	 * @param conn       why the ping has no data, {@code ConnSample.conn}; {@link NoData#NONE} = it is read
	 * @param settings   the settings now; null = none read
	 */
	public void step(long wallMs, Session s, long nowSec, Verdict card, LagEvent open, LagEvent lastClosed,
		NoData conn, SettingsView settings)
	{
		login(wallMs, s, nowSec);
		lags(wallMs, open, lastClosed);
		card(wallMs, card);
		ping(wallMs, conn);
		settings(wallMs, settings);
	}

	private void login(long wallMs, Session s, long nowSec)
	{
		final long last = s.lastSec(nowSec);
		if (last < 0)
		{
			return;
		}
		final SecondRing r = s.seconds;
		final int flags = r.flags(last);
		final int world = r.world(last);
		if (!r.valid(last))
		{
			return;
		}
		if (Flags.has(flags, Flags.NOT_LOGGED_IN))
		{
			if (login == IN)
			{
				diagnostics.note(wallMs, "logged out");
			}
			login = OUT;
			worldSeen = 0;
			return;
		}
		if (login != IN)
		{
			login = IN;
			worldSeen = Math.max(0, world);
			diagnostics.note(wallMs, world > 0 ? "logged in, world " + Fmt.thousands(world) : "logged in");
		}
		else if (world > 0 && world != worldSeen)
		{
			diagnostics.note(wallMs, (worldSeen == 0 ? "world " : "hop to world ") + Fmt.thousands(world));
			worldSeen = world;
		}
	}

	private void lags(long wallMs, LagEvent open, LagEvent lastClosed)
	{
		if (lastClosed != null && lastClosed.id != closedId)
		{
			closedId = lastClosed.id;
			if (lastClosed.id != openedId)
			{
				openedId = lastClosed.id;
				diagnostics.note(wallMs, opened(lastClosed));
			}
			diagnostics.note(wallMs, closed(lastClosed));
		}
		if (open != null && open.id != openedId)
		{
			openedId = open.id;
			diagnostics.note(wallMs, opened(open));
		}
	}

	/** {@code lag opened: WORLD, ticks 1,240 ms, ping 41 ms}. */
	private static String opened(LagEvent e)
	{
		return "lag opened: " + e.group().label().toUpperCase(Locale.ROOT) + ", ticks " + ms(e.worstTickGapMs)
			+ ", ping " + ms(e.rttMs);
	}

	/** {@code lag closed after 14 s: World lag - not you, Likely}. */
	private static String closed(LagEvent e)
	{
		final Verdict v = e.verdict;
		final String words = (v == null ? Answer.of(Cause.NOT_SURE) : Answer.of(v)).oneLine;
		final String confidence = (v == null ? Confidence.CANT_TELL : v.confidence).word();
		return "lag closed after " + Fmt.thousands(e.lengthS()) + " s: " + words + ", " + confidence;
	}

	private void card(long wallMs, Verdict card)
	{
		final Answer now = Answer.of(card);
		if (now != answer)
		{
			answer = now;
			diagnostics.note(wallMs, "card: " + now.oneLine);
		}
	}

	private void ping(long wallMs, NoData conn)
	{
		final NoData now = conn == null ? NoData.STALE : conn;
		if (now != ping)
		{
			ping = now;
			diagnostics.note(wallMs, "ping: " + pingWords(now));
		}
	}

	/** The ping probe's state in words: its five states are reading, not on this PC, could not read it and stale. */
	private static String pingWords(NoData conn)
	{
		switch (conn)
		{
			case NONE:
				return "reading";
			case UNSUPPORTED:
				return "not on this PC";
			case ERROR:
				return "could not read it";
			case STALE:
				return "stale";
			case NOT_LOGGED_IN:
				return "not logged in";
			case NOT_CONNECTED:
				return "not connected";
			default:
				return "no data";
		}
	}

	private void settings(long wallMs, SettingsView settings)
	{
		if (settings == null)
		{
			return;
		}
		final int nowCap = settings.capFps(true);
		final CapSource nowSource = settings.capSource(true);
		if (settings.renderer == renderer && nowCap == cap && nowSource == capSource)
		{
			return;
		}
		renderer = settings.renderer;
		cap = nowCap;
		capSource = nowSource;
		diagnostics.note(wallMs, "settings: renderer " + renderer.label() + ", cap "
			+ (cap > 0 ? Fmt.thousands(cap) + " (" + nowSource.label() + ")" : "none"));
	}

	/** A millisecond number with its unit; "-" when there is no data. */
	private static String ms(int value)
	{
		return value < 0 ? "-" : Fmt.thousands(value) + " ms";
	}
}
