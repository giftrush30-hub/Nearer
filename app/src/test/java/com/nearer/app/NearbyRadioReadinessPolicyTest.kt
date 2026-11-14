package com.nearer.app

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NearbyRadioReadinessPolicyTest {
    @Test fun bluetoothAloneCanStartNearbyConnections() {
        assertTrue(NearbyRadioReadinessPolicy.canStartNearby(bluetoothEnabled = true, wifiEnabled = false))
    }

    @Test fun wifiAloneCanStartNearbyConnections() {
        assertTrue(NearbyRadioReadinessPolicy.canStartNearby(bluetoothEnabled = false, wifiEnabled = true))
    }

    @Test fun bothRadiosDisabledMustNotStartNearbyConnections() {
        assertFalse(NearbyRadioReadinessPolicy.canStartNearby(bluetoothEnabled = false, wifiEnabled = false))
    }

    @Test fun wifiDirectRequiresWifi() {
        assertTrue(NearbyRadioReadinessPolicy.shouldStartWifiDirect(true))
        assertFalse(NearbyRadioReadinessPolicy.shouldStartWifiDirect(false))
    }
}
