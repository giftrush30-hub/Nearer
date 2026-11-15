package com.nearer.app

/**
 * The legacy Wi-Fi Direct line protocol has no authenticated application-layer
 * session. Pairing-code approval alone does not encrypt or authenticate MSG/ACK
 * frames, so it must never be used as a private-chat transport.
 *
 * File-transfer compatibility remains separate; chat uses Nearby Connections'
 * authenticated and encrypted message session.
 */
internal object LegacySocketChatPolicy {
    fun mayUseSocketChat(): Boolean = false
    fun mayAcceptSocketChatFrame(): Boolean = false
    fun maySendCommunityOrGameOverSocket(): Boolean = false
    fun mayAcceptCommunityOrGameSocketFrame(): Boolean = false
}
