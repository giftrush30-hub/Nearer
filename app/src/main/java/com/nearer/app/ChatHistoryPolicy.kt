package com.nearer.app

/**
 * Keeps the on-device chat timeline bounded so long-running conversations
 * cannot grow the Compose state list without limit.
 */
internal object ChatHistoryPolicy {
    const val MAX_MESSAGES = 500

    fun trim(messages: MutableList<ChatMessage>, limit: Int = MAX_MESSAGES) {
        require(limit >= 0) { "Chat history limit cannot be negative" }
        while (messages.size > limit) messages.removeAt(0)
    }
}
