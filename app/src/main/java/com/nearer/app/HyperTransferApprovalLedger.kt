package com.nearer.app

/**
 * Service-owned, one-shot approval ledger for a single authenticated Nearby peer
 * and an exact HyperTransfer offer. A UI decision can never authorize a different
 * file, endpoint, identity, or later socket session.
 */
internal class HyperTransferApprovalLedger(
    private val nowMillis: () -> Long = System::currentTimeMillis,
    private val lifetimeMillis: Long = 10 * 60 * 1000L,
    private val maximumEntries: Int = 64
) {
    enum class Direction { INCOMING, OUTGOING }
    enum class State { WAITING_FOR_USER, APPROVED, DECLINED, CONSUMED }
    enum class OfferResult { ADDED, DUPLICATE, CONFLICT, REJECTED }
    private data class Entry(
        val endpointId: String,
        val peerId: String,
        val offer: HyperTransferTcpOfferCodec.Offer,
        val direction: Direction,
        val createdAt: Long,
        var state: State
    )
    private val entries = LinkedHashMap<String, Entry>()

    @Synchronized
    fun recordIncomingOffer(endpointId: String, peerId: String, offer: HyperTransferTcpOfferCodec.Offer): OfferResult =
        record(endpointId, peerId, offer, Direction.INCOMING)

    @Synchronized
    fun recordOutgoingOffer(endpointId: String, peerId: String, offer: HyperTransferTcpOfferCodec.Offer): OfferResult =
        record(endpointId, peerId, offer, Direction.OUTGOING)

    private fun record(endpoint: String, peer: String, offer: HyperTransferTcpOfferCodec.Offer, direction: Direction): OfferResult {
        prune()
        if (endpoint.isBlank() || NeaRerIdentity.normalize(peer) != peer ||
            runCatching { HyperTransferTcpOfferCodec.encodeOffer(offer) }.isFailure) return OfferResult.REJECTED
        val key = key(endpoint, offer.transferId)
        val old = entries[key]
        if (old != null) {
            return if (old.peerId == peer && old.offer == offer && old.direction == direction) OfferResult.DUPLICATE
            else OfferResult.CONFLICT
        }
        if (entries.size >= maximumEntries) return OfferResult.REJECTED
        entries[key] = Entry(endpoint, peer, offer.copy(
            transferId = offer.transferId.lowercase(), sha256 = offer.sha256.lowercase()
        ), direction, nowMillis(), State.WAITING_FOR_USER)
        return OfferResult.ADDED
    }

    /** Record the local receiver's decision only for the exact pending incoming offer. */
    @Synchronized
    fun decideIncoming(endpointId: String, peerId: String, transferId: String, accepted: Boolean): Boolean {
        val entry = matching(endpointId, peerId, transferId, Direction.INCOMING) ?: return false
        if (entry.state != State.WAITING_FOR_USER) return false
        entry.state = if (accepted) State.APPROVED else State.DECLINED
        return true
    }

    /** Record the remote receiver's decision for an exact outgoing offer. */
    @Synchronized
    fun recordRemoteDecision(endpointId: String, peerId: String, decision: HyperTransferTcpOfferCodec.Decision): Boolean {
        val entry = matching(endpointId, peerId, decision.transferId, Direction.OUTGOING) ?: return false
        if (entry.state != State.WAITING_FOR_USER) return false
        entry.state = if (decision.accepted) State.APPROVED else State.DECLINED
        return true
    }

    /**
     * Atomically consume authorization at the moment a socket handshake is about
     * to begin. It cannot be replayed after a failed/finished attempt.
     */
    @Synchronized
    fun consumeApproval(
        endpointId: String,
        peerId: String,
        offer: HyperTransferTcpOfferCodec.Offer,
        direction: Direction
    ): Boolean {
        val entry = matching(endpointId, peerId, offer.transferId, direction) ?: return false
        if (entry.offer != offer.copy(transferId = offer.transferId.lowercase(), sha256 = offer.sha256.lowercase()) ||
            entry.state != State.APPROVED) return false
        entry.state = State.CONSUMED
        return true
    }

    @Synchronized
    fun clearEndpoint(endpointId: String) {
        entries.entries.removeAll { it.value.endpointId == endpointId }
    }

    @Synchronized
    fun clearAll() = entries.clear()

    private fun matching(endpoint: String, peer: String, transferId: String, direction: Direction): Entry? {
        prune()
        if (endpoint.isBlank() || NeaRerIdentity.normalize(peer) != peer ||
            !transferId.matches(Regex("^[a-fA-F0-9]{64}$"))) return null
        return entries[key(endpoint, transferId)]?.takeIf {
            it.endpointId == endpoint && it.peerId == peer && it.direction == direction
        }
    }

    private fun prune() {
        val cutoff = nowMillis() - lifetimeMillis
        entries.entries.removeAll { it.value.createdAt < cutoff }
    }

    private fun key(endpoint: String, transferId: String) = "$endpoint|$transferId".lowercase()
}
