package com.nearer.app

/**
 * Lightweight index for the local conversation inbox. Messages remain stored
 * in the existing offline history; this groups them without requiring cloud
 * accounts or a database migration.
 */
internal data class ConversationSummary(
    val conversationId: String,
    val displayName: String,
    val lastMessage: ChatMessage,
    val messageCount: Int
)

internal object ConversationIndex {
    fun build(messages: List<ChatMessage>): List<ConversationSummary> =
        messages.groupBy { message -> message.conversationId.ifBlank { "legacy:${message.id}" } }
            .map { (id, thread) ->
                val latest = thread.maxByOrNull { it.timestamp }!!
                ConversationSummary(
                    conversationId = id,
                    displayName = latest.conversationName.ifBlank { "Nearby person" },
                    lastMessage = latest,
                    messageCount = thread.size
                )
            }
            .sortedByDescending { it.lastMessage.timestamp }

    fun messagesFor(messages: List<ChatMessage>, conversationId: String): List<ChatMessage> =
        messages.filter { (it.conversationId.ifBlank { "legacy" }) == conversationId }
            .sortedBy { it.timestamp }
}
