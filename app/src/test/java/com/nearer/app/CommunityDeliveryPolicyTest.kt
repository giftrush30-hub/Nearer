package com.nearer.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CommunityDeliveryPolicyTest {
    @Test fun everyTargetHasAnExplicitInitialState() {
        assertEquals(
            mapOf("PEER_A" to "Queued", "PEER_B" to "Not verified"),
            CommunityDeliveryPolicy.initialStatuses(setOf("PEER_A", "PEER_B"), setOf("PEER_A"))
        )
    }

    @Test fun receiptChangesOnlyItsOwnRecipient() {
        val initial = mapOf("PEER_A" to "Queued", "PEER_B" to "Queued")
        assertEquals(
            mapOf("PEER_A" to "Delivered", "PEER_B" to "Queued"),
            CommunityDeliveryPolicy.applyStatus(initial, "PEER_A", "Delivered")
        )
    }

    @Test fun unknownPeerAndInvalidStatusCannotChangeDeliveryState() {
        val initial = mapOf("PEER_A" to "Queued")
        assertNull(CommunityDeliveryPolicy.applyStatus(initial, "PEER_B", "Delivered"))
        assertNull(CommunityDeliveryPolicy.applyStatus(initial, "PEER_A", "Read"))
    }

    @Test fun discoveredButUnauthenticatedPeerIsQueuedWithoutEndpoint() {
        assertEquals("", CommunityDeliveryPolicy.usableEndpoint("nearby-endpoint", false, true, false))
        assertEquals("", CommunityDeliveryPolicy.usableEndpoint("nearby-endpoint", true, false, true))
    }

    @Test fun fullyAuthenticatedPeerCanUseItsLiveEndpoint() {
        assertEquals(
            "nearby-endpoint",
            CommunityDeliveryPolicy.usableEndpoint("nearby-endpoint", true, true, true)
        )
    }

    @Test fun summaryCountsDeliveredQueuedAndUnverifiedSeparately() {
        assertEquals(
            CommunityDeliveryPolicy.Summary(1, 1, 1, 3),
            CommunityDeliveryPolicy.summarize(
                mapOf("A" to "Delivered", "B" to "Queued", "C" to "Not verified")
            )
        )
    }
}
