package com.nearer.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class ChatHistoryPolicyTest {
    private fun message(id: Int) =
        ChatMessage(id.toString(), "Message $id", false, "Received", id.toLong())

    @Test fun trimsOldestMessagesAndKeepsNewest() {
        val messages = (1..505).map(::message).toMutableList()
        ChatHistoryPolicy.trim(messages)
        assertEquals(ChatHistoryPolicy.MAX_MESSAGES, messages.size)
        assertEquals("6", messages.first().id)
        assertEquals("505", messages.last().id)
    }

    @Test fun leavesHistoryUnchangedWhenWithinLimit() {
        val messages = (1..3).map(::message).toMutableList()
        ChatHistoryPolicy.trim(messages)
        assertEquals(listOf("1", "2", "3"), messages.map { it.id })
    }

    @Test fun zeroLimitClearsHistory() {
        val messages = (1..3).map(::message).toMutableList()
        ChatHistoryPolicy.trim(messages, 0)
        assertEquals(0, messages.size)
    }

    @Test fun rejectsNegativeLimit() {
        assertThrows(IllegalArgumentException::class.java) {
            ChatHistoryPolicy.trim(mutableListOf(message(1)), -1)
        }
    }
}
