package com.nearer.app

import org.json.JSONObject

internal data class LudoRoomPacket(val type: String, val roomId: String, val body: String = "", val sequence: Long = 0L)

internal object LudoRoomProtocol {
    private val types = setOf("invite", "accept", "decline", "chat", "leave", "state", "intent", "voice_note")
    fun encode(type: String, roomId: String, body: String = "", sequence: Long = 0L): String? {
        val bodyLimit = when (type) { "state" -> 12_000; "intent" -> 100; else -> 300 }
        if (type !in types || roomId.isBlank() || roomId.length > 64 ||
            body.length > bodyLimit || sequence < 0L) return null
        if (type == "chat" && body.isBlank()) return null
        val encoded = JSONObject().put("type", type).put("roomId", roomId)
            .put("body", body).put("sequence", sequence).toString()
        return encoded.takeIf { it.length <= 16_384 }
    }
    fun parse(raw: String): LudoRoomPacket? {
        if (raw.isBlank() || raw.length > 16_384) return null
        return try {
            val json = JSONObject(raw)
            val type = json.optString("type")
            val roomId = json.optString("roomId")
            val body = json.optString("body")
            val sequence = json.optLong("sequence", 0L)
            val bodyLimit = when (type) { "state" -> 12_000; "intent" -> 100; else -> 300 }
            if (type !in types || roomId.isBlank() || roomId.length > 64 ||
                body.length > bodyLimit || sequence < 0L) null
            else if (type == "chat" && body.isBlank()) null
            else LudoRoomPacket(type, roomId, body, sequence)
        } catch (_: Exception) { null }
    }
}
