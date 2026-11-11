package com.nearer.app

import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.MessageDigest
import java.security.PublicKey
import java.security.SecureRandom
import java.security.spec.ECGenParameterSpec
import java.security.spec.X509EncodedKeySpec
import javax.crypto.Cipher
import javax.crypto.KeyAgreement
import javax.crypto.Mac
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/** Recipient-addressed E2E encryption; no relay or direct-session key is involved. */
internal object RecipientMessageCrypto {
    private const val VERSION = 1
    private const val MAX_LIFETIME_MS = 7L * 24L * 60L * 60L * 1000L

    data class EncryptedPayload(
        val ephemeralPublicKey: ByteArray,
        val nonce: ByteArray,
        val ciphertext: ByteArray
    )

    fun encrypt(
        originId: String,
        destinationId: String,
        packetId: String,
        createdAtMs: Long,
        expiresAtMs: Long,
        hopLimit: Int,
        recipientPublicKeyEncoded: ByteArray,
        plaintext: ByteArray
    ): EncryptedPayload {
        validateHeader(originId, destinationId, packetId, createdAtMs, expiresAtMs, hopLimit)
        require(plaintext.isNotEmpty() && plaintext.size <= 16000)
        val recipientPublicKey = decodeP256PublicKey(recipientPublicKeyEncoded)
        val ephemeral = KeyPairGenerator.getInstance("EC").apply {
            initialize(ECGenParameterSpec("secp256r1"))
        }.generateKeyPair()
        val sharedSecret = agree(ephemeral.private, recipientPublicKey)
        try {
            val associatedData = associatedData(originId, destinationId, packetId, createdAtMs, expiresAtMs, hopLimit)
            val key = deriveKey(sharedSecret, associatedData)
            try {
                val nonce = ByteArray(12).also { SecureRandom().nextBytes(it) }
                val cipher = Cipher.getInstance("AES/GCM/NoPadding")
                cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, nonce))
                cipher.updateAAD(associatedData)
                return EncryptedPayload(ephemeral.public.encoded, nonce, cipher.doFinal(plaintext))
            } finally {
                key.fill(0)
            }
        } finally {
            sharedSecret.fill(0)
        }
    }

    fun decrypt(
        recipientPrivateKey: java.security.PrivateKey,
        originId: String,
        destinationId: String,
        packetId: String,
        createdAtMs: Long,
        expiresAtMs: Long,
        hopLimit: Int,
        payload: EncryptedPayload
    ): ByteArray {
        validateHeader(originId, destinationId, packetId, createdAtMs, expiresAtMs, hopLimit)
        require(payload.nonce.size == 12 && payload.ciphertext.size in 16..24576)
        val ephemeralPublicKey = decodeP256PublicKey(payload.ephemeralPublicKey)
        val sharedSecret = agree(recipientPrivateKey, ephemeralPublicKey)
        try {
            val associatedData = associatedData(originId, destinationId, packetId, createdAtMs, expiresAtMs, hopLimit)
            val key = deriveKey(sharedSecret, associatedData)
            try {
                val cipher = Cipher.getInstance("AES/GCM/NoPadding")
                cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, payload.nonce))
                cipher.updateAAD(associatedData)
                return cipher.doFinal(payload.ciphertext)
            } finally {
                key.fill(0)
            }
        } finally {
            sharedSecret.fill(0)
        }
    }

    private fun validateHeader(
        originId: String, destinationId: String, packetId: String,
        createdAtMs: Long, expiresAtMs: Long, hopLimit: Int
    ) {
        require(NeaRerIdentity.normalize(originId) != null && NeaRerIdentity.normalize(destinationId) != null)
        require(originId != destinationId)
        require(packetId.matches(Regex("[A-Za-z0-9._:-]{1,80}")))
        require(createdAtMs >= 0L && expiresAtMs > createdAtMs)
        require(expiresAtMs - createdAtMs <= MAX_LIFETIME_MS)
        require(hopLimit in 1..4)
    }

    private fun decodeP256PublicKey(encoded: ByteArray): PublicKey {
        require(encoded.isNotEmpty() && encoded.size <= 512)
        val key = KeyFactory.getInstance("EC").generatePublic(X509EncodedKeySpec(encoded))
        require((key as? java.security.interfaces.ECPublicKey)?.params?.curve?.field?.fieldSize == 256)
        return key
    }

    private fun agree(privateKey: java.security.PrivateKey, publicKey: PublicKey): ByteArray =
        KeyAgreement.getInstance("ECDH").run {
            init(privateKey)
            doPhase(publicKey, true)
            generateSecret()
        }

    private fun associatedData(
        originId: String, destinationId: String, packetId: String,
        createdAtMs: Long, expiresAtMs: Long, hopLimit: Int
    ): ByteArray {
        val output = ByteArrayOutputStream()
        DataOutputStream(output).use { data ->
            data.write("NeaRer-recipient-message-v1".toByteArray(Charsets.US_ASCII))
            listOf(originId, destinationId, packetId).forEach { value ->
                val bytes = value.toByteArray(Charsets.UTF_8)
                data.writeShort(bytes.size)
                data.write(bytes)
            }
            data.writeLong(createdAtMs)
            data.writeLong(expiresAtMs)
            data.writeByte(VERSION)
            data.writeByte(hopLimit)
        }
        return output.toByteArray()
    }

    private fun deriveKey(sharedSecret: ByteArray, saltInput: ByteArray): ByteArray {
        val extract = Mac.getInstance("HmacSHA256")
        extract.init(SecretKeySpec(MessageDigest.getInstance("SHA-256").digest(saltInput), "HmacSHA256"))
        val prk = extract.doFinal(sharedSecret)
        try {
            val expand = Mac.getInstance("HmacSHA256")
            expand.init(SecretKeySpec(prk, "HmacSHA256"))
            expand.update("NeaRer-recipient-aes-256-gcm-v1".toByteArray(Charsets.US_ASCII))
            expand.update(1.toByte())
            return expand.doFinal().copyOf(32)
        } finally {
            prk.fill(0)
        }
    }
}
