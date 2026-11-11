package com.nearer.app

import android.content.Context
import android.util.Base64
import org.json.JSONObject

/**
 * Encrypted-at-rest cache of signed route advertisements.
 * Restored routes remain unusable until their authenticated next-hop endpoint reconnects.
 */
internal class MeshRoutePersistence(context: Context) {
    private val app = context.applicationContext
    private val prefs = app.getSharedPreferences("nearer_mesh_route_cache_v1", Context.MODE_PRIVATE)

    @Synchronized
    fun save(advertisement: SignedRouteAdvertisement, destinationEncryptionKey: ByteArray): Boolean {
        if (destinationEncryptionKey.isEmpty() || destinationEncryptionKey.size > 512) return false
        val fingerprint = java.security.MessageDigest.getInstance("SHA-256").digest(destinationEncryptionKey)
        if (!java.security.MessageDigest.isEqual(fingerprint, advertisement.destinationKeyFingerprint)) return false
        val entries = read() ?: LinkedHashMap()
        entries[advertisement.destinationId] = JSONObject()
            .put("advertisement", Base64.encodeToString(advertisement.encode(), Base64.NO_WRAP))
            .put("destinationKey", Base64.encodeToString(destinationEncryptionKey, Base64.NO_WRAP))
        while (entries.size > MAX_ROUTES) entries.remove(entries.keys.first())
        return write(entries)
    }

    @Synchronized
    fun removeDestination(destinationId: String): Boolean {
        if (NeaRerIdentity.normalize(destinationId) != destinationId) return false
        val entries = read() ?: return false
        entries.remove(destinationId)
        return write(entries)
    }

    @Synchronized
    fun removeAdvertiser(advertiserId: String): Boolean {
        if (NeaRerIdentity.normalize(advertiserId) != advertiserId) return false
        val entries = read() ?: return false
        val iterator = entries.entries.iterator()
        while (iterator.hasNext()) {
            val item = iterator.next().value
            val ad = runCatching {
                SignedRouteAdvertisement.decode(Base64.decode(item.getString("advertisement"), Base64.NO_WRAP))
            }.getOrNull()
            if (ad?.advertiserId == advertiserId) iterator.remove()
        }
        return write(entries)
    }

    @Synchronized
    fun restore(
        table: MeshRouteTable,
        pinnedSigningKey: (String) -> ByteArray?,
        nowMs: Long
    ): Int {
        val entries = read() ?: return 0
        var restored = 0
        val valid = LinkedHashMap<String, JSONObject>()
        entries.forEach { (destinationId, item) ->
            try {
                val ad = SignedRouteAdvertisement.decode(
                    Base64.decode(item.getString("advertisement"), Base64.NO_WRAP)
                )
                val destinationKey = Base64.decode(item.getString("destinationKey"), Base64.NO_WRAP)
                val signingKey = pinnedSigningKey(ad.advertiserId) ?: return@forEach
                if (ad.destinationId != destinationId || ad.expiresAtMs <= nowMs) return@forEach
                if (table.update(ad, ad.advertiserId, signingKey, nowMs, destinationKey)) {
                    valid[destinationId] = item
                    restored += 1
                }
            } catch (_: Exception) { }
        }
        // Prune expired, malformed, or no-longer-trusted records during restore.
        write(valid)
        return restored
    }

    @Synchronized
    fun destinationKeys(): Map<String, ByteArray> {
        val entries = read() ?: return emptyMap()
        val result = LinkedHashMap<String, ByteArray>()
        entries.forEach { (destinationId, item) ->
            try {
                val advertisement = SignedRouteAdvertisement.decode(
                    Base64.decode(item.getString("advertisement"), Base64.NO_WRAP)
                )
                val key = Base64.decode(item.getString("destinationKey"), Base64.NO_WRAP)
                val fingerprint = java.security.MessageDigest.getInstance("SHA-256").digest(key)
                if (advertisement.destinationId == destinationId &&
                    java.security.MessageDigest.isEqual(fingerprint, advertisement.destinationKeyFingerprint)) {
                    result[destinationId] = key
                }
            } catch (_: Exception) { }
        }
        return result
    }

    /** Remove every cached route when replay protection cannot be committed. */
    @Synchronized
    fun clearAll(): Boolean = try {
        prefs.edit().remove(KEY).commit()
    } catch (_: Exception) { false }

    @Synchronized
    fun size(): Int = read()?.size ?: 0

    private fun read(): LinkedHashMap<String, JSONObject>? = try {
        val encrypted = prefs.getString(KEY, null) ?: return LinkedHashMap()
        val clear = LocalDataCipher.decrypt(app, encrypted) ?: return null
        val json = JSONObject(clear.toString(Charsets.UTF_8))
        if (json.length() > MAX_ROUTES) return null
        val result = LinkedHashMap<String, JSONObject>()
        val keys = json.keys()
        while (keys.hasNext()) {
            val id = keys.next()
            if (NeaRerIdentity.normalize(id) != id) return null
            val item = json.optJSONObject(id) ?: return null
            if (item.getString("advertisement").length > MAX_AD_CHARS ||
                item.getString("destinationKey").length > MAX_KEY_CHARS) return null
            result[id] = item
        }
        result
    } catch (_: Exception) { null }

    private fun write(entries: Map<String, JSONObject>): Boolean {
        if (entries.size > MAX_ROUTES) return false
        val json = JSONObject()
        entries.forEach { (id, item) -> json.put(id, item) }
        val clear = json.toString().toByteArray(Charsets.UTF_8)
        if (clear.size > MAX_STORAGE_BYTES) return false
        return try {
            val encrypted = LocalDataCipher.encrypt(app, clear)
            prefs.edit().putString(KEY, encrypted).commit()
        } catch (_: Exception) { false }
    }

    private companion object {
        const val KEY = "routes_encrypted_v1"
        const val MAX_ROUTES = 256
        const val MAX_STORAGE_BYTES = 512 * 1024
        const val MAX_AD_CHARS = 4096
        const val MAX_KEY_CHARS = 1024
    }
}
