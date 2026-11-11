package com.nearer.app

import android.util.Base64
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.security.KeyFactory
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.MessageDigest
import java.security.SecureRandom
import java.security.spec.ECGenParameterSpec
import java.security.spec.X509EncodedKeySpec
import javax.crypto.Cipher
import javax.crypto.KeyAgreement
import javax.crypto.Mac
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Per-connection end-to-end chat encryption.
 *
 * A fresh P-256 ECDH keypair is created for every Nearby connection. The long-term
 * device signing identity authenticates both ephemeral keys through the handshake
 * transcript. Directional AES-256-GCM keys are then derived with HKDF-SHA256.
 */
internal object NearbyMessageCrypto {
    private const val VERSION = 1
    private const val NONCE_BYTES = 12
    private const val TAG_BITS = 128

    data class Session(val sendKey: ByteArray, val receiveKey: ByteArray)

    fun newEphemeralKeyPair(): KeyPair =
        KeyPairGenerator.getInstance("EC").apply {
            initialize(ECGenParameterSpec("secp256r1"))
        }.generateKeyPair()

    fun decodeEphemeralPublicKey(encoded: ByteArray): java.security.PublicKey {
        require(encoded.isNotEmpty() && encoded.size <= 512)
        val key = KeyFactory.getInstance("EC").generatePublic(X509EncodedKeySpec(encoded))
        require(key.algorithm.equals("EC", ignoreCase = true))
        return key
    }

    fun deriveSession(
        localId: String,
        remoteId: String,
        localPrivateKey: java.security.PrivateKey,
        remotePublicKey: java.security.PublicKey,
        transcript: ByteArray
    ): Session {
        require(localId.isNotBlank() && remoteId.isNotBlank() && localId != remoteId)
        val agreement = KeyAgreement.getInstance("ECDH")
        agreement.init(localPrivateKey)
        agreement.doPhase(remotePublicKey, true)
        val sharedSecret = agreement.generateSecret()
        try {
            val salt = MessageDigest.getInstance("SHA-256").digest(transcript)
            val info = "NeaRer-chat-E2E-v1".toByteArray(Charsets.US_ASCII)
            val material = hkdfSha256(sharedSecret, salt, info, 64)
            return if (localId < remoteId) {
                Session(material.copyOfRange(0, 32), material.copyOfRange(32, 64))
            } else {
                Session(material.copyOfRange(32, 64), material.copyOfRange(0, 32))
            }
        } finally {
            sharedSecret.fill(0)
        }
    }

    fun encrypt(session: Session, senderId: String, recipientId: String, messageId: String, plaintext: ByteArray): ByteArray {
        require(messageId.isNotBlank() && messageId.length <= 80)
        val nonce = ByteArray(NONCE_BYTES).also { SecureRandom().nextBytes(it) }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(session.sendKey, "AES"), GCMParameterSpec(TAG_BITS, nonce))
        cipher.updateAAD(aad(senderId, recipientId, messageId))
        val encrypted = cipher.doFinal(plaintext)
        return JSONObject()
            .put("kind", "secure_packet_v1")
            .put("version", VERSION)
            .put("id", messageId)
            .put("nonce", Base64.encodeToString(nonce, Base64.NO_WRAP))
            .put("ciphertext", Base64.encodeToString(encrypted, Base64.NO_WRAP))
            .toString().toByteArray(Charsets.UTF_8)
    }

    fun decrypt(session: Session, recipientId: String, senderId: String, packet: JSONObject): Pair<String, ByteArray> {
        require(packet.optString("kind") == "secure_packet_v1")
        require(packet.optInt("version", -1) == VERSION)
        val messageId = packet.getString("id")
        require(messageId.isNotBlank() && messageId.length <= 80)
        val nonce = Base64.decode(packet.getString("nonce"), Base64.NO_WRAP)
        val ciphertext = Base64.decode(packet.getString("ciphertext"), Base64.NO_WRAP)
        require(nonce.size == NONCE_BYTES && ciphertext.size in 16..16384)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(session.receiveKey, "AES"), GCMParameterSpec(TAG_BITS, nonce))
        cipher.updateAAD(aad(senderId, recipientId, messageId))
        return messageId to cipher.doFinal(ciphertext)
    }

    fun transcript(
        firstId: String, firstNonce: ByteArray, firstEphemeralKey: ByteArray,
        secondId: String, secondNonce: ByteArray, secondEphemeralKey: ByteArray
    ): ByteArray {
        require(firstId != secondId && firstNonce.size == 32 && secondNonce.size == 32)
        require(firstEphemeralKey.isNotEmpty() && firstEphemeralKey.size <= 512)
        require(secondEphemeralKey.isNotEmpty() && secondEphemeralKey.size <= 512)
        val ordered = if (firstId < secondId) {
            listOf(Triple(firstId, firstNonce, firstEphemeralKey), Triple(secondId, secondNonce, secondEphemeralKey))
        } else {
            listOf(Triple(secondId, secondNonce, secondEphemeralKey), Triple(firstId, firstNonce, firstEphemeralKey))
        }
        val output = ByteArrayOutputStream()
        DataOutputStream(output).use { data ->
            data.write("NeaRer-Nearby-Peer-Handshake-v2".toByteArray(Charsets.US_ASCII))
            ordered.forEach { (id, nonce, key) ->
                val encoded = id.toByteArray(Charsets.UTF_8)
                data.writeShort(encoded.size)
                data.write(encoded)
                data.writeByte(nonce.size)
                data.write(nonce)
                data.writeShort(key.size)
                data.write(key)
            }
        }
        return output.toByteArray()
    }

    private fun aad(senderId: String, recipientId: String, messageId: String): ByteArray =
        "NeaRer-secure-message-v1\n$senderId\n$recipientId\n$messageId".toByteArray(Charsets.UTF_8)

    private fun hkdfSha256(ikm: ByteArray, salt: ByteArray, info: ByteArray, length: Int): ByteArray {
        val extract = Mac.getInstance("HmacSHA256")
        extract.init(SecretKeySpec(salt, "HmacSHA256"))
        val prk = extract.doFinal(ikm)
        try {
            val output = ByteArrayOutputStream()
            var previous = ByteArray(0)
            var counter = 1
            while (output.size() < length) {
                val expand = Mac.getInstance("HmacSHA256")
                expand.init(SecretKeySpec(prk, "HmacSHA256"))
                expand.update(previous)
                expand.update(info)
                expand.update(counter.toByte())
                previous = expand.doFinal()
                output.write(previous)
                counter++
            }
            return output.toByteArray().copyOf(length)
        } finally {
            prk.fill(0)
        }
    }
}
