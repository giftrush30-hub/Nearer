package com.nearer.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatRestartRecoveryPolicyTest {
    private fun message(id: String, outgoing: Boolean = true, delivery: String) =
        ChatMessage(id, "Body", outgoing, delivery, timestamp = 1L)

    @Test fun queuedOutboxMessagesBecomeQueuedAfterRestart() {
        val messages = mutableListOf(message("queued", delivery = "Sending…"))
        assertTrue(ChatRestartRecoveryPolicy.reconcile(messages, setOf("queued")))
        assertEquals("Queued", messages.single().delivery)
    }

    @Test fun transientSendingWithoutOutboxBecomesInterrupted() {
        val messages = mutableListOf(
            message("ellipsis", delivery = "Sending…"),
            message("plain", delivery = "Sending")
        )
        assertTrue(ChatRestartRecoveryPolicy.reconcile(messages, emptySet()))
        assertEquals(listOf("Interrupted", "Interrupted"), messages.map { it.delivery })
    }

    @Test fun authenticatedAckHistoryRestoresDeliveredAfterCrash() {
        val messages = mutableListOf(message("acked", delivery = "Sending…"))
        assertTrue(ChatRestartRecoveryPolicy.reconcile(messages, emptySet(), setOf("acked")))
        assertEquals("Delivered", messages.single().delivery)
    }

    @Test fun terminalAndIncomingStatesRemainUnchanged() {
        val messages = mutableListOf(
            message("delivered", delivery = "Delivered"),
            message("failed", delivery = "Failed"),
            message("incoming", outgoing = false, delivery = "Received")
        )
        assertFalse(ChatRestartRecoveryPolicy.reconcile(messages, emptySet()))
        assertEquals(listOf("Delivered", "Failed", "Received"), messages.map { it.delivery })
    }

    @Test fun reconciliationIsIdempotent() {
        val messages = mutableListOf(message("lost", delivery = "Sending…"))
        ChatRestartRecoveryPolicy.reconcile(messages, emptySet())
        assertFalse(ChatRestartRecoveryPolicy.reconcile(messages, emptySet()))
    }
}
