package com.nearer.app

/**
 * Both endpoints must agree before NeaRer overlaps independent FILE payloads.
 * Missing capability data (including an older peer) safely falls back to one.
 */
internal object TransferPeerWindowPolicy {
    fun recommended(localWindow: Int, peerWindow: Int?): Int {
        require(localWindow in 1..TransferConcurrencyPolicy.NORMAL_WINDOW) {
            "Local transfer window is outside the supported range"
        }
        if (peerWindow == null) return 1
        require(peerWindow in 1..TransferConcurrencyPolicy.NORMAL_WINDOW) {
            "Peer transfer window is outside the supported range"
        }
        return minOf(localWindow, peerWindow)
    }
}
