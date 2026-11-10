package com.nearer.app

/**
 * A route learned from a peer. It is a hint, not proof of delivery.
 * Callers should only add advertisements received through an authenticated peer session.
 */
internal data class MeshRoute(
    val destinationPeerId: String,
    val nextHopPeerId: String,
    val hopCount: Int,
    val learnedFromPeerId: String,
    val lastUpdatedMillis: Long
)

internal class MeshRouteTable(
    private val maxHops: Int = MeshRelayPolicy.MAX_HOPS,
    private val maxAgeMillis: Long = 90_000L
) {
    private val routes = LinkedHashMap<String, MeshRoute>()

    @Synchronized
    fun learn(
        destinationPeerId: String,
        nextHopPeerId: String,
        advertisedHops: Int,
        learnedFromPeerId: String,
        localPeerId: String,
        nowMillis: Long
    ): Boolean {
        if (destinationPeerId.isBlank() || nextHopPeerId.isBlank() ||
            learnedFromPeerId.isBlank() || localPeerId.isBlank()) return false
        if (destinationPeerId == localPeerId || nextHopPeerId == localPeerId) return false
        if (advertisedHops !in 0 until maxHops || destinationPeerId == nextHopPeerId) return false
        val candidate = MeshRoute(
            destinationPeerId = destinationPeerId,
            nextHopPeerId = nextHopPeerId,
            hopCount = advertisedHops + 1,
            learnedFromPeerId = learnedFromPeerId,
            lastUpdatedMillis = nowMillis
        )
        val current = routes[destinationPeerId]
        if (current == null || isStale(current, nowMillis) ||
            candidate.hopCount < current.hopCount ||
            (candidate.hopCount == current.hopCount && candidate.lastUpdatedMillis > current.lastUpdatedMillis)
        ) {
            routes[destinationPeerId] = candidate
            return true
        }
        return false
    }

    @Synchronized
    fun nextHop(destinationPeerId: String, nowMillis: Long): MeshRoute? {
        prune(nowMillis)
        return routes[destinationPeerId]
    }

    @Synchronized
    fun removeNextHop(peerId: String) {
        routes.entries.removeAll { it.value.nextHopPeerId == peerId || it.value.learnedFromPeerId == peerId }
    }

    @Synchronized
    fun snapshot(nowMillis: Long): List<MeshRoute> {
        prune(nowMillis)
        return routes.values.toList()
    }

    @Synchronized
    fun prune(nowMillis: Long) {
        routes.entries.removeAll { isStale(it.value, nowMillis) }
    }

    private fun isStale(route: MeshRoute, nowMillis: Long): Boolean =
        nowMillis < route.lastUpdatedMillis || nowMillis - route.lastUpdatedMillis > maxAgeMillis
}
