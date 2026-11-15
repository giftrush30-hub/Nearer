package com.nearer.app

import org.junit.Assert.*
import org.junit.Test

class ChatDeletePolicyTest {
    @Test fun onlyDeliveredOutgoingMessagesCanBeDeletedForEveryone() {
        assertTrue(ChatDeletePolicy.canDeleteForEveryone(true, "Delivered", 0L, false))
        assertTrue(ChatDeletePolicy.canDeleteForEveryone(true, "Sending…", 100L, false))
        assertFalse(ChatDeletePolicy.canDeleteForEveryone(false, "Delivered", 0L, false))
        assertFalse(ChatDeletePolicy.canDeleteForEveryone(true, "Queued", 0L, false))
        assertFalse(ChatDeletePolicy.canDeleteForEveryone(true, "Failed", 0L, false))
        assertFalse(ChatDeletePolicy.canDeleteForEveryone(true, "Delivered", 0L, true))
    }

    @Test fun onlyOriginalSenderCanDeleteIncomingCopy() {
        assertTrue(ChatDeletePolicy.canApplyIncoming(false, "PEER_A", "PEER_A", false))
        assertFalse(ChatDeletePolicy.canApplyIncoming(true, "PEER_A", "PEER_A", false))
        assertFalse(ChatDeletePolicy.canApplyIncoming(false, "PEER_B", "PEER_A", false))
        assertFalse(ChatDeletePolicy.canApplyIncoming(false, "PEER_A", "PEER_A", true))
    }

    @Test fun deletePacketIsBounded() {
        assertTrue(ChatDeletePolicy.isValidPacket(1, "message-1", 100L))
        assertFalse(ChatDeletePolicy.isValidPacket(2, "message-1", 100L))
        assertFalse(ChatDeletePolicy.isValidPacket(1, "", 100L))
        assertFalse(ChatDeletePolicy.isValidPacket(1, "x".repeat(81), 100L))
        assertFalse(ChatDeletePolicy.isValidPacket(1, "message-1", 0L))
    }
}
