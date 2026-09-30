package com.whylag.core;

import com.whylag.DevHandle;
import com.whylag.PanelControl;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.Set;
import java.util.TreeSet;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * The seams that the badge and the fold rows added to the spine (contract 3.6, 3.9), by reflection: the open event on
 * {@link LagSource}, the two fold rows on {@link PanelControl} and the badge on {@link DevHandle}. Each is a method
 * every implementation must write (no default body), and each interface holds exactly the methods the contract
 * names, so a stage 2 lot that needs another one stops and reports.
 */
public class SeamsTest
{
	@Test
	public void lagSourceHasOpenEvent() throws NoSuchMethodException
	{
		final Method m = LagSource.class.getMethod("openEvent");
		assertEquals(LagEvent.class, m.getReturnType());
		assertEquals(0, m.getParameterCount());
		assertMustBeWritten(m);
		assertEquals(names("verdict", "addVerdictListener", "removeVerdictListener", "session", "snapshot",
			"selfTimer", "openEvent"), methods(LagSource.class));
	}

	@Test
	public void panelControlHasTheFolds() throws NoSuchMethodException
	{
		final Method fold = PanelControl.class.getMethod("fold", boolean.class, boolean.class);
		assertEquals(void.class, fold.getReturnType());
		assertMustBeWritten(fold);
		for (String name : Arrays.asList("graphsOpen", "lagsOpen"))
		{
			final Method m = PanelControl.class.getMethod(name);
			assertEquals(name, boolean.class, m.getReturnType());
			assertMustBeWritten(m);
		}
		assertEquals(names("setRange", "range", "copyReport", "select", "fold", "graphsOpen", "lagsOpen", "isActive",
			"activations", "deactivations", "component"), methods(PanelControl.class));
	}

	@Test
	public void devHandleHasTheBadge() throws NoSuchMethodException
	{
		final Method m = DevHandle.class.getMethod("badge");
		assertEquals(BadgeView.class, m.getReturnType());
		assertEquals(0, m.getParameterCount());
		assertMustBeWritten(m);
		assertEquals(names("source", "settings", "hostProbe", "samplerThreadId", "control", "badge"),
			methods(DevHandle.class));
	}

	private static void assertMustBeWritten(Method m)
	{
		assertTrue(m.getName() + " is public", Modifier.isPublic(m.getModifiers()));
		assertTrue(m.getName() + " is abstract", Modifier.isAbstract(m.getModifiers()));
		assertFalse(m.getName() + " has no default body", m.isDefault());
	}

	private static Set<String> methods(Class<?> type)
	{
		final Set<String> out = new TreeSet<>();
		for (Method m : type.getDeclaredMethods())
		{
			if (!m.isSynthetic())
			{
				out.add(m.getName());
			}
		}
		return out;
	}

	private static Set<String> names(String... names)
	{
		return new TreeSet<>(Arrays.asList(names));
	}
}
