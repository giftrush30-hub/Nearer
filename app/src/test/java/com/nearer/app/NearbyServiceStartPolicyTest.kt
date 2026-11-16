package com.nearer.app

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NearbyServiceStartPolicyTest {
    @Test fun discoveryStartsOnlyWhenPlatformPrerequisitesAreReady() {
        assertTrue(NearbyServiceStartPolicy.shouldStartDiscovery(true, true, true, 30, true))
        assertFalse(NearbyServiceStartPolicy.shouldStartDiscovery(false, true, true, 30, true))
        assertFalse(NearbyServiceStartPolicy.shouldStartDiscovery(true, false, true, 30, true))
        assertFalse(NearbyServiceStartPolicy.shouldStartDiscovery(true, true, false, 30, true))
        assertFalse(NearbyServiceStartPolicy.shouldStartDiscovery(true, true, true, 30, false))
    }

    @Test fun locationServicesAreRequiredOnlyThroughAndroid11() {
        assertTrue(NearbyServiceStartPolicy.requiresLocationServices(26))
        assertTrue(NearbyServiceStartPolicy.requiresLocationServices(30))
        assertFalse(NearbyServiceStartPolicy.requiresLocationServices(31))
        assertFalse(NearbyServiceStartPolicy.requiresLocationServices(35))
        assertFalse(NearbyServiceStartPolicy.shouldStartDiscovery(true, true, true, 31, false))
    }

    @Test fun radioAndPermissionRecoveryCanReenableDiscovery() {
        assertFalse(NearbyServiceStartPolicy.shouldStartDiscovery(true, true, false, 30, true))
        assertTrue(NearbyServiceStartPolicy.shouldStartDiscovery(true, true, true, 30, true))
        assertFalse(NearbyServiceStartPolicy.shouldStartDiscovery(true, false, true, 30, true))
        assertTrue(NearbyServiceStartPolicy.shouldStartDiscovery(true, true, true, 30, true))
    }
}
