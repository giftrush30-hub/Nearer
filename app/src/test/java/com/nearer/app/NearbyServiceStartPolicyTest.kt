package com.nearer.app

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NearbyServiceStartPolicyTest {
    @Test fun discoveryStartsOnlyWhenAllPlatformPrerequisitesAreReady() {
        assertTrue(NearbyServiceStartPolicy.shouldStartDiscovery(true, true, true, true))
        assertFalse(NearbyServiceStartPolicy.shouldStartDiscovery(false, true, true, true))
        assertFalse(NearbyServiceStartPolicy.shouldStartDiscovery(true, false, true, true))
        assertFalse(NearbyServiceStartPolicy.shouldStartDiscovery(true, true, false, true))
        assertFalse(NearbyServiceStartPolicy.shouldStartDiscovery(true, true, true, false))
    }

    @Test fun radioAndPermissionRecoveryCanReenableDiscoveryWithoutChangingPolicy() {
        assertFalse(NearbyServiceStartPolicy.shouldStartDiscovery(true, true, false, true))
        assertTrue(NearbyServiceStartPolicy.shouldStartDiscovery(true, true, true, true))
        assertFalse(NearbyServiceStartPolicy.shouldStartDiscovery(true, false, true, true))
        assertTrue(NearbyServiceStartPolicy.shouldStartDiscovery(true, true, true, true))
    }
}
