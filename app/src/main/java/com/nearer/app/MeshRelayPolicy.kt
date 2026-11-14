package com.nearer.app

/**
 * Mesh is currently a community-message transport, not a private-chat transport.
 * Keep the allow-list narrow so a private packet cannot be relayed by setting TTL.
 */
internal object MeshRelayPolicy {
    fun shouldRelay(enabled: Boolean, body: String, ttl: Int, hops: Int): Boolean =
        enabled && body.startsWith("NR_COMMUNITY:") && ttl in 1..4 && hops in 0..3
}
