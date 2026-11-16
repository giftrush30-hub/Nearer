package com.nearer.app

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * Encrypted durable inbox for Nearby messages received while no Activity is bound.
 * A sender is not acknowledged merely because an event entered this queue; the
 * chat/community layer must confirm that it safely committed the event first.
 */
internal class NearbyIncomingMessageInbox(context: Context) {
    data class Entry(
        val messageId: String,
        val peerId: String,
        val senderName: String,
        val body: String,
        val replyToMessageId: String? = null,
        val replyToPreview: String? = null,
        val replyToSender: String? = null,
        val mesh: Boolean = false
    )

    private val appContext = context.applicationContext
    private val preferences = appContext.getSharedPreferences("nearer_local", Context.MODE_PRIVATE)
    private val lock = Any()
    private val entries = LinkedHashMap<String, Entry>()
    private var restoreFailed = false

    init { restore() }

    fun put(entry: Entry): Boolean = synchronized(lock) {
        if (restoreFailed || !isValid(entry)) return false
        val existing = entries[entry.messageId]
        if (existing != null) return existing == entry
        if (entries.size >= MAX_ENTRIES) return false
        entries[entry.messageId] = entry
        if (persistLocked()) true else {
            entries.remove(entry.messageId)
            false
        }
    }

    fun snapshot(): List<Entry> = synchronized(lock) { entries.values.toList() }

    fun remove(messageId: String): Boolean = synchronized(lock) {
        val removed = entries.remove(messageId) ?: return true
        if (persistLocked()) true else {
            entries[messageId] = removed
            false
        }
    }

    private fun isValid(entry: Entry): Boolean {
        if (entry.messageId.isBlank() || entry.messageId.length > 80) return false
        if (NeaRerIdentity.normalize(entry.peerId) == null) return false
        if (entry.senderName.isBlank() || entry.senderName.length > 64) return false
        if (entry.body.isBlank() || entry.body.length > 4_000) return false
        val hasReply = entry.replyToMessageId != null || entry.replyToPreview != null || entry.replyToSender != null
        return !hasReply || ChatReplyPolicy.isValidIncomingQuote(
            entry.replyToMessageId, entry.replyToPreview, entry.replyToSender
        )
    }

    private fun restore() = synchronized(lock) {
        val stored = preferences.getString(KEY, null) ?: return
        try {
            val plaintext = LocalDataCipher.decrypt(appContext, stored)
                ?: throw IllegalStateException("The saved Nearby inbox could not be unlocked.")
            val array = JSONArray(plaintext.toString(Charsets.UTF_8))
            for (index in 0 until array.length()) {
                val item = array.optJSONObject(index) ?: continue
                val entry = Entry(
                    messageId = item.optString("messageId"),
                    peerId = item.optString("peerId"),
                    senderName = item.optString("senderName"),
                    body = item.optString("body"),
                    replyToMessageId = item.optString("replyToMessageId").takeIf { it.isNotBlank() },
                    replyToPreview = item.optString("replyToPreview").takeIf { it.isNotBlank() },
                    replyToSender = item.optString("replyToSender").takeIf { it.isNotBlank() },
                    mesh = item.optBoolean("mesh", false)
                )
                if (isValid(entry) && entries.size < MAX_ENTRIES) entries.putIfAbsent(entry.messageId, entry)
            }
        } catch (_: Exception) {
            // Keep unreadable ciphertext intact; never replace it with an empty inbox.
            restoreFailed = true
        }
    }

    private fun persistLocked(): Boolean = try {
        val array = JSONArray()
        entries.values.forEach { entry ->
            array.put(JSONObject()
                .put("messageId", entry.messageId)
                .put("peerId", entry.peerId)
                .put("senderName", entry.senderName)
                .put("body", entry.body)
                .put("replyToMessageId", entry.replyToMessageId)
                .put("replyToPreview", entry.replyToPreview)
                .put("replyToSender", entry.replyToSender)
                .put("mesh", entry.mesh))
        }
        val encrypted = LocalDataCipher.encrypt(appContext, array.toString().toByteArray(Charsets.UTF_8))
        preferences.edit().putString(KEY, encrypted).commit()
    } catch (_: Exception) { false }

    private companion object {
        const val KEY = "nearby_incoming_message_inbox_v1"
        const val MAX_ENTRIES = 200
    }
}
