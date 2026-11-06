package com.nearer.app

/**
 * Coordinates the two independent events needed before a bundle file can be
 * trusted when its SHA-256 is sent separately from the bulk FILE payload:
 * the file payload must finish and its digest must arrive.
 *
 * Nearby does not guarantee ordering between FILE and BYTES payload types, so
 * callers may report either event first. This tracker is bounded and scoped by
 * endpoint + payload ID to avoid cross-peer association and unbounded state.
 */
internal class DeferredFileDigestTracker(
    private val maxPendingEntries: Int = DEFAULT_MAX_PENDING_ENTRIES
) {
    init {
        require(maxPendingEntries > 0) { "Pending digest capacity must be positive" }
    }

    data class Ready(
        val endpointId: String,
        val payloadId: Long,
        val sha256: String
    )

    private data class Key(val endpointId: String, val payloadId: Long)
    private data class Entry(
        var expectedSizeBytes: Long?,
        var digest: String? = null,
        var fileCompleted: Boolean = false
    )

    private val entries = LinkedHashMap<Key, Entry>()

    @Synchronized
    fun registerFile(endpointId: String, payloadId: Long, expectedSizeBytes: Long): Ready? {
        require(endpointId.isNotBlank() && payloadId >= 0L && expectedSizeBytes >= 0L)
        val key = Key(endpointId, payloadId)
        val existing = entries[key]
        if (existing != null) {
            if (existing.expectedSizeBytes == null) existing.expectedSizeBytes = expectedSizeBytes
            else require(existing.expectedSizeBytes == expectedSizeBytes) {
                "Conflicting size for deferred-digest file"
            }
            return takeIfReady(key, existing)
        }
        require(entries.size < maxPendingEntries) { "Deferred-digest queue is full" }
        val entry = Entry(expectedSizeBytes)
        entries[key] = entry
        return takeIfReady(key, entry)
    }

    @Synchronized
    fun recordDigest(endpointId: String, payloadId: Long, sha256: String): Ready? {
        require(endpointId.isNotBlank() && payloadId >= 0L)
        require(SHA256.matches(sha256)) { "Invalid SHA-256 digest" }
        val key = Key(endpointId, payloadId)
        val entry = entries[key] ?: run {
            require(entries.size < maxPendingEntries) { "Deferred-digest queue is full" }
            Entry(expectedSizeBytes = null).also { entries[key] = it }
        }
        val normalized = sha256.lowercase()
        require(entry.digest == null || entry.digest == normalized) {
            "Conflicting deferred SHA-256 digest"
        }
        entry.digest = normalized
        return takeIfReady(key, entry)
    }

    @Synchronized
    fun markFileCompleted(endpointId: String, payloadId: Long): Ready? {
        require(endpointId.isNotBlank() && payloadId >= 0L)
        val key = Key(endpointId, payloadId)
        val entry = entries[key] ?: run {
            require(entries.size < maxPendingEntries) { "Deferred-digest queue is full" }
            Entry(expectedSizeBytes = -1L).also { entries[key] = it }
        }
        entry.fileCompleted = true
        return takeIfReady(key, entry)
    }

    @Synchronized
    fun remove(endpointId: String, payloadId: Long): Boolean =
        entries.remove(Key(endpointId, payloadId)) != null

    @Synchronized
    fun removeEndpoint(endpointId: String): Int {
        val keys = entries.keys.filter { it.endpointId == endpointId }
        keys.forEach(entries::remove)
        return keys.size
    }

    @Synchronized
    fun pendingCount(): Int = entries.size

    private fun takeIfReady(key: Key, entry: Entry): Ready? {
        val digest = entry.digest ?: return null
        if (!entry.fileCompleted || entry.expectedSizeBytes == null) return null
        entries.remove(key)
        return Ready(key.endpointId, key.payloadId, digest)
    }

    private companion object {
        const val DEFAULT_MAX_PENDING_ENTRIES = 16
        val SHA256 = Regex("^[a-fA-F0-9]{64}$")
    }
}
