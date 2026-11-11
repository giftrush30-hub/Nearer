package com.nearer.app

import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.security.KeyPairGenerator
import java.security.Signature
import java.security.spec.ECGenParameterSpec

class RecipientIdentityBindingTest {
    private fun pair() = KeyPairGenerator.getInstance("EC").apply {
        initialize(ECGenParameterSpec("secp256r1"))
    }.generateKeyPair()

    private fun sign(privateKey: java.security.PrivateKey, bytes: ByteArray): ByteArray =
        Signature.getInstance("SHA256withECDSA").run {
            initSign(privateKey)
            update(bytes)
            sign()
        }

    private fun binding(
        id: String = "AAAAAA",
        sequence: Long = 1L,
        issuedAt: Long = 1_000L,
        expiresAt: Long = 5_000L
    ): Pair<RecipientIdentityBinding, java.security.KeyPair> {
        val signing = pair()
        val encryption = pair()
        val value = RecipientIdentityBinding.create(
            id, signing.public.encoded, encryption.public.encoded,
            sequence, issuedAt, expiresAt
        ) { bytes -> sign(signing.private, bytes) }
        return value to signing
    }

    @Test fun bindingMustBeSignedByPinnedSigningIdentity() {
        val (binding, signing) = binding()
        val attacker = pair()
        assertTrue(binding.verify(signing.public.encoded, 2_000L))
        assertFalse(binding.verify(attacker.public.encoded, 2_000L))
        assertFalse(binding.copy(sequence = 2L).verify(signing.public.encoded, 2_000L))
        assertFalse(binding.verify(signing.public.encoded, 5_000L))
    }

    @Test fun bindingRejectsModifiedIdentityAndEncryptionKey() {
        val (binding, signing) = binding()
        val otherEncryption = pair()
        assertFalse(binding.copy(deviceId = "BBBBBB").verify(signing.public.encoded, 2_000L))
        assertFalse(
            binding.copy(recipientEncryptionPublicKey = otherEncryption.public.encoded)
                .verify(signing.public.encoded, 2_000L)
        )
        assertFalse(
            binding.copy(signingKeyFingerprint = ByteArray(32))
                .verify(signing.public.encoded, 2_000L)
        )
        assertFalse(
            binding.copy(signature = binding.signature.copyOf().also { it[it.lastIndex] = (it.last().toInt() xor 1).toByte() })
                .verify(signing.public.encoded, 2_000L)
        )
    }

    @Test fun bindingRejectsInvalidSequenceAndLifetime() {
        val signing = pair()
        val encryption = pair()
        assertThrows(IllegalArgumentException::class.java) {
            RecipientIdentityBinding.create(
                "AAAAAA", signing.public.encoded, encryption.public.encoded,
                -1L, 1_000L, 5_000L
            ) { bytes -> sign(signing.private, bytes) }
        }
        assertThrows(IllegalArgumentException::class.java) {
            RecipientIdentityBinding.create(
                "AAAAAA", signing.public.encoded, encryption.public.encoded,
                1L, 1_000L, 86_401_001L
            ) { bytes -> sign(signing.private, bytes) }
        }

        val (alreadyExpired, expiredSigning) = binding(issuedAt = 1_000L, expiresAt = 1_500L)
        assertFalse(alreadyExpired.verify(expiredSigning.public.encoded, 2_000L))
    }

    @Test fun bindingRejectsExcessivelyFutureDatedIssuance() {
        val (future, signing) = binding(issuedAt = 400_001L, expiresAt = 500_000L)
        assertFalse(future.verify(signing.public.encoded, 0L))
    }

    @Test fun creationRejectsMalformedIdsAndInvalidKeyTypes() {
        val signing = pair()
        val encryption = pair()
        assertThrows(IllegalArgumentException::class.java) {
            RecipientIdentityBinding.create(
                "not-an-id", signing.public.encoded, encryption.public.encoded,
                1L, 1_000L, 5_000L
            ) { bytes -> sign(signing.private, bytes) }
        }
        assertThrows(IllegalArgumentException::class.java) {
            RecipientIdentityBinding.create(
                "AAAAAA", signing.public.encoded, encryption.public.encoded,
                -1L, 1_000L, 5_000L
            ) { bytes -> sign(signing.private, bytes) }
        }
        assertThrows(IllegalArgumentException::class.java) {
            RecipientIdentityBinding.create(
                "AAAAAA", signing.public.encoded, encryption.public.encoded,
                1L, 1_000L, 86_401_001L
            ) { bytes -> sign(signing.private, bytes) }
        }
    }
}
