package com.nearer.app

/** Local inbox read-state rules. These are not network read receipts. */
internal object ChatInboxStatePolicy {
    fun unreadCount(messages: List<ChatMessage>, readThroughTimestamp: Long): Int =
        messages.count { message ->
            !message.outgoing &&
                message.body.isNotBlank() &&
                message.timestamp > readThroughTimestamp
        }
}
