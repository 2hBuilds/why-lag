package com.whylag.host;

import com.whylag.core.MemorySource;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Stream;
import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * The chooser (contract 7, L7): {@code systemStats} off is the Runtime probe; on, it is the management probe; a
 * management probe that cannot be made - its module missing (a {@code LinkageError}) or refused (a
 * {@code RuntimeException}) - falls back to the Runtime probe and never throws; and a Runtime-only build is
 * {@code ManagementHostProbe.java} and ONE line of {@code HostProbes} less.
 *
 * <p>The two fallbacks are made real, not mocked: the classes of {@code com.whylag} are loaded afresh in a class
 * loader that refuses {@code com.sun.management}, as a JVM without the {@code jdk.management} module would.
 */
public class HostProbesTest
{
	private static final String MANAGEMENT_PROBE = "com.whylag.host.ManagementHostProbe";
	private static final String RUNTIME_PROBE = "com.whylag.host.RuntimeHostProbe";

	@Test
	public void systemStatsOffIsTheRuntimeProbe()
	{
		final HostProbe probe = HostProbes.create(false);
		assertTrue(probe instanceof RuntimeHostProbe);
		assertEquals(MemorySource.RUNTIME, probe.source());
	}

	@Test
	public void systemStatsOnIsTheManagementProbe()
	{
		final HostProbe probe = HostProbes.create(true);
		assertTrue(probe instanceof ManagementHostProbe);
		assertEquals(MemorySource.MANAGEMENT, probe.source());
		probe.stop();
	}

	@Test
	public void aMissingManagementModuleFallsBackToRuntime() throws Exception
	{
		assertEquals("control: the same loader with nothing refused makes the management probe", MANAGEMENT_PROBE,
			createIn(new Isolated(HostProbesTest::mainClassBytes, null)).getClass().getName());

		final Isolated missing = new Isolated(HostProbesTest::mainClassBytes, ClassNotFoundException::new);
		assertEquals("the management probe itself fails to be made", NoClassDefFoundError.class,
			failureOfNew(missing).getClass());
		final Object probe = createIn(new Isolated(HostProbesTest::mainClassBytes, ClassNotFoundException::new));
		assertEquals(RUNTIME_PROBE, probe.getClass().getName());
		assertEquals("RUNTIME", String.valueOf(probe.getClass().getMethod("source").invoke(probe)));
	}

	@Test
	public void aRefusedManagementBeanFallsBackToRuntime() throws Exception
	{
		final Function<String, Throwable> refuse = name -> new SecurityException("refused: " + name);
		assertEquals("the management probe itself fails to be made, with a RuntimeException", SecurityException.class,
			failureOfNew(new Isolated(HostProbesTest::mainClassBytes, refuse)).getClass());
		final Object probe = createIn(new Isolated(HostProbesTest::mainClassBytes, refuse));
		assertEquals(RUNTIME_PROBE, probe.getClass().getName());
		assertEquals("RUNTIME", String.valueOf(probe.getClass().getMethod("source").invoke(probe)));
	}

	/**
	 * Contract 7, L7: "Swapping the memory source out for a reviewer = delete ManagementHostProbe.java and one line of
	 * HostProbes." Exactly one line of code names the management probe; without that line the file compiles
	 * ({@code --release 11}, against the plugin's classes), and, loaded where no management probe exists, it answers
	 * the Runtime probe with {@code systemStats} on.
	 */
	@Test
	public void aRuntimeOnlyBuildIsOneLineLess() throws Exception
	{
		final Path source = pluginSource("host/HostProbes.java");
		final List<String> lines = Files.readAllLines(source, StandardCharsets.UTF_8);
		final String[] code = withoutComments(String.join("\n", lines)).split("\n", -1);
		final List<Integer> naming = new ArrayList<>();
		for (int i = 0; i < code.length; i++)
		{
			if (code[i].contains("ManagementHostProbe"))
			{
				naming.add(i);
			}
		}
		assertEquals("lines of code that name the management probe: " + naming, 1, naming.size());

		final List<String> edited = new ArrayList<>(lines);
		edited.remove((int) naming.get(0));
		final Path work = Files.createTempDirectory("whylag-runtime-only");
		try
		{
			final Path src = work.resolve("src/com/whylag/host/HostProbes.java");
			Files.createDirectories(src.getParent());
			Files.write(src, edited, StandardCharsets.UTF_8);
			final Path out = work.resolve("out");
			Files.createDirectories(out);
			final JavaCompiler javac = ToolProvider.getSystemJavaCompiler();
			assertNotNull("the tests run on a JDK", javac);
			final ByteArrayOutputStream errors = new ByteArrayOutputStream();
			final int result = javac.run(null, null, errors, "--release", "11", "-proc:none", "-d", out.toString(),
				"-classpath", mainClassesDir().toString(), src.toString());
			assertEquals("HostProbes without that line compiles:\n" + errors.toString("UTF-8"), 0, result);

			final Path compiled = out.resolve("com/whylag/host/HostProbes.class");
			assertTrue(Files.isRegularFile(compiled));
			final Isolated runtimeOnly = new Isolated(name ->
			{
				if (name.equals(MANAGEMENT_PROBE))
				{
					return null;
				}
				if (name.equals("com.whylag.host.HostProbes"))
				{
					return read(compiled);
				}
				return mainClassBytes(name);
			}, null);
			final Object probe = createIn(runtimeOnly);
			assertEquals(RUNTIME_PROBE, probe.getClass().getName());
		}
		finally
		{
			try (Stream<Path> walk = Files.walk(work))
			{
				walk.sorted(Comparator.reverseOrder()).forEach(p -> p.toFile().delete());
			}
		}
	}

	// ---------------------------------------------------------------- helpers

	/** {@code HostProbes.create(true)} in the given loader. */
	private static Object createIn(ClassLoader loader) throws Exception
	{
		final Class<?> probes = Class.forName("com.whylag.host.HostProbes", true, loader);
		final Object probe = probes.getMethod("create", boolean.class).invoke(null, true);
		probe.getClass().getMethod("stop").invoke(probe);
		return probe;
	}

	/** What {@code new ManagementHostProbe()} throws in the given loader. */
	private static Throwable failureOfNew(ClassLoader loader) throws Exception
	{
		try
		{
			Class.forName(MANAGEMENT_PROBE, true, loader).getConstructor().newInstance();
		}
		catch (InvocationTargetException e)
		{
			return e.getCause();
		}
		catch (LinkageError e)
		{
			return e;
		}
		fail("the management probe was made in a loader that refuses com.sun.management");
		return null;
	}

	/**
	 * Loads every {@code com.whylag} class afresh from {@code bytes} (null = not found), and answers
	 * {@code refuse}'s throwable for every {@code com.sun.management} class when {@code refuse} is set; all else
	 * comes from the test's own loader.
	 */
	private static final class Isolated extends ClassLoader
	{
		private final Function<String, byte[]> bytes;
		private final Function<String, Throwable> refuse;

		Isolated(Function<String, byte[]> bytes, Function<String, Throwable> refuse)
		{
			super(HostProbesTest.class.getClassLoader());
			this.bytes = bytes;
			this.refuse = refuse;
		}

		@Override
		protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException
		{
			synchronized (getClassLoadingLock(name))
			{
				if (refuse != null && name.startsWith("com.sun.management."))
				{
					final Throwable t = refuse.apply(name);
					if (t instanceof ClassNotFoundException)
					{
						throw (ClassNotFoundException) t;
					}
					throw (RuntimeException) t;
				}
				if (!name.startsWith("com.whylag."))
				{
					return super.loadClass(name, resolve);
				}
				Class<?> c = findLoadedClass(name);
				if (c == null)
				{
					final byte[] b = bytes.apply(name);
					if (b == null)
					{
						throw new ClassNotFoundException(name);
					}
					c = defineClass(name, b, 0, b.length);
				}
				if (resolve)
				{
					resolveClass(c);
				}
				return c;
			}
		}
	}

	/** The bytes of a class as the test's own loader sees it; null = none. */
	private static byte[] mainClassBytes(String name)
	{
		try (InputStream in = HostProbesTest.class.getClassLoader()
			.getResourceAsStream(name.replace('.', '/') + ".class"))
		{
			return in == null ? null : in.readAllBytes();
		}
		catch (IOException e)
		{
			throw new AssertionError(e);
		}
	}

	private static byte[] read(Path p)
	{
		try
		{
			return Files.readAllBytes(p);
		}
		catch (IOException e)
		{
			throw new AssertionError(e);
		}
	}

	/** The folder the plugin's main classes were loaded from. */
	private static Path mainClassesDir() throws URISyntaxException
	{
		final Path p = Paths.get(HostProbes.class.getProtectionDomain().getCodeSource().getLocation().toURI());
		assertTrue("the main classes are a folder: " + p, Files.isDirectory(p));
		return p;
	}

	/** A file under {@code src/main/java/com/whylag}, found above the working directory. Not finding it FAILS. */
	private static Path pluginSource(String relative)
	{
		for (Path dir = Paths.get("").toAbsolutePath(); dir != null; dir = dir.getParent())
		{
			final Path file = dir.resolve(Paths.get("src", "main", "java", "com", "whylag")).resolve(relative);
			if (Files.isRegularFile(file))
			{
				return file;
			}
		}
		fail("no src/main/java/com/whylag/" + relative + " above " + Paths.get("").toAbsolutePath());
		return null;
	}

	/** The text with every comment blanked, newline for newline, so line i of the answer is line i of the source. */
	private static String withoutComments(String s)
	{
		final StringBuilder out = new StringBuilder(s.length());
		int i = 0;
		while (i < s.length())
		{
			final char c = s.charAt(i);
			final char next = i + 1 < s.length() ? s.charAt(i + 1) : '\0';
			if (c == '/' && next == '/')
			{
				while (i < s.length() && s.charAt(i) != '\n')
				{
					out.append(' ');
					i++;
				}
			}
			else if (c == '/' && next == '*')
			{
				final int close = s.indexOf("*/", i + 2);
				final int end = close < 0 ? s.length() : close + 2;
				for (; i < end; i++)
				{
					out.append(s.charAt(i) == '\n' ? '\n' : ' ');
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

	@Test
	public void theStripperKeepsLinesAndDropsComments()
	{
		final String src = String.join("\n", "a // ManagementHostProbe", "/** ManagementHostProbe", " */ b",
			"new ManagementHostProbe();");
		final String[] code = withoutComments(src).split("\n", -1);
		assertEquals(4, code.length);
		assertEquals(Arrays.asList(false, false, false, true), Arrays.asList(code[0].contains("ManagementHostProbe"),
			code[1].contains("ManagementHostProbe"), code[2].contains("ManagementHostProbe"),
			code[3].contains("ManagementHostProbe")));
		assertTrue(code[2].contains("b"));
	}
}
