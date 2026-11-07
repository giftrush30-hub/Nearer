package com.nearer.app

/**
 * Normalizes persistent nearby identities before privacy decisions.
 * Endpoint IDs are session-scoped and must never be persisted.
 */
internal object BlockedPeerPolicy {
    fun normalize(stableId: String?): String? {
        val value = stableId?.trim()?.uppercase(java.util.Locale.ROOT) ?: return null
        if (value.length !in setOf(6, 32) || value.any { it !in '0'..'9' && it !in 'A'..'F' }) return null
        return value
    }

    fun isBlocked(stableId: String?, blockedIds: Set<String>): Boolean {
        val normalized = normalize(stableId) ?: return false
        return blockedIds.any { normalize(it) == normalized }
    }

    fun add(stableId: String?, blockedIds: Set<String>): Set<String> {
        val normalized = normalize(stableId) ?: return blockedIds.toSet()
        return blockedIds.mapNotNull(::normalize).toSet() + normalized
    }
}
