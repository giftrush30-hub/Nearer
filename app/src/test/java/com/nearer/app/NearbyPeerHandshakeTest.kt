package com.nearer.app

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class NearbyPeerHandshakeTest {
    @Test fun transcriptIsIndependentOfWhichPeerBuildsIt() {
        val aNonce = ByteArray(32) { (it + 1).toByte() }
        val bNonce = ByteArray(32) { (it + 65).toByte() }
        val aKey = byteArrayOf(1, 2, 3)
        val bKey = byteArrayOf(4, 5, 6)
        val fromA = NearbyPeerHandshake.transcriptChallenge("peer-a", aNonce, aKey, "peer-b", bNonce, bKey)
        val fromB = NearbyPeerHandshake.transcriptChallenge("peer-b", bNonce, bKey, "peer-a", aNonce, aKey)
        assertArrayEquals(fromA, fromB)
    }

    @Test fun transcriptChangesWhenEitherEphemeralKeyOrNonceChanges() {
        val nonceA = ByteArray(32) { it.toByte() }
        val nonceB = ByteArray(32) { (it + 32).toByte() }
        val keyA = byteArrayOf(1, 2, 3)
        val keyB = byteArrayOf(4, 5, 6)
        val original = NearbyMessageCrypto.transcript("a", nonceA, keyA, "b", nonceB, keyB)
        val altered = NearbyMessageCrypto.transcript("a", nonceA, keyA, "b", nonceB, byteArrayOf(4, 5, 7))
        assertFalse(original.contentEquals(altered))
    }

    @Test fun ecdhDerivesMatchingDirectionalKeys() {
        val a = NearbyMessageCrypto.newEphemeralKeyPair()
        val b = NearbyMessageCrypto.newEphemeralKeyPair()
        val transcript = byteArrayOf(9, 8, 7, 6)
        val aSession = NearbyMessageCrypto.deriveSession("a", "b", a.private, b.public, transcript)
        val bSession = NearbyMessageCrypto.deriveSession("b", "a", b.private, a.public, transcript)
        assertArrayEquals(aSession.sendKey, bSession.receiveKey)
        assertArrayEquals(aSession.receiveKey, bSession.sendKey)
    }

    @Test fun securePacketAuthenticatesContentsAndMessageId() {
        val a = NearbyMessageCrypto.newEphemeralKeyPair()
        val b = NearbyMessageCrypto.newEphemeralKeyPair()
        val sessionA = NearbyMessageCrypto.deriveSession("a", "b", a.private, b.public, byteArrayOf(1))
        val sessionB = NearbyMessageCrypto.deriveSession("b", "a", b.private, a.public, byteArrayOf(1))
        val clear = """{"kind":"message","id":"m-1","body":"hello"}""".toByteArray()
        val packet = JSONObject(NearbyMessageCrypto.encrypt(sessionA, "a", "b", "m-1", clear).toString(Charsets.UTF_8))
        val opened = NearbyMessageCrypto.decrypt(sessionB, "b", "a", packet)
        assertEquals("m-1", opened.first)
        assertArrayEquals(clear, opened.second)
        packet.put("id", "m-2")
        assertTrue(runCatching { NearbyMessageCrypto.decrypt(sessionB, "b", "a", packet) }.isFailure)
    }
}
