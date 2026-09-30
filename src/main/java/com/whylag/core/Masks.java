package com.whylag.core;

/**
 * The detector's mask (contract 6.1). ONLY {@link LagDetector} calls it: every other lot leaves out the seconds that
 * {@link Flags#masked(int)} names, which holds four of the six rules below (contract 3.2).
 *
 * <p>A second is masked when any of these holds:
 * <ol>
 * <li>{@link Flags#NOT_LOGGED_IN}: the login screen;</li>
 * <li>{@link Flags#LOADING}: a load, and the second in which a frame that overlapped a load ended (6.1);</li>
 * <li>{@link Flags#HOP}: a hop;</li>
 * <li>{@link Flags#LOGIN_MASK}: the seconds of the first {@link Thresholds#LOGIN_MASK_TICKS} ticks after a login, a
 * hop or a lost connection ends (the first of them spans the whole outage);</li>
 * <li>the tail of a load: any of the {@link Thresholds#LOAD_TAIL_S} seconds before it carries LOADING;</li>
 * <li>the window is not focused while FPS Control's unfocused limit is the cap in force,
 * {@code s.capSource(false) == FPS_CONTROL_UNFOCUSED} (the second's own focus, as every cap read takes it, 3.7).</li>
 * </ol>
 * A masked second fires only DISCONNECT and LONG_LOAD, feeds no usual, and counts as quiet for closing an event
 * (contract 6.1, 6.2); those rules are the detector's.
 *
 * <p>The second asked about is one the ring holds: the detector asks only of the second it is reading.
 *
 * <p>Choice: a second before the ring's tail cannot be read, so it is not searched for the tail of a load.
 */
public final class Masks
{
	private Masks()
	{
	}

	/**
	 * True when second {@code sec} is masked by the six rules of the class notes.
	 *
	 * @param ring the second ring; {@code sec} must be a second it holds
	 * @param sec  the second judged
	 * @param s    the settings in force: the cap that FPS Control's unfocused limit must be for rule 6
	 */
	public static boolean masked(SecondRing ring, long sec, SettingsView s)
	{
		final int flags = ring.flags(sec);
		if (Flags.masked(flags))
		{
			return true;
		}
		for (int back = 1; back <= Thresholds.LOAD_TAIL_S; back++)
		{
			final long before = sec - back;
			if (before < 0)
			{
				break;
			}
			final int beforeFlags = ring.flags(before);
			if (ring.valid(before) && Flags.has(beforeFlags, Flags.LOADING))
			{
				return true;
			}
		}
		return !Flags.has(flags, Flags.FOCUSED) && s.capSource(false) == CapSource.FPS_CONTROL_UNFOCUSED;
	}
}
