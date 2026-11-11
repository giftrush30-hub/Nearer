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

    @Test fun authenticatedDirectPeerBecomesAnExpiringReverseRoute() {
        val peer = pair()
        val table = MeshRouteTable("AAAAAA")
        assertTrue(table.addDirectPeer("BBBBBB", peer.public.encoded, 1000L))
        val route = table.find("BBBBBB", 2000L)
        assertNotNull(route)
        assertEquals("BBBBBB", route!!.nextHopPeerId)
        assertEquals(0, route.hopCount)
        assertEquals(listOf("AAAAAA", "BBBBBB"), route.path)
        table.removeNextHop("BBBBBB")
        assertNull(table.find("BBBBBB", 2000L))
    }

    @Test fun acceptsOnlyFreshSignedRouteFromTheAuthenticatedAdvertiser() {
        val sender = pair()
        val destination = pair()
        val ad = SignedRouteAdvertisement.create(
            "AAAAAA", "BBBBBB", destination.public.encoded, 4L, 1, 1000L, 5000L,
            path = listOf("AAAAAA", "EEEEEE", "BBBBBB")
        ) { sign(sender.private, it) }
        val table = MeshRouteTable("CCCCCC")
        assertTrue(table.update(ad, "AAAAAA", sender.public.encoded, 2000L))
        val route = table.find("BBBBBB", 2000L)
        assertNotNull(route)
        assertEquals("AAAAAA", route!!.nextHopPeerId)
        assertEquals(2, route.hopCount)
        assertEquals(listOf("CCCCCC", "AAAAAA", "EEEEEE", "BBBBBB"), route.path)
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

    @Test fun routeAdvertisementHasBoundedRoundTripEncoding() {
        val sender = pair()
        val destination = pair()
        val ad = SignedRouteAdvertisement.create(
            "AAAAAA", "BBBBBB", destination.public.encoded, 9L, 1, 1000L, 5000L,
            path = listOf("AAAAAA", "EEEEEE", "BBBBBB")
        ) { sign(sender.private, it) }
        val decoded = SignedRouteAdvertisement.decode(ad.encode())
        assertTrue(decoded.verify(sender.public.encoded, 2000L))
        assertEquals(ad.destinationId, decoded.destinationId)
        assertEquals(ad.sequence, decoded.sequence)
        assertFalse(runCatching { SignedRouteAdvertisement.decode(ad.encode() + byteArrayOf(1)) }.isSuccess)
        val table = MeshRouteTable("CCCCCC")
        assertFalse(table.update(decoded, "AAAAAA", sender.public.encoded, 2000L, pair().public.encoded))
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
    @Test fun routeSequenceNumbersAreComparedOnlyWithinTheSameAdvertiser() {
        val firstAdvertiser = pair()
        val secondAdvertiser = pair()
        val destination = pair()
        val table = MeshRouteTable("CCCCCC")
        val olderFromA = SignedRouteAdvertisement.create(
            "AAAAAA", "BBBBBB", destination.public.encoded, 900L, 1, 1000L, 9000L,
            path = listOf("AAAAAA", "EEEEEE", "BBBBBB")
        ) { sign(firstAdvertiser.private, it) }
        val newerFromD = SignedRouteAdvertisement.create(
            "DDDDDD", "BBBBBB", destination.public.encoded, 1L, 0, 3000L, 9000L
        ) { sign(secondAdvertiser.private, it) }

        assertTrue(table.update(olderFromA, "AAAAAA", firstAdvertiser.public.encoded, 4000L))
        // A lower sequence from another authenticated advertiser is not stale:
        // sequence spaces are local to each advertiser.
        assertTrue(table.update(newerFromD, "DDDDDD", secondAdvertiser.public.encoded, 4000L))
        assertEquals("DDDDDD", table.find("BBBBBB", 4000L)!!.nextHopPeerId)

        val staleFromD = SignedRouteAdvertisement.create(
            "DDDDDD", "BBBBBB", destination.public.encoded, 0L, 0, 2500L, 9000L
        ) { sign(secondAdvertiser.private, it) }
        assertFalse(table.update(staleFromD, "DDDDDD", secondAdvertiser.public.encoded, 4000L))
    }

    @Test fun rejectsPathVectorsThatWouldRouteBackThroughThisDevice() {
        val sender = pair()
        val destination = pair()
        val ad = SignedRouteAdvertisement.create(
            "AAAAAA", "BBBBBB", destination.public.encoded, 2L, 1, 1000L, 5000L,
            path = listOf("AAAAAA", "CCCCCC", "BBBBBB")
        ) { sign(sender.private, it) }
        val table = MeshRouteTable("CCCCCC")
        assertFalse(table.update(ad, "AAAAAA", sender.public.encoded, 2000L))
    }

    @Test fun rejectsMalformedOrRepeatedPathVector() {
        val sender = pair()
        val destination = pair()
        val repeated = runCatching {
            SignedRouteAdvertisement.create(
                "AAAAAA", "BBBBBB", destination.public.encoded, 2L, 1, 1000L, 5000L,
                path = listOf("AAAAAA", "EEEEEE", "EEEEEE", "BBBBBB")
            ) { sign(sender.private, it) }
        }
        assertFalse(repeated.isSuccess)
    }

    @Test fun rejectsRoutesThatPointToTheLocalDeviceOrAdvertiserItself() {
        val sender = pair()
        val destination = pair()
        val table = MeshRouteTable("CCCCCC")
        val toLocal = SignedRouteAdvertisement.create(
            "AAAAAA", "CCCCCC", destination.public.encoded, 1L, 0, 1000L, 5000L
        ) { sign(sender.private, it) }
        val localAdvertiser = SignedRouteAdvertisement.create(
            "CCCCCC", "DDDDDD", destination.public.encoded, 2L, 0, 1000L, 5000L
        ) { sign(destination.private, it) }

        assertFalse(table.update(toLocal, "AAAAAA", sender.public.encoded, 2000L))
        assertFalse(table.update(localAdvertiser, "CCCCCC", destination.public.encoded, 2000L))
        assertNull(table.find("CCCCCC", 2000L))
    }

}
