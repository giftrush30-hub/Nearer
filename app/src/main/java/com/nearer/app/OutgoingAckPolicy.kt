package com.nearer.app

/** Prevents a stale or recycled Nearby endpoint from acknowledging another peer's message. */
internal object OutgoingAckPolicy {
    fun matchesExpectedRecipient(
        expectedEndpointId: String?,
        incomingEndpointId: String,
        expectedPeerId: String?,
        incomingPeerId: String?
    ): Boolean {
        if (expectedEndpointId.isNullOrBlank() || incomingEndpointId.isBlank()) return false
        val expectedPeer = NeaRerIdentity.normalize(expectedPeerId) ?: return false
        val incomingPeer = NeaRerIdentity.normalize(incomingPeerId) ?: return false
        return expectedEndpointId == incomingEndpointId && expectedPeer == incomingPeer
    }
}
