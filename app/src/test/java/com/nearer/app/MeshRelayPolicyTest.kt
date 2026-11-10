package com.nearer.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class MeshRelayPolicyTest {
    private val now = 1_800_000_000_000L
    private fun packet(
        destination: String = "peer-d",
        ttl: Int = 4,
        hops: Int = 0,
        visited: List<String> = emptyList()
    ) = MeshRelayEnvelope(
        messageId = "message-1",
        originPeerId = "peer-a",
        destinationPeerId = destination,
        ciphertext = "opaque-encrypted-payload",
        ttlRemaining = ttl,
        hopCount = hops,
        visitedPeerIds = visited,
        createdAtMillis = now
    )

    @Test fun intermediatePeerForwardsAndConsumesExactlyOneHop() {
        val result = MeshRelayPolicy.evaluate(packet(), "peer-b", true, false, now)
        assertEquals(MeshRelayDisposition.FORWARD, result.disposition)
        assertEquals(3, result.forwardedEnvelope?.ttlRemaining)
        assertEquals(1, result.forwardedEnvelope?.hopCount)
        assertEquals(listOf("peer-b"), result.forwardedEnvelope?.visitedPeerIds)
    }

    @Test fun destinationDeliversWithoutNeedingRelayConsentOrRemainingTtl() {
        val result = MeshRelayPolicy.evaluate(packet(destination = "peer-c", ttl = 0), "peer-c", false, false, now)
        assertEquals(MeshRelayDisposition.DELIVER_LOCALLY, result.disposition)
        assertNull(result.forwardedEnvelope)
    }

    @Test fun relayRequiresExplicitConsent() {
        val result = MeshRelayPolicy.evaluate(packet(), "peer-b", false, false, now)
        assertEquals(MeshRelayDisposition.DROP_RELAY_NOT_CONSENTED, result.disposition)
        assertNull(result.forwardedEnvelope)
    }

    @Test fun duplicateAndExpiredPacketsAreDropped() {
        assertEquals(
            MeshRelayDisposition.DROP_DUPLICATE,
            MeshRelayPolicy.evaluate(packet(), "peer-b", true, true, now).disposition
        )
        assertEquals(
            MeshRelayDisposition.DROP_EXPIRED,
            MeshRelayPolicy.evaluate(packet(), "peer-b", true, false, now + MeshRelayPolicy.DEFAULT_MAX_AGE_MILLIS + 1).disposition
        )
    }

    @Test fun visitedPeerAndHopLimitPreventForwardingLoops() {
        assertEquals(
            MeshRelayDisposition.DROP_LOOP,
            MeshRelayPolicy.evaluate(packet(visited = listOf("peer-b")), "peer-b", true, false, now).disposition
        )
        assertEquals(
            MeshRelayDisposition.DROP_HOP_LIMIT,
            MeshRelayPolicy.evaluate(packet(ttl = 0), "peer-b", true, false, now).disposition
        )
        assertEquals(
            MeshRelayDisposition.DROP_HOP_LIMIT,
            MeshRelayPolicy.evaluate(packet(ttl = 1, hops = MeshRelayPolicy.MAX_HOPS), "peer-b", true, false, now).disposition
        )
    }

    @Test fun malformedOrPlainEmptyPayloadIsRejected() {
        val invalid = packet().copy(ciphertext = "")
        val result = MeshRelayPolicy.evaluate(invalid, "peer-b", true, false, now)
        assertEquals(MeshRelayDisposition.DROP_INVALID, result.disposition)
        assertNull(result.forwardedEnvelope)
    }

    @Test fun fourHopBudgetIsExplicitAndBounded() {
        assertEquals(4, MeshRelayPolicy.MAX_HOPS)
        assertNotNull(MeshRelayPolicy.evaluate(packet(ttl = 1, hops = 3, visited = listOf("peer-b", "peer-c", "peer-x")), "peer-y", true, false, now).forwardedEnvelope)
    }
}
