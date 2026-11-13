package com.nearer.app

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NearbyDiscoveryGenerationPolicyTest {
    @Test fun currentActiveGenerationAcceptsCallbacks() {
        assertTrue(NearbyDiscoveryGenerationPolicy.isCurrent(true, 7, 7))
    }

    @Test fun stoppedDiscoveryRejectsLateCallbacks() {
        assertFalse(NearbyDiscoveryGenerationPolicy.isCurrent(false, 7, 7))
    }

    @Test fun previousScanCannotResurrectStalePeers() {
        assertFalse(NearbyDiscoveryGenerationPolicy.isCurrent(true, 6, 7))
    }

    @Test fun newerScanDoesNotAcceptCallbacksFromFutureGeneration() {
        assertFalse(NearbyDiscoveryGenerationPolicy.isCurrent(true, 8, 7))
    }
}
