package com.nearer.app

import org.junit.Assert.*
import org.junit.Test

class DeferredFileDigestTrackerTest {
    private val digest = "A".repeat(64)

    @Test fun acceptsFileCompletionBeforeDigest() {
        val tracker = DeferredFileDigestTracker()
        assertNull(tracker.registerFile("peer", 10L, 123L))
        assertNull(tracker.markFileCompleted("peer", 10L))
        val ready = tracker.recordDigest("peer", 10L, digest)
        assertEquals(DeferredFileDigestTracker.Ready("peer", 10L, digest.lowercase()), ready)
        assertEquals(0, tracker.pendingCount())
    }

    @Test fun acceptsDigestBeforeMetadataAndFileCompletion() {
        val tracker = DeferredFileDigestTracker()
        assertNull(tracker.recordDigest("peer", 11L, digest))
        assertNull(tracker.registerFile("peer", 11L, 456L))
        val ready = tracker.markFileCompleted("peer", 11L)
        assertEquals(DeferredFileDigestTracker.Ready("peer", 11L, digest.lowercase()), ready)
    }

    @Test fun doesNotMixSamePayloadIdAcrossEndpoints() {
        val tracker = DeferredFileDigestTracker()
        tracker.registerFile("peer-a", 12L, 100L)
        tracker.registerFile("peer-b", 12L, 100L)
        tracker.markFileCompleted("peer-a", 12L)
        assertNull(tracker.recordDigest("peer-b", 12L, digest))
        assertEquals(1, tracker.pendingCount())
        assertEquals(DeferredFileDigestTracker.Ready("peer-a", 12L, digest.lowercase()),
            tracker.recordDigest("peer-a", 12L, digest))
    }

    @Test fun rejectsConflictingDigestAndInvalidValues() {
        val tracker = DeferredFileDigestTracker()
        tracker.registerFile("peer", 13L, 1L)
        tracker.recordDigest("peer", 13L, digest)
        assertThrows(IllegalArgumentException::class.java) {
            tracker.recordDigest("peer", 13L, "B".repeat(64))
        }
        assertThrows(IllegalArgumentException::class.java) {
            tracker.recordDigest("peer", 13L, "not-a-digest")
        }
        assertThrows(IllegalArgumentException::class.java) {
            tracker.registerFile("peer", 13L, 2L)
        }
    }

    @Test fun boundsPendingEntriesAndSupportsEndpointCleanup() {
        val tracker = DeferredFileDigestTracker(maxPendingEntries = 2)
        tracker.recordDigest("peer-a", 1L, digest)
        tracker.markFileCompleted("peer-b", 2L)
        assertThrows(IllegalArgumentException::class.java) {
            tracker.registerFile("peer-c", 3L, 3L)
        }
        assertEquals(2, tracker.removeEndpoint("peer-a"))
        assertEquals(0, tracker.pendingCount())
    }
}
