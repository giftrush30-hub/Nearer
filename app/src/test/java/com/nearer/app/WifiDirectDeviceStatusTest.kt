package com.nearer.app

import android.net.wifi.p2p.WifiP2pDevice
import org.junit.Assert.assertEquals
import org.junit.Test

class WifiDirectDeviceStatusTest {
    @Test fun availablePeerHasAvailableLabel() {
        assertEquals("Available", deviceStatus(WifiP2pDevice.AVAILABLE))
    }

    @Test fun invitedPeerHasPendingLabel() {
        assertEquals("Invitation pending", deviceStatus(WifiP2pDevice.INVITED))
    }

    @Test fun connectedPeerHasConnectedLabel() {
        assertEquals("Connected", deviceStatus(WifiP2pDevice.CONNECTED))
    }

    @Test fun failedPeerHasFailureLabel() {
        assertEquals("Connection failed", deviceStatus(WifiP2pDevice.FAILED))
    }

    @Test fun unavailablePeerHasTemporaryUnavailableLabel() {
        assertEquals("Temporarily unavailable", deviceStatus(WifiP2pDevice.UNAVAILABLE))
    }

    @Test fun unknownStateUsesSafeFallback() {
        assertEquals("Nearby Wi-Fi Direct device", deviceStatus(Int.MIN_VALUE))
    }
}
