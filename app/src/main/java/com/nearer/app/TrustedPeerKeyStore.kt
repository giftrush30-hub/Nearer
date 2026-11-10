package com.nearer.app

import android.content.Context
import android.util.Base64
import org.json.JSONObject
import java.security.MessageDigest

/**
 * Local trust-on-first-use pin store for nearby peer signing keys.
 *
 * A pin prevents a later connection from silently changing the key associated
 * with a self-reported NeaRer ID. It does not authenticate a real-world person,
 * and first use still requires the user to decide whether the peer is expected.
 */
internal object TrustedPeerKeyStore {
    enum class Result { FIRST_SEEN, MATCH, KEY_CHANGED, STORAGE_ERROR }

    @Synchronized
    fun checkAndPin(context: Context, publicId: String, publicKey: ByteArray): Result {
        if (!NeaRerIdentity.normalize(publicId) != null || !SecurePeerIdentity.isValidPublicKey(publicKey)) {
            return Result.STORAGE_ERROR
        }
        return try {
            val prefs = context.applicationContext.getSharedPreferences("nearer_local", Context.MODE_PRIVATE)
            val raw = prefs.getString("nearby_peer_key_pins", "{}") ?: "{}"
            val pins = JSONObject(raw)
            val encoded = Base64.encodeToString(publicKey, Base64.NO_WRAP)
            if (pins.has(publicId)) {
                val saved = pins.optString(publicId)
                if (saved.isBlank()) return Result.STORAGE_ERROR
                val savedBytes = try { Base64.decode(saved, Base64.NO_WRAP) } catch (_: Exception) { return Result.STORAGE_ERROR }
                if (MessageDigest.isEqual(savedBytes, publicKey)) Result.MATCH else Result.KEY_CHANGED
            } else {
                pins.put(publicId, encoded)
                if (prefs.edit().putString("nearby_peer_key_pins", pins.toString()).commit()) {
                    Result.FIRST_SEEN
                } else {
                    Result.STORAGE_ERROR
                }
            }
        } catch (_: Exception) {
            Result.STORAGE_ERROR
        }
    }
}
