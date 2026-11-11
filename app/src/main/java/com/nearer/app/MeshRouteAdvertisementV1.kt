package com.nearer.app

import android.util.Base64
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.security.MessageDigest

/**
 * Signed next-hop assertion. The destination signature binds its stable ECDH
 * encryption key to its signing identity; the advertiser signature binds the
 * route claim to the authenticated neighbor that announced it.
 */
internal data class MeshRouteAdvertisementV1(
    val destinationId: String,
    val destinationSigningPublicKey: ByteArray,
    val destinationEncryptionPublicKey: ByteArray,
    val destinationKeyBindingSignature: ByteArray,
    val advertiserId: String,
    val nextHopId: String,
    val hopCount: Int,
    val sequence: Long,
    val issuedAtMs: Long,
    val expiresAtMs: Long,
    val advertiserSigningPublicKey: ByteArray,
    val advertiserSignature: ByteArray
) {
    fun destinationBindingBytes(): ByteArray {
        val output = ByteArrayOutputStream()
        DataOutputStream(output).use { data ->
            data.write("NeaRer-destination-key-binding-v1".toByteArray(Charsets.US_ASCII))
            writeString(data, destinationId)
            writeBytes(data, MessageDigest.getInstance("SHA-256").digest(destinationEncryptionPublicKey))
        }
        return output.toByteArray()
    }

    fun signedBytes(): ByteArray {
        val output = ByteArrayOutputStream()
        DataOutputStream(output).use { data ->
            data.write("NeaRer-route-advertisement-v1".toByteArray(Charsets.US_ASCII))
            writeString(data, destinationId)
            writeBytes(data, MessageDigest.getInstance("SHA-256").digest(destinationSigningPublicKey))
            writeBytes(data, MessageDigest.getInstance("SHA-256").digest(destinationEncryptionPublicKey))
            writeBytes(data, MessageDigest.getInstance("SHA-256").digest(destinationKeyBindingSignature))
            writeString(data, advertiserId)
            writeString(data, nextHopId)
            data.writeByte(hopCount)
            data.writeLong(sequence)
            data.writeLong(issuedAtMs)
            data.writeLong(expiresAtMs)
            writeBytes(data, MessageDigest.getInstance("SHA-256").digest(advertiserSigningPublicKey))
        }
        return output.toByteArray()
    }

    fun validate(nowMs: Long): Boolean = try {
        require(NeaRerIdentity.normalize(destinationId) == destinationId)
        require(NeaRerIdentity.normalize(advertiserId) == advertiserId)
        require(NeaRerIdentity.normalize(nextHopId) == nextHopId)
        require(sequence >= 0L && issuedAtMs >= 0L && expiresAtMs > issuedAtMs)
        require(issuedAtMs <= nowMs + MAX_CLOCK_SKEW_MS && expiresAtMs > nowMs)
        require(expiresAtMs - issuedAtMs <= MAX_LIFETIME_MS)
        require(hopCount in 0..4)
        if (hopCount == 0) {
            require(advertiserId == destinationId && nextHopId == destinationId)
        } else {
            require(advertiserId != destinationId && nextHopId != advertiserId)
        }
        require(isP256(destinationSigningPublicKey) && isP256(destinationEncryptionPublicKey))
        require(isP256(advertiserSigningPublicKey))
        require(destinationKeyBindingSignature.size in 8..128 && advertiserSignature.size in 8..128)
        true
    } catch (_: Exception) { false }

    /** Both the advertiser and destination identities must already be trusted by the caller. */
    fun verify(
        trustedAdvertiserSigningKey: ByteArray,
        trustedDestinationSigningKey: ByteArray,
        nowMs: Long
    ): Boolean {
        if (!validate(nowMs)) return false
        if (!MessageDigest.isEqual(advertiserSigningPublicKey, trustedAdvertiserSigningKey)) return false
        if (!MessageDigest.isEqual(destinationSigningPublicKey, trustedDestinationSigningKey)) return false
        val destinationBindingValid = SecurePeerIdentity.verifyChallenge(
            destinationId, trustedDestinationSigningKey, destinationBindingBytes(), destinationKeyBindingSignature
        )
        if (!destinationBindingValid) return false
        return SecurePeerIdentity.verifyChallenge(
            advertiserId, trustedAdvertiserSigningKey, signedBytes(), advertiserSignature
        )
    }

    fun encode(): ByteArray = JSONObject()
        .put("kind", "mesh_route_v1")
        .put("destinationId", destinationId)
        .put("destinationSigningPublicKey", Base64.encodeToString(destinationSigningPublicKey, Base64.NO_WRAP))
        .put("destinationEncryptionPublicKey", Base64.encodeToString(destinationEncryptionPublicKey, Base64.NO_WRAP))
        .put("destinationKeyBindingSignature", Base64.encodeToString(destinationKeyBindingSignature, Base64.NO_WRAP))
        .put("advertiserId", advertiserId)
        .put("nextHopId", nextHopId)
        .put("hopCount", hopCount)
        .put("sequence", sequence)
        .put("issuedAtMs", issuedAtMs)
        .put("expiresAtMs", expiresAtMs)
        .put("advertiserSigningPublicKey", Base64.encodeToString(advertiserSigningPublicKey, Base64.NO_WRAP))
        .put("advertiserSignature", Base64.encodeToString(advertiserSignature, Base64.NO_WRAP))
        .toString().toByteArray(Charsets.UTF_8)

    private fun writeString(data: DataOutputStream, value: String) {
        val bytes = value.toByteArray(Charsets.UTF_8)
        data.writeShort(bytes.size)
        data.write(bytes)
    }

    private fun writeBytes(data: DataOutputStream, value: ByteArray) {
        data.writeShort(value.size)
        data.write(value)
    }

    companion object {
        private const val MAX_CLOCK_SKEW_MS = 60L * 1000L
        private const val MAX_LIFETIME_MS = 10L * 60L * 1000L

        fun createLocalDestination(
            localId: String,
            destinationEncryptionPublicKey: ByteArray,
            sequence: Long,
            issuedAtMs: Long,
            expiresAtMs: Long
        ): MeshRouteAdvertisementV1 {
            val signingKey = SecurePeerIdentity.publicKeyEncoded()
            val unsigned = MeshRouteAdvertisementV1(
                destinationId = localId,
                destinationSigningPublicKey = signingKey,
                destinationEncryptionPublicKey = destinationEncryptionPublicKey,
                destinationKeyBindingSignature = ByteArray(0),
                advertiserId = localId,
                nextHopId = localId,
                hopCount = 0,
                sequence = sequence,
                issuedAtMs = issuedAtMs,
                expiresAtMs = expiresAtMs,
                advertiserSigningPublicKey = signingKey,
                advertiserSignature = ByteArray(0)
            )
            val binding = SecurePeerIdentity.signChallenge(localId, signingKey, unsigned.destinationBindingBytes())
            val withBinding = unsigned.copy(destinationKeyBindingSignature = binding)
            val signature = SecurePeerIdentity.signChallenge(localId, signingKey, withBinding.signedBytes())
            return withBinding.copy(advertiserSignature = signature)
        }

        fun createForwardedBy(
            localAdvertiserId: String,
            nextHopId: String,
            hopCount: Int,
            sequence: Long,
            issuedAtMs: Long,
            expiresAtMs: Long,
            destinationId: String,
            destinationSigningPublicKey: ByteArray,
            destinationEncryptionPublicKey: ByteArray,
            destinationKeyBindingSignature: ByteArray
        ): MeshRouteAdvertisementV1 {
            require(hopCount in 1..4)
            val advertiserKey = SecurePeerIdentity.publicKeyEncoded()
            val unsigned = MeshRouteAdvertisementV1(
                destinationId, destinationSigningPublicKey, destinationEncryptionPublicKey,
                destinationKeyBindingSignature, localAdvertiserId, nextHopId, hopCount,
                sequence, issuedAtMs, expiresAtMs, advertiserKey, ByteArray(0)
            )
            val signature = SecurePeerIdentity.signChallenge(localAdvertiserId, advertiserKey, unsigned.signedBytes())
            return unsigned.copy(advertiserSignature = signature)
        }

        fun decode(bytes: ByteArray): MeshRouteAdvertisementV1 {
            require(bytes.size in 1..12000)
            val json = JSONObject(bytes.toString(Charsets.UTF_8))
            require(json.optString("kind") == "mesh_route_v1")
            return MeshRouteAdvertisementV1(
                json.getString("destinationId"),
                Base64.decode(json.getString("destinationSigningPublicKey"), Base64.NO_WRAP),
                Base64.decode(json.getString("destinationEncryptionPublicKey"), Base64.NO_WRAP),
                Base64.decode(json.getString("destinationKeyBindingSignature"), Base64.NO_WRAP),
                json.getString("advertiserId"),
                json.getString("nextHopId"),
                json.getInt("hopCount"),
                json.getLong("sequence"),
                json.getLong("issuedAtMs"),
                json.getLong("expiresAtMs"),
                Base64.decode(json.getString("advertiserSigningPublicKey"), Base64.NO_WRAP),
                Base64.decode(json.getString("advertiserSignature"), Base64.NO_WRAP)
            )
        }

        private fun isP256(encoded: ByteArray): Boolean = try {
            if (encoded.isEmpty() || encoded.size > 512) return false
            val key = java.security.KeyFactory.getInstance("EC")
                .generatePublic(java.security.spec.X509EncodedKeySpec(encoded))
            (key as? java.security.interfaces.ECPublicKey)?.params?.curve?.field?.fieldSize == 256
        } catch (_: Exception) { false }
    }
}
