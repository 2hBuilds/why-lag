package com.whylag;

import com.google.inject.Provides;
import com.whylag.core.BadgeModel;
import com.whylag.core.BadgeView;
import com.whylag.core.LagDetector;
import com.whylag.core.LagEngine;
import com.whylag.core.LagSource;
import com.whylag.core.MemorySource;
import com.whylag.core.Os;
import com.whylag.core.PanelSnapshot;
import com.whylag.core.ReportText;
import com.whylag.core.Session;
import com.whylag.core.SettingsView;
import com.whylag.core.SnapshotBuilder;
import com.whylag.core.State;
import com.whylag.core.Thresholds;
import com.whylag.core.VerdictEngine;
import com.whylag.host.HostProbe;
import com.whylag.host.HostProbes;
import com.whylag.ui.NavIcon;
import com.whylag.ui.WhyLagPanel;
import java.awt.Canvas;
import java.awt.DisplayMode;
import java.awt.GraphicsConfiguration;
import java.time.ZoneId;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.LongSupplier;
import java.util.function.Supplier;
import javax.inject.Inject;
import javax.inject.Named;
import javax.swing.SwingUtilities;
import net.runelite.api.Client;
import net.runelite.api.Player;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.BeforeRender;
import net.runelite.api.events.FocusChanged;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.NpcDespawned;
import net.runelite.api.events.NpcSpawned;
import net.runelite.api.events.PlayerDespawned;
import net.runelite.api.events.PlayerSpawned;
import net.runelite.api.events.WorldChanged;
import net.runelite.client.RuneLiteProperties;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.chat.ChatMessageManager;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.events.PluginChanged;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.PluginManager;
import net.runelite.client.plugins.worldhopper.ping.Ping;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.client.ui.overlay.infobox.InfoBoxManager;
import net.runelite.client.ui.overlay.tooltip.TooltipManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 2h Why Lag: the wiring (contract 7, L9). It joins the recording ({@link LagEngine} over the real detector, judge
 * and snapshot builder), the host probe, the RuneLite-side readers, the side panel and the game badge, and it owns
 * the one thread the plugin runs, {@value #SAMPLER_THREAD}.
 *
 * <p><b>Threads.</b> The client thread hands the engine every frame ({@link #onBeforeRender}), every tick
 * ({@link #onGameTick}, with the scene counts every {@code SCENE_EVERY_TICKS} ticks), the game state, the world and
 * the spawn counts; both timestamped subscribers run at priority 100 so other plugins' handlers do not shift them.
 * The sampler thread runs {@link #sampleOnce} once a second at a fixed rate: the connection probe, the host probe,
 * the settings when they may have changed, the host second, the step, the badge and its chat line, and a snapshot
 * for the panel only while the panel is showing (T9). The handlers of {@code PluginChanged}, {@code ConfigChanged}
 * and {@code FocusChanged} only write a volatile (T12); a {@code systemStats} change swaps the host probe on the
 * sampler thread. The sampler never reads the game state: it reads the volatile {@code inGame} flag that the client
 * thread writes.
 *
 * <p><b>No pings.</b> The plugin sends no packet of its own: the connection is read from the game's own socket by
 * {@link ConnectionProbe}, and nothing here calls {@code Ping.ping}.
 *
 * <p><b>Developer mode.</b> {@link WhyLagDevBridge#handle} is filled only when RuneLite's injected
 * {@code developerMode} constant is true, and nulled first thing in {@link #shutDown}; the self timer runs only
 * then. Nothing a user sees depends on it.
 *
 * <p>
 * Choice: the scene counts are handed over on the FIRST tick after start-up and then every
 * {@code SCENE_EVERY_TICKS}-th tick (ticks 1, 6, 11 ...), so the region is known from the first tick.
 * <br>
 * Choice: the sampler takes its clock ({@code nanos} and wall ms) once, FIRST in each run, so a slow socket or
 * settings read never moves a sample into the next second; the session's start is read from the same clock.
 * <br>
 * Choice: a {@code systemStats} swap is done at the START of the next sampler run, before the probes are read, and
 * marks the settings dirty, so that run already reads the new probe and the new memory source.
 * <br>
 * Choice: the settings-dirty flag is cleared BEFORE the settings are read, so a change that arrives during the read
 * is read again at the next run.
 * <br>
 * Choice: every {@code ConfigChanged} and {@code PluginChanged} marks the settings dirty, whatever the group: the GPU,
 * 117 HD and FPS Control groups all feed the settings.
 * <br>
 * Choice: {@link PanelActions#activated} and {@link PanelActions#rangeChanged} hand ONE snapshot build to the sampler
 * thread at once (it builds nothing while the panel is hidden); the Swing thread never takes the step lock.
 * <br>
 * Choice: {@code shutDown} stops the panel's timer through the panel's own {@code onDeactivate}, posted to the Swing
 * thread, when the panel is showing: that is the one door to its "Copied" timer.
 * <br>
 * Choice: the scene counter is reset on the login screen (the authenticator page included, which
 * {@link StateCodes} maps to it) and on a hop, by the state code.
 * <br>
 * Choice: {@link DevHandle#samplerThreadId} is the id of the thread the executor made, -1 before it made one.
 * <br>
 * Choice: the clocks, the host probe chooser, the engine, the executor and the hop to the Swing thread are
 * package-private seams that {@code WhyLagWiringTest} replaces; in a client they are the real ones.
 */
@PluginDescriptor(
	name = "2h Why Lag",
	description = "Tells you what caused the lag: frames, ticks, ping or memory",
	tags = {"lag", "ping", "fps", "tick", "freeze", "stutter", "memory"}
)
public class WhyLagPlugin extends Plugin
{
	private static final Logger log = LoggerFactory.getLogger(WhyLagPlugin.class);

	/** The plugin's one thread (T5). */
	static final String SAMPLER_THREAD = "whylag-sampler";
	/** The sidebar button's place (a wiring number, contract 3.1). */
	static final int NAV_PRIORITY = 7;
	/** The priority of the two timestamped subscribers: higher runs first (a wiring number, contract 3.1). */
	static final float FIRST = 100;
	/** The sampler's period, in seconds (a wiring number, contract 3.1). */
	static final long PERIOD_S = 1;
	private static final long NANOS_PER_SECOND = 1_000_000_000L;

	@Inject
	private Client client;

	@Inject
	private ClientThread clientThread;

	@Inject
	private ClientToolbar clientToolbar;

	@Inject
	private ConfigManager configManager;

	@Inject
	private PluginManager pluginManager;

	@Inject
	private WhyLagConfig config;

	@Inject
	private OverlayManager overlayManager;

	@Inject
	private InfoBoxManager infoBoxManager;

	@Inject
	private TooltipManager tooltipManager;

	@Inject
	private ChatMessageManager chatMessageManager;

	/**
	 * RuneLite's own developer-mode constant ({@code RuneLiteModule} binds it as {@code Names.named("developerMode")},
	 * 1.12.37 line 120). All it gates here is {@link WhyLagDevBridge} and the self timer.
	 */
	@Inject
	@Named("developerMode")
	private boolean developerMode;

	// ---------------------------------------------------------------- seams (WhyLagWiringTest)

	/** The sampler's clock and the session's start. */
	LongSupplier nanoClock = System::nanoTime;
	/** Wall time, for words only. */
	LongSupplier wallClock = System::currentTimeMillis;
	/** The host probe chooser, handed {@code systemStats}. */
	Function<Boolean, HostProbe> hostProbes = HostProbes::create;
	/** The recording over a new session: the real detector, judge and snapshot builder. */
	Function<Session, LagEngine> engines = s -> new LagEngine(s, new LagDetector(), new VerdictEngine(),
		new SnapshotBuilder());
	/** The plugin's own executor (T5). */
	Supplier<ScheduledExecutorService> executors = this::newExecutor;
	/** The hop to the Swing thread. */
	Consumer<Runnable> edt = SwingUtilities::invokeLater;

	// ---------------------------------------------------------------- what startUp builds

	private final PanelActions actions = new Actions();
	/** Exception classes the sampler has already logged: each is logged once (T6). */
	private final Set<Class<?>> warned = ConcurrentHashMap.newKeySet();

	private Os os;
	private Session session;
	private LagEngine engine;
	private SceneCounter counter;
	private ConnectionProbe connection;
	private final ConnSample conn = new ConnSample();
	private SettingsReader settingsReader;
	private WhyLagPanel panel;
	private NavigationButton navButton;
	private BadgeModel badge;
	private BadgeOverlay overlay;
	private BadgeInfoBox infoBox;
	private ChatLine chatLine;
	private ScheduledExecutorService executor;
	private ScheduledFuture<?> sampler;

	/** Swapped on the sampler thread; read on the client thread once a frame. */
	private volatile HostProbe hostProbe;
	/** The settings the last step was given. */
	private volatile SettingsView settings;
	/** Written on the client thread only; the sampler hands it to the connection probe. */
	private volatile boolean inGame;
	/** The settings must be read again at the next sampler run. */
	private volatile boolean settingsDirty;
	/** A {@code systemStats} change asks the sampler to swap the host probe. */
	private volatile boolean probeSwapAsked;
	/** The thread the executor made; null before it made one. */
	private volatile Thread samplerThread;

	/** When the settings were read last, on the sampler's clock. Sampler thread only. */
	private long settingsReadNanos;
	/** Ticks since the scene was last handed over, 0 = hand it over at this tick. Client thread only. */
	private int sceneTicks;

	@Provides
	WhyLagConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(WhyLagConfig.class);
	}

	@Override
	protected void startUp()
	{
		os = Os.of(System.getProperty("os.name"));
		final HostProbe probe = hostProbes.apply(config.systemStats());
		hostProbe = probe;
		session = new Session(nanoClock.getAsLong(), wallClock.getAsLong(), os, ZoneId.systemDefault());
		engine = engines.apply(session);
		probe.start(engine::gcPause, session.startNanos);

		counter = new SceneCounter();
		sceneTicks = 0;
		inGame = false;
		connection = new ConnectionProbe(client::getSocketFD, Ping::getTCPInfo);
		final String version = RuneLiteProperties.getVersion();
		settingsReader = new SettingsReader(configManager, pluginManager, this::refreshHz, this::memorySource,
			probe.heapMaxMb(), os, version == null ? "" : version);

		// The panel opens on 1 min; the chips on the panel are the one place the range is chosen.
		panel = new WhyLagPanel(actions, GraphRange.ONE_MIN, developerMode);
		// The user, 2026-09-29: "i would like the graphs to be expanded by default". The lag list stays folded.
		panel.fold(true, false);
		navButton = NavigationButton.builder()
			.tooltip("2h Why Lag")
			.icon(NavIcon.create())
			.priority(NAV_PRIORITY)
			.panel(panel)
			.build();
		clientToolbar.addNavigation(navButton);

		final BadgeIcons icons = new BadgeIcons();
		final BadgePainter painter = new BadgePainter(icons);
		badge = new BadgeModel();
		overlay = new BadgeOverlay(badge::view, painter, tooltipManager);
		chatLine = new ChatLine(chatMessageManager);
		infoBox = new BadgeInfoBox(this, badge::view, painter, infoBoxManager);
		overlayManager.add(overlay);
		infoBoxManager.addInfoBox(infoBox);

		executor = executors.get();
		sampler = executor.scheduleAtFixedRate(this::sampleOnce, PERIOD_S, PERIOD_S, TimeUnit.SECONDS);
		clientThread.invokeLater(this::readStartState);

		settings = SettingsView.unknown(probe.heapMaxMb(), os, probe.source());
		settingsDirty = true;
		if (developerMode)
		{
			engine.selfTimer().on(true);
			WhyLagDevBridge.handle = new Handle();
		}
	}

	@Override
	protected void shutDown()
	{
		// First, before every null guard: a stale handle must never outlive the plugin it points at (T7).
		WhyLagDevBridge.handle = null;
		final ScheduledFuture<?> task = sampler;
		sampler = null;
		if (task != null)
		{
			task.cancel(false);
		}
		final ScheduledExecutorService ex = executor;
		executor = null;
		if (ex != null)
		{
			ex.shutdownNow();
		}
		final HostProbe probe = hostProbe;
		if (probe != null)
		{
			probe.stop();
		}
		if (navButton != null)
		{
			clientToolbar.removeNavigation(navButton);
			navButton = null;
		}
		if (overlay != null)
		{
			overlayManager.remove(overlay);
			overlay = null;
		}
		if (infoBox != null)
		{
			infoBoxManager.removeInfoBox(infoBox);
			infoBox = null;
		}
		final WhyLagPanel p = panel;
		panel = null;
		if (p != null && p.isActive())
		{
			edt.accept(p::onDeactivate);
		}
	}

	// ---------------------------------------------------------------- the client thread

	@Subscribe(priority = FIRST)
	public void onBeforeRender(BeforeRender event)
	{
		final long nanos = System.nanoTime();
		engine.frame(nanos, client.getGameCycle(), hostProbe.currentThreadCpuNanos());
	}

	@Subscribe(priority = FIRST)
	public void onGameTick(GameTick event)
	{
		final long nanos = System.nanoTime();
		if (sceneTicks == 0)
		{
			engine.scene(counter.players(), counter.npcs(), region());
		}
		sceneTicks = sceneTicks + 1 >= Thresholds.SCENE_EVERY_TICKS ? 0 : sceneTicks + 1;
		engine.tick(nanos, client.getGameCycle());
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged event)
	{
		final long nanos = System.nanoTime();
		final int code = StateCodes.of(event.getGameState());
		inGame = State.inGame(code);
		engine.gameState(nanos, code);
		if (code == State.LOGIN_SCREEN || code == State.HOPPING)
		{
			counter.reset();
		}
	}

	@Subscribe
	public void onWorldChanged(WorldChanged event)
	{
		engine.world(System.nanoTime(), client.getWorld());
	}

	@Subscribe
	public void onPlayerSpawned(PlayerSpawned event)
	{
		counter.playerSpawned();
	}

	@Subscribe
	public void onPlayerDespawned(PlayerDespawned event)
	{
		counter.playerDespawned();
	}

	@Subscribe
	public void onNpcSpawned(NpcSpawned event)
	{
		counter.npcSpawned();
	}

	@Subscribe
	public void onNpcDespawned(NpcDespawned event)
	{
		counter.npcDespawned();
	}

	/** The start-up read (client thread): what starts the warm-up when the plugin is switched on while logged in. */
	private void readStartState()
	{
		final long nanos = System.nanoTime();
		final int code = StateCodes.of(client.getGameState());
		inGame = State.inGame(code);
		engine.gameState(nanos, code);
		engine.world(nanos, client.getWorld());
	}

	/** The local player's region, 0 when there is no player. Client thread. */
	private int region()
	{
		final Player player = client.getLocalPlayer();
		if (player == null)
		{
			return 0;
		}
		final WorldPoint where = player.getWorldLocation();
		return where == null ? 0 : where.getRegionID();
	}

	// ---------------------------------------------------------------- only a volatile (T12)

	@Subscribe
	public void onFocusChanged(FocusChanged event)
	{
		engine.focus(event.isFocused());
	}

	@Subscribe
	public void onPluginChanged(PluginChanged event)
	{
		settingsDirty = true;
	}

	@Subscribe
	public void onConfigChanged(ConfigChanged event)
	{
		settingsDirty = true;
		if (WhyLagConfig.GROUP.equals(event.getGroup()) && "systemStats".equals(event.getKey()))
		{
			probeSwapAsked = true;
		}
	}

	// ---------------------------------------------------------------- the sampler thread

	/** One run of the 1 s task (T6). It can never die: every failure is logged once per class and it carries on. */
	void sampleOnce()
	{
		try
		{
			sample();
		}
		catch (RuntimeException | LinkageError e)
		{
			warnOnce(e);
		}
	}

	private void sample()
	{
		final long nanos = nanoClock.getAsLong();
		final long wallMs = wallClock.getAsLong();
		if (probeSwapAsked)
		{
			probeSwapAsked = false;
			swapProbe();
		}
		connection.read(inGame, conn);
		final HostProbe probe = hostProbe;
		final int heapUsedMb = probe.heapUsedMb();
		final int procCpuPct = probe.processCpuPct();
		final int sysCpuPct = probe.systemCpuPct();
		if (settingsDirty || nanos - settingsReadNanos >= Thresholds.REFRESH_REREAD_S * NANOS_PER_SECOND)
		{
			settingsDirty = false;
			settingsReadNanos = nanos;
			settings = settingsReader.read();
		}
		engine.host(nanos, conn.rttMicros, conn.sent, conn.resent, conn.conn, heapUsedMb, procCpuPct, sysCpuPct);
		engine.step(nanos, wallMs, settings);

		final long nowSec = session.secOf(nanos);
		badge.update(engine.verdict(), engine.openEvent(), session.events.last(), session.lastSec(nowSec),
			config.badgeShow(), config.badgeStyle(), config.badgeWhenSmooth(), config.badgeChatLine());
		final String line = badge.takeChatLine();
		if (line != null)
		{
			chatLine.send(line);
		}
		refreshPanel();
	}

	/** The old probe stopped, a new one made from the setting and started on the same session (sampler thread). */
	private void swapProbe()
	{
		final HostProbe old = hostProbe;
		old.stop();
		final HostProbe next = hostProbes.apply(config.systemStats());
		next.start(engine::gcPause, session.startNanos);
		hostProbe = next;
		settingsDirty = true;
	}

	/** One snapshot for the panel, only while it is showing (T9). Sampler thread. */
	private void refreshPanel()
	{
		final WhyLagPanel p = panel;
		if (p == null || !p.isActive())
		{
			return;
		}
		final PanelSnapshot s = engine.snapshot(p.range(), wallClock.getAsLong());
		edt.accept(() -> p.show(s));
	}

	/** A snapshot asked for by the panel, on the sampler thread. */
	private void refreshSafely()
	{
		try
		{
			refreshPanel();
		}
		catch (RuntimeException | LinkageError e)
		{
			warnOnce(e);
		}
	}

	/** Hands one snapshot build to the sampler thread; nothing when the plugin is stopping. Swing thread. */
	private void askForSnapshot()
	{
		final ScheduledExecutorService ex = executor;
		if (ex == null)
		{
			return;
		}
		try
		{
			ex.execute(this::refreshSafely);
		}
		catch (RejectedExecutionException e)
		{
			// the plugin is shutting down: no panel to feed
		}
	}

	private void warnOnce(Throwable e)
	{
		if (warned.add(e.getClass()))
		{
			log.warn("2h Why Lag: the sampler caught {} and carries on (logged once per kind)", e.toString(), e);
		}
	}

	/** The screen's refresh rate now, 0 on any failure or when the screen does not say (sampler thread). */
	private int refreshHz()
	{
		try
		{
			final Canvas canvas = client.getCanvas();
			if (canvas == null)
			{
				return 0;
			}
			final GraphicsConfiguration gc = canvas.getGraphicsConfiguration();
			if (gc == null)
			{
				return 0;
			}
			final int hz = gc.getDevice().getDisplayMode().getRefreshRate();
			return hz == DisplayMode.REFRESH_RATE_UNKNOWN || hz < 0 ? 0 : hz;
		}
		catch (RuntimeException e)
		{
			return 0;
		}
	}

	/** The memory source of the probe in use now: the field is swapped on a {@code systemStats} change. */
	private MemorySource memorySource()
	{
		return hostProbe.source();
	}

	private ScheduledExecutorService newExecutor()
	{
		return Executors.newSingleThreadScheduledExecutor(r ->
		{
			final Thread t = new Thread(r, SAMPLER_THREAD);
			t.setDaemon(true);
			samplerThread = t;
			return t;
		});
	}

	// ---------------------------------------------------------------- the panel's and the lab's doors

	/** What the panel asks of the plugin, on the Swing thread. */
	private final class Actions implements PanelActions
	{
		@Override
		public void rangeChanged(int minutes)
		{
			askForSnapshot();
		}

		@Override
		public void activated()
		{
			askForSnapshot();
		}

		@Override
		public String report(PanelSnapshot s)
		{
			return ReportText.of(s);
		}
	}

	/** The developer-mode handle the lab's probe reads (installed only when {@code developerMode} is true). */
	private final class Handle implements DevHandle
	{
		@Override
		public LagSource source()
		{
			return engine;
		}

		@Override
		public SettingsView settings()
		{
			return settings;
		}

		@Override
		public HostProbe hostProbe()
		{
			return hostProbe;
		}

		@Override
		public long samplerThreadId()
		{
			final Thread t = samplerThread;
			return t == null ? -1 : t.getId();
		}

		@Override
		public PanelControl control()
		{
			return panel;
		}

		@Override
		public BadgeView badge()
		{
			return badge.view();
		}
	}
}
