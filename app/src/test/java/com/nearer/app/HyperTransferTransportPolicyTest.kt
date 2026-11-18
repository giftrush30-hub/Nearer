package com.nearer.app

import org.junit.Assert.*
import org.junit.Test

class HyperTransferTransportPolicyTest {
    private val capable = HyperTransferTransportPolicy.PeerCapabilities(
        apiLevel = 35,
        nearbyPointToPointAvailable = true,
        wifiDirectHardwareSupported = true,
        wifiDirectPermissionGranted = true,
        wifiAwareHardwareSupported = true,
        wifiAwarePermissionGranted = true
    )

    private fun profile(
        transport: HyperTransferTransportPolicy.Transport,
        rate: Long = 100_000_000L,
        pair: String = "phone-a|phone-b",
        direction: HyperTransferTransportPolicy.Direction = HyperTransferTransportPolicy.Direction.LOCAL_TO_REMOTE,
        runs: Int = 5,
        integrity: Boolean = true
    ) = HyperTransferTransportPolicy.BenchmarkProfile(
        transport, pair, direction, rate, runs, integrity
    )

    @Test fun choosesFastestQualifiedRouteForExactPairAndDirection() {
        val decision = HyperTransferTransportPolicy.choose(
            capable, capable, "phone-a|phone-b",
            HyperTransferTransportPolicy.Direction.LOCAL_TO_REMOTE,
            listOf(
                profile(HyperTransferTransportPolicy.Transport.WIFI_DIRECT_TCP, 180_000_000L),
                profile(HyperTransferTransportPolicy.Transport.WIFI_AWARE_TCP, 210_000_000L)
            )
        )
        assertEquals(HyperTransferTransportPolicy.Transport.WIFI_AWARE_TCP, decision.transport)
        assertEquals(210_000_000L, decision.measuredBytesPerSecond)
    }

    @Test fun unmeasuredAlternativeCannotDisplaceNearbyFallback() {
        val decision = HyperTransferTransportPolicy.choose(
            capable, capable, "phone-a|phone-b",
            HyperTransferTransportPolicy.Direction.LOCAL_TO_REMOTE,
            listOf(profile(HyperTransferTransportPolicy.Transport.WIFI_DIRECT_TCP, runs = 4))
        )
        assertEquals(HyperTransferTransportPolicy.Transport.NEARBY_POINT_TO_POINT, decision.transport)
        assertNull(decision.measuredBytesPerSecond)
    }

    @Test fun rejectsBenchmarkFromDifferentDevicePair() {
        val decision = HyperTransferTransportPolicy.choose(
            capable, capable, "phone-a|phone-b",
            HyperTransferTransportPolicy.Direction.LOCAL_TO_REMOTE,
            listOf(profile(HyperTransferTransportPolicy.Transport.WIFI_DIRECT_TCP, pair = "phone-c|phone-d"))
        )
        assertEquals(HyperTransferTransportPolicy.Transport.NEARBY_POINT_TO_POINT, decision.transport)
    }

    @Test fun rejectsBenchmarkFromOppositeDirection() {
        val decision = HyperTransferTransportPolicy.choose(
            capable, capable, "phone-a|phone-b",
            HyperTransferTransportPolicy.Direction.LOCAL_TO_REMOTE,
            listOf(profile(
                HyperTransferTransportPolicy.Transport.WIFI_DIRECT_TCP,
                direction = HyperTransferTransportPolicy.Direction.REMOTE_TO_LOCAL
            ))
        )
        assertEquals(HyperTransferTransportPolicy.Transport.NEARBY_POINT_TO_POINT, decision.transport)
    }

    @Test fun rejectsProfilesWithoutIntegrityVerification() {
        val decision = HyperTransferTransportPolicy.choose(
            capable, capable, "phone-a|phone-b",
            HyperTransferTransportPolicy.Direction.LOCAL_TO_REMOTE,
            listOf(profile(HyperTransferTransportPolicy.Transport.WIFI_AWARE_TCP, integrity = false))
        )
        assertEquals(HyperTransferTransportPolicy.Transport.NEARBY_POINT_TO_POINT, decision.transport)
    }

    @Test fun alternativeRequiresSupportAndPermissionOnBothPhones() {
        val remoteWithoutPermission = capable.copy(wifiDirectPermissionGranted = false)
        assertFalse(HyperTransferTransportPolicy.isEligible(
            HyperTransferTransportPolicy.Transport.WIFI_DIRECT_TCP,
            capable, remoteWithoutPermission
        ))
        assertTrue(HyperTransferTransportPolicy.isEligible(
            HyperTransferTransportPolicy.Transport.NEARBY_POINT_TO_POINT,
            capable, remoteWithoutPermission
        ))
    }

    @Test fun awareRequiresApiAndHardwareOnBothPhones() {
        val oldPhone = capable.copy(apiLevel = 25)
        assertFalse(HyperTransferTransportPolicy.isEligible(
            HyperTransferTransportPolicy.Transport.WIFI_AWARE_TCP, oldPhone, capable
        ))
        val noAware = capable.copy(wifiAwareHardwareSupported = false)
        assertFalse(HyperTransferTransportPolicy.isEligible(
            HyperTransferTransportPolicy.Transport.WIFI_AWARE_TCP, capable, noAware
        ))
    }

    @Test fun fallsBackToNoRouteWhenNearbyUnavailableAndNoQualifiedAlternative() {
        val noNearby = capable.copy(nearbyPointToPointAvailable = false)
        val decision = HyperTransferTransportPolicy.choose(
            noNearby, noNearby, "phone-a|phone-b",
            HyperTransferTransportPolicy.Direction.LOCAL_TO_REMOTE, emptyList()
        )
        assertNull(decision.transport)
    }

    @Test fun rejectsBlankPairKey() {
        try {
            HyperTransferTransportPolicy.choose(
                capable, capable, " ",
                HyperTransferTransportPolicy.Direction.LOCAL_TO_REMOTE, emptyList()
            )
            fail("Expected blank device-pair key to be rejected")
        } catch (_: IllegalArgumentException) {
            // Expected.
        }
    }
}
