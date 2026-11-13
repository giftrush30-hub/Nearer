package com.nearer.app

/**
 * Measures elapsed time from a connection request/offer until identity
 * authentication completes. Uses caller-supplied monotonic timestamps so it
 * remains deterministic in JVM tests; endpoint IDs stay session-scoped.
 */
internal class NearbyConnectionTimingMeter {
    private val startedAtByEndpoint = HashMap<String, Long>()

    @Synchronized
    fun begin(endpointId: String, elapsedRealtimeMillis: Long): Boolean {
        if (endpointId.isBlank() || elapsedRealtimeMillis < 0L) return false
        if (endpointId in startedAtByEndpoint) return false
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
}
