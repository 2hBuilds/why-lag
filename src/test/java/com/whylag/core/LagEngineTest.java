package com.whylag.core;

import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

/**
 * {@link LagEngine} (contract section 7, L2) over a fake {@link Detector}, {@link Judge} and
 * {@link SnapshotSource}: the engine names no class of another lot, and neither does this test. Times are session
 * ms.
 */
public class LagEngineTest
{
	private static final long START = 4_000_000_000_000L;
	private static final long START_WALL_MS = 1_790_000_000_000L;
	private static final long NANOS_PER_MS = 1_000_000L;
	private static final long RTT_40_MS = 40_000L;
	private static final long WAIT_S = 10;

	private Session session;
	private FakeDetector detector;
	private FakeJudge judge;
	private FakeSnapshots snapshots;
	private LagEngine engine;
	private SettingsView settings;
	private SettingsView changed;

	@Before
	public void setUp()
	{
		session = new Session(START, START_WALL_MS, Os.WINDOWS, ZoneOffset.UTC);
		detector = new FakeDetector();
		judge = new FakeJudge();
		snapshots = new FakeSnapshots();
		engine = new LagEngine(session, detector, judge, snapshots);
		settings = SettingsView.unknown(Os.WINDOWS);
		changed = SettingsView.unknown(Os.WINDOWS);
	}

	private static long at(long ms)
	{
		return START + ms * NANOS_PER_MS;
	}

	private void step(long ms)
	{
		engine.step(at(ms), START_WALL_MS + ms, settings);
	}

	/** A host sample with the steady readings. */
	private void host(long ms, long rttMicros, long sent)
	{
		host(ms, rttMicros, sent, 0);
	}

	/** The same, with the socket's re-sent counter. */
	private void host(long ms, long rttMicros, long sent, long resent)
	{
		engine.host(at(ms), rttMicros, sent, resent, NoData.NONE);
	}

	private static Verdict verdict(Cause cause, long eventId)
	{
		return new Verdict(cause, Confidence.LIKELY, Level.BAD, cause.name(), "proof", "", "", 0, 0, 416, eventId,
			null, null);
	}

	private static LagEvent event(long id, long startSec, long endSec, boolean open)
	{
		return new LagEvent(id, startSec, endSec, START_WALL_MS + startSec * 1000, Trigger.FRAME_GAP.bit(),
			Trigger.FRAME_GAP, 416, 0, 0, 0, 50, 400, 600, 600, 0, 40, 40, -1, 0, 0, open, false, null);
	}

	// ---------------------------------------------------------------- the verdict and its listeners

	@Test
	public void beforeTheFirstStepNothingIsNull()
	{
		assertNotNull(engine.verdict());
		assertSame("what the judge answers for the empty session", judge.answer, engine.verdict());
		assertEquals(1, judge.currentCalls);
		assertEquals(0, judge.lastNowSec);
		assertSame(session, judge.lastSession);
		assertEquals(Renderer.UNKNOWN, judge.lastSettings.renderer);
		assertEquals(Os.OTHER, judge.lastSettings.os);
		assertNull(engine.openEvent());
		assertSame(session, engine.session());
		assertNotNull(engine.selfTimer());

		final PanelSnapshot snapshot = engine.snapshot(10, START_WALL_MS + 300);
		assertNotNull(snapshot);
		assertEquals(0, snapshots.lastNowSec);
		assertSame(engine.verdict(), snapshots.lastShown);
		assertNotNull(snapshots.lastSettings);
		assertEquals("", snapshots.lastFooter);

		// a judge that answers nothing still leaves a verdict: the measuring state
		judge.answer = null;
		final LagEngine other = new LagEngine(session, detector, judge, snapshots);
		assertNotNull(other.verdict());
		assertEquals(Cause.WARMING_UP, other.verdict().cause);
		assertSame(Answer.MEASURING, Answer.of(other.verdict()));
	}

	@Test
	public void verdictChangesWithNoPanel()
	{
		final Verdict first = engine.verdict();
		engine.gameState(at(0), State.LOGGED_IN);
		for (long ms = 0; ms <= 3000; ms += 20)
		{
			engine.frame(at(ms), (int) (ms / 20));
		}
		engine.tick(at(600), 30);
		host(3010, RTT_40_MS, 1000);
		judge.answer = verdict(Cause.SLOW_WORLD, -1);
		step(3020);
		assertSame(judge.answer, engine.verdict());
		assertNotSame(first, engine.verdict());

		judge.answer = verdict(Cause.ALL_CLEAR, -1);
		step(4020);
		assertSame(judge.answer, engine.verdict());

		assertEquals("nobody asked for a snapshot, and none was built", 0, snapshots.builds);
		// T10: the recording ran all the while
		assertEquals(2, session.seconds.frameHead());
		assertEquals(2, session.seconds.hostHead());
		assertEquals(0, session.ticks.head());
		assertEquals(50, session.seconds.frames(1));
		assertEquals(2, detector.advances);
	}

	@Test
	public void listenerIsToldOncePerChange()
	{
		final List<Verdict> told = new ArrayList<>();
		final VerdictListener listener = told::add;
		final List<Verdict> toldToo = new ArrayList<>();
		final VerdictListener second = toldToo::add;
		engine.addVerdictListener(listener);
		engine.addVerdictListener(listener);
		engine.addVerdictListener(second);

		step(1000);
		step(2000);
		assertEquals("the judge answers the object it answered at the start", 0, told.size());

		judge.answer = verdict(Cause.PING_HIGH, -1);
		step(3000);
		step(4000);
		step(5000);
		assertEquals(1, told.size());
		assertSame(judge.answer, told.get(0));
		assertEquals(1, toldToo.size());

		engine.removeVerdictListener(second);
		judge.answer = verdict(Cause.ALL_CLEAR, -1);
		step(6000);
		assertEquals(2, told.size());
		assertSame(judge.answer, told.get(1));
		assertEquals("a removed listener hears nothing", 1, toldToo.size());
	}

	@Test
	public void aListenerThatThrowsDoesNotSilenceTheOthers()
	{
		final List<Verdict> told = new ArrayList<>();
		engine.addVerdictListener(v ->
		{
			throw new IllegalStateException("a listener's own fault");
		});
		engine.addVerdictListener(told::add);
		judge.answer = verdict(Cause.PING_HIGH, -1);
		step(1000);
		assertEquals(1, told.size());
		assertSame(judge.answer, engine.verdict());
	}

	// ---------------------------------------------------------------- threads

	@Test(timeout = 60_000)
	public void frameNeverWaitsForTheStepLock() throws InterruptedException
	{
		judge.entered = new CountDownLatch(1);
		judge.release = new CountDownLatch(1);
		final Thread stepper = new Thread(() -> step(5000), "test-stepper");
		stepper.setDaemon(true);
		stepper.start();
		assertTrue("the step reached the judge", judge.entered.await(WAIT_S, TimeUnit.SECONDS));

		// the step lock is held now. Everything the client thread calls must still run to its end.
		final CountDownLatch clientDone = new CountDownLatch(1);
		final Thread client = new Thread(() ->
		{
			engine.gameState(at(0), State.LOGGED_IN);
			engine.world(at(0), 416);
			engine.scene(3, 4, 12850);
			engine.focus(true);
			for (long ms = 0; ms <= 2000; ms += 20)
			{
				engine.frame(at(ms), (int) (ms / 20));
			}
			engine.tick(at(600), 30);
			clientDone.countDown();
		}, "test-client");
		client.setDaemon(true);
		client.start();
		assertTrue("frame, tick and the rest ran while a step held the lock",
			clientDone.await(WAIT_S, TimeUnit.SECONDS));
		assertEquals(1, session.seconds.frameHead());
		assertEquals(0, session.ticks.head());
		assertEquals(0, session.loggedInSinceSec());

		// the lock IS held: a snapshot, which shares it, waits until the step is over
		final CountDownLatch snapshotDone = new CountDownLatch(1);
		final Thread panel = new Thread(() ->
		{
			engine.snapshot(10, START_WALL_MS);
			snapshotDone.countDown();
		}, "test-panel");
		panel.setDaemon(true);
		panel.start();
		assertFalse("a snapshot waits for the step", snapshotDone.await(300, TimeUnit.MILLISECONDS));
		assertEquals(0, snapshots.builds);

		judge.release.countDown();
		assertTrue(snapshotDone.await(WAIT_S, TimeUnit.SECONDS));
		stepper.join(TimeUnit.SECONDS.toMillis(WAIT_S));
		assertFalse(stepper.isAlive());
		assertEquals(1, snapshots.builds);
	}

	// ---------------------------------------------------------------- the log and the open event

	@Test
	public void openedAddsAndClosedReplaces()
	{
		final LagEvent opened = event(0, 10, 10, true);
		detector.toOpen = opened;
		step(12_000);
		assertEquals(1, session.events.size());
		assertSame(opened, session.events.get(0));
		assertEquals("an open event is not counted", 0, session.events.sessionTotal());
		assertNull(session.events.last());

		final LagEvent closed = event(0, 10, 13, false);
		detector.toClose = closed;
		step(19_000);
		assertEquals("the log holds it ONCE", 1, session.events.size());
		final LagEvent held = session.events.get(0);
		assertFalse(held.open);
		assertEquals(13, held.endSec);
		assertNotNull("the verdict is attached before the log takes it", held.verdict);
		assertEquals(0, held.verdict.eventId);
		assertEquals(Cause.CLIENT_BUSY, held.verdict.cause);
		assertEquals(1, session.events.sessionTotal());
		assertEquals(1, session.events.sessionCount(Group.FRAME_RATE));
		assertSame(held, session.events.last());
		assertSame(held, session.events.byId(0));
		assertSame("the judge was handed the step's settings", settings, judge.lastEventSettings);
	}

	@Test
	public void twoEventsInOneStepAreBothLogged()
	{
		detector.script = out ->
		{
			out.opened(event(0, 5, 5, true));
			out.closed(event(0, 5, 6, false));
			out.opened(event(1, 20, 20, true));
		};
		detector.open = event(1, 20, 20, true);
		step(22_000);
		assertEquals(2, session.events.size());
		assertFalse(session.events.get(0).open);
		assertNotNull(session.events.get(0).verdict);
		assertTrue(session.events.get(1).open);
		assertNull(session.events.get(1).verdict);
		assertEquals(1, session.events.sessionTotal());
		assertEquals(1, engine.openEvent().id);
	}

	@Test
	public void openEventCarriesAProvisionalVerdict()
	{
		final List<Verdict> told = new ArrayList<>();
		engine.addVerdictListener(told::add);
		final Verdict card = engine.verdict();

		final LagEvent open = event(0, 10, 11, true);
		detector.toOpen = open;
		step(12_000);

		final LagEvent published = engine.openEvent();
		assertNotNull(published);
		assertEquals(0, published.id);
		assertTrue(published.open);
		assertEquals(10, published.startSec);
		assertEquals(11, published.endSec);
		assertNotNull(published.verdict);
		assertSame("the judge's verdict of that event", judge.lastEventVerdict, published.verdict);
		assertEquals(0, published.verdict.eventId);
		assertSame("the judge was handed the open event", open, judge.lastEvent);

		assertSame("the card's verdict has not changed", card, engine.verdict());
		assertEquals("and no listener was told", 0, told.size());
		assertNull("nor is it in the log", session.events.get(0).verdict);
	}

	@Test
	public void openEventIsNullWhenNoneIsOpen()
	{
		step(1000);
		assertNull(engine.openEvent());

		detector.toOpen = event(0, 10, 10, true);
		step(12_000);
		assertNotNull(engine.openEvent());
		step(13_000);
		assertNotNull("still open", engine.openEvent());

		detector.toClose = event(0, 10, 10, false);
		step(19_000);
		assertNull("closed: nothing is open", engine.openEvent());
	}

	@Test
	public void theLogKeepsTheOpenedEventUntilItCloses()
	{
		final LagEvent opened = event(0, 10, 10, true);
		detector.toOpen = opened;
		step(12_000);
		// the event grows: the detector answers a new object from open(), the log is told nothing
		final LagEvent grown = event(0, 10, 14, true);
		detector.open = grown;
		step(15_000);
		step(16_000);

		assertEquals(14, engine.openEvent().endSec);
		assertNotNull(engine.openEvent().verdict);
		assertEquals(1, session.events.size());
		assertSame("the log keeps what opened() handed in", opened, session.events.get(0));
		assertNull("the provisional verdict is not in the log", session.events.get(0).verdict);
		assertTrue(session.events.get(0).open);
		assertEquals(0, session.events.sessionTotal());

		detector.toClose = event(0, 10, 14, false);
		step(20_000);
		assertEquals(1, session.events.size());
		assertFalse(session.events.get(0).open);
		assertNotNull(session.events.get(0).verdict);
	}

	@Test
	public void engineNeverTouchesAUsual()
	{
		for (int i = 0; i < 2 * Thresholds.USUAL_MIN_SAMPLES; i++)
		{
			session.rttUsual.add(40);
			session.rttSession.add(45);
			session.fpsUsual.add(50);
		}
		engine.gameState(at(0), State.LOGGED_IN);
		engine.world(at(0), 416);
		for (long ms = 0; ms <= 5000; ms += 20)
		{
			if (ms % 600 == 0 && ms > 0)
			{
				engine.tick(at(ms), (int) (ms / 20));
			}
			engine.frame(at(ms), (int) (ms / 20));
			if (ms % 1000 == 0 && ms > 0)
			{
				host(ms + 10, RTT_40_MS, ms * 3);
				step(ms + 12);
			}
		}
		engine.gameState(at(5100), State.HOPPING);
		engine.world(at(5200), 302);
		engine.gameState(at(5900), State.LOGGED_IN);
		host(6010, RTT_40_MS, 20_000);
		step(6012);
		engine.snapshot(10, START_WALL_MS + 6100);

		assertEquals("a world change resets no usual: the detector does that", 2 * Thresholds.USUAL_MIN_SAMPLES,
			session.rttUsual.count());
		assertEquals(40, session.rttUsual.median());
		assertEquals(2 * Thresholds.USUAL_MIN_SAMPLES, session.rttSession.count());
		assertEquals(2 * Thresholds.USUAL_MIN_SAMPLES, session.fpsUsual.count());
	}

	// ---------------------------------------------------------------- the warm-up

	@Test
	public void loginStartsTheWarmUp()
	{
		assertEquals(Session.NEVER, session.loggedInSinceSec());
		engine.gameState(at(1200), State.LOGIN_SCREEN);
		engine.gameState(at(2200), State.LOGGING_IN);
		assertEquals("not in the game yet", Session.NEVER, session.loggedInSinceSec());
		engine.gameState(at(3200), State.LOADING);
		assertEquals(3, session.loggedInSinceSec());
		engine.gameState(at(4200), State.LOGGED_IN);
		assertEquals("the end of the load is no login", 3, session.loggedInSinceSec());
		engine.gameState(at(9000), State.LOADING);
		engine.gameState(at(9400), State.LOGGED_IN);
		assertEquals("nor is a map load", 3, session.loggedInSinceSec());
		assertFalse(session.warm(3 + Thresholds.WARMUP_S - 1));
		assertTrue(session.warm(3 + Thresholds.WARMUP_S));

		// logged out and in again: it counts from the new login
		engine.gameState(at(100_000), State.LOGIN_SCREEN);
		assertEquals("never set back", 3, session.loggedInSinceSec());
		engine.gameState(at(130_000), State.LOGGING_IN);
		engine.gameState(at(131_500), State.LOGGED_IN);
		assertEquals(131, session.loggedInSinceSec());
	}

	@Test
	public void enablingWhileLoggedInStartsTheWarmUp()
	{
		engine.gameState(at(3400), State.LOGGED_IN);
		assertEquals(3, session.loggedInSinceSec());
	}

	@Test
	public void hopAndReconnectDoNotRestartTheWarmUp()
	{
		engine.gameState(at(3400), State.LOGGED_IN);
		assertEquals(3, session.loggedInSinceSec());

		long ms = 20_000;
		final int[][] orders = {
			{State.HOPPING, State.LOADING, State.LOGGED_IN},
			{State.HOPPING, State.LOGGING_IN, State.LOADING, State.LOGGED_IN},
			{State.CONNECTION_LOST, State.LOGGED_IN},
			{State.CONNECTION_LOST, State.LOGGING_IN, State.LOADING, State.LOGGED_IN},
		};
		for (int[] order : orders)
		{
			for (int code : order)
			{
				ms += 1500;
				engine.gameState(at(ms), code);
				assertEquals("after code " + code + " at " + ms, 3, session.loggedInSinceSec());
			}
			ms += 10_000;
		}

		// a reconnect that failed: back at the login screen, and the login after it counts
		engine.gameState(at(190_000), State.CONNECTION_LOST);
		engine.gameState(at(195_000), State.LOGIN_SCREEN);
		engine.gameState(at(199_000), State.LOGGING_IN);
		assertEquals(3, session.loggedInSinceSec());
		engine.gameState(at(200_300), State.LOGGED_IN);
		assertEquals(200, session.loggedInSinceSec());
	}

	@Test
	public void enablingDuringAHopStillWarmsUp()
	{
		engine.gameState(at(1100), State.HOPPING);
		assertEquals(Session.NEVER, session.loggedInSinceSec());
		engine.gameState(at(4700), State.LOADING);
		assertEquals("no login had been seen yet", 4, session.loggedInSinceSec());
		engine.gameState(at(5200), State.LOGGED_IN);
		assertEquals(4, session.loggedInSinceSec());

		// the same, switched on in the middle of a reconnect
		setUp();
		engine.gameState(at(1100), State.CONNECTION_LOST);
		engine.gameState(at(2100), State.LOGGING_IN);
		engine.gameState(at(6300), State.LOGGED_IN);
		assertEquals(6, session.loggedInSinceSec());
	}

	// ---------------------------------------------------------------- now

	@Test
	public void snapshotUsesTheNowOfTheLastStep()
	{
		step(7300);
		assertEquals("the judge is handed the clock's second", 7, judge.lastNowSec);
		assertEquals(START_WALL_MS + 7300, judge.lastWallMs);
		assertSame(settings, judge.lastSettings);
		assertEquals(-1, detector.lastThroughSec);

		final PanelSnapshot built = engine.snapshot(60, START_WALL_MS + 9_900);
		assertSame(snapshots.answer, built);
		assertEquals("the second of the last step, not of the wall time handed in", 7, snapshots.lastNowSec);
		assertEquals(60, snapshots.lastRange);
		assertEquals(START_WALL_MS + 9_900, snapshots.lastWallMs);
		assertSame(session, snapshots.lastSession);
		assertSame(engine.verdict(), snapshots.lastShown);
		assertSame("the settings of the last step", settings, snapshots.lastSettings);

		engine.step(at(12_999), START_WALL_MS + 12_999, changed);
		engine.snapshot(1, START_WALL_MS + 13_500);
		assertEquals(12, snapshots.lastNowSec);
		assertSame(changed, snapshots.lastSettings);
	}

	@Test
	public void theDetectorIsAdvancedThroughTheRingsHead()
	{
		for (long ms = 0; ms <= 6000; ms += 20)
		{
			engine.frame(at(ms), (int) (ms / 20));
		}
		host(4010, RTT_40_MS, 0);
		step(6020);
		assertEquals("the frame head is 5, the host head 3", 3, detector.lastThroughSec);
		assertSame(session, detector.lastSession);
		assertSame(settings, detector.lastSettings);
	}

	@Test
	public void theFooterIsTheSelfTimersWhileItIsOn()
	{
		engine.snapshot(10, START_WALL_MS);
		assertEquals("", snapshots.lastFooter);
		engine.selfTimer().on(true);
		engine.frame(at(0), 0);
		engine.tick(at(600), 30);
		step(1000);
		engine.snapshot(10, START_WALL_MS);
		assertEquals(engine.selfTimer().footer(), snapshots.lastFooter);
		assertTrue(snapshots.lastFooter.startsWith("self: frame "));
		assertEquals(1, engine.selfTimer().count(SelfTimer.FRAME));
		assertEquals(1, engine.selfTimer().count(SelfTimer.TICK));
		assertEquals(1, engine.selfTimer().count(SelfTimer.STEP));
	}

	// ---------------------------------------------------------------- host seconds

	@Test
	public void aSampleFillsTheSecondBeforeIt()
	{
		engine.host(at(990), RTT_40_MS, 1000, 0, NoData.NONE);
		assertEquals("taken in second 0: there is no second before it", -1, session.seconds.hostHead());

		host(1010, RTT_40_MS, 5000);
		assertEquals("the first sample, taken in second 1, fills second 0", 0, session.seconds.hostHead());
		host(2010, RTT_40_MS, 5400);
		host(3010, RTT_40_MS, 6000);
		host(4010, RTT_40_MS, 6900);
		assertEquals(3, session.seconds.hostHead());
		host(5010, 47_000L, 8100);
		assertEquals("a sample at 5.010 s writes second 4", 4, session.seconds.hostHead());
		assertEquals(1200, session.seconds.sentUnits(4));
		assertEquals(47, session.seconds.rttMs(4));
		assertEquals(0, session.seconds.rttAgeS(4));
		assertEquals(NoData.NONE, session.seconds.conn(4));
		assertEquals(900, session.seconds.sentUnits(3));
		assertEquals(40, session.seconds.rttMs(3));
	}

	@Test
	public void aSecondSampleInOneSecondWritesNothing()
	{
		host(4010, RTT_40_MS, 10_000);
		host(5010, RTT_40_MS, 11_000);
		assertEquals(4, session.seconds.hostHead());
		assertEquals(1000, session.seconds.sentUnits(4));

		engine.host(at(5700), 99_000L, 11_400, 0, NoData.NONE);
		assertEquals("one host second", 4, session.seconds.hostHead());
		assertEquals(1000, session.seconds.sentUnits(4));
		assertEquals(40, session.seconds.rttMs(4));

		host(6010, RTT_40_MS, 12_000);
		assertEquals(5, session.seconds.hostHead());
		assertEquals("400 and 600: what both samples saw go out, none lost", 1000, session.seconds.sentUnits(5));
		assertEquals(40, session.seconds.rttMs(5));
	}

	@Test
	public void aShortHoleTakesTheReadingsOfTheSampleThatClosesIt()
	{
		host(4010, RTT_40_MS, 4000);
		engine.host(at(5010), RTT_40_MS, 5000, 0, NoData.NONE);
		assertEquals(4, session.seconds.hostHead());
		// the sample of 6.010 s was skipped
		engine.host(at(7010), 45_000L, 6200, 30, NoData.NONE);
		assertEquals(6, session.seconds.hostHead());

		final SecondRing r = session.seconds;
		for (long sec = 5; sec <= 6; sec++)
		{
			assertEquals("second " + sec, 45, r.rttMs(sec));
			assertEquals(0, r.rttAgeS(sec));
			assertEquals(NoData.NONE, r.conn(sec));
		}
		assertEquals("the filled second sent nothing", 0, r.sentUnits(5));
		assertEquals(0, r.resentUnits(5));
		assertEquals("the whole difference stays in the sample's own second", 1200, r.sentUnits(6));
		assertEquals(30, r.resentUnits(6));
		assertEquals("the second before the hole is as it was", 40, r.rttMs(4));

		// a hole of exactly HOST_FILL_S seconds is still filled so
		final long next = 7 + Thresholds.HOST_FILL_S + 1;
		engine.host(at(next * 1000 + 10), 52_000L, 9000, 30, NoData.NONE);
		assertEquals(next - 1, r.hostHead());
		for (long sec = 7; sec < next; sec++)
		{
			assertEquals("second " + sec, 52, r.rttMs(sec));
			assertEquals(NoData.NONE, r.conn(sec));
		}
		assertEquals(0, r.sentUnits(7));
		assertEquals(2800, r.sentUnits(next - 1));
	}

	@Test
	public void aLongHoleIsFillers()
	{
		host(4010, RTT_40_MS, 4000);
		host(5010, RTT_40_MS, 5000);
		final SecondRing r = session.seconds;
		assertEquals(4, r.hostHead());

		final long hole = Thresholds.HOST_FILL_S + 1;
		final long sampleSec = 5 + hole;
		engine.host(at((sampleSec + 1) * 1000 + 10), 45_000L, 9000, 0, NoData.NONE);
		assertEquals(sampleSec, r.hostHead());
		for (long sec = 5; sec < sampleSec; sec++)
		{
			assertEquals("second " + sec, NoData.STALE, r.conn(sec));
			assertEquals(-1, r.rttMs(sec));
			assertEquals(-1, r.rttAgeS(sec));
			assertEquals(0, r.sentUnits(sec));
			assertEquals(0, r.resentUnits(sec));
		}
		assertEquals("the sample's own second holds its readings", 45, r.rttMs(sampleSec));
		assertEquals(4000, r.sentUnits(sampleSec));
		assertEquals(NoData.NONE, r.conn(sampleSec));

		// the seconds before a first sample that comes late are a hole by the same rule
		setUp();
		host(9010, RTT_40_MS, 4000);
		assertEquals(8, session.seconds.hostHead());
		for (long sec = 0; sec < 8; sec++)
		{
			assertEquals(NoData.STALE, session.seconds.conn(sec));
		}
	}

	// ---------------------------------------------------------------- the connection

	@Test
	public void aHopResetsTheLossWindowAndAMapLoadDoesNot()
	{
		engine.gameState(at(0), State.LOGGED_IN);
		host(1010, RTT_40_MS, 50_000);
		host(2010, RTT_40_MS, 51_000);
		assertEquals(40, session.seconds.rttMs(1));

		engine.gameState(at(2100), State.LOADING);
		engine.gameState(at(2400), State.LOGGED_IN);
		host(3010, RTT_40_MS, 52_000);
		assertEquals("a map load keeps its socket", 1000, session.seconds.sentUnits(2));
		assertEquals(NoData.NONE, session.seconds.conn(2));

		engine.gameState(at(3100), State.HOPPING);
		engine.gameState(at(3600), State.LOGGED_IN);
		host(4010, RTT_40_MS, 90_000);
		assertEquals("the new world's socket starts a new base", 0, session.seconds.sentUnits(3));
		assertEquals(NoData.STALE, session.seconds.conn(3));
		host(5010, RTT_40_MS, 90_700);
		assertEquals(700, session.seconds.sentUnits(4));

		engine.world(at(5100), 302);
		host(6010, RTT_40_MS, 95_000);
		assertEquals("a world change resets it too", 0, session.seconds.sentUnits(5));
	}

	@Test
	public void aLostConnectionKeepsItsSecondAndTheReconnectStartsANewBase()
	{
		engine.gameState(at(0), State.LOGGED_IN);
		for (long k = 1; k <= 4; k++)
		{
			host(k * 1000 + 10, RTT_40_MS, 900 * k);
		}
		assertEquals(900, session.seconds.sentUnits(3));

		// the connection is lost 300 ms into second 4, and the old socket can still be read: what went out in that
		// second, and what was sent again, is what the re-send trigger and D1 read
		engine.gameState(at(4300), State.CONNECTION_LOST);
		host(5010, RTT_40_MS, 900 * 5, 300);
		final SecondRing r = session.seconds;
		assertEquals("the second the connection dropped in keeps what it sent", 900, r.sentUnits(4));
		assertEquals(300, r.resentUnits(4));
		assertEquals(40, r.rttMs(4));
		assertEquals(NoData.NONE, r.conn(4));
		host(6010, RTT_40_MS, 900 * 5, 300);
		assertEquals("nothing went out: still the old base", 0, r.sentUnits(5));
		assertEquals(0, r.resentUnits(5));

		// back with no LOGGING_IN between, on a new socket whose counters, sent AND re-sent, happen to be higher than
		// the old ones: no counter falls, so only the reset makes its first sample a new base
		engine.gameState(at(6500), State.LOGGED_IN);
		host(7010, RTT_40_MS, 60_000, 400);
		assertEquals("the reconnect's first sample is the new base", 0, r.sentUnits(6));
		assertEquals(0, r.resentUnits(6));
		assertEquals(NoData.STALE, r.conn(6));
		host(8010, RTT_40_MS, 60_900, 400);
		assertEquals(900, r.sentUnits(7));
		assertEquals(0, r.resentUnits(7));
		assertEquals(NoData.NONE, r.conn(7));

		// lost again, and back through LOGGING_IN: the login is the reset, and the end of the login is no second one
		engine.gameState(at(8300), State.CONNECTION_LOST);
		host(9010, RTT_40_MS, 61_800, 400);
		assertEquals(900, r.sentUnits(8));
		engine.gameState(at(9200), State.LOGGING_IN);
		host(10_010, RTT_40_MS, 90_000, 500);
		assertEquals("the login's first sample is the new base", 0, r.sentUnits(9));
		engine.gameState(at(10_100), State.LOGGED_IN);
		host(11_010, RTT_40_MS, 90_800, 500);
		assertEquals("the end of the login is no second reset", 800, r.sentUnits(10));
	}

	@Test
	public void aLoginStartsANewBaseAndTheLoginScreenAloneDoesNot()
	{
		engine.gameState(at(0), State.LOGGED_IN);
		host(1010, RTT_40_MS, 10_000);
		host(2010, RTT_40_MS, 10_900);
		engine.gameState(at(2300), State.LOGIN_SCREEN);
		host(3010, RTT_40_MS, 11_800);
		assertEquals("the login screen is no reset", 900, session.seconds.sentUnits(2));

		engine.gameState(at(3200), State.LOGGING_IN);
		host(4010, RTT_40_MS, 50_000);
		assertEquals("a login is", 0, session.seconds.sentUnits(3));
		assertEquals(NoData.STALE, session.seconds.conn(3));
		engine.gameState(at(4100), State.LOGGED_IN);
		host(5010, RTT_40_MS, 50_700);
		assertEquals("and the end of the login is not a second one", 700, session.seconds.sentUnits(4));
	}

	@Test
	public void aTickCarriesTheNewestRtt()
	{
		engine.gameState(at(0), State.LOGGED_IN);
		engine.tick(at(600), 30);
		assertEquals("no host sample yet", -1, session.ticks.rttMs(0));
		host(1010, RTT_40_MS, 1000);
		host(2010, 63_000L, 2000);
		engine.tick(at(2400), 120);
		assertEquals(63, session.ticks.rttMs(1));
	}

	@Test
	public void theWorldAndTheSceneReachTheSecond()
	{
		engine.gameState(at(0), State.LOGGED_IN);
		engine.world(at(0), 416);
		engine.scene(12, 34, 12850);
		engine.focus(false);
		for (long ms = 0; ms <= 2000; ms += 20)
		{
			engine.frame(at(ms), (int) (ms / 20));
		}
		assertEquals(416, session.seconds.world(1));
		assertEquals(12, session.seconds.players(1));
		assertEquals(34, session.seconds.npcs(1));
		assertEquals(12850, session.seconds.region(1));
		assertEquals(State.LOGGED_IN, session.seconds.state(1));
		assertFalse(Flags.has(session.seconds.flags(1), Flags.FOCUSED));
	}

	// ---------------------------------------------------------------- the fakes

	/** What a test wants the detector to tell its listener during one advance. */
	private interface Script
	{
		void run(DetectorListener out);
	}

	/**
	 * A detector that does what the test says: {@code toOpen} is opened in the next advance and is then what
	 * {@code open()} answers; {@code toClose} is closed in the next advance, after which nothing is open.
	 */
	private static final class FakeDetector implements Detector
	{
		LagEvent toOpen, toClose, open;
		Script script;
		int advances;
		long lastThroughSec = Long.MIN_VALUE;
		Session lastSession;
		SettingsView lastSettings;

		@Override
		public void advance(Session s, long throughSec, SettingsView settings, DetectorListener out)
		{
			advances++;
			lastSession = s;
			lastThroughSec = throughSec;
			lastSettings = settings;
			if (script != null)
			{
				script.run(out);
				script = null;
			}
			if (toOpen != null)
			{
				out.opened(toOpen);
				open = toOpen;
				toOpen = null;
			}
			if (toClose != null)
			{
				out.closed(toClose);
				open = null;
				toClose = null;
			}
		}

		@Override
		public LagEvent open()
		{
			return open;
		}

		@Override
		public boolean quiet(long sec)
		{
			return true;
		}
	}

	/** A judge that answers {@code answer} from {@code current}, the same object until the test changes it. */
	private static final class FakeJudge implements Judge
	{
		volatile Verdict answer = new Verdict(Cause.WARMING_UP, Confidence.CANT_TELL, Level.NO_DATA,
			Answer.HEAD_NOT_LOGGED_IN, "", "", "", 0, 0, 0, -1, null, null);
		volatile CountDownLatch entered, release;
		int currentCalls;
		long lastNowSec = Long.MIN_VALUE, lastWallMs;
		Session lastSession;
		SettingsView lastSettings, lastEventSettings;
		LagEvent lastEvent;
		Verdict lastEventVerdict;

		@Override
		public Verdict judgeEvent(Session s, LagEvent closed, SettingsView settings)
		{
			lastEvent = closed;
			lastEventSettings = settings;
			lastEventVerdict = verdict(Cause.CLIENT_BUSY, closed.id);
			return lastEventVerdict;
		}

		@Override
		public Verdict current(Session s, long nowSec, long wallMs, SettingsView settings)
		{
			currentCalls++;
			lastSession = s;
			lastNowSec = nowSec;
			lastWallMs = wallMs;
			lastSettings = settings;
			final CountDownLatch in = entered;
			final CountDownLatch out = release;
			if (in != null && out != null)
			{
				in.countDown();
				try
				{
					out.await(WAIT_S, TimeUnit.SECONDS);
				}
				catch (InterruptedException e)
				{
					Thread.currentThread().interrupt();
				}
			}
			return answer;
		}
	}

	private static final class FakeSnapshots implements SnapshotSource
	{
		final PanelSnapshot answer = new PanelSnapshot(0, ZoneOffset.UTC, 0, null, new Tile[0], 10, 0, 0,
			new Strip[0], null, null, new int[Group.values().length], 0, 0, null, "");
		volatile int builds;
		long lastNowSec = Long.MIN_VALUE, lastWallMs;
		int lastRange;
		Session lastSession;
		Verdict lastShown;
		SettingsView lastSettings;
		String lastFooter;

		@Override
		public PanelSnapshot build(Session s, Verdict shown, int rangeMinutes, long nowSec, long wallMs,
			SettingsView settings, String footer)
		{
			builds++;
			lastSession = s;
			lastShown = shown;
			lastRange = rangeMinutes;
			lastNowSec = nowSec;
			lastWallMs = wallMs;
			lastSettings = settings;
			lastFooter = footer;
			return answer;
		}
	}
}
