package com.whylag;

import java.io.FileDescriptor;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Map;
import java.util.TreeMap;
import net.runelite.api.Client;
import net.runelite.client.plugins.worldhopper.ping.Ping;
import net.runelite.client.plugins.worldhopper.ping.TCPInfo;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * The three RuneLite names the connection probe leans on, pinned by reflection against the client on the class path
 * (contract 7, L7): {@code Ping.getTCPInfo(FileDescriptor)} is public static and answers a {@code TCPInfo};
 * {@code TCPInfo} has exactly the three {@code long} getters; and {@code Client.getSocketFD()} answers the socket's
 * {@code FileDescriptor}. A client that renames any of them fails here, not in a player's ping cell.
 */
public class PingSignatureTest
{
	@Test
	public void getTcpInfoIsPublicStatic() throws NoSuchMethodException
	{
		final Method m = Ping.class.getMethod("getTCPInfo", FileDescriptor.class);
		assertTrue("public", Modifier.isPublic(m.getModifiers()));
		assertTrue("static", Modifier.isStatic(m.getModifiers()));
		assertEquals(TCPInfo.class, m.getReturnType());
		assertEquals("no checked exception to catch", 0, m.getExceptionTypes().length);
	}

	@Test
	public void tcpInfoHasExactlyTheThreeLongGetters()
	{
		assertTrue(TCPInfo.class.isInterface());
		final Map<String, Class<?>> getters = new TreeMap<>();
		for (Method m : TCPInfo.class.getMethods())
		{
			assertEquals(m.getName() + " takes nothing", 0, m.getParameterCount());
			assertFalse(m.getName() + " is not static", Modifier.isStatic(m.getModifiers()));
			getters.put(m.getName(), m.getReturnType());
		}
		final Map<String, Class<?>> expected = new TreeMap<>();
		expected.put("getRTT", long.class);
		expected.put("getTransmitted", long.class);
		expected.put("getRetransmitted", long.class);
		assertEquals(expected, getters);
	}

	@Test
	public void theClientHandsOutItsSocket() throws NoSuchMethodException
	{
		final Method m = Client.class.getMethod("getSocketFD");
		assertEquals(FileDescriptor.class, m.getReturnType());
		assertFalse(Modifier.isStatic(m.getModifiers()));
	}
}
