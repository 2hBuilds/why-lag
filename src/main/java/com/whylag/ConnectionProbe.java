package com.whylag;

import com.whylag.core.NoData;
import java.io.FileDescriptor;
import java.util.function.Function;
import java.util.function.Supplier;
import net.runelite.client.plugins.worldhopper.ping.TCPInfo;

/**
 * Reads the game socket's round trip time and its sent and re-sent counters, once a second on the sampler thread
 * (contract 3.11, 7 L7). Read-only: it sends no packet and never calls {@code Ping.ping}. The plugin builds it as
 * {@code new ConnectionProbe(client::getSocketFD, Ping::getTCPInfo)}; tests hand in fakes.
 *
 * <p>The outcomes, in the order they are tested (the Ping cell's words are contract 5.2's):
 * <ul>
 * <li>not logged in: {@link NoData#NOT_LOGGED_IN}, and the socket is not asked;</li>
 * <li>no socket (a null descriptor): {@link NoData#NOT_CONNECTED};</li>
 * <li>no {@code TCPInfo} (Ping answers null on this system), or a {@code LinkageError} anywhere:
 * {@link NoData#UNSUPPORTED}, "Not on this PC";</li>
 * <li>any other exception, a {@code RuntimeException} above all (without {@code --add-opens} Ping cannot read the
 * socket's descriptor and throws one on JDK 17): {@link NoData#ERROR}, "Could not read it";</li>
 * <li>else {@link NoData#NONE} with the socket's three numbers.</li>
 * </ul>
 * It never throws (T13).
 *
 * <p>Choice: a read without data sets the three numbers to -1, so no number of an earlier read is handed on.
 * <p>Choice: the three getters are all read before the sample is written, so a throw leaves no half-written reading.
 * <p>Choice: the numbers are passed on as the socket gives them; making them per-second values is the engine's work.
 * <p>Choice: a null sample is ignored (nothing to fill), which keeps the promise never to throw.
 */
public final class ConnectionProbe
{
	/** A number with no data. */
	private static final long NO_DATA = -1;

	private final Supplier<FileDescriptor> socket;
	private final Function<FileDescriptor, TCPInfo> info;

	/**
	 * @param socket the game's socket, {@code client::getSocketFD}; null while there is none
	 * @param info   the TCP numbers of a socket, {@code Ping::getTCPInfo}; null where the system has none
	 */
	public ConnectionProbe(Supplier<FileDescriptor> socket, Function<FileDescriptor, TCPInfo> info)
	{
		this.socket = socket;
		this.info = info;
	}

	/**
	 * One reading into {@code out}. {@code loggedIn} is the plugin's volatile in-game flag, written on the client
	 * thread (contract 7, L9): this runs on the sampler thread and never reads the game state itself. Never throws.
	 */
	public void read(boolean loggedIn, ConnSample out)
	{
		if (out == null)
		{
			return;
		}
		if (!loggedIn)
		{
			noData(out, NoData.NOT_LOGGED_IN);
			return;
		}
		try
		{
			final FileDescriptor fd = socket.get();
			if (fd == null)
			{
				noData(out, NoData.NOT_CONNECTED);
				return;
			}
			final TCPInfo tcp = info.apply(fd);
			if (tcp == null)
			{
				noData(out, NoData.UNSUPPORTED);
				return;
			}
			final long rtt = tcp.getRTT();
			final long sent = tcp.getTransmitted();
			final long resent = tcp.getRetransmitted();
			out.rttMicros = rtt;
			out.sent = sent;
			out.resent = resent;
			out.conn = NoData.NONE;
		}
		catch (LinkageError e)
		{
			noData(out, NoData.UNSUPPORTED);
		}
		catch (Exception e)
		{
			noData(out, NoData.ERROR);
		}
	}

	private static void noData(ConnSample out, NoData why)
	{
		out.rttMicros = NO_DATA;
		out.sent = NO_DATA;
		out.resent = NO_DATA;
		out.conn = why;
	}
}
