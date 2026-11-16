package com.nearer.app

import android.net.wifi.p2p.WifiP2pDevice

/**
 * Converts Android Wi-Fi Direct peer states into concise labels for the nearby-device UI.
 * Kept independent of Activity state so every state and the fallback can be tested.
 */
internal fun deviceStatus(status: Int): String = when (status) {
    WifiP2pDevice.AVAILABLE -> "Available"
    WifiP2pDevice.INVITED -> "Invitation pending"
    WifiP2pDevice.CONNECTED -> "Connected"
    WifiP2pDevice.FAILED -> "Connection failed"
    WifiP2pDevice.UNAVAILABLE -> "Temporarily unavailable"
    else -> "Nearby Wi-Fi Direct device"
}
