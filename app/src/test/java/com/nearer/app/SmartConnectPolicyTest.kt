package com.nearer.app

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SmartConnectPolicyTest {
    @Test fun bluetoothOnlyAndWifiOnlyAreBothUsable() {
        assertTrue(SmartConnectPolicy.hasUsableRadio(bluetoothEnabled = true, wifiEnabled = false))
        assertTrue(SmartConnectPolicy.hasUsableRadio(bluetoothEnabled = false, wifiEnabled = true))
        assertTrue(SmartConnectPolicy.hasUsableRadio(bluetoothEnabled = true, wifiEnabled = true))
        assertFalse(SmartConnectPolicy.hasUsableRadio(bluetoothEnabled = false, wifiEnabled = false))
    }

    @Test fun discoveryStartsOnlyAfterOnboardingPermissionAndRadioReadiness() {
        assertTrue(SmartConnectPolicy.shouldStartDiscovery(true, true, true, false, false))
        assertFalse(SmartConnectPolicy.shouldStartDiscovery(false, true, true, false, false))
        assertFalse(SmartConnectPolicy.shouldStartDiscovery(true, false, true, false, false))
        assertFalse(SmartConnectPolicy.shouldStartDiscovery(true, true, false, false, false))
    }

    @Test fun discoveryDoesNotRestartWhenAlreadySearchingOrConnected() {
        assertFalse(SmartConnectPolicy.shouldStartDiscovery(true, true, true, true, false))
        assertFalse(SmartConnectPolicy.shouldStartDiscovery(true, true, true, false, true))
    }

    @Test fun reconnectRequiresBothPreviouslyPinnedKeys() {
        assertTrue(SmartConnectPolicy.shouldAutoAcceptReconnect(true, true))
        assertFalse(SmartConnectPolicy.shouldAutoAcceptReconnect(true, false))
        assertFalse(SmartConnectPolicy.shouldAutoAcceptReconnect(false, true))
        assertFalse(SmartConnectPolicy.shouldAutoAcceptReconnect(false, false))
    }
}
