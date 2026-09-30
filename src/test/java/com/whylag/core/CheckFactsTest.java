package com.whylag.core;

import java.time.ZoneId;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * What {@link CheckFacts.Builder#session} reads from the rings (1.0.1, lot B): a steady minute, a logged-out
 * session, frames that stopped, ticks that stopped or slowed, each state of the ping, and a session that has nothing
 * yet - each read from a {@link Trace}, the way the sampler thread reads the session. The settings' two facts are
 * read from a {@link SettingsView}.
 */
public class CheckFactsTest
{
	private static final long NANOS_PER_SECOND = 1_000_000_000L;

	/** The facts of the session {@code seconds} seconds after its start. */
	private static CheckFacts at(Session s, int seconds)
	{
		return new CheckFacts.Builder().session(s, s.startNanos + seconds * NANOS_PER_SECOND).build();
	}

	/** A steady minute read at its end: logged in to 416, 50 fps, a tick every 600 ms, a ping of 40 ms. */
	@Test
	public void aSteadyMinute()
	{
		final CheckFacts f = at(Trace.steady(60).build(), 60);

		assertTrue(f.loggedIn);
		assertEquals(416, f.world);
		assertEquals("two complete seconds of 50", 100, f.framesLast2s);
		assertEquals(50, f.fps);
		assertEquals("the last of the 99 ticks came at 59.4 s", 600, f.lastTickAgoMs);
		assertEquals("ticks 84 to 99 lie in the last 10 s", 16, f.ticksLast10s);
		assertEquals(600, f.meanGapMs);
		assertEquals(CheckFacts.Ping.READING, f.pingState);
		assertEquals(40, f.rttMs);
		assertEquals(0, f.pingAgeS);
		assertEquals("logged in at second 0", 60, f.loggedInS);
		assertTrue("the steady trace passes the checks that read the rings", Checks.run(f).get(0).pass);
	}

	/** Seconds before the login are at the login screen: nobody is logged in, and no time has counted. */
	@Test
	public void theLoginScreenIsNotLoggedIn()
	{
		final CheckFacts out = at(Trace.steady(60).loginAt(40).build(), 30);
		assertFalse(out.loggedIn);
		assertEquals(0, out.world);
		assertEquals(0, out.loggedInS);

		final CheckFacts in = at(Trace.steady(60).loginAt(40).build(), 50);
		assertTrue(in.loggedIn);
		assertEquals(10, in.loggedInS);
	}

	/** A client that stopped drawing: the newest seconds the ring holds are old, so the facts read 0 frames. */
	@Test
	public void framesThatStoppedReadZero()
	{
		final Session s = Trace.steady(60).build();
		final CheckFacts stopped = at(s, 70);
		assertEquals("no frame for certainly more than 2 s", 0, stopped.framesLast2s);
		assertEquals(0, stopped.fps);
		assertEquals("the ticks are read from the clock, so they age too", 10600, stopped.lastTickAgoMs);
		assertEquals(0, stopped.ticksLast10s);

		final CheckFacts gap = at(Trace.steady(60).fps(58, 59, 0).build(), 60);
		assertEquals("seconds with no frame, as the sampler fills them", 0, gap.framesLast2s);
		assertEquals(0, gap.fps);
	}

	/** Ticks that slowed, or stopped, inside the last 10 s. */
	@Test
	public void ticksThatSlowedOrStopped()
	{
		final CheckFacts slow = at(Trace.steady(60).ticksEvery(50, 59, 1000).build(), 60);
		assertTrue("a mean gap near 1,000 ms: " + slow.meanGapMs, slow.meanGapMs > 900 && slow.meanGapMs < 1100);
		assertTrue(slow.ticksLast10s < 12);

		final CheckFacts none = at(Trace.steady(60).noTicks(50, 59).build(), 60);
		assertEquals(0, none.ticksLast10s);
		assertEquals("nothing to average", -1, none.meanGapMs);
		assertTrue("the newest tick is 10 s old or more: " + none.lastTickAgoMs, none.lastTickAgoMs >= 10000);
	}

	/** Each state the ring can hold for the ping, and the age of the reading. */
	@Test
	public void eachStateOfThePing()
	{
		assertEquals(CheckFacts.Ping.UNSUPPORTED, at(Trace.steady(60).rttNoData(0, 59, NoData.UNSUPPORTED).build(), 60)
			.pingState);
		assertEquals(CheckFacts.Ping.ERROR, at(Trace.steady(60).rttNoData(0, 59, NoData.ERROR).build(), 60).pingState);
		assertEquals(CheckFacts.Ping.STALE, at(Trace.steady(60).rttStale(40, 59).build(), 60).pingState);
		assertEquals(CheckFacts.Ping.NONE, at(Trace.steady(60).rttNoData(0, 59, NoData.NOT_CONNECTED).build(), 60)
			.pingState);
		assertEquals(CheckFacts.Ping.NONE, at(Trace.steady(60).rttNoData(0, 59, NoData.NOT_LOGGED_IN).build(), 60)
			.pingState);
		final CheckFacts none = at(Trace.steady(60).rttNoData(0, 59, NoData.UNSUPPORTED).build(), 60);
		assertEquals(-1, none.rttMs);
		assertEquals(-1, none.pingAgeS);
		assertEquals(12, at(Trace.steady(60).rtt(0, 59, 12).build(), 60).rttMs);
	}

	/** A session that has nothing yet has no facts: every reading is "none" and nothing is thrown. */
	@Test
	public void aSessionWithNothingYet()
	{
		final Session fresh = new Session(System.nanoTime(), 1_790_000_000_000L, Os.WINDOWS, ZoneId.of("UTC"));
		final CheckFacts f = at(fresh, 1);

		assertFalse(f.loggedIn);
		assertEquals(0, f.world);
		assertEquals(-1, f.framesLast2s);
		assertEquals(-1, f.fps);
		assertEquals(-1, f.lastTickAgoMs);
		assertEquals(0, f.ticksLast10s);
		assertEquals(-1, f.meanGapMs);
		assertEquals(CheckFacts.Ping.NONE, f.pingState);
		assertEquals(0, f.loggedInS);
		assertEquals(10, Checks.run(f).size());
	}

	/** The facts of a builder nobody filled say "nothing known", with a zone and a card state that are never null. */
	@Test
	public void anEmptyBuilderKnowsNothing()
	{
		final CheckFacts f = new CheckFacts.Builder().build();
		assertFalse(f.loggedIn);
		assertEquals(-1, f.lastStepAgoMs);
		assertEquals(-1, f.stepCostUs);
		assertEquals(Renderer.UNKNOWN, f.renderer);
		assertEquals("UTC", f.zone.getId());
		assertEquals(Answer.MEASURING, f.cardState);
		assertEquals("", f.clientVersion);
		assertEquals(CheckFacts.Ping.NONE, f.pingState);

		final CheckFacts.Builder b = new CheckFacts.Builder();
		b.pingState = null;
		b.renderer = null;
		b.zone = null;
		b.cardState = null;
		b.os = null;
		final CheckFacts n = b.build();
		assertEquals(CheckFacts.Ping.NONE, n.pingState);
		assertEquals(Renderer.UNKNOWN, n.renderer);
		assertEquals("UTC", n.zone.getId());
		assertEquals(Answer.MEASURING, n.cardState);
		assertEquals("", n.os);
	}

	/** The renderer and the cap of a settings view; a V-Sync cap needs the refresh rate. */
	@Test
	public void theSettingsFacts()
	{
		final CheckFacts none = new CheckFacts.Builder().settings(null).build();
		assertEquals(Renderer.UNKNOWN, none.renderer);
		assertFalse(none.capKnown);

		assertFalse("an unread renderer", CheckFacts.capKnown(SettingsView.unknown(Os.WINDOWS)));
		assertTrue("CPU has the client's own cap", CheckFacts.capKnown(view(Renderer.CPU, false, "", 0, 0)));
		assertTrue("an unlocked GPU with V-Sync off and no target: no cap is a known state",
			CheckFacts.capKnown(view(Renderer.GPU, true, "OFF", 0, 0)));
		assertFalse("V-Sync on, refresh rate unread", CheckFacts.capKnown(view(Renderer.GPU, true, "ON", 0, 0)));
		assertFalse("adaptive too, on 117 HD", CheckFacts.capKnown(view(Renderer.HD, true, "ADAPTIVE", 0, 0)));
		assertTrue("V-Sync on with its refresh rate", CheckFacts.capKnown(view(Renderer.GPU, true, "ON", 0, 144)));
		assertTrue("a locked GPU is the client's 50", CheckFacts.capKnown(view(Renderer.GPU, false, "ON", 0, 0)));

		final CheckFacts ok = new CheckFacts.Builder().settings(view(Renderer.GPU, true, "OFF", 144, 0)).build();
		assertEquals(Renderer.GPU, ok.renderer);
		assertTrue(ok.capKnown);
	}

	private static SettingsView view(Renderer renderer, boolean unlockFps, String vsync, int target, int refreshHz)
	{
		return new SettingsView(renderer, false, false, 0, false, 0, unlockFps, vsync, target, 50, "", 0, refreshHz,
			Os.WINDOWS, "1.13.0");
	}
}
