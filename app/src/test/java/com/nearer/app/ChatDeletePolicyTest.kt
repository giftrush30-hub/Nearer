package com.nearer.app

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatDeletePolicyTest {
    @Test fun onlyDeliveredOutgoingMessagesCanBeDeletedForEveryone() {
        assertTrue(ChatDeletePolicy.canDeleteForEveryone(true, "Delivered", 0L, false))
        assertTrue(ChatDeletePolicy.canDeleteForEveryone(true, "Queued", 100L, false))
        assertFalse(ChatDeletePolicy.canDeleteForEveryone(false, "Delivered", 0L, false))
        assertFalse(ChatDeletePolicy.canDeleteForEveryone(true, "Queued", 0L, false))
        assertFalse(ChatDeletePolicy.canDeleteForEveryone(true, "Delivered", 0L, true))
    }

    @Test fun onlyTheOriginalSenderCanDeleteTheIncomingCopy() {
        assertTrue(ChatDeletePolicy.canApplyIncoming(false, "PEER_A", "PEER_A", false))
        assertFalse(ChatDeletePolicy.canApplyIncoming(true, "PEER_A", "PEER_A", false))
        assertFalse(ChatDeletePolicy.canApplyIncoming(false, "PEER_B", "PEER_A", false))
        assertFalse(ChatDeletePolicy.canApplyIncoming(false, "PEER_A", "PEER_A", true))
    }

    @Test fun validatesDeleteEventShape() {
        assertTrue(ChatDeletePolicy.isValidPacket(1, "message-id", 10L))
        assertFalse(ChatDeletePolicy.isValidPacket(2, "message-id", 10L))
        assertFalse(ChatDeletePolicy.isValidPacket(1, "", 10L))
        assertFalse(ChatDeletePolicy.isValidPacket(1, "message-id", 0L))
        assertFalse(ChatDeletePolicy.isValidPacket(1, "x".repeat(81), 10L))
    }
}
