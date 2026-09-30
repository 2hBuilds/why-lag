package com.whylag.core;

import java.time.ZoneOffset;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * {@link TickSampler} (contract section 7, L2): what a tick row holds. Times are session ms. Where frames matter
 * they run on a 20 ms grid, and a tick that falls on a frame's ms arrives BEFORE that frame, as the client posts it.
 */
public class TickSamplerTest
{
	private static final long START = 9_000_000_000_000L;
	private static final long NANOS_PER_MS = 1_000_000L;
	private static final int FRAME_MS = 20;
	private static final int CYCLE_MS = 20;

	private Session session;
	private TickRing ring;
	private FrameSampler frames;
	private TickSampler ticks;

	@Before
	public void setUp()
	{
		session = new Session(START, 1_000_000L, Os.WINDOWS, ZoneOffset.UTC);
		ring = session.ticks;
		final SelfTimer timer = new SelfTimer();
		frames = new FrameSampler(session, timer);
		ticks = new TickSampler(session, frames, timer);
	}

	private static long at(long ms)
	{
		return START + ms * NANOS_PER_MS;
	}

	private void state(long ms, int code)
	{
		frames.state(at(ms), code);
		ticks.state(at(ms), code);
	}

	private void tick(long ms)
	{
		ticks.tick(at(ms), (int) (ms / CYCLE_MS), 40);
	}

	private void frame(long ms)
	{
		frames.frame(at(ms), (int) (ms / CYCLE_MS));
	}

	/**
	 * Frames every 20 ms from {@code fromMs} to {@code toMs}, both included, and a tick every 600 ms from
	 * {@code firstTickMs} on, each just before the frame of its ms.
	 */
	private void run(long fromMs, long toMs, long firstTickMs)
	{
		long nextTick = firstTickMs;
		for (long t = fromMs; t <= toMs; t += FRAME_MS)
		{
			while (nextTick <= t)
			{
				tick(nextTick);
				nextTick += Thresholds.TICK_MS;
			}
			frame(t);
		}
	}

	private boolean masked(long seq)
	{
		return Flags.has(ring.flags(seq), Flags.LOGIN_MASK);
	}

	@Test
	public void gapAndCycleJump()
	{
		state(0, State.LOGGED_IN);
		ticks.tick(at(600), 30, 41);
		ticks.tick(at(1200), 60, 42);
		ticks.tick(at(1850), 93, -1);
		assertEquals(2, ring.head());

		assertEquals(600L, ring.atMs(0));
		assertEquals(Thresholds.TICK_MS, ring.gapMs(0));
		assertEquals(-1, ring.cycleJump(0));
		assertEquals(41, ring.rttMs(0));

		assertEquals(1200L, ring.atMs(1));
		assertEquals(600, ring.gapMs(1));
		assertEquals(30, ring.cycleJump(1));
		assertEquals(42, ring.rttMs(1));

		assertEquals(1850L, ring.atMs(2));
		assertEquals(650, ring.gapMs(2));
		assertEquals(33, ring.cycleJump(2));
		assertEquals(-1, ring.rttMs(2));
		assertTrue(ring.late(2));
		assertEquals(Flags.FOCUSED, ring.flags(2));
	}

	@Test
	public void atMsIsALongAndAGapIsHeldInAnInt()
	{
		final long month = 40L * 86_400_000L;
		ticks.tick(at(600), 30, 40);
		ticks.tick(at(month), 60, 40);
		ticks.tick(at(month + 600), 90, 40);
		assertEquals("40 days in ms do not fit an int", month, ring.atMs(1));
		assertEquals(Integer.MAX_VALUE, ring.gapMs(1));
		assertEquals(600, ring.gapMs(2));
		assertEquals(month + 600, ring.atMs(2));
	}

	@Test
	public void theSessionsFirstTickIsNeverOff()
	{
		// switched on while logged in: no login mask, and the first tick comes 7 s after the session's start
		state(10, State.LOGGED_IN);
		tick(7000);
		tick(7640);
		assertEquals(7000L, ring.atMs(0));
		assertEquals("not measured from the session's start", Thresholds.TICK_MS, ring.gapMs(0));
		assertEquals(-1, ring.cycleJump(0));
		assertEquals(0, ring.corrected(0));
		assertFalse(masked(0));

		assertEquals("the true difference", 640, ring.gapMs(1));
		assertEquals(32, ring.cycleJump(1));
		assertEquals(40, ring.corrected(1));
	}

	@Test
	public void frameIntervalTravelsWithTheTick()
	{
		state(0, State.LOGGED_IN);
		run(0, 1400, 600);
		frame(1480);
		run(1500, 3000, 1800);
		assertEquals(4, ring.head());
		assertEquals("600", FRAME_MS, ring.frameMs(0));
		assertEquals("1200", FRAME_MS, ring.frameMs(1));
		assertEquals("1800: the 80 ms frame lies in its gap", 80, ring.frameMs(2));
		assertEquals("2400: it does not travel on", FRAME_MS, ring.frameMs(3));
		assertEquals(FRAME_MS, ring.frameMs(4));
	}

	@Test
	public void theOpenFrameIsChargedToTheLateTick()
	{
		state(0, State.LOGGED_IN);
		run(0, 1780, 600);
		// the client stalls for 300 ms: no frame and no tick until 2080. The tick due at 1800 is handled first ...
		tick(2080);
		// ... and then the frame that held the stall is drawn
		frame(2085);
		for (long t = 2100; t <= 2380; t += FRAME_MS)
		{
			frame(t);
		}
		// the next tick was on time at the server, so it follows only 320 ms later
		tick(2400);
		run(2400, 3600, 3000);

		assertEquals(2080L, ring.atMs(2));
		assertEquals(880, ring.gapMs(2));
		assertEquals("the frame still open when the late tick arrived", 300, ring.frameMs(2));
		assertTrue(ring.corrected(2) < Thresholds.TICK_OFF_MS);
		assertEquals(0, ring.corrected(2));

		assertEquals(320, ring.gapMs(3));
		assertTrue(ring.early(3));
		assertEquals("the stall frame itself, once: not the open and the closed reading added up", 305,
			ring.frameMs(3));
		assertTrue(ring.corrected(3) < Thresholds.TICK_OFF_MS);

		assertEquals(600, ring.gapMs(4));
		assertEquals("the stall is spent: the tick after the catch-up carries an ordinary frame", FRAME_MS,
			ring.frameMs(4));
		assertEquals(0, ring.corrected(4));
	}

	@Test
	public void withoutAFrameYetATickCarriesNoFrameTime()
	{
		state(0, State.LOGGED_IN);
		tick(600);
		tick(1200);
		assertEquals(0, ring.frameMs(0));
		assertEquals(0, ring.frameMs(1));
	}

	@Test
	public void loginMaskCoversFifteenTicks()
	{
		state(0, State.LOGIN_SCREEN);
		run(0, 980, Long.MAX_VALUE);
		state(1000, State.LOGGING_IN);
		run(1000, 1980, Long.MAX_VALUE);
		state(2000, State.LOADING);
		run(2000, 2480, Long.MAX_VALUE);
		state(2500, State.LOGGED_IN);
		// ticks at 3000, 3600 ... : the fifteenth arrives at 11,400, the sixteenth at 12,000, the last at 15,000
		run(2500, 15000, 3000);

		assertEquals(20, ring.head());
		for (long seq = 0; seq < Thresholds.LOGIN_MASK_TICKS; seq++)
		{
			assertTrue("tick " + seq, masked(seq));
		}
		for (long seq = Thresholds.LOGIN_MASK_TICKS; seq <= ring.head(); seq++)
		{
			assertFalse("tick " + seq, masked(seq));
		}
		assertEquals(11_400L, ring.atMs(Thresholds.LOGIN_MASK_TICKS - 1));

		final SecondRing seconds = session.seconds;
		assertEquals(14, seconds.frameHead());
		for (long sec = 0; sec <= 2; sec++)
		{
			assertFalse("second " + sec, Flags.has(seconds.flags(sec), Flags.LOGIN_MASK));
		}
		for (long sec = 3; sec <= 11; sec++)
		{
			assertTrue("second " + sec, Flags.has(seconds.flags(sec), Flags.LOGIN_MASK));
		}
		for (long sec = 12; sec <= 14; sec++)
		{
			assertFalse("second " + sec, Flags.has(seconds.flags(sec), Flags.LOGIN_MASK));
		}
	}

	@Test
	public void aTickInAMaskedSecondCarriesItsSecondsBit()
	{
		state(0, State.HOPPING);
		state(500, State.LOGGED_IN);
		// ticks at 2600, 3200 ... : the fifteenth arrives at 11,000 and the sixteenth at 11,600, in the same second
		run(500, 14_000, 2600);
		final long last = Thresholds.LOGIN_MASK_TICKS - 1;
		assertEquals(11_000L, ring.atMs(last));
		assertTrue(masked(last));
		assertEquals(11_600L, ring.atMs(last + 1));
		assertTrue("it carries the flags of its second as they stand, as a trace's tick does", masked(last + 1));
		assertEquals(12_200L, ring.atMs(last + 2));
		assertFalse(masked(last + 2));
		assertTrue(Flags.has(session.seconds.flags(11), Flags.LOGIN_MASK));
		assertFalse(Flags.has(session.seconds.flags(12), Flags.LOGIN_MASK));
	}

	@Test
	public void aSecondWithNoMaskedTickIsNotMasked()
	{
		state(0, State.HOPPING);
		state(900, State.LOGGED_IN);
		for (long t = 0; t <= 8000; t += FRAME_MS)
		{
			// ticks 2,000 ms apart: the masked ones arrive in seconds 1, 3 and 5 only
			if (t == 1000 || t == 3000 || t == 5000)
			{
				tick(t);
			}
			frame(t);
		}
		final SecondRing seconds = session.seconds;
		assertTrue(masked(0));
		assertTrue(masked(2));
		for (long sec = 1; sec <= 5; sec += 2)
		{
			assertTrue("second " + sec, Flags.has(seconds.flags(sec), Flags.LOGIN_MASK));
		}
		for (long sec = 2; sec <= 6; sec += 2)
		{
			assertFalse("second " + sec, Flags.has(seconds.flags(sec), Flags.LOGIN_MASK));
		}
	}

	@Test
	public void maskedTicksMarkTheirSecondsWhileNoFrameIsDrawn()
	{
		// minimised: frames stop, ticks go on. The seconds are closed later, by the first frame that comes.
		state(0, State.HOPPING);
		run(0, 900, Long.MAX_VALUE);
		state(950, State.LOGGED_IN);
		for (long t = 1200; t <= 12_000; t += Thresholds.TICK_MS)
		{
			tick(t);
		}
		assertEquals(-1, session.seconds.frameHead());
		run(13_000, 14_000, Long.MAX_VALUE);
		final SecondRing seconds = session.seconds;
		assertEquals(13, seconds.frameHead());
		// fifteen ticks from 1,200: the last masked one arrives at 9,600
		for (long sec = 1; sec <= 9; sec++)
		{
			assertTrue("second " + sec, Flags.has(seconds.flags(sec), Flags.LOGIN_MASK));
			assertTrue(Flags.has(seconds.flags(sec), Flags.NO_FRAMES));
		}
		for (long sec = 10; sec <= 13; sec++)
		{
			assertFalse("second " + sec, Flags.has(seconds.flags(sec), Flags.LOGIN_MASK));
		}
		assertFalse(Flags.has(seconds.flags(0), Flags.LOGIN_MASK));
	}

	@Test
	public void hopSetsTheFlag()
	{
		state(0, State.LOGGED_IN);
		run(0, 4980, 600);
		state(5000, State.HOPPING);
		for (long t = 5000; t <= 6980; t += FRAME_MS)
		{
			if (t == 5400)
			{
				tick(t);
			}
			frame(t);
		}
		state(7000, State.LOGGED_IN);
		run(7000, 20_000, 7300);

		// ticks 0 .. 7 arrived at 600 .. 4,800, tick 8 inside the hop, tick 9 after it
		assertEquals(4800L, ring.atMs(7));
		assertFalse(Flags.has(ring.flags(7), Flags.HOP));
		assertFalse(masked(7));

		assertEquals(5400L, ring.atMs(8));
		assertTrue("a tick inside the hop carries its second's flag", Flags.has(ring.flags(8), Flags.HOP));
		assertFalse(masked(8));

		for (long seq = 9; seq < 9 + Thresholds.LOGIN_MASK_TICKS; seq++)
		{
			assertTrue("tick " + seq, masked(seq));
			assertFalse(Flags.has(ring.flags(seq), Flags.HOP));
		}
		assertEquals("the first of them spans the hop", 1900, ring.gapMs(9));
		assertFalse(masked(9 + Thresholds.LOGIN_MASK_TICKS));

		final SecondRing seconds = session.seconds;
		assertFalse(Flags.has(seconds.flags(4), Flags.HOP));
		assertTrue(Flags.has(seconds.flags(5), Flags.HOP));
		assertTrue(Flags.has(seconds.flags(6), Flags.HOP));
		assertFalse(Flags.has(seconds.flags(7), Flags.HOP));
		assertTrue(Flags.has(seconds.flags(7), Flags.LOGIN_MASK));
	}

	@Test
	public void theEndOfALostConnectionMasksFifteenTicks()
	{
		state(0, State.LOGGED_IN);
		run(0, 6080, 600);
		state(6100, State.CONNECTION_LOST);
		run(6100, 14_080, Long.MAX_VALUE);
		// back with no LOGGING_IN between
		state(14_100, State.LOGGED_IN);
		run(14_100, 30_000, 14_400);

		// ticks 0 .. 9 arrived at 600 .. 6,000
		assertEquals(6000L, ring.atMs(9));
		assertFalse(masked(9));
		assertEquals(14_400L, ring.atMs(10));
		assertEquals("the tick that spans the outage", 8400, ring.gapMs(10));
		for (long seq = 10; seq < 10 + Thresholds.LOGIN_MASK_TICKS; seq++)
		{
			assertTrue("tick " + seq, masked(seq));
		}
		assertFalse(masked(10 + Thresholds.LOGIN_MASK_TICKS));
		assertTrue(Flags.has(session.seconds.flags(14), Flags.LOGIN_MASK));
		assertFalse(Flags.has(session.seconds.flags(13), Flags.LOGIN_MASK));
		assertTrue(Flags.has(session.seconds.flags(13), Flags.DISCONNECT));
	}

	@Test
	public void aMapLoadStartsNoMask()
	{
		state(0, State.LOGGED_IN);
		run(0, 2980, 600);
		state(3000, State.LOADING);
		state(3300, State.LOGGED_IN);
		run(3320, 6000, 3600);
		for (long seq = 0; seq <= ring.head(); seq++)
		{
			assertFalse("tick " + seq, masked(seq));
		}
		// the tick at 3,600 arrived in second 3, which held the load: it carries its second's flag
		assertEquals(3600L, ring.atMs(4));
		assertTrue(Flags.has(ring.flags(4), Flags.LOADING));
		assertFalse(Flags.has(ring.flags(3), Flags.LOADING));
		assertFalse(Flags.has(ring.flags(5), Flags.LOADING));
	}

	@Test
	public void aTickCarriesNoLoadingThatItsSecondDoesNotHold()
	{
		// a load from 2,800 to 2,950 ms; ticks every 600 ms from 500 ms, frames every 20 ms
		state(0, State.LOGGED_IN);
		run(0, 2780, 500);
		state(2800, State.LOADING);
		run(2800, 2940, 2900);
		state(2950, State.LOGGED_IN);
		run(2960, 5000, 3500);

		final SecondRing seconds = session.seconds;
		assertEquals(4, seconds.frameHead());
		assertEquals(150, seconds.loadingMs(2));
		assertTrue(Flags.has(seconds.flags(2), Flags.LOADING));
		assertEquals("second 3 holds no loading time", 0, seconds.loadingMs(3));
		assertFalse("and no loading frame ended in it", Flags.has(seconds.flags(3), Flags.LOADING));

		assertEquals(2900L, ring.atMs(4));
		assertTrue("a tick inside the load carries its second's flag", Flags.has(ring.flags(4), Flags.LOADING));
		assertEquals(3500L, ring.atMs(5));
		assertFalse("the load lay in this tick's gap, but not in its second", Flags.has(ring.flags(5), Flags.LOADING));
		assertEquals("exactly its second's flags as they stood", Flags.FOCUSED, ring.flags(5));
		assertFalse(Flags.has(ring.flags(6), Flags.LOADING));
	}

	@Test
	public void aTickCarriesTheFlagsOfItsSecondAsTheyStand()
	{
		state(0, State.LOGGED_IN);
		frames.focus(false);
		tick(600);
		frames.focus(true);
		state(1100, State.CONNECTION_LOST);
		tick(1200);
		state(1300, State.LOGIN_SCREEN);
		tick(1800);
		assertEquals(0, ring.flags(0));
		assertEquals(Flags.FOCUSED | Flags.DISCONNECT, ring.flags(1));
		// second 1 held lost-connection time, and the code in force at the tick is the login screen; the end of the
		// lost connection started the mask
		assertEquals(Flags.FOCUSED | Flags.DISCONNECT | Flags.NOT_LOGGED_IN | Flags.LOGIN_MASK, ring.flags(2));
	}
}
