package com.nearer.app

import org.junit.Assert.*
import org.junit.Test

class MeshCryptoAndRoutingTest {
    @Test fun onlyIntendedRecipientCanOpenSignedPacket() {
        val alice = MeshCrypto.generateIdentity()
        val bob = MeshCrypto.generateIdentity()
        val mallory = MeshCrypto.generateIdentity()
        val packet = MeshCrypto.seal(
            "hello mesh".toByteArray(), "msg-1", "alice", "bob",
            alice.signing, bob.agreementPublicKey
        )
        val opened = MeshCrypto.open(packet, "bob", bob.agreement.private, alice.signingPublicKey)
        assertArrayEquals("hello mesh".toByteArray(), opened)
        assertNull(MeshCrypto.open(packet, "mallory", mallory.agreement.private, alice.signingPublicKey))
        assertNull(MeshCrypto.open(packet, "bob", bob.agreement.private, mallory.signingPublicKey))
    }

    @Test fun packetTamperingIsRejected() {
        val alice = MeshCrypto.generateIdentity()
        val bob = MeshCrypto.generateIdentity()
        val packet = MeshCrypto.seal(
            "private".toByteArray(), "msg-2", "alice", "bob",
            alice.signing, bob.agreementPublicKey
        )
        assertNull(MeshCrypto.open(packet.copy(destinationPeerId = "mallory"), "bob", bob.agreement.private, alice.signingPublicKey))
        assertNull(MeshCrypto.open(packet.copy(ciphertext = packet.ciphertext.dropLast(4) + "AAAA"), "bob", bob.agreement.private, alice.signingPublicKey))
    }

    @Test fun routePrefersShorterPathAndExpiresDisconnectedNextHop() {
        val table = MeshRouteTable(maxAgeMillis = 1000)
        assertTrue(table.learn("peer-d", "peer-b", 2, "peer-b", "peer-a", 100))
        assertTrue(table.learn("peer-d", "peer-c", 1, "peer-c", "peer-a", 200))
        assertEquals("peer-c", table.nextHop("peer-d", 250)?.nextHopPeerId)
        table.removeNextHop("peer-c")
        assertEquals("peer-b", table.nextHop("peer-d", 250)?.nextHopPeerId)
        assertNull(table.nextHop("peer-d", 1200))
    }

    @Test fun routeRejectsSelfRoutesAndHopOverflow() {
        val table = MeshRouteTable()
        assertFalse(table.learn("peer-a", "peer-b", 1, "peer-b", "peer-a", 100))
        assertFalse(table.learn("peer-z", "peer-b", MeshRelayPolicy.MAX_HOPS, "peer-b", "peer-a", 100))
        assertTrue(table.snapshot(100).isEmpty())
    }
}
