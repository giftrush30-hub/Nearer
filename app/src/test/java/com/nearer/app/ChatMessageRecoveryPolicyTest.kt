package com.nearer.app

import org.junit.Assert.assertEquals
import org.junit.Test

class ChatMessageRecoveryPolicyTest {
    @Test fun outgoingInFlightMessageBecomesInterruptedAfterRestart() {
        assertEquals("Interrupted", ChatMessageRecoveryPolicy.deliveryAfterProcessRestart(true, "Sending…"))
        assertEquals("Interrupted", ChatMessageRecoveryPolicy.deliveryAfterProcessRestart(true, "Sending..."))
    }

    @Test fun incomingMessageDeliveryStateIsPreserved() {
        assertEquals("Received", ChatMessageRecoveryPolicy.deliveryAfterProcessRestart(false, "Received"))
    }

    @Test fun terminalAndDurablyQueuedStatesArePreserved() {
        assertEquals("Delivered", ChatMessageRecoveryPolicy.deliveryAfterProcessRestart(true, "Delivered"))
        assertEquals("Failed", ChatMessageRecoveryPolicy.deliveryAfterProcessRestart(true, "Failed"))
        assertEquals("Queued", ChatMessageRecoveryPolicy.deliveryAfterProcessRestart(true, "Queued"))
    }
}
