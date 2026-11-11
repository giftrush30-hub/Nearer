package com.nearer.app

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.security.KeyPairGenerator
import java.security.spec.ECGenParameterSpec

class RecipientMessageCryptoTest {
    @Test fun onlyRecipientPrivateKeyCanDecryptRecipientAddressedPayload() {
        val recipient = KeyPairGenerator.getInstance("EC").apply {
            initialize(ECGenParameterSpec("secp256r1"))
        }.generateKeyPair()
        val other = KeyPairGenerator.getInstance("EC").apply {
            initialize(ECGenParameterSpec("secp256r1"))
        }.generateKeyPair()
        val clear = "private message for device D".toByteArray()
        val encrypted = RecipientMessageCrypto.encrypt(
            "AAAAAA", "BBBBBB", "packet-001", 1000L, 2000L, 3,
            recipient.public.encoded, clear
        )
        assertArrayEquals(clear, RecipientMessageCrypto.decrypt(
            recipient.private, "AAAAAA", "BBBBBB", "packet-001", 1000L, 2000L, 3, encrypted
        ))
        assertTrue(runCatching {
            RecipientMessageCrypto.decrypt(other.private, "AAAAAA", "BBBBBB", "packet-001", 1000L, 2000L, 3, encrypted)
        }.isFailure)
    }

    @Test fun recipientEnvelopeMetadataIsAuthenticated() {
        val recipient = KeyPairGenerator.getInstance("EC").apply {
            initialize(ECGenParameterSpec("secp256r1"))
        }.generateKeyPair()
        val encrypted = RecipientMessageCrypto.encrypt(
            "AAAAAA", "BBBBBB", "packet-002", 1000L, 2000L, 2,
            recipient.public.encoded, "hello".toByteArray()
        )
        assertTrue(runCatching {
            RecipientMessageCrypto.decrypt(
                recipient.private, "AAAAAA", "BBBBBB", "packet-002", 1000L, 2001L, 2, encrypted
            )
        }.isFailure)
        assertTrue(runCatching {
            RecipientMessageCrypto.decrypt(
                recipient.private, "AAAAAA", "BBBBBB", "packet-002", 1000L, 2000L, 5, encrypted
            )
        }.isFailure)
    }
}
