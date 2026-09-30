package com.whylag.core;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.time.ZoneOffset;
import java.util.concurrent.CountDownLatch;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * The session (contract 3.5): its clocks, session ms as a {@code long} that does not wrap after 24.8 days, what it
 * holds, each ring one slot larger than its constant, the ranges of its usuals, and where the warm-up counts from -
 * {@link Session#loggedInSinceSec()}, {@link Session#warm} and {@link Session#warmupLeftS}, the one rule for the
 * verdict and the snapshot. The login second is its one mutable field, a volatile long. And "now" in ring terms, the
 * one rule for the verdict and the snapshot: {@link Session#lastSec} (the newest complete second) and
 * {@link Session#framesStopped} (no frame for certainly longer than {@link Thresholds#NO_FRAMES_MS}).
 */
public class SessionTest
{
	private static final long START = 5_000_000_000_000L;
	private static final long WALL = 1_790_000_000_000L;
	private static final long NANOS_PER_DAY = 86_400L * 1_000_000_000L;

	@Test
	public void clocks()
	{
		final Session s = session();
		assertEquals(0, s.secOf(START));
		assertEquals(0, s.secOf(START + 999_999_999L));
		assertEquals(1, s.secOf(START + 1_000_000_000L));
		assertEquals("floor, not truncation", -1, s.secOf(START - 1));
		assertEquals(WALL, s.wallMsOf(0));
		assertEquals(WALL + 61_000, s.wallMsOf(61));
		assertEquals(0L, s.msOf(START));
		assertEquals(1_234L, s.msOf(START + 1_234_999_999L));
		assertEquals("floor, not truncation", -1L, s.msOf(START - 1));
		assertEquals(61_000L, s.msOfSec(61));
		assertEquals(s.secOf(START + 7_500_000_000L), Math.floorDiv(s.msOf(START + 7_500_000_000L), 1000L));
	}

	@Test
	public void holdsWhatItWasGiven()
	{
		final Session s = new Session(START, WALL, Os.MAC, ZoneOffset.ofHours(2));
		assertEquals(START, s.startNanos);
		assertEquals(WALL, s.startWallMs);
		assertEquals(Os.MAC, s.os);
		assertEquals(ZoneOffset.ofHours(2), s.zone);
		s.loggedInSince(42);
		assertEquals(42, s.loggedInSinceSec());
		s.loggedInSince(-60);
		assertEquals("a login before second 0, as a steady trace has", -60, s.loggedInSinceSec());
	}

	/** 30 days of nanos are 2,592,000,000 ms: past {@code Integer.MAX_VALUE}, where an int would have wrapped. */
	@Test
	public void msOfDoesNotWrapAfterTwentyFiveDays() throws NoSuchMethodException
	{
		assertEquals("session ms are a long", long.class, Session.class.getMethod("msOf", long.class).getReturnType());
		final Session s = session();
		final long thirtyDays = 30 * NANOS_PER_DAY;
		assertEquals(2_592_000_000L, s.msOf(START + thirtyDays));
		assertTrue(s.msOf(START + thirtyDays) > Integer.MAX_VALUE);
		assertEquals(2_592_000L, s.secOf(START + thirtyDays));
		assertEquals(s.msOfSec(s.secOf(START + thirtyDays)), s.msOf(START + thirtyDays));
		// System.nanoTime() may start anywhere, even near the top of a long, and run over it.
		final Session nearTheTop = new Session(Long.MAX_VALUE - 1_000, WALL, Os.WINDOWS, ZoneOffset.UTC);
		assertEquals(2_592_000_000L, nearTheTop.msOf(Long.MAX_VALUE - 1_000 + thirtyDays));
	}

	@Test
	public void eachRingIsOneSlotLargerThanItsConstant()
	{
		final Session s = session();
		assertEquals(Thresholds.SECONDS + 1, s.seconds.capacity());
		assertEquals(-1, s.seconds.head());
		assertEquals(-1, s.ticks.head());
		assertEquals(0, s.events.size());
		for (int i = 0; i <= Thresholds.EVENTS; i++)
		{
			s.events.add(new LagEvent(i, i, i, 0, 0, Trigger.FRAME_GAP, 0, 0, 0, 0, -1, -1, -1, -1, -1, -1, -1, -1,
				0, 0, true, false, null));
		}
		assertEquals(Thresholds.EVENTS, s.events.size());
	}

	@Test
	public void theUsualsKeepTheirRanges()
	{
		final Session s = session();
		for (int i = 0; i < Thresholds.USUAL_MIN_SAMPLES; i++)
		{
			s.rttUsual.add(5000);
			s.rttSession.add(5000);
			s.fpsUsual.add(5000);
		}
		assertEquals(2000, s.rttUsual.median());
		assertEquals(2000, s.rttSession.median());
		assertEquals(1000, s.fpsUsual.median());
	}

	@Test
	public void aNewSessionHasSeenNoLogin()
	{
		final Session s = session();
		assertEquals(Long.MAX_VALUE, Session.NEVER);
		assertEquals(Session.NEVER, s.loggedInSinceSec());
		assertFalse(s.warm(0));
		assertFalse(s.warm(1_000_000));
		assertFalse(s.warm(Long.MAX_VALUE));
		assertEquals(Thresholds.WARMUP_S, s.warmupLeftS(0));
		assertEquals(Thresholds.WARMUP_S, s.warmupLeftS(1_000_000));
	}

	/** No warm-up since the first live look (WARMUP_S 0, 2026-09-29): green from the login second on. */
	@Test
	public void warmFromTheLoginSecond()
	{
		final Session s = session();
		s.loggedInSince(100);
		assertTrue("the login second itself", s.warm(100));
		assertTrue("59 s after the login", s.warm(159));
		assertTrue("60 s after the login", s.warm(160));
		assertTrue(s.warm(100_000));
		assertFalse("a second before the login", s.warm(99));
		s.loggedInSince(-Thresholds.WARMUP_S);
		assertTrue("a steady trace's login: warm from second 0", s.warm(0));
		assertFalse(s.warm(-1));
	}

	/** No warm-up since the first live look: nothing is left from the login second on; 1 before it (not warm). */
	@Test
	public void warmupLeftIsNoneFromTheLogin()
	{
		final Session s = session();
		s.loggedInSince(100);
		assertEquals(0, s.warmupLeftS(100));
		assertEquals(0, s.warmupLeftS(101));
		assertEquals(0, s.warmupLeftS(120));
		assertEquals(0, s.warmupLeftS(159));
		assertEquals(0, s.warmupLeftS(160));
		assertEquals(0, s.warmupLeftS(100_000));
		assertEquals("held at 1 before the login", 1, s.warmupLeftS(40));
		assertFalse(s.warm(40));
		for (long now = 40; now < 160; now++)
		{
			assertEquals(!s.warm(now), s.warmupLeftS(now) > 0);
		}
	}

	/**
	 * The login second is written on the client thread and read on the sampler thread: it must be the one volatile
	 * field of the session. First the modifier itself, which no timing can hide; then a reader spinning on the
	 * getter, compiled before the write, must see the one write. A plain field would be read once and held.
	 */
	@Test(timeout = 60_000)
	public void theLoginIsOneVolatileField() throws Exception
	{
		final Field login = Session.class.getDeclaredField("loggedInSinceSec");
		final int m = login.getModifiers();
		assertTrue("Session.loggedInSinceSec must be volatile (contract 3.5)", Modifier.isVolatile(m));
		assertTrue(Modifier.isPrivate(m));
		assertEquals(long.class, login.getType());
		for (Field f : Session.class.getDeclaredFields())
		{
			if (!Modifier.isStatic(f.getModifiers()) && !f.isSynthetic() && !f.equals(login))
			{
				assertTrue("Session." + f.getName() + " must be final: the login is the one mutable field",
					Modifier.isFinal(f.getModifiers()));
			}
		}

		final Session s = session();
		final CountDownLatch spinning = new CountDownLatch(1);
		final Thread reader = new Thread(() ->
		{
			spinning.countDown();
			while (s.loggedInSinceSec() == Session.NEVER)
			{
				// spin: nothing else in the loop, so only the field's own ordering can end it
			}
		}, "login-reader");
		reader.setDaemon(true);
		reader.start();
		spinning.await();
		// Long enough for the JIT to compile the spinning loop, so a plain field would be hoisted out of it.
		Thread.sleep(300);
		s.loggedInSince(42);
		reader.join(10_000);
		assertFalse("the reader never saw the login", reader.isAlive());
	}

	/**
	 * {@code lastSec(nowSec)} is {@code min(head, nowSec - 1)}: live it is the head (the clock runs ahead of the
	 * rings), a replay at an earlier {@code nowSec} sees nothing after {@code nowSec - 1}, and -1 means none yet.
	 */
	@Test
	public void lastSecIsTheNewestCompleteSecond()
	{
		final Session s = session();
		assertEquals("nothing written", -1, s.lastSec(0));
		assertEquals(-1, s.lastSec(100));
		frames(s, 0, 99);
		assertEquals("no host column yet: no complete second", -1, s.lastSec(100));
		host(s, 0, 99);
		assertEquals("now at the end of 100 seconds", 99, s.lastSec(100));
		assertEquals("live: the clock runs ahead of the rings", 99, s.lastSec(5_000));
		assertEquals("a replay: nothing after nowSec - 1", 49, s.lastSec(50));
		assertEquals("second 0 still running", -1, s.lastSec(0));
		assertEquals(0, s.lastSec(1));
		assertEquals(-1, s.lastSec(-3));
		host(s, 100, 104);
		assertEquals("the slower writer's head", 99, s.lastSec(200));
	}

	/**
	 * Frame head F: the newest frame came in [F + 1, F + 2) s. At the starting 2,000 ms, "certainly longer" needs the
	 * head 4 seconds behind the clock; the host columns never count, and a replay of the past is never stopped.
	 */
	@Test
	public void framesStoppedOnlyWhenCertainlyLongerThanNoFramesMs()
	{
		final Session s = session();
		assertFalse("no frame yet, and not for long", s.framesStopped(0));
		assertFalse(s.framesStopped(2));
		assertTrue("no frame in the first 3 s", s.framesStopped(3));
		frames(s, 0, 99);
		assertFalse("frames running: the head is one second behind", s.framesStopped(100));
		assertFalse(s.framesStopped(101));
		assertFalse("at most 2 s: not certainly longer", s.framesStopped(102));
		assertTrue("more than 2 s, certainly", s.framesStopped(103));
		assertTrue(s.framesStopped(100_000));
		assertFalse("a replay of the past", s.framesStopped(50));
		host(s, 0, 300);
		assertTrue("the host columns run on without the client", s.framesStopped(103));
		assertFalse(s.framesStopped(102));
		assertEquals("2 + ceil(NO_FRAMES_MS / 1000) seconds behind", 4, 2 + (Thresholds.NO_FRAMES_MS + 999) / 1000);
	}

	private static void frames(Session s, long fromSec, long toSec)
	{
		final FrameSecond f = new FrameSecond();
		f.frames = 50;
		f.state = State.LOGGED_IN;
		for (long sec = fromSec; sec <= toSec; sec++)
		{
			s.seconds.putFrame(sec, f);
		}
	}

	private static void host(Session s, long fromSec, long toSec)
	{
		final HostSecond h = new HostSecond();
		for (long sec = fromSec; sec <= toSec; sec++)
		{
			s.seconds.putHost(sec, h);
		}
	}

	private static Session session()
	{
		return new Session(START, WALL, Os.WINDOWS, ZoneOffset.UTC);
	}
}
