package com.nearer.app

/** Pure policy for separating a user's own mesh traffic from third-party transit. */
internal object MeshRelayParticipationPolicy {
    fun mayAcceptPacket(enabled: Boolean, packetDestinationId: String, localId: String): Boolean =
        enabled || packetDestinationId == localId

    fun mayForwardPacket(enabled: Boolean, packetOriginId: String, localId: String): Boolean =
        enabled || packetOriginId == localId

    fun mayAcceptReceipt(enabled: Boolean, receiptOriginId: String, localId: String): Boolean =
        enabled || receiptOriginId == localId

    fun mayForwardReceipt(enabled: Boolean, receiptDestinationId: String, localId: String): Boolean =
        enabled || receiptDestinationId == localId

    fun mayAdvertiseRoutes(enabled: Boolean): Boolean = enabled
}
