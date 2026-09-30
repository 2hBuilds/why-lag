package com.whylag.core;

import java.time.Instant;
import java.time.ZoneOffset;
import java.util.regex.Pattern;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * The minute line and the log that keeps an hour of them (1.0.1, lot A, A3): the arithmetic from a fixture of sixty
 * seconds with one masked second and one lag start; a dash for a reading with no data; the exact shape of the line;
 * and the ring that keeps the newest sixty of seventy. The fixture is a {@link Trace}, written into the rings as the
 * samplers would.
 */
public class MinuteLogTest
{
	/** Second 0 of every {@link Trace}: 2026-09-28 20:52:00 UTC. */
	private static final long START = Instant.parse("2026-09-28T20:52:00Z").toEpochMilli();
	private static final Pattern SHAPE = Pattern.compile("\\d\\d:\\d\\d  fps (-|\\d+/\\d+/\\d+)  tick (-|[\\d,]+/[\\d,]+ ms)"
		+ "  ping (-|\\d+-\\d+ ms)  lags \\d+  masked \\d+ s");

	/**
	 * Sixty seconds, the numbers worked by hand: 10 s at 48 fps, 10 s at 51 and 40 s at 50, one of them (second 30,
	 * a load) masked and so left out, which leaves 59 seconds of 2,940 frames, mean 49.83 and so 50; 100 ticks of
	 * which the two that arrive in the loading second are left out, 98 ticks whose gaps sum to 58,800 ms, the worst 952
	 * (one tick 352 ms late), so 600 and 952; a ping of 40 ms with ten seconds at 43 - a stale one of 900 ms and two
	 * seconds with none are not counted; one lag started in it.
	 */
	@Test
	public void theArithmeticOfSixtySeconds()
	{
		final Session s = fixture().build();
		s.events.add(lag(0, 25));

		final MinuteLine line = MinuteLine.of(s, 60);

		assertEquals(START, line.startWallMs);
		assertEquals(48, line.fpsMin);
		assertEquals(50, line.fpsMean);
		assertEquals(51, line.fpsMax);
		assertEquals(600, line.tickMeanMs);
		assertEquals(952, line.tickWorstMs);
		assertEquals(40, line.pingMinMs);
		assertEquals(43, line.pingMaxMs);
		assertEquals(1, line.lags);
		assertEquals(1, line.maskedSeconds);
		assertEquals("20:52  fps 48/50/51  tick 600/952 ms  ping 40-43 ms  lags 1  masked 1 s",
			line.text(ZoneOffset.UTC));
	}

	/** A tick that comes late inside a masked second is left out of the tick gap: the worst stays 952, not 1,100. */
	@Test
	public void aMaskedSecondsTicksAreLeftOut()
	{
		final Session s = fixture().tickLate(30, 500, true).build();

		final MinuteLine line = MinuteLine.of(s, 60);

		assertEquals("the 1,100 ms gap came in the loading second", 952, line.tickWorstMs);
		assertEquals(600, line.tickMeanMs);
		assertEquals(1, line.maskedSeconds);
	}

	/** The window is the sixty seconds before the step: a lag that started outside it is not in the line. */
	@Test
	public void aLagIsCountedInTheMinuteItStartedIn()
	{
		final Session s = Trace.steady(120).build();
		s.events.add(lag(0, 25));
		s.events.add(lag(1, 61));
		s.events.add(lag(2, 119));

		assertEquals(1, MinuteLine.of(s, 60).lags);
		assertEquals(2, MinuteLine.of(s, 120).lags);
		assertEquals("the second minute starts a minute later", START + 60_000L, MinuteLine.of(s, 120).startWallMs);
	}

	/** A stale round trip is not a ping: the highest stays 43 although the kept number of a stale second is 900. */
	@Test
	public void aStaleRoundTripIsNotCounted()
	{
		final MinuteLine line = MinuteLine.of(Trace.steady(60).rtt(0, 59, 40).rtt(10, 19, 43).rtt(20, 20, 900)
			.rttStale(20, 20).build(),
			60);

		assertEquals(40, line.pingMinMs);
		assertEquals(43, line.pingMaxMs);
	}

	/** Every reading with no data prints "-" and no unit. */
	@Test
	public void aReadingWithNoDataPrintsADash()
	{
		final Session s = Trace.steady(60).rttNoData(0, 59, NoData.NOT_LOGGED_IN).build();

		final MinuteLine line = MinuteLine.of(s, 60);

		assertEquals(-1, line.pingMinMs);
		assertEquals("20:52  fps 50/50/50  tick 600/600 ms  ping -  lags 0  masked 0 s",
			line.text(ZoneOffset.UTC));
		assertEquals("all dashes",
			"20:52  fps -  tick -  ping -  lags 0  masked 60 s",
			new MinuteLine(START, -1, -1, -1, -1, -1, -1, -1, 0, 60).text(ZoneOffset.UTC));
	}

	/** A session with nothing written: a line of dashes, and no exception. */
	@Test
	public void noSecondsAtAllIsAllDashes()
	{
		final Session s = new Session(1_000_000_000L, START, Os.WINDOWS, ZoneOffset.UTC);

		assertEquals("20:52  fps -  tick -  ping -  lags 0  masked 0 s",
			MinuteLine.of(s, 60).text(ZoneOffset.UTC));
		assertEquals("a session that has just begun",
			"20:52  fps -  tick -  ping -  lags 0  masked 0 s",
			MinuteLine.of(s, 0).text(ZoneOffset.UTC));
	}

	/** The shape of the plan, character for character; thousands get their commas. */
	@Test
	public void theLineHasTheExactShape()
	{
		final long at = Instant.parse("2026-09-30T21:47:00Z").toEpochMilli();
		assertEquals("21:47  fps 48/50/51  tick 601/952 ms  ping 40-43 ms  lags 1  masked 0 s",
			new MinuteLine(at, 48, 50, 51, 601, 952, 40, 43, 1, 0).text(ZoneOffset.UTC));
		assertEquals("21:47  fps 1,000/1,000/1,000  tick 600/1,240 ms  ping 1,200-1,300 ms  lags 1,001  masked 60 s",
			new MinuteLine(at, 1000, 1000, 1000, 600, 1240, 1200, 1300, 1001, 60).text(ZoneOffset.UTC));
		assertTrue(SHAPE.matcher(MinuteLine.of(fixture().build(), 60).text(ZoneOffset.UTC)).matches());
		assertTrue(SHAPE.matcher(new MinuteLine(at, -1, -1, -1, -1, -1, -1, -1, 0, 0)
			.text(ZoneOffset.UTC)).matches());
	}

	/** The clock is the minute's first second in the log's zone. */
	@Test
	public void theClockFollowsTheZone()
	{
		final MinuteLine line = new MinuteLine(START, -1, -1, -1, -1, -1, -1, -1, 0, 0);

		assertEquals("20:52", line.text(ZoneOffset.UTC).substring(0, 5));
		assertEquals("05:52", line.text(ZoneOffset.ofHours(9)).substring(0, 5));
	}

	// ---------------------------------------------------------------- the log

	/** Seventy minutes in, the newest sixty out, oldest first: minute 10 to minute 69. */
	@Test
	public void theRingKeepsTheNewestSixtyOfSeventy()
	{
		assertEquals(60, MinuteLog.LINES);
		final MinuteLog log = new MinuteLog(ZoneOffset.UTC);
		for (int i = 0; i < 70; i++)
		{
			log.add(new MinuteLine(START + i * 60_000L, 50, 50, 50, 600, 600, 40, 40, i, 0));
		}

		final String[] lines = log.text().split("\n");

		assertEquals(60, log.size());
		assertEquals(60, lines.length);
		assertTrue(lines[0], lines[0].startsWith("21:02  fps "));
		assertTrue(lines[0], lines[0].endsWith("  lags 10  masked 0 s"));
		assertTrue(lines[59], lines[59].startsWith("22:01  fps "));
		assertTrue(lines[59], lines[59].endsWith("  lags 69  masked 0 s"));
		for (int i = 0; i < lines.length; i++)
		{
			assertTrue(lines[i], lines[i].endsWith("  lags " + (10 + i) + "  masked 0 s"));
		}
	}

	@Test
	public void anEmptyLogIsAnEmptyTextAndNullIsIgnored()
	{
		final MinuteLog log = new MinuteLog(ZoneOffset.UTC);

		assertEquals("", log.text());
		assertEquals(0, log.size());
		log.add(null);
		assertEquals(0, log.size());
		log.add(new MinuteLine(START, -1, -1, -1, -1, -1, -1, -1, 0, 0));
		assertTrue("every line is ended with a new line", log.text().endsWith("masked 0 s\n"));
	}

	// ---------------------------------------------------------------- fixtures

	/**
	 * The sixty seconds of {@link #theArithmeticOfSixtySeconds}: 48 fps in seconds 0 to 9, 51 in 10 to 19, a tick
	 * 352 ms late in 20, a load in 30, a ping of 43 in 40 to 49, none in 50 to 52, a stale one in 55.
	 */
	private static Trace fixture()
	{
		return Trace.steady(60).fps(0, 9, 48).fps(10, 19, 51).tickLate(20, 352, true).loading(30, 400)
			.rtt(40, 49, 43).rttNoData(50, 52, NoData.ERROR).rtt(55, 55, 900).rttStale(55, 55);
	}

	/** A lag of one second that started at session second {@code startSec}. */
	private static LagEvent lag(long id, long startSec)
	{
		return new LagEvent(id, startSec, startSec, START + startSec * 1000, Trigger.TICK_OFF.bit(),
			Trigger.TICK_OFF, 416, 0, 0, 0, 50, 34, 952, 1240, 640, 41, 44, 41, 900, 0, false, false, null);
	}
}
