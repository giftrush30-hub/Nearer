package com.nearer.app

/**
 * Durable identity for one message delivery to one stable peer.
 *
 * The visible message ID stays the same across group fan-out; the peer component
 * prevents one recipient's acknowledgement from retiring another recipient's copy.
 */
internal object MessageDeliveryKey {
    fun forPeer(messageId: String, peerId: String): String {
        require(messageId.isNotBlank() && messageId.length <= 80)
        require(peerId.isNotBlank() && peerId.length <= 64)
        return "$messageId::$peerId"
    }

    /**
     * Old direct-chat outbox entries stored only messageId. Accept that legacy form,
     * but always migrate it to the new messageId+peerId key when restoring.
     */
    fun restoreKey(messageId: String, peerId: String, storedKey: String): String? {
        if (messageId.isBlank() || messageId.length > 80 ||
            peerId.isBlank() || peerId.length > 64) return null
        val expected = forPeer(messageId, peerId)
        if (storedKey.isNotBlank() && storedKey != messageId && storedKey != expected) return null
        return expected
    }
}
