package com.nearer.app

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.security.KeyFactory
import java.security.Signature
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec
import javax.crypto.AEADBadTagException

/** Fixed P-256, HKDF and AES-GCM vectors shared with the iOS CryptoKit tests. */
class AndroidCryptoGoldenVectorTest {
    private val alicePrivate = hex("308187020100301306072a8648ce3d020106082a8648ce3d030107046d306b02010104200000000000000000000000000000000000000000000000000000000000000001a144034200046b17d1f2e12c4247f8bce6e563a440f277037d812deb33a0f4a13945d898c2964fe342e2fe1a7f9b8ee7eb4a7c0f9e162bce33576b315ececbb6406837bf51f5")
    private val bobPrivate = hex("308187020100301306072a8648ce3d020106082a8648ce3d030107046d306b02010104200000000000000000000000000000000000000000000000000000000000000002a144034200047cf27b188d034f7e8a52380304b51ac3c08969e277f21b35a60b48fc4766997807775510db8ed040293d9ac69f7430dbba7dade63ce982299e04b79d227873d1")
    private val alicePublic = hex("3059301306072a8648ce3d020106082a8648ce3d030107034200046b17d1f2e12c4247f8bce6e563a440f277037d812deb33a0f4a13945d898c2964fe342e2fe1a7f9b8ee7eb4a7c0f9e162bce33576b315ececbb6406837bf51f5")
    private val bobPublic = hex("3059301306072a8648ce3d020106082a8648ce3d030107034200047cf27b188d034f7e8a52380304b51ac3c08969e277f21b35a60b48fc4766997807775510db8ed040293d9ac69f7430dbba7dade63ce982299e04b79d227873d1")
    private val transcript = "NeaRer cross-platform crypto fixture v1".toByteArray()
    private val keyFactory = KeyFactory.getInstance("EC")

    @Test
    fun p256EcdhHkdfAndAesGcmMatchCryptoKitVector() {
        val alicePriv = keyFactory.generatePrivate(PKCS8EncodedKeySpec(alicePrivate))
        val bobPriv = keyFactory.generatePrivate(PKCS8EncodedKeySpec(bobPrivate))
        val alicePub = keyFactory.generatePublic(X509EncodedKeySpec(alicePublic))
        val bobPub = keyFactory.generatePublic(X509EncodedKeySpec(bobPublic))
        val aliceKeys = NearbyMessageCrypto.deriveSession("AAAAAB", "BBBBBB", alicePriv, bobPub, transcript)
        val bobKeys = NearbyMessageCrypto.deriveSession("BBBBBB", "AAAAAB", bobPriv, alicePub, transcript)
        assertEquals("1decdff0942fedbe28650b20d380c348f2e677263e654cf23a8e812a2db07337", aliceKeys.sendKey.hex())
        assertArrayEquals(aliceKeys.sendKey, bobKeys.receiveKey)
        assertArrayEquals(aliceKeys.receiveKey, bobKeys.sendKey)

        val packet = NearbyMessageCrypto.EncryptedPayload(
            "crypto-vector-1",
            hex("000102030405060708090a0b"),
            hex("4f063a9107c822dcadedc9ea333bc6d0fd1e44f75698ac4d0a1df58ed0154beb7462d10871331867b9f3ee606b3f913edc6fd4b3")
        )
        assertArrayEquals(
            "NeaRer Android iPhone secure message".toByteArray(),
            NearbyMessageCrypto.open(bobKeys, "BBBBBB", "AAAAAB", packet)
        )
        assertThrows(AEADBadTagException::class.java) {
            NearbyMessageCrypto.open(bobKeys, "AAAAAB", "BBBBBB", packet)
        }
    }

    @Test
    fun androidEcdsaSignatureFixtureMatchesCryptoKit() {
        val publicKey = keyFactory.generatePublic(X509EncodedKeySpec(alicePublic))
        val verifier = Signature.getInstance("SHA256withECDSA")
        verifier.initVerify(publicKey)
        verifier.update("NeaRer Android iPhone signature fixture".toByteArray())
        assertEquals(true, verifier.verify(hex("30440220647d62e9379d7ff20d13af0c52416a3f0e04dd4962e491f27f295d53bc6201b702205db2285c63e274b10ed7c5f9d56dabf0bc5063fdf51d6c41f1abc3afd50a2bbf")))
    }

    private fun hex(value: String): ByteArray =
        value.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
    private fun ByteArray.hex(): String = joinToString("") { "%02x".format(it) }
}
