package com.nearer.app

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NearbyInboxCommitPolicyTest {
    @Test fun senderIsNotAcknowledgedBeforeInboxCommit() {
        assertFalse(NearbyInboxCommitPolicy.canAcknowledge(inboxCommitted = false, dedupeCommitted = true))
    }

    @Test fun senderIsNotAcknowledgedBeforeDedupeCommit() {
        assertFalse(NearbyInboxCommitPolicy.canAcknowledge(inboxCommitted = true, dedupeCommitted = false))
    }

    @Test fun senderIsAcknowledgedOnlyAfterBothDurableWrites() {
        assertTrue(NearbyInboxCommitPolicy.canAcknowledge(inboxCommitted = true, dedupeCommitted = true))
    }

    @Test fun inboxIsNotRetiredUntilChatStoreCommits() {
        assertFalse(
            NearbyInboxCommitPolicy.canRetireInbox(
                inboxCommitted = true, dedupeCommitted = true, chatStoreCommitted = false
            )
        )
    }

    @Test fun inboxIsNotRetiredWhenDedupeWasNotCommitted() {
        assertFalse(
            NearbyInboxCommitPolicy.canRetireInbox(
                inboxCommitted = true, dedupeCommitted = false, chatStoreCommitted = true
            )
        )
    }

    @Test fun inboxRetiresOnlyAfterAllThreeCommitGates() {
        assertTrue(
            NearbyInboxCommitPolicy.canRetireInbox(
                inboxCommitted = true, dedupeCommitted = true, chatStoreCommitted = true
            )
        )
    }
}
