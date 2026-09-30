package com.whylag.core;

/**
 * The settings that change a verdict, read-only and immutable (contract 3.7), and the one rule for the frame cap
 * in force.
 *
 * <p>{@code vsyncMode} is the active renderer's own key, "OFF", "ON", "ADAPTIVE", or "" when it was not read;
 * {@code antiAliasing} the stored enum name ("" unknown); {@code clientVersion} "" when unknown. Null strings are
 * kept as "" and a null renderer as {@link Renderer#UNKNOWN}.
 *
 * <p><b>Which focus a reader passes</b> (contract 3.7). The four cap reads take the window's focus. A reader passes
 * the FOCUSED flag of the one second it judges, never a constant; only the report, which reads no second, prints
 * the focused cap.
 *
 * <p><b>The cap rule</b> (skeptic C9, C10, C11). There are TWO candidates, one from FPS Control and one from the
 * renderer. The LOWER of the two wins; a tie goes to FPS Control. A candidate of 0 or less is no cap and takes no
 * part (117 HD's target may be 0, "no target"; it must not win "lower", and {@link #capIntervalMs} must never divide
 * by it).
 *
 * <p>FPS Control's candidate is ONE of lines 1 and 2, never both. RuneLite's own limiter reads
 * {@code fps = limitFpsUnfocused && !focused ? maxFpsUnfocused : maxFps} ({@code FpsDrawListener.reloadConfig()}),
 * so while the window is unfocused and the unfocused limit is on, {@code maxFps} is NOT in force, even when lower:
 * <ol>
 * <li>FPS Control active, the window unfocused, its unfocused limit on: {@code maxFpsUnfocused},
 * FPS_CONTROL_UNFOCUSED. Line 2 is then not read; with {@code maxFpsUnfocused <= 0} FPS Control gives nothing.</li>
 * <li>Otherwise, FPS Control active and its limit on: {@code maxFps}, FPS_CONTROL. With {@code maxFps <= 0} it gives
 * nothing.</li>
 * </ol>
 * The renderer's candidate is ONE of lines 3 to 5:
 * <ol start="3">
 * <li>GPU or 117 HD, {@code unlockFps}, V-Sync "OFF", {@code fpsTarget > 0}: the target, GPU_TARGET / HD_TARGET.</li>
 * <li>GPU or 117 HD, {@code unlockFps}, V-Sync "ON" or "ADAPTIVE", {@code refreshHz > 0}: the refresh rate,
 * GPU_VSYNC / HD_VSYNC.</li>
 * <li>CPU, or GPU / 117 HD with {@code unlockFps} off: the client's own {@link Thresholds#CLIENT_CAP_FPS},
 * CLIENT_50.</li>
 * <li>An {@link Renderer#UNKNOWN} renderer: no renderer candidate; lines 1 and 2 only.</li>
 * </ol>
 * Worked case: FPS Control's limit on at 20, its unfocused limit on at 40, GPU unlocked with a target of 144.
 * Focused: 20, FPS_CONTROL. Unfocused: 40, FPS_CONTROL_UNFOCUSED (not 20).
 */
public final class SettingsView
{
	private static final int MS_PER_SECOND = 1000;
	private static final int PERCENT = 100;
	private static final String VSYNC_OFF = "OFF";
	private static final String VSYNC_ON = "ON";
	private static final String VSYNC_ADAPTIVE = "ADAPTIVE";

	public final Renderer renderer;
	public final boolean fpsControlActive, limitFps, limitFpsUnfocused, unlockFps;
	public final int maxFps, maxFpsUnfocused, fpsTarget, drawDistance, expandedMapLoading, refreshHz;
	/** "OFF" | "ON" | "ADAPTIVE" | "" (the renderer's own key, read only while it is active). */
	public final String vsyncMode;
	/** The stored enum name, "" = unknown. */
	public final String antiAliasing;
	public final Os os;
	/** "" = unknown. */
	public final String clientVersion;

	public SettingsView(Renderer renderer, boolean fpsControlActive, boolean limitFps, int maxFps,
		boolean limitFpsUnfocused, int maxFpsUnfocused, boolean unlockFps, String vsyncMode, int fpsTarget,
		int drawDistance, String antiAliasing, int expandedMapLoading, int refreshHz, Os os, String clientVersion)
	{
		this.renderer = renderer == null ? Renderer.UNKNOWN : renderer;
		this.fpsControlActive = fpsControlActive;
		this.limitFps = limitFps;
		this.maxFps = maxFps;
		this.limitFpsUnfocused = limitFpsUnfocused;
		this.maxFpsUnfocused = maxFpsUnfocused;
		this.unlockFps = unlockFps;
		this.vsyncMode = vsyncMode == null ? "" : vsyncMode;
		this.fpsTarget = fpsTarget;
		this.drawDistance = drawDistance;
		this.antiAliasing = antiAliasing == null ? "" : antiAliasing;
		this.expandedMapLoading = expandedMapLoading;
		this.refreshHz = refreshHz;
		this.os = os;
		this.clientVersion = clientVersion == null ? "" : clientVersion;
	}

	/**
	 * Settings not read yet: renderer {@link Renderer#UNKNOWN}, no cap known, refresh 0, every other number 0 and
	 * every text "".
	 */
	public static SettingsView unknown(Os os)
	{
		return new SettingsView(Renderer.UNKNOWN, false, false, 0, false, 0, false, "", 0, 0, "", 0, 0, os, "");
	}

	/** The cap in force; 0 = no cap known. */
	public int capFps(boolean focused)
	{
		return fpsOf(capSource(focused));
	}

	/** Who set the cap in force; {@link CapSource#NONE} when there is none. See the class notes. */
	public CapSource capSource(boolean focused)
	{
		final CapSource fpsControl = fpsControlCap(focused);
		final CapSource own = rendererCap();
		final int fpsControlFps = fpsOf(fpsControl);
		final int ownFps = fpsOf(own);
		if (fpsControlFps <= 0)
		{
			return ownFps > 0 ? own : CapSource.NONE;
		}
		// The lower of the two wins; a tie goes to FPS Control.
		return ownFps > 0 && ownFps < fpsControlFps ? own : fpsControl;
	}

	/** {@code 1000 / capFps}, or 0 when there is no cap. */
	public int capIntervalMs(boolean focused)
	{
		final int cap = capFps(focused);
		return cap > 0 ? MS_PER_SECOND / cap : 0;
	}

	/** The frame gap trigger: {@code max(FRAME_GAP_MS, capIntervalMs * FRAME_GAP_CAP_PCT / 100)}. */
	public int frameGapLimitMs(boolean focused)
	{
		return Math.max(Thresholds.FRAME_GAP_MS, capIntervalMs(focused) * Thresholds.FRAME_GAP_CAP_PCT / PERCENT);
	}

	/**
	 * Lines 1 and 2 of the cap rule: FPS Control's ONE candidate, as RuneLite's {@code FpsDrawListener} reads it, or
	 * NONE. Unfocused with the unfocused limit on, line 2 is not read, even when its cap is lower or line 1's is 0.
	 */
	private CapSource fpsControlCap(boolean focused)
	{
		if (!fpsControlActive)
		{
			return CapSource.NONE;
		}
		if (!focused && limitFpsUnfocused)
		{
			return maxFpsUnfocused > 0 ? CapSource.FPS_CONTROL_UNFOCUSED : CapSource.NONE;
		}
		return limitFps && maxFps > 0 ? CapSource.FPS_CONTROL : CapSource.NONE;
	}

	/** Lines 3 to 6 of the cap rule: what the renderer itself caps at, or NONE. */
	private CapSource rendererCap()
	{
		switch (renderer)
		{
			case CPU:
				return CapSource.CLIENT_50;
			case GPU:
			case HD:
				if (!unlockFps)
				{
					return CapSource.CLIENT_50;
				}
				final boolean hd = renderer == Renderer.HD;
				if (VSYNC_OFF.equals(vsyncMode))
				{
					return fpsTarget > 0 ? (hd ? CapSource.HD_TARGET : CapSource.GPU_TARGET) : CapSource.NONE;
				}
				if (VSYNC_ON.equals(vsyncMode) || VSYNC_ADAPTIVE.equals(vsyncMode))
				{
					return refreshHz > 0 ? (hd ? CapSource.HD_VSYNC : CapSource.GPU_VSYNC) : CapSource.NONE;
				}
				return CapSource.NONE;
			default:
				return CapSource.NONE;
		}
	}

	/** The frame rate a cap source stands for in THESE settings; 0 for NONE. */
	private int fpsOf(CapSource source)
	{
		switch (source)
		{
			case FPS_CONTROL_UNFOCUSED:
				return Math.max(0, maxFpsUnfocused);
			case FPS_CONTROL:
				return Math.max(0, maxFps);
			case GPU_TARGET:
			case HD_TARGET:
				return Math.max(0, fpsTarget);
			case GPU_VSYNC:
			case HD_VSYNC:
				return Math.max(0, refreshHz);
			case CLIENT_50:
				return Thresholds.CLIENT_CAP_FPS;
			default:
				return 0;
		}
	}
}
