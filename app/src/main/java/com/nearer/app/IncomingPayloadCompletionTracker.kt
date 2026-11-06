package com.nearer.app

/**
 * Remembers FILE SUCCESS callbacks that arrive before onPayloadReceived supplies
 * the FILE descriptor. Callback state is scoped by endpoint + payload ID.
 */
internal class IncomingPayloadCompletionTracker(
    private val maxEntries: Int = DEFAULT_MAX_ENTRIES
) {
    private data class Key(val endpointId: String, val payloadId: Long)
    private val completed = LinkedHashSet<Key>()

    init {
        require(maxEntries > 0) { "Completion tracker capacity must be positive" }
    }

    @Synchronized
    fun markCompleted(endpointId: String, payloadId: Long) {
        require(endpointId.isNotBlank() && payloadId >= 0L)
        val key = Key(endpointId, payloadId)
        completed.remove(key)
        completed.add(key)
        while (completed.size > maxEntries) completed.remove(completed.first())
    }

    @Synchronized
    fun isCompleted(endpointId: String, payloadId: Long): Boolean =
        Key(endpointId, payloadId) in completed

    @Synchronized
    fun remove(endpointId: String, payloadId: Long): Boolean =
        completed.remove(Key(endpointId, payloadId))

    @Synchronized
    fun removeEndpoint(endpointId: String): Int {
        val keys = completed.filter { it.endpointId == endpointId }
        keys.forEach(completed::remove)
        return keys.size
    }

    @Synchronized
    fun pendingCount(): Int = completed.size

    private companion object { const val DEFAULT_MAX_ENTRIES = 4096 }
}
