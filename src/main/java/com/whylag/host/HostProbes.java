package com.whylag.host;

/**
 * The chooser (contract 7, L7): which {@link HostProbe} the plugin reads memory, CPU and pauses from.
 *
 * <p>{@code systemStats} off answers the {@link RuntimeHostProbe}. On, it tries the management probe inside
 * {@code catch (RuntimeException | LinkageError e)} and falls back to the Runtime probe when the beans are refused or
 * missing, so {@link #create} never throws. Swapping the memory source out for a reviewer = delete
 * {@code ManagementHostProbe.java} and the ONE line below that names it; what is left compiles and always answers
 * the Runtime probe ({@code HostProbesTest.aRuntimeOnlyBuildIsOneLineLess} proves it).
 */
public final class HostProbes
{
	private HostProbes()
	{
	}

	/** The management probe when {@code systemStats} is on and it can be made; else the Runtime probe. */
	public static HostProbe create(boolean systemStats)
	{
		if (systemStats)
		{
			try
			{
				return new ManagementHostProbe();
			}
			catch (RuntimeException | LinkageError e)
			{
				// the management beans are refused or missing: the Runtime probe below
			}
		}
		return new RuntimeHostProbe();
	}
}
