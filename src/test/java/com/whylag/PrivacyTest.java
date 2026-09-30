package com.whylag;

import com.whylag.WhyLagWiringTest.Fixture;
import com.whylag.core.Answer;
import com.whylag.core.Cause;
import com.whylag.core.CheckFacts;
import com.whylag.core.CheckFixtures;
import com.whylag.core.Checks;
import com.whylag.core.Confidence;
import com.whylag.WhyLagWiringTest.FakeDetector;
import com.whylag.WhyLagWiringTest.FakeJudge;
import com.whylag.core.Diagnostics;
import com.whylag.core.LagEngine;
import com.whylag.core.LagEvent;
import com.whylag.core.Level;
import com.whylag.core.MinuteLine;
import com.whylag.core.MinuteLog;
import com.whylag.core.Os;
import com.whylag.core.PanelSnapshot;
import com.whylag.core.ReportText;
import com.whylag.core.Renderer;
import com.whylag.core.Session;
import com.whylag.core.SettingsView;
import com.whylag.core.SnapshotBuilder;
import com.whylag.core.Trace;
import com.whylag.core.Trigger;
import com.whylag.core.Verdict;
import java.io.IOException;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import net.runelite.api.GameState;
import net.runelite.api.Player;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.WorldChanged;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import static com.whylag.WhyLagWiringTest.field;
import static com.whylag.WhyLagWiringTest.gameState;
import static com.whylag.WhyLagWiringTest.onEdt;
import static com.whylag.WhyLagWiringTest.onSamplerThread;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Privacy (1.0.1, lot A): the report is pasted into a public post, so no player name, no account hash and no file
 * path may be in it, whichever door they try to come in by. The path is a thing the plugin CAN meet (an exception
 * from the disk names its file, and the file is under the Windows user's name), so a fake one is fed through every
 * entry point - the notes, the warnings, the errors, the minute line and the snapshot - and never appears in
 * {@code ReportText}'s output. The name and the hash are things the plugin never reads: the test gives the client a
 * fake player and a fake account hash, drives the whole plugin, and proves neither was asked for and neither is in
 * the report.
 */
public class PrivacyTest
{
	private static final String NAME = "Zezima Fakename";
	private static final long HASH = -7_718_231_960_412_345_678L;
	private static final String WIN_USER = "fakeperson";
	private static final String PATH = "C:\\Users\\" + WIN_USER + "\\AppData\\Roaming\\RuneLite\\whylag.txt";
	private static final long WALL = 1_790_000_000_000L;

	/** A path in a note, a warning's key and text, an error's message and frame, the client version, a verdict. */
	@Test
	public void aPathFedThroughEveryEntryPointNeverReachesTheReport()
	{
		final Diagnostics d = new Diagnostics(ZoneOffset.UTC);
		d.note(WALL, "cannot open " + PATH);
		d.warnOnce("key " + PATH, "text " + PATH);
		d.error(WALL, new IOException(PATH));
		final RuntimeException odd = new IllegalStateException("bad " + PATH, new IOException(PATH));
		odd.setStackTrace(new StackTraceElement[] {new StackTraceElement("a.B", "m", PATH, 3)});
		d.error(WALL, odd);

		final Trace trace = Trace.steady(60);
		final Session session = trace.build();
		final MinuteLog minutes = new MinuteLog(ZoneOffset.UTC);
		minutes.add(MinuteLine.of(session, 60));

		final Verdict v = new Verdict(Cause.ALL_CLEAR, Confidence.SURE, Level.OK, "Smooth " + PATH, "No lag " + PATH,
			"Fix " + PATH, "", 0, 0, 416, -1, null, null);
		final Verdict lag = new Verdict(Cause.SLOW_WORLD, Confidence.LIKELY, Level.BAD, "World " + PATH, "p " + PATH,
			"f " + PATH, "", 0, 3, 416, 0, null, null);
		session.events.add(new LagEvent(0, 10, 12, session.wallMsOf(10), Trigger.TICK_OFF.bit(), Trigger.TICK_OFF,
			416, 0, 0, 0, 50, 22, 700, 952, 640, 41, 45, 40, 900, 0, false, false, lag));
		final SettingsView settings = new SettingsView(Renderer.GPU, false, false, 0, false, 0, false, "", 0, 50,
			"MSAA_2 " + PATH, 0, 60, Os.WINDOWS, "1.13.0 " + PATH);
		final PanelSnapshot built = new SnapshotBuilder().build(session, v, 10, 60, session.wallMsOf(60), settings,
			"self: " + PATH);

		final String report = ReportText.of(built.withDiagnostics("1.0.1 " + PATH, minutes.text(), d.text()),
			"Verdict: " + PATH + "\n");

		assertFalse(report, report.contains(WIN_USER));
		assertFalse(report, report.contains("C:\\"));
		assertFalse(report, report.contains("AppData"));
		assertFalse(report, report.contains("RuneLite\\"));
		assertTrue("every one of them says so", report.split("<path>", -1).length - 1 >= 12);
		assertTrue(report, report.contains("java.io.IOException: <path>"));
	}

	/**
	 * A path in every word the ten checks print - the system's name and both versions - never reaches the
	 * report's Verdict and Checks sections, nor the verdict the Troubleshoot window shows on top.
	 */
	@Test
	public void aPathInTheFactsNeverReachesTheChecksOrTheVerdict()
	{
		final CheckFacts.Builder b = CheckFixtures.healthy();
		b.os = "Windows " + PATH;
		b.clientVersion = "1.12.38 " + PATH;
		b.javaVersion = "17 " + PATH;
		b.cardState = Answer.SMOOTH;
		final CheckFacts facts = b.build();

		final String verdict = Checks.verdict(Checks.run(facts));
		assertTrue("the client is too old, so C8 is the verdict: " + verdict, verdict.contains("is older than"));
		assertFalse(verdict, verdict.contains(WIN_USER));
		assertTrue(verdict, verdict.contains("<path>"));

		final Trace trace = Trace.steady(60);
		final Session session = trace.build();
		final Verdict smooth = new Verdict(Cause.ALL_CLEAR, Confidence.SURE, Level.OK, "Smooth", "No lag", "", "", 0, 0,
			416, -1, null, null);
		final PanelSnapshot built = new SnapshotBuilder().build(session, smooth, 10, 60, session.wallMsOf(60),
			trace.settings(), "");
		final String report = ReportText.of(built, Checks.section(Checks.run(facts)));
		assertFalse(report, report.contains(WIN_USER));
		assertFalse(report, report.contains("C:\\"));
		assertFalse(report, report.contains("AppData"));
		assertTrue(report, report.contains("Verdict: Client 1.12.38 <path> is older"));
	}

	/**
	 * The plugin driven end to end with a client that has a fake player and a fake account hash, a step that throws
	 * an exception whose message is a path, a login, a hop, a lag and a minute of steps: the name and the hash are
	 * never asked for, and neither they nor the path are in the report.
	 */
	@Test
	public void theNameAndTheHashAreNeverReadAndThePathNeverShows() throws Exception
	{
		final Fixture f = new Fixture(false);
		final Player player = mock(Player.class);
		when(player.getName()).thenReturn(NAME);
		when(player.getWorldLocation()).thenReturn(new WorldPoint(3200, 3200, 0));
		when(f.client.getLocalPlayer()).thenReturn(player);
		when(f.client.getAccountHash()).thenReturn(HASH);
		when(f.client.getWorld()).thenReturn(416);
		final FakeJudge judge = new FakeJudge();
		judge.message = "cannot read " + PATH;
		f.plugin.engines = s -> new LagEngine(s, new FakeDetector(), judge, new SnapshotBuilder());
		onEdt(f.plugin::startUp);

		f.plugin.onGameStateChanged(gameState(GameState.LOGGED_IN));
		f.plugin.onWorldChanged(new WorldChanged());
		f.plugin.onGameTick(new GameTick());
		f.closeSecond(100);
		for (int i = 1; i <= 130; i++)
		{
			f.at(100 + i);
			judge.throwing = i <= 5;
			f.plugin.sampleOnce();
		}

		final PanelSnapshot s = f.plugin.attach(f.engine().snapshot(10, f.wall()));
		final String report = ReportText.of(s);

		assertTrue("the failing step was recorded",
			report.contains("java.lang.IllegalStateException: cannot read <path>"));
		assertTrue("the steps after the failures went on and noted the login", report.contains("logged in, world 416"));
		assertFalse(report, report.contains(NAME));
		assertFalse(report, report.contains("Zezima"));
		assertFalse(report, report.contains(Long.toString(HASH)));
		assertFalse(report, report.contains(Long.toString(Math.abs(HASH))));
		assertFalse(report, report.contains(WIN_USER));
		assertFalse(report, report.contains("C:\\"));
		verify(player, never()).getName();
		verify(f.client, never()).getAccountHash();

		// ... and the same through the button: the checks run on the sampler thread, the report comes back whole.
		final List<Report> got = new ArrayList<>();
		f.posted.clear();
		((PanelActions) field(f.plugin, "actions")).testAndReport(s, got::add);
		final ArgumentCaptor<Runnable> task = ArgumentCaptor.forClass(Runnable.class);
		verify(f.executor).execute(task.capture());
		onSamplerThread(task.getValue());
		onEdt(f.posted.get(0));
		final String tested = got.get(0).text;
		assertTrue(tested, tested.contains("Checks:"));
		assertFalse(tested, tested.contains(NAME));
		assertFalse(tested, tested.contains("Zezima"));
		assertFalse(tested, tested.contains(Long.toString(HASH)));
		assertFalse(tested, tested.contains(Long.toString(Math.abs(HASH))));
		assertFalse(tested, tested.contains(WIN_USER));
		assertFalse(tested, tested.contains("C:\\"));
		assertFalse(got.get(0).verdict, got.get(0).verdict.contains(WIN_USER));
		verify(player, never()).getName();
		verify(f.client, never()).getAccountHash();
		onEdt(f.plugin::shutDown);
	}
}
