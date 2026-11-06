package com.nearer.app

/**
 * Correlates the three independently ordered events in a Nearby resume exchange:
 * a proposal BYTES payload, a decision BYTES payload, and the matching FILE/STREAM
 * payload callback. Nearby does not promise ordering across payload types, so a
 * caller must not assume that a decision arrives before its data payload.
 *
 * This tracker is intentionally transport-agnostic. The transport owns it per
 * connection, feeds decoded envelopes and payload callbacks into it, and calls
 * takeReady only when it is ready to validate/apply the decision. A ready entry
 * is consumed exactly once. Entries are bounded to prevent a peer from growing
 * memory use with unbounded control messages.
 */
internal class TransferResumeExchangeTracker(
    private val maxPendingExchanges: Int = DEFAULT_MAX_PENDING,
    private val nowMillis: () -> Long = { System.nanoTime() / 1_000_000L },
    private val entryTtlMillis: Long = DEFAULT_ENTRY_TTL_MILLIS
) {
    data class Key(val endpointId: String, val payloadId: Long)

    data class ReadyExchange(
        val key: Key,
        val proposal: TransferResumeNegotiation.Proposal,
        val decision: TransferResumeNegotiation.Decision
    )

    enum class Update {
        STORED,
        DUPLICATE,
        CONFLICT,
        CAPACITY_REACHED,
        INVALID_KEY
    }

    private data class Entry(
        var updatedAtMillis: Long,
        var proposal: TransferResumeNegotiation.Proposal? = null,
        var decision: TransferResumeNegotiation.Decision? = null,
        var dataPayloadSeen: Boolean = false,
        var consumed: Boolean = false
    )

    private val entries = LinkedHashMap<Key, Entry>()
    // Short-lived tombstones prevent late/duplicate callbacks from recreating a completed or rejected exchange.
    private val terminalEntries = LinkedHashMap<Key, Long>()

    init {
        require(entryTtlMillis > 0L) { "Resume exchange expiry must be positive" }
        require(maxPendingExchanges in 1..MAX_ALLOWED_PENDING) {
            "Resume exchange capacity is outside the safe range"
        }
    }

    @Synchronized
    fun recordProposal(endpointId: String, envelope: TransferResumeMessageCodec.Envelope): Update {
        val key = key(endpointId, envelope.payloadId) ?: return Update.INVALID_KEY
        pruneExpired()
        if (terminalEntries.containsKey(key)) return Update.DUPLICATE
        val entry = entryFor(key) ?: return Update.CAPACITY_REACHED
        val existing = entry.proposal
        if (existing != null) return if (existing == envelope.proposal) Update.DUPLICATE else invalidate(key)
        entry.proposal = envelope.proposal
        entry.updatedAtMillis = nowMillis()
        return Update.STORED
    }

    @Synchronized
    fun recordDecision(endpointId: String, envelope: TransferResumeMessageCodec.DecisionEnvelope): Update {
        val key = key(endpointId, envelope.payloadId) ?: return Update.INVALID_KEY
        pruneExpired()
        if (terminalEntries.containsKey(key)) return Update.DUPLICATE
        val entry = entryFor(key) ?: return Update.CAPACITY_REACHED
        val existing = entry.decision
        if (existing != null) return if (existing == envelope.decision) Update.DUPLICATE else invalidate(key)
        entry.decision = envelope.decision
        entry.updatedAtMillis = nowMillis()
        return Update.STORED
    }

    @Synchronized
    fun recordDataPayload(endpointId: String, payloadId: Long): Update {
        val key = key(endpointId, payloadId) ?: return Update.INVALID_KEY
        pruneExpired()
        if (terminalEntries.containsKey(key)) return Update.DUPLICATE
        val entry = entryFor(key) ?: return Update.CAPACITY_REACHED
        if (entry.dataPayloadSeen) return Update.DUPLICATE
        entry.dataPayloadSeen = true
        entry.updatedAtMillis = nowMillis()
        return Update.STORED
    }

    /**
     * Returns a complete exchange only after all three events are present.
     * Calling this method consumes it, so duplicate callbacks cannot apply an
     * offset twice. Incomplete entries remain pending until completed or cleared.
     */
    @Synchronized
    fun takeReady(endpointId: String, payloadId: Long): ReadyExchange? {
        pruneExpired()
        val key = key(endpointId, payloadId) ?: return null
        val entry = entries[key] ?: return null
        if (entry.consumed || !entry.dataPayloadSeen) return null
        val proposal = entry.proposal ?: return null
        val decision = entry.decision ?: return null
        entry.consumed = true
        entries.remove(key)
        rememberTerminal(key)
        return ReadyExchange(key, proposal, decision)
    }

    @Synchronized
    fun clearEndpoint(endpointId: String) {
        val iterator = entries.keys.iterator()
        while (iterator.hasNext()) {
            if (iterator.next().endpointId == endpointId) iterator.remove()
        }
        val terminalIterator = terminalEntries.keys.iterator()
        while (terminalIterator.hasNext()) {
            if (terminalIterator.next().endpointId == endpointId) terminalIterator.remove()
        }
    }

    @Synchronized
    fun clear() {
        entries.clear()
        terminalEntries.clear()
    }

    @Synchronized
    fun pendingCount(): Int {
        pruneExpired()
        return entries.size
    }

    private fun entryFor(key: Key): Entry? {
        entries[key]?.let { return it }
        if (entries.size >= maxPendingExchanges) return null
        return Entry(updatedAtMillis = nowMillis()).also { entries[key] = it }
    }

    private fun invalidate(key: Key): Update {
        entries.remove(key)
        rememberTerminal(key)
        return Update.CONFLICT
    }

    private fun rememberTerminal(key: Key) {
        terminalEntries[key] = nowMillis()
        val maxTerminalEntries = (maxPendingExchanges * 2).coerceAtMost(MAX_ALLOWED_PENDING * 2)
        while (terminalEntries.size > maxTerminalEntries) {
            val oldest = terminalEntries.keys.iterator()
            if (oldest.hasNext()) { oldest.next(); oldest.remove() } else break
        }
    }

    private fun pruneExpired() {
        val now = nowMillis()
        val iterator = entries.entries.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next().value
            if (now - entry.updatedAtMillis >= entryTtlMillis || now < entry.updatedAtMillis) iterator.remove()
        }
        val terminalIterator = terminalEntries.entries.iterator()
        while (terminalIterator.hasNext()) {
            val createdAt = terminalIterator.next().value
            if (now - createdAt >= entryTtlMillis || now < createdAt) terminalIterator.remove()
        }
    }

    private fun key(endpointId: String, payloadId: Long): Key? =
        if (endpointId.isBlank() || payloadId < 0L) null else Key(endpointId, payloadId)

    private companion object {
        const val DEFAULT_MAX_PENDING = 64
        const val DEFAULT_ENTRY_TTL_MILLIS = 120_000L
        const val MAX_ALLOWED_PENDING = 256
    }
}
