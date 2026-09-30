package com.whylag.core;

import java.time.ZoneOffset;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * {@link FrameSampler} (contract section 7, L2): what a second holds once the first frame past its boundary has
 * closed it. Times are session ms; the frame clock of a test runs on a 20 ms grid unless the test says otherwise.
 */
public class FrameSamplerTest
{
	private static final long START = 5_000_000_000_000L;
	private static final long NANOS_PER_MS = 1_000_000L;
	private static final int FRAME_MS = 20;

	private Session session;
	private SecondRing ring;
	private FrameSampler frames;

	@Before
	public void setUp()
	{
		session = new Session(START, 1_000_000L, Os.WINDOWS, ZoneOffset.UTC);
		ring = session.seconds;
		frames = new FrameSampler(session, new SelfTimer());
	}

	private static long at(long ms)
	{
		return START + ms * NANOS_PER_MS;
	}

	private void frame(long ms)
	{
		frames.frame(at(ms), (int) (ms / FRAME_MS));
	}

	/** Frames at {@code fromMs}, {@code fromMs + 20} ... up to and including {@code toMs}. */
	private void run(long fromMs, long toMs)
	{
		for (long t = fromMs; t <= toMs; t += FRAME_MS)
		{
			frame(t);
		}
	}

	private boolean has(long sec, int bit)
	{
		return Flags.has(ring.flags(sec), bit);
	}

	@Test
	public void fiftyFramesASecond()
	{
		frames.state(at(0), State.LOGGED_IN);
		frames.world(416);
		frames.scene(7, 9, 12850);
		run(0, 2980);
		assertEquals("no frame has passed second 2's boundary yet", 1, ring.frameHead());
		frame(3000);
		assertEquals(2, ring.frameHead());
		for (long sec = 0; sec <= 2; sec++)
		{
			assertEquals("frames of second " + sec, 50, ring.frames(sec));
			assertEquals(FRAME_MS, ring.worstFrameMs(sec));
			assertEquals(0, ring.slowFrames(sec));
			assertEquals(State.LOGGED_IN, ring.state(sec));
			assertEquals(Flags.FOCUSED, ring.flags(sec));
			assertEquals(416, ring.world(sec));
			assertEquals(7, ring.players(sec));
			assertEquals(9, ring.npcs(sec));
			assertEquals(12850, ring.region(sec));
			assertEquals(0, ring.loadingMs(sec));
		}
		assertEquals("of equal frames the later is the worst", 980, ring.worstFrameEndMs(1));
	}

	@Test
	public void aStallIsChargedToTheSecondItEndedIn()
	{
		frames.state(at(0), State.LOGGED_IN);
		run(0, 1500);
		run(3700, 5000);
		assertEquals(4, ring.frameHead());

		assertEquals("1000 .. 1500", 26, ring.frames(1));
		assertEquals("the stall is not charged to the second it began in", FRAME_MS, ring.worstFrameMs(1));
		assertFalse(has(1, Flags.NO_FRAMES));

		assertEquals(0, ring.frames(2));
		assertEquals(0, ring.worstFrameMs(2));
		assertTrue(has(2, Flags.NO_FRAMES));

		assertEquals("3700 .. 3980", 15, ring.frames(3));
		assertEquals(2200, ring.worstFrameMs(3));
		assertEquals(700, ring.worstFrameEndMs(3));
		assertEquals(1, ring.slowFrames(3));
		assertFalse(has(3, Flags.NO_FRAMES));

		assertEquals(FRAME_MS, ring.worstFrameMs(4));
	}

	@Test
	public void worstFrameEndIsItsOffsetInTheSecond()
	{
		run(0, 1240);
		frame(1340);
		run(1360, 1900);
		frame(1960);
		run(1980, 2000);
		assertEquals(100, ring.worstFrameMs(1));
		assertEquals("the 100 ms frame ended 340 ms into second 1", 340, ring.worstFrameEndMs(1));
		assertEquals("100 ms and 60 ms", 2, ring.slowFrames(1));

		// a frame that ends in the first ms of a second, and one that ends in its last
		setUp();
		run(0, 900);
		frame(1000);
		run(1020, 1900);
		frame(2999);
		frame(3000);
		assertEquals(100, ring.worstFrameMs(1));
		assertEquals(0, ring.worstFrameEndMs(1));
		assertEquals(1099, ring.worstFrameMs(2));
		assertEquals(999, ring.worstFrameEndMs(2));
	}

	@Test
	public void missedSecondsAreNoFrames()
	{
		frames.state(at(0), State.LOGGED_IN);
		frames.world(302);
		frame(500);
		frame(520);
		frame(4500);
		assertEquals(3, ring.frameHead());

		assertEquals(2, ring.frames(0));
		assertFalse(has(0, Flags.NO_FRAMES));
		for (long sec = 1; sec <= 3; sec++)
		{
			assertEquals(0, ring.frames(sec));
			assertEquals(0, ring.worstFrameMs(sec));
			assertEquals(0, ring.worstFrameEndMs(sec));
			assertEquals(0, ring.slowFrames(sec));
			assertEquals("a filled second keeps the code in force", State.LOGGED_IN, ring.state(sec));
			assertEquals(302, ring.world(sec));
			assertEquals(Flags.NO_FRAMES | Flags.FOCUSED, ring.flags(sec));
		}
		frame(5000);
		assertEquals(3980, ring.worstFrameMs(4));
		assertEquals(500, ring.worstFrameEndMs(4));
		assertFalse(has(4, Flags.NO_FRAMES));
	}

	@Test
	public void aDayAwayIsFilledInOneGo()
	{
		final long day = 86_400;
		frames.state(at(0), State.LOGGED_IN);
		run(0, 500);
		final long before = System.nanoTime();
		frame((day + 1) * 1000 + 500);
		final long tookMs = (System.nanoTime() - before) / NANOS_PER_MS;
		assertEquals("seconds 0 .. 86,400 are written by the one frame", day, ring.frameHead());
		assertTrue("filling a day took " + tookMs + " ms", tookMs < 2000);

		// the host half, so that the ring's newest seconds are readable
		final HostSecond h = new HostSecond();
		for (long sec = 0; sec <= day; sec++)
		{
			ring.putHost(sec, h);
		}
		final long tail = ring.tail();
		assertEquals(day - Thresholds.SECONDS + 1, tail);
		for (long sec = tail; sec <= day; sec++)
		{
			assertTrue(ring.valid(sec));
			assertTrue("second " + sec, has(sec, Flags.NO_FRAMES));
			assertEquals(0, ring.frames(sec));
			assertEquals(State.LOGGED_IN, ring.state(sec));
		}
		frame((day + 2) * 1000);
		assertEquals("the day is charged to the second it ended in, clamped to the column", Short.MAX_VALUE,
			ring.worstFrameMs(day + 1));
		assertEquals(500, ring.worstFrameEndMs(day + 1));
		assertEquals(1, ring.frames(day + 1));
	}

	@Test
	public void loadingMsIsSummed()
	{
		frames.state(at(0), State.LOGGED_IN);
		run(0, 2100);
		frames.state(at(2110), State.LOADING);
		run(2120, 2220);
		frames.state(at(2230), State.LOGGED_IN);
		run(2240, 2600);
		frames.state(at(2610), State.LOADING);
		run(2620, 2680);
		frames.state(at(2690), State.LOGGED_IN);
		run(2700, 4000);

		assertEquals(0, ring.loadingMs(1));
		assertFalse(has(1, Flags.LOADING));
		assertEquals("120 ms and 80 ms", 200, ring.loadingMs(2));
		assertTrue(has(2, Flags.LOADING));
		assertEquals(State.LOGGED_IN, ring.state(2));
		assertEquals(0, ring.loadingMs(3));
		assertFalse("every loading frame ended in second 2", has(3, Flags.LOADING));
	}

	@Test
	public void teleportFrameAfterLoadIsMasked()
	{
		frames.state(at(0), State.LOGGED_IN);
		run(0, 2700);
		frames.state(at(2705), State.LOADING);
		frame(2720);
		frames.state(at(2900), State.LOGGED_IN);
		frame(3170);
		run(3190, 5000);

		assertEquals(195, ring.loadingMs(2));
		assertTrue(has(2, Flags.LOADING));

		assertEquals(450, ring.worstFrameMs(3));
		assertEquals(170, ring.worstFrameEndMs(3));
		assertEquals("second 3 holds no LOADING time", 0, ring.loadingMs(3));
		assertEquals(State.LOGGED_IN, ring.state(3));
		assertTrue("the frame that began under LOADING ended here", has(3, Flags.LOADING));

		assertFalse("the frames after it are ordinary", has(4, Flags.LOADING));
	}

	@Test
	public void aLoadBetweenTwoFramesMarksTheFrameThatHeldIt()
	{
		frames.state(at(0), State.LOGGED_IN);
		run(0, 1900);
		// entered and left between two frames, in second 1; the frame that held it ends in second 2
		frames.state(at(1910), State.LOADING);
		frames.state(at(1990), State.LOGGED_IN);
		frame(2300);
		run(2320, 4000);
		assertEquals(80, ring.loadingMs(1));
		assertTrue(has(1, Flags.LOADING));
		assertEquals(0, ring.loadingMs(2));
		assertTrue(has(2, Flags.LOADING));
		assertFalse(has(3, Flags.LOADING));
	}

	@Test
	public void aSecondKeepsTheStateInForceAtItsEnd()
	{
		frames.state(at(0), State.LOGGED_IN);
		run(0, 2680);
		frames.state(at(2700), State.LOGIN_SCREEN);
		run(2700, 2980);
		frames.focus(false);
		frame(3000);
		frames.state(at(3001), State.LOGGING_IN);
		run(3020, 3980);
		frames.focus(true);
		run(4000, 5000);

		assertEquals(State.LOGGED_IN, ring.state(1));
		assertFalse(has(1, Flags.NOT_LOGGED_IN));
		assertTrue(has(1, Flags.FOCUSED));

		assertEquals("handed in 300 ms before the end", State.LOGIN_SCREEN, ring.state(2));
		assertTrue(has(2, Flags.NOT_LOGGED_IN));
		assertFalse("the focus at the close", has(2, Flags.FOCUSED));

		assertEquals("handed in just after second 2's end", State.LOGGING_IN, ring.state(3));
		assertTrue(has(3, Flags.NOT_LOGGED_IN));
		assertTrue(has(3, Flags.FOCUSED));
	}

	@Test
	public void aStateHandedInBeforeTheClosingFrameKeepsItsTime()
	{
		frames.state(at(0), State.LOGGED_IN);
		run(0, 5500);
		// no frame between these three calls: the frame at 8020 closes seconds 5, 6 and 7
		frames.state(at(5700), State.LOGIN_SCREEN);
		frames.state(at(6001), State.LOGGING_IN);
		frames.state(at(7999), State.LOADING);
		assertEquals("a state call closes no second: the frame head says where the frames are", 4,
			ring.frameHead());
		run(8020, 9000);

		assertEquals(State.LOGIN_SCREEN, ring.state(5));
		assertTrue(has(5, Flags.NOT_LOGGED_IN));
		assertEquals(State.LOGGING_IN, ring.state(6));
		assertTrue(has(6, Flags.NOT_LOGGED_IN));
		assertEquals(State.LOADING, ring.state(7));
		assertFalse(has(7, Flags.NOT_LOGGED_IN));
		assertTrue(has(7, Flags.LOADING));
		assertEquals(1, ring.loadingMs(7));
		assertEquals(1000, ring.loadingMs(8));
	}

	@Test
	public void hopAndLostConnectionFlagEachOfTheirSeconds()
	{
		frames.state(at(0), State.LOGGED_IN);
		run(0, 1980);
		frames.state(at(2000), State.HOPPING);
		run(2000, 4980);
		frames.state(at(5000), State.LOGGED_IN);
		run(5000, 7980);
		frames.state(at(8000), State.CONNECTION_LOST);
		run(8000, 10980);
		frames.state(at(11000), State.LOGGED_IN);
		run(11000, 13480);
		frames.state(at(13500), State.LOADING);
		frames.state(at(16100), State.LOGGED_IN);
		run(16200, 18000);

		assertFalse(has(1, Flags.HOP));
		for (long sec = 2; sec <= 4; sec++)
		{
			assertTrue("hop second " + sec, has(sec, Flags.HOP));
			assertEquals(State.HOPPING, ring.state(sec));
			assertFalse(has(sec, Flags.NOT_LOGGED_IN));
			assertFalse(has(sec, Flags.DISCONNECT));
		}
		assertFalse("the hop ended with second 4", has(5, Flags.HOP));
		assertEquals(State.LOGGED_IN, ring.state(5));

		assertFalse(has(7, Flags.DISCONNECT));
		for (long sec = 8; sec <= 10; sec++)
		{
			assertTrue("lost second " + sec, has(sec, Flags.DISCONNECT));
			assertEquals(State.CONNECTION_LOST, ring.state(sec));
			assertFalse(has(sec, Flags.NOT_LOGGED_IN));
			assertFalse(has(sec, Flags.HOP));
		}
		assertFalse(has(11, Flags.DISCONNECT));

		assertEquals(500, ring.loadingMs(13));
		assertTrue(has(13, Flags.LOADING));
		for (long sec = 14; sec <= 15; sec++)
		{
			assertTrue("a filled second", has(sec, Flags.NO_FRAMES));
			assertEquals(1000, ring.loadingMs(sec));
			assertTrue(has(sec, Flags.LOADING));
			assertEquals(State.LOADING, ring.state(sec));
		}
		assertEquals(100, ring.loadingMs(16));
		assertTrue(has(16, Flags.LOADING));
		assertEquals(State.LOGGED_IN, ring.state(16));
		assertFalse(has(17, Flags.LOADING));
	}

	@Test
	public void theFirstFrameClosesNoInterval()
	{
		frames.state(at(0), State.LOGGED_IN);
		assertEquals(0, frames.openFrameMs(at(4000)));
		assertEquals(0, frames.worstFrameSinceTickMs());
		frame(5300);
		assertEquals("the first frame is not measured from the session's start", 0, frames.worstFrameSinceTickMs());
		assertEquals(4, ring.frameHead());
		for (long sec = 0; sec <= 4; sec++)
		{
			assertTrue("second " + sec, has(sec, Flags.NO_FRAMES));
			assertEquals(0, ring.frames(sec));
		}
		assertEquals(250, frames.openFrameMs(at(5550)));
		frame(6100);
		frame(7000);

		assertEquals("the first frame counts", 1, ring.frames(5));
		assertEquals("and closes no interval", 0, ring.worstFrameMs(5));
		assertEquals(0, ring.worstFrameEndMs(5));
		assertEquals(0, ring.slowFrames(5));
		assertFalse(has(5, Flags.NO_FRAMES));

		assertEquals(800, ring.worstFrameMs(6));
		assertEquals(100, ring.worstFrameEndMs(6));
		assertEquals(1, ring.slowFrames(6));
	}

	@Test
	public void theWorstFrameSinceTheTickStartsAgainAtAReset()
	{
		run(0, 400);
		frame(480);
		run(500, 600);
		assertEquals(80, frames.worstFrameSinceTickMs());
		frames.resetWorstSinceTick();
		assertEquals(0, frames.worstFrameSinceTickMs());
		run(620, 700);
		assertEquals(FRAME_MS, frames.worstFrameSinceTickMs());
		assertEquals(35, frames.openFrameMs(at(735)));
	}

	@Test
	public void loadingSinceTickSeesALoadThatCameAndWent()
	{
		frames.state(at(0), State.LOGGED_IN);
		assertFalse(frames.loadingSinceTick());
		frames.state(at(100), State.LOADING);
		assertTrue(frames.loadingSinceTick());
		frames.resetWorstSinceTick();
		assertTrue("still loading", frames.loadingSinceTick());
		frames.state(at(300), State.LOGGED_IN);
		assertTrue("the load ended inside this tick's gap", frames.loadingSinceTick());
		frames.resetWorstSinceTick();
		assertFalse(frames.loadingSinceTick());
	}

	@Test
	public void moreStateChangesThanTheSamplerKeepsAreSafe()
	{
		frames.state(at(0), State.LOGGED_IN);
		run(0, 900);
		for (int i = 0; i < 300; i++)
		{
			frames.state(at(1000 + i * 10), i % 2 == 0 ? State.LOADING : State.LOGGED_IN);
		}
		frames.state(at(4500), State.HOPPING);
		run(5020, 7000);
		assertEquals(6, ring.frameHead());
		assertEquals("the newest code is never lost", State.HOPPING, ring.state(5));
		assertTrue(has(5, Flags.HOP));
		assertTrue(has(1, Flags.LOADING));
		assertEquals(State.HOPPING, ring.state(6));
	}
}
