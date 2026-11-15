package com.nearer.app

import org.junit.Assert.*
import org.junit.Test

class ChatReadReceiptPolicyTest {
    @Test fun validatesBoundedUniqueReceiptPackets() {
        assertTrue(ChatReadReceiptPolicy.isValidPacket(1, listOf("m1", "m2"), 100L, 100L))
        assertFalse(ChatReadReceiptPolicy.isValidPacket(2, listOf("m1"), 100L, 100L))
        assertFalse(ChatReadReceiptPolicy.isValidPacket(1, listOf("m1", "m1"), 100L, 100L))
        assertFalse(ChatReadReceiptPolicy.isValidPacket(1, emptyList(), 100L, 100L))
        assertFalse(ChatReadReceiptPolicy.isValidPacket(1, listOf("m1"), 0L, 100L))
        assertFalse(ChatReadReceiptPolicy.isValidPacket(1, listOf("m1"), 400_000L, 100L))
        assertFalse(ChatReadReceiptPolicy.isValidPacket(1, (1..41).map { "m$it" }, 100L, 100L))
    }

    @Test fun onlyIncomingMessagesInSelectedConversationBecomeRead() {
        assertTrue(ChatReadReceiptPolicy.shouldMarkRead(false, "PEER_A", "PEER_A", 0L, 0L))
        assertFalse(ChatReadReceiptPolicy.shouldMarkRead(true, "PEER_A", "PEER_A", 0L, 0L))
        assertFalse(ChatReadReceiptPolicy.shouldMarkRead(false, "PEER_B", "PEER_A", 0L, 0L))
        assertFalse(ChatReadReceiptPolicy.shouldMarkRead(false, "PEER_A", "PEER_A", 0L, 100L))
        assertFalse(ChatReadReceiptPolicy.shouldMarkRead(false, "PEER_A", "PEER_A", 100L, 0L))
    }

    @Test fun pendingReceiptsCanRetryAfterReconnect() {
        assertTrue(ChatReadReceiptPolicy.shouldSendReceipt(false, "PEER_A", "PEER_A", 100L, 0L))
        assertFalse(ChatReadReceiptPolicy.shouldSendReceipt(false, "PEER_A", "PEER_A", 0L, 0L))
        assertFalse(ChatReadReceiptPolicy.shouldSendReceipt(false, "PEER_A", "PEER_A", 100L, 200L))
        assertFalse(ChatReadReceiptPolicy.shouldSendReceipt(true, "PEER_A", "PEER_A", 100L, 0L))
    }

    @Test fun receiptOnlyUpdatesMessagesSentToAuthenticatedActor() {
        assertTrue(ChatReadReceiptPolicy.canApplyReceipt(true, "PEER_A", "PEER_A", 100L))
        assertFalse(ChatReadReceiptPolicy.canApplyReceipt(true, "PEER_B", "PEER_A", 100L))
        assertFalse(ChatReadReceiptPolicy.canApplyReceipt(false, "PEER_A", "PEER_A", 100L))
        assertFalse(ChatReadReceiptPolicy.canApplyReceipt(true, "PEER_A", "", 100L))
    }
}
