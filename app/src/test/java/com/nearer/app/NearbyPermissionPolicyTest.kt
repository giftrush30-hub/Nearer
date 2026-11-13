package com.nearer.app

import android.Manifest
import org.junit.Assert.assertEquals
import org.junit.Test

class NearbyPermissionPolicyTest {
    @Test fun androidEightAndNineUseLegacyLocationPermission() {
        assertEquals(
            listOf(Manifest.permission.ACCESS_FINE_LOCATION),
            NearbyPermissionPolicy.runtimePermissions(26)
        )
        assertEquals(
            listOf(Manifest.permission.ACCESS_FINE_LOCATION),
            NearbyPermissionPolicy.runtimePermissions(28)
        )
    }

    @Test fun androidTenUsesLocationPermissionForNearbyWifiDiscovery() {
        assertEquals(
            Manifest.permission.ACCESS_FINE_LOCATION,
            NearbyPermissionPolicy.discoveryPermission(29)
        )
        assertEquals(
            listOf(Manifest.permission.ACCESS_FINE_LOCATION),
            NearbyPermissionPolicy.runtimePermissions(30)
        )
    }

    @Test fun androidTwelveRequestsBluetoothAndLocationPermissions() {
        assertEquals(
            listOf(
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_ADVERTISE,
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.ACCESS_FINE_LOCATION
            ),
            NearbyPermissionPolicy.runtimePermissions(31)
        )
        assertEquals(
            Manifest.permission.ACCESS_FINE_LOCATION,
            NearbyPermissionPolicy.discoveryPermission(32)
        )
    }

    @Test fun androidThirteenAndLaterUseNearbyWifiPermission() {
        val expected = listOf(
            Manifest.permission.BLUETOOTH_SCAN,
            Manifest.permission.BLUETOOTH_ADVERTISE,
            Manifest.permission.BLUETOOTH_CONNECT,
            Manifest.permission.NEARBY_WIFI_DEVICES
        )
        assertEquals(expected, NearbyPermissionPolicy.runtimePermissions(33))
        assertEquals(expected, NearbyPermissionPolicy.runtimePermissions(35))
        assertEquals(
            Manifest.permission.NEARBY_WIFI_DEVICES,
            NearbyPermissionPolicy.discoveryPermission(35)
        )
    }
}
