package com.nearer.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatDeliveryRecoveryPolicyTest {
    @Test fun processRestartQueuesOutgoingMessagesWithUncertainDelivery() {
        listOf("Sending", "Awaiting delivery", "Sent").forEach { status ->
            assertEquals("Queued", ChatDeliveryRecoveryPolicy.restoreDelivery(true, status))
        }
    }

    @Test fun processRestartPreservesConfirmedAndIncomingMessages() {
        assertEquals("Delivered", ChatDeliveryRecoveryPolicy.restoreDelivery(true, "Delivered"))
        assertEquals("Received", ChatDeliveryRecoveryPolicy.restoreDelivery(false, "Received"))
    }

    @Test fun disconnectQueuesOnlyUnconfirmedMessagesForThatPeer() {
        assertTrue(ChatDeliveryRecoveryPolicy.shouldQueueAfterDisconnect(
            true, "Awaiting delivery", "peer-a", "peer-a"
        ))
        assertFalse(ChatDeliveryRecoveryPolicy.shouldQueueAfterDisconnect(
            true, "Awaiting delivery", "peer-b", "peer-a"
        ))
    }

    @Test fun disconnectNeverQueuesDeliveredOrIncomingMessages() {
        assertFalse(ChatDeliveryRecoveryPolicy.shouldQueueAfterDisconnect(
            true, "Delivered", "peer-a", "peer-a"
        ))
        assertFalse(ChatDeliveryRecoveryPolicy.shouldQueueAfterDisconnect(
            false, "Received", "peer-a", "peer-a"
        ))
    }

    @Test fun reconnectRetriesOnlyQueuedMessagesForTheConnectedPeer() {
        assertTrue(ChatDeliveryRecoveryPolicy.canRetryOnReconnect(
            true, "Queued", "peer-a", "peer-a", false
        ))
        assertFalse(ChatDeliveryRecoveryPolicy.canRetryOnReconnect(
            true, "Queued", "peer-b", "peer-a", false
        ))
    }

    @Test fun reconnectWaitsForPendingPersistenceAndNeverRetriesConfirmedMessages() {
        assertFalse(ChatDeliveryRecoveryPolicy.canRetryOnReconnect(
            true, "Queued", "peer-a", "peer-a", true
        ))
        assertFalse(ChatDeliveryRecoveryPolicy.canRetryOnReconnect(
            true, "Delivered", "peer-a", "peer-a", false
        ))
        assertFalse(ChatDeliveryRecoveryPolicy.canRetryOnReconnect(
            false, "Queued", "peer-a", "peer-a", false
        ))
    }
}
