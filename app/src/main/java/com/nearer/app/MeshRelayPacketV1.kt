package com.nearer.app

import android.util.Base64
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.security.MessageDigest

/**
 * Opaque recipient-encrypted mesh envelope. Its immutable header and ciphertext
 * are signed by the origin; hopsTaken is mutable forwarding metadata.
 */
internal data class MeshRelayPacketV1(
    val packetId: String,
    val originId: String,
    val destinationId: String,
    val createdAtMs: Long,
    val expiresAtMs: Long,
    val hopLimit: Int,
    val hopsTaken: Int,
    val encryptedPayload: RecipientMessageCrypto.EncryptedPayload,
    val originSigningPublicKey: ByteArray,
    val originSignature: ByteArray
) {
    fun signedBytes(): ByteArray {
        val output = ByteArrayOutputStream()
        DataOutputStream(output).use { data ->
            data.write("NeaRer-mesh-envelope-v1".toByteArray(Charsets.US_ASCII))
            listOf(packetId, originId, destinationId).forEach { value ->
                val bytes = value.toByteArray(Charsets.UTF_8)
                data.writeShort(bytes.size)
                data.write(bytes)
            }
            data.writeLong(createdAtMs)
            data.writeLong(expiresAtMs)
            data.writeByte(hopLimit)
            writeBytes(data, encryptedPayload.ephemeralPublicKey)
            writeBytes(data, encryptedPayload.nonce)
            val ciphertextHash = MessageDigest.getInstance("SHA-256").digest(encryptedPayload.ciphertext)
            writeBytes(data, ciphertextHash)
            val signerFingerprint = MessageDigest.getInstance("SHA-256").digest(originSigningPublicKey)
            writeBytes(data, signerFingerprint)
        }
        return output.toByteArray()
    }

    fun validate(nowMs: Long): Boolean = try {
        require(NeaRerIdentity.normalize(originId) == originId)
        require(NeaRerIdentity.normalize(destinationId) == destinationId)
        require(originId != destinationId)
        require(packetId.matches(Regex("[A-Za-z0-9._:-]{1,80}")))
        require(createdAtMs >= 0L && expiresAtMs > createdAtMs)
        require(expiresAtMs > nowMs && createdAtMs <= nowMs + MAX_CLOCK_SKEW_MS)
        require(expiresAtMs - createdAtMs <= MAX_LIFETIME_MS)
        require(hopLimit in 1..4 && hopsTaken in 0..hopLimit)
        require(encryptedPayload.ephemeralPublicKey.isNotEmpty() && encryptedPayload.ephemeralPublicKey.size <= 512)
        require((java.security.KeyFactory.getInstance("EC")
            .generatePublic(java.security.spec.X509EncodedKeySpec(encryptedPayload.ephemeralPublicKey))
            as? java.security.interfaces.ECPublicKey)?.params?.curve?.field?.fieldSize == 256)
        require(encryptedPayload.nonce.size == 12)
        require(encryptedPayload.ciphertext.size in 16..24576)
        require(originSigningPublicKey.isNotEmpty() && originSigningPublicKey.size <= 512)
        require((java.security.KeyFactory.getInstance("EC")
            .generatePublic(java.security.spec.X509EncodedKeySpec(originSigningPublicKey))
            as? java.security.interfaces.ECPublicKey)?.params?.curve?.field?.fieldSize == 256)
        require(originSignature.size in 8..128)
        true
    } catch (_: Exception) { false }

    fun verify(trustedOriginSigningKey: ByteArray, nowMs: Long): Boolean {
        if (!validate(nowMs) || !MessageDigest.isEqual(originSigningPublicKey, trustedOriginSigningKey)) return false
        return SecurePeerIdentity.verifyDetached(trustedOriginSigningKey, signedBytes(), originSignature)
    }

    /** Checks packet integrity using its embedded signer key; this is NOT identity trust. */
    fun verifyEmbeddedOrigin(nowMs: Long): Boolean =
        validate(nowMs) && SecurePeerIdentity.verifyDetached(originSigningPublicKey, signedBytes(), originSignature)

    /** Exact duplicate check used by persistent transit storage. */
    fun sameImmutablePacket(other: MeshRelayPacketV1): Boolean =
        packetId == other.packetId &&
            MessageDigest.isEqual(signedBytes(), other.signedBytes()) &&
            MessageDigest.isEqual(originSignature, other.originSignature) &&
            MessageDigest.isEqual(encryptedPayload.ciphertext, other.encryptedPayload.ciphertext)

    /** Returns null at the hop limit. Does not alter the origin-signed content. */
    fun forwarded(): MeshRelayPacketV1? =
        if (hopsTaken >= hopLimit) null else copy(hopsTaken = hopsTaken + 1)

    fun encode(): ByteArray = JSONObject()
        .put("kind", "mesh_packet_v1")
        .put("packetId", packetId)
        .put("originId", originId)
        .put("destinationId", destinationId)
        .put("createdAtMs", createdAtMs)
        .put("expiresAtMs", expiresAtMs)
        .put("hopLimit", hopLimit)
        .put("hopsTaken", hopsTaken)
        .put("ephemeralPublicKey", Base64.encodeToString(encryptedPayload.ephemeralPublicKey, Base64.NO_WRAP))
        .put("nonce", Base64.encodeToString(encryptedPayload.nonce, Base64.NO_WRAP))
        .put("ciphertext", Base64.encodeToString(encryptedPayload.ciphertext, Base64.NO_WRAP))
        .put("originSigningPublicKey", Base64.encodeToString(originSigningPublicKey, Base64.NO_WRAP))
        .put("originSignature", Base64.encodeToString(originSignature, Base64.NO_WRAP))
        .toString().toByteArray(Charsets.UTF_8)

    private fun writeBytes(data: DataOutputStream, value: ByteArray) {
        data.writeInt(value.size)
        data.write(value)
    }

    companion object {
        // Nearby Connections BYTES payloads are limited to 32 KiB.
        const val MAX_NEARBY_BYTES = 32 * 1024
        private const val MAX_CLOCK_SKEW_MS = 5L * 60L * 1000L
        private const val MAX_LIFETIME_MS = 7L * 24L * 60L * 60L * 1000L

        fun create(
            originId: String,
            destinationId: String,
            packetId: String,
            createdAtMs: Long,
            expiresAtMs: Long,
            hopLimit: Int,
            recipientPublicKey: ByteArray,
            plaintext: ByteArray
        ): MeshRelayPacketV1 {
            val encrypted = RecipientMessageCrypto.encrypt(
                originId, destinationId, packetId, createdAtMs, expiresAtMs,
                hopLimit, recipientPublicKey, plaintext
            )
            val signingPublicKey = SecurePeerIdentity.publicKeyEncoded()
            val unsigned = MeshRelayPacketV1(
                packetId, originId, destinationId, createdAtMs, expiresAtMs,
                hopLimit, 0, encrypted, signingPublicKey, ByteArray(0)
            )
            val signature = SecurePeerIdentity.signDetached(unsigned.signedBytes())
            return unsigned.copy(originSignature = signature)
        }

        fun decode(bytes: ByteArray): MeshRelayPacketV1 {
            require(bytes.size in 1..MAX_NEARBY_BYTES)
            val json = JSONObject(bytes.toString(Charsets.UTF_8))
            require(json.optString("kind") == "mesh_packet_v1")
            return MeshRelayPacketV1(
                packetId = json.getString("packetId"),
                originId = json.getString("originId"),
                destinationId = json.getString("destinationId"),
                createdAtMs = json.getLong("createdAtMs"),
                expiresAtMs = json.getLong("expiresAtMs"),
                hopLimit = json.getInt("hopLimit"),
                hopsTaken = json.getInt("hopsTaken"),
                encryptedPayload = RecipientMessageCrypto.EncryptedPayload(
                    Base64.decode(json.getString("ephemeralPublicKey"), Base64.NO_WRAP),
                    Base64.decode(json.getString("nonce"), Base64.NO_WRAP),
                    Base64.decode(json.getString("ciphertext"), Base64.NO_WRAP)
                ),
                originSigningPublicKey = Base64.decode(json.getString("originSigningPublicKey"), Base64.NO_WRAP),
                originSignature = Base64.decode(json.getString("originSignature"), Base64.NO_WRAP)
            )
        }
    }
}
