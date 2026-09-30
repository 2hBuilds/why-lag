package com.whylag.core;

import java.io.IOException;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * The diagnostics memory of 1.0.1 (lot A, A2): a ring of the newest 200 notes printed {@code hh:mm:ss  text}; a key
 * that is new once and counted every time; the last ten DISTINCT errors, each with three frames and a count; a
 * scrub that takes every file path out of every text that comes in; and the text in its three sections. The clock is
 * a parameter and the zone is fixed, so every stamp is a known one.
 */
public class DiagnosticsTest
{
	/** 2026-09-30T14:05:09Z. */
	private static final long T0 = Instant.parse("2026-09-30T14:05:09Z").toEpochMilli();

	private final Diagnostics d = new Diagnostics(ZoneOffset.UTC);

	// ---------------------------------------------------------------- the notes

	@Test
	public void aNoteIsStampedHoursMinutesSecondsThenTwoSpacesThenTheText()
	{
		d.note(T0, "plugin started, version 1.0.1, client 1.13.0, Windows 11");

		assertEquals(Arrays.asList("14:05:09  plugin started, version 1.0.1, client 1.13.0, Windows 11"),
			section(d.text(), "Notes"));
	}

	@Test
	public void theStampFollowsTheZone()
	{
		final Diagnostics tokyo = new Diagnostics(ZoneId.of("Asia/Tokyo"));

		tokyo.note(T0, "x");

		assertEquals(Arrays.asList("23:05:09  x"), section(tokyo.text(), "Notes"));
	}

	/** Two hundred and fifty notes in, the newest two hundred out, oldest first: note 51 to note 250. */
	@Test
	public void theRingKeepsTheNewestTwoHundredOfTwoHundredFiftyNotes()
	{
		assertEquals(200, Diagnostics.NOTES);
		for (int i = 1; i <= 250; i++)
		{
			d.note(T0 + i * 1000L, "note " + i);
		}

		final List<String> notes = section(d.text(), "Notes");

		assertEquals(200, notes.size());
		assertEquals("the oldest fifty are gone", "14:06:00  note 51", notes.get(0));
		assertEquals("14:09:19  note 250", notes.get(199));
		for (int i = 0; i < notes.size(); i++)
		{
			assertTrue("in order: " + notes.get(i), notes.get(i).endsWith("  note " + (51 + i)));
		}
	}

	@Test
	public void aLineBreakInANoteNeverBreaksTheText()
	{
		d.note(T0, "a\nb\r\nc\rd");

		assertEquals(Arrays.asList("14:05:09  a b c d"), section(d.text(), "Notes"));
	}

	// ---------------------------------------------------------------- the warnings

	@Test
	public void aKeyIsNewOnceAndCountedEveryTime()
	{
		assertEquals(0, d.warningCount("step: IllegalStateException"));

		assertTrue(d.warnOnce("step: IllegalStateException", "boom"));
		assertFalse(d.warnOnce("step: IllegalStateException", "boom"));
		assertFalse(d.warnOnce("step: IllegalStateException", "boom"));
		assertTrue("another key is its own", d.warnOnce("probe", "no beans"));

		assertEquals(3, d.warningCount("step: IllegalStateException"));
		assertEquals(1, d.warningCount("probe"));
		assertEquals(Arrays.asList("step: IllegalStateException  3 times  boom", "probe  1 time  no beans"),
			section(d.text(), "Warnings"));
	}

	@Test
	public void aWarningKeepsTheTextOfItsFirstTime()
	{
		d.warnOnce("k", "first");
		d.warnOnce("k", "second");

		assertEquals(Arrays.asList("k  2 times  first"), section(d.text(), "Warnings"));
	}

	// ---------------------------------------------------------------- the errors

	/** Eleven distinct errors, the ten newest kept; a repeat bumps its count and keeps its place. */
	@Test
	public void tenDistinctErrorsOfElevenAreKeptAndTheRepeatIsCounted()
	{
		assertEquals(10, Diagnostics.ERRORS);
		for (int i = 1; i <= 11; i++)
		{
			d.error(T0 + i * 1000L, new IllegalStateException("message " + i));
		}
		d.error(T0 + 12_000L, new IllegalStateException("message 5"));

		final List<String> errors = errorLines(d.text());

		assertEquals("the first error is gone, ten are kept", 10, errors.size());
		assertEquals("14:05:11  java.lang.IllegalStateException: message 2", errors.get(0));
		assertEquals("a repeat keeps its place and its first time, and counts",
			"14:05:14  java.lang.IllegalStateException: message 5  (2 times)", errors.get(3));
		assertEquals("14:05:20  java.lang.IllegalStateException: message 11", errors.get(9));
		assertFalse(d.text().contains("message 1\n"));
	}

	@Test
	public void theSameClassWithAnotherMessageIsAnotherError()
	{
		d.error(T0, new IllegalStateException("a"));
		d.error(T0, new IllegalArgumentException("a"));
		d.error(T0, new IllegalStateException("b"));
		d.error(T0, new IllegalStateException("a"));

		final List<String> errors = errorLines(d.text());

		assertEquals(3, errors.size());
		assertEquals("14:05:09  java.lang.IllegalStateException: a  (2 times)", errors.get(0));
	}

	/** An error keeps its class, its message and its first three frames, and no more. */
	@Test
	public void anErrorKeepsItsFirstThreeFrames()
	{
		assertEquals(3, Diagnostics.FRAMES);
		final IllegalStateException boom = new IllegalStateException("the probe fails");
		boom.setStackTrace(new StackTraceElement[] {
			new StackTraceElement("a.A", "one", "A.java", 1), new StackTraceElement("b.B", "two", "B.java", 2),
			new StackTraceElement("c.C", "three", "C.java", 3), new StackTraceElement("d.D", "four", "D.java", 4)});

		d.error(T0, boom);

		assertEquals(Arrays.asList("14:05:09  java.lang.IllegalStateException: the probe fails",
			"    at a.A.one(A.java:1)", "    at b.B.two(B.java:2)", "    at c.C.three(C.java:3)"),
			section(d.text(), "Errors"));
	}

	@Test
	public void anErrorWithNoMessageIsItsClassAlone()
	{
		d.error(T0, new NoClassDefFoundError());

		assertTrue(errorLines(d.text()).get(0), errorLines(d.text()).get(0).endsWith("  java.lang.NoClassDefFoundError"));
	}

	// ---------------------------------------------------------------- the scrub

	@Test
	public void scrubTakesEveryKindOfPathOut()
	{
		assertEquals("<path>", Diagnostics.scrub("C:\\Users\\john\\x.txt"));
		assertEquals("<path>", Diagnostics.scrub("/home/john/x"));
		assertEquals("<path>", Diagnostics.scrub("/Users/john/x"));
		assertEquals("<path>", Diagnostics.scrub("/tmp/whylag/x.txt"));
		assertEquals("a path before words", "<path> (Access is denied)",
			Diagnostics.scrub("C:\\Users\\John Smith\\AppData\\x.txt (Access is denied)"));
		assertEquals("a path inside words", "cannot open <path> now",
			Diagnostics.scrub("cannot open C:\\Users\\john\\x.txt now"));
		assertEquals("a name with a space after a drive-less Users\\", "<path>",
			Diagnostics.scrub("Users\\John Smith\\x"));
		assertEquals("a network path", "<path>", Diagnostics.scrub("\\\\host\\share\\x.txt"));
		assertEquals("a Unix path of two parts", "read <path> failed", Diagnostics.scrub("read /var/log failed"));
		assertEquals("a file URL", "file://<path>", Diagnostics.scrub("file:///home/john/x"));
		assertEquals("a forward-slash drive path", "<path>", Diagnostics.scrub("C:/Users/john/x.txt"));
	}

	@Test
	public void scrubLeavesWordsAndNumbersAlone()
	{
		for (String plain : new String[] {"world 416", "hop to world 302", "fps 48/50/51", "and/or", "a / b",
			"https://example.com/a/b", "ticks 1,240 ms, ping 41 ms", "lag closed after 14 s: World lag - not you, Likely",
			"plugin started, version 1.0.1, client 1.13.0, Windows 11"})
		{
			assertEquals(plain, Diagnostics.scrub(plain));
		}
		assertEquals("", Diagnostics.scrub(null));
		assertEquals("", Diagnostics.scrub(""));
	}

	/** A message that is only a path: the error prints the class and "<path>", and nothing of the path. */
	@Test
	public void anExceptionMessageThatIsOnlyAPathLeavesNothingOfIt()
	{
		d.error(T0, new IOException("C:\\Users\\john\\AppData\\Roaming\\x"));
		d.error(T0, new IOException("/home/john/x"));

		final String text = d.text();

		assertTrue(text, text.contains("java.io.IOException: <path>"));
		assertFalse(text, text.contains("john"));
		assertFalse(text, text.contains("AppData"));
		assertEquals("both are one error: the same class and the same scrubbed message", 1,
			errorLines(text).size());
		assertTrue(errorLines(text).get(0).endsWith("  (2 times)"));
	}

	/** Every door a text comes in by is scrubbed: a note, a warning's key and text, an error's message. */
	@Test
	public void everyEntryPointIsScrubbed()
	{
		d.note(T0, "cannot open C:\\Users\\john\\x.txt");
		d.warnOnce("key /home/john/x", "text /Users/john/y");
		d.error(T0, new IllegalStateException("bad C:\\Users\\john\\z"));

		final String text = d.text();

		assertFalse(text, text.contains("john"));
		assertFalse(text, text.contains("Users"));
		assertEquals(4, count(text, "<path>"));
	}

	// ---------------------------------------------------------------- the text

	@Test
	public void theTextIsNotesThenWarningsThenErrorsWithABlankLineBetween()
	{
		d.note(T0, "n");
		d.warnOnce("w", "t");
		d.error(T0, new IllegalStateException("e"));

		final String[] lines = d.text().split("\n", -1);

		assertEquals(Arrays.asList("Notes", "14:05:09  n", "", "Warnings", "w  1 time  t", "", "Errors"),
			Arrays.asList(lines).subList(0, 7));
		assertTrue(lines[7], lines[7].startsWith("14:05:09  java.lang.IllegalStateException: e"));
		assertTrue("ends with a new line", d.text().endsWith("\n"));
	}

	@Test
	public void aSectionWithNothingInItSaysNone()
	{
		assertEquals("Notes\n(none)\n\nWarnings\n(none)\n\nErrors\n(none)\n", d.text());
		assertEquals(d.text(), Diagnostics.empty());
		assertEquals("(none)", Diagnostics.NONE);
	}

	// ---------------------------------------------------------------- threads

	/** The sampler writes while the report reads: no exception, and the ring is still 200. */
	@Test
	public void itIsSafeToReadWhileTheOtherThreadWrites() throws Exception
	{
		final AtomicReference<Throwable> failed = new AtomicReference<>();
		final Thread writer = new Thread(() ->
		{
			try
			{
				for (int i = 0; i < 2000; i++)
				{
					d.note(T0, "note " + i);
					d.warnOnce("k" + i % 7, "t");
					d.error(T0, new IllegalStateException("e" + i % 13));
				}
			}
			catch (Throwable t)
			{
				failed.set(t);
			}
		}, "test-writer");
		writer.start();
		for (int i = 0; i < 200; i++)
		{
			assertTrue(d.text().startsWith("Notes\n"));
		}
		writer.join(20_000);
		assertFalse(writer.isAlive());
		assertEquals(null, failed.get());
		assertEquals(200, section(d.text(), "Notes").size());
		assertEquals(10, errorLines(d.text()).size());
	}

	// ---------------------------------------------------------------- helpers

	/** The lines under one heading, up to the blank line or the end. */
	private static List<String> section(String text, String heading)
	{
		final String[] lines = text.split("\n", -1);
		final List<String> out = new ArrayList<>();
		boolean in = false;
		for (String line : lines)
		{
			if (!in)
			{
				in = line.equals(heading);
			}
			else if (line.isEmpty())
			{
				break;
			}
			else
			{
				out.add(line);
			}
		}
		return out;
	}

	/** The first line of each error: the ones that start with a clock. */
	private static List<String> errorLines(String text)
	{
		final List<String> out = new ArrayList<>();
		for (String line : section(text, "Errors"))
		{
			if (!line.startsWith("    at "))
			{
				out.add(line);
			}
		}
		return out;
	}

	private static int count(String text, String part)
	{
		int n = 0;
		for (int at = text.indexOf(part); at >= 0; at = text.indexOf(part, at + 1))
		{
			n++;
		}
		return n;
	}
}
