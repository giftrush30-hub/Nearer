package com.nearer.app

import org.junit.Assert.assertEquals
import org.junit.Test

class NearbyConnectionsTransportListenerAdapterTest {
    @Test fun legacyAdapterDefaultAcknowledgesWithoutAHandlerOverride() {
        var acknowledgementCalls = 0
        val listener = object : NearbyConnectionsTransport.ListenerAdapter() {}

        listener.onMessage(
            id = "endpoint",
            senderName = "Nearby peer",
            messageId = "message-1",
            body = "hello",
            acknowledge = {
                acknowledgementCalls += 1
                true
            }
        )

        // Characterizes a known hazardous compatibility default. This is not
        // the desired durable-delivery contract; change only after all listener
        // implementations and callers have been audited.
        assertEquals(1, acknowledgementCalls)
    }
}
