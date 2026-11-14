package com.nearer.app

/**
 * Keeps private chat delivery recoverable across disconnects and process restarts.
 * Only uncertain outgoing messages are retried, and only on the exact same peer's
 * newly approved connection. Stable message IDs let the receiver deduplicate retries.
 */
internal object ChatDeliveryRecoveryPolicy {
    private val uncertainDeliveries = setOf("Sending", "Awaiting delivery", "Sent")

    fun restoreDelivery(outgoing: Boolean, delivery: String): String =
        if (outgoing && delivery in uncertainDeliveries) "Queued" else delivery

    fun shouldQueueAfterDisconnect(
        outgoing: Boolean,
        delivery: String,
        conversationId: String,
        disconnectedPeerId: String
    ): Boolean =
        outgoing &&
            disconnectedPeerId.isNotBlank() &&
            conversationId.ifBlank { "legacy" } == disconnectedPeerId &&
            delivery in uncertainDeliveries

    fun canRetryOnReconnect(
        outgoing: Boolean,
        delivery: String,
        conversationId: String,
        connectedPeerId: String,
        persistenceInProgress: Boolean
    ): Boolean =
        outgoing &&
            delivery == "Queued" &&
            connectedPeerId.isNotBlank() &&
            conversationId.ifBlank { "legacy" } == connectedPeerId &&
            !persistenceInProgress
}
