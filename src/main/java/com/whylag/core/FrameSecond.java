package com.whylag.core;

/**
 * The client thread's half of one second (contract 3.3): a mutable carrier, allocated once by its owner and
 * reused for every second, which {@link SecondRing#putFrame} copies into its columns.
 *
 * <p>{@code worstFrameEndMs} is where INSIDE the second the worst frame ENDED, 0..999: that frame ran over
 * [second start + worstFrameEndMs - worstFrameMs, second start + worstFrameEndMs], in session ms. {@code busyPm}
 * and {@code worstBusyPm} are the client thread's CPU share, per mille, of the second and of its worst frame; -1
 * means the CPU clock gave no answer.
 */
public final class FrameSecond
{
	public int frames, worstFrameMs, worstFrameEndMs, slowFrames, busyPm, worstBusyPm, loadingMs, state, flags,
		world, players, npcs, region;

	/** A cleared carrier (see {@link #clear()}). */
	public FrameSecond()
	{
		clear();
	}

	/** Every field 0, except {@code busyPm = worstBusyPm = -1} (not measured). */
	public void clear()
	{
		frames = 0;
		worstFrameMs = 0;
		worstFrameEndMs = 0;
		slowFrames = 0;
		busyPm = -1;
		worstBusyPm = -1;
		loadingMs = 0;
		state = 0;
		flags = 0;
		world = 0;
		players = 0;
		npcs = 0;
		region = 0;
	}
}
