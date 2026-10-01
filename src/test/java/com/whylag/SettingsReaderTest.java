package com.whylag;

import com.whylag.core.CapSource;
import com.whylag.core.Os;
import com.whylag.core.Renderer;
import com.whylag.core.SettingsView;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;
import java.util.function.IntSupplier;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.plugins.fps.FpsPlugin;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * The settings reader (contract 3.7, 3.11, 7 L7; skeptic C10, C11), over a {@code ConfigManager} and two suppliers
 * (1.0.0, the Hub's rule: no plugin manager and no plugin object): whether a GPU renderer is on is the client's own
 * answer ({@code Client#isGpu()}, a supplier here); whether 117 HD and FPS Control are on is RuneLite's own stored
 * flag under the group "runelite"; a group is read ONLY while its plugin is on; with a GPU renderer on, 117 HD's
 * flag picks its group over the GPU group; RuneLite's own defaults stand for missing keys; the refresh rate and the
 * renderer are asked on every read; a failed read answers {@code SettingsView.unknown}; and a read never throws.
 *
 * <p>Choice: the flag key of FPS Control is proved against the client's own descriptor, so a client that renames it
 * fails here; 117 HD, a Hub plugin that is not on the class path, has its two keys written out.
 * <p>Choice: a flag that was never stored reads as RuneLite's own default for that plugin: FPS Control and a Hub
 * plugin are off.
 */
public class SettingsReaderTest
{
	private static final String VERSION = "1.12.38";

	private final Map<String, String> stored = new HashMap<>();
	private final List<String> groupsRead = Collections.synchronizedList(new ArrayList<>());
	/** What {@code Client#isGpu()} answers: a GPU renderer is on, unless a test says otherwise. */
	private final AtomicBoolean gpuOn = new AtomicBoolean(true);
	private ConfigManager config;

	@Before
	public void setUp()
	{
		config = mock(ConfigManager.class);
		when(config.getConfiguration(anyString(), anyString())).thenAnswer(i ->
		{
			groupsRead.add(i.getArgument(0));
			return stored.get(i.getArgument(0) + "." + i.getArgument(1));
		});
		gpuOn.set(true);
	}

	/**
	 * A plugin is on when its flag says "true": FPS Control's flag is the client's own key (the probe's
	 * {@code SettingsReaderStructureTest} reads it, and the client's default, from FPS Control's own descriptor).
	 */
	@Test
	public void theFlagKeysAndDefaultsAreWhatRuneLiteStores()
	{
		assertEquals("runelite", SettingsReader.FLAG_GROUP);
		assertEquals("FPS Control's class is FpsPlugin: its simple name, lower case", "fpsplugin",
			SettingsReader.FPS_FLAG);
		assertEquals("117 HD's class is rs117.hd.HdPlugin: its simple name, lower case", "hdplugin",
			SettingsReader.HD_FLAG);
		assertEquals("and its config group, read too", "hd", SettingsReader.HD_FLAG_ALT);
	}

	/** C10: a setting stays stored when its plugin is off, and an off plugin caps nothing. */
	@Test
	public void aPluginThatIsOffIsNotRead()
	{
		gpuOn.set(false);
		stored.put("runelite.fpsplugin", "false");
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
		assertEquals("no GPU renderer: the client's own renderer", Renderer.CPU, v.renderer);
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
		assertFalse("no group of an off plugin is read: " + groupsRead,
			groupsRead.contains("gpu") || groupsRead.contains("hd") || groupsRead.contains("fpscontrol"));

		stored.put("runelite.fpsplugin", "true");
		final SettingsView on = reader().read();
		assertTrue(on.fpsControlActive);
		assertEquals("switched on, the same stored limit counts", 20, on.capFps(true));
		assertEquals(10, on.capFps(false));
		assertFalse("the renderer groups stay unread: " + groupsRead,
			groupsRead.contains("gpu") || groupsRead.contains("hd"));
	}

	/**
	 * With a GPU renderer on, 117 HD on by the flag of its config group alone, or by its class's alone, reads as
	 * 117 HD.
	 */
	@Test
	public void hdIsOnByEitherFlag()
	{
		stored.put("runelite.hd", "true");
		assertEquals("\"hd\" true, \"hdplugin\" absent", Renderer.HD, reader().read().renderer);

		stored.remove("runelite.hd");
		stored.put("runelite.hdplugin", "true");
		assertEquals("\"hdplugin\" true, \"hd\" absent", Renderer.HD, reader().read().renderer);

		stored.put("runelite.hdplugin", "false");
		stored.put("runelite.hd", "true");
		assertEquals("either one", Renderer.HD, reader().read().renderer);

		stored.put("runelite.hd", "false");
		assertEquals("both off: a GPU renderer that is not 117 HD is the GPU plugin", Renderer.GPU,
			reader().read().renderer);
	}

	/** isGpu true with no 117 HD flag is the GPU plugin; isGpu false is the CPU renderer whatever the flags say. */
	@Test
	public void gpuIsOnByTheClientAndNotOnIsTheCpuRenderer()
	{
		assertEquals("isGpu true, no 117 HD flag", Renderer.GPU, reader().read().renderer);
		stored.put("runelite.hdplugin", "false");
		stored.put("runelite.hd", "false");
		assertEquals("isGpu true, the 117 HD flags off", Renderer.GPU, reader().read().renderer);

		gpuOn.set(false);
		assertEquals("isGpu false", Renderer.CPU, reader().read().renderer);
		stored.put("runelite.hdplugin", "true");
		stored.put("runelite.hd", "true");
		stored.put("runelite.gpuplugin", "true");
		assertEquals("isGpu false is the CPU renderer even when the flags say 117 HD and GPU", Renderer.CPU,
			reader().read().renderer);
	}

	/** A flag that was never stored is RuneLite's own default: FPS Control and 117 HD off. */
	@Test
	public void aFlagNeverStoredIsTheDefaultOfThatPlugin()
	{
		stored.clear();
		final SettingsView v = reader().read();
		assertEquals("a GPU client whose 117 HD flag was never stored runs the GPU plugin", Renderer.GPU,
			v.renderer);
		assertFalse("FPS Control is off until it is switched on", v.fpsControlActive);
		stored.put("fpscontrol.limitFps", "true");
		stored.put("fpscontrol.maxFps", "30");
		assertFalse("and its stored limit caps nothing", reader().read().fpsControlActive);

		stored.put("runelite.hdplugin", "true");
		assertEquals("117 HD wins the moment its flag is stored", Renderer.HD, reader().read().renderer);
	}

	@Test
	public void hdWinsOverGpu()
	{
		stored.put("runelite.hd", "true");
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
		stored.put("runelite.fpsplugin", "true");
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

		stored.put("runelite.fpsplugin", "false");
		stored.put("runelite.hd", "true");
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
		stored.put("runelite.fpsplugin", "true");
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
		gpuOn.set(false);
		stored.put("runelite.hd", "true");
		stored.put("gpu.drawDistance", "90");
		stored.put("gpu.antiAliasingMode", "MSAA_16");
		final SettingsView v = reader().read();
		assertEquals(Renderer.CPU, v.renderer);
		assertFalse(v.unlockFps);
		assertEquals("", v.vsyncMode);
		assertEquals(0, v.fpsTarget);
		assertEquals("contract 6.6: on CPU the key is not read (0)", 0, v.drawDistance);
		assertEquals("and the anti-aliasing is empty", "", v.antiAliasing);
		assertEquals(0, v.expandedMapLoading);
		assertFalse("no renderer group was read: " + groupsRead, groupsRead.contains("gpu")
			|| groupsRead.contains("hd"));
	}

	@Test
	public void refreshRateIsAskedOnEveryRead()
	{
		stored.put("gpu.vsyncMode", "ON");
		final AtomicInteger asked = new AtomicInteger();
		final int[] hz = {165};
		final SettingsReader r = new SettingsReader(config, gpuOn::get, () ->
		{
			asked.incrementAndGet();
			return hz[0];
		}, Os.WINDOWS, VERSION);
		final SettingsView first = r.read();
		assertEquals(165, first.refreshHz);
		assertEquals("V-Sync on the 165 Hz screen", 165, first.capFps(true));
		hz[0] = 60;
		final SettingsView second = r.read();
		assertEquals("the window moved to the 60 Hz screen", 60, second.refreshHz);
		assertEquals(60, second.capFps(true));
		assertEquals(2, asked.get());
	}

	/** The renderer is the client's answer of now: asked on every read, so a switch shows at the next one. */
	@Test
	public void theRendererIsAskedOnEveryRead()
	{
		final AtomicInteger asked = new AtomicInteger();
		final BooleanSupplier isGpu = () ->
		{
			asked.incrementAndGet();
			return gpuOn.get();
		};
		final SettingsReader r = new SettingsReader(config, isGpu, () -> 60, Os.WINDOWS, VERSION);
		assertEquals(Renderer.GPU, r.read().renderer);
		gpuOn.set(false);
		assertEquals("the renderer went to the CPU one", Renderer.CPU, r.read().renderer);
		gpuOn.set(true);
		stored.put("runelite.hd", "true");
		assertEquals("and back, with 117 HD on", Renderer.HD, r.read().renderer);
		assertEquals(3, asked.get());
	}

	@Test
	public void valuesParseAsRuneLiteParsesThem()
	{
		stored.put("runelite.fpsplugin", "true");
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
	public void aFlagThatIsNotTrueIsOff()
	{
		stored.put("runelite.hd", "yes");
		assertEquals("Boolean.parseBoolean: only true is true", Renderer.GPU, reader().read().renderer);
		stored.put("runelite.hd", "TRUE");
		assertEquals(Renderer.HD, reader().read().renderer);
	}

	@Test
	public void theGivenNumbersReachTheView()
	{
		final SettingsView v = new SettingsReader(config, gpuOn::get, () -> 144, Os.LINUX, "1.13.0").read();
		assertEquals(Os.LINUX, v.os);
		assertEquals("1.13.0", v.clientVersion);
		assertEquals(144, v.refreshHz);
	}

	@Test
	public void aThrowingSupplierDoesNotLoseTheRest()
	{
		final SettingsView noRefresh = new SettingsReader(config, gpuOn::get, () ->
		{
			throw new IllegalStateException("no screen");
		}, Os.WINDOWS, VERSION).read();
		assertEquals("the refresh rate is unknown", 0, noRefresh.refreshHz);
		assertEquals("the rest is read", Renderer.GPU, noRefresh.renderer);
	}

	@Test
	public void aFailedReadIsUnknown()
	{
		final ConfigManager broken = mock(ConfigManager.class);
		when(broken.getConfiguration(anyString(), anyString())).thenThrow(new IllegalStateException("broken"));

		final SettingsView v = new SettingsReader(broken, gpuOn::get, () -> 60, Os.WINDOWS, VERSION).read();
		assertEquals("the settings are unknown", Renderer.UNKNOWN, v.renderer);
		assertEquals(Os.WINDOWS, v.os);
		assertEquals("", v.clientVersion);

		final SettingsView noAnswer = new SettingsReader(config, () ->
		{
			throw new IllegalStateException("no answer");
		}, () -> 60, Os.WINDOWS, VERSION).read();
		assertEquals("a renderer supplier that throws fails the read as a whole", Renderer.UNKNOWN,
			noAnswer.renderer);
	}

	@Test
	public void neverThrows()
	{
		final List<Runnable> breakages = new ArrayList<>();
		breakages.add(() -> when(config.getConfiguration(anyString(), anyString()))
			.thenThrow(new NoClassDefFoundError("net/runelite/client/config/ConfigData")));
		breakages.add(() -> when(config.getConfiguration(anyString(), anyString())).thenThrow(new RuntimeException()));
		breakages.add(() -> when(config.getConfiguration(anyString(), anyString())).thenReturn(null));
		breakages.add(() -> when(config.getConfiguration(anyString(), anyString())).thenReturn("not a number"));
		for (Runnable breakage : breakages)
		{
			setUp();
			breakage.run();
			final SettingsView v = reader().read();
			assertNotNull(v);
			assertNotNull(v.renderer);
		}
		final IntSupplier hz = () ->
		{
			throw new UnsatisfiedLinkError();
		};
		assertNotNull(new SettingsReader(null, gpuOn::get, hz, Os.OTHER, null).read());
		assertNotNull(new SettingsReader(null, null, null, null, null).read());
	}

	// ---------------------------------------------------------------- helpers

	private SettingsReader reader()
	{
		return new SettingsReader(config, gpuOn::get, () -> 60, Os.WINDOWS, VERSION);
	}
}
