package com.nearer.app

/** Authenticates and bounds direct-chat read receipts. */
internal object ChatReadReceiptPolicy {
    const val MAX_MESSAGE_IDS = 40

    fun isValidPacket(version: Int, messageIds: List<String>, readAt: Long, now: Long): Boolean =
        version == 1 &&
            readAt > 0L &&
            readAt <= now + 5 * 60_000L &&
            messageIds.isNotEmpty() &&
            messageIds.size <= MAX_MESSAGE_IDS &&
            messageIds.all { it.isNotBlank() && it.length <= 80 } &&
            messageIds.distinct().size == messageIds.size

    fun shouldMarkRead(
        outgoing: Boolean,
        messageConversationId: String,
        selectedConversationId: String,
        readReceiptSentAt: Long,
        locallyReadAt: Long
    ): Boolean = !outgoing && selectedConversationId.isNotBlank() &&
        messageConversationId == selectedConversationId &&
        readReceiptSentAt <= 0L && locallyReadAt <= 0L

    fun shouldSendReceipt(
        outgoing: Boolean,
        messageConversationId: String,
        recipientPeerId: String,
        locallyReadAt: Long,
        readReceiptSentAt: Long
    ): Boolean = !outgoing && recipientPeerId.isNotBlank() &&
        messageConversationId == recipientPeerId &&
        locallyReadAt > 0L && readReceiptSentAt <= 0L

    fun canApplyReceipt(
        outgoing: Boolean,
        messageConversationId: String,
        actorPeerId: String,
        readAt: Long
    ): Boolean = outgoing && actorPeerId.isNotBlank() &&
        messageConversationId == actorPeerId && readAt > 0L
}
