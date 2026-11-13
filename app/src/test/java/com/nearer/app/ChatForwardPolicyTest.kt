package com.nearer.app

import org.junit.Assert.*
import org.junit.Test

class ChatForwardPolicyTest {
    private fun source(body: String = "Meet by the library", conversationId: String = "peer-original") =
        ChatMessage("original-message-secret-id", body, false, "Received",
            conversationId = conversationId, conversationName = "Ada")

    @Test fun createsFreshForwardWithoutOriginalIdentityByDefault() {
        val draft = ChatForwardPolicy.create(source(), "peer-new", now = 100L)!!
        assertEquals("Forwarded message\nMeet by the library", draft.body)
        assertNull(draft.attribution)
        assertFalse(draft.body.contains("peer-original"))
        assertFalse(draft.body.contains("original-message-secret-id"))
    }

    @Test fun attributionIsExplicitAndSanitized() {
        val draft = ChatForwardPolicy.create(source(), "peer-new", true, "  Ada\nAdmin  ", 100L)!!
        assertEquals("Ada Admin", draft.attribution)
        assertEquals("Forwarded from Ada Admin\nMeet by the library", draft.body)
    }

    @Test fun refusesMissingOptInAttribution() {
        assertNull(ChatForwardPolicy.create(source(), "peer-new", true, null, 100L))
    }

    @Test fun boundsAttributionAndBody() {
        val draft = ChatForwardPolicy.create(source(), "peer-new", true, "A".repeat(100), 100L)!!
        assertEquals(48, draft.attribution!!.length)
        assertNull(ChatForwardPolicy.create(source("x".repeat(3500)), "peer-new", now = 100L))
    }

    @Test fun rejectsInvalidSourceDestinationAndTime() {
        assertNull(ChatForwardPolicy.create(null, "peer-new", now = 100L))
        assertNull(ChatForwardPolicy.create(source(conversationId = ""), "peer-new", now = 100L))
        assertNull(ChatForwardPolicy.create(source(), " ", now = 100L))
        assertNull(ChatForwardPolicy.create(source(), "peer-new", now = 0L))
        assertNull(ChatForwardPolicy.create(source(" "), "peer-new", now = 100L))
    }
}
