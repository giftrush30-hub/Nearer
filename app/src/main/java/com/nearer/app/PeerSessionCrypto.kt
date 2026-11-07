package com.nearer.app

import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import java.security.KeyFactory
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.PrivateKey
import java.security.SecureRandom
import java.security.spec.ECGenParameterSpec
import java.security.spec.X509EncodedKeySpec
import javax.crypto.Cipher
import javax.crypto.KeyAgreement
import javax.crypto.Mac
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import java.util.Base64

/**
 * Cryptographic building blocks for the future authenticated Wi-Fi Direct session.
 *
 * This helper is intentionally not wired into the socket protocol yet. The protocol
 * must first exchange ephemeral public keys and confirm a short authentication string
 * on BOTH phones before any encrypted frame is accepted.
 */
internal object PeerSessionCrypto {
    private const val CURVE = "secp256r1"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private const val NONCE_BYTES = 12
    private const val TAG_BITS = 128
    private const val KEY_BYTES = 32
    private const val MAX_FRAME_BYTES = 64 * 1024
    private val aad = "NeaRer-WiFiDirect-v1".toByteArray(StandardCharsets.UTF_8)
    private val random = SecureRandom()

    data class EphemeralKeyPair internal constructor(
        val publicKeyBase64: String,
        internal val privateKey: PrivateKey
    )

    fun createEphemeralKeyPair(): EphemeralKeyPair {
        val generator = KeyPairGenerator.getInstance("EC")
        generator.initialize(ECGenParameterSpec(CURVE), random)
        val pair: KeyPair = generator.generateKeyPair()
        return EphemeralKeyPair(
            Base64.getEncoder().encodeToString(pair.public.encoded),
            pair.private
        )
    }

    fun deriveSessionKey(
        privateKey: PrivateKey,
        remotePublicKeyBase64: String,
        sessionContext: String
    ): SecretKey {
        require(sessionContext.isNotBlank() && sessionContext.length <= 256) {
            "Invalid session context"
        }
        val encoded = Base64.getDecoder().decode(remotePublicKeyBase64)
        require(encoded.size in 64..256) { "Invalid peer public key length" }
        val remotePublicKey = KeyFactory.getInstance("EC")
            .generatePublic(X509EncodedKeySpec(encoded))
        val agreement = KeyAgreement.getInstance("ECDH")
        agreement.init(privateKey)
        agreement.doPhase(remotePublicKey, true)
        val sharedSecret = agreement.generateSecret()
        try {
            val salt = sha256("NeaRer-ECDH-v1".toByteArray(StandardCharsets.UTF_8))
            val extract = Mac.getInstance("HmacSHA256")
            extract.init(SecretKeySpec(salt, "HmacSHA256"))
            val pseudorandomKey = extract.doFinal(sharedSecret)
            val expand = Mac.getInstance("HmacSHA256")
            expand.init(SecretKeySpec(pseudorandomKey, "HmacSHA256"))
            val info = ("NeaRer-AES-256-GCM|" + sessionContext)
                .toByteArray(StandardCharsets.UTF_8)
            expand.update(info)
            expand.update(0x01.toByte())
            val keyBytes = expand.doFinal().copyOf(KEY_BYTES)
            return SecretKeySpec(keyBytes, "AES")
        } finally {
            sharedSecret.fill(0)
        }
    }

    fun encrypt(plaintext: String, key: SecretKey): String {
        val nonce = ByteArray(NONCE_BYTES).also(random::nextBytes)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(TAG_BITS, nonce))
        cipher.updateAAD(aad)
        val encrypted = cipher.doFinal(plaintext.toByteArray(StandardCharsets.UTF_8))
        val frame = ByteBuffer.allocate(nonce.size + encrypted.size)
            .put(nonce)
            .put(encrypted)
            .array()
        require(frame.size <= MAX_FRAME_BYTES) { "Encrypted frame is too large" }
        return Base64.getEncoder().encodeToString(frame)
    }

    fun decrypt(frameBase64: String, key: SecretKey): String {
        require(frameBase64.length <= MAX_FRAME_BYTES * 2) { "Encrypted frame is too large" }
        val frame = Base64.getDecoder().decode(frameBase64)
        require(frame.size in (NONCE_BYTES + 16)..MAX_FRAME_BYTES) { "Invalid encrypted frame" }
        val buffer = ByteBuffer.wrap(frame)
        val nonce = ByteArray(NONCE_BYTES).also(buffer::get)
        val ciphertext = ByteArray(buffer.remaining()).also(buffer::get)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(TAG_BITS, nonce))
        cipher.updateAAD(aad)
        return String(cipher.doFinal(ciphertext), StandardCharsets.UTF_8)
    }

    private fun sha256(value: ByteArray): ByteArray =
        java.security.MessageDigest.getInstance("SHA-256").digest(value)
}
