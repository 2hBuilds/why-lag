package com.whylag;

import com.google.inject.Provides;
import com.whylag.core.Answer;
import com.whylag.core.BadgeStyle;
import com.whylag.core.BadgeView;
import com.whylag.core.Cause;
import com.whylag.core.Confidence;
import com.whylag.core.Detector;
import com.whylag.core.Diagnostics;
import com.whylag.core.DetectorListener;
import com.whylag.core.Flags;
import com.whylag.core.Icon;
import com.whylag.core.Judge;
import com.whylag.core.LagEngine;
import com.whylag.core.LagEvent;
import com.whylag.core.Level;
import com.whylag.core.MinuteLog;
import com.whylag.core.PanelSnapshot;
import com.whylag.core.Renderer;
import com.whylag.core.ReportText;
import com.whylag.core.Session;
import com.whylag.core.SettingsView;
import com.whylag.core.SnapshotBuilder;
import com.whylag.core.SnapshotSource;
import com.whylag.core.State;
import com.whylag.core.Thresholds;
import com.whylag.core.Trigger;
import com.whylag.core.Verdict;
import com.whylag.core.WhenSmooth;
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
import java.util.regex.Pattern;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;
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
import net.runelite.api.events.WorldChanged;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.chat.ChatMessageManager;
import net.runelite.client.chat.QueuedMessage;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
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
 * settings reads, the scene counts, the start-up read, and the game badge fed every step.
 *
 * <p>No client boots and no Guice injector builds the plugin: the injected fields are filled by reflection and the
 * handlers are called directly, which is how RuneLite calls them. The clock, the engine, the
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
		assertEquals("Tells you what caused the lag: frames, ticks or ping (v" + Version.CURRENT + ")",
			d.description());
		assertEquals(Arrays.asList("lag", "ping", "fps", "tick", "freeze", "stutter"), Arrays.asList(d.tags()));
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
		final FakeJudge judge = new FakeJudge();
		f.plugin.engines = e -> new LagEngine(e, new FakeDetector(), judge, f.snapshots);
		onEdt(f.plugin::startUp);
		final Runnable task = f.scheduledTask();
		verify(f.executor).scheduleAtFixedRate(any(Runnable.class), eq(1L), eq(1L), eq(TimeUnit.SECONDS));
		verify(f.executor, never()).scheduleWithFixedDelay(any(Runnable.class), anyLong(), anyLong(),
			any(TimeUnit.class));
		verify(f.executor, never()).schedule(any(Runnable.class), anyLong(), any(TimeUnit.class));

		// The task scheduled is the sampler's run: it steps the judge.
		final int before = judge.currents.get();
		f.at(1);
		task.run();
		assertEquals(before + 1, judge.currents.get());
		onEdt(f.plugin::shutDown);
	}

	@Test
	public void taskSurvivesAThrowingStep() throws Exception
	{
		final Fixture f = new Fixture(false);
		final FakeJudge judge = new FakeJudge();
		f.plugin.engines = s -> new LagEngine(s, new FakeDetector(), judge, f.snapshots);
		onEdt(f.plugin::startUp);
		final Runnable task = f.scheduledTask();
		final int before = judge.currents.get();

		judge.throwing = true;
		for (int i = 1; i <= 3; i++)
		{
			f.at(i);
			task.run();
			assertEquals("run " + i + " reached the judge", before + i, judge.currents.get());
		}
		assertTrue("both kinds were thrown: a RuntimeException and a LinkageError", judge.thrownKinds.size() == 2);

		// ... and the task carries on: once the judge answers again, the next run steps to the end.
		judge.throwing = false;
		f.at(4);
		task.run();
		assertEquals(before + 4, judge.currents.get());
		onEdt(f.plugin::shutDown);
	}

	// ---------------------------------------------------------------- shutDown (T7) and the bridge

	@Test
	public void shutDownCancelsEverything() throws Exception
	{
		final Fixture f = new Fixture(false);
		onEdt(f.plugin::startUp);
		final NavigationButton nav = f.nav();
		final Overlay overlay = f.overlay();
		final WhyLagPanel panel = (WhyLagPanel) field(f.plugin, "panel");
		onEdt(panel::onActivate);
		assertTrue(panel.isActive());
		f.posted.clear();

		onEdt(f.plugin::shutDown);
		final InOrder order = inOrder(f.future, f.executor, f.clientToolbar, f.overlayManager);
		order.verify(f.future).cancel(anyBoolean());
		order.verify(f.executor).shutdownNow();
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
			seen.add("removeNavigation " + WhyLagDevBridge.handle);
			return null;
		}).when(f.clientToolbar).removeNavigation(nav);
		doAnswer(inv ->
		{
			seen.add("remove " + WhyLagDevBridge.handle);
			return true;
		}).when(f.overlayManager).remove(overlay);

		onEdt(f.plugin::shutDown);
		assertEquals(Arrays.asList("cancel null", "shutdownNow null", "removeNavigation null", "remove null"), seen);

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

	/**
	 * The report is {@code ReportText.of(s, checks)} with the diagnostics texts attached again: with no checks it is
	 * the report of the snapshot as it was, and the checks section goes between its first line and {@code Now}. The
	 * panel's door to it is {@code testAndReport}, which no longer answers the text itself (1.0.1, lot B).
	 */
	@Test
	public void theReportIsReportTextWithTheChecksBetweenLineOneAndNow() throws Exception
	{
		final Fixture f = steppedFixture(3);
		final PanelSnapshot s = f.plugin.attach(f.engine().snapshot(10, f.wall()));
		final List<Report> got = press(f, s);

		assertEquals(1, got.size());
		final String text = got.get(0).text;
		final String[] lines = text.split("\n", -1);
		assertTrue(lines[0], lines[0].startsWith("2h Why Lag " + Version.CURRENT + " report - "));
		assertTrue(lines[1], lines[1].startsWith("Verdict: "));
		assertEquals("", lines[2]);
		assertEquals("Checks:", lines[3]);
		for (int i = 0; i < 10; i++)
		{
			assertTrue(lines[4 + i], lines[4 + i].matches("(PASS|FAIL)  C" + (i + 1) + " .+"));
		}
		assertEquals("", lines[14]);
		assertTrue(lines[15], lines[15].startsWith("Now: "));
		assertEquals("the verdict line is the report's own", lines[1], "Verdict: " + got.get(0).verdict);
		assertEquals("the rest of the report is the plain report: line one, then what follows the checks",
			ReportText.of(f.plugin.attach(s)),
			lines[0] + "\n" + String.join("\n", Arrays.copyOfRange(lines, 15, lines.length)));
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
		for (String name : new String[]{"settingsDirty", "inGame"})
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

		f.plugin.onConfigChanged(configChanged("gpu", "fpsTarget"));
		assertTrue("any group feeds the settings", (boolean) field(f.plugin, "settingsDirty"));
		set(f.plugin, "settingsDirty", false);
		f.plugin.onConfigChanged(configChanged("runelite", "gpuplugin"));
		assertTrue("the plugin flags too", (boolean) field(f.plugin, "settingsDirty"));
		final FocusChanged focus = new FocusChanged();
		focus.setFocused(false);
		f.plugin.onFocusChanged(focus);

		verifyNoInteractions(f.client, f.clientThread, f.clientToolbar, f.configManager, f.config,
			f.overlayManager, f.tooltipManager, f.chat, f.executor);

		// The focus reached the engine: the second the next frames close is not FOCUSED (a new one would be).
		final Session s = f.engine().session();
		f.closeSecond(100);
		assertFalse(Flags.has(s.seconds.flags(100), Flags.FOCUSED));
		// ... and a second closed after the focus came back is.
		focus.setFocused(true);
		f.plugin.onFocusChanged(focus);
		f.engine().frame(f.base + 102 * SECOND + 100 * MS, 0);
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

	/**
	 * Whether a GPU renderer is on is {@code Client#isGpu()}, asked on the client thread only: by the start-up read and
	 * by a task each settings re-read posts. The sampler reads the volatile the task fills, and a change makes the
	 * next run read the settings again.
	 */
	@Test
	public void theRendererIsTheClientsAnswerReadOnTheClientThread() throws Exception
	{
		assertTrue("gpu is volatile", Modifier.isVolatile(WhyLagPlugin.class.getDeclaredField("gpu").getModifiers()));
		final Fixture f = new Fixture(true);
		final AtomicBoolean isGpu = new AtomicBoolean(true);
		final Thread clientThread = Thread.currentThread();
		final List<String> offThread = new ArrayList<>();
		when(f.client.isGpu()).thenAnswer(inv ->
		{
			if (Thread.currentThread() != clientThread)
			{
				synchronized (offThread)
				{
					offThread.add(Thread.currentThread().getName());
				}
			}
			return isGpu.get();
		});
		onEdt(f.plugin::startUp);
		verify(f.client, never()).isGpu();

		// The first re-read runs before any client thread task has: the answer is not known yet.
		f.at(1);
		onSamplerThread(f.plugin::sampleOnce);
		verify(f.client, never()).isGpu();
		assertEquals(Renderer.CPU, WhyLagDevBridge.handle.settings().renderer);
		final ArgumentCaptor<BooleanSupplier> asks = ArgumentCaptor.forClass(BooleanSupplier.class);
		verify(f.clientThread, times(1)).invokeLater(asks.capture());
		assertTrue("the task is done at once", asks.getValue().getAsBoolean());
		verify(f.client, times(1)).isGpu();
		assertTrue((boolean) field(f.plugin, "gpu"));
		assertTrue("a change makes the next run read again", (boolean) field(f.plugin, "settingsDirty"));

		f.at(2);
		onSamplerThread(f.plugin::sampleOnce);
		assertEquals("the client's answer reached the reader", Renderer.GPU, WhyLagDevBridge.handle.settings().renderer);
		assertFalse("an answer that did not change asks for nothing more", (boolean) field(f.plugin, "settingsDirty"));

		isGpu.set(false);
		verify(f.clientThread, times(2)).invokeLater(asks.capture());
		asks.getValue().getAsBoolean();
		f.at(3);
		onSamplerThread(f.plugin::sampleOnce);
		assertEquals("the renderer went to the CPU one", Renderer.CPU, WhyLagDevBridge.handle.settings().renderer);

		// The start-up read asks the same question.
		isGpu.set(true);
		final ArgumentCaptor<Runnable> start = ArgumentCaptor.forClass(Runnable.class);
		verify(f.clientThread).invokeLater(start.capture());
		start.getValue().run();
		assertTrue((boolean) field(f.plugin, "gpu"));
		assertTrue("asked on the client thread only: " + offThread, offThread.isEmpty());
		onEdt(f.plugin::shutDown);
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
		f.engine().frame(f.base + 102 * SECOND + 100 * MS, 0);
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
		f.engine().frame(f.base + 103 * SECOND + 100 * MS, 0);
		assertEquals(0, s.seconds.region(102));

		// LOADING keeps the scene's counts (the bundled NPC Indicators idiom); a hop resets them.
		f.plugin.onGameStateChanged(gameState(GameState.LOADING));
		for (int i = 17; i <= 21; i++)
		{
			f.plugin.onGameTick(new GameTick());
		}
		f.engine().frame(f.base + 104 * SECOND + 100 * MS, 0);
		assertEquals(4, s.seconds.players(103));
		f.plugin.onGameStateChanged(gameState(GameState.HOPPING));
		for (int i = 22; i <= 26; i++)
		{
			f.plugin.onGameTick(new GameTick());
		}
		f.engine().frame(f.base + 105 * SECOND + 100 * MS, 0);
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

	// ---------------------------------------------------------------- the diagnostics and the minute log (1.0.1)

	/** The notes the diagnostics hold, oldest first, each as the report prints it: {@code hh:mm:ss  text}. */
	static List<String> notesOf(Fixture f) throws Exception
	{
		final String text = ((Diagnostics) field(f.plugin, "diagnostics")).text();
		final List<String> notes = new ArrayList<>();
		boolean in = false;
		for (String line : text.split("\n", -1))
		{
			if (!in)
			{
				in = line.equals("Notes");
			}
			else if (line.isEmpty())
			{
				break;
			}
			else
			{
				notes.add(line);
			}
		}
		return notes;
	}

	/** True when a note of the log ends with {@code what} (the clock in front of it is whatever it is). */
	private static boolean noted(List<String> notes, String what)
	{
		for (String note : notes)
		{
			if (note.matches("\\d\\d:\\d\\d:\\d\\d  " + Pattern.quote(what)))
			{
				return true;
			}
		}
		return false;
	}

	/** The place of the first note that ends with {@code what}; -1 when none does. */
	private static int indexOf(List<String> notes, String what)
	{
		for (int i = 0; i < notes.size(); i++)
		{
			if (notes.get(i).endsWith("  " + what))
			{
				return i;
			}
		}
		return -1;
	}

	private static int notedTimes(List<String> notes, String what)
	{
		int n = 0;
		for (String note : notes)
		{
			if (note.matches("\\d\\d:\\d\\d:\\d\\d  " + Pattern.quote(what)))
			{
				n++;
			}
		}
		return n;
	}

	/** The first note is the start-up line, and it is the only one until a step runs. */
	@Test
	public void theFirstNoteAfterStartUpNamesTheVersionTheClientAndTheSystem() throws Exception
	{
		final Fixture f = new Fixture(false);
		onEdt(f.plugin::startUp);

		final List<String> notes = notesOf(f);

		assertTrue(notes.get(0), notes.get(0).matches("\\d\\d:\\d\\d:\\d\\d  plugin started, version "
			+ Pattern.quote(Version.CURRENT) + ", client \\S+, .+"));
		assertEquals(1, notes.size());
		onEdt(f.plugin::shutDown);
	}

	/**
	 * A login, a hop and a logout, each noted by the step that finds it in the newest complete second of the ring:
	 * the world comes from the ring, and the same state noted again says nothing.
	 */
	@Test
	public void aLoginAHopAndALogoutAreNotedByTheStepThatFindsThem() throws Exception
	{
		final Fixture f = new Fixture(false);
		f.plugin.engines = s -> new LagEngine(s, new FakeDetector(), new FakeJudge(), f.snapshots);
		when(f.client.getWorld()).thenReturn(416);
		onEdt(f.plugin::startUp);

		f.plugin.onGameStateChanged(gameState(GameState.LOGGED_IN));
		f.plugin.onWorldChanged(new WorldChanged());
		f.closeSecond(100);
		f.at(103);
		f.plugin.sampleOnce();
		f.at(104);
		f.plugin.sampleOnce();
		assertEquals("noted once, not at every step", 1, notedTimes(notesOf(f), "logged in, world 416"));

		when(f.client.getWorld()).thenReturn(302);
		f.plugin.onWorldChanged(new WorldChanged());
		f.closeSecond(110);
		f.at(113);
		f.plugin.sampleOnce();
		assertEquals(1, notedTimes(notesOf(f), "hop to world 302"));

		f.plugin.onGameStateChanged(gameState(GameState.LOGIN_SCREEN));
		f.closeSecond(120);
		f.at(123);
		f.plugin.sampleOnce();
		final List<String> notes = notesOf(f);
		assertEquals(1, notedTimes(notes, "logged out"));
		assertTrue("in the order they happened", indexOf(notes, "logged in, world 416") < indexOf(notes,
			"hop to world 302") && indexOf(notes, "hop to world 302") < indexOf(notes, "logged out"));
		onEdt(f.plugin::shutDown);
	}

	/** A lag that opened and closed is noted with its cause and its words, the way the chat line says it. */
	@Test
	public void aClosedLagIsNotedOpenedAndClosed() throws Exception
	{
		final Fixture f = new Fixture(false);
		final FakeDetector detector = new FakeDetector();
		f.plugin.engines = s -> new LagEngine(s, detector, new FakeJudge(), f.snapshots);
		onEdt(f.plugin::startUp);
		detector.toClose = event(0, false);

		f.at(1);
		f.plugin.sampleOnce();
		f.at(2);
		f.plugin.sampleOnce();

		final List<String> notes = notesOf(f);
		assertEquals(1, notedTimes(notes, "lag opened: WORLD, ticks 1,240 ms, ping 41 ms"));
		assertEquals(1, notedTimes(notes, "lag closed after 14 s: World lag - not you, Likely"));
		onEdt(f.plugin::shutDown);
	}

	/** The first step notes the card, the ping probe's state and the settings; a steady state is not noted again. */
	@Test
	public void theFirstStepNotesTheCardThePingAndTheSettings() throws Exception
	{
		final Fixture f = new Fixture(false);
		f.plugin.engines = s -> new LagEngine(s, new FakeDetector(), new FakeJudge(), f.snapshots);
		onEdt(f.plugin::startUp);
		// The client thread has answered Client#isGpu() (true), as it does within a tick of the plugin starting.
		set(f.plugin, "gpu", true);

		f.at(1);
		f.plugin.sampleOnce();
		List<String> notes = notesOf(f);
		assertTrue(notes.toString(), noted(notes, "card: Smooth"));
		assertTrue(notes.toString(), noted(notes, "ping: not logged in"));
		assertTrue(notes.toString(), noted(notes, "settings: renderer GPU, cap 60 (GPU: FPS target)"));

		for (int i = 2; i <= 4; i++)
		{
			f.at(i);
			f.plugin.sampleOnce();
		}
		assertEquals("a steady state says nothing twice", notes.size(), notesOf(f).size());

		// The client logs in: the probe has no socket yet, and that is a change of the ping probe's state.
		f.plugin.onGameStateChanged(gameState(GameState.LOGGED_IN));
		f.at(5);
		f.plugin.sampleOnce();
		notes = notesOf(f);
		assertEquals(1, notedTimes(notes, "ping: not connected"));

		// A re-read that changed the cap is noted; one that changed nothing is not.
		final int before = notesOf(f).size();
		f.plugin.onConfigChanged(configChanged("gpu", "fpsTarget"));
		f.at(6);
		f.plugin.sampleOnce();
		assertEquals("the settings were read again and are the same", before, notesOf(f).size());
		onEdt(f.plugin::shutDown);
	}

	/**
	 * A step that throws is recorded - the error with its class and message, and a warning key per class - and the
	 * NEXT step still runs: the sampler survives. Four failures of two kinds count twice each; the client log gets
	 * one WARN per key, which {@code warnOnce} answering true once is what decides (asserted in DiagnosticsTest).
	 */
	@Test
	public void aStepThatThrowsIsRecordedAndTheNextStepStillRuns() throws Exception
	{
		final Fixture f = new Fixture(false);
		final FakeJudge judge = new FakeJudge();
		f.plugin.engines = s -> new LagEngine(s, new FakeDetector(), judge, f.snapshots);
		onEdt(f.plugin::startUp);
		final Diagnostics d = (Diagnostics) field(f.plugin, "diagnostics");
		final int before = judge.currents.get();

		judge.throwing = true;
		for (int i = 1; i <= 4; i++)
		{
			f.at(i);
			f.plugin.sampleOnce();
		}

		final String text = d.text();
		assertTrue(text, text.contains("java.lang.NoClassDefFoundError: net/runelite/api/Gone  (2 times)"));
		assertTrue(text, text.contains("java.lang.IllegalStateException: the judge fails  (2 times)"));
		assertEquals(2, d.warningCount("step: java.lang.NoClassDefFoundError"));
		assertEquals(2, d.warningCount("step: java.lang.IllegalStateException"));
		assertEquals("every run reached the judge", before + 4, judge.currents.get());

		judge.throwing = false;
		f.at(5);
		f.plugin.sampleOnce();
		assertEquals("the sampler survived: the next step ran", before + 5, judge.currents.get());
		assertTrue("and noted what it found", noted(notesOf(f), "card: Smooth"));

		judge.throwing = true;
		for (int i = 6; i <= 10; i++)
		{
			f.at(i);
			f.plugin.sampleOnce();
		}
		assertEquals("the keys count on through the session", 5,
			d.warningCount("step: java.lang.NoClassDefFoundError"));
		assertEquals(4, d.warningCount("step: java.lang.IllegalStateException"));
		judge.throwing = false;
		onEdt(f.plugin::shutDown);
	}

	/** One line a minute: none for 59 steps, the first at the 60th, the next at the 120th; the report carries them. */
	@Test
	public void aMinuteLineIsAddedEverySixtyStepsAndReachesTheReport() throws Exception
	{
		assertEquals(60, WhyLagPlugin.MINUTE_STEPS);
		final Fixture f = new Fixture(false);
		f.plugin.engines = s -> new LagEngine(s, new FakeDetector(), new FakeJudge(), new SnapshotBuilder());
		onEdt(f.plugin::startUp);
		final MinuteLog log = (MinuteLog) field(f.plugin, "minutes");

		for (int i = 1; i <= 59; i++)
		{
			f.at(i);
			f.plugin.sampleOnce();
		}
		assertEquals(0, log.size());
		f.at(60);
		f.plugin.sampleOnce();
		assertEquals(1, log.size());
		for (int i = 61; i <= 119; i++)
		{
			f.at(i);
			f.plugin.sampleOnce();
		}
		assertEquals(1, log.size());
		f.at(120);
		f.plugin.sampleOnce();
		assertEquals(2, log.size());

		final String report = ReportText.of(f.plugin.attach(f.engine().snapshot(10, f.wall())));
		final String[] lines = report.split("\n", -1);
		int at = 0;
		while (!lines[at].equals("Last 60 minutes"))
		{
			at++;
		}
		for (int k = 1; k <= 2; k++)
		{
			assertTrue(lines[at + k], lines[at + k].matches("\\d\\d:\\d\\d  fps .*  lags \\d+  masked \\d+ s"));
		}
		assertEquals("", lines[at + 3]);
		assertEquals("Notes", lines[at + 4]);
		onEdt(f.plugin::shutDown);
	}


	// ---------------------------------------------------------------- the gear menu's settings (1.0.1, lot C)

	/**
	 * Each of the four setting methods of {@code PanelActions} stores its value through {@code ConfigManager} under
	 * the frozen key of {@code WhyLagConfig} - the two enums by their constants' NAMES, as RuneLite stores them - on the
	 * calling thread, and asks for one snapshot at once so the menu that opens next ticks it.
	 */
	@Test
	public void eachSettingMethodWritesTheRightKeyAndValue() throws Exception
	{
		final Fixture f = new Fixture(false);
		onEdt(f.plugin::startUp);
		final PanelActions actions = (PanelActions) field(f.plugin, "actions");
		clearInvocations(f.configManager, f.executor);

		onEdt(() -> actions.badgeShow(false));
		verify(f.configManager).setConfiguration("whylag", "badgeShow", false);
		onEdt(() -> actions.badgeStyle(BadgeStyle.SHAPE_ONLY));
		verify(f.configManager).setConfiguration("whylag", "badgeStyle", "SHAPE_ONLY");
		onEdt(() -> actions.badgeWhenSmooth(WhenSmooth.HIDE));
		verify(f.configManager).setConfiguration("whylag", "badgeWhenSmooth", "HIDE");
		onEdt(() -> actions.badgeChatLine(false));
		verify(f.configManager).setConfiguration("whylag", "badgeChatLine", false);
		verifyNoMoreInteractions(f.configManager);
		verify(f.executor, times(4)).execute(any(Runnable.class));

		onEdt(() -> actions.badgeShow(true));
		verify(f.configManager).setConfiguration("whylag", "badgeShow", true);
		onEdt(() -> actions.badgeStyle(BadgeStyle.ICON_AND_WORDS));
		verify(f.configManager).setConfiguration("whylag", "badgeStyle", "ICON_AND_WORDS");
		onEdt(() -> actions.badgeWhenSmooth(WhenSmooth.SHOW));
		verify(f.configManager).setConfiguration("whylag", "badgeWhenSmooth", "SHOW");
		onEdt(() -> actions.badgeChatLine(true));
		verify(f.configManager).setConfiguration("whylag", "badgeChatLine", true);
		onEdt(() -> actions.badgeStyle(BadgeStyle.SHAPE_AND_WORDS));
		verify(f.configManager).setConfiguration("whylag", "badgeStyle", "SHAPE_AND_WORDS");
		onEdt(() -> actions.badgeStyle(BadgeStyle.ICON));
		verify(f.configManager).setConfiguration("whylag", "badgeStyle", "ICON");
		onEdt(f.plugin::shutDown);
	}

	/** The keys the four methods write are the config's own items, and the group is its group. */
	@Test
	public void theKeysWrittenAreTheConfigsFrozenKeys() throws Exception
	{
		assertEquals("whylag", WhyLagConfig.GROUP);
		for (String key : new String[] {"badgeShow", "badgeStyle", "badgeWhenSmooth", "badgeChatLine"})
		{
			final net.runelite.client.config.ConfigItem item = WhyLagConfig.class.getMethod(key)
				.getAnnotation(net.runelite.client.config.ConfigItem.class);
			assertNotNull(key, item);
			assertEquals(key, item.keyName());
		}
		assertEquals("the stored names of the two enums are the constants' names", "SHAPE_ONLY",
			BadgeStyle.SHAPE_ONLY.name());
		assertEquals("HIDE", WhenSmooth.HIDE.name());
	}

	/** The snapshot carries the four settings the config holds, read on the sampler thread with every attach. */
	@Test
	public void theSnapshotCarriesTheSettingsTheConfigHolds() throws Exception
	{
		final Fixture f = steppedFixture(3);
		final PanelSnapshot stored = f.plugin.attach(f.engine().snapshot(10, f.wall()));
		assertTrue(stored.badgeSettings.show);
		assertEquals(BadgeStyle.ICON, stored.badgeSettings.style);
		assertEquals(WhenSmooth.SHOW, stored.badgeSettings.whenSmooth);
		assertTrue(stored.badgeSettings.chatLine);

		when(f.config.badgeShow()).thenReturn(false);
		when(f.config.badgeStyle()).thenReturn(BadgeStyle.SHAPE_ONLY);
		when(f.config.badgeWhenSmooth()).thenReturn(WhenSmooth.HIDE);
		when(f.config.badgeChatLine()).thenReturn(false);
		final PanelSnapshot changed = f.plugin.attach(f.engine().snapshot(10, f.wall()));
		assertFalse(changed.badgeSettings.show);
		assertEquals(BadgeStyle.SHAPE_ONLY, changed.badgeSettings.style);
		assertEquals(WhenSmooth.HIDE, changed.badgeSettings.whenSmooth);
		assertFalse(changed.badgeSettings.chatLine);
		assertEquals("the diagnostics are attached as before", Version.CURRENT, changed.pluginVersion);

		when(f.config.badgeStyle()).thenReturn(null);
		when(f.config.badgeWhenSmooth()).thenReturn(null);
		final PanelSnapshot none = f.plugin.attach(f.engine().snapshot(10, f.wall()));
		assertEquals("a config that answers nothing keeps the defaults", BadgeStyle.ICON, none.badgeSettings.style);
		assertEquals(WhenSmooth.SHOW, none.badgeSettings.whenSmooth);
		onEdt(f.plugin::shutDown);
	}

	/** A snapshot built for the panel by a sampler step holds the settings as stored at that step. */
	@Test
	public void theSnapshotTheSamplerPostsHoldsTheStoredSettings() throws Exception
	{
		final Fixture f = steppedFixture(2);
		final WhyLagPanel panel = (WhyLagPanel) field(f.plugin, "panel");
		onEdt(panel::onActivate);
		when(f.config.badgeShow()).thenReturn(false);
		when(f.config.badgeStyle()).thenReturn(BadgeStyle.SHAPE_AND_WORDS);
		f.posted.clear();
		f.at(3);
		f.plugin.sampleOnce();
		assertEquals(1, f.posted.size());
		onEdt(f.posted.get(0));

		final java.lang.reflect.Field last = WhyLagPanel.class.getDeclaredField("last");
		last.setAccessible(true);
		final PanelSnapshot shown = (PanelSnapshot) last.get(panel);
		assertFalse(shown.badgeSettings.show);
		assertEquals(BadgeStyle.SHAPE_AND_WORDS, shown.badgeSettings.style);
		assertEquals(WhenSmooth.SHOW, shown.badgeSettings.whenSmooth);
		onEdt(panel::onDeactivate);
		onEdt(f.plugin::shutDown);
	}

	// ---------------------------------------------------------------- Troubleshoot... (1.0.1, lots B and C)

	/** A started plugin that has taken {@code steps} sampler steps, with real snapshots. */
	private static Fixture steppedFixture(int steps) throws Exception
	{
		final Fixture f = new Fixture(false);
		f.plugin.engines = s -> new LagEngine(s, new FakeDetector(), new FakeJudge(), new SnapshotBuilder());
		onEdt(f.plugin::startUp);
		for (int i = 1; i <= steps; i++)
		{
			f.at(i);
			f.plugin.sampleOnce();
		}
		return f;
	}

	/**
	 * Presses the way the panel does, on the Swing thread, runs the one task it handed the executor on a thread of
	 * its own standing in for the sampler, and runs what the task posted to the Swing thread there. Answers what the
	 * callback received.
	 */
	private static List<Report> press(Fixture f, PanelSnapshot s) throws Exception
	{
		final List<Report> got = new ArrayList<>();
		final PanelActions actions = (PanelActions) field(f.plugin, "actions");
		f.posted.clear();
		clearInvocations(f.executor);
		onEdt(() -> actions.testAndReport(s, got::add));
		assertTrue("the press returns at once: nothing came back yet", got.isEmpty());
		assertTrue("and nothing was posted yet", f.posted.isEmpty());
		final ArgumentCaptor<Runnable> task = ArgumentCaptor.forClass(Runnable.class);
		verify(f.executor).execute(task.capture());
		onSamplerThread(task.getValue());
		assertTrue("the callback has not run on the sampler thread", got.isEmpty());
		assertEquals("one hop to the Swing thread", 1, f.posted.size());
		onEdt(f.posted.get(0));
		return got;
	}

	/**
	 * A press reaches the executor (one {@code execute}, no wait), the facts are built on the sampler thread, the
	 * callback runs on the Swing thread with a report that opens with the plugin's name, holds the verdict and twelve
	 * check lines, and the diagnostics hold the note of the run.
	 */
	@Test
	public void aPressRunsTheChecksOnTheSamplerThreadAndAnswersOnTheSwingThread() throws Exception
	{
		final Fixture f = steppedFixture(3);
		final List<String> clockThreads = new ArrayList<>();
		f.plugin.nanoClock = () ->
		{
			clockThreads.add(Thread.currentThread().getName());
			return f.now;
		};
		final PanelSnapshot s = f.plugin.attach(f.engine().snapshot(10, f.wall()));
		final List<Report> got = new ArrayList<>();
		final List<Boolean> onEdtThread = new ArrayList<>();
		final PanelActions actions = (PanelActions) field(f.plugin, "actions");
		f.posted.clear();
		clearInvocations(f.executor);

		final long pressStart = System.nanoTime();
		onEdt(() -> actions.testAndReport(s, r ->
		{
			onEdtThread.add(SwingUtilities.isEventDispatchThread());
			got.add(r);
		}));
		assertTrue("the press did not wait for the work", System.nanoTime() - pressStart < 2_000_000_000L);
		assertTrue(clockThreads.toString(), clockThreads.isEmpty());
		final ArgumentCaptor<Runnable> task = ArgumentCaptor.forClass(Runnable.class);
		verify(f.executor, times(1)).execute(task.capture());
		verifyNoMoreInteractions(f.executor);

		onSamplerThread(task.getValue());
		assertFalse("the facts were read", clockThreads.isEmpty());
		for (String name : clockThreads)
		{
			assertEquals("every read of the clock was on the sampler thread", "test-sampler", name);
		}
		assertEquals(1, f.posted.size());
		onEdt(f.posted.get(0));

		assertEquals(Arrays.asList(true), onEdtThread);
		final String text = got.get(0).text;
		assertTrue(text.startsWith("2h Why Lag"));
		assertTrue(text, text.contains("\nVerdict: " + got.get(0).verdict + "\n"));
		assertEquals(10, Pattern.compile("(?m)^(PASS|FAIL)  C\\d+ ").matcher(text).results().count());
		assertTrue(notesOf(f).toString(), notesOf(f).get(notesOf(f).size() - 1).matches(
			"\\d\\d:\\d\\d:\\d\\d  tests run: \\d+ pass, \\d+ fail.*"));
		assertTrue("and the report holds the note of its own run", text.contains("  tests run: "));
		onEdt(f.plugin::shutDown);
	}

	/** A plugin that is stopping has no thread: the press does nothing and never calls back. */
	@Test
	public void aPressOnAStoppedPluginDoesNothing() throws Exception
	{
		final Fixture f = steppedFixture(1);
		final PanelSnapshot s = f.plugin.attach(f.engine().snapshot(10, f.wall()));
		final PanelActions actions = (PanelActions) field(f.plugin, "actions");
		onEdt(f.plugin::shutDown);
		final List<Report> got = new ArrayList<>();
		onEdt(() -> actions.testAndReport(s, got::add));
		assertTrue(got.isEmpty());

		final Fixture g = steppedFixture(1);
		final PanelActions rejecting = (PanelActions) field(g.plugin, "actions");
		doAnswer(inv ->
		{
			throw new java.util.concurrent.RejectedExecutionException("shut down");
		}).when(g.executor).execute(any(Runnable.class));
		onEdt(() -> rejecting.testAndReport(s, got::add));
		assertTrue("a rejected task is swallowed too", got.isEmpty());
		onEdt(g.plugin::shutDown);
	}

	/** A setting that throws while the facts are read: recorded, and the panel still gets a verdict and a report. */
	@Test
	public void aTestThatThrowsStillAnswers() throws Exception
	{
		final Fixture f = steppedFixture(3);
		final PanelSnapshot s = f.plugin.attach(f.engine().snapshot(10, f.wall()));
		final Diagnostics d = (Diagnostics) field(f.plugin, "diagnostics");
		// The facts read it first and fail; the plain report that follows reads it again and gets through.
		when(f.config.badgeShow()).thenThrow(new NoClassDefFoundError("net/runelite/api/Gone")).thenReturn(true);

		final List<Report> got = press(f, s);
		when(f.config.badgeShow()).thenReturn(true);

		assertEquals(1, got.size());
		assertEquals("The checks could not finish: NoClassDefFoundError", got.get(0).verdict);
		assertTrue("the report as it stands, with no checks section", got.get(0).text.startsWith("2h Why Lag"));
		assertFalse(got.get(0).text.contains("Checks:"));
		assertEquals(1, d.warningCount("step: java.lang.NoClassDefFoundError"));
		assertTrue(d.text().contains("java.lang.NoClassDefFoundError"));
		onEdt(f.plugin::shutDown);
	}

	/** The wall clock set back by 5 s between two steps is what check C9 reports, with the clock of the jump. */
	@Test
	public void aClockThatJumpedBackIsReportedByCheckNine() throws Exception
	{
		final Fixture f = steppedFixture(2);
		f.plugin.wallClock = () -> f.wall() - 5000;
		f.at(3);
		f.plugin.sampleOnce();
		f.at(4);
		f.plugin.sampleOnce();
		final PanelSnapshot s = f.plugin.attach(f.engine().snapshot(10, f.wall()));

		final String text = press(f, s).get(0).text;

		assertTrue(text, text.matches("(?s).*\\nFAIL  C9 Clock sane  the clock jumped back 5 s at \\d\\d:\\d\\d\\n.*"));
		onEdt(f.plugin::shutDown);
	}

	/** A quiet clock passes C9 and names the zone. */
	@Test
	public void aSteadyClockPassesCheckNine() throws Exception
	{
		final Fixture f = steppedFixture(4);
		final PanelSnapshot s = f.plugin.attach(f.engine().snapshot(10, f.wall()));

		final String text = press(f, s).get(0).text;

		assertTrue(text, text.matches("(?s).*\\nPASS  C9 Clock sane  never went back, zone \\S+\\n.*"));
		onEdt(f.plugin::shutDown);
	}

	// ---------------------------------------------------------------- fixtures

	/** Every injected collaborator mocked, and the seams set so nothing runs on its own. */
	static final class Fixture
	{
		final WhyLagPlugin plugin = new WhyLagPlugin();
		final Client client = mock(Client.class);
		final ClientThread clientThread = mock(ClientThread.class);
		final ClientToolbar clientToolbar = mock(ClientToolbar.class);
		final ConfigManager configManager = mock(ConfigManager.class);
		final WhyLagConfig config = mock(WhyLagConfig.class);
		final OverlayManager overlayManager = mock(OverlayManager.class);
		final InfoBoxManager infoBoxManager = mock(InfoBoxManager.class);
		final TooltipManager tooltipManager = mock(TooltipManager.class);
		final ChatMessageManager chat = mock(ChatMessageManager.class);
		final ScheduledExecutorService executor = mock(ScheduledExecutorService.class);
		final ScheduledFuture<?> future = mock(ScheduledFuture.class);
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
			set(plugin, "config", config);
			set(plugin, "overlayManager", overlayManager);
			set(plugin, "infoBoxManager", infoBoxManager);
			set(plugin, "tooltipManager", tooltipManager);
			set(plugin, "chatMessageManager", chat);
			set(plugin, "developerMode", developerMode);
			plugin.nanoClock = () -> now;
			plugin.wallClock = this::wall;
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
			engine().frame(base + sec * SECOND + 100 * MS, 0);
			engine().frame(base + (sec + 1) * SECOND + 100 * MS, 0);
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
			clearInvocations(client, clientThread, clientToolbar, configManager, config, overlayManager,
				infoBoxManager, tooltipManager, chat, executor);
		}
	}

	/** A detector that closes {@link #toClose} at its next advance, and has nothing open. */
	static final class FakeDetector implements Detector
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

	/**
	 * The card says all clear (V2); every event is the world's (W1). Counts the card's steps, and throws from each
	 * of them while {@link #throwing} (two kinds, in turn: a LinkageError and a RuntimeException), which is how a
	 * test makes one run of the sampler fail.
	 */
	static final class FakeJudge implements Judge
	{
		final AtomicInteger currents = new AtomicInteger();
		final List<Class<?>> thrownKinds = new ArrayList<>();
		volatile boolean throwing;
		volatile String message = "the judge fails";
		private boolean linkage;

		private void maybeThrow()
		{
			if (!throwing)
			{
				return;
			}
			linkage = !linkage;
			final RuntimeException runtime = new IllegalStateException(message);
			final Error error = new NoClassDefFoundError("net/runelite/api/Gone");
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
			maybeThrow();
			return new Verdict(Cause.ALL_CLEAR, Confidence.SURE, Level.OK, "No lag", "No lag for 4 min.", "", "",
				0, 0, 0, -1, null, null);
		}
	}

	/** A lag of 14 s, seconds 0 to 13: the worst tick 1,240 ms, ping 41 ms. */
	static LagEvent event(long id, boolean open)
	{
		return new LagEvent(id, 0, 13, 1_790_000_000_000L, Trigger.TICK_OFF.bit(), Trigger.TICK_OFF, 416, 12850, 3,
			2, 50, 22, 700, 1240, 640, 41, 45, 40, 900, 0, open, false, null);
	}

	static GameStateChanged gameState(GameState state)
	{
		final GameStateChanged e = new GameStateChanged();
		e.setGameState(state);
		return e;
	}

	static ConfigChanged configChanged(String group, String key)
	{
		final ConfigChanged e = new ConfigChanged();
		e.setGroup(group);
		e.setKey(key);
		return e;
	}

	static void set(Object target, String name, Object value) throws Exception
	{
		final Field f = WhyLagPlugin.class.getDeclaredField(name);
		f.setAccessible(true);
		f.set(target, value);
	}

	static Object field(Object target, String name) throws Exception
	{
		final Field f = WhyLagPlugin.class.getDeclaredField(name);
		f.setAccessible(true);
		return f.get(target);
	}

	/** Runs on the Swing thread and rethrows whatever happened there, the way the client would see it. */
	static void onEdt(Runnable r) throws Exception
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
	static void onSamplerThread(Runnable r) throws Exception
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
