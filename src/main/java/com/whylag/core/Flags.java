package com.whylag.core;

/**
 * The flag bits of one second, stored as ONE byte per second (contract 3.2, 6.1). Every bit is under 128, so the
 * byte column never goes negative.
 *
 * <p>Who sets each bit is the samplers' rule (contract section 7, L2, "The state and the flags of a second"):
 * {@link #FOCUSED} is the focus when the second is closed; {@link #NOT_LOGGED_IN} marks a second whose state is
 * OTHER, LOGIN_SCREEN or LOGGING_IN (never HOPPING or CONNECTION_LOST); {@link #LOADING}, {@link #HOP} and
 * {@link #DISCONNECT} mark every second that overlaps LOADING, HOPPING or CONNECTION_LOST time (LOADING also the
 * second a loading frame ended in); {@link #NO_FRAMES} a filled second; {@link #LOGIN_MASK} the seconds of the
 * ticks after a login, a hop or a lost connection ends.
 *
 * <p>{@link #masked(int)} is the mask EVERY lot can read: a second masked by its flags alone. The detector's own
 * {@code Masks.masked} (contract 6.1) has two rules more, which need the ring and the settings; only
 * {@code LagDetector} calls it.
 */
public final class Flags
{
	public static final int FOCUSED = 1, LOADING = 2, HOP = 4, LOGIN_MASK = 8, NOT_LOGGED_IN = 16,
		NO_FRAMES = 32, DISCONNECT = 64;

	/** The four bits that mask a second by its flags alone: LOADING, HOP, LOGIN_MASK and NOT_LOGGED_IN (30). */
	public static final int MASKED = LOADING | HOP | LOGIN_MASK | NOT_LOGGED_IN;

	private Flags()
	{
	}

	/** True when {@code bit} is set in {@code flags}. */
	public static boolean has(int flags, int bit)
	{
		return (flags & bit) != 0;
	}

	/**
	 * True when {@code flags} carry any bit of {@link #MASKED}: the second is masked BY ITS FLAGS ALONE (contract 3.2)
	 * - a load, a hop, the login mask or the login screen. {@code Masks.masked} (L3) adds the tail of a load and the
	 * unfocused second under FPS Control's unfocused limit; the verdict (L4) and the snapshot (L5) never call it, and
	 * where they must leave a second out they leave out the seconds this names (contract 6.3, "Which seconds a
	 * condition reads").
	 */
	public static boolean masked(int flags)
	{
		return (flags & MASKED) != 0;
	}
}
