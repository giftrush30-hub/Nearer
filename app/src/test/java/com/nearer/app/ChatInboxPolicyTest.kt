package com.nearer.app

import org.junit.Assert.assertEquals
import org.junit.Test

class ChatInboxPolicyTest {
    private fun message(id: String, peer: String, time: Long, body: String, name: String = peer) =
        ChatMessage(id, body, false, "Received", time, peer, name)

    @Test fun searchFindsOlderMessagesNotOnlyLatestPreview() {
        val messages = listOf(
            message("old", "peer-a", 1, "Need the blue notebook"),
            message("new", "peer-a", 2, "Okay, see you")
        )
        assertEquals(listOf("peer-a"), ChatInboxPolicy.conversationIds(messages, emptyList(), "blue notebook"))
    }

    @Test fun pinnedConversationsSortBeforeNewerUnpinnedOnes() {
        val messages = listOf(
            message("new", "peer-new", 100, "Latest"),
            message("pin", "peer-pin", 10, "Important")
        )
        assertEquals(
            listOf("peer-pin", "peer-new"),
            ChatInboxPolicy.conversationIds(messages, emptyList(), pinnedIds = setOf("peer-pin"))
        )
    }

    @Test fun filtersDistinguishDirectMeshAndOfflineConversations() {
        val recipients = listOf(
            MeshRecipient("direct", "Direct", 0, true),
            MeshRecipient("mesh", "Mesh", 2, false)
        )
        val messages = listOf(
            message("a", "direct", 1, "one"),
            message("b", "mesh", 2, "two"),
            message("c", "offline", 3, "three")
        )
        assertEquals(listOf("direct"), ChatInboxPolicy.conversationIds(messages, recipients, filter = "Nearby"))
        assertEquals(listOf("mesh"), ChatInboxPolicy.conversationIds(messages, recipients, filter = "Via others"))
        assertEquals(listOf("offline"), ChatInboxPolicy.conversationIds(messages, recipients, filter = "Offline"))
    }

    @Test fun searchMatchesDisplayNameAndStableId() {
        val recipients = listOf(MeshRecipient("peer-123", "Ada", 0, true))
        assertEquals(listOf("peer-123"), ChatInboxPolicy.conversationIds(emptyList(), recipients, "ada"))
        assertEquals(listOf("peer-123"), ChatInboxPolicy.conversationIds(emptyList(), recipients, "123"))
    }
}
