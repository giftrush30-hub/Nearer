package com.nearer.app

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NearbyPeerProofGoldenVectorTest {
    @Test
    fun androidProofMessageMatchesSwiftCryptoKitFixture() {
        val publicKey = hex("3059301306072a8648ce3d020106082a8648ce3d030107034200046b17d1f2e12c4247f8bce6e563a440f277037d812deb33a0f4a13945d898c2964fe342e2fe1a7f9b8ee7eb4a7c0f9e162bce33576b315ececbb6406837bf51f5")
        val challenge = "NeaRer Android iPhone signature fixture".toByteArray()
        val signature = hex("304502207e31700985a269fe326027c9809ae2751f669b5897117096350d34952bf570ee0221009a0243bed67481475f3b2b45306c3d4a341523c864e5754e3f4b90bd4ac4a031")
        assertTrue(SecurePeerIdentity.verifyChallenge("AAAAAB", publicKey, challenge, signature))
        assertFalse(SecurePeerIdentity.verifyChallenge("BBBBBB", publicKey, challenge, signature))
    }

    private fun hex(value: String): ByteArray =
        value.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
}
