package com.nearer.app

/** Pure validation and idempotent merge rules for group read receipts. */
internal object CommunityReadReceiptPolicy {
    const val MAX_IDS = 40

    fun isValid(messageIds: List<String>, readAt: Long, now: Long): Boolean =
        readAt > 0L && readAt <= now + 5 * 60_000L &&
            messageIds.isNotEmpty() && messageIds.size <= MAX_IDS &&
            messageIds.all { it.isNotBlank() && it.length <= 80 } &&
            messageIds.distinct().size == messageIds.size

    fun mayAcceptReader(
        authenticatedPeerId: String,
        claimedReaderPeerId: String,
        groupMemberPeerIds: Set<String>
    ): Boolean = authenticatedPeerId.isNotBlank() &&
        authenticatedPeerId == claimedReaderPeerId &&
        authenticatedPeerId in groupMemberPeerIds

    fun apply(
        current: Map<String, Long>,
        readerPeerId: String,
        readAt: Long
    ): Map<String, Long> {
        if (readerPeerId.isBlank() || readAt <= 0L) return current
        val previous = current[readerPeerId] ?: 0L
        return if (readAt > previous) current + (readerPeerId to readAt) else current
    }
}
