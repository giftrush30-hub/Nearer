package com.nearer.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MessageDeliveryKeyTest {
    @Test fun sameMessageHasIndependentKeysForDifferentRecipients() {
        val alice = MessageDeliveryKey.forPeer("message-123", "ALICE234")
        val bob = MessageDeliveryKey.forPeer("message-123", "BOB23456")
        assertNotEquals(alice, bob)
        assertEquals("message-123::ALICE234", alice)
        assertEquals("message-123::BOB23456", bob)
    }

    @Test fun keysAreStableAcrossRestarts() {
        val first = MessageDeliveryKey.forPeer("message-123", "ALICE234")
        val restored = MessageDeliveryKey.restoreKey("message-123", "ALICE234", first)
        assertEquals(first, restored)
    }

    @Test fun migratesLegacyDirectMessageKeyWithoutChangingMessageIdentity() {
        assertEquals(
            "message-123::ALICE234",
            MessageDeliveryKey.restoreKey("message-123", "ALICE234", "message-123")
        )
        assertEquals(
            "message-123::ALICE234",
            MessageDeliveryKey.restoreKey("message-123", "ALICE234", "")
        )
    }

    @Test fun rejectsQueueKeyBoundToAnotherRecipientOrMessage() {
        assertNull(MessageDeliveryKey.restoreKey("message-123", "ALICE234", "message-123::BOB23456"))
        assertNull(MessageDeliveryKey.restoreKey("message-123", "ALICE234", "other-message::ALICE234"))
    }

    @Test fun rejectsMalformedDeliveryIdentities() {
        assertNull(MessageDeliveryKey.restoreKey("", "ALICE234", ""))
        assertNull(MessageDeliveryKey.restoreKey("message-123", "", ""))
        assertNull(MessageDeliveryKey.restoreKey("x".repeat(81), "ALICE234", ""))
        assertNull(MessageDeliveryKey.restoreKey("message-123", "x".repeat(65), ""))
    }
}
