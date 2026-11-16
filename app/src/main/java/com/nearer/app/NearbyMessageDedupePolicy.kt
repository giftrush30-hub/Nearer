package com.nearer.app

/**
 * Gives every incoming message a deduplication identity scoped to its authenticated
 * sender. Scoping prevents one peer's ID from suppressing another peer's message
 * if IDs collide or a peer deliberately reuses an ID.
 */
internal object NearbyMessageDedupePolicy {
    fun key(peerId: String, messageId: String): String? {
        val normalizedPeer = NeaRerIdentity.normalize(peerId) ?: return null
        if (messageId.isBlank() || messageId.length > 80) return null
        return "$normalizedPeer|$messageId"
    }

    /** Raw IDs are accepted as a compatibility fallback for pre-scoping history. */
    fun hasSeen(history: Set<String>, peerId: String, messageId: String): Boolean {
        val scoped = key(peerId, messageId) ?: return false
        return scoped in history || messageId in history
    }
}
