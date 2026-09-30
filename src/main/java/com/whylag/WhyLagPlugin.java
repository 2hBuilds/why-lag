package com.whylag;

import com.google.inject.Provides;
import com.whylag.core.Answer;
import com.whylag.core.BadgeModel;
import com.whylag.core.BadgeSettings;
import com.whylag.core.BadgeStyle;
import com.whylag.core.BadgeView;
import com.whylag.core.CheckFacts;
import com.whylag.core.CheckResult;
import com.whylag.core.Checks;
import com.whylag.core.Diagnostics;
import com.whylag.core.LagDetector;
import com.whylag.core.LagEngine;
import com.whylag.core.LagSource;
import com.whylag.core.MinuteLine;
import com.whylag.core.MinuteLog;
import com.whylag.core.Os;
import com.whylag.core.PanelSnapshot;
import com.whylag.core.ReportText;
import com.whylag.core.Session;
import com.whylag.core.SettingsView;
import com.whylag.core.SnapshotBuilder;
import com.whylag.core.State;
import com.whylag.core.StepNotes;
import com.whylag.core.Thresholds;
import com.whylag.core.VerdictEngine;
import com.whylag.core.WhenSmooth;
import com.whylag.ui.NavIcon;
import com.whylag.ui.WhyLagPanel;
import java.awt.Canvas;
import java.awt.DisplayMode;
import java.awt.GraphicsConfiguration;
import java.time.ZoneId;
import java.util.List;
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
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
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
 * and snapshot builder), the RuneLite-side readers, the side panel and the game badge, and it owns the one thread
 * the plugin runs, {@value #SAMPLER_THREAD}.
 *
 * <p><b>Threads.</b> The client thread hands the engine every frame ({@link #onBeforeRender}), every tick
 * ({@link #onGameTick}, with the scene counts every {@code SCENE_EVERY_TICKS} ticks), the game state, the world and
 * the spawn counts; both timestamped subscribers run at priority 100 so other plugins' handlers do not shift them.
 * The sampler thread runs {@link #sampleOnce} once a second at a fixed rate: the connection probe, the settings
 * when they may have changed, the host second, the step, the badge and its chat line, and a snapshot for the panel
 * only while the panel is showing (T9). The handlers of {@code ConfigChanged} and {@code FocusChanged} only write a
 * volatile (T12). The sampler never reads the game state: it reads the volatile {@code inGame} flag that the client
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
 * Choice: the settings-dirty flag is cleared BEFORE the settings are read, so a change that arrives during the read
 * is read again at the next run.
 * <br>
 * Choice: every {@code ConfigChanged} marks the settings dirty, whatever the group: RuneLite's own group holds the
 * flags that say which of the GPU, 117 HD and FPS Control plugins is on, and their three groups hold the rest
 * ({@link SettingsReader}). No plugin object is asked for anything, so no {@code PluginChanged} is heard.
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
 * Choice: the clocks, the engine, the executor and the hop to the Swing thread are package-private seams that
 * {@code WhyLagWiringTest} replaces; in a client they are the real ones.
 * <br>
 * Choice: the diagnostics log and the minute log are written on the sampler thread alone - the notes of a step by
 * {@link StepNotes} after the engine's step, one {@link MinuteLine} every {@value #MINUTE_STEPS} steps - and read
 * there too: {@link #refreshPanel} attaches their texts to the snapshot it posts, so the Swing thread never reads
 * either, and nothing of either is written to disk or sent.
 * <br>
 * Choice: a step that throws is recorded ({@code Diagnostics.error}) and counted under a key of its exception's
 * kind ({@link Diagnostics#kind}, the class name read from the exception's text); the client log gets one WARN per
 * key per session, the first time {@code warnOnce} answers true.
 * <br>
 * Choice: "Troubleshoot..." ({@link PanelActions#testAndReport}) is one task on the sampler's executor: the
 * facts are read there ({@link CheckFacts.Builder#session} reads the rings, which only the sampler thread may), the
 * ten checks run there, the result is noted, and the report is made there from the panel's snapshot with the
 * diagnostics texts attached again, so it holds the note of this very run. Only the callback goes to the Swing
 * thread. The Swing thread never waits and never reads a ring.
 * <br>
 * Choice: the gear menu's four badge settings (1.0.1, lot C) are written on the Swing thread through
 * {@code ConfigManager.setConfiguration} under the frozen keys of {@link WhyLagConfig}, the two enum settings by
 * their constants' NAMES as RuneLite stores them, and read back on the sampler thread with the config the badge
 * reads, {@link BadgeSettings}, attached to every snapshot by {@link #attach}; a write then asks for one snapshot at
 * once, so the menu that opens next ticks the new value.
 * <br>
 * Choice: "the last step" of check C5 is the last step's START on the sampler's clock, so a press that waited behind
 * a long step sees the gap; its work is what the engine's {@code host} and {@code step} calls took, measured with
 * the same clock, which leaves the probes' own reads and the snapshot for the panel out.
 * <br>
 * Choice: the clock watch compares each step's wall time with the monotonic clock's: the wall clock going back
 * against it by {@value #CLOCK_BACK_MIN_MS} ms or more is a jump back; the latest one is kept for check C9.
 * <br>
 * Choice: a test that throws is recorded like a step that throws and still hands the panel a verdict ("the checks
 * could not finish") with the report as it stands, so the window never stays on "Testing...".
 */
@PluginDescriptor(
	name = "2h Why Lag",
	description = "Tells you what caused the lag: frames, ticks or ping (v" + Version.CURRENT + ")",
	tags = {"lag", "ping", "fps", "tick", "freeze", "stutter"}
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
	/** Sampler steps between two lines of the minute log: a minute at the 1 s period (a wiring number). */
	static final int MINUTE_STEPS = 60;
	/** The wall clock going back against the monotonic one by this many ms is a jump back (a wiring number). */
	static final long CLOCK_BACK_MIN_MS = 2000;
	private static final long NANOS_PER_SECOND = 1_000_000_000L;
	private static final long NANOS_PER_MS = 1_000_000L;
	private static final long NANOS_PER_US = 1_000L;
	private static final long MS_PER_SECOND = 1000L;
	/** A clock reading not taken yet. */
	private static final long NO_CLOCK = Long.MIN_VALUE;

	@Inject
	private Client client;

	@Inject
	private ClientThread clientThread;

	@Inject
	private ClientToolbar clientToolbar;

	@Inject
	private ConfigManager configManager;

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
	/** The recording over a new session: the real detector, judge and snapshot builder. */
	Function<Session, LagEngine> engines = s -> new LagEngine(s, new LagDetector(), new VerdictEngine(),
		new SnapshotBuilder());
	/** The plugin's own executor (T5). */
	Supplier<ScheduledExecutorService> executors = this::newExecutor;
	/** The hop to the Swing thread. */
	Consumer<Runnable> edt = SwingUtilities::invokeLater;

	// ---------------------------------------------------------------- what startUp builds

	private final PanelActions actions = new Actions();

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
	/** What the plugin has been doing: notes, warnings and errors. Written on the sampler thread. */
	private Diagnostics diagnostics;
	/** One line a minute, the last hour. Written on the sampler thread. */
	private MinuteLog minutes;
	/** What changed since the step before, written as notes. Sampler thread only. */
	private StepNotes notes;
	/** The client's version as RuneLite names it; "" = unknown. Set in startUp. */
	private String clientVersion = "";

	/** The settings the last step was given. */
	private volatile SettingsView settings;
	/** Written on the client thread only; the sampler hands it to the connection probe. */
	private volatile boolean inGame;
	/** Whether a GPU renderer is on ({@code Client#isGpu}). Written on the client thread only; the reader asks it. */
	private volatile boolean gpu;
	/** The settings must be read again at the next sampler run. */
	private volatile boolean settingsDirty;
	/** The thread the executor made; null before it made one. */
	private volatile Thread samplerThread;

	/** When the settings were read last, on the sampler's clock. Sampler thread only. */
	private long settingsReadNanos;
	/** Steps since the last line of the minute log. Sampler thread only. */
	private int minuteSteps;
	/** When the last step started, on the sampler's clock; {@link #NO_CLOCK} before the first. Sampler thread only. */
	private long stepStartNanos = NO_CLOCK;
	/** What the last step's host and step calls took, in ns; -1 before the first. Sampler thread only. */
	private long stepCostNanos = -1;
	/** The monotonic and the wall reading of the step before; {@link #NO_CLOCK} before the first. Sampler thread only. */
	private long clockNanos = NO_CLOCK, clockWallMs;
	/** How far back the wall clock last jumped, in whole seconds, and when; 0 = never. Sampler thread only. */
	private int clockBackS;
	private long clockBackAtWallMs;
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
		final String osName = System.getProperty("os.name");
		os = Os.of(osName);
		final ZoneId zone = ZoneId.systemDefault();
		diagnostics = new Diagnostics(zone);
		minutes = new MinuteLog(zone);
		notes = new StepNotes(diagnostics);
		minuteSteps = 0;
		stepStartNanos = NO_CLOCK;
		stepCostNanos = -1;
		clockNanos = NO_CLOCK;
		clockBackS = 0;
		clockBackAtWallMs = 0;
		final String version = RuneLiteProperties.getVersion();
		clientVersion = version == null ? "" : version;
		diagnostics.note(wallClock.getAsLong(), "plugin started, version " + Version.CURRENT + ", client "
			+ (version == null ? "unknown" : version) + ", " + (osName == null ? "unknown system" : osName));
		session = new Session(nanoClock.getAsLong(), wallClock.getAsLong(), os, zone);
		engine = engines.apply(session);

		counter = new SceneCounter();
		sceneTicks = 0;
		inGame = false;
		connection = new ConnectionProbe(client::getSocketFD, Ping::getTCPInfo);
		gpu = false;
		settingsReader = new SettingsReader(configManager, () -> gpu, this::refreshHz, os,
			version == null ? "" : version);

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

		settings = SettingsView.unknown(os);
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
		engine.frame(nanos, client.getGameCycle());
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
		readRenderer();
	}

	/**
	 * Whether a GPU renderer is on, from the client's own answer (client thread). A change marks the settings to be
	 * read again, so the answer that arrives after a re-read began is used a second later.
	 */
	private boolean readRenderer()
	{
		final boolean now = client.isGpu();
		if (now != gpu)
		{
			gpu = now;
			settingsDirty = true;
		}
		return true;
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
	public void onConfigChanged(ConfigChanged event)
	{
		settingsDirty = true;
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
			recordFailure(e);
		}
	}

	private void sample()
	{
		final long nanos = nanoClock.getAsLong();
		final long wallMs = wallClock.getAsLong();
		stepStartNanos = nanos;
		watchClock(nanos, wallMs);
		connection.read(inGame, conn);
		if (settingsDirty || nanos - settingsReadNanos >= Thresholds.REFRESH_REREAD_S * NANOS_PER_SECOND)
		{
			settingsDirty = false;
			settingsReadNanos = nanos;
			// The renderer is the client thread's to ask: the answer comes back through the volatile, and a change
			// makes the next run read again.
			clientThread.invokeLater(this::readRenderer);
			settings = settingsReader.read();
		}
		final long work = nanoClock.getAsLong();
		engine.host(nanos, conn.rttMicros, conn.sent, conn.resent, conn.conn);
		engine.step(nanos, wallMs, settings);
		stepCostNanos = Math.max(0, nanoClock.getAsLong() - work);

		final long nowSec = session.secOf(nanos);
		badge.update(engine.verdict(), engine.openEvent(), session.events.last(), session.lastSec(nowSec),
			config.badgeShow(), config.badgeStyle(), config.badgeWhenSmooth(), config.badgeChatLine());
		final String line = badge.takeChatLine();
		if (line != null)
		{
			chatLine.send(line);
		}
		notes.step(wallMs, session, nowSec, engine.verdict(), engine.openEvent(), session.events.last(), conn.conn,
			settings);
		if (++minuteSteps >= MINUTE_STEPS)
		{
			minuteSteps = 0;
			minutes.add(MinuteLine.of(session, nowSec));
		}
		refreshPanel();
	}

	/** Keeps the latest jump back of the wall clock against the monotonic one, for check C9 (sampler thread). */
	private void watchClock(long nanos, long wallMs)
	{
		if (clockNanos != NO_CLOCK)
		{
			final long back = (nanos - clockNanos) / NANOS_PER_MS - (wallMs - clockWallMs);
			if (back >= CLOCK_BACK_MIN_MS)
			{
				clockBackS = (int) Math.min(Integer.MAX_VALUE, back / MS_PER_SECOND);
				clockBackAtWallMs = wallMs;
			}
		}
		clockNanos = nanos;
		clockWallMs = wallMs;
	}

	/** One snapshot for the panel, only while it is showing (T9). Sampler thread. */
	private void refreshPanel()
	{
		final WhyLagPanel p = panel;
		if (p == null || !p.isActive())
		{
			return;
		}
		final PanelSnapshot s = attach(engine.snapshot(p.range(), wallClock.getAsLong()));
		edt.accept(() -> p.show(s));
	}

	/**
	 * {@code built} with the plugin's version, the texts of the minute log and the diagnostics and the badge's four
	 * settings as stored attached: the report is made from the first three, the gear menu ticks the last. Sampler
	 * thread. A null snapshot (a test's source that builds none) stays null.
	 */
	PanelSnapshot attach(PanelSnapshot built)
	{
		if (built == null)
		{
			return null;
		}
		return built.withDiagnostics(Version.CURRENT, minutes.text(), diagnostics.text()).withBadgeSettings(
			new BadgeSettings(config.badgeShow(), config.badgeStyle(), config.badgeWhenSmooth(),
				config.badgeChatLine()));
	}

	/**
	 * One run of "Troubleshoot..." (sampler thread): the facts, the ten checks, the note of the result and
	 * the report made from {@code s}, handed to the Swing thread through {@code back}. A run that throws is recorded
	 * and still answers, with a verdict that says the checks could not finish.
	 */
	private void testNow(PanelSnapshot s, Consumer<Report> back)
	{
		Report report;
		try
		{
			final long wallMs = wallClock.getAsLong();
			final List<CheckResult> results = Checks.run(facts(nanoClock.getAsLong()));
			diagnostics.note(wallMs, Checks.summary(results));
			report = new Report(ReportText.of(attach(s), Checks.section(results)), Checks.verdict(results));
		}
		catch (RuntimeException | LinkageError e)
		{
			recordFailure(e);
			report = new Report(plainReport(s), "The checks could not finish: " + simpleKind(e));
		}
		final Report ready = report;
		edt.accept(() -> back.accept(ready));
	}

	/** The report of {@code s} with no checks section; "" when even that cannot be made. */
	private String plainReport(PanelSnapshot s)
	{
		try
		{
			return ReportText.of(attach(s));
		}
		catch (RuntimeException | LinkageError e)
		{
			return "";
		}
	}

	/** Everything the ten checks need, read now (sampler thread): the rings, the settings, the clocks. */
	private CheckFacts facts(long nanos)
	{
		final CheckFacts.Builder b = new CheckFacts.Builder().session(session, nanos).settings(settings);
		b.lastStepAgoMs = stepStartNanos == NO_CLOCK ? -1 : Math.max(0, (nanos - stepStartNanos) / NANOS_PER_MS);
		b.stepCostUs = stepCostNanos < 0 ? -1 : stepCostNanos / NANOS_PER_US;
		b.badgeRegistered = overlay != null && infoBox != null;
		b.badgeShow = config.badgeShow();
		b.clientVersion = clientVersion;
		b.javaVersion = System.getProperty("java.version");
		b.os = System.getProperty("os.name");
		b.clockJumpedBackS = clockBackS;
		b.clockJumpAtWallMs = clockBackAtWallMs;
		b.zone = session.zone;
		b.cardState = Answer.of(engine.verdict());
		return b.build();
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
			recordFailure(e);
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

	/**
	 * A run of the sampler failed: the error is kept for the report, and the client log gets one WARN per kind of
	 * trouble per session, the first time its key is raised (T6). The sampler carries on either way.
	 */
	private void recordFailure(Throwable e)
	{
		final Diagnostics d = diagnostics;
		final boolean first = d == null
			|| d.warnOnce("step: " + Diagnostics.kind(e), e.toString());
		if (d != null)
		{
			d.error(wallClock.getAsLong(), e);
		}
		if (first)
		{
			log.warn("2h Why Lag: the sampler caught {} and carries on (logged once per kind)", e.toString(), e);
		}
	}

	/** The kind of a throwable as a short word: {@link Diagnostics#kind} without its package. */
	private static String simpleKind(Throwable e)
	{
		final String kind = Diagnostics.kind(e);
		return kind.substring(kind.lastIndexOf('.') + 1);
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
		public void testAndReport(PanelSnapshot s, Consumer<Report> back)
		{
			final ScheduledExecutorService ex = executor;
			if (ex == null)
			{
				return;
			}
			try
			{
				ex.execute(() -> testNow(s, back));
			}
			catch (RejectedExecutionException e)
			{
				// the plugin is shutting down: nobody is left to show a report to
			}
		}

		@Override
		public void badgeShow(boolean on)
		{
			write("badgeShow", on);
		}

		@Override
		public void badgeStyle(BadgeStyle style)
		{
			write("badgeStyle", style.name());
		}

		@Override
		public void badgeWhenSmooth(WhenSmooth choice)
		{
			write("badgeWhenSmooth", choice.name());
		}

		@Override
		public void badgeChatLine(boolean on)
		{
			write("badgeChatLine", on);
		}

		/** One setting stored under its frozen key, then one snapshot asked for so the next menu ticks it. */
		private void write(String key, boolean value)
		{
			configManager.setConfiguration(WhyLagConfig.GROUP, key, value);
			askForSnapshot();
		}

		private void write(String key, String name)
		{
			configManager.setConfiguration(WhyLagConfig.GROUP, key, name);
			askForSnapshot();
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
