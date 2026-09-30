package com.whylag.core;

/**
 * The judge (contract 6.3 to 6.5): names the cause of one event, and says what the card shows now.
 *
 * <p>{@link #judgeEvent} is pure of the clock and of this object's state: it reads the rings over the span it is
 * given (never {@code LagEvent.open}, so an open event is judged as if it had closed now), scores the event rules
 * and applies the margin. {@link #current} holds the card's state - what is shown, since when, and which condition
 * has been winning since when - and is called once a second, on the sampler thread only.
 *
 * <p><b>What the card shows</b> (contract 6.5). (1) The three states - not logged in, warming up, no frames, tested
 * in that order - are entered and left at once. (2, 3) The newest closed event's verdict, from its close until
 * {@link Thresholds#EVENT_SHOW_S} after its end. (4) Then the winning condition, once it has won for
 * {@link Thresholds#CONDITION_HOLD_S} seconds in a row; else V2. (5) Any change but those of rule 1 waits until the
 * card has stood {@link Thresholds#VERDICT_HOLD_S} seconds. (6) A step of V2's counting text is not a change: it
 * shows at once and does not restart the hold. The SAME object is answered while nothing changed.
 *
 * <p>Choice: an event's verdict is shown while {@code nowSec - endSec < EVENT_SHOW_S}; the first V2 after it, at
 * {@code nowSec - endSec == EVENT_SHOW_S}, reads "No lag for 10 s.". With EVENT_SHOW_S equal to VERDICT_HOLD_S,
 * the card changes at the first step the hold allows: 15 s after the last bad tick (the event closes
 * {@code EVENT_QUIET_S} after it, the card names it from then, and shows it for VERDICT_HOLD_S).
 * <p>Choice: "the winner for CONDITION_HOLD_S seconds in a row" is counted on the clock: the condition shows at the
 * first step whose {@code nowSec} is CONDITION_HOLD_S or more past the step at which its streak began. The streak
 * is followed in every logged-in step (during the warm-up, a frame stop and an event's show time too). A step that
 * is not logged in (the seconds of a hop or a lost connection that are not in-game) neither follows nor ends it, so
 * a condition that still wins after a hop shows again at once, dated from when it began winning; a new login
 * ({@code Session.loggedInSinceSec()} changed) forgets it, as does a logged-in step that another condition, or
 * none, wins.
 * <p>Choice: a condition whose numbers changed (its proof, its world) while its cause stayed is a change like any
 * other: it respects the hold, so its text is refreshed at most once per VERDICT_HOLD_S.
 * <p>Choice: entering or leaving a state counts as a change of the card; the hold counts from it.
 * <p>Choice: a closed event in the log with no verdict attached is judged here, once, and that verdict is kept
 * for as long as it is the newest event.
 * <p>Choice: {@code wallMs} is not read: every clock a verdict carries comes from the session's own seconds.
 */
public final class VerdictEngine implements Judge
{
	/** What the card shows; null before the first call. */
	private Verdict shown;
	/** True when {@link #shown} is one of the three states of rule 1. */
	private boolean shownIsState;
	/** The clock second of the card's last change (a counting step is none). */
	private long changedAtSec;

	/** The condition that is winning, the step at which its streak began and the newest second of that step. */
	private Rule streakRule;
	private long streakSinceSec;
	private long streakFirstLastSec;
	/** {@code Session.loggedInSinceSec()} as the streak's last logged-in step read it: a change is a new login. */
	private long streakLogin = Session.NEVER;
	/** The winning condition's verdict once its streak is long enough; else null. */
	private Verdict conditionReady;

	/** The one verdict made here for a logged event that came without one. */
	private long judgedId = -1;
	private Verdict judged;

	public VerdictEngine()
	{
	}

	// ------------------------------------------------------------------ an event

	@Override
	public Verdict judgeEvent(Session s, LagEvent closed, SettingsView settings)
	{
		final Evidence e = EvidenceBuilder.forEvent(s, closed, settings);
		Rule top = null;
		Rule second = null;
		int topScore = 0;
		int secondScore = 0;
		for (Rule r : Rules.ALL)
		{
			if (r.kind != Rule.Kind.EVENT)
			{
				continue;
			}
			final int score = r.score(e);
			if (score > topScore)
			{
				second = top;
				secondScore = topScore;
				top = r;
				topScore = score;
			}
			else if (score > secondScore)
			{
				second = r;
				secondScore = score;
			}
		}

		if (top != null && topScore - secondScore >= Thresholds.SCORE_MARGIN)
		{
			final Confidence sure = top.confidence(e);
			return new Verdict(top.cause, sure, eventLevel(sure), Words.headline(top, e, s),
				Words.proof(top, e, settings), Words.fix(top, e, settings), Words.ruledOut(top.cause, e, settings),
				closed.startWallMs, closed.lengthS(), closed.world, closed.id, null, null);
		}
		// Can't tell: the top two, the lower rank first; rank never picks a winner. No rule above 0: no names.
		Cause a = null;
		Cause b = null;
		if (top != null && second != null)
		{
			final boolean topFirst = top.rank < second.rank;
			a = topFirst ? top.cause : second.cause;
			b = topFirst ? second.cause : top.cause;
		}
		final Rule x = Rules.X;
		return new Verdict(x.cause, x.ceiling, eventLevel(x.ceiling), Words.headline(x, e, s),
			Words.cantTellProof(e, a, b),
			Words.fix(x, e, settings), Words.ruledOut(x.cause, e, settings), closed.startWallMs, closed.lengthS(),
			closed.world, closed.id, a, b);
	}

	/**
	 * The level of an event's verdict: BAD, except that a verdict of {@link Confidence#CANT_TELL} is at most WARN - a
	 * lag the judge cannot name is never shown as a red lag (changes after the first live look, 2026-09-29). The ONE
	 * place this is decided: the card, the cells' culprit mark, the list row, the badge and the chat line all read the
	 * verdict's level.
	 */
	static Level eventLevel(Confidence c)
	{
		return c == Confidence.CANT_TELL ? Level.WARN : Level.BAD;
	}

	// ------------------------------------------------------------------ the card

	@Override
	public Verdict current(Session s, long nowSec, long wallMs, SettingsView settings)
	{
		final long last = s.lastSec(nowSec);
		final boolean loggedIn = last >= 0 && State.inGame(s.seconds.state(last));
		if (loggedIn)
		{
			// A step that is not logged in shows its state (rule 1) and leaves the streak as it was (class notes).
			followTheCondition(s, nowSec, last, settings);
		}

		final Verdict state = stateOf(s, nowSec, loggedIn);
		if (state != null)
		{
			if (shownIsState && same(shown, state))
			{
				return shown;
			}
			return show(state, true, nowSec);
		}

		final Verdict want = wanted(s, nowSec, settings);
		if (shown == null || shownIsState)
		{
			return show(want, false, nowSec);
		}
		if (want == shown || same(shown, want))
		{
			return shown;
		}
		if (shown.cause == Cause.ALL_CLEAR && want.cause == Cause.ALL_CLEAR)
		{
			// The counting text stepped: not a change of verdict, and the hold is not restarted.
			shown = want;
			return shown;
		}
		if (nowSec - changedAtSec >= Thresholds.VERDICT_HOLD_S)
		{
			return show(want, false, nowSec);
		}
		return shown;
	}

	/**
	 * The winning condition's verdict at this second with no hold and no streak, dated from the newest second; null
	 * when no condition scores. For tests of the condition rules; {@link #current} is what the card shows.
	 */
	Verdict conditionNow(Session s, long nowSec, SettingsView settings)
	{
		final long last = s.lastSec(nowSec);
		if (last < 0)
		{
			return null;
		}
		final Evidence e = EvidenceBuilder.forCondition(s, nowSec, settings);
		final Rule r = Rules.bestCondition(e);
		return r == null ? null : conditionVerdict(r, e, s, settings, last);
	}

	private Verdict show(Verdict v, boolean state, long nowSec)
	{
		shown = v;
		shownIsState = state;
		changedAtSec = nowSec;
		return v;
	}

	/** Rule 1: the state at this second, or null when the card shows a verdict of rules 2 to 4. */
	private static Verdict stateOf(Session s, long nowSec, boolean loggedIn)
	{
		if (!loggedIn)
		{
			return state(Answer.HEAD_NOT_LOGGED_IN, Words.notLoggedInProof());
		}
		if (!s.warm(nowSec))
		{
			return state(Answer.HEAD_MEASURING, Words.measuringProof(s.warmupLeftS(nowSec)));
		}
		if (s.framesStopped(nowSec))
		{
			return state(Answer.HEAD_WAITING, Words.waitingProof());
		}
		return null;
	}

	private static Verdict state(String headline, String proof)
	{
		return new Verdict(Cause.WARMING_UP, Confidence.CANT_TELL, Level.NO_DATA, headline, proof, "", "", 0, 0, 0,
			-1, null, null);
	}

	/** Rules 2 to 4: the newest event while it shows, else the condition that is ready, else V2. */
	private Verdict wanted(Session s, long nowSec, SettingsView settings)
	{
		final LagEvent newest = s.events.last();
		if (newest != null && nowSec - newest.endSec < Thresholds.EVENT_SHOW_S)
		{
			return verdictOf(s, newest, settings);
		}
		if (conditionReady != null)
		{
			return conditionReady;
		}
		final Rule v2 = Rules.V2;
		final long sinceEndS = newest == null ? -1 : nowSec - newest.endSec;
		return new Verdict(v2.cause, v2.ceiling, Level.OK, Words.headline(v2, null, s),
			Words.allClearProof(sinceEndS), "", "", newest == null ? 0 : newest.startWallMs, 0,
			newest == null ? 0 : newest.world, -1, null, null);
	}

	private Verdict verdictOf(Session s, LagEvent e, SettingsView settings)
	{
		if (e.verdict != null)
		{
			return e.verdict;
		}
		if (judged == null || judgedId != e.id)
		{
			judged = judgeEvent(s, e, settings);
			judgedId = e.id;
		}
		return judged;
	}

	/** Rule 4's streak, at a logged-in step: which condition wins at this step, and since when. */
	private void followTheCondition(Session s, long nowSec, long last, SettingsView settings)
	{
		final long login = s.loggedInSinceSec();
		if (login != streakLogin)
		{
			// A new login: the streak of the stretch before it is over. A hop or a lost connection keeps the login.
			streakLogin = login;
			streakRule = null;
			conditionReady = null;
		}
		final Evidence e = EvidenceBuilder.forCondition(s, nowSec, settings);
		final Rule winner = Rules.bestCondition(e);
		if (winner == null)
		{
			streakRule = null;
			conditionReady = null;
			return;
		}
		if (winner != streakRule)
		{
			streakRule = winner;
			streakSinceSec = nowSec;
			streakFirstLastSec = last;
		}
		conditionReady = nowSec - streakSinceSec >= Thresholds.CONDITION_HOLD_S
			? conditionVerdict(winner, e, s, settings, streakFirstLastSec) : null;
	}

	private static Verdict conditionVerdict(Rule r, Evidence e, Session s, SettingsView settings, long sinceSec)
	{
		return new Verdict(r.cause, r.confidence(e), Level.WARN, Words.headline(r, e, s),
			Words.proof(r, e, settings), Words.fix(r, e, settings), Words.ruledOut(r.cause, e, settings),
			s.wallMsOf(sinceSec), 0, e.world, -1, null, null);
	}

	/** True when the two say and carry the same: nothing the card, the report or the badge prints differs. */
	private static boolean same(Verdict a, Verdict b)
	{
		return a != null && a.sameAs(b)
			&& a.confidence == b.confidence
			&& a.level == b.level
			&& a.whenWallMs == b.whenWallMs
			&& a.world == b.world
			&& a.durationS == b.durationS
			&& a.ruledOut.equals(b.ruledOut);
	}
}
