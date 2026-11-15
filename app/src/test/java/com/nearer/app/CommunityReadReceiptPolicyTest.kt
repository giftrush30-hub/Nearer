package com.nearer.app

import org.junit.Assert.*
import org.junit.Test

class CommunityReadReceiptPolicyTest {
    @Test fun validatesUniqueBoundedGroupMessageIds() {
        assertTrue(CommunityReadReceiptPolicy.isValid(listOf("m1", "m2"), 100L, 100L))
        assertFalse(CommunityReadReceiptPolicy.isValid(emptyList(), 100L, 100L))
        assertFalse(CommunityReadReceiptPolicy.isValid(listOf("m1", "m1"), 100L, 100L))
        assertFalse(CommunityReadReceiptPolicy.isValid((1..41).map { "m$it" }, 100L, 100L))
        assertFalse(CommunityReadReceiptPolicy.isValid(listOf("m1"), 0L, 100L))
        assertFalse(CommunityReadReceiptPolicy.isValid(listOf("m1"), 400_000L, 100L))
    }

    @Test fun readerMustBeAuthenticatedAndInGroupRoster() {
        val members = setOf("PEER_A", "PEER_B")
        assertTrue(CommunityReadReceiptPolicy.mayAcceptReader("PEER_A", "PEER_A", members))
        assertFalse(CommunityReadReceiptPolicy.mayAcceptReader("PEER_A", "PEER_B", members))
        assertFalse(CommunityReadReceiptPolicy.mayAcceptReader("PEER_C", "PEER_C", members))
        assertFalse(CommunityReadReceiptPolicy.mayAcceptReader("", "", members))
    }

    @Test fun readTimesMergeMonotonicallyAndIdempotently() {
        val first = CommunityReadReceiptPolicy.apply(emptyMap(), "PEER_A", 100L)
        assertEquals(100L, first["PEER_A"])
        assertEquals(first, CommunityReadReceiptPolicy.apply(first, "PEER_A", 90L))
        assertEquals(120L, CommunityReadReceiptPolicy.apply(first, "PEER_A", 120L)["PEER_A"])
        assertEquals(first, CommunityReadReceiptPolicy.apply(first, "", 200L))
    }
}
