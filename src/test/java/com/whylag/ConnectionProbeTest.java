package com.whylag;

import com.whylag.core.NoData;
import java.io.FileDescriptor;
import java.lang.reflect.InaccessibleObjectException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.function.Supplier;
import net.runelite.client.plugins.worldhopper.ping.Ping;
import net.runelite.client.plugins.worldhopper.ping.TCPInfo;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * The connection probe (contract 3.11, 7 L7; T13): one test per outcome - not logged in, no socket, no TCPInfo, a
 * LinkageError, a RuntimeException, a reading - and {@code neverThrows}, which throws every kind of failure from
 * every point the probe calls. A read without data never hands on an earlier read's numbers.
 */
public class ConnectionProbeTest
{
	private static final FileDescriptor SOCKET = new FileDescriptor();

	/** The five points the probe calls, in its order. */
	private enum Point
	{
		SOCKET, INFO, RTT, SENT, RESENT
	}

	@Test
	public void notLoggedInIsNotLoggedIn()
	{
		final AtomicInteger asked = new AtomicInteger();
		final ConnectionProbe probe = new ConnectionProbe(() ->
		{
			asked.incrementAndGet();
			return SOCKET;
		}, fd -> tcp(40_000, 900, 0));
		final ConnSample out = reading();
		probe.read(false, out);
		assertNoData(NoData.NOT_LOGGED_IN, out);
		assertEquals("the socket is not asked while logged out", 0, asked.get());
	}

	@Test
	public void noSocketIsNotConnected()
	{
		final AtomicInteger infos = new AtomicInteger();
		final ConnSample out = reading();
		new ConnectionProbe(() -> null, fd ->
		{
			infos.incrementAndGet();
			return tcp(40_000, 900, 0);
		}).read(true, out);
		assertNoData(NoData.NOT_CONNECTED, out);
		assertEquals("no socket, no TCP numbers asked", 0, infos.get());
	}

	@Test
	public void noTcpInfoIsUnsupported()
	{
		final ConnSample out = reading();
		new ConnectionProbe(() -> SOCKET, fd -> null).read(true, out);
		assertNoData(NoData.UNSUPPORTED, out);
	}

	@Test
	public void aLinkageErrorIsUnsupported()
	{
		for (Point at : Point.values())
		{
			for (LinkageError e : new LinkageError[] {new NoClassDefFoundError("com/sun/jna/Native"),
				new UnsatisfiedLinkError("WSAIoctl"), new NoSuchMethodError("getSocketFD")})
			{
				final ConnSample out = reading();
				failingAt(at, e).read(true, out);
				assertNoData(e + " at " + at, NoData.UNSUPPORTED, out);
			}
		}
	}

	@Test
	public void aRuntimeExceptionIsError()
	{
		for (Point at : Point.values())
		{
			for (RuntimeException e : new RuntimeException[] {
				new InaccessibleObjectException("JDK 17 without --add-opens: FileDescriptor.fd"),
				new IllegalStateException("closed"), new NullPointerException()})
			{
				final ConnSample out = reading();
				failingAt(at, e).read(true, out);
				assertNoData(e + " at " + at, NoData.ERROR, out);
			}
		}
	}

	@Test
	public void aReadingIsPassedOnAsTheSocketGivesIt()
	{
		final ConnSample out = new ConnSample();
		final ConnectionProbe probe = new ConnectionProbe(() -> SOCKET, fd ->
		{
			assertTrue("the socket's own descriptor is asked", fd == SOCKET);
			return tcp(41_250, 1_234_567_890_123L, 4_321);
		});
		probe.read(true, out);
		assertEquals(NoData.NONE, out.conn);
		assertEquals("microseconds, as TCPInfo gives them", 41_250, out.rttMicros);
		assertEquals("cumulative, not per second", 1_234_567_890_123L, out.sent);
		assertEquals(4_321, out.resent);
	}

	@Test
	public void aReadWithoutDataLeavesNoOldNumbers()
	{
		final boolean[] loggedIn = {true};
		final FileDescriptor[] socket = {SOCKET};
		final TCPInfo[] info = {tcp(40_000, 900, 7)};
		final ConnectionProbe probe = new ConnectionProbe(() -> socket[0], fd -> info[0]);
		final ConnSample out = new ConnSample();
		probe.read(true, out);
		assertEquals(NoData.NONE, out.conn);

		socket[0] = null;
		probe.read(true, out);
		assertNoData(NoData.NOT_CONNECTED, out);

		socket[0] = SOCKET;
		probe.read(true, out);
		assertEquals("the same sample reads again", NoData.NONE, out.conn);
		assertEquals(40_000, out.rttMicros);

		info[0] = null;
		probe.read(true, out);
		assertNoData(NoData.UNSUPPORTED, out);

		info[0] = tcp(40_000, 900, 7);
		probe.read(true, out);
		loggedIn[0] = false;
		probe.read(loggedIn[0], out);
		assertNoData(NoData.NOT_LOGGED_IN, out);
	}

	@Test
	public void aFailingGetterLeavesNoHalfReading()
	{
		final ConnSample out = new ConnSample();
		final ConnectionProbe good = new ConnectionProbe(() -> SOCKET, fd -> tcp(40_000, 900, 7));
		good.read(true, out);
		failingAt(Point.RESENT, new IllegalStateException()).read(true, out);
		assertNoData("rtt and sent were read before resent threw", NoData.ERROR, out);
	}

	@Test
	public void neverThrows()
	{
		final Throwable[] failures = {new RuntimeException(), new IllegalArgumentException(),
			new InaccessibleObjectException("x"), new ArithmeticException(), new NoClassDefFoundError(),
			new UnsatisfiedLinkError(), new NoSuchMethodError(), new NoSuchFieldError(), new ClassFormatError(),
			new ExceptionInInitializerError()};
		for (Point at : Point.values())
		{
			for (Throwable t : failures)
			{
				final ConnSample out = reading();
				failingAt(at, t).read(true, out);
				assertNotEquals(t + " at " + at, NoData.NONE, out.conn);
				assertNotNull(out.conn);
			}
		}
		final ConnSample out = reading();
		new ConnectionProbe(null, null).read(true, out);
		assertNoData("no suppliers at all", NoData.ERROR, out);
		new ConnectionProbe(() -> SOCKET, fd -> tcp(1, 2, 3)).read(true, null);
		new ConnectionProbe(null, null).read(false, null);
	}

	@Test
	public void aNewSampleHoldsNoData()
	{
		final ConnSample fresh = new ConnSample();
		assertEquals(-1, fresh.rttMicros);
		assertEquals(-1, fresh.sent);
		assertEquals(-1, fresh.resent);
		assertNotNull("never null: a null would be stored as NONE, a fresh RTT (contract 3.3)", fresh.conn);
		assertNotEquals(NoData.NONE, fresh.conn);
	}

	/**
	 * The real seam, {@code Ping::getTCPInfo}, on a descriptor that is no socket: never NONE and never a throw. On
	 * JDK 17 without {@code --add-opens} Ping's reflection throws, which is ERROR, "Could not read it" (contract 7,
	 * L9); with the opens, the system call fails and Ping answers null, which is UNSUPPORTED.
	 */
	@Test
	public void theRealPingOnANonSocketIsNeverNone()
	{
		final ConnSample out = reading();
		new ConnectionProbe(FileDescriptor::new, Ping::getTCPInfo).read(true, out);
		assertTrue("ERROR or UNSUPPORTED: " + out.conn, out.conn == NoData.ERROR || out.conn == NoData.UNSUPPORTED);
		assertNoData(out.conn, out);
	}

	// ---------------------------------------------------------------- helpers

	/** A sample that holds a good reading, so a test sees each outcome overwrite it. */
	private static ConnSample reading()
	{
		final ConnSample s = new ConnSample();
		s.rttMicros = 39_000;
		s.sent = 800;
		s.resent = 1;
		s.conn = NoData.NONE;
		return s;
	}

	private static void assertNoData(NoData why, ConnSample out)
	{
		assertNoData(why.name(), why, out);
	}

	private static void assertNoData(String message, NoData why, ConnSample out)
	{
		assertEquals(message, why, out.conn);
		assertEquals(message + ": rtt", -1, out.rttMicros);
		assertEquals(message + ": sent", -1, out.sent);
		assertEquals(message + ": resent", -1, out.resent);
	}

	private static TCPInfo tcp(long rttMicros, long sent, long resent)
	{
		return new FakeTcp(rttMicros, sent, resent, null, null);
	}

	/** A probe whose call at {@code at} throws {@code t}; every other call answers a good reading. */
	private static ConnectionProbe failingAt(Point at, Throwable t)
	{
		final Supplier<FileDescriptor> socket = () ->
		{
			if (at == Point.SOCKET)
			{
				sneak(t);
			}
			return SOCKET;
		};
		final Function<FileDescriptor, TCPInfo> info = fd ->
		{
			if (at == Point.INFO)
			{
				sneak(t);
			}
			return new FakeTcp(40_000, 900, 0, at, t);
		};
		return new ConnectionProbe(socket, info);
	}

	private static void sneak(Throwable t)
	{
		if (t instanceof RuntimeException)
		{
			throw (RuntimeException) t;
		}
		throw (Error) t;
	}

	/** TCP numbers, one of whose getters may throw. */
	private static final class FakeTcp implements TCPInfo
	{
		private final long rtt;
		private final long sent;
		private final long resent;
		private final Point failAt;
		private final Throwable failure;

		FakeTcp(long rtt, long sent, long resent, Point failAt, Throwable failure)
		{
			this.rtt = rtt;
			this.sent = sent;
			this.resent = resent;
			this.failAt = failAt;
			this.failure = failure;
		}

		@Override
		public long getRTT()
		{
			fail(Point.RTT);
			return rtt;
		}

		@Override
		public long getTransmitted()
		{
			fail(Point.SENT);
			return sent;
		}

		@Override
		public long getRetransmitted()
		{
			fail(Point.RESENT);
			return resent;
		}

		private void fail(Point here)
		{
			if (failAt == here)
			{
				sneak(failure);
			}
		}
	}
}
