package com.nearer.app

/**
 * Remembers FILE SUCCESS callbacks that arrive before onPayloadReceived supplies
 * the FILE descriptor. Callback state is scoped by endpoint + payload ID.
 *
 * Nearby callbacks can be lost if a peer disappears between the two events.
 * Expiring markers prevents those orphaned IDs from occupying the bounded
 * tracker indefinitely or being mistaken for a much later receive.
 */
internal class IncomingPayloadCompletionTracker(
    private val maxEntries: Int = DEFAULT_MAX_ENTRIES,
    private val ttlMillis: Long = DEFAULT_TTL_MILLIS,
    private val nowMillis: () -> Long = { System.nanoTime() / 1_000_000L }
) {
    private data class Key(val endpointId: String, val payloadId: Long)
    private val completed = LinkedHashMap<Key, Long>()

    init {
        require(maxEntries > 0) { "Completion tracker capacity must be positive" }
        require(ttlMillis > 0L) { "Completion marker lifetime must be positive" }
    }

    @Synchronized
    fun markCompleted(endpointId: String, payloadId: Long) {
        require(endpointId.isNotBlank() && payloadId >= 0L)
        purgeExpired()
        val key = Key(endpointId, payloadId)
        completed.remove(key)
        completed[key] = nowMillis()
        while (completed.size > maxEntries) {
            completed.remove(completed.keys.first())
        }
    }

    @Synchronized
    fun isCompleted(endpointId: String, payloadId: Long): Boolean {
        purgeExpired()
        return Key(endpointId, payloadId) in completed
    }

    @Synchronized
    fun remove(endpointId: String, payloadId: Long): Boolean {
        purgeExpired()
        return completed.remove(Key(endpointId, payloadId)) != null
    }

    @Synchronized
    fun removeEndpoint(endpointId: String): Int {
        purgeExpired()
        val keys = completed.keys.filter { it.endpointId == endpointId }
        keys.forEach(completed::remove)
        return keys.size
    }

    @Synchronized
    fun pendingCount(): Int {
        purgeExpired()
        return completed.size
    }

    private fun purgeExpired() {
        val now = nowMillis()
        val expired = completed.entries
            .filter { entry ->
                // System.nanoTime has an arbitrary origin and wraps. Use elapsed
                // subtraction so a valid short interval across Long.MAX_VALUE is
                // not mistaken for a future timestamp.
                val ageMillis = now - entry.value
                ageMillis < 0L || ageMillis >= ttlMillis
            }
            .map { it.key }
        expired.forEach(completed::remove)
    }

    private companion object {
        const val DEFAULT_MAX_ENTRIES = 4096
        const val DEFAULT_TTL_MILLIS = 120_000L
    }
}
