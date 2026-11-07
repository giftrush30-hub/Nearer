package com.nearer.app

import org.junit.Assert.assertEquals
import org.junit.Test

class ConversationIndexTest {
    private fun message(id: String, peer: String, time: Long, body: String = id) =
        ChatMessage(id, body, false, "Received", peer, peer, time)

    @Test fun groupsMessagesByStableConversationId() {
        val result = ConversationIndex.build(listOf(
            message("a", "peer-a", 10),
            message("b", "peer-b", 20),
            message("c", "peer-a", 30)
        ))
        assertEquals(2, result.size)
        assertEquals("peer-a", result.first().conversationId)
        assertEquals(2, result.first().messageCount)
    }

    @Test fun returnsConversationMessagesInTimeOrder() {
        val messages = listOf(message("later", "peer-a", 20), message("earlier", "peer-a", 10))
        assertEquals(listOf("earlier", "later"), ConversationIndex.messagesFor(messages, "peer-a").map { it.id })
    }

    @Test fun legacyBlankConversationIdsStayInLegacyThread() {
        val result = ConversationIndex.build(listOf(
            ChatMessage("x", "x", false, "Received", "", "Old", 1),
            ChatMessage("y", "y", false, "Received", "legacy", "Old", 2)
        ))
        assertEquals(1, result.size)
    }
}
