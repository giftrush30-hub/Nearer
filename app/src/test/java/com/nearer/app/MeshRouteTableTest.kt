package com.nearer.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.security.KeyPairGenerator
import java.security.Signature
import java.security.spec.ECGenParameterSpec

class MeshRouteTableTest {
    private fun pair() = KeyPairGenerator.getInstance("EC").apply {
        initialize(ECGenParameterSpec("secp256r1"))
    }.generateKeyPair()

    private fun sign(privateKey: java.security.PrivateKey, bytes: ByteArray): ByteArray =
        Signature.getInstance("SHA256withECDSA").run {
            initSign(privateKey)
            update(bytes)
            sign()
        }

    @Test fun acceptsOnlyFreshSignedRouteFromTheAuthenticatedAdvertiser() {
        val sender = pair()
        val destination = pair()
        val ad = SignedRouteAdvertisement.create(
            "AAAAAA", "BBBBBB", destination.public.encoded, 4L, 1, 1000L, 5000L
        ) { sign(sender.private, it) }
        val table = MeshRouteTable("CCCCCC")
        assertTrue(table.update(ad, "AAAAAA", sender.public.encoded, 2000L))
        val route = table.find("BBBBBB", 2000L)
        assertNotNull(route)
        assertEquals("AAAAAA", route!!.nextHopPeerId)
        assertEquals(2, route.hopCount)
        assertFalse(table.update(ad, "DDDDDD", sender.public.encoded, 2000L))
        assertFalse(table.update(ad, "AAAAAA", destination.public.encoded, 2000L))
    }

    @Test fun rejectsStaleSequenceAndExpiresRoutes() {
        val sender = pair()
        val destination = pair()
        val table = MeshRouteTable("CCCCCC")
        val newer = SignedRouteAdvertisement.create(
            "AAAAAA", "BBBBBB", destination.public.encoded, 8L, 0, 1000L, 5000L
        ) { sign(sender.private, it) }
        val older = SignedRouteAdvertisement.create(
            "AAAAAA", "BBBBBB", destination.public.encoded, 7L, 0, 1000L, 5000L
        ) { sign(sender.private, it) }
        assertTrue(table.update(newer, "AAAAAA", sender.public.encoded, 2000L))
        assertFalse(table.update(older, "AAAAAA", sender.public.encoded, 2000L))
        assertNull(table.find("BBBBBB", 5000L))
    }

    @Test fun rejectsAdvertisementTampering() {
        val sender = pair()
        val destination = pair()
        val ad = SignedRouteAdvertisement.create(
            "AAAAAA", "BBBBBB", destination.public.encoded, 1L, 0, 1000L, 5000L
        ) { sign(sender.private, it) }
        assertFalse(ad.copy(hopCount = 2).verify(sender.public.encoded, 2000L))
        assertFalse(ad.copy(destinationId = "DDDDDD").verify(sender.public.encoded, 2000L))
    }
}
