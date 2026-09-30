package com.whylag.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * The session's events, oldest first, at most {@code capacity} of them (contract 3.4). Every method is
 * synchronized.
 *
 * <p><b>One writer.</b> The engine alone writes it: {@link #add} when the detector opens an event, {@link #replace}
 * when it closes (with its verdict attached). An open event is held with {@code open == true}, and every reader of
 * CLOSED events - {@link #between}, {@link #last}, {@link #sessionTotal}, {@link #sessionCount} - skips it.
 *
 * <p><b>The session counts outlive the cap.</b> When the log is full the oldest event is dropped, but it stays
 * counted: the counts are of every closed event this session, by its {@link LagEvent#group()}.
 */
public final class EventLog
{
	private final LagEvent[] held;
	private final int[] counts = new int[Group.values().length];
	/** Index in {@link #held} of the oldest held event. */
	private int first;
	private int size;
	private int total;

	/**
	 * @param capacity events held, at least 1
	 */
	public EventLog(int capacity)
	{
		if (capacity < 1)
		{
			throw new IllegalArgumentException("an event log needs room for one event, got " + capacity);
		}
		held = new LagEvent[capacity];
	}

	/** Appends {@code e}, dropping the oldest when full (its count is kept). A closed {@code e} is counted. */
	public synchronized void add(LagEvent e)
	{
		Objects.requireNonNull(e, "event");
		if (size == held.length)
		{
			held[first] = null;
			first = (first + 1) % held.length;
			size--;
		}
		held[(first + size) % held.length] = e;
		size++;
		count(e, 1);
	}

	/**
	 * Swaps the held event with {@code e}'s id for {@code e}, and re-counts: the old one's group loses it if it was
	 * closed, {@code e}'s group gains it if {@code e} is closed.
	 *
	 * @return false when no held event has that id (nothing changes)
	 */
	public synchronized boolean replace(LagEvent e)
	{
		Objects.requireNonNull(e, "event");
		for (int k = size - 1; k >= 0; k--)
		{
			final int i = (first + k) % held.length;
			if (held[i].id == e.id)
			{
				count(held[i], -1);
				held[i] = e;
				count(e, 1);
				return true;
			}
		}
		return false;
	}

	/** Events held, open ones included. */
	public synchronized int size()
	{
		return size;
	}

	/** The {@code i}-th event held; 0 is the oldest. */
	public synchronized LagEvent get(int i)
	{
		if (i < 0 || i >= size)
		{
			throw new IndexOutOfBoundsException("event " + i + " of " + size);
		}
		return held[(first + i) % held.length];
	}

	/** The held event with this id, or null. */
	public synchronized LagEvent byId(long id)
	{
		for (int k = size - 1; k >= 0; k--)
		{
			final LagEvent e = held[(first + k) % held.length];
			if (e.id == id)
			{
				return e;
			}
		}
		return null;
	}

	/** Every held event, open ones included, oldest first; unmodifiable. */
	public synchronized List<LagEvent> copy()
	{
		final List<LagEvent> out = new ArrayList<>(size);
		for (int k = 0; k < size; k++)
		{
			out.add(held[(first + k) % held.length]);
		}
		return Collections.unmodifiableList(out);
	}

	/** The CLOSED events overlapping {@code fromSec .. toSec} (both included), oldest first; unmodifiable. */
	public synchronized List<LagEvent> between(long fromSec, long toSec)
	{
		final List<LagEvent> out = new ArrayList<>();
		for (int k = 0; k < size; k++)
		{
			final LagEvent e = held[(first + k) % held.length];
			if (!e.open && e.startSec <= toSec && e.endSec >= fromSec)
			{
				out.add(e);
			}
		}
		return Collections.unmodifiableList(out);
	}

	/** Closed events this session, dropped ones included. */
	public synchronized int sessionTotal()
	{
		return total;
	}

	/** Closed events this session counted under {@code g}, dropped ones included. */
	public synchronized int sessionCount(Group g)
	{
		return counts[g.ordinal()];
	}

	/** The newest CLOSED event, or null. */
	public synchronized LagEvent last()
	{
		for (int k = size - 1; k >= 0; k--)
		{
			final LagEvent e = held[(first + k) % held.length];
			if (!e.open)
			{
				return e;
			}
		}
		return null;
	}

	private void count(LagEvent e, int step)
	{
		if (!e.open)
		{
			total += step;
			counts[e.group().ordinal()] += step;
		}
	}
}
