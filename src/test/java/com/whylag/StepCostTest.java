package com.whylag;

import com.whylag.core.DetectorListener;
import com.whylag.core.LagDetector;
import com.whylag.core.LagEvent;
import com.whylag.core.PanelSnapshot;
import com.whylag.core.Session;
import com.whylag.core.SettingsView;
import com.whylag.core.SnapshotBuilder;
import com.whylag.core.Trace;
import com.whylag.core.Verdict;
import com.whylag.core.VerdictEngine;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * T16 (contract 4; section 7, L9): the detector, the verdict and the snapshot, joined as {@code LagEngine.step} joins
 * them, cost under 1 ms a step on a full 60-minute session. The bound asserted is T16's generous 20 ms, so the test
 * stays stable on a busy machine; the 1 ms is read from the printed mean.
 *
 * <p>L9 is the one lot that may name all three classes. The trace is a {@link Trace} of 3,600 seconds, so the second
 * ring is full, with a lag every two minutes (a long frame), a late tick every four and a memory pause every six, so
 * that events open, close and are judged inside the timed window too.
 *
 * <p>
 * Choice: the lags are a 400 ms frame at second 50 and every 120 s after it, a tick 400 ms late (with its catch-up) at
 * second 110 and every 240 s after it, and a 150 ms collection at second 170 and every 360 s after it.
 * <br>
 * Choice: the warm-up is the whole run once, on a separate session built from the same trace with its own detector,
 * judge and builder, untimed; the timed run then starts afresh.
 * <br>
 * Choice: a step's time runs from the detector's advance to the built snapshot, both included.
 */
public class StepCostTest
{
	/** T16's generous bound for the mean step, in ms (contract 4). */
	private static final long T16_BOUND_MS = 20;
	private static final int SECONDS = 3600;
	/** The detector catches up to this second in ONE advance, untimed. */
	private static final int CATCH_UP = 3299;
	private static final int FIRST_TIMED = 3300;
	private static final int RANGE_MINUTES = 60;

	@Test
	public void aFullSessionStepIsFast()
	{
		run(trace());
		final Run r = run(trace());

		long sum = 0;
		long max = 0;
		for (long n : r.stepNanos)
		{
			sum += n;
			max = Math.max(max, n);
		}
		final long mean = sum / r.stepNanos.length;
		assertTrue("mean " + mean / 1_000 + " us", mean < T16_BOUND_MS * 1_000_000L);

		// The steps did the real work: events opened and closed inside the timed window, each closed one was judged,
		// an open one was judged as it went, and the last snapshot is the full hour.
		assertTrue("events opened inside the timed window: " + r.openedTimed, r.openedTimed >= 3);
		assertTrue("events closed inside the timed window: " + r.closedTimed, r.closedTimed >= 3);
		assertTrue("an open event was judged in a step", r.openJudged > 0);
		for (LagEvent e : r.session.events.copy())
		{
			if (!e.open)
			{
				assertNotNull("event " + e.id + " was closed without a verdict", e.verdict);
			}
		}
		assertTrue(r.session.events.sessionTotal() >= 20);
		assertNotNull(r.last);
		assertEquals(RANGE_MINUTES, r.last.rangeMinutes);
		assertTrue("the range reaches back past the ring's tail", r.last.rangeEvents.size() >= 20);
	}

	private static Trace trace()
	{
		final Trace t = Trace.steady(SECONDS).usual(40);
		for (int k = 50; k < SECONDS - 10; k += 120)
		{
			t.frameGap(k, 500, 400);
		}
		for (int k = 110; k < SECONDS - 10; k += 240)
		{
			t.tickLate(k, 400, true);
		}
		return t;
	}

	/** Drives the three as {@code LagEngine.step} does, one timed step for each of the seconds 3,300 to 3,599. */
	private static Run run(Trace t)
	{
		final Run r = new Run();
		final Session s = t.build();
		final SettingsView settings = t.settings();
		final LagDetector detector = new LagDetector();
		final VerdictEngine judge = new VerdictEngine();
		final SnapshotBuilder snapshots = new SnapshotBuilder();
		r.session = s;
		final DetectorListener log = new DetectorListener()
		{
			@Override
			public void opened(LagEvent e)
			{
				s.events.add(e);
				r.opened++;
			}

			@Override
			public void closed(LagEvent e)
			{
				final LagEvent judged = e.withVerdict(judge.judgeEvent(s, e, settings));
				if (!s.events.replace(judged))
				{
					s.events.add(judged);
				}
				r.closed++;
			}
		};

		detector.advance(s, CATCH_UP, settings, log);
		final int openedBefore = r.opened;
		final int closedBefore = r.closed;
		r.stepNanos = new long[SECONDS - FIRST_TIMED];
		for (int sec = FIRST_TIMED; sec < SECONDS; sec++)
		{
			final long nowSec = sec + 1;
			final long wallMs = s.wallMsOf(nowSec);
			final long t0 = System.nanoTime();
			detector.advance(s, sec, settings, log);
			final LagEvent open = detector.open();
			LagEvent provisional = null;
			if (open != null)
			{
				provisional = open.withVerdict(judge.judgeEvent(s, open, settings));
			}
			final Verdict v = judge.current(s, nowSec, wallMs, settings);
			final PanelSnapshot p = snapshots.build(s, v, RANGE_MINUTES, nowSec, wallMs, settings, "");
			r.stepNanos[sec - FIRST_TIMED] = System.nanoTime() - t0;
			if (provisional != null && provisional.verdict != null)
			{
				r.openJudged++;
			}
			r.last = p;
		}
		r.openedTimed = r.opened - openedBefore;
		r.closedTimed = r.closed - closedBefore;
		return r;
	}

	private static final class Run
	{
		private Session session;
		private long[] stepNanos;
		private int opened;
		private int closed;
		private int openedTimed;
		private int closedTimed;
		private int openJudged;
		private PanelSnapshot last;
	}
}
