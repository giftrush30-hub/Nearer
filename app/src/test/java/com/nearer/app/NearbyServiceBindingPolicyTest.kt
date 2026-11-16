package com.nearer.app

import org.junit.Assert.assertEquals
import org.junit.Test

class NearbyServiceBindingPolicyTest {
    @Test fun foregroundPromotionIsNotSkippedWhenAlreadyBound() {
        assertEquals(
            NearbyServiceBindingPolicy.Actions(startForegroundService = true, bindService = false),
            NearbyServiceBindingPolicy.actions(true, false, true, false)
        )
    }

    @Test fun foregroundPromotionIsNotSkippedWhileBindingIsPending() {
        assertEquals(
            NearbyServiceBindingPolicy.Actions(startForegroundService = true, bindService = false),
            NearbyServiceBindingPolicy.actions(true, false, false, true)
        )
    }

    @Test fun bindingAndForegroundStartAreIndependentDecisions() {
        assertEquals(
            NearbyServiceBindingPolicy.Actions(startForegroundService = true, bindService = true),
            NearbyServiceBindingPolicy.actions(true, false, false, false)
        )
        assertEquals(
            NearbyServiceBindingPolicy.Actions(startForegroundService = false, bindService = true),
            NearbyServiceBindingPolicy.actions(true, true, false, false)
        )
        assertEquals(
            NearbyServiceBindingPolicy.Actions(startForegroundService = false, bindService = false),
            NearbyServiceBindingPolicy.actions(false, false, true, true)
        )
    }
}
