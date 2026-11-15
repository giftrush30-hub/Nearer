package com.nearer.app

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatReadReceiptPolicyTest {
    @Test fun onlyUnreadIncomingMessagesInTheSelectedConversationAreMarked() {
        assertTrue(ChatReadReceiptPolicy.shouldMarkRead(false, "PEER_A", "PEER_A", 0L))
        assertFalse(ChatReadReceiptPolicy.shouldMarkRead(true, "PEER_A", "PEER_A", 0L))
        assertFalse(ChatReadReceiptPolicy.shouldMarkRead(false, "PEER_B", "PEER_A", 0L))
        assertFalse(ChatReadReceiptPolicy.shouldMarkRead(false, "PEER_A", "PEER_A", 100L))
        assertFalse(ChatReadReceiptPolicy.shouldMarkRead(false, "", "", 0L))
    }

    @Test fun onlyOriginalRecipientCanMarkItsOutgoingMessagesRead() {
        assertTrue(ChatReadReceiptPolicy.canApplyReceipt(true, "PEER_A", "PEER_A", 100L))
        assertFalse(ChatReadReceiptPolicy.canApplyReceipt(false, "PEER_A", "PEER_A", 100L))
        assertFalse(ChatReadReceiptPolicy.canApplyReceipt(true, "PEER_B", "PEER_A", 100L))
        assertFalse(ChatReadReceiptPolicy.canApplyReceipt(true, "PEER_A", "PEER_A", 0L))
    }

    @Test fun validatesBoundedUniqueReceiptPacketsAndClockSkew() {
        assertTrue(ChatReadReceiptPolicy.isValidPacket(1, listOf("id-1", "id-2"), 1_000L, 1_000L))
        assertFalse(ChatReadReceiptPolicy.isValidPacket(2, listOf("id-1"), 1_000L, 1_000L))
        assertFalse(ChatReadReceiptPolicy.isValidPacket(1, emptyList(), 1_000L, 1_000L))
        assertFalse(ChatReadReceiptPolicy.isValidPacket(1, listOf("id-1", "id-1"), 1_000L, 1_000L))
        assertFalse(ChatReadReceiptPolicy.isValidPacket(1, listOf(""), 1_000L, 1_000L))
        assertFalse(ChatReadReceiptPolicy.isValidPacket(1, List(81) { "id-$it" }, 1_000L, 1_000L))
        assertFalse(ChatReadReceiptPolicy.isValidPacket(1, listOf("id-1"), 600_001L, 1_000L))
    }
}
