package com.whylag.core;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.function.Consumer;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * The detector's properties over random traces, seeds 1 .. 50 (contract 7, L3): events never overlap, come in order
 * and are numbered 0, 1, 2 ...; reading in one step, second by second or in random steps gives the same events; a
 * fully masked trace and a steady one have none; shifting a trace shifts its events; and a ring that lost its
 * oldest seconds still finds every later event, the same in every field but the id.
 *
 * <p>A random trace is a {@link Recipe}: incidents (freezes, pauses, late and missing ticks, slow worlds, ping
 * changes, re-sends, loads, hops, lost connections, focus, frame rates, CPU and scene columns, storms of frame gaps
 * that outlast {@link Thresholds#EVENT_MAX_S}) laid on a steady trace, kept as steps so the same incidents can be
 * laid on a trace of another length. A step that {@link Trace} refuses changes nothing and is skipped.
 *
 * <p>Choice: wrapLosesOnlyTheOldest lays one recipe on SECONDS s and on m s more, and compares events from m + 60.
 * <p>Choice: a trace is fully masked one of four ways: the login screen, one endless load, FPS Control unfocused, hops.
 * <p>Choice: in shiftingTheTraceShiftsTheEvents no tick gap is under TICK_MS, so the ring holds every tick (bound c).
 * <p>Choice: traces run 60 to 3,600 s (3,600 + m when wrapped), with random settings, OS and steps of advance.
 */
public class LagDetectorPropertyTest
{
	private static final int SEEDS = 50;
	/** The steady seconds that the wrap property keeps free of incidents where the wrapped ring begins. */
	private static final int CALM_S = 60;
	/** Where the wrap property's incidents end. */
	private static final int WRAP_INCIDENTS_END = 3400;

	@Test
	public void eventsNeverOverlapAndAreOrdered()
	{
		// What the seeds reached, so a generator that stopped making some kind of event fails here.
		final int[] withTrigger = new int[Trigger.values().length];
		int conditions = 0;
		int merged = 0;
		for (long seed = 1; seed <= SEEDS; seed++)
		{
			final Random r = new Random(seed);
			final int n = 300 + r.nextInt(3300);
			final Trace t = anything(r, n).on(n);
			final SettingsView v = randomSettings(r);
			final Session s = t.build();
			final LagDetector d = new LagDetector();
			final LagDetectorTest.Recorder out = new LagDetectorTest.Recorder();
			advanceInSteps(r, d, s, n, v, out);
			checkOrder("seed " + seed, s, d, out, n);
			for (LagEvent e : out.closed)
			{
				for (Trigger trigger : Trigger.values())
				{
					withTrigger[trigger.ordinal()] += e.has(trigger) ? 1 : 0;
				}
				conditions += e.becameCondition ? 1 : 0;
				merged += e.lengthS() > 1 ? 1 : 0;
			}
		}
		for (Trigger trigger : Trigger.values())
		{
			assertTrue("the seeds reach an event with " + trigger, withTrigger[trigger.ordinal()] > 0);
		}
		assertTrue("the seeds reach an event that became a condition", conditions > 0);
		assertTrue("the seeds reach an event of several seconds", merged > 0);
	}

	@Test
	public void advanceInOneStepEqualsAdvanceSecondBySecond()
	{
		for (long seed = 1; seed <= SEEDS; seed++)
		{
			final Random r = new Random(seed);
			final int n = 200 + r.nextInt(2400);
			final Trace t = anything(r, n).on(n);
			final SettingsView v = randomSettings(r);

			final Session oneS = t.build();
			final LagDetector one = new LagDetector();
			final LagDetectorTest.Recorder oneOut = new LagDetectorTest.Recorder();
			one.advance(oneS, n - 1, v, oneOut);

			final Session bySecondS = t.build();
			final LagDetector bySecond = new LagDetector();
			final LagDetectorTest.Recorder bySecondOut = new LagDetectorTest.Recorder();
			for (long k = 0; k < n; k++)
			{
				bySecond.advance(bySecondS, k, v, bySecondOut);
			}

			final Session stepsS = t.build();
			final LagDetector steps = new LagDetector();
			final LagDetectorTest.Recorder stepsOut = new LagDetectorTest.Recorder();
			advanceInSteps(r, steps, stepsS, n, v, stepsOut);

			final String at = "seed " + seed;
			assertFalse(at + ": a trace with incidents has events", oneOut.opened.isEmpty());
			assertEquals(at + ": one step against second by second", oneOut.log, bySecondOut.log);
			assertEquals(at + ": one step against random steps", oneOut.log, stepsOut.log);
			final String openOne = LagDetectorTest.describe(one.open(), 0, true);
			assertEquals(at, openOne, LagDetectorTest.describe(bySecond.open(), 0, true));
			assertEquals(at, openOne, LagDetectorTest.describe(steps.open(), 0, true));
			for (long sec = 0; sec < n; sec++)
			{
				assertEquals(at + ": triggers @" + sec, one.triggers(sec), bySecond.triggers(sec));
				assertEquals(at + ": triggers @" + sec, one.triggers(sec), steps.triggers(sec));
			}
			assertSameUsuals(at, oneS, bySecondS);
			assertSameUsuals(at, oneS, stepsS);
		}
	}

	@Test
	public void fullyMaskedTraceHasNoEvents()
	{
		for (long seed = 1; seed <= SEEDS; seed++)
		{
			final Random r = new Random(seed);
			final int n = 60 + r.nextInt(2000);
			final int how = (int) (seed % 4);
			final Space space = new Space(0, how == 0 ? n - 1 : n);
			space.disconnects = false;
			space.loads = false;
			space.tickMoves = how != 3;
			final Recipe recipe = incidents(r, space, n / 20);
			SettingsView v = Trace.steady(1).settings();
			switch (how)
			{
				case 0:
					// The login screen, and a load in the last second, which has not ended.
					recipe.add(t -> t.loginAt(n - 1));
					recipe.add(t -> t.loading(n - 1, 100));
					break;
				case 1:
					// One load from the first second to the last, which has not ended.
					for (int sec = 0; sec < n; sec++)
					{
						final int s = sec;
						final int ms = 1 + r.nextInt(1000);
						recipe.add(t -> t.loading(s, ms));
					}
					break;
				case 2:
					// Unfocused throughout, under FPS Control's unfocused limit.
					v = new SettingsView(Renderer.CPU, true, r.nextBoolean(), 30, true, 1 + r.nextInt(50), false, "", 0,
						0, "", 0, 60, 768, MemorySource.MANAGEMENT, Os.WINDOWS, "");
					recipe.add(t -> t.unfocused(0, n - 1));
					break;
				default:
					// A hop every 5 s: each masks its own second and the nine after it.
					for (int sec = 0; sec < n; sec += 5)
					{
						final int s = sec;
						recipe.add(t -> t.hop(s, 300 + s % 7));
					}
					break;
			}
			final Session s = recipe.on(n).build();
			for (long sec = 0; sec < n; sec++)
			{
				if (!Masks.masked(s.seconds, sec, v))
				{
					fail("seed " + seed + ", way " + how + ": the generator left second " + sec + " unmasked");
				}
			}
			// A recipe may hold Trace.usual, which build() puts in rttUsual before the detector reads anything.
			final int given = s.rttUsual.count();
			final LagDetector d = new LagDetector();
			final LagDetectorTest.Recorder out = new LagDetectorTest.Recorder();
			advanceInSteps(r, d, s, n, v, out);
			final String at = "seed " + seed + ", way " + how;
			assertTrue(at + ": " + out.log, out.opened.isEmpty());
			assertNull(at, d.open());
			for (long sec = 0; sec < n; sec++)
			{
				assertTrue(at + ": quiet @" + sec, d.quiet(sec));
			}
			// rttSession is never reset and build() puts nothing in it: it counts every RTT the detector fed.
			assertEquals(at + ": a masked second feeds no usual", 0, s.rttSession.count());
			assertEquals(at, 0, s.fpsUsual.count());
			assertTrue(at + ": rttUsual as built, or emptied by a world change",
				s.rttUsual.count() == given || s.rttUsual.count() == 0);
		}
	}

	@Test
	public void steadyTraceHasNoEvents()
	{
		final Os[] systems = Os.values();
		for (long seed = 1; seed <= SEEDS; seed++)
		{
			final Random r = new Random(seed);
			final int n = 1 + r.nextInt(Thresholds.SECONDS);
			final Trace t = Trace.steady(n).os(systems[r.nextInt(systems.length)]);
			final boolean usual = r.nextBoolean();
			if (usual)
			{
				t.usual(40);
			}
			final SettingsView v = randomSettings(r);
			final Session s = t.build();
			final LagDetector d = new LagDetector();
			final LagDetectorTest.Recorder out = new LagDetectorTest.Recorder();
			advanceInSteps(r, d, s, n, v, out);
			final String at = "seed " + seed + " (" + n + " s)";
			assertTrue(at + ": " + out.log, out.opened.isEmpty());
			assertNull(at, d.open());
			for (long sec = 0; sec < n; sec++)
			{
				assertTrue(at + ": quiet @" + sec, d.quiet(sec));
			}
			assertEquals(at + ": every steady second feeds", n, s.fpsUsual.count());
			assertEquals(at, n, s.rttSession.count());
			assertEquals(at, usual || n >= Thresholds.USUAL_MIN_SAMPLES ? 40 : -1, s.rttUsual.median());
		}
	}

	@Test
	public void shiftingTheTraceShiftsTheEvents()
	{
		for (long seed = 1; seed <= SEEDS; seed++)
		{
			final Random r = new Random(seed);
			final int n = 200 + r.nextInt(2400);
			final int k = 1 + r.nextInt(Thresholds.SECONDS - n);
			// Bound (a): the first USUAL_MIN_SAMPLES seconds untouched, or the login screen before a login.
			final boolean login = r.nextBoolean();
			final int lo = login ? 1 + r.nextInt(100) : Thresholds.USUAL_MIN_SAMPLES;
			final Space space = new Space(lo, n);
			// Bound (b): every fresh RTT is the steady 40.
			space.pingFortyOnly = true;
			// No tick is added (the ring holds every tick of a shifted trace of at most SECONDS seconds, bound (c)).
			space.fewTicksOnly = true;
			final Recipe recipe = new Recipe();
			if (login)
			{
				recipe.add(t -> t.loginAt(lo));
			}
			recipe.addAll(incidents(r, space, n / 20));
			final Trace t = recipe.on(n);
			final SettingsView v = randomSettings(r);

			final Session plain = t.build();
			final LagDetector before = new LagDetector();
			final LagDetectorTest.Recorder beforeOut = new LagDetectorTest.Recorder();
			before.advance(plain, n - 1, v, beforeOut);

			t.shift(k);
			final Session shifted = t.build();
			final LagDetector after = new LagDetector();
			final LagDetectorTest.Recorder afterOut = new LagDetectorTest.Recorder();
			after.advance(shifted, n + k - 1, v, afterOut);

			final String at = "seed " + seed + " (shift " + k + ")";
			assertFalse(at + ": a trace with incidents has events", beforeOut.opened.isEmpty());
			assertEquals(at, texts(beforeOut.opened, 0, true), texts(afterOut.opened, k, true));
			assertEquals(at, texts(beforeOut.closed, 0, true), texts(afterOut.closed, k, true));
			assertEquals(at, LagDetectorTest.describe(before.open(), 0, true),
				LagDetectorTest.describe(after.open(), k, true));
		}
	}

	@Test
	public void wrapLosesOnlyTheOldest()
	{
		for (long seed = 1; seed <= SEEDS; seed++)
		{
			final Random r = new Random(seed);
			// The wrapped trace is m seconds longer than the ring, so its ring begins at second m: the incidents
			// before m are lost to it. The first CALM_S seconds from m hold no incident.
			final int m = 200 + r.nextInt(1800);
			final Space space = new Space(0, WRAP_INCIDENTS_END);
			space.holeLo = m;
			space.holeHi = m + CALM_S;
			space.pingFortyOnly = true;
			space.fewTicksOnly = true;
			final Recipe recipe = incidents(r, space, WRAP_INCIDENTS_END / 20);
			// One freeze that only the whole trace sees, and one that both see.
			recipe.add(t -> t.frameGap(m / 2, 500, 400));
			recipe.add(t -> t.frameGap(m + CALM_S + 40, 500, 400));
			final SettingsView v = randomSettings(r);

			final Session whole = recipe.on(Thresholds.SECONDS).build();
			final LagDetector all = new LagDetector();
			final LagDetectorTest.Recorder allOut = new LagDetectorTest.Recorder();
			all.advance(whole, Thresholds.SECONDS - 1, v, allOut);

			final Session wrapped = recipe.on(Thresholds.SECONDS + m).build();
			assertEquals("seed " + seed + ": the premise", m, wrapped.seconds.tail());
			final LagDetector late = new LagDetector();
			final LagDetectorTest.Recorder lateOut = new LagDetectorTest.Recorder();
			late.advance(wrapped, Thresholds.SECONDS + m - 1, v, lateOut);

			final String at = "seed " + seed + " (the ring begins at " + m + ")";
			final List<LagEvent> seenByAll = from(allOut.closed, m + CALM_S);
			final List<LagEvent> seenLate = from(lateOut.closed, m + CALM_S);
			assertFalse(at + ": events after the calm", seenLate.isEmpty());
			assertEquals(at, texts(seenByAll, 0, false), texts(seenLate, 0, false));
			assertTrue(at + ": the whole trace has an event the wrapped ring lost",
				allOut.closed.get(0).startSec < m);
			final long offset = seenByAll.get(0).id - seenLate.get(0).id;
			assertTrue(at + ": the late detector numbers from 0, so its ids are lower", offset >= 1);
			for (int i = 0; i < seenLate.size(); i++)
			{
				assertEquals(at + ": ids move by one offset", offset, seenByAll.get(i).id - seenLate.get(i).id);
			}
			for (int i = 0; i < lateOut.opened.size(); i++)
			{
				assertEquals(at + ": the late detector counts from 0", i, lateOut.opened.get(i).id);
			}
			assertNull(at, all.open());
			assertNull(at, late.open());
		}
	}

	// ---------------------------------------------------------------- checks

	/** Events in order, never overlapping, numbered 0, 1, 2 ..., each closed by the rules of contract 6.2. */
	private static void checkOrder(String at, Session s, LagDetector d, LagDetectorTest.Recorder out, int n)
	{
		final int opened = out.opened.size();
		final int closed = out.closed.size();
		assertTrue(at + ": opened " + opened + ", closed " + closed, closed == opened || closed == opened - 1);
		assertEquals(at + ": one open event at most", closed == opened - 1, d.open() != null);
		for (int i = 0; i < out.log.size(); i++)
		{
			final String turn = i % 2 == 0 ? "opened" : "closed";
			assertTrue(at + ": opened and closed take turns", out.log.get(i).startsWith(turn));
		}
		for (int i = 0; i < opened; i++)
		{
			final LagEvent o = out.opened.get(i);
			assertEquals(at + ": ids count up from 0", i, o.id);
			assertTrue(at, o.open);
			assertEquals(at, o.startSec, o.endSec);
			assertEquals(at, s.wallMsOf(o.startSec), o.startWallMs);
		}
		for (int i = 0; i < closed; i++)
		{
			final LagEvent e = out.closed.get(i);
			final String which = at + ", event " + i + " (" + e.startSec + " .. " + e.endSec + ")";
			assertEquals(which + ": the id opened handed", out.opened.get(i).id, e.id);
			assertEquals(which, out.opened.get(i).startSec, e.startSec);
			assertFalse(which, e.open);
			assertTrue(which, e.startSec <= e.endSec);
			assertTrue(which + ": at most EVENT_MAX_S", e.lengthS() <= Thresholds.EVENT_MAX_S);
			assertEquals(which, s.wallMsOf(e.startSec), e.startWallMs);
			assertTrue(which + ": it opens on an opening trigger",
				e.first != Trigger.RTT_SPIKE || Thresholds.RTT_SPIKE_OPENS);
			assertTrue(which + ": its first trigger fired in its first second",
				(d.triggers(e.startSec) & e.first.bit()) != 0);
			assertFalse(which + ": it ends on a trigger second", d.quiet(e.endSec));
			int bits = 0;
			int quietRun = 0;
			for (long sec = e.startSec; sec <= e.endSec; sec++)
			{
				bits |= d.triggers(sec);
				quietRun = d.quiet(sec) ? quietRun + 1 : 0;
				assertTrue(which + ": no EVENT_QUIET_S quiet seconds inside it", quietRun < Thresholds.EVENT_QUIET_S);
			}
			assertEquals(which + ": its triggers are its seconds'", bits, e.triggers);
			if (e.becameCondition)
			{
				// Closed by the trigger second that would carry it past EVENT_MAX_S.
				final long crossing = e.startSec + Thresholds.EVENT_MAX_S;
				boolean found = false;
				for (long sec = e.endSec + 1; sec <= e.endSec + Thresholds.EVENT_QUIET_S && sec < n; sec++)
				{
					if (!d.quiet(sec))
					{
						assertTrue(which + ": the first trigger after it crosses EVENT_MAX_S", sec >= crossing);
						found = true;
						break;
					}
				}
				assertTrue(which + ": a condition is closed by a trigger", found);
			}
			else
			{
				for (long sec = e.endSec + 1; sec <= e.endSec + Thresholds.EVENT_QUIET_S; sec++)
				{
					assertTrue(which + ": closed by quiet seconds @" + sec, d.quiet(sec));
				}
			}
			final LagEvent next = i + 1 < opened ? out.opened.get(i + 1) : null;
			if (next == null)
			{
				continue;
			}
			assertTrue(which + ": the next starts after it", next.startSec > e.endSec);
			if (!e.becameCondition || next.first != Trigger.DISCONNECT)
			{
				assertTrue(which + ": EVENT_QUIET_S quiet seconds come before the next",
					longestQuietRun(d, e.endSec + 1, next.startSec - 1) >= Thresholds.EVENT_QUIET_S);
			}
		}
	}

	private static int longestQuietRun(LagDetector d, long from, long to)
	{
		int best = 0;
		int run = 0;
		for (long sec = from; sec <= to; sec++)
		{
			run = d.quiet(sec) ? run + 1 : 0;
			best = Math.max(best, run);
		}
		return best;
	}

	private static void assertSameUsuals(String at, Session a, Session b)
	{
		assertEquals(at, a.rttUsual.count(), b.rttUsual.count());
		assertEquals(at, a.rttUsual.median(), b.rttUsual.median());
		assertEquals(at, a.rttSession.count(), b.rttSession.count());
		assertEquals(at, a.rttSession.median(), b.rttSession.median());
		assertEquals(at, a.fpsUsual.count(), b.fpsUsual.count());
		assertEquals(at, a.fpsUsual.median(), b.fpsUsual.median());
	}

	private static List<String> texts(List<LagEvent> events, long shiftSec, boolean withId)
	{
		final List<String> out = new ArrayList<>();
		for (LagEvent e : events)
		{
			out.add(LagDetectorTest.describe(e, shiftSec, withId));
		}
		return out;
	}

	private static List<LagEvent> from(List<LagEvent> events, long startSec)
	{
		final List<LagEvent> out = new ArrayList<>();
		for (LagEvent e : events)
		{
			if (e.startSec >= startSec)
			{
				out.add(e);
			}
		}
		return out;
	}

	/** Reads seconds 0 .. n - 1 in random steps of 1 to 300 seconds. */
	private static void advanceInSteps(Random r, LagDetector d, Session s, int n, SettingsView v, DetectorListener out)
	{
		long k = -1;
		while (k < n - 1)
		{
			k = Math.min(n - 1, k + 1 + r.nextInt(300));
			d.advance(s, k, v, out);
		}
	}

	// ---------------------------------------------------------------- random traces

	/** Incidents anywhere in a trace of {@code n} seconds, of every kind, any ping, sometimes a usual and an OS. */
	private static Recipe anything(Random r, int n)
	{
		final Recipe recipe = new Recipe();
		final Os[] systems = Os.values();
		final Os os = systems[r.nextInt(systems.length)];
		recipe.add(t -> t.os(os));
		recipe.addAll(incidents(r, new Space(0, n), n / 15));
		return recipe;
	}

	/** About {@code count} random incidents in {@code space}. */
	private static Recipe incidents(Random r, Space space, int count)
	{
		final Recipe recipe = new Recipe();
		for (int i = 0; i < count + r.nextInt(10); i++)
		{
			incident(r, recipe, space);
		}
		return recipe;
	}

	private static void incident(Random r, Recipe recipe, Space sp)
	{
		final int at = sp.pick(r);
		final int top = sp.lastOfASpanFrom(at);
		final int to = Math.min(top, at + r.nextInt(12));
		switch (r.nextInt(21))
		{
			case 0:
			case 1:
			case 2:
			{
				final int end = r.nextInt(1000);
				final int ms = Math.min(20 + r.nextInt(1600), sp.longestFrameEndingIn(at, end));
				recipe.add(t -> t.frameGap(at, end, ms));
				if (r.nextBoolean())
				{
					final int pm = r.nextInt(1001);
					recipe.add(t -> t.busy(at, pm));
				}
				break;
			}
			case 3:
			{
				final int offset = r.nextInt(1000);
				final int ms = 1 + r.nextInt(500);
				final int heapAfter = 200 + r.nextInt(600);
				recipe.add(t -> t.gcPause(at, offset, ms, heapAfter));
				break;
			}
			case 4:
			{
				final int heapAfter = 200 + r.nextInt(600);
				recipe.add(t -> t.gcInferred(at, heapAfter));
				break;
			}
			case 5:
				if (sp.tickMoves)
				{
					final int late = 30 + r.nextInt(900);
					final boolean catchUp = r.nextBoolean();
					recipe.add(t -> t.tickLate(at, late, catchUp));
				}
				break;
			case 6:
				if (sp.tickMoves)
				{
					// A slow world; with fewTicksOnly never faster than one tick in TICK_MS, so no tick is added.
					final int spanTo = Math.min(top, at + 2 + r.nextInt(40));
					final int gap = (sp.fewTicksOnly ? Thresholds.TICK_MS + 10 : 350) + r.nextInt(900);
					recipe.add(t -> t.ticksEvery(at, spanTo, gap));
				}
				break;
			case 7:
				if (sp.tickMoves)
				{
					final int holeTo = Math.min(top, at + r.nextInt(3));
					recipe.add(t -> t.noTicks(at, holeTo));
				}
				break;
			case 8:
			case 9:
				ping(r, recipe, sp, at, to);
				break;
			case 10:
			{
				final int units = r.nextInt(3000);
				recipe.add(t -> t.resent(at, units));
				break;
			}
			case 11:
			{
				final int units = r.nextInt(3000);
				recipe.add(t -> t.sent(at, to, units));
				break;
			}
			case 12:
				if (sp.loads)
				{
					final int runTo = Math.min(top, at + r.nextInt(4));
					for (int sec = at; sec <= runTo; sec++)
					{
						final int s = sec;
						final int ms = 100 + r.nextInt(901);
						recipe.add(t -> t.loading(s, ms));
					}
				}
				break;
			case 13:
			{
				final int world = 300 + r.nextInt(260);
				recipe.add(t -> t.hop(at, world));
				break;
			}
			case 14:
				if (sp.disconnects)
				{
					recipe.add(t -> t.disconnect(at));
				}
				break;
			case 15:
			{
				final int focusTo = Math.min(top, at + r.nextInt(60));
				recipe.add(t -> t.unfocused(at, focusTo));
				break;
			}
			case 16:
			{
				final int fps = r.nextInt(61);
				recipe.add(t -> t.fps(at, to, fps));
				break;
			}
			case 17:
			{
				final int sys = r.nextInt(102) - 1;
				final int game = r.nextInt(102) - 1;
				final int heap = 100 + r.nextInt(700);
				final int players = r.nextInt(300);
				final int npcs = r.nextInt(300);
				final int region = r.nextInt(65536);
				recipe.add(t -> t.cpu(at, to, sys, game).heap(at, to, heap).players(at, to, players).npcs(at, to, npcs)
					.region(at, to, region));
				break;
			}
			case 18:
			{
				// A storm: a frame gap every second, often for longer than EVENT_MAX_S.
				final int stormTo = Math.min(top, at + 60 + r.nextInt(160));
				for (int sec = at; sec <= stormTo; sec++)
				{
					final int s = sec;
					final int ms = Math.min(250 + r.nextInt(300), sp.longestFrameEndingIn(s, 500));
					recipe.add(t -> t.frameGap(s, 500, ms));
				}
				break;
			}
			case 19:
				if (!sp.pingFortyOnly)
				{
					// A spike, sometimes with a click in the same second.
					final int rtt = 100 + r.nextInt(500);
					recipe.add(t -> t.rtt(at, at, rtt));
					if (r.nextBoolean())
					{
						final int sent = 900 + r.nextInt(1500);
						recipe.add(t -> t.sent(at, at, sent));
					}
				}
				break;
			default:
				if (sp.disconnects)
				{
					// A lost connection inside a lag.
					final int end = r.nextInt(1000);
					final int ms = Math.min(300 + r.nextInt(900), sp.longestFrameEndingIn(at, end));
					recipe.add(t -> t.frameGap(at, end, ms));
					recipe.add(t -> t.disconnect(to));
				}
				break;
		}
	}

	private static void ping(Random r, Recipe recipe, Space sp, int at, int to)
	{
		final int kind = r.nextInt(sp.pingFortyOnly ? 3 : 4);
		if (kind == 0)
		{
			final int ms = sp.pingFortyOnly ? 40 : 10 + r.nextInt(600);
			recipe.add(t -> t.rtt(at, to, ms));
		}
		else if (kind == 1)
		{
			final NoData[] reasons = {NoData.NOT_CONNECTED, NoData.UNSUPPORTED, NoData.ERROR};
			final NoData why = reasons[r.nextInt(reasons.length)];
			recipe.add(t -> t.rttNoData(at, to, why));
		}
		else if (kind == 2)
		{
			recipe.add(t -> t.rttStale(at, to));
		}
		else
		{
			final int usual = 20 + r.nextInt(100);
			recipe.add(t -> t.usual(usual));
		}
	}

	/** Random settings: the defaults, a GPU target, 117 HD with V-Sync, FPS Control, or the fallback memory source. */
	private static SettingsView randomSettings(Random r)
	{
		switch (r.nextInt(5))
		{
			case 1:
				return new SettingsView(Renderer.GPU, false, false, 0, false, 0, true, "OFF", r.nextInt(241), 50,
					"MSAA_2", 3, 60, 768, MemorySource.MANAGEMENT, Os.WINDOWS, "");
			case 2:
				return new SettingsView(Renderer.HD, false, false, 0, false, 0, true, "ON", 0, 90, "", 3,
					30 + r.nextInt(200), 1024, MemorySource.MANAGEMENT, Os.WINDOWS, "");
			case 3:
				return new SettingsView(Renderer.CPU, true, r.nextBoolean(), 1 + r.nextInt(60), r.nextBoolean(),
					r.nextInt(60), false, "", 0, 0, "", 0, 60, 768, MemorySource.MANAGEMENT, Os.WINDOWS, "");
			case 4:
				return new SettingsView(Renderer.CPU, false, false, 0, false, 0, false, "", 0, 0, "", 0, 60,
					r.nextBoolean() ? 0 : 256 + r.nextInt(2000), MemorySource.RUNTIME, Os.WINDOWS, "");
			default:
				return Trace.steady(1).settings();
		}
	}

	/** Where random incidents may land: {@code lo .. hi - 1}, never in {@code holeLo .. holeHi - 1}. */
	private static final class Space
	{
		final int lo;
		final int hi;
		int holeLo = Integer.MAX_VALUE;
		int holeHi = Integer.MAX_VALUE;
		/** Every fresh RTT is the steady 40 (bound (b) of the shift property). */
		boolean pingFortyOnly;
		/** No incident adds ticks: a slow world is never faster than one tick in TICK_MS. */
		boolean fewTicksOnly;
		boolean disconnects = true;
		boolean loads = true;
		boolean tickMoves = true;

		Space(int lo, int hi)
		{
			this.lo = lo;
			this.hi = hi;
		}

		int pick(Random r)
		{
			while (true)
			{
				final int at = lo + r.nextInt(hi - lo);
				if (at < holeLo || at >= holeHi)
				{
					return at;
				}
			}
		}

		/** The last second a span that starts at {@code at} may reach: before the hole, or before {@code hi}. */
		int lastOfASpanFrom(int at)
		{
			return at < holeLo ? Math.min(hi, holeLo) - 1 : hi - 1;
		}

		/** The longest frame that ends {@code end} ms into {@code at} and starts inside the space. */
		int longestFrameEndingIn(int at, int end)
		{
			final int floor = at >= holeHi ? holeHi : lo;
			return (at - floor) * 1000 + end;
		}
	}

	/** Incidents kept as steps, so the same ones can be laid on a trace of any length. */
	private static final class Recipe
	{
		private final List<Consumer<Trace>> steps = new ArrayList<>();

		void add(Consumer<Trace> step)
		{
			steps.add(step);
		}

		void addAll(Recipe other)
		{
			steps.addAll(other.steps);
		}

		Trace on(int seconds)
		{
			final Trace t = Trace.steady(seconds);
			for (Consumer<Trace> step : steps)
			{
				try
				{
					step.accept(t);
				}
				catch (IllegalArgumentException refused)
				{
					// Trace refused the step and changed nothing (a tick that is not there, a span past the end).
				}
			}
			return t;
		}
	}
}
