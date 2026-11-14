package com.nearer.app

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DiscoveryReadinessPolicyTest {
    @Test
    fun android12AndOlderRequireLocationServicesForLegacyNearbyWifiDiscovery() {
        assertTrue(DiscoveryReadinessPolicy.requiresLocationServices(26))
        assertTrue(DiscoveryReadinessPolicy.requiresLocationServices(30))
        assertTrue(DiscoveryReadinessPolicy.requiresLocationServices(31))
        assertTrue(DiscoveryReadinessPolicy.requiresLocationServices(32))
    }

    @Test
    fun android13AndNewerUseNearbyWifiPermissionWithoutLocationToggleGate() {
        assertFalse(DiscoveryReadinessPolicy.requiresLocationServices(33))
        assertFalse(DiscoveryReadinessPolicy.requiresLocationServices(34))
        assertFalse(DiscoveryReadinessPolicy.requiresLocationServices(35))
    }
}
