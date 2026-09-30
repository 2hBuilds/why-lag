package com.whylag.core;

/**
 * What sets the frame cap in force (contract 3.2, 3.7). {@link #selfSet()}: the player chose it, so a frame rate
 * held at it is no lag (F1). {@link #waits()}: the renderer paces frames by WAITING for it, so a gap of a couple of
 * cap intervals can be pacing rather than a stall (S4's exclude).
 */
public enum CapSource
{
	NONE("", false, false),
	CLIENT_50("the client", false, false),
	FPS_CONTROL("FPS Control", true, true),
	FPS_CONTROL_UNFOCUSED("FPS Control (unfocused)", true, true),
	GPU_TARGET("GPU: FPS target", true, true),
	GPU_VSYNC("GPU: V-Sync", true, true),
	HD_TARGET("117 HD: FPS target", true, true),
	HD_VSYNC("117 HD: V-Sync", true, true);

	private final String label;
	private final boolean selfSet;
	private final boolean waits;

	CapSource(String label, boolean selfSet, boolean waits)
	{
		this.label = label;
		this.selfSet = selfSet;
		this.waits = waits;
	}

	/** The words F1's proof and the report print ("Set by FPS Control."); "" for {@link #NONE}. */
	public String label()
	{
		return label;
	}

	/** True when the player set this cap. */
	public boolean selfSet()
	{
		return selfSet;
	}

	/** True when frames are paced by waiting for this cap. */
	public boolean waits()
	{
		return waits;
	}
}
