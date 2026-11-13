package com.nearer.app

/**
 * Measures request/offer-to-authentication time with monotonic timestamps.
 * Endpoint IDs are transient and are removed after completion or cancellation.
 */
internal class NearbyConnectionTimingMeter {
    private val startedAtByEndpoint = HashMap<String, Long>()

    @Synchronized
    fun begin(endpointId: String, elapsedRealtimeMillis: Long): Boolean {
        if (endpointId.isBlank() || elapsedRealtimeMillis < 0L || endpointId in startedAtByEndpoint) return false
        startedAtByEndpoint[endpointId] = elapsedRealtimeMillis
        return true
    }

    @Synchronized
    fun finish(endpointId: String, elapsedRealtimeMillis: Long): Long? {
        val startedAt = startedAtByEndpoint.remove(endpointId) ?: return null
        if (elapsedRealtimeMillis < startedAt) return null
        return elapsedRealtimeMillis - startedAt
    }

    @Synchronized
    fun cancel(endpointId: String) {
        if (endpointId.isNotBlank()) startedAtByEndpoint.remove(endpointId)
    }

    @Synchronized
    fun clearAll() {
        startedAtByEndpoint.clear()
    }
}
