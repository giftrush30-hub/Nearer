package com.nearer.app

import org.json.JSONObject

internal data class LudoRoomPacket(val type: String, val roomId: String, val body: String = "")

internal object LudoRoomProtocol {
    private val types = setOf("invite", "accept", "chat", "leave", "state", "voice_note")
    fun encode(type: String, roomId: String, body: String = ""): String? {
        if (type !in types || roomId.isBlank() || roomId.length > 64 || body.length > 300) return null
        if (type == "chat" && body.isBlank()) return null
        return JSONObject().put("type", type).put("roomId", roomId).put("body", body).toString()
    }
    fun parse(raw: String): LudoRoomPacket? {
        if (raw.isBlank() || raw.length > 4096) return null
        return try {
            val json = JSONObject(raw)
            val type = json.optString("type")
            val roomId = json.optString("roomId")
            val body = json.optString("body")
            if (type !in types || roomId.isBlank() || roomId.length > 64 || body.length > 300) null
            else if (type == "chat" && body.isBlank()) null
            else LudoRoomPacket(type, roomId, body)
        } catch (_: Exception) { null }
    }
}
