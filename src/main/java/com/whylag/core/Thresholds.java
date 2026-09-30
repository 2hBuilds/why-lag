package com.whylag.core;

/**
 * Every threshold of 2h Why Lag, in one place (contract section 3.1).
 *
 * <p>Each constant says what it means and which experiment of the M0 probe sets it ("E1" .. "E14"); "set by
 * design" means no experiment moves it. No other class under {@code src/main/java/com/whylag} may hold a numeric
 * threshold literal: a rule that needs a line reads it here, so a live measurement changes one number and nothing
 * else. {@code ThresholdsTest} pins every starting value by literal.
 *
 * <p>These are NOT thresholds and stay where they are used: sizes and pixels in {@code ui} and in the badge's
 * painter (layout, contract 5 and P2.4); the panel's own timing and cap (the 2 s that "Copied" shows, the "99+" of a
 * count over 99, in {@code ui}); the length limits and the largest printed values of the words (in {@code Words})
 * and of the cells (in {@link Cells}: 9,999); the rounding of three fillers of the words (in {@code Words}:
 * W1's "{900}+" is rounded down to a multiple of 10, the share "{2} in 100" is {@code max(1, (pm + 5) / 10)}, V2's
 * minutes are at least 1); the rank, base and ceiling of each rule (in {@code Rules}); unit conversions (1000, 100,
 * 60); the ranges of the ring columns (the clamp table of contract 3.3); the two maxima of the usuals (2000 and 1000,
 * sizes of {@link Session}'s arrays); the clock arithmetic of {@link Session#framesStopped} (its 2 s); RuneLite's own
 * defaults that {@code SettingsReader} falls back to when a key is missing (contract 7, L7); and the wiring numbers
 * of {@code WhyLagPlugin} (the 1 s period of the sampler task, the subscriber priority 100, the navigation button's
 * priority 7).
 *
 * <p>There is no {@code DISCONNECT_TAIL_S} (removed 2026-09-29, gap G2 of contract section 11): an event that the
 * detector makes always holds its disconnect inside its own span, so nothing reads a tail.
 */
public final class Thresholds
{
	/** Readable seconds of history; the ring holds one slot more (section 3.3). Set by design. */
	public static final int SECONDS = 3600;
	/** Readable ticks; the ring holds one slot more. Set by design. */
	public static final int TICKS = 6000;
	/** Events the log holds; older ones are dropped, their session counts kept. Set by design. */
	public static final int EVENTS = 500;
	/** One server tick, in ms. Set by design. */
	public static final int TICK_MS = 600;
	/** "Still measuring" lasts this many seconds after a login: none. Set from the live recording of 2026-09-29. */
	public static final int WARMUP_S = 0;
	/** The look-back of every tile's small line and of every condition: "the last 60 s". Set by design. */
	public static final int WINDOW_S = 60;
	/** Logged in and no frame for LONGER than this, in ms: "No frames drawn", "Waiting for the game". Set by E12. */
	public static final int NO_FRAMES_MS = 2000;
	/** The client's own cap, in frames a second: one frame a 20 ms client cycle. Set by design. */
	public static final int CLIENT_CAP_FPS = 50;
	/** The scene counts are handed to the engine once every this many ticks. Set by design. */
	public static final int SCENE_EVERY_TICKS = 5;
	/** Samples a usual needs before it has a median. Set by E4. */
	public static final int USUAL_MIN_SAMPLES = 30;
	/** A frame this long or longer is a slow frame. Set by E1. */
	public static final int SLOW_FRAME_MS = 50;
	/** The frame gap trigger's floor, in ms. Set by E5 and E9. */
	public static final int FRAME_GAP_MS = 200;
	/** The frame gap trigger is at least this % of the cap interval (C9). Set by E9. */
	public static final int FRAME_GAP_CAP_PCT = 150;
	/** A worst frame under this is "frames clean". Set by E5. */
	public static final int FRAME_CLEAN_MS = 100;
	/** N2 needs a median frame under this (C5). Set by E5. */
	public static final int LOW_FPS_FRAME_MS = 25;
	/** Frame rate tile: under this is WARN. Set by E1. */
	public static final int FPS_WARN = 40;
	/** Frame rate tile: under this is BAD. Set by E1. */
	public static final int FPS_BAD = 25;
	/** A frame rate within this of a cap counts as capped. Set by E9. */
	public static final int CAP_MATCH_FPS = 2;
	/** Tick trigger, on the deviation less the frame interval (C5). Set from the live recording of 2026-09-29. */
	public static final int TICK_OFF_MS = 250;
	/** Ticks tile: a corrected deviation of this or more is WARN. Set from the live recording of 2026-09-29. */
	public static final int TICK_WARN_MS = 200;
	/** Ticks tile: a corrected deviation of this or more is BAD. Set from the live recording of 2026-09-29. */
	public static final int TICK_BAD_MS = 400;
	/** Tick gaps over this are left out of the tile's mean. Set by E7. */
	public static final int TICK_TRIM_MS = 900;
	/** No tick for this long while frames run fires NO_TICK. Set by E10. */
	public static final int NO_TICK_MS = 1200;
	/** W1 needs this many ticks in the event. Set by E7. */
	public static final int SLOW_WORLD_MIN_TICKS = 5;
	/** W1 event: the median corrected gap is at or over this. Set by E7. */
	public static final int SLOW_WORLD_MEDIAN_MS = 660;
	/** W1 condition: the median gap of the {@link #WINDOW_S} window is at or over this. Set by E7. */
	public static final int SLOW_WORLD_WINDOW_MS = 620;
	/** W1 condition: at least this many ticks in the {@link #WINDOW_S} window. Set by E7. */
	public static final int SLOW_WORLD_WINDOW_TICKS = 60;
	/** W1 needs the median ping within this many ms of the usual. Set by E4 and E7. */
	public static final int SLOW_WORLD_PING_MS = 20;
	/** W1's support: the event lasted this many seconds or longer. Set by E7. */
	public static final int SLOW_WORLD_LONG_S = 10;
	/** Ticks masked after a login, a hop or a lost connection ends. Set by E14. */
	public static final int LOGIN_MASK_TICKS = 15;
	/** Seconds masked after a run of LOADING ends. Set by E14. */
	public static final int LOAD_TAIL_S = 1;
	/** D1 judges this many seconds that end at the LAST tick. Set by E10. */
	public static final int D1_LOOK_S = 10;
	/** A cap's pacing explains a frame gap up to this many cap intervals (S3's exclude). Set by E9. */
	public static final int CAP_WAIT_FACTOR = 2;
	/**
	 * "Ready in", the warm-up's counting text, steps by this many seconds; "No lag for" steps by it under a minute,
	 * then counts minutes. Set by design.
	 */
	public static final int TEXT_STEP_S = 10;
	/** The refresh rate of the screen is read again this often, in seconds. Set by design. */
	public static final int REFRESH_REREAD_S = 10;
	/** RTT spike: at least this % of the usual (and RTT_SPIKE_ADD_MS over it). Set by E4. */
	public static final int RTT_SPIKE_PCT = 200;
	/** RTT spike: at least this many ms over the usual (and RTT_SPIKE_PCT of it). Set by E4. */
	public static final int RTT_SPIKE_ADD_MS = 50;
	/** Whether an RTT spike alone may OPEN an event (C21). Set by E4. */
	public static final boolean RTT_SPIKE_OPENS = false;
	/** An RTT older than this many seconds is "no data" (C21). Set by E4 and E10. */
	public static final int RTT_STALE_S = 5;
	/** N2's support: the highest RTT is at least this many times the usual. Set by E4. */
	public static final int RTT_SWING_FACTOR = 3;
	/** A jump in sent BYTES this large over the quiet median voids an RTT spike in that second. Set by E4. */
	public static final int CLICK_SENT_BYTES = 600;
	/** A jump in sent SEGMENTS this large over the quiet median voids an RTT spike in that second. Set by E4. */
	public static final int CLICK_SENT_UNITS = 4;
	/** The quiet seconds whose median sent is the base of that jump. Set by E4. */
	public static final int CLICK_BASE_S = 10;
	/** Ping tile: this RTT or more is WARN. Set by E4. */
	public static final int PING_WARN_MS = 80;
	/** Ping tile: this RTT or more is BAD. Set by E4. */
	public static final int PING_BAD_MS = 150;
	/** N1: the ping range (max - min) over the {@link #WINDOW_S} window is under this. Set by E4. */
	public static final int PING_STEADY_MS = 50;
	/** N1's words name the usual only when it is this many ms lower, or more. Set by E4. */
	public static final int PING_USUAL_LOWER_MS = 30;
	/** The re-sent share is taken over this many seconds (samples). Set by E10. */
	public static final int RESENT_WINDOW_S = 16;
	/** Least BYTES sent in {@link #RESENT_WINDOW_S} before re-sends count (C1). Set by E10. */
	public static final int RESENT_MIN_BYTES = 2048;
	/** Least SEGMENTS sent in {@link #RESENT_WINDOW_S} before re-sends count (C1). Set by E10. */
	public static final int RESENT_MIN_UNITS = 8;
	/** A re-sent share, per mille, of this or more counts. Set by E10. */
	public static final int RESENT_PER_MILLE = 10;
	/** Seconds either side of an event searched for re-sends. Set by E10. */
	public static final int RESENT_LOOK_S = 2;
	/** S1: a load this long or longer is listed. Set by E14. */
	public static final int LOAD_LONG_MS = 2000;
	/** S1's words count the loads of this many seconds ("the last 10 min"). Set by design. */
	public static final int LOADS_LOOK_S = 600;
	/** Quiet seconds that close an event (and merge the ones closer than this). Set by E14. */
	public static final int EVENT_QUIET_S = 5;
	/** An event longer than this is closed and becomes a condition. Set by design. */
	public static final int EVENT_MAX_S = 120;
	/** A condition must hold this many seconds in a row. Set by design. */
	public static final int CONDITION_HOLD_S = 10;
	/** The card holds a verdict at least this many seconds. Set by design. */
	public static final int VERDICT_HOLD_S = 10;
	/**
	 * An event verdict stays on the card this many seconds after the event's end. Set by the user 2026-09-29 (back to
	 * Smooth after a short, defined time); equal to VERDICT_HOLD_S so the card changes the moment the show ends, 15 s
	 * after the last bad tick.
	 */
	public static final int EVENT_SHOW_S = 10;
	/** The top event rule must beat the second by this many points. Set by E14. */
	public static final int SCORE_MARGIN = 15;
	/** Points one support adds to a rule's score. Set by design. */
	public static final int SUPPORT_POINTS = 10;
	/** Rows the event list shows. Set by design. */
	public static final int EVENT_ROWS = 6;
	/** Strip columns, one value per pixel of the 213 px panel. Set by design. */
	public static final int STRIP_COLUMNS = 213;
	/** Frame rate lane: the scale's top is at least this many frames a second. Set by design. */
	public static final int STRIP_FPS_MAX = 60;
	/** Ticks lane: the scale's bottom, in ms. Set by design. */
	public static final int STRIP_TICK_MIN_MS = 450;
	/** Ticks lane: the scale's top is at least this, in ms. Set by design. */
	public static final int STRIP_TICK_MAX_MS = 900;
	/** Ticks lane: the scale's top is at least the longest gap plus this, in ms. Set by design. */
	public static final int STRIP_TICK_PAD_MS = 50;
	/** Ping lane: the scale's top is at least this, in ms. Set by design. */
	public static final int STRIP_PING_MAX_MS = 100;
	/** Ping lane: the scale's top is at least this % of the highest RTT. Set by design. */
	public static final int STRIP_PING_PAD_PCT = 120;
	/** A hole of at most this many host seconds takes the ping readings of the sample that closes it (L2). Design. */
	public static final int HOST_FILL_S = 3;
	/** The badge keeps a lag, dimmed, this many seconds past the event's last trigger second (P2.2). Set by design. */
	public static final int BADGE_HOLD_S = 15;
	/** The least time between two chat lines, in seconds (P2.6). Set by design. */
	public static final int CHAT_GAP_S = 30;

	private Thresholds()
	{
	}
}
