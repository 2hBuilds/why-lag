package com.whylag.core;

import java.time.ZoneId;

/**
 * Everything the ten checks need to know, immutable once built (1.0.1, lot B): one set of plain numbers, flags
 * and words, read in ONE step of the sampler thread, so a check is a pure function of it ({@link Checks#run}) and a
 * test feeds it any state it likes. Nothing here is a name, a hash, a host, an address or a path: the world
 * NUMBER, the client's and Java's versions and the system's name are the most personal words it holds.
 *
 * <p><b>Who reads what.</b> {@link Builder#session} reads the rings and the event clocks of the session - the frame
 * and tick columns the client thread writes, the host columns the sampler writes - and is called on the sampler
 * thread only, the thread that owns every ring ({@code MinuteLine.of} and {@code SnapshotBuilder} do the same). The
 * plugin sets the rest by hand, from the fields it already keeps: the settings, the badge, the last step's timings
 * and the clock watch. The Swing thread never reads a ring and never builds one of these.
 *
 * <p>A fact with no value is -1 (or 0 for a count, "" for a word, false for a flag): every check then says "no data"
 * in its own words and none reads a ring itself.
 *
 * <p>
 * Choice: the frame rate and the frames of the last {@value #FRAMES_S} s come from the newest COMPLETE seconds, as
 * every "newest second" in the plugin does, and both read 0 when no frame has been drawn for certainly longer than
 * {@link Thresholds#NO_FRAMES_MS} ({@link Session#framesStopped}): the frame columns stop moving when the client
 * stops drawing, so the last second the ring holds is an old one, and reading it would pass a frozen client.
 * <br>
 * Choice: the last tick's age, the ticks of the last {@value #TICKS_S} s and their mean gap come from the CLOCK, not
 * from the newest second, so a world that stopped sending ticks reads as a growing age. The mean leaves out the
 * gaps of masked ticks (a login, a hop, a load) and any gap of 0 or less, a tick with no tick before it.
 * <br>
 * Choice: the ping state is the newest complete second's own {@link SecondRing#conn}: {@link Ping#READING} is a
 * fresh round trip time, {@link Ping#UNSUPPORTED}, {@link Ping#ERROR} and {@link Ping#STALE} are the probe's three
 * other answers, and every other reason (not logged in, not connected, still measuring) is {@link Ping#NONE}.
 * <br>
 * Choice: {@link #capKnown} is false for an unread renderer and for a V-Sync cap whose refresh rate could not be
 * read; "no cap" of an unlocked renderer is a known state, and the report already says "cap none" for it.
 */
public final class CheckFacts
{
	/** The state of the ping probe as a check sees it. */
	public enum Ping
	{
		/** A fresh round trip time was read. */
		READING,
		/** The game's socket or the TCP reading is not offered on this PC or by this client. */
		UNSUPPORTED,
		/** The probe tried and failed. */
		ERROR,
		/** Nothing has been sent lately, so the reading is old. */
		STALE,
		/** No reading and no reason of the three: not logged in, not connected, or still measuring. */
		NONE
	}

	/** The seconds the frame count looks back over. */
	public static final int FRAMES_S = 2;
	/** The seconds the tick count looks back over. */
	public static final int TICKS_S = 10;

	private static final long MS_PER_SECOND = 1000L;
	private static final ZoneId UTC = ZoneId.of("UTC");
	private static final String VSYNC_ON = "ON";
	private static final String VSYNC_ADAPTIVE = "ADAPTIVE";

	/** True when the newest complete second is in-game. */
	public final boolean loggedIn;
	/** That second's world; 0 = none known. */
	public final int world;
	/** Frames drawn in the last {@value #FRAMES_S} complete seconds; -1 = no second; 0 when frames have stopped. */
	public final int framesLast2s;
	/** Frames in the newest complete second; -1 = none; 0 when frames have stopped. */
	public final int fps;
	/** Milliseconds since the newest tick arrived; -1 = none yet. */
	public final long lastTickAgoMs;
	/** Ticks that arrived in the last {@value #TICKS_S} s. */
	public final int ticksLast10s;
	/** Their mean gap in ms, masked ticks left out; -1 = none to average. */
	public final int meanGapMs;
	public final Ping pingState;
	/** The newest second's round trip time in ms; -1 = none. */
	public final int rttMs;
	/** How old that reading is, in seconds; -1 = unknown. */
	public final int pingAgeS;
	/** Milliseconds since the last sampler step started; -1 = no step yet. */
	public final long lastStepAgoMs;
	/** What the last step's host and step calls took, in microseconds; -1 = not timed yet. */
	public final long stepCostUs;
	public final Renderer renderer;
	public final boolean capKnown;
	/** The infobox and the overlay are both registered with the client. */
	public final boolean badgeRegistered;
	/** The "Show" setting of the badge. */
	public final boolean badgeShow;
	/** "" = unknown. */
	public final String clientVersion;
	/** {@code java.version}; "" = unknown. */
	public final String javaVersion;
	/** {@code os.name}; "" = unknown. */
	public final String os;
	/** How far back the wall clock last jumped, in whole seconds; 0 = never. */
	public final int clockJumpedBackS;
	/** The wall time of that jump; 0 = never. */
	public final long clockJumpAtWallMs;
	/** The zone the clocks are written in; never null. */
	public final ZoneId zone;
	/** Seconds since the current login; 0 when not logged in. */
	public final long loggedInS;
	/** The card's state now; never null. */
	public final Answer cardState;

	private CheckFacts(Builder b)
	{
		loggedIn = b.loggedIn;
		world = b.world;
		framesLast2s = b.framesLast2s;
		fps = b.fps;
		lastTickAgoMs = b.lastTickAgoMs;
		ticksLast10s = b.ticksLast10s;
		meanGapMs = b.meanGapMs;
		pingState = b.pingState == null ? Ping.NONE : b.pingState;
		rttMs = b.rttMs;
		pingAgeS = b.pingAgeS;
		lastStepAgoMs = b.lastStepAgoMs;
		stepCostUs = b.stepCostUs;
		renderer = b.renderer == null ? Renderer.UNKNOWN : b.renderer;
		capKnown = b.capKnown;
		badgeRegistered = b.badgeRegistered;
		badgeShow = b.badgeShow;
		clientVersion = b.clientVersion == null ? "" : b.clientVersion;
		javaVersion = b.javaVersion == null ? "" : b.javaVersion;
		os = b.os == null ? "" : b.os;
		clockJumpedBackS = b.clockJumpedBackS;
		clockJumpAtWallMs = b.clockJumpAtWallMs;
		zone = b.zone == null ? UTC : b.zone;
		loggedInS = b.loggedInS;
		cardState = b.cardState == null ? Answer.MEASURING : b.cardState;
	}

	/**
	 * True when the settings hold a cap reading a verdict can use: the renderer is read, and a V-Sync cap has a
	 * refresh rate to stand on.
	 */
	public static boolean capKnown(SettingsView v)
	{
		if (v == null || v.renderer == Renderer.UNKNOWN)
		{
			return false;
		}
		final boolean ownCap = v.renderer == Renderer.GPU || v.renderer == Renderer.HD;
		final boolean vsync = VSYNC_ON.equals(v.vsyncMode) || VSYNC_ADAPTIVE.equals(v.vsyncMode);
		return !(ownCap && v.unlockFps && vsync && v.refreshHz <= 0);
	}

	/**
	 * The facts being gathered: every field starts as "nothing known" and {@link #build} freezes them. Not thread
	 * safe and not kept: the sampler thread makes one, fills it and builds.
	 */
	public static final class Builder
	{
		public boolean loggedIn;
		public int world;
		public int framesLast2s = -1;
		public int fps = -1;
		public long lastTickAgoMs = -1;
		public int ticksLast10s;
		public int meanGapMs = -1;
		public Ping pingState = Ping.NONE;
		public int rttMs = -1;
		public int pingAgeS = -1;
		public long lastStepAgoMs = -1;
		public long stepCostUs = -1;
		public Renderer renderer = Renderer.UNKNOWN;
		public boolean capKnown;
		public boolean badgeRegistered;
		public boolean badgeShow;
		public String clientVersion = "";
		public String javaVersion = "";
		public String os = "";
		public int clockJumpedBackS;
		public long clockJumpAtWallMs;
		public ZoneId zone = UTC;
		public long loggedInS;
		public Answer cardState = Answer.MEASURING;

		/** The settings' two facts: the renderer and whether the cap is known. */
		public Builder settings(SettingsView v)
		{
			renderer = v == null ? Renderer.UNKNOWN : v.renderer;
			capKnown = capKnown(v);
			return this;
		}

		/**
		 * The facts of the session at {@code nanos}: login, world, frames, ticks, ping and the time since the login.
		 * Sampler thread only; never throws for a session with no data.
		 *
		 * @param s     the session to read
		 * @param nanos the sampler's clock now, {@code System.nanoTime()} in a client
		 */
		public Builder session(Session s, long nanos)
		{
			final long nowSec = s.secOf(nanos);
			final long nowMs = s.msOf(nanos);
			final SecondRing r = s.seconds;
			final long last = s.lastSec(nowSec);
			readNewest(r, last);
			readFrames(s, r, last, nowSec);
			readTicks(s.ticks, nowMs);
			readPing(r, last);
			final long since = s.loggedInSinceSec();
			loggedInS = loggedIn && since != Session.NEVER ? Math.max(0, nowSec - since) : 0;
			return this;
		}

		/** Login, world and the newest second's frame rate. */
		private void readNewest(SecondRing r, long last)
		{
			loggedIn = false;
			world = 0;
			fps = -1;
			if (last < 0 || !r.valid(last))
			{
				return;
			}
			final int flags = r.flags(last);
			final int w = r.world(last);
			final int f = r.frames(last);
			if (!r.valid(last))
			{
				return;
			}
			loggedIn = !Flags.has(flags, Flags.NOT_LOGGED_IN);
			world = loggedIn ? Math.max(0, w) : 0;
			fps = Math.max(0, f);
		}

		/** The frames of the last {@value CheckFacts#FRAMES_S} complete seconds; 0 while the client has stopped drawing. */
		private void readFrames(Session s, SecondRing r, long last, long nowSec)
		{
			framesLast2s = -1;
			if (s.framesStopped(nowSec))
			{
				framesLast2s = 0;
				fps = 0;
				return;
			}
			int sum = 0;
			boolean any = false;
			for (long k = last; k > last - FRAMES_S && k >= 0; k--)
			{
				if (!r.valid(k))
				{
					continue;
				}
				final int frames = r.frames(k);
				if (r.valid(k))
				{
					any = true;
					sum += Math.max(0, frames);
				}
			}
			framesLast2s = any ? sum : -1;
		}

		/** The newest tick's age, and the count and mean gap of the ticks of the last {@value CheckFacts#TICKS_S} s. */
		private void readTicks(TickRing t, long nowMs)
		{
			lastTickAgoMs = -1;
			ticksLast10s = 0;
			meanGapMs = -1;
			final long head = t.head();
			final long from = nowMs - TICKS_S * MS_PER_SECOND;
			long gapSum = 0;
			int gaps = 0;
			for (long seq = head; seq >= 0; seq--)
			{
				if (!t.valid(seq))
				{
					break;
				}
				final long at = t.atMs(seq);
				final int gap = t.gapMs(seq);
				final int flags = t.flags(seq);
				if (!t.valid(seq))
				{
					break;
				}
				if (seq == head)
				{
					lastTickAgoMs = Math.max(0, nowMs - at);
				}
				if (at < from)
				{
					break;   // ticks arrive in order: this one and every older one is outside the window
				}
				ticksLast10s++;
				if (gap > 0 && !Flags.masked(flags))
				{
					gapSum += gap;
					gaps++;
				}
			}
			meanGapMs = gaps == 0 ? -1 : (int) ((gapSum + gaps / 2) / gaps);
		}

		/** The newest second's ping. */
		private void readPing(SecondRing r, long last)
		{
			pingState = Ping.NONE;
			rttMs = -1;
			pingAgeS = -1;
			if (last < 0 || !r.valid(last))
			{
				return;
			}
			final NoData conn = r.conn(last);
			final int rtt = r.rttMs(last);
			final int age = r.rttAgeS(last);
			if (r.valid(last))
			{
				pingState = pingOf(conn);
				rttMs = rtt;
				pingAgeS = age;
			}
		}

		private static Ping pingOf(NoData conn)
		{
			switch (conn)
			{
				case NONE:
					return Ping.READING;
				case UNSUPPORTED:
					return Ping.UNSUPPORTED;
				case ERROR:
					return Ping.ERROR;
				case STALE:
					return Ping.STALE;
				default:
					return Ping.NONE;
			}
		}

		/** The immutable facts. */
		public CheckFacts build()
		{
			return new CheckFacts(this);
		}
	}
}
