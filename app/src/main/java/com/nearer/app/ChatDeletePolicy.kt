package com.nearer.app

/** Guards the delete-for-everyone control event against forged authors and undelivered messages. */
internal object ChatDeletePolicy {
    fun canDeleteForEveryone(
        outgoing: Boolean,
        delivery: String,
        readAt: Long,
        alreadyDeleted: Boolean
    ): Boolean =
        outgoing && !alreadyDeleted && (delivery == "Delivered" || readAt > 0L)

    fun canApplyIncoming(
        outgoing: Boolean,
        conversationId: String,
        actorPeerId: String,
        alreadyDeleted: Boolean
    ): Boolean =
        !outgoing && !alreadyDeleted && actorPeerId.isNotBlank() && conversationId == actorPeerId

    fun isValidPacket(version: Int, messageId: String, deletedAt: Long): Boolean =
        version == 1 && messageId.isNotBlank() && messageId.length <= 80 && deletedAt > 0L
}
