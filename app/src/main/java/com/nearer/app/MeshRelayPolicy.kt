package com.nearer.app

/**
 * Pure, deterministic rules for deciding whether a received mesh envelope may be
 * delivered locally or forwarded. This class deliberately does not perform I/O,
 * select transport endpoints, or enable relaying by itself.
 *
 * The payload field is named ciphertext: forwarding plaintext is not permitted by
 * the intended mesh protocol. Production forwarding must remain disabled until
 * end-to-end encryption and recipient-key verification are integrated.
 */
internal data class MeshRelayEnvelope(
    val messageId: String,
    val originPeerId: String,
    val destinationPeerId: String,
    val ciphertext: String,
    val ttlRemaining: Int,
    val hopCount: Int,
    val visitedPeerIds: List<String>,
    val createdAtMillis: Long
)

internal enum class MeshRelayDisposition {
    DELIVER_LOCALLY,
    FORWARD,
    DROP_INVALID,
    DROP_EXPIRED,
    DROP_DUPLICATE,
    DROP_LOOP,
    DROP_HOP_LIMIT,
    DROP_RELAY_NOT_CONSENTED
}

internal data class MeshRelayDecision(
    val disposition: MeshRelayDisposition,
    val forwardedEnvelope: MeshRelayEnvelope? = null
)

/**
 * Safety limits for the first NeaRer mesh protocol iteration.
 * A maximum of four relay hops is a protocol ceiling, not a promise of range.
 */
internal object MeshRelayPolicy {
    const val MAX_HOPS = 4
    const val MAX_TTL = 4
    const val MAX_MESSAGE_ID_LENGTH = 128
    const val MAX_PEER_ID_LENGTH = 64
    const val MAX_CIPHERTEXT_LENGTH = 64 * 1024
    const val DEFAULT_MAX_AGE_MILLIS = 24L * 60L * 60L * 1000L

    fun evaluate(
        envelope: MeshRelayEnvelope,
        localPeerId: String,
        relayConsentGranted: Boolean,
        alreadySeen: Boolean,
        nowMillis: Long,
        maxAgeMillis: Long = DEFAULT_MAX_AGE_MILLIS
    ): MeshRelayDecision {
        if (localPeerId.isBlank() || localPeerId.length > MAX_PEER_ID_LENGTH ||
            envelope.messageId.isBlank() || envelope.messageId.length > MAX_MESSAGE_ID_LENGTH ||
            envelope.originPeerId.isBlank() || envelope.originPeerId.length > MAX_PEER_ID_LENGTH ||
            envelope.destinationPeerId.isBlank() || envelope.destinationPeerId.length > MAX_PEER_ID_LENGTH ||
            envelope.ciphertext.isBlank() || envelope.ciphertext.length > MAX_CIPHERTEXT_LENGTH ||
            envelope.ttlRemaining !in 0..MAX_TTL ||
            envelope.hopCount !in 0..MAX_HOPS ||
            envelope.visitedPeerIds.size > MAX_HOPS ||
            envelope.visitedPeerIds.any { it.isBlank() || it.length > MAX_PEER_ID_LENGTH } ||
            envelope.visitedPeerIds.toSet().size != envelope.visitedPeerIds.size ||
            envelope.createdAtMillis <= 0L || nowMillis < envelope.createdAtMillis ||
            maxAgeMillis <= 0L
        ) return MeshRelayDecision(MeshRelayDisposition.DROP_INVALID)

        if (nowMillis - envelope.createdAtMillis > maxAgeMillis) {
            return MeshRelayDecision(MeshRelayDisposition.DROP_EXPIRED)
        }
        if (alreadySeen) return MeshRelayDecision(MeshRelayDisposition.DROP_DUPLICATE)

        // A destination can accept its packet even when no further relay budget remains.
        if (envelope.destinationPeerId == localPeerId) {
            return MeshRelayDecision(MeshRelayDisposition.DELIVER_LOCALLY)
        }
        if (localPeerId in envelope.visitedPeerIds || localPeerId == envelope.originPeerId) {
            return MeshRelayDecision(MeshRelayDisposition.DROP_LOOP)
        }
        if (!relayConsentGranted) {
            return MeshRelayDecision(MeshRelayDisposition.DROP_RELAY_NOT_CONSENTED)
        }
        if (envelope.ttlRemaining <= 0 || envelope.hopCount >= MAX_HOPS ||
            envelope.visitedPeerIds.size >= MAX_HOPS
        ) return MeshRelayDecision(MeshRelayDisposition.DROP_HOP_LIMIT)

        return MeshRelayDecision(
            disposition = MeshRelayDisposition.FORWARD,
            forwardedEnvelope = envelope.copy(
                ttlRemaining = envelope.ttlRemaining - 1,
                hopCount = envelope.hopCount + 1,
                visitedPeerIds = envelope.visitedPeerIds + localPeerId
            )
        )
    }
}
