package com.nearer.app

/** Identifies the sender and immutable content details of an incoming transfer. */
internal data class IncomingFileMetadataIdentity(
    val endpointId: String,
    val peerId: String,
    val name: String,
    val size: Long,
    val sha256: String
)

/** Prevents another nearby peer or conflicting metadata from hijacking a payload ID. */
internal object IncomingFileMetadataPolicy {
    fun canAccept(
        existing: IncomingFileMetadataIdentity?,
        incoming: IncomingFileMetadataIdentity
    ): Boolean {
        if (incoming.endpointId.isBlank() || incoming.name.isBlank() || incoming.size < 0L) return false
        if (incoming.sha256.isNotBlank() && !incoming.sha256.matches(Regex("[a-fA-F0-9]{64}"))) return false
        if (existing == null) return true

        val samePeer = existing.peerId.isNotBlank() && incoming.peerId.isNotBlank() &&
            existing.peerId == incoming.peerId
        val sameEndpoint = existing.endpointId == incoming.endpointId
        if (!samePeer && !sameEndpoint) return false

        val compatibleHash = existing.sha256.isBlank() || incoming.sha256.isBlank() ||
            existing.sha256.equals(incoming.sha256, ignoreCase = true)
        return existing.name == incoming.name && existing.size == incoming.size && compatibleHash
    }
}
