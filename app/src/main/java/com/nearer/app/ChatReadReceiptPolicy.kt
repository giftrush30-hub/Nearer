package com.nearer.app

/**
 * Validates per-conversation read receipts. A receipt may only update messages
 * originally sent to the authenticated peer that is reporting the read event.
 */
internal object ChatReadReceiptPolicy {
    const val MAX_MESSAGE_IDS = 40
    fun isValidPacket(version: Int, messageIds: List<String>, readAt: Long, @Suppress("UNUSED_PARAMETER") now: Long): Boolean =
        version == 1 &&
            readAt > 0L &&
            messageIds.isNotEmpty() &&
            messageIds.size <= MAX_MESSAGE_IDS &&
            messageIds.all { it.isNotBlank() && it.length <= 80 } &&
            messageIds.distinct().size == messageIds.size

    fun shouldMarkRead(
        outgoing: Boolean,
        messageConversationId: String,
        selectedConversationId: String,
        readReceiptSentAt: Long
    ): Boolean =
        !outgoing &&
            selectedConversationId.isNotBlank() &&
            messageConversationId == selectedConversationId &&
            readReceiptSentAt <= 0L

    fun canApplyReceipt(
        outgoing: Boolean,
        messageConversationId: String,
        actorPeerId: String,
        readAt: Long
    ): Boolean =
        outgoing &&
            actorPeerId.isNotBlank() &&
            messageConversationId == actorPeerId &&
            readAt > 0L
}
