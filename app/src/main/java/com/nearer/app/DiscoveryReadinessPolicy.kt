package com.nearer.app

/**
 * Android 13 (API 33) introduced NEARBY_WIFI_DEVICES for nearby Wi-Fi operations.
 * For NeaRer's Wi-Fi Direct discovery, only Android 12L and older need the
 * legacy location-services gate in addition to their runtime permission.
 */
internal object DiscoveryReadinessPolicy {
    fun requiresLocationServices(sdkInt: Int): Boolean = sdkInt <= 32
}
