package com.nearer.app

/** Bounded insertion-ordered history of authenticated outgoing delivery ACKs. */
internal object OutgoingReceiptHistoryPolicy {
    const val MAX_IDS = 2000

    fun append(existing: List<String>, messageId: String, maxIds: Int = MAX_IDS): List<String> {
        require(maxIds > 0) { "maxIds must be positive" }
        if (messageId.isBlank()) return existing.filter { it.isNotBlank() }.distinct().takeLast(maxIds)
        val ordered = LinkedHashSet(existing.filter { it.isNotBlank() })
        ordered.remove(messageId)
        ordered.add(messageId)
        while (ordered.size > maxIds) ordered.remove(ordered.first())
        return ordered.toList()
    }
}
