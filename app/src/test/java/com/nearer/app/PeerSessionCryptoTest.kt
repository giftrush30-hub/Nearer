package com.nearer.app

import java.util.Base64
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PeerSessionCryptoTest {
    @Test
    fun bothPeersDeriveTheSameSessionKey() {
        val alice = PeerSessionCrypto.createEphemeralKeyPair()
        val bob = PeerSessionCrypto.createEphemeralKeyPair()
        val aliceKey = PeerSessionCrypto.deriveSessionKey(
            alice.privateKey, bob.publicKeyBase64, "alice|bob|session-1"
        )
        val bobKey = PeerSessionCrypto.deriveSessionKey(
            bob.privateKey, alice.publicKeyBase64, "alice|bob|session-1"
        )
        val frame = PeerSessionCrypto.encrypt("hello NeaRer", aliceKey)
        assertEquals("hello NeaRer", PeerSessionCrypto.decrypt(frame, bobKey))
    }

    @Test
    fun bothPeersCalculateTheSameAuthenticationCode() {
        val alice = PeerSessionCrypto.createEphemeralKeyPair()
        val bob = PeerSessionCrypto.createEphemeralKeyPair()
        val context = "alice-id|bob-id|pairing-v1"
        val aliceKey = PeerSessionCrypto.deriveSessionKey(alice.privateKey, bob.publicKeyBase64, context)
        val bobKey = PeerSessionCrypto.deriveSessionKey(bob.privateKey, alice.publicKeyBase64, context)
        assertEquals(
            PeerSessionCrypto.authenticationCode(aliceKey, context),
            PeerSessionCrypto.authenticationCode(bobKey, context)
        )
    }

    @Test
    fun authenticationCodeChangesWithTranscript() {
        val alice = PeerSessionCrypto.createEphemeralKeyPair()
        val bob = PeerSessionCrypto.createEphemeralKeyPair()
        val key = PeerSessionCrypto.deriveSessionKey(alice.privateKey, bob.publicKeyBase64, "session")
        val first = PeerSessionCrypto.authenticationCode(key, "alice|bob")
        val second = PeerSessionCrypto.authenticationCode(key, "alice|mallory")
        assertNotEquals(first, second)
    }

    @Test
    fun eachEncryptionUsesAFreshNonce() {
        val alice = PeerSessionCrypto.createEphemeralKeyPair()
        val bob = PeerSessionCrypto.createEphemeralKeyPair()
        val key = PeerSessionCrypto.deriveSessionKey(
            alice.privateKey, bob.publicKeyBase64, "alice|bob|session-2"
        )
        val first = PeerSessionCrypto.encrypt("same message", key)
        val second = PeerSessionCrypto.encrypt("same message", key)
        assertNotEquals(first, second)
        assertEquals("same message", PeerSessionCrypto.decrypt(first, key))
        assertEquals("same message", PeerSessionCrypto.decrypt(second, key))
    }

    @Test
    fun modifiedCiphertextIsRejected() {
        val alice = PeerSessionCrypto.createEphemeralKeyPair()
        val bob = PeerSessionCrypto.createEphemeralKeyPair()
        val key = PeerSessionCrypto.deriveSessionKey(
            alice.privateKey, bob.publicKeyBase64, "alice|bob|session-3"
        )
        val bytes = Base64.getDecoder().decode(PeerSessionCrypto.encrypt("do not alter", key))
        bytes[bytes.lastIndex] = (bytes.last().toInt() xor 0x40).toByte()
        val modified = Base64.getEncoder().encodeToString(bytes)
        var rejected = false
        try {
            PeerSessionCrypto.decrypt(modified, key)
        } catch (_: Exception) {
            rejected = true
        }
        assertTrue("Tampered authenticated ciphertext must be rejected", rejected)
    }

    @Test
    fun differentSessionContextsDeriveDifferentKeys() {
        val alice = PeerSessionCrypto.createEphemeralKeyPair()
        val bob = PeerSessionCrypto.createEphemeralKeyPair()
        val first = PeerSessionCrypto.deriveSessionKey(
            alice.privateKey, bob.publicKeyBase64, "alice|bob|session-A"
        )
        val second = PeerSessionCrypto.deriveSessionKey(
            alice.privateKey, bob.publicKeyBase64, "alice|bob|session-B"
        )
        val frame = PeerSessionCrypto.encrypt("session bound", first)
        var rejected = false
        try {
            PeerSessionCrypto.decrypt(frame, second)
        } catch (_: Exception) {
            rejected = true
        }
        assertTrue("A different session context must not decrypt the frame", rejected)
    }
}
