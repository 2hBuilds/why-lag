package com.whylag;

import com.whylag.core.CapSource;
import com.whylag.core.MemorySource;
import com.whylag.core.Os;
import com.whylag.core.Renderer;
import com.whylag.core.SettingsView;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.IntSupplier;
import java.util.function.Supplier;
import net.bytebuddy.ByteBuddy;
import net.bytebuddy.dynamic.loading.ClassLoadingStrategy;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginManager;
import net.runelite.client.plugins.fps.FpsPlugin;
import net.runelite.client.plugins.gpu.GpuPlugin;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * The settings reader (contract 3.7, 3.11, 7 L7; skeptic C10, C11): the plugins found by class name; a group read
 * ONLY while its plugin is active; 117 HD winning over the GPU plugin; RuneLite's own defaults for missing keys;
 * both suppliers asked on every read; a failed read answering {@code SettingsView.unknown} with the memory source
 * the supplier gave; and a read that never throws.
 *
 * <p>The GPU plugin and FPS Control are the client's real classes; 117 HD, a Hub plugin that is not on the class
 * path, is a class made here with its exact name, {@code rs117.hd.HdPlugin}.
 *
 * <p>Choice: 117 HD's stand-in is made with ByteBuddy, which Mockito already brings; no file outside the lot is added.
 */
public class SettingsReaderTest
{
	private static final int HEAP = 768;
	private static final String VERSION = "1.12.38";

	private final Map<String, String> stored = new HashMap<>();
	private final List<String> groupsRead = Collections.synchronizedList(new ArrayList<>());
	private final Set<Plugin> active = new HashSet<>();
	private final List<Plugin> installed = new ArrayList<>();
	private ConfigManager config;
	private PluginManager plugins;
	private Plugin gpu;
	private Plugin fps;
	private Plugin hd;

	@Before
	public void setUp() throws Exception
	{
		config = mock(ConfigManager.class);
		when(config.getConfiguration(anyString(), anyString())).thenAnswer(i ->
		{
			groupsRead.add(i.getArgument(0));
			return stored.get(i.getArgument(0) + "." + i.getArgument(1));
		});
		plugins = mock(PluginManager.class);
		when(plugins.getPlugins()).thenReturn(installed);
		when(plugins.isPluginActive(any())).thenAnswer(i -> active.contains(i.<Plugin>getArgument(0)));
		gpu = new GpuPlugin();
		fps = new FpsPlugin();
		hd = hdPlugin();
		installed.addAll(Arrays.asList(new Plugin()
		{
		}, gpu, fps, hd));
	}

	@Test
	public void theClassNamesAreTheRealPlugins()
	{
		assertEquals(GpuPlugin.class.getName(), SettingsReader.GPU_PLUGIN);
		assertEquals(FpsPlugin.class.getName(), SettingsReader.FPS_PLUGIN);
		assertEquals("117 HD's class at its Hub pin (contract, the API list)", "rs117.hd.HdPlugin",
			SettingsReader.HD_PLUGIN);
		assertEquals(SettingsReader.HD_PLUGIN, hd.getClass().getName());
	}

	/** C10: a setting stays stored when its plugin is off, and an off plugin caps nothing. */
	@Test
	public void inactivePluginIsNotRead()
	{
		stored.put("fpscontrol.limitFps", "true");
		stored.put("fpscontrol.maxFps", "20");
		stored.put("fpscontrol.limitFpsUnfocused", "true");
		stored.put("fpscontrol.maxFpsUnfocused", "10");
		stored.put("gpu.unlockFps", "true");
		stored.put("gpu.vsyncMode", "OFF");
		stored.put("gpu.fpsTarget", "144");
		stored.put("hd.unlockFps", "true");
		stored.put("hd.fpsTarget", "30");

		final SettingsView v = reader().read();
		assertEquals("installed but off: the client's own renderer", Renderer.CPU, v.renderer);
		assertFalse(v.fpsControlActive);
		assertFalse(v.limitFps);
		assertEquals(0, v.maxFps);
		assertFalse(v.limitFpsUnfocused);
		assertEquals(0, v.maxFpsUnfocused);
		assertFalse(v.unlockFps);
		assertEquals("", v.vsyncMode);
		assertEquals(0, v.fpsTarget);
		assertEquals("the stored limits cap nothing", CapSource.CLIENT_50, v.capSource(true));
		assertEquals(CapSource.CLIENT_50, v.capSource(false));
		assertEquals(50, v.capFps(true));
		assertTrue("no group of an off plugin is read: " + groupsRead, groupsRead.isEmpty());

		active.add(fps);
		final SettingsView on = reader().read();
		assertTrue(on.fpsControlActive);
		assertEquals("switched on, the same stored limit counts", 20, on.capFps(true));
		assertEquals(10, on.capFps(false));
		assertFalse("the renderer groups stay unread: " + groupsRead,
			groupsRead.contains("gpu") || groupsRead.contains("hd"));
	}

	@Test
	public void hdWinsOverGpu()
	{
		active.add(gpu);
		active.add(hd);
		stored.put("gpu.unlockFps", "true");
		stored.put("gpu.vsyncMode", "OFF");
		stored.put("gpu.fpsTarget", "144");
		stored.put("gpu.drawDistance", "90");
		stored.put("hd.unlockFps", "true");
		stored.put("hd.vsyncMode", "OFF");
		stored.put("hd.fpsTarget", "75");
		stored.put("hd.drawDistance", "184");
		stored.put("hd.antiAliasingMode", "MSAA_16");
		stored.put("hd.expandedMapLoadingChunks", "5");

		final SettingsView v = reader().read();
		assertEquals(Renderer.HD, v.renderer);
		assertTrue(v.unlockFps);
		assertEquals("OFF", v.vsyncMode);
		assertEquals(75, v.fpsTarget);
		assertEquals(184, v.drawDistance);
		assertEquals("MSAA_16", v.antiAliasing);
		assertEquals(5, v.expandedMapLoading);
		assertEquals(CapSource.HD_TARGET, v.capSource(true));
		assertEquals(75, v.capFps(true));
		assertFalse("the losing renderer's group is not read: " + groupsRead, groupsRead.contains("gpu"));
	}

	@Test
	public void defaultsWhenKeysAreMissing()
	{
		active.add(gpu);
		active.add(fps);
		final SettingsView g = reader().read();
		assertEquals(Renderer.GPU, g.renderer);
		assertTrue("gpu unlockFps", g.unlockFps);
		assertEquals("OFF", g.vsyncMode);
		assertEquals(60, g.fpsTarget);
		assertEquals(50, g.drawDistance);
		assertEquals("MSAA_2", g.antiAliasing);
		assertEquals(3, g.expandedMapLoading);
		assertTrue(g.fpsControlActive);
		assertFalse("fpscontrol limitFps", g.limitFps);
		assertEquals(50, g.maxFps);
		assertFalse(g.limitFpsUnfocused);
		assertEquals(50, g.maxFpsUnfocused);
		assertEquals("a default GPU player is at the target of 60 (C11)", 60, g.capFps(true));
		assertEquals(CapSource.GPU_TARGET, g.capSource(true));

		active.clear();
		active.add(hd);
		final SettingsView h = reader().read();
		assertEquals(Renderer.HD, h.renderer);
		assertFalse("hd unlockFps", h.unlockFps);
		assertEquals("ADAPTIVE", h.vsyncMode);
		assertEquals(60, h.fpsTarget);
		assertEquals(50, h.drawDistance);
		assertEquals("MSAA_8", h.antiAliasing);
		assertEquals(3, h.expandedMapLoading);
		assertEquals("a default 117 HD player is at the client's 50 (C11)", CapSource.CLIENT_50, h.capSource(true));
	}

	@Test
	public void storedValuesAreRead()
	{
		active.add(gpu);
		active.add(fps);
		stored.put("gpu.unlockFps", "false");
		stored.put("gpu.vsyncMode", "ON");
		stored.put("gpu.fpsTarget", "90");
		stored.put("gpu.drawDistance", "25");
		stored.put("gpu.antiAliasingMode", "MSAA_4");
		stored.put("gpu.expandedMapLoadingChunks", "0");
		stored.put("fpscontrol.limitFps", "true");
		stored.put("fpscontrol.maxFps", "30");
		stored.put("fpscontrol.limitFpsUnfocused", "true");
		stored.put("fpscontrol.maxFpsUnfocused", "5");

		final SettingsView v = reader().read();
		assertFalse(v.unlockFps);
		assertEquals("ON", v.vsyncMode);
		assertEquals(90, v.fpsTarget);
		assertEquals(25, v.drawDistance);
		assertEquals("MSAA_4", v.antiAliasing);
		assertEquals(0, v.expandedMapLoading);
		assertTrue(v.limitFps);
		assertEquals(30, v.maxFps);
		assertTrue(v.limitFpsUnfocused);
		assertEquals(5, v.maxFpsUnfocused);
		assertEquals("FPS Control's 30 is under the locked GPU's 50", CapSource.FPS_CONTROL, v.capSource(true));
		assertEquals(CapSource.FPS_CONTROL_UNFOCUSED, v.capSource(false));
		assertEquals(5, v.capFps(false));
	}

	@Test
	public void theCpuRendererReadsNoRendererKey()
	{
		installed.remove(hd);
		stored.put("gpu.drawDistance", "90");
		stored.put("gpu.antiAliasingMode", "MSAA_16");
		final SettingsView v = reader().read();
		assertEquals(Renderer.CPU, v.renderer);
		assertFalse(v.unlockFps);
		assertEquals("", v.vsyncMode);
		assertEquals(0, v.fpsTarget);
		assertEquals("contract 6.6: on CPU the key is not read (0)", 0, v.drawDistance);
		assertEquals("and the anti-aliasing is \"\"", "", v.antiAliasing);
		assertEquals(0, v.expandedMapLoading);
		assertTrue(groupsRead.isEmpty());

		installed.clear();
		assertEquals("no plugin installed at all", Renderer.CPU, reader().read().renderer);
	}

	@Test
	public void memorySourceIsAskedOnEveryRead()
	{
		final AtomicInteger asked = new AtomicInteger();
		final MemorySource[] now = {MemorySource.MANAGEMENT};
		final SettingsReader r = new SettingsReader(config, plugins, () -> 60, () ->
		{
			asked.incrementAndGet();
			return now[0];
		}, HEAP, Os.WINDOWS, VERSION);
		assertEquals(MemorySource.MANAGEMENT, r.read().memorySource);
		now[0] = MemorySource.RUNTIME;
		assertEquals("a systemStats swap is seen at the next read", MemorySource.RUNTIME, r.read().memorySource);
		now[0] = MemorySource.MANAGEMENT;
		assertEquals(MemorySource.MANAGEMENT, r.read().memorySource);
		assertEquals(3, asked.get());
		now[0] = null;
		assertEquals("a null source is the safe side", MemorySource.RUNTIME, r.read().memorySource);
		assertEquals(4, asked.get());
	}

	@Test
	public void refreshRateIsAskedOnEveryRead()
	{
		active.add(gpu);
		stored.put("gpu.vsyncMode", "ON");
		final AtomicInteger asked = new AtomicInteger();
		final int[] hz = {165};
		final SettingsReader r = new SettingsReader(config, plugins, () ->
		{
			asked.incrementAndGet();
			return hz[0];
		}, () -> MemorySource.MANAGEMENT, HEAP, Os.WINDOWS, VERSION);
		final SettingsView first = r.read();
		assertEquals(165, first.refreshHz);
		assertEquals("V-Sync on the 165 Hz screen", 165, first.capFps(true));
		hz[0] = 60;
		final SettingsView second = r.read();
		assertEquals("the window moved to the 60 Hz screen", 60, second.refreshHz);
		assertEquals(60, second.capFps(true));
		assertEquals(2, asked.get());
	}

	@Test
	public void valuesParseAsRuneLiteParsesThem()
	{
		active.add(gpu);
		active.add(fps);
		stored.put("gpu.unlockFps", "TRUE");
		stored.put("gpu.fpsTarget", "sixty");
		stored.put("gpu.drawDistance", " 40");
		stored.put("gpu.vsyncMode", "SOMETIMES");
		stored.put("gpu.antiAliasingMode", "SMAA");
		stored.put("fpscontrol.limitFps", "yes");
		stored.put("fpscontrol.maxFps", "2147483648");
		final SettingsView v = reader().read();
		assertTrue("Boolean.parseBoolean ignores case", v.unlockFps);
		assertEquals("not a number: the default", 60, v.fpsTarget);
		assertEquals("Integer.parseInt does not trim: the default", 50, v.drawDistance);
		assertEquals("an unknown mode: the default", "OFF", v.vsyncMode);
		assertEquals("the anti-aliasing is kept as stored", "SMAA", v.antiAliasing);
		assertFalse("anything but true is false", v.limitFps);
		assertEquals("out of int range: the default", 50, v.maxFps);
	}

	@Test
	public void theGivenNumbersReachTheView()
	{
		final SettingsView v = new SettingsReader(config, plugins, () -> 144, () -> MemorySource.RUNTIME, 1024,
			Os.LINUX, "1.13.0").read();
		assertEquals(1024, v.heapMaxMb);
		assertEquals(Os.LINUX, v.os);
		assertEquals("1.13.0", v.clientVersion);
		assertEquals(144, v.refreshHz);
		assertEquals(MemorySource.RUNTIME, v.memorySource);
	}

	@Test
	public void aThrowingSupplierDoesNotLoseTheRest()
	{
		active.add(gpu);
		final SettingsView noRefresh = new SettingsReader(config, plugins, () ->
		{
			throw new IllegalStateException("no screen");
		}, () -> MemorySource.MANAGEMENT, HEAP, Os.WINDOWS, VERSION).read();
		assertEquals("the refresh rate is unknown", 0, noRefresh.refreshHz);
		assertEquals("the rest is read", Renderer.GPU, noRefresh.renderer);
		assertEquals(MemorySource.MANAGEMENT, noRefresh.memorySource);

		final SettingsView noSource = new SettingsReader(config, plugins, () -> 60, () ->
		{
			throw new IllegalStateException("probe swapping");
		}, HEAP, Os.WINDOWS, VERSION).read();
		assertEquals("a supplier that throws is the safe side", MemorySource.RUNTIME, noSource.memorySource);
		assertEquals(Renderer.GPU, noSource.renderer);
		assertEquals(60, noSource.refreshHz);
	}

	@Test
	public void aFailedReadKeepsTheMemorySource()
	{
		final ConfigManager broken = mock(ConfigManager.class);
		when(broken.getConfiguration(anyString(), anyString())).thenThrow(new IllegalStateException("broken"));
		active.add(gpu);

		final SettingsView runtime = new SettingsReader(broken, plugins, () -> 60, () -> MemorySource.RUNTIME, HEAP,
			Os.WINDOWS, VERSION).read();
		assertEquals("the settings are unknown", Renderer.UNKNOWN, runtime.renderer);
		assertEquals(MemorySource.RUNTIME, runtime.memorySource);
		assertEquals(HEAP, runtime.heapMaxMb);
		assertEquals(Os.WINDOWS, runtime.os);

		final SettingsView management = new SettingsReader(broken, plugins, () -> 60, () -> MemorySource.MANAGEMENT,
			HEAP, Os.WINDOWS, VERSION).read();
		assertEquals(Renderer.UNKNOWN, management.renderer);
		assertEquals("the supplier's answer, not a fixed one", MemorySource.MANAGEMENT, management.memorySource);

		final SettingsView thrown = new SettingsReader(broken, plugins, () -> 60, () ->
		{
			throw new IllegalStateException();
		}, HEAP, Os.WINDOWS, VERSION).read();
		assertEquals(Renderer.UNKNOWN, thrown.renderer);
		assertEquals("the supplier itself threw: RUNTIME", MemorySource.RUNTIME, thrown.memorySource);
	}

	@Test
	public void neverThrows()
	{
		active.add(gpu);
		active.add(fps);
		final List<Runnable> breakages = new ArrayList<>();
		breakages.add(() -> when(config.getConfiguration(anyString(), anyString()))
			.thenThrow(new NoClassDefFoundError("net/runelite/client/config/ConfigData")));
		breakages.add(() -> when(plugins.getPlugins()).thenThrow(new IllegalStateException()));
		breakages.add(() -> when(plugins.getPlugins()).thenReturn(null));
		breakages.add(() -> when(plugins.getPlugins()).thenReturn(Arrays.asList(null, gpu, null)));
		breakages.add(() -> when(plugins.isPluginActive(any())).thenThrow(new NoSuchMethodError("isPluginActive")));
		breakages.add(() -> when(plugins.isPluginActive(any())).thenThrow(new RuntimeException()));
		for (Runnable breakage : breakages)
		{
			setUpQuietly();
			active.add(gpu);
			breakage.run();
			final SettingsView v = reader().read();
			assertNotNull(v);
			assertNotNull(v.renderer);
		}
		final Supplier<MemorySource> source = () ->
		{
			throw new ExceptionInInitializerError();
		};
		final IntSupplier hz = () ->
		{
			throw new UnsatisfiedLinkError();
		};
		assertNotNull(new SettingsReader(null, null, hz, source, HEAP, Os.OTHER, null).read());
		assertNotNull(new SettingsReader(null, null, null, null, 0, null, null).read());
	}

	// ---------------------------------------------------------------- helpers

	private SettingsReader reader()
	{
		return new SettingsReader(config, plugins, () -> 60, () -> MemorySource.MANAGEMENT, HEAP, Os.WINDOWS, VERSION);
	}

	private void setUpQuietly()
	{
		try
		{
			stored.clear();
			groupsRead.clear();
			active.clear();
			installed.clear();
			setUp();
		}
		catch (Exception e)
		{
			throw new AssertionError(e);
		}
	}

	private static Class<? extends Plugin> hdClass;

	/** A plugin whose class is named {@code rs117.hd.HdPlugin}, as 117 HD's is; made once. */
	private static synchronized Plugin hdPlugin() throws ReflectiveOperationException
	{
		if (hdClass == null)
		{
			hdClass = new ByteBuddy()
				.subclass(Plugin.class)
				.name(SettingsReader.HD_PLUGIN)
				.make()
				.load(SettingsReaderTest.class.getClassLoader(), ClassLoadingStrategy.Default.WRAPPER)
				.getLoaded();
		}
		return hdClass.getDeclaredConstructor().newInstance();
	}

	@Test
	public void theTestsOwnPluginsAreWhatTheyClaim()
	{
		assertTrue(installed.contains(gpu) && installed.contains(fps) && installed.contains(hd));
		final Collection<Plugin> seen = plugins.getPlugins();
		assertEquals(4, seen.size());
		active.add(hd);
		assertTrue(plugins.isPluginActive(hd));
		assertFalse(plugins.isPluginActive(gpu));
	}
}
