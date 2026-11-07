package com.nearer.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NearbyDiscoveryBudgetPolicyTest {
    @Test fun foregroundSearchHasABoundedOneMinuteBudget() {
        assertEquals(60_000L, NearbyDiscoveryBudgetPolicy.FOREGROUND_SEARCH_TIMEOUT_MS)
    }

    @Test fun activeDiscoveryStopsWhenActivityLeavesForeground() {
        assertTrue(NearbyDiscoveryBudgetPolicy.shouldStopWhenActivityStops(true))
        assertFalse(NearbyDiscoveryBudgetPolicy.shouldStopWhenActivityStops(false))
    }
}
