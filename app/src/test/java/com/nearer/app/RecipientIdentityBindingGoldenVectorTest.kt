package com.nearer.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RecipientIdentityBindingGoldenVectorTest {
    @Test
    fun androidBindingBytesAndSignatureMatchSwiftFixture() {
        val signingPublic = hex("3059301306072a8648ce3d020106082a8648ce3d030107034200046b17d1f2e12c4247f8bce6e563a440f277037d812deb33a0f4a13945d898c2964fe342e2fe1a7f9b8ee7eb4a7c0f9e162bce33576b315ececbb6406837bf51f5")
        val recipientPublic = hex("3059301306072a8648ce3d020106082a8648ce3d030107034200047cf27b188d034f7e8a52380304b51ac3c08969e277f21b35a60b48fc4766997807775510db8ed040293d9ac69f7430dbba7dade63ce982299e04b79d227873d1")
        val binding = RecipientIdentityBinding(
            deviceId = "AAAAAB",
            signingKeyFingerprint = hex("5cd252fb0ce8932436faf8ccd1040981b89ee4ad6b9fe9e2a2b7e71aacb27cd3"),
            recipientEncryptionPublicKey = recipientPublic,
            sequence = 1L,
            issuedAtMs = 1_000_000L,
            expiresAtMs = 87_400_000L,
            signature = hex("3046022100deee14b7289bf4fb1113089883e4b3bbe773a2d8e382a600dd19d575daedf1c8022100fad6d4a94a5bd9c61fe5542a77f808deba9a757f631904fa0d1ec94a4c2d0cb5")
        )
        val expected = "4e65615265722d726563697069656e742d6964656e746974792d62696e64696e672d76310006414141414142000000205cd252fb0ce8932436faf8ccd1040981b89ee4ad6b9fe9e2a2b7e71aacb27cd30000005b" +
            "3059301306072a8648ce3d020106082a8648ce3d030107034200047cf27b188d034f7e8a52380304b51ac3c08969e277f21b35a60b48fc4766997807775510db8ed040293d9ac69f7430dbba7dade63ce982299e04b79d227873d1" +
            "000000000000000100000000000f42400000000005359e40"
        assertEquals(expected, binding.signedBytes().joinToString("") { "%02x".format(it) })
        assertTrue(binding.verify(signingPublic, 1_000_001L))
        assertFalse(binding.verify(signingPublic, 87_400_000L))
    }

    private fun hex(value: String): ByteArray =
        value.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
}
