package com.nearer.app

import org.junit.Assert.assertEquals
import org.junit.Test

class ChatDeliveryLabelPolicyTest {
    @Test fun explainsNormalDeliveryStatesInPlainLanguage() {
        assertEquals("Sending…", ChatDeliveryLabelPolicy.label("Sending…"))
        assertEquals("Delivered to their phone", ChatDeliveryLabelPolicy.label("Delivered"))
        assertEquals("Waiting for them to reconnect", ChatDeliveryLabelPolicy.label("Queued"))
        assertEquals("Not sent", ChatDeliveryLabelPolicy.label("Failed"))
    }

    @Test fun interruptedMessageDoesNotPretendItWasSentOrInviteBlindResend() {
        assertEquals("Not confirmed — check before resending", ChatDeliveryLabelPolicy.label("Interrupted"))
    }

    @Test fun unknownStatesRemainVisibleRatherThanBeingMisreported() {
        assertEquals("Custom state", ChatDeliveryLabelPolicy.label("Custom state"))
    }
}
