package com.nearer.app

import android.content.Context
import android.util.Base64
import org.json.JSONObject
import java.security.KeyFactory
import java.security.MessageDigest
import java.security.spec.X509EncodedKeySpec

/**
 * Local TOFU pins bind a self-reported NeaRer ID to both its signing identity
 * and its recipient ECDH public key. This is key continuity, not real-world identity.
 */
internal object TrustedPeerKeyStore {
    enum class Result { FIRST_SEEN, MATCH, KEY_CHANGED, STORAGE_ERROR }

    /** Read-only TOFU/key-continuity check; does not pin until the signed binding is verified. */
    @Synchronized
    fun checkPinned(
        context: Context,
        publicId: String,
        signingPublicKey: ByteArray,
        recipientEncryptionPublicKey: ByteArray
    ): Result {
        if (NeaRerIdentity.normalize(publicId) != publicId ||
            !isP256(signingPublicKey) || !isP256(recipientEncryptionPublicKey)) return Result.STORAGE_ERROR
        return try {
            val prefs = context.applicationContext.getSharedPreferences("nearer_local", Context.MODE_PRIVATE)
            val signingPins = JSONObject(prefs.getString("nearby_peer_key_pins", "{}") ?: "{}")
            val encryptionPins = JSONObject(prefs.getString("nearby_peer_encryption_key_pins", "{}") ?: "{}")
            val savedSigning = decodePin(signingPins, publicId)
            val savedEncryption = decodePin(encryptionPins, publicId)
            if (savedSigning != null && !MessageDigest.isEqual(savedSigning, signingPublicKey)) return Result.KEY_CHANGED
            if (savedEncryption != null && !MessageDigest.isEqual(savedEncryption, recipientEncryptionPublicKey)) return Result.KEY_CHANGED
            if (savedSigning == null && savedEncryption != null) return Result.STORAGE_ERROR
            if (savedSigning == null || savedEncryption == null) Result.FIRST_SEEN else Result.MATCH
        } catch (_: Exception) { Result.STORAGE_ERROR }
    }

    @Synchronized
    fun checkAndPin(
        context: Context,
        publicId: String,
        signingPublicKey: ByteArray,
        recipientEncryptionPublicKey: ByteArray
    ): Result {
        if (NeaRerIdentity.normalize(publicId) != publicId ||
            !isP256(signingPublicKey) || !isP256(recipientEncryptionPublicKey)) return Result.STORAGE_ERROR
        return try {
            val prefs = context.applicationContext.getSharedPreferences("nearer_local", Context.MODE_PRIVATE)
            val signingPins = JSONObject(prefs.getString("nearby_peer_key_pins", "{}") ?: "{}")
            val encryptionPins = JSONObject(prefs.getString("nearby_peer_encryption_key_pins", "{}") ?: "{}")
            val savedSigning = decodePin(signingPins, publicId)
            val savedEncryption = decodePin(encryptionPins, publicId)

            if (savedSigning != null && !MessageDigest.isEqual(savedSigning, signingPublicKey)) return Result.KEY_CHANGED
            if (savedEncryption != null && !MessageDigest.isEqual(savedEncryption, recipientEncryptionPublicKey)) return Result.KEY_CHANGED
            if (savedSigning == null && savedEncryption != null) return Result.STORAGE_ERROR

            val firstSeen = savedSigning == null || savedEncryption == null
            if (firstSeen) {
                signingPins.put(publicId, Base64.encodeToString(signingPublicKey, Base64.NO_WRAP))
                encryptionPins.put(publicId, Base64.encodeToString(recipientEncryptionPublicKey, Base64.NO_WRAP))
                val saved = prefs.edit()
                    .putString("nearby_peer_key_pins", signingPins.toString())
                    .putString("nearby_peer_encryption_key_pins", encryptionPins.toString())
                    .commit()
                if (!saved) return Result.STORAGE_ERROR
            }
            if (firstSeen) Result.FIRST_SEEN else Result.MATCH
        } catch (_: Exception) {
            Result.STORAGE_ERROR
        }
    }

    @Synchronized
    fun pinnedSigningKey(context: Context, publicId: String): ByteArray? = try {
        if (NeaRerIdentity.normalize(publicId) != publicId) return null
        val prefs = context.applicationContext.getSharedPreferences("nearer_local", Context.MODE_PRIVATE)
        decodePin(JSONObject(prefs.getString("nearby_peer_key_pins", "{}") ?: "{}"), publicId)
    } catch (_: Exception) { null }

    @Synchronized
    fun pinnedRecipientEncryptionKey(context: Context, publicId: String): ByteArray? = try {
        if (NeaRerIdentity.normalize(publicId) != publicId) return null
        val prefs = context.applicationContext.getSharedPreferences("nearer_local", Context.MODE_PRIVATE)
        decodePin(JSONObject(prefs.getString("nearby_peer_encryption_key_pins", "{}") ?: "{}"), publicId)
    } catch (_: Exception) { null }

    @Synchronized
    fun acceptBindingSequence(context: Context, publicId: String, sequence: Long): Boolean {
        if (NeaRerIdentity.normalize(publicId) != publicId || sequence < 0L) return false
        return try {
            val prefs = context.applicationContext.getSharedPreferences("nearer_local", Context.MODE_PRIVATE)
            val sequences = JSONObject(prefs.getString("nearby_peer_binding_sequences", "{}") ?: "{}")
            val previous = if (sequences.has(publicId)) sequences.getLong(publicId) else -1L
            if (sequence <= previous) return false
            sequences.put(publicId, sequence)
            prefs.edit().putString("nearby_peer_binding_sequences", sequences.toString()).commit()
        } catch (_: Exception) { false }
    }

    /** Both identity keys must be pinned before SmartConnect may skip first-contact approval. */
    @Synchronized
    fun hasPinnedPeer(context: Context, publicId: String): Boolean {
        if (NeaRerIdentity.normalize(publicId) != publicId) return false
        return pinnedSigningKey(context, publicId) != null &&
            pinnedRecipientEncryptionKey(context, publicId) != null
    }

    private fun decodePin(pins: JSONObject, id: String): ByteArray? {
        if (!pins.has(id)) return null
        val encoded = pins.optString(id)
        if (encoded.isBlank()) throw IllegalStateException("Saved peer key pin is empty.")
        return Base64.decode(encoded, Base64.NO_WRAP)
    }

    private fun isP256(encoded: ByteArray): Boolean = try {
        if (encoded.isEmpty() || encoded.size > 512) return false
        val key = KeyFactory.getInstance("EC").generatePublic(X509EncodedKeySpec(encoded))
        (key as? java.security.interfaces.ECPublicKey)?.params?.curve?.field?.fieldSize == 256
    } catch (_: Exception) { false }
}
