package com.whylag;

import com.whylag.core.Os;
import com.whylag.core.Renderer;
import com.whylag.core.SettingsView;
import java.util.function.BooleanSupplier;
import java.util.function.IntSupplier;
import net.runelite.client.config.ConfigManager;

/**
 * Reads the settings that change a verdict into one immutable {@link SettingsView} (contract 3.7, 3.11, 7 L7), on
 * the sampler thread, from RuneLite's stored settings alone. Read-only: it writes no setting.
 *
 * <ul>
 * <li>Whether a GPU renderer is on is {@code Client#isGpu()}, asked of a supplier the plugin fills on the client
 * thread; the reader never touches the client. When it is on, 117 HD - a Hub plugin this build cannot name as a
 * type - decides which group is read: RuneLite's own stored flag under the group {@value #FLAG_GROUP}, the key of
 * the plugin ({@value #HD_FLAG} or {@value #HD_FLAG_ALT}), either stored "true" is 117 HD on. No plugin object is
 * asked for anything. Whether FPS Control is on is its stored flag too ({@value #FPS_FLAG}, its class's simple name,
 * lower case, as RuneLite's plugin manager stores it).</li>
 * <li>A group is read ONLY while its plugin is on (skeptic C10): a setting stays stored when its plugin is off, and
 * an off plugin caps nothing.</li>
 * <li>The renderer is the client's own CPU one when no GPU renderer is on; else 117 HD when its flag says so, else
 * the GPU plugin. Only the winner's group is read ({@code hd} or {@code gpu}); on the CPU renderer no renderer key
 * is read, so its six fields are false, "" and 0 (contract 6.6: "on CPU ... their keys are not read").</li>
 * <li>A missing key falls back to RuneLite's own default - numbers of RuneLite, not thresholds (contract 3.1):
 * {@code gpu} unlockFps true, vsyncMode OFF, fpsTarget 60, drawDistance 50, antiAliasingMode MSAA_2,
 * expandedMapLoadingChunks 3 (proven in the 1.12.37 clone); {@code hd} false, ADAPTIVE, 60, 50, MSAA_8, 3 (proven
 * at 117 HD's Hub pin, skeptic C11); {@code fpscontrol} limitFps false, maxFps 50, limitFpsUnfocused false,
 * maxFpsUnfocused 50.</li>
 * <li>The refresh rate supplier is asked on EVERY read (the window may have moved to another screen). When a read
 * fails as a whole, the answer is {@code SettingsView.unknown(os)}.</li>
 * </ul>
 * {@link #read} never throws.
 *
 * <p>Choice: the supplier is asked once, first, and on its own: a throwing one reads as 0, the rest still read.
 * <p>Choice: a missing flag is RuneLite's own default for that plugin: FPS Control's descriptor switches it off by
 * default ({@code enabledByDefault = false}) and a Hub plugin is on only when its flag says so, so theirs read as
 * off.
 * <p>Choice: the GPU supplier is asked once per read, like the refresh rate; a throwing one makes the read as a
 * whole fail, which answers {@code SettingsView.unknown}.
 * <p>Choice: values parse as RuneLite's config proxy parses them; a value that does not parse reads as the default.
 * <p>Choice: V-Sync keeps only OFF, ON or ADAPTIVE (the view's words); any other stored name reads as the default.
 * <p>Choice: the anti-aliasing name is kept as stored, since it is only printed.
 * <p>Choice: an off plugin's fields are what SettingsView.unknown holds: false, 0 and "" (nothing read).
 * <p>Choice: with 117 HD on the gpu group is not read at all.
 */
public final class SettingsReader
{
	/** RuneLite's group of the "this plugin is on" flags. */
	static final String FLAG_GROUP = "runelite";

	/** The two flags: a plugin's configName, else its class's simple name, lower case. */
	static final String FPS_FLAG = "fpsplugin";
	static final String HD_FLAG = "hdplugin";
	static final String HD_FLAG_ALT = "hd";

	/** Their config groups. */
	static final String GPU_GROUP = "gpu";
	static final String FPS_GROUP = "fpscontrol";
	static final String HD_GROUP = "hd";

	/** The renderer keys: the same six names in {@code gpu} and {@code hd}. */
	private static final String UNLOCK_FPS = "unlockFps";
	private static final String VSYNC_MODE = "vsyncMode";
	private static final String FPS_TARGET = "fpsTarget";
	private static final String DRAW_DISTANCE = "drawDistance";
	private static final String ANTI_ALIASING = "antiAliasingMode";
	private static final String MAP_LOADING = "expandedMapLoadingChunks";

	/** FPS Control's keys. */
	private static final String LIMIT_FPS = "limitFps";
	private static final String MAX_FPS = "maxFps";
	private static final String LIMIT_FPS_UNFOCUSED = "limitFpsUnfocused";
	private static final String MAX_FPS_UNFOCUSED = "maxFpsUnfocused";

	/** The stored names of the three V-Sync modes, as {@code SettingsView} reads them. */
	private static final String[] VSYNC_MODES = {"OFF", "ON", "ADAPTIVE"};

	/** Whether a flag that was never stored reads as on: RuneLite's descriptor defaults of the two plugins. */
	private static final boolean FPS_ON_BY_DEFAULT = false;
	private static final boolean HD_ON_BY_DEFAULT = false;

	/** RuneLite's defaults (contract 3.1: RuneLite's numbers, not ours). */
	private static final RendererDefaults GPU_DEFAULTS = new RendererDefaults(true, "OFF", 60, 50, "MSAA_2", 3);
	private static final RendererDefaults HD_DEFAULTS = new RendererDefaults(false, "ADAPTIVE", 60, 50, "MSAA_8", 3);
	private static final boolean LIMIT_FPS_DEFAULT = false;
	private static final int MAX_FPS_DEFAULT = 50;
	private static final boolean LIMIT_FPS_UNFOCUSED_DEFAULT = false;
	private static final int MAX_FPS_UNFOCUSED_DEFAULT = 50;

	private final ConfigManager configManager;
	private final BooleanSupplier gpuOn;
	private final IntSupplier refreshHz;
	private final Os os;
	private final String clientVersion;

	/**
	 * @param gpuOn         whether a GPU renderer is on now ({@code Client#isGpu()}, read by the plugin on the client
	 *                      thread); asked on every read
	 * @param refreshHz     the screen's refresh rate now, 0 = unknown; asked on every read
	 * @param clientVersion RuneLite's version, "" = unknown
	 */
	public SettingsReader(ConfigManager configManager, BooleanSupplier gpuOn, IntSupplier refreshHz, Os os,
		String clientVersion)
	{
		this.configManager = configManager;
		this.gpuOn = gpuOn;
		this.refreshHz = refreshHz;
		this.os = os;
		this.clientVersion = clientVersion;
	}

	/** The settings now. Sampler thread; never throws; asks the supplier on every call. */
	public SettingsView read()
	{
		final int refresh = askRefreshHz();
		try
		{
			return readAll(refresh);
		}
		catch (Exception | LinkageError e)
		{
			return SettingsView.unknown(os);
		}
	}

	private int askRefreshHz()
	{
		try
		{
			return refreshHz.getAsInt();
		}
		catch (Exception | LinkageError e)
		{
			return 0;
		}
	}

	private SettingsView readAll(int refresh)
	{
		final Renderer renderer;
		final String group;
		final RendererDefaults defaults;
		if (!gpuOn.getAsBoolean())
		{
			renderer = Renderer.CPU;
			group = null;
			defaults = null;
		}
		else if (flag(HD_FLAG, HD_ON_BY_DEFAULT) || flag(HD_FLAG_ALT, HD_ON_BY_DEFAULT))
		{
			renderer = Renderer.HD;
			group = HD_GROUP;
			defaults = HD_DEFAULTS;
		}
		else
		{
			renderer = Renderer.GPU;
			group = GPU_GROUP;
			defaults = GPU_DEFAULTS;
		}

		boolean unlockFps = false;
		String vsyncMode = "";
		int fpsTarget = 0;
		int drawDistance = 0;
		String antiAliasing = "";
		int mapLoading = 0;
		if (group != null)
		{
			unlockFps = bool(group, UNLOCK_FPS, defaults.unlockFps);
			vsyncMode = vsync(group, defaults.vsyncMode);
			fpsTarget = integer(group, FPS_TARGET, defaults.fpsTarget);
			drawDistance = integer(group, DRAW_DISTANCE, defaults.drawDistance);
			antiAliasing = text(group, ANTI_ALIASING, defaults.antiAliasing);
			mapLoading = integer(group, MAP_LOADING, defaults.mapLoading);
		}

		final boolean fpsControl = flag(FPS_FLAG, FPS_ON_BY_DEFAULT);
		boolean limitFps = false;
		int maxFps = 0;
		boolean limitFpsUnfocused = false;
		int maxFpsUnfocused = 0;
		if (fpsControl)
		{
			limitFps = bool(FPS_GROUP, LIMIT_FPS, LIMIT_FPS_DEFAULT);
			maxFps = integer(FPS_GROUP, MAX_FPS, MAX_FPS_DEFAULT);
			limitFpsUnfocused = bool(FPS_GROUP, LIMIT_FPS_UNFOCUSED, LIMIT_FPS_UNFOCUSED_DEFAULT);
			maxFpsUnfocused = integer(FPS_GROUP, MAX_FPS_UNFOCUSED, MAX_FPS_UNFOCUSED_DEFAULT);
		}

		return new SettingsView(renderer, fpsControl, limitFps, maxFps, limitFpsUnfocused, maxFpsUnfocused,
			unlockFps, vsyncMode, fpsTarget, drawDistance, antiAliasing, mapLoading, refresh, os, clientVersion);
	}

	/** RuneLite's "is this plugin on" flag: the stored value, else the plugin's own default. */
	private boolean flag(String key, boolean fallback)
	{
		return bool(FLAG_GROUP, key, fallback);
	}

	/** A missing key is the default; else {@code Boolean.parseBoolean}, as RuneLite reads its own. */
	private boolean bool(String group, String key, boolean fallback)
	{
		final String value = configManager.getConfiguration(group, key);
		return value == null ? fallback : Boolean.parseBoolean(value);
	}

	/** A missing key, or one that is not a whole number, is the default, as RuneLite's config proxy does. */
	private int integer(String group, String key, int fallback)
	{
		final String value = configManager.getConfiguration(group, key);
		if (value == null)
		{
			return fallback;
		}
		try
		{
			return Integer.parseInt(value);
		}
		catch (NumberFormatException e)
		{
			return fallback;
		}
	}

	/** OFF, ON or ADAPTIVE as stored; a missing key or any other name is the default. */
	private String vsync(String group, String fallback)
	{
		final String value = configManager.getConfiguration(group, VSYNC_MODE);
		for (String mode : VSYNC_MODES)
		{
			if (mode.equals(value))
			{
				return mode;
			}
		}
		return fallback;
	}

	/** The stored text; a missing key is the default. */
	private String text(String group, String key, String fallback)
	{
		final String value = configManager.getConfiguration(group, key);
		return value == null ? fallback : value;
	}

	/** One renderer's defaults, in RuneLite's numbers. */
	private static final class RendererDefaults
	{
		final boolean unlockFps;
		final String vsyncMode;
		final int fpsTarget;
		final int drawDistance;
		final String antiAliasing;
		final int mapLoading;

		RendererDefaults(boolean unlockFps, String vsyncMode, int fpsTarget, int drawDistance, String antiAliasing,
			int mapLoading)
		{
			this.unlockFps = unlockFps;
			this.vsyncMode = vsyncMode;
			this.fpsTarget = fpsTarget;
			this.drawDistance = drawDistance;
			this.antiAliasing = antiAliasing;
			this.mapLoading = mapLoading;
		}
	}
}
