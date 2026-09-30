package com.whylag.core;

import java.util.concurrent.CopyOnWriteArrayList;

/**
 * The facade of the recording (contract 3.10 and section 7, L2): the plugin hands it frames, ticks, game states
 * and host samples, and once a second a {@link #step}; it answers the verdict, the open event and the
 * panel's snapshot ({@link LagSource}). It joins the {@link Detector}, the {@link Judge} and the
 * {@link SnapshotSource} it was given and names no class of theirs.
 *
 * <p><b>Threads.</b> {@link #frame}, {@link #tick}, {@link #gameState}, {@link #world} and {@link #scene} run on
 * the client thread and take NO lock (T4); {@link #focus} is a volatile write from any thread; {@link #host} and
 * {@link #step} run on the sampler thread.
 * {@link #step} and {@link #snapshot} share one lock, and nothing else takes it. Recording never waits for the
 * panel: nothing here knows whether one exists (T10).
 *
 * <p><b>One writer.</b> This class alone writes {@link EventLog} ({@code add} on opened, {@code replace} on closed,
 * after the verdict is attached) and {@link Session#loggedInSince}. It touches no {@link Usual}.
 *
 * <p><b>The open event</b> is judged in every step as if it closed now and published for {@link #openEvent()}; that
 * provisional verdict goes nowhere else: not into the log, not to the listeners, not into {@link #verdict()}.
 *
 * <p><b>Host seconds.</b> A sample taken at {@code nanos} fills the second BEFORE the one it was taken in. A second
 * sample inside one second writes nothing and does not run the loss calculator; a hole of at most
 * {@link Thresholds#HOST_FILL_S} seconds takes the readings of the sample that closes it, with nothing sent; the
 * seconds of a longer hole are fillers ({@link NoData#STALE}, every number -1, nothing sent).
 *
 * <p>
 * Choice: the verdict before the first step (ruling R1) is asked of the judge ONCE, in the constructor, at
 * second 0 and the session's start as wall time; a judge that answers null there is replaced by the "Still
 * measuring" state.
 * <br>
 * Choice: a judge that answers null in a step leaves the verdict as it is.
 * <br>
 * Choice: {@link #gameState} asks the loss calculator for a reset on HOPPING (a hop), on LOGGING_IN (a login) and
 * on the first code after CONNECTION_LOST that is a different code (the END of a lost connection: a reconnect is a
 * new socket, whether or not it passes through LOGGING_IN); never when a connection is lost, and never on
 * LOGIN_SCREEN, LOADING or LOGGED_IN as such. So the second in which a connection dropped keeps its sent, re-sent
 * and RTT figures, which the re-send trigger and D1 read, and a map load keeps its window.
 * <br>
 * Choice: a tick carries the RTT of the newest host sample as written, its number kept when it is stale.
 * <br>
 * Choice: the footer of a snapshot is {@link SelfTimer#footer()} while the timer is on, else "".
 * <br>
 * Choice: a closed event that the log no longer holds (replace answers false) is added.
 * <br>
 * Choice: a listener that throws is skipped for that change; the others are still told.
 * <br>
 * Choice: the verdict listeners are told on the sampler thread INSIDE the step lock, in the order they were added.
 * <br>
 * Choice: {@link #addVerdictListener} ignores null and a listener already added.
 * <br>
 * Choice: {@link #snapshot} before the first step builds with {@code SettingsView.unknown(Os.OTHER)}, the
 * settings the constructor judged with (ruling R1).
 */
public final class LagEngine implements LagSource
{
	private final Session session;
	private final Detector detector;
	private final Judge judge;
	private final SnapshotSource snapshots;

	private final SelfTimer selfTimer = new SelfTimer();
	private final FrameSampler frames;
	private final TickSampler ticks;
	private final LossCalculator loss;
	private final CopyOnWriteArrayList<VerdictListener> listeners = new CopyOnWriteArrayList<>();

	/** {@link #step} and {@link #snapshot} share it; the client thread never takes it. */
	private final Object stepLock = new Object();
	private final Log log = new Log();

	/** The host carrier and the filler of a long hole: sampler thread only. */
	private final HostSecond host = new HostSecond();
	private final HostSecond filler = new HostSecond();

	/** The newest code handed to {@link #gameState} that was not LOGGING_IN. Client thread only. */
	private int codeBefore = State.OTHER;
	/** The newest code handed to {@link #gameState}, LOGGING_IN included. Client thread only. */
	private int lastCode = State.OTHER;

	private volatile Verdict verdict;
	private volatile LagEvent openEvent;
	private volatile SettingsView lastSettings;
	private volatile long nowSec;
	private volatile int latestRttMs = -1;

	public LagEngine(Session s, Detector detector, Judge judge, SnapshotSource snapshots)
	{
		this.session = s;
		this.detector = detector;
		this.judge = judge;
		this.snapshots = snapshots;
		this.frames = new FrameSampler(s, selfTimer);
		this.ticks = new TickSampler(s, frames, selfTimer);
		this.loss = new LossCalculator(s.os);
		final Verdict first = judge.current(s, 0, s.startWallMs, unknownSettings());
		this.verdict = first != null
			? first
			: new Verdict(Cause.WARMING_UP, Confidence.CANT_TELL, Level.NO_DATA, Answer.HEAD_MEASURING, "", "", "",
				0, 0, 0, -1, null, null);
	}

	// ---------------------------------------------------------------- the client thread

	/** One frame. Client thread; no lock, no allocation. */
	public void frame(long nanos, int gameCycle)
	{
		frames.frame(nanos, gameCycle);
	}

	/** One game tick. Client thread; no lock, no allocation. */
	public void tick(long nanos, int gameCycle)
	{
		ticks.tick(nanos, gameCycle, latestRttMs);
	}

	/**
	 * The game state changed. Client thread. Also writes where the warm-up counts from (contract 3.5): an in-game
	 * code starts it when no login has been seen yet, or when the code before was not in-game, not HOPPING and not
	 * CONNECTION_LOST; "the code before" looks through LOGGING_IN. A hop, a login and the end of a lost connection
	 * ask the loss calculator for a reset (the class notes).
	 */
	public void gameState(long nanos, int stateCode)
	{
		frames.state(nanos, stateCode);
		ticks.state(nanos, stateCode);
		final boolean lostConnectionEnds = lastCode == State.CONNECTION_LOST && stateCode != State.CONNECTION_LOST;
		if (stateCode == State.HOPPING || stateCode == State.LOGGING_IN || lostConnectionEnds)
		{
			loss.requestReset();
		}
		lastCode = stateCode;
		if (State.inGame(stateCode))
		{
			final boolean carriesOn = State.inGame(codeBefore) || codeBefore == State.HOPPING
				|| codeBefore == State.CONNECTION_LOST;
			if (session.loggedInSinceSec() == Session.NEVER || !carriesOn)
			{
				session.loggedInSince(session.secOf(nanos));
			}
		}
		if (stateCode != State.LOGGING_IN)
		{
			codeBefore = stateCode;
		}
	}

	/** The world changed. Client thread. */
	public void world(long nanos, int world)
	{
		frames.world(world);
		loss.requestReset();
	}

	/** The scene counts and the region. Client thread. */
	public void scene(int players, int npcs, int region)
	{
		frames.scene(players, npcs, region);
	}

	/** The window's focus. Any thread: one volatile write. */
	public void focus(boolean focused)
	{
		frames.focus(focused);
	}

	// ---------------------------------------------------------------- the sampler thread

	/** One host sample, taken at {@code nanos}: it fills the second before the one it was taken in. */
	public void host(long nanos, long rttMicros, long sent, long resent, NoData conn)
	{
		final SecondRing ring = session.seconds;
		final long sec = session.secOf(nanos) - 1;
		final long head = ring.hostHead();
		if (sec < 0 || sec <= head)
		{
			return;
		}
		final HostSecond h = host;
		h.clear();
		loss.sample(nanos, rttMicros, sent, resent, conn, h);
		latestRttMs = h.rttMs;

		final long hole = sec - head - 1;
		if (hole > 0)
		{
			if (hole <= Thresholds.HOST_FILL_S)
			{
				final int sentUnits = h.sentUnits;
				final int resentUnits = h.resentUnits;
				h.sentUnits = 0;
				h.resentUnits = 0;
				for (long k = head + 1; k < sec; k++)
				{
					ring.putHost(k, h);
				}
				h.sentUnits = sentUnits;
				h.resentUnits = resentUnits;
			}
			else
			{
				final HostSecond f = filler;
				f.clear();
				f.conn = NoData.STALE;
				for (long k = head + 1; k < sec; k++)
				{
					ring.putHost(k, f);
				}
			}
		}
		ring.putHost(sec, h);
	}

	/** The one-second step: the detector, the log, the open event, the verdict. Sampler thread. */
	public void step(long nanos, long wallMs, SettingsView settings)
	{
		final boolean timed = selfTimer.on();
		final long t0 = timed ? System.nanoTime() : 0L;
		Verdict changed = null;
		synchronized (stepLock)
		{
			lastSettings = settings;
			final long now = session.secOf(nanos);
			nowSec = now;

			log.settings = settings;
			detector.advance(session, session.seconds.head(), settings, log);

			final LagEvent open = detector.open();
			openEvent = open == null ? null : open.withVerdict(judge.judgeEvent(session, open, settings));

			final Verdict v = judge.current(session, now, wallMs, settings);
			if (v != null && v != verdict)
			{
				verdict = v;
				changed = v;
			}
			if (changed != null)
			{
				for (VerdictListener l : listeners)
				{
					try
					{
						l.verdictChanged(changed);
					}
					catch (RuntimeException e)
					{
						// one listener's fault is not the others'
					}
				}
			}
		}
		if (timed)
		{
			selfTimer.add(SelfTimer.STEP, System.nanoTime() - t0);
		}
	}

	// ---------------------------------------------------------------- LagSource

	@Override
	public Verdict verdict()
	{
		return verdict;
	}

	@Override
	public void addVerdictListener(VerdictListener l)
	{
		if (l != null)
		{
			listeners.addIfAbsent(l);
		}
	}

	@Override
	public void removeVerdictListener(VerdictListener l)
	{
		listeners.remove(l);
	}

	@Override
	public Session session()
	{
		return session;
	}

	/** Builds with the {@code nowSec} of the last step, 0 before the first. Any thread but the client thread. */
	@Override
	public PanelSnapshot snapshot(int rangeMinutes, long wallMs)
	{
		synchronized (stepLock)
		{
			final SettingsView given = lastSettings;
			final SettingsView settings = given != null ? given : unknownSettings();
			final String footer = selfTimer.on() ? selfTimer.footer() : "";
			return snapshots.build(session, verdict, rangeMinutes, nowSec, wallMs, settings, footer);
		}
	}

	@Override
	public SelfTimer selfTimer()
	{
		return selfTimer;
	}

	@Override
	public LagEvent openEvent()
	{
		return openEvent;
	}

	// ---------------------------------------------------------------- helpers

	/** The settings of a session that has not been stepped yet (ruling R1). */
	private static SettingsView unknownSettings()
	{
		return SettingsView.unknown(Os.OTHER);
	}

	/** What the detector tells the engine during a step: the log's one writer. Used under the step lock only. */
	private final class Log implements DetectorListener
	{
		private SettingsView settings;

		@Override
		public void opened(LagEvent e)
		{
			session.events.add(e);
		}

		@Override
		public void closed(LagEvent e)
		{
			final LagEvent judged = e.withVerdict(judge.judgeEvent(session, e, settings));
			if (!session.events.replace(judged))
			{
				session.events.add(judged);
			}
		}
	}
}
