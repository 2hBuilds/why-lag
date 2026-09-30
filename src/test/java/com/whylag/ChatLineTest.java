package com.whylag;

import java.util.List;
import net.runelite.api.ChatMessageType;
import net.runelite.client.chat.ChatMessageManager;
import net.runelite.client.chat.QueuedMessage;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;

/**
 * The badge's chat line (contract P2.6, T21): one CONSOLE message QUEUED on the {@link ChatMessageManager}, its
 * RuneLite-formatted text the line as given, with no tag or colour added, and nothing else asked of the manager;
 * {@code send} never throws, and a null or empty line queues nothing.
 */
public class ChatLineTest
{
	private static final String LINE = "[Why Lag] World lag - not you (14 s). Ticks 1,240 ms, ping 41 ms.";

	@Test
	public void queuesOneConsoleMessage()
	{
		final ChatMessageManager chat = mock(ChatMessageManager.class);
		new ChatLine(chat).send(LINE);
		final ArgumentCaptor<QueuedMessage> queued = ArgumentCaptor.forClass(QueuedMessage.class);
		verify(chat, times(1)).queue(queued.capture());
		verifyNoMoreInteractions(chat);
		final QueuedMessage m = queued.getValue();
		assertSame(ChatMessageType.CONSOLE, m.getType());
		assertEquals("the line as given: no tag, no colour of its own", LINE, m.getRuneLiteFormattedMessage());
		assertNull("no raw value", m.getValue());
		assertNull("no name", m.getName());
		assertNull("no sender", m.getSender());
		assertEquals("no timestamp of its own", 0, m.getTimestamp());
	}

	@Test
	public void eachSendQueuesItsOwnMessage()
	{
		final ChatMessageManager chat = mock(ChatMessageManager.class);
		final ChatLine line = new ChatLine(chat);
		line.send(LINE);
		line.send("[Why Lag] Lag - can't tell why (3 s).");
		final ArgumentCaptor<QueuedMessage> queued = ArgumentCaptor.forClass(QueuedMessage.class);
		verify(chat, times(2)).queue(queued.capture());
		final List<QueuedMessage> all = queued.getAllValues();
		assertEquals(LINE, all.get(0).getRuneLiteFormattedMessage());
		assertEquals("[Why Lag] Lag - can't tell why (3 s).", all.get(1).getRuneLiteFormattedMessage());
		verifyNoMoreInteractions(chat);
	}

	@Test
	public void neverThrows()
	{
		final ChatMessageManager chat = mock(ChatMessageManager.class);
		doThrow(new IllegalStateException("the queue refused")).when(chat).queue(any());
		final ChatLine line = new ChatLine(chat);
		line.send(LINE);
		line.send(LINE);
		verify(chat, times(2)).queue(any());

		new ChatLine(null).send(LINE);
	}

	/** Choice of ChatLine: RuneLite would fail on a message with no text later, on the client thread. */
	@Test
	public void aNullOrEmptyLineQueuesNothing()
	{
		final ChatMessageManager chat = mock(ChatMessageManager.class);
		final ChatLine line = new ChatLine(chat);
		line.send(null);
		line.send("");
		verifyNoInteractions(chat);
	}
}
