package com.nearer.app

/**
 * Session-scoped duplicate suppression for trusted-peer reconnect requests.
 * Temporary Nearby endpoint IDs are never treated as persistent identity.
 */
internal class TrustedPeerReconnectTracker {
    private val pendingEndpoints = HashSet<String>()

    @Synchronized
    fun shouldRequest(endpointId: String, stablePeerId: String, hasPinnedTrust: Boolean, alreadyConnected: Boolean): Boolean {
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
