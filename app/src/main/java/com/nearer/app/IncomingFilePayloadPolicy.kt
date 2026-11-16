package com.nearer.app

/**
 * Validates that a received file payload belongs to the peer that supplied its metadata.
 *
 * Stable peer identities survive endpoint changes across reconnects. Legacy peers without
 * a stable identity remain bound to the original endpoint.
 */
internal object IncomingFilePayloadPolicy {
    fun canAccept(
        expectedEndpointId: String?,
        expectedPeerId: String?,
        incomingEndpointId: String,
        incomingPeerId: String
    ): Boolean {
        if (incomingEndpointId.isBlank()) return false
        if (!expectedPeerId.isNullOrBlank()) {
            return incomingPeerId.isNotBlank() && expectedPeerId == incomingPeerId
        }
        return expectedEndpointId == null || expectedEndpointId == incomingEndpointId
    }
}
