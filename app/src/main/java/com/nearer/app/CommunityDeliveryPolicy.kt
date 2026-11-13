package com.nearer.app

/** Deterministic per-recipient delivery state for a single community message. */
internal object CommunityDeliveryPolicy {
    data class Summary(val delivered: Int, val queued: Int, val notVerified: Int, val total: Int)

    fun initialStatuses(targetPeerIds: Set<String>, queuedPeerIds: Set<String>): Map<String, String> =
        targetPeerIds.associateWith { peerId ->
            if (peerId in queuedPeerIds) "Queued" else "Not verified"
        }

    fun applyStatus(current: Map<String, String>, peerId: String, status: String): Map<String, String>? {
        if (peerId !in current || status !in setOf("Queued", "Delivered", "Failed")) return null
        return current + (peerId to status)
    }

    fun summarize(statuses: Map<String, String>): Summary = Summary(
        delivered = statuses.values.count { it == "Delivered" },
        queued = statuses.values.count { it == "Queued" },
        notVerified = statuses.values.count { it == "Not verified" },
        total = statuses.size
    )
}
