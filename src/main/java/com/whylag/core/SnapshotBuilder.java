package com.whylag.core;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Builds what the panel reads (contract 3.8, 5.2, 5.3; lot L5): the three tiles, the three strips with the value at
 * the right of each lane, the world of now, the range's events, the session's events and the session counts.
 * The plugin calls it once a second on the sampler thread, under the engine's step lock, while the panel shows. It
 * only READS the session - the rings, the event log and this world's RTT usual - and writes nothing: it keeps no state
 * of its own, so one instance serves every call.
 *
 * <p><b>Now.</b> Every "now" is the newest COMPLETE second, {@code last = s.lastSec(nowSec)} (contract 3.5); -1 means
 * none yet. The player is logged in when that second exists and its state is in-game ({@link State#inGame}). While
 * not, every tile is "-" with "Not logged in", every lane has no value now and the snapshot's {@code world} is 0
 * whatever the world column holds. The warm-up is the card's alone: the tiles show their
 * numbers through it, and nothing here reads {@link Session#warm}.
 *
 * <p><b>The window</b> (contract 3.5) is the seconds {@code last - WINDOW_S + 1 .. last}, none before the ring's
 * tail. Its ticks are those that arrived in {@code [msOfSec(first), msOfSec(last + 1))} and are not masked: no bit
 * of {@link Flags#MASKED} on the tick nor on the second it arrived in (a hop's or a teleport's tick is no lag). The
 * Frame rate tile's "worst" is the largest {@code worstFrameMs} of the window's seconds; the Server ticks tile reads
 * the window's ticks.
 *
 * <p><b>The time map</b> (contract 5.3). The range ends at the end of the newest second: {@code msOfSec(last + 1)} in
 * session ms, {@code s.wallMsOf(last + 1)} in wall ms, and starts {@code rangeMinutes x 60,000} ms before it - or at
 * session ms 0, the session's first second, while the session holds less than the range: the columns then STRETCH
 * the data held over the full width, and the snapshot's range start says so (changes after the first live look);
 * {@link Strip#columnOf} and {@link Strip#columnStart} cut it into {@link Thresholds#STRIP_COLUMNS} columns. A second
 * belongs to every column its milliseconds touch and COUNTS when it is readable, no later than {@code last}, and not
 * flagged NOT_LOGGED_IN. A tick's gap belongs to the columns of the time it spanned, {@code atMs - gapMs .. atMs}; it
 * counts when it arrived before the range's end and is not masked (above). A column with no counting second is
 * {@link Strip#NONE} in all three lanes; a column whose counting seconds are all masked ({@link Flags#masked}) is
 * {@link Strip#NONE} in the frame rate and ticks lanes, so a load's frame-rate dip and a hop's tick gap are not
 * drawn. A column holds the WORST of its slice: the lowest {@code frames}, the largest gap, the highest FRESH RTT;
 * a -1 counts for nothing.
 *
 * <p><b>Reading the rings.</b> A second or a tick is read and then checked with {@code valid} again; a read that fails
 * the check is thrown away, as contract 3.3 asks. The event log's lists and counts are read in one hold of its lock.
 *
 * <p>Choice: a range of less than one minute is built as one minute, the shortest range the column map needs.
 * <p>Choice: with no complete second yet the range is the full chosen range (nothing is drawn); the stretch starts
 * with the first second.
 * <p>Choice: a column that holds both masked and unmasked seconds keeps the worst UNMASKED tick and frame rate; only a
 * column with no unmasked second is a gap. The ping lane keeps masked seconds: they are real readings, not a lag
 * the game made.
 * <p>Choice: a null settings view is built as {@code SettingsView.unknown(s.os)}, so nothing throws.
 * <p>Choice: the event log is read under its own lock, so the listed events, the counts and the total agree.
 * <p>Choice: the frame rate scale's "highest seen" is the highest column value drawn.
 * <p>Choice: the ping scale rounds {@code highest x STRIP_PING_PAD_PCT / 100} UP, so its top is at least that share.
 * <p>Choice: the Ping tile shows its RTT only while {@code conn} is NONE and the RTT is 0 or more; else "Nothing sent".
 * <p>Choice: a newest second with a negative frame count is a Frame rate dash with "Could not read it" (never written).
 */
public final class SnapshotBuilder implements SnapshotSource
{
	private static final int COLUMNS = Thresholds.STRIP_COLUMNS;
	private static final int LAST_COLUMN = COLUMNS - 1;
	/** The shortest range the column map takes, in minutes. */
	private static final int SHORTEST_RANGE_MINUTES = 1;
	private static final int SECONDS_PER_MINUTE = 60;
	private static final long MS_PER_MINUTE = 60_000L;
	private static final long MS_PER_SECOND = 1000L;
	/** From a second's first millisecond to its last. */
	private static final int LAST_MS_OF_SECOND = 999;
	private static final int PERCENT = 100;
	/** The Server ticks tile's small line: "plus-minus 13 ms" (contract 5.2). */
	private static final String PLUS_MINUS = "\u00b1";
	private static final String DASH = "-";

	public SnapshotBuilder()
	{
	}

	@Override
	public PanelSnapshot build(Session s, Verdict shown, int rangeMinutes, long nowSec, long wallMs,
		SettingsView settings, String footer)
	{
		final SettingsView view = settings != null ? settings : SettingsView.unknown(s.os);
		final int minutes = Math.max(SHORTEST_RANGE_MINUTES, rangeMinutes);
		final Pass p = new Pass(s, view, minutes, nowSec);
		p.readNewest();
		p.readSeconds();
		p.readTicks();
		final Tile[] tiles = {p.frameTile(), p.tickTile(), p.pingTile()};
		final Strip[] strips = {
			p.frameStrip(tiles[Lane.FRAME_RATE.ordinal()]),
			p.tickStrip(tiles[Lane.TICKS.ordinal()]),
			p.pingStrip(tiles[Lane.PING.ordinal()])};

		final List<LagEvent> rangeEvents;
		final List<LagEvent> sessionEvents = new ArrayList<>();
		final int[] counts = new int[Group.values().length];
		final int total;
		// Every method of EventLog holds its own lock (contract 3.4); holding it across the reads keeps them in step.
		synchronized (s.events)
		{
			rangeEvents = s.events.between(p.last + 1 - (long) minutes * SECONDS_PER_MINUTE, p.last);
			for (LagEvent e : s.events.copy())
			{
				if (!e.open)
				{
					sessionEvents.add(e);
				}
			}
			for (Group g : Group.values())
			{
				counts[g.ordinal()] = s.events.sessionCount(g);
			}
			total = s.events.sessionTotal();
		}

		final long rangeEndWallMs = s.wallMsOf(p.last + 1);
		return new PanelSnapshot(wallMs, s.zone, p.worldNow(), shown, tiles, minutes,
			rangeEndWallMs - (p.endMs - p.startMs), rangeEndWallMs, strips, rangeEvents, sessionEvents, counts, total,
			s.startWallMs, view, footer);
	}

	/** The work of one build: the reads of the newest second, the window's sums, and the columns of the three lanes. */
	private static final class Pass
	{
		private final Session s;
		private final SettingsView settings;
		private final long nowSec;
		/** The newest complete second; -1 = none yet. */
		private final long last;
		/** The range in session ms, {@code [startMs, endMs)}. */
		private final long startMs;
		private final long endMs;
		/** The first second of the range and of the window, none before the ring's tail. */
		private final long rangeFirst;
		private final long windowFirst;

		// The newest second.
		private boolean loggedIn;
		/** Its FOCUSED flag; the focused cap while there is no newest second (contract 3.7). */
		private boolean focused = true;
		private int frames;
		private int world;
		private int rttMs = -1;
		private NoData conn = NoData.NONE;

		// The window.
		private int worstFrameMs;
		private int ticks;
		private long gapSum;
		private int keptTicks;
		private long keptGapSum;
		private int widestRawMs;
		private int worstCorrectedMs = -1;
		private int longestGapMs;

		// The columns.
		private final boolean[] counting = new boolean[COLUMNS];
		/** A counting second of the column is not masked ({@link Flags#masked}): the ticks and frame rate may draw. */
		private final boolean[] unmasked = new boolean[COLUMNS];
		private final int[] fps = none();
		/** The FOCUSED flag of the second that gave each frame rate column its value. */
		private final boolean[] fpsFocused = new boolean[COLUMNS];
		private final int[] gaps = none();
		private final int[] corrected = none();
		private final int[] ping = none();

		Pass(Session s, SettingsView settings, int minutes, long nowSec)
		{
			this.s = s;
			this.settings = settings;
			this.nowSec = nowSec;
			last = s.lastSec(nowSec);
			endMs = s.msOfSec(last + 1);
			final long fullStart = endMs - minutes * MS_PER_MINUTE;
			// Less data than the range: the span is the data held, from the session's first second, stretched.
			startMs = last < 0 ? fullStart : Math.max(fullStart, s.msOfSec(0));
			final long tail = s.seconds.tail();
			rangeFirst = Math.max(last + 1 - (long) minutes * SECONDS_PER_MINUTE, tail);
			windowFirst = Math.max(last - Thresholds.WINDOW_S + 1, tail);
		}

		/** The newest second's columns, and whether the player is logged in. */
		void readNewest()
		{
			if (last < 0)
			{
				return;
			}
			final SecondRing r = s.seconds;
			final int state = r.state(last);
			final int flags = r.flags(last);
			final int f = r.frames(last);
			final int w = r.world(last);
			final int rtt = r.rttMs(last);
			final NoData c = r.conn(last);
			if (!r.valid(last))
			{
				return;
			}
			loggedIn = State.inGame(state);
			focused = Flags.has(flags, Flags.FOCUSED);
			frames = f;
			world = w;
			rttMs = rtt;
			conn = c;
		}

		/** One pass over the range's seconds: the window's worst frame, and the second lanes' columns. */
		void readSeconds()
		{
			final SecondRing r = s.seconds;
			for (long k = rangeFirst; k <= last; k++)
			{
				if (!r.valid(k))
				{
					continue;
				}
				final int flags = r.flags(k);
				final int f = r.frames(k);
				final int worst = r.worstFrameMs(k);
				final int rtt = r.rttMs(k);
				final int age = r.rttAgeS(k);
				if (!r.valid(k))
				{
					continue;
				}
				if (k >= windowFirst && worst > worstFrameMs)
				{
					worstFrameMs = worst;
				}
				if (Flags.has(flags, Flags.NOT_LOGGED_IN))
				{
					continue;
				}
				final long at = s.msOfSec(k);
				final int c0 = Math.max(0, Strip.columnOf(startMs, endMs, at));
				final int c1 = Math.min(LAST_COLUMN, Strip.columnOf(startMs, endMs, at + LAST_MS_OF_SECOND));
				final boolean foc = Flags.has(flags, Flags.FOCUSED);
				final boolean fresh = rtt >= 0 && age >= 0 && age <= Thresholds.RTT_STALE_S;
				final boolean clear = !Flags.masked(flags);
				for (int c = c0; c <= c1; c++)
				{
					counting[c] = true;
					unmasked[c] |= clear;
					// "<=": of several seconds with the lowest rate, the newest gives the column its focus (3.7).
					if (clear && f >= 0 && (fps[c] == Strip.NONE || f <= fps[c]))
					{
						fps[c] = f;
						fpsFocused[c] = foc;
					}
					if (fresh && rtt > ping[c])
					{
						ping[c] = rtt;
					}
				}
			}
		}

		/** One pass over the ticks, newest first: the window's tick sums, and the ticks lane's columns. */
		void readTicks()
		{
			if (last < 0)
			{
				return;
			}
			final TickRing t = s.ticks;
			final long windowStartMs = s.msOfSec(windowFirst);
			for (long seq = t.head(); seq >= 0; seq--)
			{
				if (!t.valid(seq))
				{
					break;
				}
				final long at = t.atMs(seq);
				final int gap = Math.max(0, t.gapMs(seq));
				final int flags = t.flags(seq);
				final int corr = t.corrected(seq);
				if (!t.valid(seq))
				{
					break;
				}
				if (at < startMs)
				{
					break;   // ticks arrive in order: this one and every older one ended before the range
				}
				if (at >= endMs || Flags.masked(flags) || secondMasked(Math.floorDiv(at, MS_PER_SECOND)))
				{
					continue;   // arrived at or after the range's end (not drawn yet), or a login, hop or load's tick
				}
				if (at >= windowStartMs)
				{
					ticks++;
					gapSum += gap;
					if (gap <= Thresholds.TICK_TRIM_MS)
					{
						keptTicks++;
						keptGapSum += gap;
					}
					widestRawMs = Math.max(widestRawMs, Math.abs(gap - Thresholds.TICK_MS));
					worstCorrectedMs = Math.max(worstCorrectedMs, corr);
					longestGapMs = Math.max(longestGapMs, gap);
				}
				final int c0 = Math.max(0, Strip.columnOf(startMs, endMs, at - gap));
				final int c1 = Strip.columnOf(startMs, endMs, at);
				for (int c = c0; c <= c1; c++)
				{
					if (gap > gaps[c])
					{
						gaps[c] = gap;
					}
					if (corr > corrected[c])
					{
						corrected[c] = corr;
					}
				}
			}
		}

		/** The second a tick arrived in is readable and masked by its flags ({@link Flags#masked}). */
		private boolean secondMasked(long sec)
		{
			final SecondRing r = s.seconds;
			if (!r.valid(sec))
			{
				return false;
			}
			final int flags = r.flags(sec);
			return r.valid(sec) && Flags.masked(flags);
		}

		Tile frameTile()
		{
			if (!loggedIn)
			{
				return dash(Lane.FRAME_RATE, NoData.NOT_LOGGED_IN);
			}
			if (s.framesStopped(nowSec))
			{
				return dash(Lane.FRAME_RATE, NoData.NO_FRAMES);
			}
			if (frames < 0)
			{
				return dash(Lane.FRAME_RATE, NoData.ERROR);
			}
			return new Tile(Lane.FRAME_RATE, fpsLevel(frames, focused), Fmt.thousands(frames) + " fps",
				"worst " + Fmt.thousands(worstFrameMs) + " ms", NoData.NONE);
		}

		/**
		 * The window's mean gap, rounded down, with the gaps over {@link Thresholds#TICK_TRIM_MS} left out (the mean of
		 * all of them when that leaves none); its largest RAW deviation, or the longest gap once the largest CORRECTED
		 * deviation reaches {@link Thresholds#TICK_BAD_MS}; the level on the largest corrected deviation.
		 */
		Tile tickTile()
		{
			if (!loggedIn)
			{
				return dash(Lane.TICKS, NoData.NOT_LOGGED_IN);
			}
			if (ticks == 0)
			{
				return dash(Lane.TICKS, NoData.NO_TICKS);
			}
			final long mean = keptTicks > 0 ? keptGapSum / keptTicks : gapSum / ticks;
			final String sub = worstCorrectedMs >= Thresholds.TICK_BAD_MS
				? "worst " + Fmt.thousands(longestGapMs)
				: PLUS_MINUS + Fmt.thousands(widestRawMs) + " ms";
			return new Tile(Lane.TICKS, Levels.tick(worstCorrectedMs), Fmt.thousands((int) mean) + " ms", sub,
				NoData.NONE);
		}

		/** The newest second's RTT while its {@code conn} is NONE, with this world's usual; else the reason. */
		Tile pingTile()
		{
			if (!loggedIn)
			{
				return dash(Lane.PING, NoData.NOT_LOGGED_IN);
			}
			if (conn != NoData.NONE || rttMs < 0)
			{
				return dash(Lane.PING, conn != NoData.NONE ? conn : NoData.STALE);
			}
			final int usual = s.rttUsual.median();
			final String sub = usual < 0 ? "" : "was " + Fmt.thousands(usual) + " ms";
			return new Tile(Lane.PING, Levels.ping(rttMs), Fmt.thousands(rttMs) + " ms", sub, NoData.NONE);
		}

		/** Frame rate: 0 .. max(STRIP_FPS_MAX, the newest second's cap, the highest column); each column by its cap. */
		Strip frameStrip(Tile tile)
		{
			final byte[] levels = noDataLevels();
			int highest = 0;
			for (int c = 0; c < COLUMNS; c++)
			{
				if (fps[c] != Strip.NONE)
				{
					levels[c] = ordinal(fpsLevel(fps[c], fpsFocused[c]));
					highest = Math.max(highest, fps[c]);
				}
			}
			final int top = Math.max(Thresholds.STRIP_FPS_MAX, Math.max(settings.capFps(focused), highest));
			return new Strip(Lane.FRAME_RATE, fps, levels, 0, top, valueOf(tile), levelOf(tile));
		}

		/** Ticks: STRIP_TICK_MIN_MS .. max(STRIP_TICK_MAX_MS, longest gap + STRIP_TICK_PAD_MS). */
		Strip tickStrip(Tile tile)
		{
			final byte[] levels = noDataLevels();
			long longest = -1;
			for (int c = 0; c < COLUMNS; c++)
			{
				if (!counting[c] || !unmasked[c])
				{
					gaps[c] = Strip.NONE;   // no counting second, or only masked ones: a gap in the line
				}
				else if (gaps[c] != Strip.NONE)
				{
					levels[c] = ordinal(Levels.tick(corrected[c]));
					longest = Math.max(longest, gaps[c]);
				}
			}
			final long top = longest < 0
				? Thresholds.STRIP_TICK_MAX_MS
				: Math.max(Thresholds.STRIP_TICK_MAX_MS, longest + Thresholds.STRIP_TICK_PAD_MS);
			return new Strip(Lane.TICKS, gaps, levels, Thresholds.STRIP_TICK_MIN_MS,
				(int) Math.min(Integer.MAX_VALUE, top), valueOf(tile), levelOf(tile));
		}

		/** Ping: 0 .. max(STRIP_PING_MAX_MS, the highest RTT x STRIP_PING_PAD_PCT / 100, rounded up). */
		Strip pingStrip(Tile tile)
		{
			final byte[] levels = noDataLevels();
			long highest = 0;
			for (int c = 0; c < COLUMNS; c++)
			{
				if (ping[c] != Strip.NONE)
				{
					levels[c] = ordinal(Levels.ping(ping[c]));
					highest = Math.max(highest, ping[c]);
				}
			}
			final long padded = (highest * Thresholds.STRIP_PING_PAD_PCT + PERCENT - 1) / PERCENT;
			return new Strip(Lane.PING, ping, levels, 0,
				(int) Math.min(Integer.MAX_VALUE, Math.max(Thresholds.STRIP_PING_MAX_MS, padded)), valueOf(tile),
				levelOf(tile));
		}

		/** The newest second's world while logged in; 0 while not (contract 3.8). */
		int worldNow()
		{
			return loggedIn ? world : 0;
		}

		/** The Frame rate level of {@code v} fps, under the cap in force for that focus (contract 3.7). */
		private Level fpsLevel(int v, boolean focus)
		{
			return Levels.fps(v, settings.capFps(focus), settings.capSource(focus).selfSet());
		}
	}

	private static Tile dash(Lane lane, NoData why)
	{
		return new Tile(lane, Level.NO_DATA, DASH, why.reason(), why);
	}

	/** A lane's value at the right: its tile's value, or "" when the tile shows "-". */
	private static String valueOf(Tile t)
	{
		return t.noData == NoData.NONE ? t.value : "";
	}

	private static Level levelOf(Tile t)
	{
		return t.noData == NoData.NONE ? t.level : Level.NO_DATA;
	}

	private static int[] none()
	{
		final int[] v = new int[COLUMNS];
		Arrays.fill(v, Strip.NONE);
		return v;
	}

	private static byte[] noDataLevels()
	{
		final byte[] v = new byte[COLUMNS];
		Arrays.fill(v, ordinal(Level.NO_DATA));
		return v;
	}

	private static byte ordinal(Level level)
	{
		return (byte) level.ordinal();
	}
}
