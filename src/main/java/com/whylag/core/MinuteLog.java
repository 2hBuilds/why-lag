package com.whylag.core;

import java.time.ZoneId;
import java.util.ArrayDeque;
import java.util.Deque;

/**
 * The last {@value #LINES} {@link MinuteLine}s, oldest first: an hour of the session, one line a minute, for the
 * report's "Last 60 minutes". The sampler thread adds; the report reads. Every method is one short critical section.
 *
 * <p>
 * Choice: the ring drops the OLDEST line when a sixty-first arrives; nothing else ever removes one.
 * <br>
 * Choice: {@link #text()} is the lines only, each ended with a new line, and "" while there are none: the heading
 * and the "(none)" belong to the report, which also owns the blank line before it.
 */
public final class MinuteLog
{
	/** The most lines kept: an hour of minutes. */
	public static final int LINES = 60;

	private final ZoneId zone;
	private final Object lock = new Object();
	private final Deque<MinuteLine> lines = new ArrayDeque<>();

	/** @param zone the zone the lines' clocks are written in */
	public MinuteLog(ZoneId zone)
	{
		this.zone = zone;
	}

	/** Adds one minute; the oldest goes once there are {@value #LINES}. A null line is ignored. */
	public void add(MinuteLine line)
	{
		if (line == null)
		{
			return;
		}
		synchronized (lock)
		{
			lines.addLast(line);
			while (lines.size() > LINES)
			{
				lines.removeFirst();
			}
		}
	}

	/** How many minutes are held, 0 to {@value #LINES}. */
	public int size()
	{
		synchronized (lock)
		{
			return lines.size();
		}
	}

	/** The lines as {@link MinuteLine#text} prints them, oldest first, each ended with a new line; "" when none. */
	public String text()
	{
		final StringBuilder out = new StringBuilder(96 * LINES);
		synchronized (lock)
		{
			for (MinuteLine line : lines)
			{
				out.append(line.text(zone)).append('\n');
			}
		}
		return out.toString();
	}
}
