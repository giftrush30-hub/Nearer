package com.nearer.app

/**
 * Session-scoped duplicate suppression for trusted-peer reconnect requests.
 *
 * Nearby endpoint IDs are temporary and must never become identity. The stable
 * NeaRer ID is validated separately, and trust is supplied by the pinned-key
 * store. A lost/connected endpoint is cleared so a later discovery can retry.
 */
internal class TrustedPeerReconnectTracker {
    private val pendingEndpoints = HashSet<String>()

    @Synchronized
    fun shouldRequest(
        endpointId: String,
        stablePeerId: String,
        hasPinnedTrust: Boolean,
        alreadyConnected: Boolean
    ): Boolean {
        if (endpointId.isBlank() || !hasPinnedTrust || alreadyConnected) return false
        if (NeaRerIdentity.normalize(stablePeerId) == null) return false
        return pendingEndpoints.add(endpointId)
    }

    @Synchronized
    fun clear(endpointId: String) {
        if (endpointId.isNotBlank()) pendingEndpoints.remove(endpointId)
    }

    @Synchronized
    fun clearAll() {
        pendingEndpoints.clear()
    }
}
