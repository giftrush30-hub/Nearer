package com.nearer.app

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class MeshWireProtocolTest {
    @Test fun identityHelloIsSignedVersionedAndTamperEvident() {
        val identity = MeshCrypto.generateIdentity()
        val hello = MeshWireProtocol.createIdentityHello("peer-a", identity)
        assertTrue(MeshWireProtocol.verifyIdentityHello(hello))
        val decoded = MeshWireProtocol.decodeIdentityHello(JSONObject(
            MeshWireProtocol.encodeIdentityHello(hello).toString(Charsets.UTF_8)
        ))
        assertEquals("peer-a", decoded?.peerId)
        assertEquals(hello.fingerprint, decoded?.fingerprint)
        assertFalse(MeshWireProtocol.verifyIdentityHello(hello.copy(peerId = "peer-b")))
        assertFalse(MeshWireProtocol.verifyIdentityHello(hello.copy(agreementPublicKey = hello.signingPublicKey)))
    }

    @Test fun routeAdvertisementMustBeSignedByKnownPeerAndFresh() {
        val alice = MeshCrypto.generateIdentity()
        val mallory = MeshCrypto.generateIdentity()
        val now = 1_800_000_000_000L
        val route = MeshWireProtocol.createRouteAdvertisement("alice", "david", 1, now, alice.signing)
        assertTrue(MeshWireProtocol.verifyRouteAdvertisement(route, alice.signingPublicKey, now + 10))
        assertFalse(MeshWireProtocol.verifyRouteAdvertisement(route, mallory.signingPublicKey, now + 10))
        assertFalse(MeshWireProtocol.verifyRouteAdvertisement(route, alice.signingPublicKey, now + 100_000))
        assertFalse(MeshWireProtocol.verifyRouteAdvertisement(route.copy(hopCount = 4), alice.signingPublicKey, now + 10))
    }

    @Test fun routeWireFrameRoundTripsAndRejectsWrongVersion() {
        val alice = MeshCrypto.generateIdentity()
        val route = MeshWireProtocol.createRouteAdvertisement("alice", "bob", 0, 1000, alice.signing)
        val json = JSONObject(MeshWireProtocol.encodeRoute(route).toString(Charsets.UTF_8))
        val decoded = MeshWireProtocol.decodeRoute(json)
        assertEquals(route, decoded)
        assertNull(MeshWireProtocol.decodeRoute(json.put("version", 999)))
    }
}
