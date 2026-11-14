package com.nearer.app

import org.junit.Assert.*
import org.junit.Test

class BlockedPeerPolicyTest {
    @Test fun normalizesValidStableIdsAndRejectsSessionOrMalformedIds() {
        assertEquals("A1B2C3", BlockedPeerPolicy.normalize("a1b2c3"))
        assertEquals("0123456789ABCDEF0123456789ABCDEF", BlockedPeerPolicy.normalize("0123456789abcdef0123456789abcdef"))
        assertNull(BlockedPeerPolicy.normalize(null))
        assertNull(BlockedPeerPolicy.normalize("endpoint-session-id"))
        assertNull(BlockedPeerPolicy.normalize("123"))
        assertNull(BlockedPeerPolicy.normalize("GGGGGG"))
    }

    @Test fun blockedChecksUseStableIdentityNotDisplayNameOrEndpoint() {
        val blocked = setOf("a1b2c3")
        assertTrue(BlockedPeerPolicy.isBlocked("A1B2C3", blocked))
        assertFalse(BlockedPeerPolicy.isBlocked("endpoint-session-id", blocked))
        assertFalse(BlockedPeerPolicy.isBlocked("B1B2C3", blocked))
    }

    @Test fun addIsIdempotentAndDoesNotPersistInvalidIds() {
        val once = BlockedPeerPolicy.add("a1b2c3", emptySet())
        val twice = BlockedPeerPolicy.add("A1B2C3", once)
        assertEquals(setOf("A1B2C3"), twice)
        assertEquals(emptySet<String>(), BlockedPeerPolicy.add("not-an-id", emptySet()))
    }
}
