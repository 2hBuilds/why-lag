package com.whylag.core;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;

/**
 * The facts of the ten checks for the tests (1.0.1, lot B; ten since 1.0.0, the Hub's rule): one set that passes
 * every check, and, for each check, the states that make it fail with the detail it then says.
 * {@link ChecksTest} reads the table for its
 * words and the panel's tests read it for the widest verdict, so neither can drift from the other.
 */
public final class CheckFixtures
{
	/** The wall time of the jump in the clock fixture: 2026-09-28 21:47 UTC. */
	public static final long JUMP_AT = Instant.parse("2026-09-28T21:47:00Z").toEpochMilli();

	/** One way a check fails: which one, a name for the failure message, what sets it up, and what it then says. */
	public static final class Failing
	{
		public final int check;
		public final String why;
		public final Consumer<CheckFacts.Builder> setUp;
		public final String detail;

		Failing(int check, String why, Consumer<CheckFacts.Builder> setUp, String detail)
		{
			this.check = check;
			this.why = why;
			this.setUp = setUp;
			this.detail = detail;
		}

		/** The healthy facts with this failure set up. */
		public CheckFacts facts()
		{
			final CheckFacts.Builder b = healthy();
			setUp.accept(b);
			return b.build();
		}

		@Override
		public String toString()
		{
			return "C" + check + " " + why;
		}
	}

	private static final List<Failing> FAILING = build();

	private CheckFixtures()
	{
	}

	/** A run that passes all ten checks: logged in to 416, 50 fps, a steady tick, a ping of 41 ms, and so on. */
	public static CheckFacts.Builder healthy()
	{
		final CheckFacts.Builder b = new CheckFacts.Builder();
		b.loggedIn = true;
		b.world = 416;
		b.framesLast2s = 100;
		b.fps = 50;
		b.lastTickAgoMs = 350;
		b.ticksLast10s = 16;
		b.meanGapMs = 601;
		b.pingState = CheckFacts.Ping.READING;
		b.rttMs = 41;
		b.pingAgeS = 1;
		b.lastStepAgoMs = 340;
		b.stepCostUs = 120;
		b.renderer = Renderer.GPU;
		b.capKnown = true;
		b.badgeRegistered = true;
		b.badgeShow = true;
		b.clientVersion = "1.13.0";
		b.javaVersion = "17.0.18";
		b.os = "Windows 11";
		b.loggedInS = 300;
		b.cardState = Answer.SMOOTH;
		return b;
	}

	/** Every way each check fails, C1 first, in the order the tests read them. */
	public static List<Failing> failing()
	{
		return FAILING;
	}

	private static List<Failing> build()
	{
		final List<Failing> out = new ArrayList<>();
		out.add(new Failing(1, "not logged in", b -> b.loggedIn = false,
			"not logged in: nothing is measured yet"));
		out.add(new Failing(1, "no world", b -> b.world = 0, "logged in, but the world is not known yet"));

		out.add(new Failing(2, "no frames", b -> b.framesLast2s = 0, "no frames counted for 2 s"));
		out.add(new Failing(2, "no second yet", b -> b.framesLast2s = -1, "no frames counted for 2 s"));
		out.add(new Failing(2, "frame rate 0", b -> b.fps = 0, "the frame rate reads 0"));
		out.add(new Failing(2, "frame rate 1,500", b -> b.fps = 1500, "the frame rate reads 1,500"));

		out.add(new Failing(3, "no tick yet", b -> b.lastTickAgoMs = -1, "no tick has arrived yet"));
		out.add(new Failing(3, "last tick 4 s ago", b -> b.lastTickAgoMs = 4000, "no ticks for 4 s"));
		out.add(new Failing(3, "last tick 1.2 s ago", b -> b.lastTickAgoMs = 1200, "no ticks for 1 s"));
		out.add(new Failing(3, "three ticks", b -> b.ticksLast10s = 3, "only 3 ticks in 10 s"));
		out.add(new Failing(3, "gaps of 900", b -> b.meanGapMs = 900, "ticks average 900 ms"));
		out.add(new Failing(3, "gaps of 400", b -> b.meanGapMs = 400, "ticks average 400 ms"));

		out.add(new Failing(4, "not offered", b -> b.pingState = CheckFacts.Ping.UNSUPPORTED,
			"ping cannot be read on this PC"));
		out.add(new Failing(4, "error", b -> b.pingState = CheckFacts.Ping.ERROR,
			"ping cannot be read from this client"));
		out.add(new Failing(4, "stale", b -> b.pingState = CheckFacts.Ping.STALE,
			"ping is stale: nothing sent lately"));
		out.add(new Failing(4, "none yet", b -> b.pingState = CheckFacts.Ping.NONE, "no ping reading yet"));
		out.add(new Failing(4, "reading with no number", b -> b.rttMs = -1, "no ping reading yet"));
		out.add(new Failing(4, "reading 6 s old", b -> b.pingAgeS = 6, "ping reading is 6 s old"));

		out.add(new Failing(5, "no step for 5 s", b -> b.lastStepAgoMs = 5000, "the sampler has not run for 5 s"));
		out.add(new Failing(5, "a step of 2.5 ms", b -> b.stepCostUs = 2500, "sampler steps are slow: 2.5 ms"));

		out.add(new Failing(6, "renderer", b -> b.renderer = Renderer.UNKNOWN,
			"settings not read: renderer"));
		out.add(new Failing(6, "cap", b -> b.capKnown = false, "settings not read: cap"));
		out.add(new Failing(6, "both", b ->
		{
			b.renderer = Renderer.UNKNOWN;
			b.capKnown = false;
		}, "settings not read: renderer, cap"));

		out.add(new Failing(7, "not registered", b -> b.badgeRegistered = false,
			"the badge is not registered with the client"));

		out.add(new Failing(8, "client 1.12.38", b -> b.clientVersion = "1.12.38",
			"client 1.12.38 is older than 1.13.0"));
		out.add(new Failing(8, "client 0.9", b -> b.clientVersion = "0.9", "client 0.9 is older than 1.13.0"));
		out.add(new Failing(8, "Java 8", b -> b.javaVersion = "1.8.0_352",
			"Java 1.8.0_352 is older than 11"));
		out.add(new Failing(8, "system not named", b -> b.os = "", "the operating system is not named"));

		out.add(new Failing(9, "a jump of 5 s", b ->
		{
			b.clockJumpedBackS = 5;
			b.clockJumpAtWallMs = JUMP_AT;
		}, "the clock jumped back 5 s at 21:47"));
		out.add(new Failing(9, "a jump at no known time", b -> b.clockJumpedBackS = 12,
			"the clock jumped back 12 s"));

		out.add(new Failing(10, "measuring for 75 s", b ->
		{
			b.loggedInS = 75;
			b.cardState = Answer.MEASURING;
		}, "measuring for 75 s: no readings arrive"));
		return Collections.unmodifiableList(out);
	}
}
