package com.whylag.core;

/**
 * What the rules of contract 6.3 judge: the signals of ONE event's span, or of the window (the last
 * {@link Thresholds#WINDOW_S} seconds) for a condition. Built by {@link EvidenceBuilder} from the rings; read by
 * {@link Rules} and {@link Words}. A plain carrier: every field is public and set once by its builder.
 *
 * <p><b>No data.</b> An int of -1 means "no data". The two spike signals have three answers, {@link #YES},
 * {@link #NO} and {@link #NO_DATA}: they have no data when there is no fresh RTT to judge or no usual, whatever the
 * event's trigger bit says (contract 6.3). A need on a signal with no data fails; an exclude or a support on one is
 * skipped and lowers the rule's ceiling one step ({@link Rule}).
 *
 * <p>Choice: a fresh {@code Evidence} holds "no data" in every field (ints -1, counts 0, booleans false, the two
 * spikes {@link #NO_DATA}), so a builder that finds no readable second leaves a record on which no rule scores.
 * <p>Choice: {@code sent} and {@code resent} are held at {@link Integer#MAX_VALUE} when their sum is larger.
 * <p>Choice: beside the fields that contract 6.3 lists, the evidence carries four that only the words and one
 * condition need: {@link #event} (which kind it is), {@link #first} (X's words), {@link #capLabel} (F1's words) and
 * {@link #eventInWindow} (W1c's need "no event open or closed in the window").
 */
public final class Evidence
{
	/** The three answers of a signal that may have no data. */
	public static final int NO_DATA = -1, NO = 0, YES = 1;

	/** True for an event's evidence, false for a condition's (the window). */
	public boolean event;
	/** The event's first trigger (X's words); null for a condition. */
	public Trigger first;

	/** A second of the span (or an unmasked second of the window) carries the DISCONNECT flag. */
	public boolean disconnect;
	/** D1: the FIRST second of the span with the DISCONNECT flag; -1 = none. */
	public long disconnectSec = -1;
	/** D1: the second of the newest tick before the end of {@link #disconnectSec}, masked or not; -1 = none. */
	public long lastTickSec = -1;

	/** The worst frame's length; -1 = no second to read. */
	public int frameGapMs = -1;
	/** {@code settings.frameGapLimitMs(f)}, f the focus of the second judged (contract 3.7); -1 = no second. */
	public int frameLimitMs = -1;
	/** The longest KNOWN pause touching the worst frame's interval; 0 = measured, none; -1 = cannot be known. */
	public int gcMs = -1;
	/** The share of the worst frame that one known pause covered, 0..100; -1 = cannot be known. */
	public int gcCoverPct = -1;
	/** The ms of the worst frame that one known pause covered; -1 = cannot be known. */
	public int gcOverlapMs = -1;
	/** An inferred collection (no length) lies in the worst frame's interval. */
	public boolean gcInferred;
	/** Events: the summed loading time of every LOADING run with a second in startSec - 1 .. endSec. */
	public int loadMs;
	/** Events: the LOADING runs of the last {@link Thresholds#LOADS_LOOK_S} seconds, back from the event's end. */
	public int loads;
	/** The busy share of the worst frame, per mille; -1 = no data. */
	public int busyPm = -1;
	/** The cap in force in the second judged; 0 = none known. */
	public int capFps;
	/** {@code 1000 / capFps}; 0 = no cap. */
	public int capIntervalMs;
	/** The cap was set by the player (FPS Control, a renderer's target or V-Sync). */
	public boolean capSelfSet;
	/** The cap paces frames by waiting. */
	public boolean capWaits;
	/** Who set the cap, as F1's words print it; "" = none. */
	public String capLabel = "";

	/** The median frames a second; -1 = no second. */
	public int fps = -1;
	/** Conditions: slow seconds in a row up to the newest second. */
	public int lowFpsS;
	/** Conditions: the median rate of the slow run; -1 when {@link #lowFpsS} is 0. */
	public int lowFps = -1;
	/** Conditions: capped seconds in a row up to the newest second. */
	public int capHeldS;
	/** {@code ceil(1000 / fps)}; 1000 when fps is 0; -1 when fps is -1. */
	public int frameMedianMs = -1;
	/** {@code frameGapMs < FRAME_CLEAN_MS}; false with no frame data. */
	public boolean framesClean;

	/** A second of the span (or window) has a fresh RTT. */
	public boolean rttKnown;
	/** Median, lowest and highest fresh RTT; -1 = none. */
	public int rtt = -1, rttMin = -1, rttMax = -1;
	/** Events: the usual as it stood when the event opened. Conditions: this world's usual now. -1 = none. */
	public int rttUsual = -1;
	/** {@link #YES}, {@link #NO} or {@link #NO_DATA}. */
	public int rttSpike = NO_DATA;

	/** Events: sent and re-sent units over the span and {@link Thresholds#RESENT_LOOK_S} either side. */
	public int sent, resent;
	/** Events: the RESENT trigger fired. */
	public boolean resentCounts;
	/** Events: the NO_TICK trigger fired - a real stop of {@link Thresholds#NO_TICK_MS} or more (N6 needs it). */
	public boolean noTick;
	/** Events: {@code resent x 1000 / sent}, at most 1000, 0 when nothing was sent. */
	public int resentPm;

	/** Events: the span's ticks, and those off by TICK_OFF_MS or more, late and early. */
	public int ticks, tickLate, tickEarly;
	/** Events: the median of gap - frame time over the span's ticks; -1 = none. */
	public int tickMedianMs = -1;
	/** Events: the longest gap of the span's ticks; -1 = none. */
	public int tickWorstMs = -1;
	/** Events: the longest stretch with no tick inside the span; -1 = no tick at all before its end. */
	public int noTickMs = -1;
	/** Events, X's words: how far off the ticks ran; -1 = nothing to say. */
	public int tickOffMs = -1;
	/** Conditions: the median RAW gap of the window's ticks; -1 = none. */
	public int tickWindowMedianMs = -1;
	/** Conditions: the number of the window's ticks. */
	public int ticksInWindow;
	/** Conditions: an event, open or closed, overlaps the window; an open one reaches the newest second. */
	public boolean eventInWindow;

	/** The highest used heap; -1 = none. */
	public int heapUsedMb = -1;
	/** The settings' heap limit; -1 when it is 0 or less (unknown). */
	public int heapMaxMb = -1;
	/** Events: the event's length in seconds; 0 for a condition. */
	public int durationS;
	/** Events: the world of the first second. Conditions: the newest second's. 0 = none. */
	public int world;

	/** D1: median and highest fresh RTT of the D1_LOOK_S seconds that end at {@link #lastTickSec}; -1 = none. */
	public int beforeRtt = -1, beforeRttMax = -1;
	/** D1: the spike line on {@link #beforeRttMax} against {@link #rttUsual}; three answers. */
	public int beforeRttSpike = NO_DATA;
	/** D1: the re-sent share of those seconds, per mille; -1 = no second to read. */
	public int beforeResentPm = -1;
}
