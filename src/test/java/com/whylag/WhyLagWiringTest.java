package com.whylag;

import com.google.inject.Provides;
import com.whylag.core.Answer;
import com.whylag.core.BadgeStyle;
import com.whylag.core.BadgeView;
import com.whylag.core.Cause;
import com.whylag.core.Confidence;
import com.whylag.core.Detector;
import com.whylag.core.DetectorListener;
import com.whylag.core.Flags;
import com.whylag.core.Icon;
import com.whylag.core.Judge;
import com.whylag.core.LagEngine;
import com.whylag.core.LagEvent;
import com.whylag.core.Level;
import com.whylag.core.MemorySource;
import com.whylag.core.PanelSnapshot;
import com.whylag.core.ReportText;
import com.whylag.core.Session;
import com.whylag.core.SettingsView;
import com.whylag.core.SnapshotSource;
import com.whylag.core.State;
import com.whylag.core.Thresholds;
import com.whylag.core.Trigger;
import com.whylag.core.Verdict;
import com.whylag.core.WhenSmooth;
import com.whylag.host.GcSink;
import com.whylag.host.HostProbe;
import com.whylag.ui.WhyLagPanel;
import java.awt.Canvas;
import java.awt.DisplayMode;
import java.awt.GraphicsConfiguration;
import java.awt.GraphicsDevice;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Properties;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import javax.inject.Inject;
import javax.inject.Named;
import javax.swing.SwingUtilities;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.NPC;
import net.runelite.api.Player;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.BeforeRender;
import net.runelite.api.events.FocusChanged;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.NpcSpawned;
import net.runelite.api.events.PlayerSpawned;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.chat.ChatMessageManager;
import net.runelite.client.chat.QueuedMessage;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.events.PluginChanged;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.PluginManager;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.client.ui.overlay.infobox.InfoBox;
import net.runelite.client.ui.overlay.infobox.InfoBoxManager;
import net.runelite.client.ui.overlay.tooltip.TooltipManager;
import org.junit.After;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

/**
 * The wiring of 2h Why Lag (contract 7, L9): the descriptor, the plugin's own executor and its 1 s task, the
 * shut-down order, the developer bridge, the panel's snapshots, the handlers that only write a volatile, the
 * settings reads, the host probe swap, the scene counts, the start-up read, and the game badge fed every step.
 *
 * <p>No client boots and no Guice injector builds the plugin: the injected fields are filled by reflection and the
 * handlers are called directly, which is how RuneLite calls them. The clock, the host probe chooser, the engine, the
 * executor and the hop to the Swing thread are the plugin's package-private seams. {@code startUp} and
 * {@code shutDown} run on the Swing thread, as RuneLite runs them. Every static slot is cleared in {@code @After}.
 */
public class WhyLagWiringTest
{
	private static final long SECOND = 1_000_000_000L;
	private static final long MS = 1_000_000L;

	@After
	public void clearStatics()
	{
		WhyLagDevBridge.handle = null;
	}

	// ---------------------------------------------------------------- descriptor

	@Test
	public void descriptorAndSuperclass() throws Exception
	{
		final PluginDescriptor d = WhyLagPlugin.class.getAnnotation(PluginDescriptor.class);
		assertNotNull(d);
		assertEquals("2h Why Lag", d.name());
		assertEquals("Tells you what caused the lag: frames, ticks, ping or memory", d.description());
		assertEquals(Arrays.asList("lag", "ping", "fps", "tick", "freeze", "stutter", "memory"),
			Arrays.asList(d.tags()));
		// The loader checks the DIRECT superclass; an intermediate base class makes it skip the plugin silently.
		assertEquals(Plugin.class, WhyLagPlugin.class.getSuperclass());

		final Method provides = WhyLagPlugin.class.getDeclaredMethod("provideConfig", ConfigManager.class);
		assertNotNull(provides.getAnnotation(Provides.class));
		assertEquals(WhyLagConfig.class, provides.getReturnType());

		final Properties p = new Properties();
		try (InputStream in = WhyLagWiringTest.class.getResourceAsStream("/runelite-plugin.properties"))
		{
			assertNotNull(in);
			p.load(in);
		}
		final List<String> names = Arrays.stream(p.getProperty("plugins").split(","))
			.map(String::trim).collect(Collectors.toList());
		// The combined workspace lists four plugins; the standalone Hub repository lists this one alone. Both
		// pass: Why Lag is listed, and last (it was added after the three that share this workspace).
		assertTrue(names.toString(), names.contains(WhyLagPlugin.class.getName()));
		assertEquals("Why Lag is the last plugin listed", WhyLagPlugin.class.getName(), names.get(names.size() - 1));
	}

	// ---------------------------------------------------------------- the executor and the task (T5, T6)

	@Test
	public void executorIsOursAndNamed() throws Exception
	{
		for (Field f : WhyLagPlugin.class.getDeclaredFields())
		{
			final boolean injected = f.getAnnotation(Inject.class) != null
				|| f.getAnnotation(com.google.inject.Inject.class) != null;
			assertFalse("RuneLite's shared executor is never injected: " + f,
				injected && ExecutorService.class.isAssignableFrom(f.getType()));
		}

		final Fixture f = new Fixture(true);
		// the plugin's own executor, not the fixture's mock
		f.plugin.executors = f.ownExecutors;
		onEdt(f.plugin::startUp);
		try
		{
			final ScheduledExecutorService ex = (ScheduledExecutorService) field(f.plugin, "executor");
			assertNotNull(ex);
			final Thread t = ex.submit(Thread::currentThread).get(5, TimeUnit.SECONDS);
			assertEquals(WhyLagPlugin.SAMPLER_THREAD, t.getName());
			assertEquals("whylag-sampler", t.getName());
			assertTrue("the sampler must never keep the client's JVM alive", t.isDaemon());
			assertEquals(t.getId(), WhyLagDevBridge.handle.samplerThreadId());
			assertSame("one thread, the same for every task", t, ex.submit(Thread::currentThread).get(5,
				TimeUnit.SECONDS));

			onEdt(f.plugin::shutDown);
			assertTrue(ex.isShutdown());
		}
		finally
		{
			onEdt(f.plugin::shutDown);
		}
	}

	@Test
	public void theTaskRunsAtAFixedRate() throws Exception
	{
		final Fixture f = new Fixture(false);
		onEdt(f.plugin::startUp);
		final Runnable task = f.scheduledTask();
		verify(f.executor).scheduleAtFixedRate(any(Runnable.class), eq(1L), eq(1L), eq(TimeUnit.SECONDS));
		verify(f.executor, never()).scheduleWithFixedDelay(any(Runnable.class), anyLong(), anyLong(),
			any(TimeUnit.class));
		verify(f.executor, never()).schedule(any(Runnable.class), anyLong(), any(TimeUnit.class));

		// The task scheduled is the sampler's run: it reads the host probe.
		assertEquals(0, f.probe.heapReads.get());
		f.at(1);
		task.run();
		assertEquals(1, f.probe.heapReads.get());
		onEdt(f.plugin::shutDown);
	}

	@Test
	public void taskSurvivesAThrowingProbe() throws Exception
	{
		final Fixture f = new Fixture(false);
		final FakeJudge judge = new FakeJudge();
		f.plugin.engines = s -> new LagEngine(s, new FakeDetector(), judge, f.snapshots);
		onEdt(f.plugin::startUp);
		final Runnable task = f.scheduledTask();
		final int stepsBefore = judge.currents.get();

		f.probe.throwing = true;
		for (int i = 1; i <= 3; i++)
		{
			f.at(i);
			task.run();
			assertEquals("run " + i + " reached the probe", i, f.probe.heapReads.get());
		}
		assertEquals("a throwing probe stops the run before the step", stepsBefore, judge.currents.get());
		assertTrue("both kinds were thrown: a RuntimeException and a LinkageError", f.probe.thrownKinds.size() == 2);

		// ... and the task carries on: once the probe answers again, the next run steps.
		f.probe.throwing = false;
		f.at(4);
		task.run();
		assertEquals(stepsBefore + 1, judge.currents.get());
		onEdt(f.plugin::shutDown);
	}

	// ---------------------------------------------------------------- shutDown (T7) and the bridge

	@Test
	public void shutDownCancelsEverything() throws Exception
	{
		final Fixture f = new Fixture(false);
		final HostProbe probe = probeMock();
		f.plugin.hostProbes = on -> probe;
		onEdt(f.plugin::startUp);
		final NavigationButton nav = f.nav();
		final Overlay overlay = f.overlay();
		final WhyLagPanel panel = (WhyLagPanel) field(f.plugin, "panel");
		onEdt(panel::onActivate);
		assertTrue(panel.isActive());
		f.posted.clear();

		onEdt(f.plugin::shutDown);
		final InOrder order = inOrder(f.future, f.executor, probe, f.clientToolbar, f.overlayManager);
		order.verify(f.future).cancel(anyBoolean());
		order.verify(f.executor).shutdownNow();
		order.verify(probe).stop();
		order.verify(f.clientToolbar).removeNavigation(nav);
		order.verify(f.overlayManager).remove(overlay);
		verify(f.executor, never()).awaitTermination(anyLong(), any(TimeUnit.class));
		verify(f.executor, never()).shutdown();

		// The panel's timer is stopped through its own onDeactivate, on the Swing thread.
		assertEquals(1, f.posted.size());
		onEdt(f.posted.get(0));
		assertFalse(panel.isActive());
		assertEquals(1, panel.deactivations());
		assertNull(field(f.plugin, "executor"));
		assertNull(field(f.plugin, "navButton"));
		assertNull(field(f.plugin, "overlay"));
	}

	@Test
	public void bridgeIsNullOutsideDeveloperMode() throws Exception
	{
		final Field dev = WhyLagPlugin.class.getDeclaredField("developerMode");
		assertEquals(boolean.class, dev.getType());
		assertNotNull("an injection point", dev.getAnnotation(Inject.class));
		assertNotNull("RuneLite binds the constant by this name", dev.getAnnotation(Named.class));
		assertEquals("developerMode", dev.getAnnotation(Named.class).value());

		final Fixture off = new Fixture(false);
		assertNull("constructing the plugin installs nothing", WhyLagDevBridge.handle);
		onEdt(off.plugin::startUp);
		assertNull("a client that is not in developer mode gets no bridge", WhyLagDevBridge.handle);
		assertFalse("and no self timing", off.engine().selfTimer().on());
		onEdt(off.plugin::shutDown);
		assertNull(WhyLagDevBridge.handle);

		final Fixture on = new Fixture(true);
		onEdt(on.plugin::startUp);
		final DevHandle h = WhyLagDevBridge.handle;
		assertNotNull("developer mode is the only thing that fills the slot", h);
		assertSame(on.engine(), h.source());
		assertSame(field(on.plugin, "panel"), h.control());
		assertTrue("the graphs are open when the plugin starts (the user, 2026-09-29)", h.control().graphsOpen());
		assertFalse("the lag list stays folded", h.control().lagsOpen());
		assertSame(on.probe, h.hostProbe());
		assertNotNull(h.settings());
		assertSame(BadgeView.HIDDEN, h.badge());
		assertTrue("developer mode switches the self timer on", on.engine().selfTimer().on());
		onEdt(on.plugin::shutDown);
		assertNull(WhyLagDevBridge.handle);
	}

	@Test
	public void bridgeIsClearedFirst() throws Exception
	{
		final Fixture f = new Fixture(true);
		final HostProbe probe = probeMock();
		f.plugin.hostProbes = on -> probe;
		onEdt(f.plugin::startUp);
		final NavigationButton nav = f.nav();
		final Overlay overlay = f.overlay();
		assertNotNull(WhyLagDevBridge.handle);

		final List<String> seen = new ArrayList<>();
		doAnswer(inv ->
		{
			seen.add("cancel " + WhyLagDevBridge.handle);
			return true;
		}).when(f.future).cancel(anyBoolean());
		doAnswer(inv ->
		{
			seen.add("shutdownNow " + WhyLagDevBridge.handle);
			return new ArrayList<Runnable>();
		}).when(f.executor).shutdownNow();
		doAnswer(inv ->
		{
			seen.add("stop " + WhyLagDevBridge.handle);
			return null;
		}).when(probe).stop();
		doAnswer(inv ->
		{
			seen.add("removeNavigation " + WhyLagDevBridge.handle);
			return null;
		}).when(f.clientToolbar).removeNavigation(nav);
		doAnswer(inv ->
		{
			seen.add("remove " + WhyLagDevBridge.handle);
			return true;
		}).when(f.overlayManager).remove(overlay);

		onEdt(f.plugin::shutDown);
		assertEquals(Arrays.asList("cancel null", "shutdownNow null", "stop null", "removeNavigation null",
			"remove null"), seen);

		// shutDown clears it even on a plugin that never started: it is the first statement, before every guard.
		WhyLagDevBridge.handle = mock(DevHandle.class);
		onEdt(new WhyLagPlugin()::shutDown);
		assertNull(WhyLagDevBridge.handle);
	}

	// ---------------------------------------------------------------- the panel (T9) and its actions

	@Test
	public void noSnapshotWhileThePanelIsHidden() throws Exception
	{
		final Fixture f = new Fixture(false);
		f.plugin.engines = s -> new LagEngine(s, new FakeDetector(), new FakeJudge(), f.snapshots);
		onEdt(f.plugin::startUp);
		final WhyLagPanel panel = (WhyLagPanel) field(f.plugin, "panel");
		for (int i = 1; i <= 3; i++)
		{
			f.at(i);
			f.plugin.sampleOnce();
		}
		assertEquals("no snapshot while the panel is hidden", 0, f.builds.get());
		assertTrue("and nothing posted to the Swing thread", f.posted.isEmpty());

		// Opening the panel asks for one snapshot at once, on the sampler thread.
		onEdt(panel::onActivate);
		final ArgumentCaptor<Runnable> asked = ArgumentCaptor.forClass(Runnable.class);
		verify(f.executor).execute(asked.capture());
		assertEquals("nothing is built on the Swing thread", 0, f.builds.get());
		asked.getValue().run();
		assertEquals(1, f.builds.get());
		assertEquals(1, f.posted.size());

		f.at(4);
		f.plugin.sampleOnce();
		assertEquals("one a second while it shows", 2, f.builds.get());
		assertEquals(2, f.posted.size());

		onEdt(panel::onDeactivate);
		f.at(5);
		f.plugin.sampleOnce();
		f.at(6);
		f.plugin.sampleOnce();
		assertEquals("hidden again: none", 2, f.builds.get());
		assertEquals(2, f.posted.size());
		onEdt(f.plugin::shutDown);
	}

	@Test
	public void copyReportIsReportText() throws Exception
	{
		final Fixture f = new Fixture(false);
		onEdt(f.plugin::startUp);
		f.at(1);
		f.plugin.sampleOnce();
		final PanelSnapshot s = f.engine().snapshot(10, f.wall());
		final PanelActions actions = (PanelActions) field(f.plugin, "actions");
		final String report = actions.report(s);
		assertFalse(report.isEmpty());
		assertEquals(ReportText.of(s), report);
		onEdt(f.plugin::shutDown);
	}

	/**
	 * The plugin runs ONE thread, the sampler's: start-up makes one executor and schedules one task on it, and shut-down
	 * stops it. The world test's second thread was parked on 2026-09-30, and nothing of it is left in the plugin.
	 */
	@Test
	public void theSamplerIsTheOnlyThread() throws Exception
	{
		final Fixture f = new Fixture(false);
		for (Field field : WhyLagPlugin.class.getDeclaredFields())
		{
			assertFalse(field.getName(), field.getName().toLowerCase(Locale.ROOT).contains("world"));
		}
		onEdt(f.plugin::startUp);
		verify(f.executor, times(1)).scheduleAtFixedRate(any(Runnable.class), anyLong(), anyLong(),
			any(TimeUnit.class));
		verifyNoMoreInteractions(f.executor);

		onEdt(f.plugin::shutDown);
		verify(f.executor).shutdownNow();
		verify(f.future).cancel(false);
		verifyNoMoreInteractions(f.executor);
	}

	// ---------------------------------------------------------------- the handlers

	@Test
	public void edtEventsOnlySetAFlag() throws Exception
	{
		for (String name : new String[]{"settingsDirty", "probeSwapAsked", "inGame"})
		{
			assertTrue(name + " is volatile", Modifier.isVolatile(WhyLagPlugin.class.getDeclaredField(name)
				.getModifiers()));
		}
		final Fixture f = new Fixture(false);
		onEdt(f.plugin::startUp);
		f.at(1);
		f.plugin.sampleOnce();
		assertFalse("the first run read the settings", (boolean) field(f.plugin, "settingsDirty"));
		f.clearMocks();
		final int heapReads = f.probe.heapReads.get();

		f.plugin.onPluginChanged(new PluginChanged(mock(Plugin.class), true));
		assertTrue((boolean) field(f.plugin, "settingsDirty"));
		set(f.plugin, "settingsDirty", false);
		f.plugin.onConfigChanged(configChanged("gpu", "fpsTarget"));
		assertTrue("any group feeds the settings", (boolean) field(f.plugin, "settingsDirty"));
		assertFalse((boolean) field(f.plugin, "probeSwapAsked"));
		f.plugin.onConfigChanged(configChanged(WhyLagConfig.GROUP, "systemStats"));
		assertTrue("the swap is only asked for here; the sampler does it",
			(boolean) field(f.plugin, "probeSwapAsked"));
		final FocusChanged focus = new FocusChanged();
		focus.setFocused(false);
		f.plugin.onFocusChanged(focus);

		verifyNoInteractions(f.client, f.clientThread, f.clientToolbar, f.configManager, f.pluginManager, f.config,
			f.overlayManager, f.tooltipManager, f.chat, f.executor);
		assertEquals("the host probe is not touched", heapReads, f.probe.heapReads.get());
		assertEquals(1, f.probe.starts.get());
		assertEquals(0, f.probe.stops.get());

		// The focus reached the engine: the second the next frames close is not FOCUSED (a new one would be).
		final Session s = f.engine().session();
		f.closeSecond(100);
		assertFalse(Flags.has(s.seconds.flags(100), Flags.FOCUSED));
		// ... and a second closed after the focus came back is.
		focus.setFocused(true);
		f.plugin.onFocusChanged(focus);
		f.engine().frame(f.base + 102 * SECOND + 100 * MS, 0, -1);
		assertTrue(Flags.has(s.seconds.flags(101), Flags.FOCUSED));
		onEdt(f.plugin::shutDown);
	}

	@Test
	public void frameAndTickSubscribersRunFirst() throws Exception
	{
		final Subscribe frame = WhyLagPlugin.class.getMethod("onBeforeRender", BeforeRender.class)
			.getAnnotation(Subscribe.class);
		final Subscribe tick = WhyLagPlugin.class.getMethod("onGameTick", GameTick.class)
			.getAnnotation(Subscribe.class);
		assertNotNull(frame);
		assertNotNull(tick);
		assertEquals(100f, frame.priority(), 0f);
		assertEquals(100f, tick.priority(), 0f);
		// Higher runs first; the others keep RuneLite's default.
		assertEquals(0f, WhyLagPlugin.class.getMethod("onGameStateChanged", GameStateChanged.class)
			.getAnnotation(Subscribe.class).priority(), 0f);
	}

	@Test
	public void refreshRateIsReadAgain() throws Exception
	{
		final Fixture f = new Fixture(true);
		final AtomicReference<DisplayMode> mode = new AtomicReference<>(new DisplayMode(2560, 1440, 32, 165));
		final Canvas canvas = mock(Canvas.class);
		final GraphicsConfiguration gc = mock(GraphicsConfiguration.class);
		final GraphicsDevice device = mock(GraphicsDevice.class);
		when(f.client.getCanvas()).thenReturn(canvas);
		when(canvas.getGraphicsConfiguration()).thenReturn(gc);
		when(gc.getDevice()).thenReturn(device);
		when(device.getDisplayMode()).thenAnswer(inv -> mode.get());
		onEdt(f.plugin::startUp);
		assertEquals("unknown until the first read", 0, WhyLagDevBridge.handle.settings().refreshHz);

		f.at(1);
		f.plugin.sampleOnce();
		assertEquals(165, WhyLagDevBridge.handle.settings().refreshHz);
		verify(f.client, times(1)).getCanvas();

		// The window moves to the 60 Hz screen: nothing reads it until REFRESH_REREAD_S have passed.
		mode.set(new DisplayMode(1920, 1080, 32, 60));
		for (int k = 2; k <= Thresholds.REFRESH_REREAD_S; k++)
		{
			f.at(k);
			f.plugin.sampleOnce();
		}
		assertEquals(165, WhyLagDevBridge.handle.settings().refreshHz);
		verify(f.client, times(1)).getCanvas();

		f.at(1 + Thresholds.REFRESH_REREAD_S);
		f.plugin.sampleOnce();
		assertEquals("read again after REFRESH_REREAD_S", 60, WhyLagDevBridge.handle.settings().refreshHz);
		verify(f.client, times(2)).getCanvas();

		// A setting that changed is read at the next run, whatever the clock says.
		mode.set(new DisplayMode(1920, 1080, 32, 144));
		f.plugin.onConfigChanged(configChanged("gpu", "vsyncMode"));
		f.at(2 + Thresholds.REFRESH_REREAD_S);
		f.plugin.sampleOnce();
		assertEquals(144, WhyLagDevBridge.handle.settings().refreshHz);

		// A screen that does not say, or no canvas, reads as 0.
		mode.set(new DisplayMode(1920, 1080, 32, DisplayMode.REFRESH_RATE_UNKNOWN));
		f.plugin.onConfigChanged(configChanged("gpu", "vsyncMode"));
		f.at(3 + Thresholds.REFRESH_REREAD_S);
		f.plugin.sampleOnce();
		assertEquals(0, WhyLagDevBridge.handle.settings().refreshHz);
		onEdt(f.plugin::shutDown);
	}

	@Test
	public void systemStatsSwapRestartsTheProbe() throws Exception
	{
		final Fixture f = new Fixture(true);
		final FakeProbe a = new FakeProbe(MemorySource.MANAGEMENT);
		final FakeProbe b = new FakeProbe(MemorySource.RUNTIME);
		final List<Boolean> asked = new ArrayList<>();
		f.plugin.hostProbes = on ->
		{
			asked.add(on);
			return asked.size() == 1 ? a : b;
		};
		when(f.config.systemStats()).thenReturn(true);
		onEdt(f.plugin::startUp);
		final Session s = f.engine().session();
		assertEquals(Arrays.asList(true), asked);
		assertEquals(1, a.starts.get());
		assertEquals(s.startNanos, a.startNanos);
		f.at(1);
		f.plugin.sampleOnce();
		assertEquals(MemorySource.MANAGEMENT, WhyLagDevBridge.handle.settings().memorySource);

		// The user switches "Exact memory pauses" off: the handler only asks.
		when(f.config.systemStats()).thenReturn(false);
		f.plugin.onConfigChanged(configChanged(WhyLagConfig.GROUP, "systemStats"));
		assertEquals(0, a.stops.get());
		assertEquals(1, asked.size());

		// The sampler swaps: the old probe stopped, a new one made from the setting and started on this session.
		f.at(2);
		f.plugin.sampleOnce();
		assertEquals(1, a.stops.get());
		assertEquals(Arrays.asList(true, false), asked);
		assertEquals(1, b.starts.get());
		assertEquals(s.startNanos, b.startNanos);
		assertTrue("the old probe was stopped before the new one started", a.stoppedAt < b.startedAt);
		assertEquals("the new probe is read in the same run", 1, b.heapReads.get());
		assertSame(b, WhyLagDevBridge.handle.hostProbe());
		assertEquals("the memory source follows the probe", MemorySource.RUNTIME,
			WhyLagDevBridge.handle.settings().memorySource);

		// The new probe's pauses reach this session's ring.
		b.sink.gcPause(1500, 150, 320);
		assertEquals(0, s.gcs.head());
		assertEquals(150, s.gcs.durationMs(0));
		assertEquals(1500, s.gcs.startMs(0));

		// Nothing more swaps without being asked, and another key asks nothing.
		f.plugin.onConfigChanged(configChanged(WhyLagConfig.GROUP, "badgeShow"));
		f.at(3);
		f.plugin.sampleOnce();
		assertEquals(2, asked.size());
		assertEquals(0, b.stops.get());
		onEdt(f.plugin::shutDown);
		assertEquals("shutDown stops the probe in use", 1, b.stops.get());
		assertEquals(1, a.stops.get());
	}

	@Test
	public void sceneIsHandedOverEveryFifthTick() throws Exception
	{
		assertEquals(5, Thresholds.SCENE_EVERY_TICKS);
		final Fixture f = new Fixture(false);
		final Player player = mock(Player.class);
		final WorldPoint where = new WorldPoint(3200, 3200, 0);
		when(player.getWorldLocation()).thenReturn(where);
		when(f.client.getLocalPlayer()).thenReturn(player);
		onEdt(f.plugin::startUp);
		final Session s = f.engine().session();

		for (int i = 0; i < 3; i++)
		{
			f.plugin.onPlayerSpawned(new PlayerSpawned(player));
		}
		f.plugin.onNpcSpawned(new NpcSpawned(mock(NPC.class)));
		f.plugin.onNpcSpawned(new NpcSpawned(mock(NPC.class)));

		f.plugin.onGameTick(new GameTick());
		verify(f.client, times(1)).getLocalPlayer();
		f.plugin.onPlayerSpawned(new PlayerSpawned(player));
		for (int i = 2; i <= 5; i++)
		{
			f.plugin.onGameTick(new GameTick());
		}
		verify(f.client, times(1)).getLocalPlayer();
		// A second closed now takes the counts handed over at tick 1: ticks 2 to 5 handed nothing over.
		f.closeSecond(100);
		assertEquals(3, s.seconds.players(100));
		assertEquals(2, s.seconds.npcs(100));
		assertEquals(where.getRegionID(), s.seconds.region(100));

		f.plugin.onGameTick(new GameTick());
		verify(f.client, times(2)).getLocalPlayer();
		for (int i = 7; i <= 10; i++)
		{
			f.plugin.onGameTick(new GameTick());
		}
		verify(f.client, times(2)).getLocalPlayer();
		f.engine().frame(f.base + 102 * SECOND + 100 * MS, 0, -1);
		assertEquals("tick 6 handed the fourth player over", 4, s.seconds.players(101));
		f.plugin.onGameTick(new GameTick());
		verify(f.client, times(3)).getLocalPlayer();
		assertEquals("every tick reached the engine", 11, s.ticks.head() + 1);

		// No player (the login screen): region 0, and nothing fails.
		when(f.client.getLocalPlayer()).thenReturn(null);
		for (int i = 12; i <= 16; i++)
		{
			f.plugin.onGameTick(new GameTick());
		}
		f.engine().frame(f.base + 103 * SECOND + 100 * MS, 0, -1);
		assertEquals(0, s.seconds.region(102));

		// LOADING keeps the scene's counts (the bundled NPC Indicators idiom); a hop resets them.
		f.plugin.onGameStateChanged(gameState(GameState.LOADING));
		for (int i = 17; i <= 21; i++)
		{
			f.plugin.onGameTick(new GameTick());
		}
		f.engine().frame(f.base + 104 * SECOND + 100 * MS, 0, -1);
		assertEquals(4, s.seconds.players(103));
		f.plugin.onGameStateChanged(gameState(GameState.HOPPING));
		for (int i = 22; i <= 26; i++)
		{
			f.plugin.onGameTick(new GameTick());
		}
		f.engine().frame(f.base + 105 * SECOND + 100 * MS, 0, -1);
		assertEquals(0, s.seconds.players(104));
		assertEquals(0, s.seconds.npcs(104));
		onEdt(f.plugin::shutDown);
	}

	@Test
	public void startUpHandsTheGameStateToTheEngine() throws Exception
	{
		final Fixture f = new Fixture(false);
		when(f.client.getGameState()).thenReturn(GameState.LOGGED_IN);
		when(f.client.getWorld()).thenReturn(416);
		onEdt(f.plugin::startUp);
		final Session s = f.engine().session();
		verify(f.client, never()).getGameState();
		assertEquals("nothing handed over before the client thread runs the read", Session.NEVER,
			s.loggedInSinceSec());

		final ArgumentCaptor<Runnable> read = ArgumentCaptor.forClass(Runnable.class);
		verify(f.clientThread).invokeLater(read.capture());
		final long before = s.secOf(System.nanoTime());
		read.getValue().run();
		final long after = s.secOf(System.nanoTime());
		final long since = s.loggedInSinceSec();
		assertTrue("the plugin was switched on while logged in: the warm-up runs from the read's second, " + since,
			since >= before && since <= after);
		assertTrue((boolean) field(f.plugin, "inGame"));

		f.closeSecond(100);
		assertEquals(State.LOGGED_IN, s.seconds.state(100));
		assertEquals(416, s.seconds.world(100));
		onEdt(f.plugin::shutDown);
	}

	@Test
	public void connectionProbeGetsTheInGameFlag() throws Exception
	{
		final Fixture f = new Fixture(false);
		final Thread clientThread = Thread.currentThread();
		final AtomicReference<GameState> state = new AtomicReference<>(GameState.LOGGED_IN);
		final List<String> offThread = new ArrayList<>();
		when(f.client.getGameState()).thenAnswer(inv ->
		{
			if (Thread.currentThread() != clientThread)
			{
				synchronized (offThread)
				{
					offThread.add(Thread.currentThread().getName());
				}
			}
			return state.get();
		});
		onEdt(f.plugin::startUp);

		f.at(1);
		onSamplerThread(f.plugin::sampleOnce);
		verify(f.client, never()).getSocketFD();

		f.plugin.onGameStateChanged(gameState(GameState.LOGGED_IN));
		f.at(2);
		onSamplerThread(f.plugin::sampleOnce);
		verify(f.client, times(1)).getSocketFD();

		f.plugin.onGameStateChanged(gameState(GameState.LOGIN_SCREEN));
		f.at(3);
		onSamplerThread(f.plugin::sampleOnce);
		verify(f.client, times(1)).getSocketFD();

		// The start-up read writes the same flag, on the client thread.
		final ArgumentCaptor<Runnable> read = ArgumentCaptor.forClass(Runnable.class);
		verify(f.clientThread).invokeLater(read.capture());
		read.getValue().run();
		f.at(4);
		onSamplerThread(f.plugin::sampleOnce);
		verify(f.client, times(2)).getSocketFD();

		state.set(GameState.HOPPING);
		read.getValue().run();
		f.at(5);
		onSamplerThread(f.plugin::sampleOnce);
		verify(f.client, times(2)).getSocketFD();

		assertTrue("the game state was read off the client thread by " + offThread, offThread.isEmpty());
		onEdt(f.plugin::shutDown);
		assertTrue("... nor by startUp or shutDown: " + offThread, offThread.isEmpty());
	}

	// ---------------------------------------------------------------- the game badge (T20, P2)

	@Test
	public void overlayIsAddedAndRemoved() throws Exception
	{
		final Fixture f = new Fixture(false);
		onEdt(f.plugin::startUp);
		final ArgumentCaptor<Overlay> added = ArgumentCaptor.forClass(Overlay.class);
		verify(f.overlayManager, times(1)).add(added.capture());
		assertTrue(added.getValue() instanceof BadgeOverlay);
		verify(f.overlayManager, never()).remove(any());

		onEdt(f.plugin::shutDown);
		verify(f.overlayManager).remove(same(added.getValue()));
		verify(f.overlayManager, times(1)).remove(any());
	}

	/** Addendum D: the two styles without words are a real infobox, added at start-up and removed at shut-down. */
	@Test
	public void theBadgeInfoBoxIsAddedAtStartUpAndRemovedAtShutDown() throws Exception
	{
		final Fixture f = new Fixture(false);
		onEdt(f.plugin::startUp);
		final ArgumentCaptor<InfoBox> added = ArgumentCaptor.forClass(InfoBox.class);
		verify(f.infoBoxManager, times(1)).addInfoBox(added.capture());
		assertTrue(added.getValue() instanceof BadgeInfoBox);
		verify(f.infoBoxManager, never()).removeInfoBox(any());
		assertSame("the plugin keeps it", added.getValue(), field(f.plugin, "infoBox"));

		onEdt(f.plugin::shutDown);
		verify(f.infoBoxManager, times(1)).removeInfoBox(same(added.getValue()));
		verify(f.infoBoxManager, times(1)).removeInfoBox(any());
		verify(f.infoBoxManager, times(1)).addInfoBox(any());
		assertNull(field(f.plugin, "infoBox"));

		// A plugin that never started removes nothing.
		final Fixture idle = new Fixture(false);
		onEdt(idle.plugin::shutDown);
		verifyNoInteractions(idle.infoBoxManager);
	}

	@Test
	public void badgeIsFedEveryStep() throws Exception
	{
		final Fixture f = new Fixture(true);
		f.plugin.engines = s -> new LagEngine(s, new FakeDetector(), new FakeJudge(), f.snapshots);
		onEdt(f.plugin::startUp);
		assertSame("nothing shown before the first step", BadgeView.HIDDEN, WhyLagDevBridge.handle.badge());

		f.at(1);
		f.plugin.sampleOnce();
		final BadgeView v = WhyLagDevBridge.handle.badge();
		assertTrue(v.visible);
		assertEquals("the green circle", Level.OK, v.level);
		assertEquals(Icon.NONE, v.icon);
		assertEquals(Answer.SMOOTH.line1, v.line1);
		assertEquals("Smooth", v.line1);
		assertEquals(BadgeStyle.ICON, v.style);
		// The style is ICON, a style without words: the infobox shows what the model answers, and the overlay, which
		// draws the word styles only, shows nothing (addendum D).
		final BadgeOverlay overlay = (BadgeOverlay) f.overlay();
		assertNull(overlay.render(new java.awt.image.BufferedImage(200, 200,
			java.awt.image.BufferedImage.TYPE_INT_ARGB).createGraphics()));
		final BadgeInfoBox infoBox = f.infoBox();
		assertTrue(infoBox.render());
		assertNotNull(infoBox.getImage());
		verify(f.infoBoxManager).updateInfoBoxImage(infoBox);
		onEdt(f.plugin::shutDown);
	}

	@Test
	public void badgeWorksWithThePanelNeverOpened() throws Exception
	{
		final Fixture f = new Fixture(true);
		final FakeDetector detector = new FakeDetector();
		final FakeJudge judge = new FakeJudge();
		f.plugin.engines = s -> new LagEngine(s, detector, judge, f.snapshots);
		onEdt(f.plugin::startUp);
		final WhyLagPanel panel = (WhyLagPanel) field(f.plugin, "panel");

		f.at(1);
		f.plugin.sampleOnce();
		assertEquals("Smooth", WhyLagDevBridge.handle.badge().line1);

		detector.toClose = event(0, false);
		f.at(2);
		f.plugin.sampleOnce();
		final BadgeView v = WhyLagDevBridge.handle.badge();
		assertTrue(v.visible);
		assertEquals("World lag", v.line1);
		assertEquals("Not you", v.line2);
		assertEquals(Icon.WORLD, v.icon);
		verify(f.chat, times(1)).queue(any(QueuedMessage.class));

		assertEquals("the panel was never opened", 0, panel.activations());
		assertEquals("and no snapshot was built for it", 0, f.builds.get());
		assertTrue(f.posted.isEmpty());
		onEdt(f.plugin::shutDown);
	}

	@Test
	public void badgeReadsItsFourSettings() throws Exception
	{
		final Fixture f = new Fixture(true);
		f.plugin.engines = s -> new LagEngine(s, new FakeDetector(), new FakeJudge(), f.snapshots);
		onEdt(f.plugin::startUp);
		int k = 1;

		f.at(k++);
		f.plugin.sampleOnce();
		assertTrue(WhyLagDevBridge.handle.badge().visible);

		when(f.config.badgeShow()).thenReturn(false);
		f.at(k++);
		f.plugin.sampleOnce();
		assertSame("show off: the hidden constant itself", BadgeView.HIDDEN, WhyLagDevBridge.handle.badge());

		when(f.config.badgeShow()).thenReturn(true);
		for (BadgeStyle style : BadgeStyle.values())
		{
			when(f.config.badgeStyle()).thenReturn(style);
			f.at(k++);
			f.plugin.sampleOnce();
			final BadgeView v = WhyLagDevBridge.handle.badge();
			assertTrue(style + " is visible", v.visible);
			assertEquals(style, v.style);
		}

		when(f.config.badgeWhenSmooth()).thenReturn(WhenSmooth.HIDE);
		f.at(k++);
		f.plugin.sampleOnce();
		assertSame("smooth under Hide: hidden", BadgeView.HIDDEN, WhyLagDevBridge.handle.badge());
		when(f.config.badgeWhenSmooth()).thenReturn(WhenSmooth.SHOW);
		f.at(k++);
		f.plugin.sampleOnce();
		assertNotSame(BadgeView.HIDDEN, WhyLagDevBridge.handle.badge());
		verify(f.config, times(k - 1)).badgeShow();
		verify(f.config, times(k - 1)).badgeChatLine();
		onEdt(f.plugin::shutDown);
	}

	@Test
	public void chatLineIsSentOnce() throws Exception
	{
		final Fixture f = new Fixture(false);
		final FakeDetector detector = new FakeDetector();
		f.plugin.engines = s -> new LagEngine(s, detector, new FakeJudge(), f.snapshots);
		onEdt(f.plugin::startUp);
		detector.toClose = event(0, false);

		f.at(1);
		f.plugin.sampleOnce();
		final ArgumentCaptor<QueuedMessage> sent = ArgumentCaptor.forClass(QueuedMessage.class);
		verify(f.chat, times(1)).queue(sent.capture());
		assertEquals("[Why Lag] World lag - not you (14 s). Ticks 1,240 ms, ping 41 ms.",
			sent.getValue().getRuneLiteFormattedMessage());

		f.at(2);
		f.plugin.sampleOnce();
		f.at(3);
		f.plugin.sampleOnce();
		verify(f.chat, times(1)).queue(any(QueuedMessage.class));
		onEdt(f.plugin::shutDown);
	}

	@Test
	public void noChatLineWhenTheSettingIsOff() throws Exception
	{
		final Fixture f = new Fixture(false);
		final FakeDetector detector = new FakeDetector();
		f.plugin.engines = s -> new LagEngine(s, detector, new FakeJudge(), f.snapshots);
		when(f.config.badgeChatLine()).thenReturn(false);
		onEdt(f.plugin::startUp);
		detector.toClose = event(0, false);

		f.at(1);
		f.plugin.sampleOnce();
		f.at(2);
		f.plugin.sampleOnce();
		assertEquals("the lag was in the log", 1, f.engine().session().events.sessionTotal());
		verify(f.chat, never()).queue(any(QueuedMessage.class));
		onEdt(f.plugin::shutDown);
	}

	// ---------------------------------------------------------------- fixtures

	/** Every injected collaborator mocked, and the seams set so nothing runs on its own. */
	private static final class Fixture
	{
		final WhyLagPlugin plugin = new WhyLagPlugin();
		final Client client = mock(Client.class);
		final ClientThread clientThread = mock(ClientThread.class);
		final ClientToolbar clientToolbar = mock(ClientToolbar.class);
		final ConfigManager configManager = mock(ConfigManager.class);
		final PluginManager pluginManager = mock(PluginManager.class);
		final WhyLagConfig config = mock(WhyLagConfig.class);
		final OverlayManager overlayManager = mock(OverlayManager.class);
		final InfoBoxManager infoBoxManager = mock(InfoBoxManager.class);
		final TooltipManager tooltipManager = mock(TooltipManager.class);
		final ChatMessageManager chat = mock(ChatMessageManager.class);
		final ScheduledExecutorService executor = mock(ScheduledExecutorService.class);
		final ScheduledFuture<?> future = mock(ScheduledFuture.class);
		final FakeProbe probe = new FakeProbe(MemorySource.MANAGEMENT);
		/** What the plugin handed to the Swing thread, not run. */
		final List<Runnable> posted = new ArrayList<>();
		/** Snapshots built through {@link #snapshots}. */
		final AtomicInteger builds = new AtomicInteger();
		/** A snapshot source that counts and builds nothing (the panel ignores a null snapshot). */
		final SnapshotSource snapshots = (s, shown, range, nowSec, wallMs, settings, footer) ->
		{
			builds.incrementAndGet();
			return null;
		};
		/** The clock of the sampler and the session's start. */
		final long base = System.nanoTime();
		volatile long now = base;
		/** The plugin's own executor seam, kept before the mock replaces it. */
		final Supplier<ScheduledExecutorService> ownExecutors;

		Fixture(boolean developerMode) throws Exception
		{
			when(config.systemStats()).thenReturn(true);
			when(config.badgeShow()).thenReturn(true);
			when(config.badgeStyle()).thenReturn(BadgeStyle.ICON);
			when(config.badgeWhenSmooth()).thenReturn(WhenSmooth.SHOW);
			when(config.badgeChatLine()).thenReturn(true);
			doReturn(future).when(executor).scheduleAtFixedRate(any(Runnable.class), anyLong(), anyLong(),
				any(TimeUnit.class));

			set(plugin, "client", client);
			set(plugin, "clientThread", clientThread);
			set(plugin, "clientToolbar", clientToolbar);
			set(plugin, "configManager", configManager);
			set(plugin, "pluginManager", pluginManager);
			set(plugin, "config", config);
			set(plugin, "overlayManager", overlayManager);
			set(plugin, "infoBoxManager", infoBoxManager);
			set(plugin, "tooltipManager", tooltipManager);
			set(plugin, "chatMessageManager", chat);
			set(plugin, "developerMode", developerMode);
			plugin.nanoClock = () -> now;
			plugin.wallClock = this::wall;
			plugin.hostProbes = on -> probe;
			ownExecutors = plugin.executors;
			plugin.executors = () -> executor;
			plugin.edt = posted::add;
		}

		/** The sampler's clock at {@code sec} seconds and 10 ms after the session's start. */
		void at(long sec)
		{
			now = base + sec * SECOND + 10 * MS;
		}

		long wall()
		{
			return 1_790_000_000_000L + (now - base) / MS;
		}

		LagEngine engine() throws Exception
		{
			return (LagEngine) field(plugin, "engine");
		}

		/**
		 * Closes session second {@code sec} with two synthetic frames, 100 ms into it and 100 ms into the next: the
		 * second takes the state, world, focus and scene counts in force when it is closed. The seconds used are
		 * far ahead of the real clock that the client-thread handlers read, so no test depends on how long a
		 * {@code startUp} took.
		 */
		void closeSecond(long sec) throws Exception
		{
			engine().frame(base + sec * SECOND + 100 * MS, 0, -1);
			engine().frame(base + (sec + 1) * SECOND + 100 * MS, 0, -1);
		}

		Runnable scheduledTask()
		{
			final ArgumentCaptor<Runnable> task = ArgumentCaptor.forClass(Runnable.class);
			verify(executor).scheduleAtFixedRate(task.capture(), anyLong(), anyLong(), any(TimeUnit.class));
			return task.getValue();
		}

		NavigationButton nav()
		{
			final ArgumentCaptor<NavigationButton> nav = ArgumentCaptor.forClass(NavigationButton.class);
			verify(clientToolbar).addNavigation(nav.capture());
			return nav.getValue();
		}

		Overlay overlay()
		{
			final ArgumentCaptor<Overlay> overlay = ArgumentCaptor.forClass(Overlay.class);
			verify(overlayManager).add(overlay.capture());
			return overlay.getValue();
		}

		BadgeInfoBox infoBox()
		{
			final ArgumentCaptor<InfoBox> infoBox = ArgumentCaptor.forClass(InfoBox.class);
			verify(infoBoxManager).addInfoBox(infoBox.capture());
			return (BadgeInfoBox) infoBox.getValue();
		}

		void clearMocks()
		{
			clearInvocations(client, clientThread, clientToolbar, configManager, pluginManager, config,
				overlayManager, infoBoxManager, tooltipManager, chat, executor);
		}
	}

	/** A host probe that counts, and throws every call while {@link #throwing} (two kinds, in turn). */
	private static final class FakeProbe implements HostProbe
	{
		private static final AtomicInteger CLOCK = new AtomicInteger();

		final MemorySource source;
		final AtomicInteger heapReads = new AtomicInteger();
		final AtomicInteger starts = new AtomicInteger();
		final AtomicInteger stops = new AtomicInteger();
		final List<Class<?>> thrownKinds = new ArrayList<>();
		volatile boolean throwing;
		volatile GcSink sink;
		volatile long startNanos;
		volatile int startedAt;
		volatile int stoppedAt;
		private boolean linkage;

		FakeProbe(MemorySource source)
		{
			this.source = source;
		}

		private void maybeThrow()
		{
			if (!throwing)
			{
				return;
			}
			linkage = !linkage;
			final RuntimeException runtime = new IllegalStateException("the probe fails");
			final Error error = new NoClassDefFoundError("com/sun/management/OperatingSystemMXBean");
			final Throwable t = linkage ? error : runtime;
			if (!thrownKinds.contains(t.getClass()))
			{
				thrownKinds.add(t.getClass());
			}
			if (linkage)
			{
				throw error;
			}
			throw runtime;
		}

		@Override
		public MemorySource source()
		{
			maybeThrow();
			return source;
		}

		@Override
		public boolean start(GcSink sink, long sessionStartNanos)
		{
			maybeThrow();
			this.sink = sink;
			this.startNanos = sessionStartNanos;
			startedAt = CLOCK.incrementAndGet();
			starts.incrementAndGet();
			return true;
		}

		@Override
		public void stop()
		{
			maybeThrow();
			stoppedAt = CLOCK.incrementAndGet();
			stops.incrementAndGet();
		}

		@Override
		public int heapUsedMb()
		{
			heapReads.incrementAndGet();
			maybeThrow();
			return 400;
		}

		@Override
		public int heapMaxMb()
		{
			maybeThrow();
			return 768;
		}

		@Override
		public int processCpuPct()
		{
			maybeThrow();
			return 40;
		}

		@Override
		public int systemCpuPct()
		{
			maybeThrow();
			return 20;
		}

		@Override
		public int cores()
		{
			maybeThrow();
			return 8;
		}

		@Override
		public long currentThreadCpuNanos()
		{
			maybeThrow();
			return -1;
		}
	}

	/** A detector that closes {@link #toClose} at its next advance, and has nothing open. */
	private static final class FakeDetector implements Detector
	{
		volatile LagEvent toClose;

		@Override
		public void advance(Session s, long throughSec, SettingsView settings, DetectorListener out)
		{
			final LagEvent e = toClose;
			if (e != null)
			{
				toClose = null;
				out.opened(event(e.id, true));
				out.closed(e);
			}
		}

		@Override
		public LagEvent open()
		{
			return null;
		}

		@Override
		public boolean quiet(long sec)
		{
			return true;
		}
	}

	/** The card says all clear (V2); every event is the world's (W1). Counts the card's steps. */
	private static final class FakeJudge implements Judge
	{
		final AtomicInteger currents = new AtomicInteger();

		@Override
		public Verdict judgeEvent(Session s, LagEvent closed, SettingsView settings)
		{
			return new Verdict(Cause.SLOW_WORLD, Confidence.LIKELY, Level.BAD, "The world is slow",
				"Ticks 1,240 ms, ping steady.", "", "", closed.startWallMs, closed.lengthS(), closed.world, closed.id,
				null, null);
		}

		@Override
		public Verdict current(Session s, long nowSec, long wallMs, SettingsView settings)
		{
			currents.incrementAndGet();
			return new Verdict(Cause.ALL_CLEAR, Confidence.SURE, Level.OK, "No lag", "No lag for 4 min.", "", "",
				0, 0, 0, -1, null, null);
		}
	}

	/** A lag of 14 s, seconds 0 to 13: the worst tick 1,240 ms, ping 41 ms. */
	private static LagEvent event(long id, boolean open)
	{
		return new LagEvent(id, 0, 13, 1_790_000_000_000L, Trigger.TICK_OFF.bit(), Trigger.TICK_OFF, 416, 12850, 3,
			2, 50, 22, 700, 1240, 640, 41, 45, 40, 900, 0, 0, 400, 768, 20, 40, open, false, null);
	}

	private static HostProbe probeMock()
	{
		final HostProbe probe = mock(HostProbe.class);
		when(probe.source()).thenReturn(MemorySource.MANAGEMENT);
		when(probe.heapMaxMb()).thenReturn(768);
		when(probe.currentThreadCpuNanos()).thenReturn(-1L);
		return probe;
	}

	private static GameStateChanged gameState(GameState state)
	{
		final GameStateChanged e = new GameStateChanged();
		e.setGameState(state);
		return e;
	}

	private static ConfigChanged configChanged(String group, String key)
	{
		final ConfigChanged e = new ConfigChanged();
		e.setGroup(group);
		e.setKey(key);
		return e;
	}

	private static void set(Object target, String name, Object value) throws Exception
	{
		final Field f = WhyLagPlugin.class.getDeclaredField(name);
		f.setAccessible(true);
		f.set(target, value);
	}

	private static Object field(Object target, String name) throws Exception
	{
		final Field f = WhyLagPlugin.class.getDeclaredField(name);
		f.setAccessible(true);
		return f.get(target);
	}

	/** Runs on the Swing thread and rethrows whatever happened there, the way the client would see it. */
	private static void onEdt(Runnable r) throws Exception
	{
		final AtomicReference<Throwable> thrown = new AtomicReference<>();
		SwingUtilities.invokeAndWait(() ->
		{
			try
			{
				r.run();
			}
			catch (Throwable t)
			{
				thrown.set(t);
			}
		});
		if (thrown.get() != null)
		{
			throw new AssertionError(thrown.get());
		}
	}

	/** Runs on a thread of its own, standing in for the sampler thread, and rethrows what happened there. */
	private static void onSamplerThread(Runnable r) throws Exception
	{
		final AtomicReference<Throwable> thrown = new AtomicReference<>();
		final Thread t = new Thread(() ->
		{
			try
			{
				r.run();
			}
			catch (Throwable e)
			{
				thrown.set(e);
			}
		}, "test-sampler");
		t.start();
		t.join(10_000);
		assertFalse("the sampler run did not finish", t.isAlive());
		if (thrown.get() != null)
		{
			throw new AssertionError(thrown.get());
		}
	}
}
