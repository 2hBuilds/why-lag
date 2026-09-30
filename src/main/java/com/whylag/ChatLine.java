package com.whylag;

import net.runelite.api.ChatMessageType;
import net.runelite.client.chat.ChatMessageManager;
import net.runelite.client.chat.QueuedMessage;

/**
 * The badge's one game message per lag (contract P2.6): "[Why Lag] World lag - not you (14 s). Ticks 1,240 ms, ping
 * 41 ms." {@code BadgeModel} makes the words; this class only QUEUES them, as a {@link ChatMessageType#CONSOLE}
 * message whose RuneLite-formatted text is the line as given, with no tag and no colour of its own. Nothing is typed
 * into the chat input and nothing is sent to the server (T21). {@link ChatMessageManager#queue} adds to a concurrent
 * queue that the client thread empties, so {@link #send} may be called on any thread (the sampler thread calls it).
 *
 * <p>Choice: a null or empty text is not queued: RuneLite would fail on it later, on the client thread.
 * <p>Choice: a line that cannot be queued is dropped without a log: the badge's classes import no logger.
 */
public final class ChatLine
{
	private final ChatMessageManager chat;

	public ChatLine(ChatMessageManager chat)
	{
		this.chat = chat;
	}

	/** Queues the line as one CONSOLE game message; any thread; never throws. */
	public void send(String text)
	{
		if (text == null || text.isEmpty())
		{
			return;
		}
		try
		{
			chat.queue(QueuedMessage.builder()
				.type(ChatMessageType.CONSOLE)
				.runeLiteFormattedMessage(text)
				.build());
		}
		catch (RuntimeException e)
		{
			// never throws: this lag's line is lost, the next one is tried as usual
		}
	}
}
