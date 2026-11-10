package com.nearer.app

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * Durable, encrypted queue for the Wi-Fi Direct socket chat fallback.
 * Entries are bound to the authenticated NeaRer ID from the pairing handshake.
 * Message-ID deduplication on the receiver makes reconnect retries idempotent.
 */
internal class DirectChatOutbox(context: Context) {
    data class Entry(val id: String, val peerId: String, val body: String)

    private val appContext = context.applicationContext
    private val preferences = appContext.getSharedPreferences("nearer_local", Context.MODE_PRIVATE)
    private val lock = Any()
    private val entries = LinkedHashMap<String, Entry>()
    private var restoreFailed = false

    init { restore() }

    fun enqueue(id: String, peerId: String, body: String): Boolean = synchronized(lock) {
        if (restoreFailed || !validId(id) || NeaRerIdentity.normalize(peerId) == null || body.isBlank() || body.length > 4000) return false
        if (entries.containsKey(id)) return entries[id] == Entry(id, peerId, body)
        if (entries.size >= 500) return false
        entries[id] = Entry(id, peerId, body)
        if (persistLocked()) true else {
            entries.remove(id)
            false
        }
    }

    fun find(id: String): Entry? = synchronized(lock) { entries[id] }

    fun forPeer(peerId: String): List<Entry> = synchronized(lock) {
        entries.values.filter { it.peerId == peerId }
    }

    fun remove(id: String): Boolean = synchronized(lock) {
        val removed = entries.remove(id) ?: return true
        if (persistLocked()) true else {
            entries[id] = removed
            false
        }
    }

    private fun restore() = synchronized(lock) {
        val stored = preferences.getString(KEY, null) ?: return
        try {
            val plaintext = LocalDataCipher.decrypt(appContext, stored)
                ?: throw IllegalStateException("The saved direct-chat queue could not be unlocked.")
            val array = JSONArray(plaintext.toString(Charsets.UTF_8))
            for (i in 0 until array.length()) {
                val item = array.optJSONObject(i) ?: continue
                val id = item.optString("id")
                val peer = item.optString("peerId")
                val body = item.optString("body")
                if (validId(id) && NeaRerIdentity.normalize(peer) != null && body.isNotBlank() && body.length <= 4000 && entries.size < 500) {
                    entries[id] = Entry(id, peer, body)
                }
            }
        } catch (_: Exception) {
            // Preserve unreadable ciphertext; never overwrite it with an empty queue.
            restoreFailed = true
        }
    }

    private fun persistLocked(): Boolean = try {
        val array = JSONArray()
        entries.values.forEach { entry ->
            array.put(JSONObject().put("id", entry.id).put("peerId", entry.peerId).put("body", entry.body))
        }
        val encrypted = LocalDataCipher.encrypt(appContext, array.toString().toByteArray(Charsets.UTF_8))
        preferences.edit().putString(KEY, encrypted).commit()
    } catch (_: Exception) { false }

    private fun validId(id: String): Boolean =
        id.isNotBlank() && id.length <= 80 && id.matches(Regex("[A-Za-z0-9._:-]+"))

    private companion object { const val KEY = "wifi_direct_chat_outbox_v1" }
}
