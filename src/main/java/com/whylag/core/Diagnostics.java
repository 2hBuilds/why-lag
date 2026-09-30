package com.whylag.core;

import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * A short memory of what the plugin has been doing, for one purpose: the report's last three sections are made from
 * it, and a player pastes the report into a bug post. It exists because the plugin's log lines are few and a
 * player's client log is rarely to hand; a report that says "ping: could not read it" at 21:47:02 saves a round of
 * questions. Pure: the clock comes in as a parameter ({@code wallMs}) and the zone at construction, so every stamp
 * in a test is a known one.
 *
 * <p><b>What it holds.</b> A ring of the last {@value #NOTES} notes, each stamped {@code hh:mm:ss} in the zone and
 * printed {@code hh:mm:ss  text}; the warning keys raised this session with how often ({@link #warnOnce}), which is
 * also what keeps the client log to one WARN line per kind of trouble; and the last {@value #ERRORS} DISTINCT errors
 * (the class and the message), each with its first {@value #FRAMES} stack frames and how many times it came.
 * <b>What it never holds:</b> a player name, an account hash, a host, an address or a file path. The first four are a
 * rule for the CALLERS, who hand it states, counts and times only - the plugin never reads a name or a hash, so there
 * is none to hand over; the class cannot tell a name from any other word. The path rule is kept here as well: every
 * text that comes in passes {@link #scrub}, because an exception message from the disk is very often the path
 * itself, and a path holds the Windows user name.
 *
 * <p>Thread-safe: the sampler thread writes and the report reads. Every method is one short critical section, so
 * none of them can hold the other up.
 *
 * <p>
 * Choice: a line break in a text becomes a space, so a note, a warning and an error message stay one line each.
 * <br>
 * Choice: a repeat of an error only bumps its count; it keeps its place and its first time, so the ring drops the
 * error that came FIRST, not the one that came last.
 * <br>
 * Choice: two errors are the same when their class and their SCRUBBED message are equal, so one failing path that
 * differs in its folder only is one error counted, not ten.
 * <br>
 * Choice: {@link #text()} prints the errors oldest first, as it prints the notes, so the report reads as one timeline.
 * <br>
 * Choice: {@link #warnOnce} keeps the text of the FIRST time a key was raised and prints it after the count.
 * <br>
 * Choice: a section with nothing in it prints "(none)", never a bare heading.
 */
public final class Diagnostics
{
	/** The most notes kept; the oldest goes when a new one arrives. */
	public static final int NOTES = 200;
	/** The most distinct errors kept; the one that came first goes when an eleventh arrives. */
	public static final int ERRORS = 10;
	/** The most stack frames kept of an error. */
	public static final int FRAMES = 3;
	/** What a section with nothing in it prints. */
	public static final String NONE = "(none)";
	/** What a scrubbed path prints. */
	public static final String PATH = "<path>";

	/** A Windows path: a drive, then directories (which may hold spaces) and a last component (which may not). */
	private static final Pattern WINDOWS_PATH = Pattern.compile(
		"\\b[A-Za-z]:[\\\\/](?:[^\\\\/:\"'<>|\\r\\n]+[\\\\/])*[^\\s\\\\/:\"'<>|,;)\\]]*");
	/** A network path: two backslashes and whatever follows up to a space. */
	private static final Pattern UNC_PATH = Pattern.compile("\\\\\\\\[^\\s\"'<>|,;)\\]]+");
	/** The three home roots of the systems the client runs on: everything after the root is the path. */
	private static final Pattern HOME_PATH = Pattern.compile("(?i)(?<![\\w:.~-])/(?:home|Users|tmp)/[^\\r\\n]*");
	/** What is left of a path that lost its drive: {@code Users\}, and any run of characters after it. */
	private static final Pattern USERS_REST = Pattern.compile("(?i)Users[\\\\/][^\\r\\n]*");
	/** A Unix path of at least two components, not the tail of a URL or of a word such as "and/or". */
	private static final Pattern UNIX_PATH = Pattern.compile(
		"(?<![\\w/:.~-])/(?:[^/\\s\"'<>|:]+/)+[^\\s/\"'<>|:,;)\\]]*");

	/** One warning key: how often it was raised and the text of the first time. */
	private static final class Warning
	{
		final String text;
		int count = 1;

		Warning(String text)
		{
			this.text = text;
		}
	}

	/** One kept error: when it first came, its class and message, its first frames, and how often it came. */
	private static final class Err
	{
		final String time;
		final String type;
		final String message;
		final List<String> frames;
		int count = 1;

		Err(String time, String type, String message, List<String> frames)
		{
			this.time = time;
			this.type = type;
			this.message = message;
			this.frames = frames;
		}
	}

	private final ZoneId zone;
	private final Object lock = new Object();
	private final Deque<String> notes = new ArrayDeque<>();
	/** Warning key to its record, in the order first raised. */
	private final Map<String, Warning> warnings = new LinkedHashMap<>();
	/** Distinct errors by class and message, first seen first. */
	private final Map<String, Err> errors = new LinkedHashMap<>();

	/** @param zone the zone the notes' times are written in */
	public Diagnostics(ZoneId zone)
	{
		this.zone = zone;
	}

	/**
	 * Adds one note, stamped {@code hh:mm:ss} in the zone. The oldest is dropped once there are {@value #NOTES}.
	 *
	 * @param wallMs the wall clock, in epoch milliseconds
	 */
	public void note(long wallMs, String text)
	{
		final String line = Fmt.clockSeconds(wallMs, zone) + "  " + clean(text);
		synchronized (lock)
		{
			notes.addLast(line);
			while (notes.size() > NOTES)
			{
				notes.removeFirst();
			}
		}
	}

	/**
	 * Whether {@code key} is being raised for the FIRST time this session: true once, false ever after. Every call
	 * counts, so the report shows how often each kind of trouble came back. The plugin logs at WARN only when this
	 * answers true.
	 *
	 * @param key  a short kind of trouble, such as {@code step: IllegalStateException} - no names, no hashes
	 * @param text what it was, kept from the first time
	 */
	public boolean warnOnce(String key, String text)
	{
		final String k = clean(key);
		synchronized (lock)
		{
			final Warning before = warnings.get(k);
			if (before != null)
			{
				before.count++;
				return false;
			}
			warnings.put(k, new Warning(clean(text)));
			return true;
		}
	}

	/** How often {@code key} was raised this session; 0 when it never was. */
	public int warningCount(String key)
	{
		synchronized (lock)
		{
			final Warning w = warnings.get(clean(key));
			return w == null ? 0 : w.count;
		}
	}

	/**
	 * Keeps {@code t}: its class, its message and its first {@value #FRAMES} stack frames. The last {@value #ERRORS}
	 * DISTINCT errors are kept, distinct by class and message; the same error again only counts up.
	 *
	 * @param wallMs the wall clock, in epoch milliseconds
	 */
	public void error(long wallMs, Throwable t)
	{
		final String message = clean(t.getMessage());
		final String type = kind(t);
		final String key = type + ": " + message;
		final StackTraceElement[] trace = t.getStackTrace();
		final List<String> frames = new ArrayList<>();
		for (int i = 0; i < trace.length && i < FRAMES; i++)
		{
			frames.add(clean(trace[i].toString()));
		}
		final String time = Fmt.clockSeconds(wallMs, zone);
		synchronized (lock)
		{
			final Err before = errors.get(key);
			if (before != null)
			{
				before.count++;
				return;
			}
			errors.put(key, new Err(time, type, message, frames));
			while (errors.size() > ERRORS)
			{
				final Iterator<String> oldest = errors.keySet().iterator();
				oldest.next();
				oldest.remove();
			}
		}
	}

	/**
	 * The kind of a throwable: its class name, read as the text of {@code toString()} up to the first ':' (a message
	 * may hold more colons, a class name never does). The plugin keys its one warning per kind by it.
	 */
	public static String kind(Throwable t)
	{
		final String text = String.valueOf(t);
		final int colon = text.indexOf(':');
		return colon < 0 ? text : text.substring(0, colon);
	}

	/**
	 * The three sections, each a heading and its lines, a blank line between them, every line ended with a new
	 * line: {@code Notes} (oldest first, {@code hh:mm:ss  text}), {@code Warnings} ({@code key  3 times  text}) and
	 * {@code Errors} ({@code hh:mm:ss  class: message}, its count when above 1, and its frames, one under the other).
	 * A section with nothing in it says {@value #NONE}.
	 */
	public String text()
	{
		final StringBuilder out = new StringBuilder(2048);
		synchronized (lock)
		{
			out.append("Notes\n");
			if (notes.isEmpty())
			{
				out.append(NONE).append('\n');
			}
			for (String line : notes)
			{
				out.append(line).append('\n');
			}
			out.append("\nWarnings\n");
			if (warnings.isEmpty())
			{
				out.append(NONE).append('\n');
			}
			for (Map.Entry<String, Warning> e : warnings.entrySet())
			{
				final Warning w = e.getValue();
				out.append(e.getKey()).append("  ").append(w.count).append(w.count == 1 ? " time" : " times");
				if (!w.text.isEmpty())
				{
					out.append("  ").append(w.text);
				}
				out.append('\n');
			}
			out.append("\nErrors\n");
			if (errors.isEmpty())
			{
				out.append(NONE).append('\n');
			}
			for (Err e : errors.values())
			{
				out.append(e.time).append("  ").append(e.type);
				if (!e.message.isEmpty())
				{
					out.append(": ").append(e.message);
				}
				if (e.count > 1)
				{
					out.append("  (").append(e.count).append(" times)");
				}
				out.append('\n');
				for (String frame : e.frames)
				{
					out.append("    at ").append(frame).append('\n');
				}
			}
		}
		return out.toString();
	}

	/** The text of a diagnostics log with nothing in it: the three headings and {@value #NONE} under each. */
	public static String empty()
	{
		return new Diagnostics(ZoneOffset.UTC).text();
	}

	/**
	 * {@code text} with every file path in it replaced by {@code <path>}: a Windows path ({@code X:\...}, the folders
	 * of it may hold spaces), a network path, anything from {@code /home/}, {@code /Users/} or {@code /tmp/} to the
	 * end of the line, anything from {@code Users\} to the end of the line, and a Unix path of two components or
	 * more. A URL and a word such as "and/or" are left alone. An exception from the disk names the file it failed on,
	 * and a file lives under the player's home folder, which is named after them.
	 */
	public static String scrub(String text)
	{
		if (text == null || text.isEmpty())
		{
			return "";
		}
		String out = WINDOWS_PATH.matcher(text).replaceAll(PATH);
		out = UNC_PATH.matcher(out).replaceAll(PATH);
		out = HOME_PATH.matcher(out).replaceAll(PATH);
		out = USERS_REST.matcher(out).replaceAll(PATH);
		return UNIX_PATH.matcher(out).replaceAll(PATH);
	}

	/** One line with no path in it: a line break becomes a space and {@link #scrub} runs. */
	private static String clean(String text)
	{
		return text == null ? "" : scrub(text.replace("\r\n", " ").replace('\n', ' ').replace('\r', ' '));
	}
}
