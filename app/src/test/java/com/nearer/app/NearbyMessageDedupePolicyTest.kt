package com.nearer.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NearbyMessageDedupePolicyTest {
    @Test fun sameMessageIdFromDifferentPeersHasDifferentKeys() {
        val first = NearbyMessageDedupePolicy.key("ABCDEF234567", "message-1")
        val second = NearbyMessageDedupePolicy.key("BCDEFG345678", "message-1")
        assertEquals("ABCDEF234567|message-1", first)
        assertEquals("BCDEFG345678|message-1", second)
        assertFalse(first == second)
    }

    @Test fun peerIdsAreNormalizedBeforeKeying() {
        assertEquals(
            NearbyMessageDedupePolicy.key("abcdef234567", "message-1"),
            NearbyMessageDedupePolicy.key(" ABCDEF234567 ", "message-1")
        )
    }

    @Test fun invalidPeerOrMessageIdCannotProduceAKey() {
        assertEquals(null, NearbyMessageDedupePolicy.key("not-a-peer", "message-1"))
        assertEquals(null, NearbyMessageDedupePolicy.key("ABCDEF234567", ""))
        assertEquals(null, NearbyMessageDedupePolicy.key("ABCDEF234567", "x".repeat(81)))
    }

    @Test fun historyLookupSupportsPeerScopedAndLegacyEntries() {
        val peer = "ABCDEF234567"
        val message = "message-1"
        assertTrue(NearbyMessageDedupePolicy.hasSeen(setOf("$peer|$message"), peer, message))
        assertTrue(NearbyMessageDedupePolicy.hasSeen(setOf(message), peer, message))
        assertFalse(NearbyMessageDedupePolicy.hasSeen(setOf("$peer|other"), peer, message))
        assertFalse(NearbyMessageDedupePolicy.hasSeen(setOf("$peer|$message"), "BCDEFG345678", message))
    }
}
