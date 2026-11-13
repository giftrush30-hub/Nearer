package com.nearer.app

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TrustedPeerReconnectTrackerTest {
    @Test fun onlyTrustedValidPeersCanTriggerReconnect() {
        val tracker = TrustedPeerReconnectTracker()
        assertFalse(tracker.shouldRequest("endpoint-1", "ABC123", false, false))
        assertFalse(tracker.shouldRequest("endpoint-2", "not-an-id", true, false))
        assertFalse(tracker.shouldRequest("", "ABC123", true, false))
        assertTrue(tracker.shouldRequest("endpoint-3", "ABC123", true, false))
    }

    @Test fun duplicateRequestsAreSuppressedUntilEndpointIsCleared() {
        val tracker = TrustedPeerReconnectTracker()
        assertTrue(tracker.shouldRequest("endpoint-1", "ABC123", true, false))
        assertFalse(tracker.shouldRequest("endpoint-1", "ABC123", true, false))
        tracker.clear("endpoint-1")
        assertTrue(tracker.shouldRequest("endpoint-1", "ABC123", true, false))
    }

    @Test fun connectedPeerDoesNotStartAnotherAutomaticRequest() {
        val tracker = TrustedPeerReconnectTracker()
        assertFalse(tracker.shouldRequest("endpoint-1", "ABC123", true, true))
    }

    @Test fun separateEndpointsRemainIndependent() {
        val tracker = TrustedPeerReconnectTracker()
        assertTrue(tracker.shouldRequest("endpoint-1", "ABC123", true, false))
        assertTrue(tracker.shouldRequest("endpoint-2", "DEF456", true, false))
        tracker.clearAll()
        assertTrue(tracker.shouldRequest("endpoint-1", "ABC123", true, false))
    }
}
