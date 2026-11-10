package com.nearer.app

/**
 * Builds a stable inbox from the locally stored message timeline.
 * Legacy messages without a peer identity remain together instead of disappearing.
 */
internal data class ConversationSummary(
    val key: String,
    val name: String,
    val messages: List<ChatMessage>
) {
    val latestMessage: ChatMessage
        get() = messages.maxByOrNull { it.timestamp }!!
}

internal object ConversationListPolicy {
    fun build(messages: List<ChatMessage>): List<ConversationSummary> {
        return messages
            .groupBy(::conversationKey)
            .map { (key, items) ->
                val latest = items.maxByOrNull { it.timestamp }!!
                val name = latest.conversationName
                    .ifBlank { items.lastOrNull { it.conversationName.isNotBlank() }?.conversationName.orEmpty() }
                    .ifBlank { if (latest.outgoing) "Nearby conversation" else "Nearby contact" }
                ConversationSummary(key, name, items.sortedBy { it.timestamp })
            }
            .sortedByDescending { it.latestMessage.timestamp }
    }

    private fun conversationKey(message: ChatMessage): String = when {
        message.conversationId.isNotBlank() -> "id:${message.conversationId}"
        message.conversationName.isNotBlank() -> "name:${message.conversationName}"
        else -> "legacy"
    }
}
