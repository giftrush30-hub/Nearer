package com.nearer.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.security.KeyPairGenerator
import java.security.spec.ECGenParameterSpec

class MeshRouteAdvertisementTest {
    private fun p256() = KeyPairGenerator.getInstance("EC").apply {
        initialize(ECGenParameterSpec("secp256r1"))
    }.generateKeyPair()

    @Test fun directDestinationRouteHasZeroHops() {
        val signing = p256()
        val encryption = p256()
        val route = MeshRouteAdvertisementV1(
            "AAAAAA", signing.public.encoded, encryption.public.encoded,
            ByteArray(64) { 1 }, "AAAAAA", "AAAAAA", 0, 1L, 1000L, 2000L,
            signing.public.encoded, ByteArray(64) { 2 }
        )
        assertTrue(route.validate(1500L))
        assertFalse(route.copy(hopCount = 1).validate(1500L))
    }

    @Test fun forwardedRouteMustNameASeparateNextHopAndStayFresh() {
        val destinationSigning = p256()
        val destinationEncryption = p256()
        val advertiser = p256()
        val route = MeshRouteAdvertisementV1(
            "AAAAAA", destinationSigning.public.encoded, destinationEncryption.public.encoded,
            ByteArray(64) { 1 }, "BBBBBB", "CCCCCC", 2, 4L, 1000L, 3000L,
            advertiser.public.encoded, ByteArray(64) { 2 }
        )
        assertTrue(route.validate(2000L))
        assertFalse(route.copy(nextHopId = "BBBBBB").validate(2000L))
        assertFalse(route.copy(expiresAtMs = 1500L).validate(2000L))
        assertEquals(2, route.hopCount)
    }
}
