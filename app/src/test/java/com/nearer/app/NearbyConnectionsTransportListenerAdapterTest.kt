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

    @Test fun replyCallbackBridgeFallsThroughToLegacyAcknowledgement() {
        var acknowledgementCalls = 0
        val listener = object : NearbyConnectionsTransport.ListenerAdapter() {}

        listener.onMessageWithReply(
            id = "endpoint",
            senderName = "Nearby peer",
            messageId = "message-2",
            body = "reply",
            replyToMessageId = "parent-1",
            replyToPreview = "parent",
            replyToSender = "peer",
            acknowledge = {
                acknowledgementCalls += 1
                true
            }
        )

        // The interface's default reply bridge delegates to onMessage().
        assertEquals(1, acknowledgementCalls)
    }

    @Test fun meshReplyCallbackBridgeAlsoFallsThroughToLegacyAcknowledgement() {
        var acknowledgementCalls = 0
        val listener = object : NearbyConnectionsTransport.ListenerAdapter() {}

        listener.onMeshMessageWithReply(
            originPeerId = "stable-peer",
            senderName = "Nearby peer",
            messageId = "message-3",
            body = "mesh reply",
            replyToMessageId = "parent-2",
            replyToPreview = "parent",
            replyToSender = "peer",
            acknowledge = {
                acknowledgementCalls += 1
                true
            }
        )

        // Both mesh default bridges eventually delegate to onMessage().
        assertEquals(1, acknowledgementCalls)
    }
}
