package com.nearer.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatReplyPolicyTest {
    private fun message(
        id: String = "message-1",
        body: String = "Bring the blue notebook",
        outgoing: Boolean = false,
        conversationId: String = "peer-a",
        conversationName: String = "Ada"
    ) = ChatMessage(id, body, outgoing, "Received", 1L, conversationId, conversationName)

    @Test fun createsBoundedQuoteForMessageInSameConversation() {
        val quote = ChatReplyPolicy.createQuote(message(), "peer-a", "Ada")
        assertEquals(ChatReplyPolicy.Quote("message-1", "Bring the blue notebook", "Ada"), quote)
    }

    @Test fun rejectsQuoteFromAnotherConversation() {
        assertNull(ChatReplyPolicy.createQuote(message(conversationId = "peer-b"), "peer-a", "Ada"))
    }

    @Test fun rejectsBlankOrOversizedMessageIds() {
        assertNull(ChatReplyPolicy.createQuote(message(id = "  "), "peer-a", "Ada"))
        assertNull(ChatReplyPolicy.createQuote(message(id = "x".repeat(81)), "peer-a", "Ada"))
    }

    @Test fun boundsLongQuotedTextAndSenderNames() {
        val quote = ChatReplyPolicy.createQuote(
            message(body = "x".repeat(500)),
            "peer-a",
            "A".repeat(100)
        )!!
        assertEquals(180, quote.preview.length)
        assertEquals(48, quote.sender.length)
    }

    @Test fun incomingQuoteRequiresAllFieldsAndEnforcesLimits() {
        assertTrue(ChatReplyPolicy.isValidIncomingQuote("id-1", "A message", "Ada"))
        assertFalse(ChatReplyPolicy.isValidIncomingQuote("", "A message", "Ada"))
        assertFalse(ChatReplyPolicy.isValidIncomingQuote("id-1", "", "Ada"))
        assertFalse(ChatReplyPolicy.isValidIncomingQuote("id-1", "A message", ""))
        assertFalse(ChatReplyPolicy.isValidIncomingQuote("x".repeat(81), "A message", "Ada"))
        assertFalse(ChatReplyPolicy.isValidIncomingQuote("id-1", "x".repeat(181), "Ada"))
    }

    @Test fun displayPreviewNeverExceedsLimit() {
        assertEquals(180, ChatReplyPolicy.previewForDisplay("x".repeat(220)).length)
        assertEquals("", ChatReplyPolicy.previewForDisplay(null))
    }
}
