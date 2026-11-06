package com.nearer.app

import org.junit.Assert.*
import org.junit.Test

class IncomingPayloadCompletionTrackerTest {
    @Test fun successBeforeFileDescriptorIsRemembered() {
        val tracker = IncomingPayloadCompletionTracker()
        tracker.markCompleted("peer-a", 41L)
        assertTrue(tracker.isCompleted("peer-a", 41L))
        assertTrue(tracker.remove("peer-a", 41L))
        assertFalse(tracker.isCompleted("peer-a", 41L))
    }

    @Test fun completionIsScopedToEndpointAndPayload() {
        val tracker = IncomingPayloadCompletionTracker()
        tracker.markCompleted("peer-a", 41L)
        assertFalse(tracker.isCompleted("peer-b", 41L))
        assertFalse(tracker.isCompleted("peer-a", 42L))
    }

    @Test fun endpointCleanupRemovesOnlyItsMarkers() {
        val tracker = IncomingPayloadCompletionTracker()
        tracker.markCompleted("peer-a", 1L)
        tracker.markCompleted("peer-a", 2L)
        tracker.markCompleted("peer-b", 3L)
        assertEquals(2, tracker.removeEndpoint("peer-a"))
        assertFalse(tracker.isCompleted("peer-a", 1L))
        assertTrue(tracker.isCompleted("peer-b", 3L))
    }

    @Test fun capacityEvictsOldestMarker() {
        val tracker = IncomingPayloadCompletionTracker(maxEntries = 2)
        tracker.markCompleted("peer", 1L)
        tracker.markCompleted("peer", 2L)
        tracker.markCompleted("peer", 3L)
        assertEquals(2, tracker.pendingCount())
        assertFalse(tracker.isCompleted("peer", 1L))
        assertTrue(tracker.isCompleted("peer", 3L))
    }

    @Test fun duplicateCompletionDoesNotConsumeCapacity() {
        val tracker = IncomingPayloadCompletionTracker(maxEntries = 2)
        tracker.markCompleted("peer", 1L)
        tracker.markCompleted("peer", 1L)
        tracker.markCompleted("peer", 2L)
        assertEquals(2, tracker.pendingCount())
        assertTrue(tracker.isCompleted("peer", 1L))
    }

    @Test fun staleMarkersExpireWithoutNeedingEndpointDisconnect() {
        var now = 10_000L
        val tracker = IncomingPayloadCompletionTracker(
            maxEntries = 4,
            ttlMillis = 500L,
            nowMillis = { now }
        )
        tracker.markCompleted("peer", 7L)
        now += 499L
        assertTrue(tracker.isCompleted("peer", 7L))
        now += 1L
        assertFalse(tracker.isCompleted("peer", 7L))
        assertEquals(0, tracker.pendingCount())
    }

    @Test fun duplicateCompletionRefreshesMarkerLifetime() {
        var now = 1_000L
        val tracker = IncomingPayloadCompletionTracker(
            maxEntries = 4,
            ttlMillis = 500L,
            nowMillis = { now }
        )
        tracker.markCompleted("peer", 8L)
        now += 400L
        tracker.markCompleted("peer", 8L)
        now += 400L
        assertTrue(tracker.isCompleted("peer", 8L))
        now += 100L
        assertFalse(tracker.isCompleted("peer", 8L))
    }

    @Test fun invalidLifetimeIsRejected() {
        assertThrows(IllegalArgumentException::class.java) {
            IncomingPayloadCompletionTracker(ttlMillis = 0L)
        }
    }
}
