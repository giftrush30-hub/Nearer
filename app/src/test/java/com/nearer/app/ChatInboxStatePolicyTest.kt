package com.nearer.app

import org.junit.Assert.assertEquals
import org.junit.Test

class ChatInboxStatePolicyTest {
    @Test
    fun unreadCountIncludesOnlyIncomingMessagesAfterReadMarker() {
        val messages = listOf(
            ChatMessage("old-in", "old", false, "Received", timestamp = 10L, conversationId = "peer"),
            ChatMessage("out", "sent", true, "Delivered", timestamp = 20L, conversationId = "peer"),
            ChatMessage("new-in-1", "one", false, "Received", timestamp = 30L, conversationId = "peer"),
            ChatMessage("new-in-2", "two", false, "Received", timestamp = 40L, conversationId = "peer")
        )

        assertEquals(2, ChatInboxStatePolicy.unreadCount(messages, 20L))
        assertEquals(0, ChatInboxStatePolicy.unreadCount(messages, 40L))
    }

    @Test
    fun unreadCountDoesNotCountOtherConversationsOrBlankMessages() {
        val messages = listOf(
            ChatMessage("a", "hello", false, "Received", timestamp = 50L, conversationId = "peer-a"),
            ChatMessage("b", "hello", false, "Received", timestamp = 60L, conversationId = "peer-b"),
            ChatMessage("c", " ", false, "Received", timestamp = 70L, conversationId = "peer-a")
        )

        assertEquals(1, ChatInboxStatePolicy.unreadCount(messages.filter { it.conversationId == "peer-a" }, 0L))
    }

    @Test
    fun unreadCountNeverGoesNegative() {
        assertEquals(0, ChatInboxStatePolicy.unreadCount(emptyList(), 0L))
    }
}
