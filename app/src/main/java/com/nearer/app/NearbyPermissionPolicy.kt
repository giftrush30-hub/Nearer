package com.nearer.app

import android.Manifest

/**
 * Central source of truth for runtime permissions used by nearby discovery.
 *
 * Android 8–12 use location permission for nearby Wi-Fi discovery. Android 12+
 * additionally requires the granular Bluetooth permissions. Android 13+
 * uses NEARBY_WIFI_DEVICES for Wi-Fi discovery instead of location permission.
 */
internal object NearbyPermissionPolicy {
    fun discoveryPermission(sdkInt: Int): String =
        if (sdkInt >= 33) Manifest.permission.NEARBY_WIFI_DEVICES
        else Manifest.permission.ACCESS_FINE_LOCATION

    fun runtimePermissions(sdkInt: Int): List<String> = buildList {
        if (sdkInt >= 31) {
            add(Manifest.permission.BLUETOOTH_SCAN)
            add(Manifest.permission.BLUETOOTH_ADVERTISE)
            add(Manifest.permission.BLUETOOTH_CONNECT)
        }
        add(discoveryPermission(sdkInt))
    }
}
