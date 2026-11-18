package com.nearer.app

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NearbyPeerProofGoldenVectorTest {
    @Test
    fun androidProofMessageMatchesSwiftCryptoKitFixture() {
        val publicKey = hex("3059301306072a8648ce3d020106082a8648ce3d030107034200046b17d1f2e12c4247f8bce6e563a440f277037d812deb33a0f4a13945d898c2964fe342e2fe1a7f9b8ee7eb4a7c0f9e162bce33576b315ececbb6406837bf51f5")
        val challenge = "NeaRer Android iPhone signature fixture".toByteArray()
        val signature = hex("3045022100829350f5dea320fd1c49a8b24074ba14d8c428235c67159762a9f0d38d7a1698022032d10c3b1a2dda2e7ed58549916b363b0761d4671ce15ef8a0ed4099a6f79f30")
        assertTrue(SecurePeerIdentity.verifyChallenge("AAAAAB", publicKey, challenge, signature))
        assertFalse(SecurePeerIdentity.verifyChallenge("BBBBBB", publicKey, challenge, signature))
    }

    private fun hex(value: String): ByteArray =
        value.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
}
