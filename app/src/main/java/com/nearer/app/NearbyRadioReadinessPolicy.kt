package com.nearer.app

/**
 * Nearby Connections can use the radios available on the device. Wi-Fi Direct
 * is an additional discovery path and specifically requires Wi-Fi.
 */
internal object NearbyRadioReadinessPolicy {
    fun canStartNearby(bluetoothEnabled: Boolean, wifiEnabled: Boolean): Boolean =
        bluetoothEnabled || wifiEnabled

    fun shouldStartWifiDirect(wifiEnabled: Boolean): Boolean = wifiEnabled
}
