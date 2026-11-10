package com.nearer.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ConversationListPolicyTest {
    @Test
    fun groupsMessagesByPeerAndSortsNewestConversationFirst() {
        val messages = listOf(
            ChatMessage("a", "first", true, "Sent", 100L, "peer-a", "Ada"),
            ChatMessage("b", "other", false, "Received", 300L, "peer-b", "Bola"),
            ChatMessage("c", "reply", false, "Received", 200L, "peer-a", "Ada")
        )

        val conversations = ConversationListPolicy.build(messages)

        assertEquals(listOf("peer-b", "peer-a"), conversations.map { it.key.removePrefix("id:") })
        assertEquals(2, conversations.last().messages.size)
        assertEquals("reply", conversations.last().latestMessage.body)
        assertEquals("Ada", conversations.last().name)
    }

    @Test
    fun keepsLegacyMessagesVisibleAsOneConversation() {
        val conversations = ConversationListPolicy.build(
            listOf(
                ChatMessage("a", "old sent", true, "Sent", 10L),
                ChatMessage("b", "old received", false, "Received", 20L)
            )
        )

        assertEquals(1, conversations.size)
        assertEquals("legacy", conversations.single().key)
        assertEquals(2, conversations.single().messages.size)
        assertTrue(conversations.single().name.isNotBlank())
    }

    @Test
    fun usesPeerNameWhenStableIdentityIsUnavailable() {
        val conversations = ConversationListPolicy.build(
            listOf(ChatMessage("a", "hello", false, "Received", 10L, "", "Chika"))
        )

        assertEquals("name:Chika", conversations.single().key)
        assertEquals("Chika", conversations.single().name)
    }
}
