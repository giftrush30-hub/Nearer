package com.nearer.app

import org.json.JSONArray
import org.json.JSONObject

/**
 * Bounds and authenticates the outer shape of community packets before they
 * reach state-changing handlers. Membership/role authorization remains in
 * CommunityAccessPolicy and the handler.
 */
object CommunityPacketPolicy {
    private val stableIdPattern = Regex("([A-F0-9]{6}|[A-F0-9]{32})")
    private val groupTypes = setOf(
        "group_created", "group_join_request", "group_join_approved", "group_leave",
        "group_members", "ownership_transfer", "group_member_removed", "group_message"
    )
    private val supportedTypes = groupTypes + setOf(
        "broadcast", "game_invite", "game_accept", "game_move", "game_chat", "game_end"
    )

    fun isValid(raw: String, authenticatedSenderId: String): Boolean {
        if (raw.isBlank() || raw.length > 8_192 || !stableIdPattern.matches(authenticatedSenderId)) return false
        val packet = try { JSONObject(raw) } catch (_: Exception) { return false }
        val type = packet.optString("type")
        if (type !in supportedTypes) return false

        val senderId = packet.optString("senderId").ifBlank { authenticatedSenderId }
        if (senderId != authenticatedSenderId) return false

        val senderName = packet.optString("sender")
        if (senderName.length > 32) return false
        if (type in groupTypes && !packet.optString("code").matches(Regex("[0-9]{6}"))) return false

        return when (type) {
            "group_created" -> packet.optString("ownerId") == authenticatedSenderId &&
                packet.optString("name").length <= 32
            "group_message" -> packet.optString("id").isNotBlank() &&
                packet.optString("id").length <= 80 &&
                packet.optString("body").isNotBlank() &&
                packet.optString("body").length <= 1_500
            "group_members" -> {
                val members = packet.optJSONArray("members") ?: JSONArray()
                val pending = packet.optJSONArray("pendingMembers") ?: JSONArray()
                val moderators = packet.optJSONArray("moderators") ?: JSONArray()
                members.length() <= 100 && pending.length() <= 100 && moderators.length() <= 100
            }
            "group_join_request", "group_leave" -> senderName.isNotBlank()
            "group_join_approved" -> packet.optString("ownerId").isNotBlank()
            "ownership_transfer" -> packet.optString("ownerId").matches(stableIdPattern)
            "group_member_removed" -> packet.optString("memberId").matches(stableIdPattern)
            "broadcast" -> packet.optString("body").isNotBlank() && packet.optString("body").length <= 500
            "game_invite", "game_accept", "game_end" -> packet.optString("matchId").isNotBlank() &&
                packet.optString("matchId").length <= 80
            "game_move" -> packet.optString("matchId").isNotBlank() &&
                packet.optString("matchId").length <= 80 &&
                packet.optString("symbol") in setOf("X", "O") &&
                packet.optInt("index", -1) in 0..8
            "game_chat" -> packet.optString("matchId").isNotBlank() &&
                packet.optString("matchId").length <= 80 &&
                packet.optString("body").length <= 300
            else -> false
        }
    }
}
