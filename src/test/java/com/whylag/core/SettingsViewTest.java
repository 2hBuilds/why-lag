package com.whylag.core;

import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

/**
 * The cap rule of contract 3.7 (skeptic C9, C10, C11): one test per line; FPS Control gives ONE candidate, as
 * RuneLite's {@code FpsDrawListener} reads it (unfocused with the unfocused limit on, {@code maxFps} is not read);
 * the lower of FPS Control's and the renderer's candidate wins, a tie going to FPS Control; a cap of 0 or less
 * never takes part. With it, the settings not read yet, which keep the memory source they are given, and a null
 * memory source kept as RUNTIME. The small enums the settings carry are {@code EnumsTest}'s.
 */
public class SettingsViewTest
{
	@Test
	public void line1FpsControlUnfocused()
	{
		final SettingsView v = settings(Renderer.CPU, true, false, 0, true, 20, false, "", 0, 60);
		assertEquals(20, v.capFps(false));
		assertEquals(CapSource.FPS_CONTROL_UNFOCUSED, v.capSource(false));
		assertEquals("focused, the unfocused limit is not in force", CapSource.CLIENT_50, v.capSource(true));
		assertEquals(50, v.capFps(true));
	}

	@Test
	public void line2FpsControl()
	{
		final SettingsView v = settings(Renderer.CPU, true, true, 30, false, 0, false, "", 0, 60);
		assertEquals(30, v.capFps(true));
		assertEquals(CapSource.FPS_CONTROL, v.capSource(true));
		assertEquals(CapSource.FPS_CONTROL, v.capSource(false));
		assertEquals("an inactive FPS Control is not read (C10)", CapSource.CLIENT_50,
			settings(Renderer.CPU, false, true, 30, true, 20, false, "", 0, 60).capSource(false));
	}

	@Test
	public void line3RendererTarget()
	{
		final SettingsView gpu = settings(Renderer.GPU, false, false, 0, false, 0, true, "OFF", 90, 165);
		assertEquals(90, gpu.capFps(true));
		assertEquals(CapSource.GPU_TARGET, gpu.capSource(true));
		final SettingsView hd = settings(Renderer.HD, false, false, 0, false, 0, true, "OFF", 144, 165);
		assertEquals(144, hd.capFps(true));
		assertEquals(CapSource.HD_TARGET, hd.capSource(true));
	}

	@Test
	public void line4RendererVsync()
	{
		final SettingsView gpu = settings(Renderer.GPU, false, false, 0, false, 0, true, "ON", 90, 165);
		assertEquals("V-Sync on: the refresh rate, not the target", 165, gpu.capFps(true));
		assertEquals(CapSource.GPU_VSYNC, gpu.capSource(true));
		final SettingsView hd = settings(Renderer.HD, false, false, 0, false, 0, true, "ADAPTIVE", 60, 60);
		assertEquals(60, hd.capFps(true));
		assertEquals(CapSource.HD_VSYNC, hd.capSource(true));
	}

	@Test
	public void line5TheClientsOwnFifty()
	{
		final SettingsView cpu = settings(Renderer.CPU, false, false, 0, false, 0, true, "OFF", 144, 60);
		assertEquals("the CPU renderer ignores the GPU keys", 50, cpu.capFps(true));
		assertEquals(CapSource.CLIENT_50, cpu.capSource(true));
		final SettingsView locked = settings(Renderer.GPU, false, false, 0, false, 0, false, "OFF", 144, 60);
		assertEquals(CapSource.CLIENT_50, locked.capSource(true));
		assertEquals(50, locked.capFps(true));
		assertEquals(20, locked.capIntervalMs(true));
		assertEquals("the floor wins over 150 % of 20", 200, locked.frameGapLimitMs(true));
	}

	@Test
	public void line6AnUnknownRendererReadsFpsControlOnly()
	{
		final SettingsView bare = settings(Renderer.UNKNOWN, false, false, 0, false, 0, true, "OFF", 60, 60);
		assertEquals(0, bare.capFps(true));
		assertEquals(CapSource.NONE, bare.capSource(true));
		final SettingsView fpsControl = settings(Renderer.UNKNOWN, true, true, 30, false, 0, false, "", 0, 60);
		assertEquals(30, fpsControl.capFps(true));
		assertEquals(CapSource.FPS_CONTROL, fpsControl.capSource(true));
	}

	/**
	 * The worked case of contract 3.7: FPS Control's limit on at 20, its unfocused limit on at 40, GPU unlocked with
	 * a target of 144. RuneLite's limiter runs at 40 while unfocused, so 20 is NOT in force then, though lower.
	 */
	@Test
	public void unfocusedLimitHidesTheFocusedOne()
	{
		final SettingsView v = settings(Renderer.GPU, true, true, 20, true, 40, true, "OFF", 144, 60);
		assertEquals(20, v.capFps(true));
		assertEquals(CapSource.FPS_CONTROL, v.capSource(true));
		assertEquals("never 20 unfocused", 40, v.capFps(false));
		assertEquals(CapSource.FPS_CONTROL_UNFOCUSED, v.capSource(false));
		assertEquals(25, v.capIntervalMs(false));
		final SettingsView overTheClient = settings(Renderer.CPU, true, true, 20, true, 90, false, "", 0, 60);
		assertEquals("an unfocused limit over the client's own 50 loses to it, and 20 is still not read",
			CapSource.CLIENT_50, overTheClient.capSource(false));
		assertEquals(CapSource.FPS_CONTROL, overTheClient.capSource(true));
	}

	/**
	 * Limit on at 30, the unfocused limit on at 0, the window unfocused, renderer CPU: FPS Control gives NOTHING -
	 * line 2 is not read in place of line 1 - so the client's own 50 is the cap.
	 */
	@Test
	public void unfocusedLimitOfZeroGivesNoFpsControlCap()
	{
		final SettingsView v = settings(Renderer.CPU, true, true, 30, true, 0, false, "", 0, 60);
		assertEquals(CapSource.CLIENT_50, v.capSource(false));
		assertEquals(50, v.capFps(false));
		assertEquals("focused, line 2 answers", CapSource.FPS_CONTROL, v.capSource(true));
		assertEquals(30, v.capFps(true));
		final SettingsView unknownRenderer = settings(Renderer.UNKNOWN, true, true, 30, true, 0, false, "", 0, 60);
		assertEquals("with no renderer candidate either, no cap is known", CapSource.NONE,
			unknownRenderer.capSource(false));
		assertEquals(0, unknownRenderer.capIntervalMs(false));
	}

	@Test
	public void theLowerCandidateWinsAndATieGoesToFpsControl()
	{
		final SettingsView lower = settings(Renderer.GPU, true, true, 30, false, 0, true, "OFF", 60, 60);
		assertEquals(CapSource.FPS_CONTROL, lower.capSource(true));
		final SettingsView higher = settings(Renderer.GPU, true, true, 90, false, 0, true, "OFF", 60, 60);
		assertEquals(CapSource.GPU_TARGET, higher.capSource(true));
		assertEquals(60, higher.capFps(true));
		final SettingsView tie = settings(Renderer.HD, true, true, 60, false, 0, true, "ON", 60, 60);
		assertEquals("a tie goes to FPS Control", CapSource.FPS_CONTROL, tie.capSource(true));
		final SettingsView unfocusedTie = settings(Renderer.CPU, true, true, 25, true, 25, false, "", 0, 60);
		assertEquals("unfocused, FPS Control's one candidate is the unfocused limit", CapSource.FPS_CONTROL_UNFOCUSED,
			unfocusedTie.capSource(false));
		final SettingsView clientLower = settings(Renderer.CPU, true, true, 144, false, 0, false, "", 0, 60);
		assertEquals("FPS Control over 50 does not lift the client's own cap", CapSource.CLIENT_50,
			clientLower.capSource(true));
		final SettingsView tieWithTheClient = settings(Renderer.CPU, true, true, 50, false, 0, false, "", 0, 60);
		assertEquals("a tie with the client's own 50 goes to FPS Control", CapSource.FPS_CONTROL,
			tieWithTheClient.capSource(true));
	}

	@Test
	public void capAtFourFpsLiftsTheFrameGapLimit()
	{
		final SettingsView v = settings(Renderer.CPU, true, true, 4, false, 0, false, "", 0, 60);
		assertEquals(4, v.capFps(true));
		assertEquals(250, v.capIntervalMs(true));
		assertEquals("150 % of a 250 ms interval (C9)", 375, v.frameGapLimitMs(true));
	}

	@Test
	public void vsyncWithUnknownRefreshIsNoCap()
	{
		final SettingsView v = settings(Renderer.GPU, false, false, 0, false, 0, true, "ON", 60, 0);
		assertEquals(0, v.capFps(true));
		assertEquals(CapSource.NONE, v.capSource(true));
		assertEquals(0, v.capIntervalMs(true));
		assertEquals(Thresholds.FRAME_GAP_MS, v.frameGapLimitMs(true));
		final SettingsView unread = settings(Renderer.GPU, false, false, 0, false, 0, true, "", 60, 60);
		assertEquals("a V-Sync mode not read is no cap either", CapSource.NONE, unread.capSource(true));
	}

	@Test
	public void hdTargetZeroIsNoCap()
	{
		// 117 HD with FPS target 0 ("no target") and FPS Control at 30: 30 from FPS Control, and no division by 0.
		final SettingsView v = settings(Renderer.HD, true, true, 30, false, 0, true, "OFF", 0, 60);
		assertEquals(30, v.capFps(true));
		assertEquals(CapSource.FPS_CONTROL, v.capSource(true));
		assertEquals(33, v.capIntervalMs(true));
		assertEquals(Thresholds.FRAME_GAP_MS, v.frameGapLimitMs(true));
		final SettingsView alone = settings(Renderer.HD, false, false, 0, false, 0, true, "OFF", 0, 60);
		assertEquals(CapSource.NONE, alone.capSource(true));
		assertEquals(0, alone.capIntervalMs(true));
	}

	@Test
	public void negativeCapsNeverTakePart()
	{
		assertEquals(CapSource.CLIENT_50,
			settings(Renderer.CPU, true, true, -5, true, 0, false, "", 0, 60).capSource(false));
		assertEquals(CapSource.CLIENT_50,
			settings(Renderer.CPU, true, true, 0, true, -20, false, "", 0, 60).capSource(false));
		assertEquals(CapSource.NONE,
			settings(Renderer.GPU, false, false, 0, false, 0, true, "OFF", -1, 60).capSource(true));
		final SettingsView refresh = settings(Renderer.HD, true, true, -30, true, -30, true, "ON", 60, -60);
		assertEquals(CapSource.NONE, refresh.capSource(false));
		assertEquals(0, refresh.capFps(false));
		assertEquals(0, refresh.capIntervalMs(false));
	}

	@Test
	public void unknownSettings()
	{
		final SettingsView v = SettingsView.unknown(Os.LINUX);
		assertEquals(Renderer.UNKNOWN, v.renderer);
		assertEquals(CapSource.NONE, v.capSource(true));
		assertEquals(CapSource.NONE, v.capSource(false));
		assertEquals(0, v.capFps(true));
		assertEquals(0, v.refreshHz);
		assertEquals(Os.LINUX, v.os);
		assertEquals("", v.vsyncMode);
		assertEquals("", v.antiAliasing);
		assertEquals("", v.clientVersion);
		assertFalse(v.fpsControlActive);
	}

	@Test
	public void nullTextsAreEmpty()
	{
		final SettingsView v = new SettingsView(null, false, false, 0, false, 0, false, null, 0, 0, null, 0, 0,
			Os.WINDOWS, null);
		assertEquals(Renderer.UNKNOWN, v.renderer);
		assertEquals("", v.vsyncMode);
		assertEquals("", v.antiAliasing);
		assertEquals("", v.clientVersion);
		assertEquals(CapSource.NONE, v.capSource(true));
	}

	private static SettingsView settings(Renderer renderer, boolean fpsControlActive, boolean limitFps, int maxFps,
		boolean limitFpsUnfocused, int maxFpsUnfocused, boolean unlockFps, String vsyncMode, int fpsTarget,
		int refreshHz)
	{
		return new SettingsView(renderer, fpsControlActive, limitFps, maxFps, limitFpsUnfocused, maxFpsUnfocused,
			unlockFps, vsyncMode, fpsTarget, 50, "MSAA_2", 3, refreshHz, Os.WINDOWS,
			"1.12.38");
	}
}
