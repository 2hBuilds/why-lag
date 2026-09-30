package com.whylag.core;

/**
 * What the card says, immutable (contract 3.6, 6.4).
 *
 * <p>{@code level}: BAD for an event, WARN for a condition, OK for all clear, NO_DATA for the three states of the
 * card that are not verdicts (not logged in, warming up, no frames: contract 5.1), which carry the cause
 * {@link Cause#WARMING_UP} and the confidence {@link Confidence#CANT_TELL}, fix "" and ruledOut "", and differ by
 * their headline. {@code headline} is at most 32 characters, {@code proof} at most 64 (two lines of the card),
 * {@code fix} at most
 * 50 ("" = no fix line), {@code ruledOut} goes to the report and the tooltip only ("" = nothing to say). The four
 * texts are never null: a null given here is kept as "". {@code alsoA} / {@code alsoB} are the two candidates of a
 * NOT_SURE verdict, null otherwise.
 *
 * <p><b>What each kind carries</b> (contract 3.6; the card's when line prints it, 5.1):
 * <table>
 * <caption>whenWallMs, durationS, world and eventId by kind</caption>
 * <tr><th>Verdict</th><th>whenWallMs</th><th>durationS</th><th>world</th><th>eventId</th></tr>
 * <tr><td>an event's (X included)</td><td>the event's startWallMs</td><td>its lengthS()</td>
 * <td>the event's world (its first second)</td><td>its id</td></tr>
 * <tr><td>a condition's</td><td>wallMsOf the newest second at the first step of its current winning streak: when
 * it BEGAN winning</td><td>0</td><td>the newest second's</td><td>-1</td></tr>
 * <tr><td>V2, all clear</td><td>the startWallMs of the session's newest closed event; 0 = none</td><td>0</td>
 * <td>that event's world; 0 = none</td><td>-1</td></tr>
 * <tr><td>not logged in, warming up, no frames</td><td>0</td><td>0</td><td>0</td><td>-1</td></tr>
 * </table>
 * {@code whenWallMs} 0 means no when line, and so no confidence word, on the card.
 */
public final class Verdict
{
	public final Cause cause;
	public final Confidence confidence;
	public final Level level;
	public final String headline;
	public final String proof;
	public final String fix;
	public final String ruledOut;
	public final long whenWallMs;
	public final int durationS;
	public final int world;
	public final long eventId;
	public final Cause alsoA, alsoB;

	public Verdict(Cause cause, Confidence confidence, Level level, String headline, String proof, String fix,
		String ruledOut, long whenWallMs, int durationS, int world, long eventId, Cause alsoA, Cause alsoB)
	{
		this.cause = cause;
		this.confidence = confidence;
		this.level = level;
		this.headline = headline == null ? "" : headline;
		this.proof = proof == null ? "" : proof;
		this.fix = fix == null ? "" : fix;
		this.ruledOut = ruledOut == null ? "" : ruledOut;
		this.whenWallMs = whenWallMs;
		this.durationS = durationS;
		this.world = world;
		this.eventId = eventId;
		this.alsoA = alsoA;
		this.alsoB = alsoB;
	}

	/** True when {@code o} says the same thing: cause, event id, headline, proof and fix are equal. */
	public boolean sameAs(Verdict o)
	{
		return o != null
			&& cause == o.cause
			&& eventId == o.eventId
			&& headline.equals(o.headline)
			&& proof.equals(o.proof)
			&& fix.equals(o.fix);
	}
}
