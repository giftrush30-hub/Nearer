package com.nearer.app

/**
 * Chooses a Nearby topology only when both endpoints can participate safely.
 *
 * The high-throughput Point-to-Point topology is strictly one-to-one. It must
 * never be selected as a unilateral upgrade to an existing Cluster session:
 * both devices need to enter the mode before discovery/connection begins.
 */
internal object TransferConnectionModePolicy {
    enum class RequestedMode { MULTI_PEER, HIGH_SPEED_ONE_TO_ONE }
    enum class NearbyTopology { CLUSTER, POINT_TO_POINT }
    enum class FallbackReason {
        NONE,
        PEER_COUNT_NOT_ONE,
        PEER_DOES_NOT_SUPPORT_MODE,
        BOTH_PEERS_NOT_READY
    }

    data class Decision(
        val topology: NearbyTopology,
        val fallbackReason: FallbackReason
    ) {
        val highSpeedEnabled: Boolean
            get() = topology == NearbyTopology.POINT_TO_POINT
    }

    fun decide(
        requestedMode: RequestedMode,
        connectedPeerCount: Int,
        peerSupportsHighSpeed: Boolean,
        bothPeersReady: Boolean
    ): Decision {
        require(connectedPeerCount >= 0) { "Peer count cannot be negative" }

        if (requestedMode == RequestedMode.MULTI_PEER) {
            return Decision(NearbyTopology.CLUSTER, FallbackReason.NONE)
        }

        if (connectedPeerCount != 1) {
            return Decision(NearbyTopology.CLUSTER, FallbackReason.PEER_COUNT_NOT_ONE)
        }
        if (!peerSupportsHighSpeed) {
            return Decision(NearbyTopology.CLUSTER, FallbackReason.PEER_DOES_NOT_SUPPORT_MODE)
        }
        if (!bothPeersReady) {
            return Decision(NearbyTopology.CLUSTER, FallbackReason.BOTH_PEERS_NOT_READY)
        }

        return Decision(NearbyTopology.POINT_TO_POINT, FallbackReason.NONE)
    }
}
