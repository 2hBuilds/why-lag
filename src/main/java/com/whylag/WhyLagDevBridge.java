package com.whylag;

/**
 * A test hook and nothing else: the one static handle a developer-mode client gives the Effect Lab (contract 3.9),
 * the same shape as {@code com.lootandbeam.DevBridge} and {@code BpmDevBridge}.
 *
 * <p><b>It is null in every production client.</b> The plugin sets it in {@code startUp} only when RuneLite's
 * injected {@code @Named("developerMode")} constant is true, and clears it as the FIRST statement of
 * {@code shutDown}. Only the lab, in the test source set, reads it. Volatile: written on plugin start and stop,
 * read on the lab's HTTP thread.
 */
public final class WhyLagDevBridge
{
	/** The running plugin's handle while a developer-mode client has it on; null otherwise. */
	public static volatile DevHandle handle;

	private WhyLagDevBridge()
	{
	}
}
