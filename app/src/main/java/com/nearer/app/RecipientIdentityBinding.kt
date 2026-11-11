package com.nearer.app

import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.security.KeyFactory
import java.security.MessageDigest
import java.security.Signature
import java.security.spec.X509EncodedKeySpec

/**
 * Binds a stable recipient ECDH public key to a device's pinned signing identity.
 * A relay recipient key is not trusted merely because it is self-presented.
 */
internal data class RecipientIdentityBinding(
    val deviceId: String,
    val signingKeyFingerprint: ByteArray,
    val recipientEncryptionPublicKey: ByteArray,
    val sequence: Long,
    val issuedAtMs: Long,
    val expiresAtMs: Long,
    val signature: ByteArray
) {
    fun signedBytes(): ByteArray {
        val out = ByteArrayOutputStream()
        DataOutputStream(out).use { data ->
            data.write("NeaRer-recipient-identity-binding-v1".toByteArray(Charsets.US_ASCII))
            val id = deviceId.toByteArray(Charsets.UTF_8)
            data.writeShort(id.size)
            data.write(id)
            data.writeInt(signingKeyFingerprint.size)
            data.write(signingKeyFingerprint)
            data.writeInt(recipientEncryptionPublicKey.size)
            data.write(recipientEncryptionPublicKey)
            data.writeLong(sequence)
            data.writeLong(issuedAtMs)
            data.writeLong(expiresAtMs)
        }
        return out.toByteArray()
    }

    fun verify(pinnedSigningPublicKey: ByteArray, nowMs: Long): Boolean = try {
        require(NeaRerIdentity.normalize(deviceId) == deviceId)
        require(MessageDigest.isEqual(signingKeyFingerprint, MessageDigest.getInstance("SHA-256").digest(pinnedSigningPublicKey)))
        require(recipientEncryptionPublicKey.size in 1..512)
        val encryptionKey = KeyFactory.getInstance("EC")
            .generatePublic(X509EncodedKeySpec(recipientEncryptionPublicKey))
        require((encryptionKey as? java.security.interfaces.ECPublicKey)?.params?.curve?.field?.fieldSize == 256)
        require(sequence >= 0L && issuedAtMs <= nowMs + 300_000L)
        require(expiresAtMs > nowMs && expiresAtMs > issuedAtMs)
        require(expiresAtMs - issuedAtMs <= MAX_BINDING_LIFETIME_MS)
        require(signature.size in 8..128)
        val signingKey = KeyFactory.getInstance("EC").generatePublic(X509EncodedKeySpec(pinnedSigningPublicKey))
        val verifier = Signature.getInstance("SHA256withECDSA")
        verifier.initVerify(signingKey)
        verifier.update(signedBytes())
        verifier.verify(signature)
    } catch (_: Exception) { false }

    companion object {
        private const val MAX_BINDING_LIFETIME_MS = 24L * 60L * 60L * 1000L

        fun create(
            deviceId: String,
            signingPublicKey: ByteArray,
            recipientEncryptionPublicKey: ByteArray,
            sequence: Long,
            issuedAtMs: Long,
            expiresAtMs: Long,
            sign: (ByteArray) -> ByteArray
        ): RecipientIdentityBinding {
            val unsigned = RecipientIdentityBinding(
                deviceId,
                MessageDigest.getInstance("SHA-256").digest(signingPublicKey),
                recipientEncryptionPublicKey,
                sequence, issuedAtMs, expiresAtMs, ByteArray(0)
            )
            require(NeaRerIdentity.normalize(deviceId) == deviceId)
            require(sequence >= 0L && expiresAtMs > issuedAtMs)
            require(expiresAtMs - issuedAtMs <= MAX_BINDING_LIFETIME_MS)
            return unsigned.copy(signature = sign(unsigned.signedBytes()))
        }

        fun createLocal(context: android.content.Context, deviceId: String, sequence: Long, nowMs: Long): RecipientIdentityBinding {
            val signingPublic = SecurePeerIdentity.publicKeyEncoded()
            val recipient = RecipientEncryptionIdentity.getOrCreate(context)
            return create(
                deviceId, signingPublic, recipient.publicKeyEncoded,
                sequence, nowMs, nowMs + MAX_BINDING_LIFETIME_MS,
                SecurePeerIdentity::signDetached
            )
        }
    }
}
