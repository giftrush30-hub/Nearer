package com.nearer.app

/**
 * Pure inbox projection. Search spans the complete locally retained conversation,
 * while ordering uses the newest message and locally pinned identities.
 */
internal object ChatInboxPolicy {
    fun conversationIds(
        messages: List<ChatMessage>,
        recipients: List<MeshRecipient>,
        query: String = "",
        filter: String = "All",
        pinnedIds: Set<String> = emptySet()
    ): List<String> {
        val latestByConversation = messages
            .filter { it.conversationId.isNotBlank() }
            .groupBy { it.conversationId }
            .mapValues { (_, conversation) -> conversation.maxByOrNull { it.timestamp }!! }
        val recipientById = recipients.associateBy { it.peerId }
        val normalizedQuery = query.trim()

        return (latestByConversation.keys + recipients.map { it.peerId })
            .distinct()
            .filter { id ->
                val recipient = recipientById[id]
                when (filter) {
                    "Nearby" -> recipient?.isDirect == true
                    "Mesh" -> recipient != null && !recipient.isDirect
                    "Offline" -> recipient == null
                    else -> true
                }
            }
            .filter { id ->
                if (normalizedQuery.isBlank()) true else {
                    val name = recipientById[id]?.displayName
                        ?: latestByConversation[id]?.conversationName.orEmpty()
                    name.contains(normalizedQuery, ignoreCase = true) ||
                        id.contains(normalizedQuery, ignoreCase = true) ||
                        messages.any { it.conversationId == id && it.body.contains(normalizedQuery, ignoreCase = true) }
                }
            }
            .sortedWith(
                compareByDescending<String> { it in pinnedIds }
                    .thenByDescending { latestByConversation[it]?.timestamp ?: 0L }
            )
    }
}
