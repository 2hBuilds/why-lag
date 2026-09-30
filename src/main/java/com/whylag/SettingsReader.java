package com.whylag;

import com.whylag.core.MemorySource;
import com.whylag.core.Os;
import com.whylag.core.Renderer;
import com.whylag.core.SettingsView;
import java.util.function.IntSupplier;
import java.util.function.Supplier;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginManager;

/**
 * Reads the settings that change a verdict into one immutable {@link SettingsView} (contract 3.7, 3.11, 7 L7), on
 * the sampler thread. Read-only: it writes no setting.
 *
 * <ul>
 * <li>The three plugins are found by CLASS NAME in {@code pluginManager.getPlugins()}: the bundled GPU plugin
 * ({@value #GPU_PLUGIN}), FPS Control ({@value #FPS_PLUGIN}) and 117 HD ({@value #HD_PLUGIN}, a Hub plugin this
 * build cannot name as a type).</li>
 * <li>A group is read ONLY while its plugin {@code isPluginActive} (skeptic C10): a setting stays stored when its
 * plugin is off, and an off plugin caps nothing.</li>
 * <li>The renderer is 117 HD when it is active, else the GPU plugin when it is active, else the client's own CPU
 * renderer. Only the winner's group is read ({@code hd} or {@code gpu}); on the CPU renderer no renderer key is
 * read, so its six fields are false, "" and 0 (contract 6.6: "on CPU ... their keys are not read").</li>
 * <li>A missing key falls back to RuneLite's own default - numbers of RuneLite, not thresholds (contract 3.1):
 * {@code gpu} unlockFps true, vsyncMode OFF, fpsTarget 60, drawDistance 50, antiAliasingMode MSAA_2,
 * expandedMapLoadingChunks 3 (proven in the 1.12.37 clone); {@code hd} false, ADAPTIVE, 60, 50, MSAA_8, 3 (proven
 * at 117 HD's Hub pin, skeptic C11); {@code fpscontrol} limitFps false, maxFps 50, limitFpsUnfocused false,
 * maxFpsUnfocused 50.</li>
 * <li>Both suppliers are asked on EVERY read: the refresh rate (the window may have moved to another screen) and the
 * memory source (a {@code systemStats} swap changes it). When a read fails as a whole, the answer is
 * {@code SettingsView.unknown(heapMaxMb, os, source)}, where {@code source} is what the memory supplier answered, or
 * RUNTIME when the supplier itself threw - so a Runtime-only PC is never reported as MANAGEMENT.</li>
 * </ul>
 * {@link #read} never throws.
 *
 * <p>Choice: each supplier is asked once, first, and on its own: a throwing one reads as RUNTIME or 0, the rest still read.
 * <p>Choice: values parse as RuneLite's config proxy parses them; a value that does not parse reads as the default.
 * <p>Choice: V-Sync keeps only OFF, ON or ADAPTIVE (the view's words); any other stored name reads as the default.
 * <p>Choice: the anti-aliasing name is kept as stored, since it is only printed.
 * <p>Choice: an inactive plugin's fields are what SettingsView.unknown holds: false, 0 and "" (nothing read).
 * <p>Choice: with 117 HD active the gpu group is not read at all; the first plugin of each class name counts.
 */
public final class SettingsReader
{
	/** The three plugins, by class name. */
	static final String GPU_PLUGIN = "net.runelite.client.plugins.gpu.GpuPlugin";
	static final String FPS_PLUGIN = "net.runelite.client.plugins.fps.FpsPlugin";
	static final String HD_PLUGIN = "rs117.hd.HdPlugin";

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

	/** RuneLite's defaults (contract 3.1: RuneLite's numbers, not ours). */
	private static final RendererDefaults GPU_DEFAULTS = new RendererDefaults(true, "OFF", 60, 50, "MSAA_2", 3);
	private static final RendererDefaults HD_DEFAULTS = new RendererDefaults(false, "ADAPTIVE", 60, 50, "MSAA_8", 3);
	private static final boolean LIMIT_FPS_DEFAULT = false;
	private static final int MAX_FPS_DEFAULT = 50;
	private static final boolean LIMIT_FPS_UNFOCUSED_DEFAULT = false;
	private static final int MAX_FPS_UNFOCUSED_DEFAULT = 50;

	private final ConfigManager configManager;
	private final PluginManager pluginManager;
	private final IntSupplier refreshHz;
	private final Supplier<MemorySource> memorySource;
	private final int heapMaxMb;
	private final Os os;
	private final String clientVersion;

	/**
	 * @param refreshHz     the screen's refresh rate now, 0 = unknown; asked on every read
	 * @param memorySource  the host probe's source now; asked on every read (a {@code systemStats} swap changes it)
	 * @param heapMaxMb     the heap limit, 0 or less = unknown
	 * @param clientVersion RuneLite's version, "" = unknown
	 */
	public SettingsReader(ConfigManager configManager, PluginManager pluginManager, IntSupplier refreshHz,
		Supplier<MemorySource> memorySource, int heapMaxMb, Os os, String clientVersion)
	{
		this.configManager = configManager;
		this.pluginManager = pluginManager;
		this.refreshHz = refreshHz;
		this.memorySource = memorySource;
		this.heapMaxMb = heapMaxMb;
		this.os = os;
		this.clientVersion = clientVersion;
	}

	/** The settings now. Sampler thread; never throws; asks both suppliers on every call. */
	public SettingsView read()
	{
		final MemorySource source = askMemorySource();
		final int refresh = askRefreshHz();
		try
		{
			return readAll(source, refresh);
		}
		catch (Exception | LinkageError e)
		{
			return SettingsView.unknown(heapMaxMb, os, source);
		}
	}

	private MemorySource askMemorySource()
	{
		try
		{
			final MemorySource source = memorySource.get();
			return source == null ? MemorySource.RUNTIME : source;
		}
		catch (Exception | LinkageError e)
		{
			return MemorySource.RUNTIME;
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

	private SettingsView readAll(MemorySource source, int refresh)
	{
		Plugin gpu = null;
		Plugin fps = null;
		Plugin hd = null;
		for (Plugin plugin : pluginManager.getPlugins())
		{
			if (plugin == null)
			{
				continue;
			}
			final String name = plugin.getClass().getName();
			if (gpu == null && GPU_PLUGIN.equals(name))
			{
				gpu = plugin;
			}
			else if (fps == null && FPS_PLUGIN.equals(name))
			{
				fps = plugin;
			}
			else if (hd == null && HD_PLUGIN.equals(name))
			{
				hd = plugin;
			}
		}

		final Renderer renderer;
		final String group;
		final RendererDefaults defaults;
		if (active(hd))
		{
			renderer = Renderer.HD;
			group = HD_GROUP;
			defaults = HD_DEFAULTS;
		}
		else if (active(gpu))
		{
			renderer = Renderer.GPU;
			group = GPU_GROUP;
			defaults = GPU_DEFAULTS;
		}
		else
		{
			renderer = Renderer.CPU;
			group = null;
			defaults = null;
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

		final boolean fpsControl = active(fps);
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
			unlockFps, vsyncMode, fpsTarget, drawDistance, antiAliasing, mapLoading, refresh, heapMaxMb, source, os,
			clientVersion);
	}

	private boolean active(Plugin plugin)
	{
		return plugin != null && pluginManager.isPluginActive(plugin);
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
