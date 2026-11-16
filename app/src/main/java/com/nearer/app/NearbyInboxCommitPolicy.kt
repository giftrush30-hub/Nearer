package com.nearer.app

/**
 * Defines the durability gates for incoming Nearby delivery.
 *
 * A sender acknowledgement is allowed only after the encrypted inbox and
 * duplicate-protection record are durable. The inbox entry is retired only
 * after the chat/community store also confirms its commit.
 */
internal object NearbyInboxCommitPolicy {
    fun canAcknowledge(inboxCommitted: Boolean, dedupeCommitted: Boolean): Boolean =
        inboxCommitted && dedupeCommitted

    fun canRetireInbox(
        inboxCommitted: Boolean,
        dedupeCommitted: Boolean,
        chatStoreCommitted: Boolean
    ): Boolean = inboxCommitted && dedupeCommitted && chatStoreCommitted
}
