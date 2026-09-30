package com.whylag.core;

/**
 * The game badge's state machine and its chat line (contract P2.2, P2.6; lot L10). Pure: it reads no clock, no ring
 * and no config. {@code WhyLagPlugin} calls {@link #update} once a step on the sampler thread, after
 * {@code LagEngine.step}, with the card's verdict, the open event with its provisional verdict (contract 3.6), the
 * session's newest closed event, the newest complete second ({@code Session.lastSec}) and the four settings of "Game
 * screen", and then takes the chat line ({@link #takeChatLine}). The overlay reads {@link #view()} inside
 * {@code render}: one volatile read, no allocation, any thread.
 *
 * <p>It keeps four things between calls: the view it answered last, the id of the last closed event it saw, the
 * {@code lastSec} of the last chat line it made, and the line waiting to be taken. {@code update} and
 * {@code takeChatLine} run on the sampler thread only; the view alone is shared, through one volatile field.
 *
 * <p><b>The view.</b> {@code update} first handles the chat line, then picks the view: the FIRST rule that holds
 * wins. {@code e} is the open event when there is one, else the last closed one, and {@code q = max(0, lastSec -
 * e.endSec)} counts the quiet seconds since its last trigger second.
 * <table>
 * <caption>The six rules (contract P2.2)</caption>
 * <tr><th>#</th><th>When</th><th>The view</th></tr>
 * <tr><td>1</td><td>{@code show} is false</td><td>{@link BadgeView#HIDDEN}</td></tr>
 * <tr><td>2</td><td>no card, or its answer is {@link Answer#NOT_LOGGED_IN}</td><td>HIDDEN</td></tr>
 * <tr><td>3</td><td>{@code e} exists and {@code q <= BADGE_HOLD_S}</td><td>a lag: the answer of its verdict at the
 * verdict's level ("Lag", BAD, while it has none); dimmed unless it is going on, {@code e.open && q <= 1}</td></tr>
 * <tr><td>4</td><td>the card's level is NO_DATA ("Measuring", "Waiting")</td><td>"When smooth: Hide": HIDDEN;
 * else the hollow ring: NO_DATA, no icon, the card's answer</td></tr>
 * <tr><td>5</td><td>the card is all clear (V2), or still holds a past lag ({@code eventId >= 0})</td><td>"When
 * smooth: Hide": HIDDEN; else smooth: the green circle, {@link Answer#SMOOTH}</td></tr>
 * <tr><td>6</td><td>anything else: a condition (F1, N1, F2, W1c, G2)</td><td>slow: the card's answer at the card's
 * level, not dimmed, drawn whatever "When smooth" says</td></tr>
 * </table>
 * A hidden view is always {@link BadgeView#HIDDEN} itself, whatever the style; every visible view carries the style
 * as given and both lines of its answer (the painter leaves out what the style does not show). {@code update}
 * answers the view it answered last, the same OBJECT, while the new one is {@link BadgeView#sameAs} it (contract 4,
 * T19), so the overlay paints a view once.
 *
 * <p><b>The tooltip's two lines</b>: a lag, the answer's one line and the event's numbers; slow, the answer's one
 * line and the card's proof; smooth on V2, "Smooth. " and the card's proof, and no second line; smooth on a past
 * lag, "Smooth." and "Last lag: " with the one line of the card's answer; the ring, the card's headline and proof;
 * HIDDEN, neither.
 *
 * <p><b>An event's numbers</b>, by the answer's icon, each through {@link Fmt#thousands}: WORLD "Ticks 1,240 ms,
 * ping 41 ms" ({@code worstTickGapMs}, {@code rttMs}); LINE "Ping 310 ms, ticks 1,240 ms" ({@code rttMaxMs},
 * {@code worstTickGapMs}); PC "Worst frame 480 ms, 50 fps" ({@code worstFrameMs}, {@code fps}); MEMORY "Pause 340
 * ms, memory 742 MB" ({@code gcPauseMs}, {@code heapUsedMb}); UNKNOWN "Ticks 1,240 ms, worst frame 170 ms"
 * ({@code worstTickGapMs}, {@code worstFrameMs}). The tick is the WORST gap, never the mean. A part with no number is
 * left out and what remains starts with a capital ("Ping 41 ms"); with both parts out the numbers are "".
 *
 * <p><b>The chat line</b> (contract P2.6). The last closed event is NEW when its id is not the one seen last (none
 * at first); a new one is marked as seen at once, whatever follows. It gets a line only when {@code chat} is on and
 * no line was made in the last {@link Thresholds#CHAT_GAP_S} seconds; otherwise it never gets one, so a bad minute
 * cannot flood the chat. The line is "[Why Lag] ", the one line of its answer, " (14 s).", then a space, its numbers
 * and "." when it has numbers: "[Why Lag] World lag - not you (14 s). Ticks 1,240 ms, ping 41 ms." An open event
 * never makes a line, and the view's rules never hold one back.
 *
 * <p>Choice: the 1 of rule 3's {@code q <= 1} is {@code GOING_ON_S} here, not a Thresholds entry: it only dims.
 * <p>Choice: an event's number below 0 is left out as -1 is (every LagEvent number is -1 for none); 0 prints.
 * <p>Choice: an answer whose icon is NONE has no numbers, "": P2.2's table of numbers has no row for it.
 * <p>Choice: an event with no verdict is "Lag - can't tell why" in the chat line as in rule 3, never "Measuring".
 * <p>Choice: every update replaces the waiting line with its own, null when it made none (P2.6: the last update's).
 * <p>Choice: the ring (rule 4) carries both lines of the card's answer, as every visible view; line 2 is "" there.
 * <p>Choice: the numbers are printed whole, never clamped: the tooltip and the chat line have no fixed width.
 */
public final class BadgeModel
{
	/** "No closed event seen yet": an event's id is 0 or more and never reused (contract 3.4, "The id"). */
	private static final long NONE_SEEN = -1;
	/** "No chat line made yet". */
	private static final long NO_LINE = Long.MIN_VALUE;
	/**
	 * A lag is going on while its last trigger second is at most this many seconds behind the newest second, so the
	 * badge dims from the second quiet second (contract P2.2, rule 3: {@code e.open && q <= 1}).
	 */
	private static final int GOING_ON_S = 1;
	private static final String CHAT_START = "[Why Lag] ";
	private static final String LAST_LAG = "Last lag: ";

	/** The view answered last; {@link BadgeView#HIDDEN} before the first update. Written on the sampler thread. */
	private volatile BadgeView view = BadgeView.HIDDEN;
	/** The id of the last closed event seen; {@link #NONE_SEEN} = none. Sampler thread only. */
	private long seenId = NONE_SEEN;
	/** The {@code lastSec} of the last chat line made; {@link #NO_LINE} = none. Sampler thread only. */
	private long lineSec = NO_LINE;
	/** The line the last update made, waiting to be taken; null = none. Sampler thread only. */
	private String line;

	public BadgeModel()
	{
	}

	/**
	 * One step, on the sampler thread, after {@code LagEngine.step}: handles the chat line first (contract P2.6),
	 * then picks the view by the six rules of the class notes and publishes it for {@link #view()}.
	 *
	 * @param card the card's verdict, {@code LagSource.verdict()}; null answers HIDDEN
	 * @param open the open event with its provisional verdict, {@code LagSource.openEvent()}; null = none open
	 * @param lastClosed the session's newest closed event, {@code session().events.last()}; null = none
	 * @param lastSec the newest complete second, {@code session().lastSec(nowSec)}
	 * @param show the setting "Show on game screen"
	 * @param style the setting "Style"; every visible view carries it as given
	 * @param whenSmooth the setting "When smooth": HIDE hides smooth and measuring, never a lag or a condition
	 * @param chat the setting "Chat line on a lag"
	 * @return the view now: the SAME object as last time while nothing it shows changed
	 */
	public BadgeView update(Verdict card, LagEvent open, LagEvent lastClosed, long lastSec, boolean show,
		BadgeStyle style, WhenSmooth whenSmooth, boolean chat)
	{
		line = chatLine(lastClosed, lastSec, chat);
		final BadgeView next = pick(card, open, lastClosed, lastSec, show, style, whenSmooth);
		final BadgeView shown = view;
		if (next.sameAs(shown))
		{
			return shown;
		}
		view = next;
		return next;
	}

	/** What the badge shows now: one volatile read, no allocation, any thread. HIDDEN before the first update. */
	public BadgeView view()
	{
		return view;
	}

	/** The line the last {@link #update} made, handed out ONCE; null = none. Sampler thread only. */
	public String takeChatLine()
	{
		final String taken = line;
		line = null;
		return taken;
	}

	/** P2.6: the line for {@code closed} when it is new, the setting is on and the chat gap is over; else null. */
	private String chatLine(LagEvent closed, long lastSec, boolean chat)
	{
		if (closed == null || closed.id == seenId)
		{
			return null;
		}
		seenId = closed.id;
		if (!chat || (lineSec != NO_LINE && lastSec - lineSec < Thresholds.CHAT_GAP_S))
		{
			return null;
		}
		lineSec = lastSec;
		final Answer answer = answerOf(closed);
		final String numbers = numbers(closed, answer.icon);
		final String text = CHAT_START + answer.oneLine + " (" + closed.lengthS() + " s).";
		return numbers.isEmpty() ? text : text + " " + numbers + ".";
	}

	/** P2.2: the first of the six rules that holds. */
	private static BadgeView pick(Verdict card, LagEvent open, LagEvent lastClosed, long lastSec, boolean show,
		BadgeStyle style, WhenSmooth whenSmooth)
	{
		if (!show || card == null || Answer.of(card) == Answer.NOT_LOGGED_IN)
		{
			return BadgeView.HIDDEN;
		}
		final LagEvent e = open != null ? open : lastClosed;
		if (e != null)
		{
			final long q = Math.max(0, lastSec - e.endSec);
			if (q <= Thresholds.BADGE_HOLD_S)
			{
				return lag(e, q, style);
			}
		}
		final boolean hide = whenSmooth == WhenSmooth.HIDE;
		if (card.level == Level.NO_DATA)
		{
			if (hide)
			{
				return BadgeView.HIDDEN;
			}
			final Answer answer = Answer.of(card);
			return new BadgeView(true, style, Level.NO_DATA, Icon.NONE, false, answer.line1, answer.line2,
				card.headline, card.proof);
		}
		if (card.cause == Cause.ALL_CLEAR || card.eventId >= 0)
		{
			if (hide)
			{
				return BadgeView.HIDDEN;
			}
			final Answer smooth = Answer.SMOOTH;
			return card.cause == Cause.ALL_CLEAR
				? new BadgeView(true, style, Level.OK, Icon.NONE, false, smooth.line1, smooth.line2,
					smooth.line1 + ". " + card.proof, "")
				: new BadgeView(true, style, Level.OK, Icon.NONE, false, smooth.line1, smooth.line2,
					smooth.line1 + ".", LAST_LAG + Answer.of(card).oneLine);
		}
		final Answer answer = Answer.of(card);
		return new BadgeView(true, style, card.level, answer.icon, false, answer.line1, answer.line2,
			answer.oneLine, card.proof);
	}

	/** Rule 3: a lag, going on or within its hold. */
	private static BadgeView lag(LagEvent e, long q, BadgeStyle style)
	{
		final Answer answer = answerOf(e);
		final Level level = e.verdict == null ? Level.BAD : e.verdict.level;
		final boolean goingOn = e.open && q <= GOING_ON_S;
		return new BadgeView(true, style, level, answer.icon, !goingOn, answer.line1, answer.line2,
			answer.oneLine, numbers(e, answer.icon));
	}

	/** The answer of an event: its verdict's, and "Lag" / "Can't tell why" while it has none (P2.2, rule 3). */
	private static Answer answerOf(LagEvent e)
	{
		return e.verdict == null ? Answer.of(Cause.NOT_SURE) : Answer.of(e.verdict);
	}

	/** The event's numbers by the answer's icon (P2.2); "" with no number, and for an answer with no icon. */
	private static String numbers(LagEvent e, Icon icon)
	{
		switch (icon)
		{
			case WORLD:
				return join(part("Ticks ", e.worstTickGapMs, " ms"), part("ping ", e.rttMs, " ms"));
			case LINE:
				return join(part("Ping ", e.rttMaxMs, " ms"), part("ticks ", e.worstTickGapMs, " ms"));
			case PC:
				return join(part("Worst frame ", e.worstFrameMs, " ms"), part("", e.fps, " fps"));
			case MEMORY:
				return join(part("Pause ", e.gcPauseMs, " ms"), part("memory ", e.heapUsedMb, " MB"));
			case UNKNOWN:
				return join(part("Ticks ", e.worstTickGapMs, " ms"), part("worst frame ", e.worstFrameMs, " ms"));
			default:
				return "";
		}
	}

	/** One part of the numbers, in its place in the sentence; "" when the number is missing (below 0). */
	private static String part(String word, int number, String unit)
	{
		return number < 0 ? "" : word + Fmt.thousands(number) + unit;
	}

	/** Both parts, or the one that remains with a capital, or "". */
	private static String join(String first, String second)
	{
		if (first.isEmpty())
		{
			return second.isEmpty() ? "" : Character.toUpperCase(second.charAt(0)) + second.substring(1);
		}
		return second.isEmpty() ? first : first + ", " + second;
	}
}
