package com.whylag;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.CodeSource;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * The package rules of 2h Why Lag (contract 2, and 4 rules T8 and T14), by a scan of the shipped source under
 * {@code src/main/java/com/whylag}. Comments, string literals and char literals are blanked first, so prose that
 * NAMES a rule never trips it; a file that does not exist yet is simply absent.
 *
 * <ul>
 * <li>{@code core} reads the JDK's package TREES {@code java.lang}, {@code java.util} and {@code java.time} only -
 * those packages and every package under them, so {@code java.lang.invoke} (the rings' fences) and
 * {@code java.util.concurrent} are allowed - less two of them, {@code java.lang.management} and
 * {@code java.lang.reflect}: no RuneLite, no AWT or Swing, no management beans, no reflection, no {@code com.sun},
 * no lombok - so every rule in it can be tested without a client and moved without one.</li>
 * <li>The Plugin Hub's rule (PR #17424, "Reflection and using the management/runtime packages isn't allowed"): no
 * plugin file names the management or runtime packages or reads the JVM's host, no file uses reflection or asks for a
 * class by name or for another plugin's object, and none reads the environment. The twelve patterns of {@link #HUB}
 * each come with a sample that must bite.</li>
 * <li>No {@code Thread.sleep} anywhere (T8).</li>
 * <li>No {@code Ping.ping}, no socket, no process, no file access (T14): main code sends no packet and writes no
 * file. {@code java.io.FileDescriptor} is allowed - it is what {@code Client.getSocketFD()} answers.</li>
 * <li>{@code ui} never touches the game API.</li>
 * </ul>
 *
 * <p>No threshold literal outside {@code Thresholds} is a REVIEW rule; it is not attempted by scan.
 */
public class WhyLagGuardTest
{
	/** Names no core file may use, in an import or written out in code. */
	private static final String[] CORE_BANNED = {
		"net.runelite", "java.awt", "javax.swing", "java.lang.management", "java.lang.reflect", "javax.management",
		"com.sun", "lombok"};

	/** What a core import may start with: the three package trees, and core itself. */
	private static final String[] CORE_IMPORTS = {"java.lang.", "java.util.", "java.time.", "com.whylag.core."};

	/** The core rule, in words. */
	private static final String CORE_RULE = "core may import the java.lang, java.util and java.time package trees,"
		+ " less management and reflect (contract 2)";

	private static final Pattern IMPORT = Pattern.compile(
		"(?m)^\\s*import\\s+(?:static\\s+)?([A-Za-z_$][\\w$]*(?:\\s*\\.\\s*(?:[A-Za-z_$][\\w$]*|\\*))*)\\s*;");

	/** Sleeping a thread, with a sample each pattern must catch. */
	private static final Map<Pattern, String[]> SLEEP = new LinkedHashMap<>();

	/** Packets, sockets, processes and files (T14), each with the samples it must catch. */
	private static final Map<Pattern, String[]> NO_IO = new LinkedHashMap<>();

	/**
	 * The Plugin Hub's rule - the management and runtime packages, reflection, plugin objects and the environment -
	 * each pattern with the samples it must catch.
	 */
	private static final Map<Pattern, String[]> HUB = new LinkedHashMap<>();

	static
	{
		SLEEP.put(Pattern.compile("\\bThread\\s*\\.\\s*sleep\\b"), new String[] {"Thread.sleep(10);",
			"java.lang.Thread.sleep(1)"});
		SLEEP.put(Pattern.compile("\\bTimeUnit\\s*\\.\\s*\\w+\\s*\\.\\s*sleep\\b"),
			new String[] {"TimeUnit.SECONDS.sleep(1);"});
		SLEEP.put(Pattern.compile("\\bimport\\s+static\\s+java\\s*\\.\\s*lang\\s*\\.\\s*Thread\\s*\\.\\s*(?:sleep|\\*)"),
			new String[] {"import static java.lang.Thread.sleep;", "import static java.lang.Thread.*;"});

		NO_IO.put(Pattern.compile("\\bPing\\s*\\.\\s*ping\\b"), new String[] {"Ping.ping(world)",
			"import static net.runelite.client.plugins.worldhopper.ping.Ping.ping;"});
		NO_IO.put(Pattern.compile("\\bnew\\s+(?:[A-Za-z0-9_$.]+\\.)?Socket\\s*\\("),
			new String[] {"new Socket(host, 43594)", "new java.net.Socket()"});
		NO_IO.put(Pattern.compile("\\bDatagramSocket\\b"), new String[] {"new DatagramSocket()",
			"import java.net.DatagramSocket;"});
		NO_IO.put(Pattern.compile("\\bRuntime\\s*\\.\\s*getRuntime\\s*\\(\\s*\\)\\s*\\.\\s*exec\\s*\\("),
			new String[] {"Runtime.getRuntime().exec(\"ping\")"});
		NO_IO.put(Pattern.compile("\\bProcessBuilder\\b"), new String[] {"new ProcessBuilder(\"tracert\")"});
		NO_IO.put(Pattern.compile("\\bjava\\.io\\.(?!FileDescriptor\\b)\\w*File\\w*\\b"),
			new String[] {"java.io.FileWriter", "java.io.FileOutputStream", "java.io.FileInputStream",
				"java.io.FileReader", "java.io.RandomAccessFile", "java.io.File"});
		NO_IO.put(Pattern.compile("java\\.io\\.\\*"), new String[] {"import java.io.*;"});
		NO_IO.put(Pattern.compile("java\\.nio\\.file"), new String[] {"import java.nio.file.Files;",
			"java.nio.file.Paths.get(x)"});
		NO_IO.put(Pattern.compile("\\bFileChannel\\b"), new String[] {"FileChannel.open(p)"});

		HUB.put(Pattern.compile("java\\.lang\\.management"),
			new String[] {"import java.lang.management.ManagementFactory;", "java.lang.management.MemoryMXBean bean;"});
		HUB.put(Pattern.compile("javax\\.management"), new String[] {"import javax.management.ObjectName;",
			"new javax.management.ObjectName(n)"});
		HUB.put(Pattern.compile("com\\.sun\\."), new String[] {"import com.sun.management.OperatingSystemMXBean;",
			"(com.sun.management.GarbageCollectionNotificationInfo) x"});
		HUB.put(Pattern.compile("java\\.lang\\.reflect"), new String[] {"import java.lang.reflect.Method;",
			"java.lang.reflect.Field f;"});
		HUB.put(Pattern.compile("Runtime\\s*\\.\\s*getRuntime"), new String[] {"Runtime.getRuntime().maxMemory()",
			"Runtime . getRuntime ( ).availableProcessors()"});
		HUB.put(Pattern.compile("Class\\s*\\.\\s*forName"), new String[] {"Class.forName(name)",
			"Class . forName (\"x\")"});
		HUB.put(Pattern.compile("getDeclared\\w*\\("), new String[] {"type.getDeclaredMethods()",
			"type.getDeclaredField(\"x\")", "type.getDeclaredConstructor()"});
		HUB.put(Pattern.compile("\\.getMethod\\s*\\("), new String[] {"type.getMethod(\"run\")",
			"type.getMethod (\"run\")"});
		HUB.put(Pattern.compile("\\.invoke\\s*\\("), new String[] {"method.invoke(target)",
			"method.invoke (target, 1)"});
		HUB.put(Pattern.compile("getClass\\s*\\(\\s*\\)"), new String[] {"plugin.getClass().getName()",
			"getClass ( )"});
		HUB.put(Pattern.compile("\\bPluginManager\\b"),
			new String[] {"import net.runelite.client.plugins.PluginManager;", "private PluginManager pluginManager;"});
		HUB.put(Pattern.compile("System\\s*\\.\\s*getenv"), new String[] {"System.getenv(\"PATH\")",
			"System . getenv()"});
	}

	// ---------------------------------------------------------------- the rules

	@Test
	public void coreImportsOnlyJava() throws IOException
	{
		final Map<String, String> core = sources("core");
		assertTrue("only " + core.size() + " core files seen - wrong source root?", core.size() >= 30);
		assertTrue(core.containsKey("core/Thresholds.java"));
		final List<String> offences = new ArrayList<>();
		for (Map.Entry<String, String> e : core.entrySet())
		{
			offences.addAll(coreOffences(e.getKey(), withoutComments(e.getValue())));
		}
		assertNoOffence(CORE_RULE, offences);
	}

	/**
	 * The import rule fed four names, one import per file: a package deep inside an allowed tree passes, and the two
	 * packages taken out of the {@code java.lang} tree are offences, by import and written out in code alike.
	 */
	@Test
	public void corePackageTreesAreAllowed()
	{
		assertTrue("java.lang.invoke is in the java.lang tree", coreOffences("VarHandleUser.java",
			importing("java.lang.invoke.VarHandle")).isEmpty());
		assertTrue("java.util.concurrent.atomic is in the java.util tree", coreOffences("AtomicUser.java",
			importing("java.util.concurrent.atomic.AtomicLongArray")).isEmpty());
		assertFalse("java.lang.reflect is taken out of the java.lang tree", coreOffences("ReflectUser.java",
			importing("java.lang.reflect.Method")).isEmpty());
		assertFalse("java.lang.management is taken out of the java.lang tree", coreOffences("ManagementUser.java",
			importing("java.lang.management.ManagementFactory")).isEmpty());
		assertFalse("written out in code, not imported", coreOffences("Written.java", withoutComments(
			"package com.whylag.core;\nclass C\n{\n\tObject m = java.lang.reflect.Array.newInstance(int.class, 1);\n}\n"))
			.isEmpty());
		assertFalse("a tree outside the three is an offence", coreOffences("Io.java", importing("java.io.Serializable"))
			.isEmpty());
	}

	/**
	 * The Hub's rule: every pattern bites on each of its samples, there are exactly the twelve of the plan, and the
	 * whole of {@code src/main/java/com/whylag} is clean of all of them.
	 */
	@Test
	public void theHubsRuleIsKept() throws IOException
	{
		assertEquals("the twelve patterns of the plan", 12, HUB.size());
		provePatternsBite(HUB);
		assertNoOffence("no management or runtime package, no reflection, no plugin object, no environment"
			+ " (the Plugin Hub's rule, PR #17424)", scan(HUB));
	}

	/**
	 * The HUB patterns read code, not prose: a comment and a string that name a banned word do not trip them, and
	 * a word that only contains one does not either.
	 */
	@Test
	public void theHubsPatternsReadCodeOnly()
	{
		final String src = String.join("\n",
			"package com.whylag;",                                              // 1
			"// java.lang.management and Runtime.getRuntime() in a comment",    // 2
			"/* PluginManager, getClass() and System.getenv in a block */",     // 3
			"class C",                                                          // 4
			"{",                                                                // 5
			"\tString s = \"Class.forName and .invoke( in a string\";",        // 6
			"\tObject o = new PluginManagerLike();",                            // 7
			"\tint n = runtimeCount();",                                        // 8
			"\tvoid invokeLater() { clientThread.invokeLater(r); }",            // 9
			"}");                                                               // 10
		assertTrue("prose and lookalikes are not offences", scanOne("C.java", withoutComments(src), HUB).isEmpty());
		final String bad = "package com.whylag;\nclass C\n{\n\tObject o = plugin.getClass();\n}\n";
		assertEquals(1, scanOne("C.java", withoutComments(bad), HUB).size());
	}

	@Test
	public void noThreadSleep() throws IOException
	{
		provePatternsBite(SLEEP);
		assertNoOffence("no Thread.sleep in src/main/java/com/whylag (T8)", scan(SLEEP));
	}

	@Test
	public void noPingPingNoSocketNoFileIo() throws IOException
	{
		provePatternsBite(NO_IO);
		final Pattern file = patternOf(NO_IO, "java.io.FileWriter");
		assertFalse("FileDescriptor is allowed: it is what Client.getSocketFD() answers",
			file.matcher("java.io.FileDescriptor").find());
		assertFalse("Ping.getTCPInfo is allowed", patternOf(NO_IO, "Ping.ping(world)")
			.matcher("Ping.getTCPInfo(fd)").find());
		assertFalse("so is naming the class", patternOf(NO_IO, "Ping.ping(world)")
			.matcher("import net.runelite.client.plugins.worldhopper.ping.Ping;").find());
		assertNoOffence("main code sends no packet, opens no socket, writes no file (T14)", scan(NO_IO));
	}

	@Test
	public void uiNeverImportsTheGameApi() throws IOException
	{
		final List<String> offences = new ArrayList<>();
		for (Map.Entry<String, String> e : sources("ui").entrySet())
		{
			offences.addAll(hits(e.getKey(), withoutComments(e.getValue()), named("net.runelite.api")));
		}
		assertNoOffence("ui draws the snapshot; it never reads the game (contract 2)", offences);
	}

	/**
	 * The stripper and the name patterns on a file written to fool them: banned names in comments and strings do
	 * not count, a banned name written out in code does, and an import is read whole.
	 */
	@Test
	public void theScannerReadsCodeAndNothingElse()
	{
		final String src = String.join("\n",
			"package com.whylag.core;",                                  // 1
			"// import java.awt.Color; in a line comment",               // 2
			"/* Thread.sleep(5) in a block comment */",                  // 3
			"/** {@code net.runelite.api.Client} in javadoc */",         // 4
			"import java.util.List;",                                    // 5
			"import java.awt.Color;",                                    // 6
			"class C",                                                   // 7
			"{",                                                         // 8
			"\tString s = \"javax.swing and Ping.ping() in a string\";",  // 9
			"\tchar q = '\"';",                                           // 10
			"\tObject o = new javax.swing.JPanel();",                     // 11
			"}");                                                        // 12
		final String code = withoutComments(src);
		assertEquals("blanked, not deleted", src.length(), code.length());
		assertEquals(Collections.singletonList("C.java:6  java.awt"), hits("C.java", code, named("java.awt")));
		assertEquals(Collections.singletonList("C.java:11  javax.swing"), hits("C.java", code, named("javax.swing")));
		assertTrue(hits("C.java", code, named("net.runelite")).isEmpty());
		assertTrue(scanOne("C.java", code, SLEEP).isEmpty());
		assertTrue(scanOne("C.java", code, NO_IO).isEmpty());
		final Matcher m = IMPORT.matcher(code);
		assertTrue(m.find());
		assertEquals("java.util.List", m.group(1));
		assertTrue(m.find());
		assertEquals("java.awt.Color", m.group(1));
		assertFalse(m.find());
		assertTrue("spaces around a dot do not hide a name",
			named("net.runelite").matcher("net . runelite.api").find());
		assertFalse("a longer word is not the name", named("com.sun").matcher("com.sunshine").find());
	}

	// ---------------------------------------------------------------- the scan

	/**
	 * The core rule on one file whose comments are already blanked: every banned name written in its code, and
	 * every import that starts with none of {@link #CORE_IMPORTS}.
	 */
	private static List<String> coreOffences(String path, String code)
	{
		final List<String> offences = new ArrayList<>();
		for (String name : CORE_BANNED)
		{
			offences.addAll(hits(path, code, named(name)));
		}
		final Matcher m = IMPORT.matcher(code);
		while (m.find())
		{
			final String imported = m.group(1).replaceAll("\\s+", "");
			boolean allowed = false;
			for (String prefix : CORE_IMPORTS)
			{
				allowed |= imported.startsWith(prefix);
			}
			if (!allowed)
			{
				offences.add(path + ":" + lineOf(code, m.start(1)) + "  import " + imported);
			}
		}
		return offences;
	}

	/** A core file that imports {@code name} and nothing else, comments blanked. */
	private static String importing(String name)
	{
		return withoutComments("package com.whylag.core;\n\nimport " + name + ";\n\nclass C\n{\n}\n");
	}

	/** Each pattern catches every one of its samples - a guard that could never fire is worse than none. */
	private static void provePatternsBite(Map<Pattern, String[]> patterns)
	{
		for (Map.Entry<Pattern, String[]> e : patterns.entrySet())
		{
			for (String sample : e.getValue())
			{
				assertTrue(e.getKey() + " must catch: " + sample, e.getKey().matcher(sample).find());
			}
		}
	}

	private static Pattern patternOf(Map<Pattern, String[]> patterns, String sample)
	{
		for (Map.Entry<Pattern, String[]> e : patterns.entrySet())
		{
			for (String s : e.getValue())
			{
				if (s.equals(sample))
				{
					return e.getKey();
				}
			}
		}
		throw new AssertionError("no pattern holds the sample " + sample);
	}

	private static List<String> scan(Map<Pattern, String[]> patterns) throws IOException
	{
		final List<String> offences = new ArrayList<>();
		for (Map.Entry<String, String> e : sources("").entrySet())
		{
			offences.addAll(scanOne(e.getKey(), withoutComments(e.getValue()), patterns));
		}
		return offences;
	}

	private static List<String> scanOne(String path, String code, Map<Pattern, String[]> patterns)
	{
		final List<String> offences = new ArrayList<>();
		for (Pattern p : patterns.keySet())
		{
			offences.addAll(hits(path, code, p));
		}
		return offences;
	}

	/** A dotted name as a pattern: whole words, any spacing around the dots. */
	private static Pattern named(String dotted)
	{
		final String[] parts = dotted.split("\\.");
		final StringBuilder regex = new StringBuilder("\\b");
		for (int i = 0; i < parts.length; i++)
		{
			if (i > 0)
			{
				regex.append("\\s*\\.\\s*");
			}
			regex.append(Pattern.quote(parts[i]));
		}
		return Pattern.compile(regex.append("\\b").toString());
	}

	private static List<String> hits(String path, String code, Pattern p)
	{
		final List<String> found = new ArrayList<>();
		final Matcher m = p.matcher(code);
		while (m.find())
		{
			found.add(path + ":" + lineOf(code, m.start()) + "  " + m.group().replaceAll("\\s+", ""));
		}
		return found;
	}

	private static void assertNoOffence(String rule, List<String> offences)
	{
		if (!offences.isEmpty())
		{
			fail(rule + "\n  " + String.join("\n  ", offences));
		}
	}

	/**
	 * The same text with every comment, string literal and char literal replaced by spaces, character for
	 * character and newline for newline, so an index into the answer is an index into the source. The same four
	 * states as {@code com.lootandbeam.HubApiGuardTest}'s stripper (release 11: no text blocks in this tree).
	 */
	static String withoutComments(String source)
	{
		final int n = source.length();
		final StringBuilder out = new StringBuilder(n);
		int i = 0;
		while (i < n)
		{
			final char c = source.charAt(i);
			final char next = i + 1 < n ? source.charAt(i + 1) : '\0';
			if (c == '/' && next == '/')
			{
				while (i < n && source.charAt(i) != '\n')
				{
					out.append(' ');
					i++;
				}
			}
			else if (c == '/' && next == '*')
			{
				final int close = source.indexOf("*/", i + 2);
				final int end = close < 0 ? n : close + 2;
				for (; i < end; i++)
				{
					out.append(source.charAt(i) == '\n' ? '\n' : ' ');
				}
			}
			else if (c == '"' || c == '\'')
			{
				out.append(' ');
				i++;
				while (i < n)
				{
					final char d = source.charAt(i);
					out.append(d == '\n' ? '\n' : ' ');
					i++;
					if (d == '\\' && i < n)
					{
						out.append(source.charAt(i) == '\n' ? '\n' : ' ');
						i++;
					}
					else if (d == c || d == '\n')
					{
						break;
					}
				}
			}
			else
			{
				out.append(c);
				i++;
			}
		}
		return out.toString();
	}

	private static int lineOf(String s, int index)
	{
		int line = 1;
		for (int i = 0; i < index; i++)
		{
			if (s.charAt(i) == '\n')
			{
				line++;
			}
		}
		return line;
	}

	/**
	 * Every {@code .java} file under {@code src/main/java/com/whylag/<sub>} ("" = the whole plugin), keyed by its
	 * path below {@code com/whylag} with forward slashes. A folder that does not exist yet answers nothing.
	 */
	private static Map<String, String> sources(String sub) throws IOException
	{
		final Path plugin = pluginRoot();
		final Path dir = sub.isEmpty() ? plugin : plugin.resolve(sub);
		final Map<String, String> out = new LinkedHashMap<>();
		if (!Files.isDirectory(dir))
		{
			return out;
		}
		final List<Path> files;
		try (Stream<Path> walk = Files.walk(dir))
		{
			files = walk.filter(Files::isRegularFile)
				.filter(p -> p.getFileName().toString().endsWith(".java"))
				.sorted()
				.collect(Collectors.toList());
		}
		for (Path f : files)
		{
			out.put(plugin.relativize(f).toString().replace('\\', '/'),
				new String(Files.readAllBytes(f), StandardCharsets.UTF_8));
		}
		return out;
	}

	/** {@code src/main/java/com/whylag}, found above the working directory or this class. Not finding it FAILS. */
	private static Path pluginRoot()
	{
		final List<Path> starts = new ArrayList<>();
		starts.add(Paths.get("").toAbsolutePath());
		final Path code = codeSourceDir();
		if (code != null)
		{
			starts.add(code);
		}
		for (Path start : starts)
		{
			for (Path dir = start; dir != null; dir = dir.getParent())
			{
				final Path plugin = dir.resolve(Paths.get("src", "main", "java", "com", "whylag"));
				if (Files.isDirectory(plugin))
				{
					return plugin;
				}
			}
		}
		fail("no src/main/java/com/whylag above " + starts + " - the guard must never pass by reading nothing");
		return null;
	}

	private static Path codeSourceDir()
	{
		try
		{
			final CodeSource cs = WhyLagGuardTest.class.getProtectionDomain().getCodeSource();
			if (cs == null || cs.getLocation() == null)
			{
				return null;
			}
			final Path p = Paths.get(cs.getLocation().toURI());
			return Files.isDirectory(p) ? p : p.getParent();
		}
		catch (URISyntaxException | RuntimeException e)
		{
			return null;
		}
	}
}
