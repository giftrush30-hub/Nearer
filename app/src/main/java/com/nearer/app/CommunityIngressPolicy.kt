package com.nearer.app

import org.json.JSONObject

/**
 * Structural and transport-identity gate for the packet vocabulary actually
 * emitted by MainActivity. Authorization against group rosters and Ludo room
 * state remains in the stateful handler.
 */
internal object CommunityIngressPolicy {
    private val groupTypes = setOf(
        "group_created", "group_join", "group_members", "group_message", "group_read_receipt"
    )
    private val ludoTypes = setOf(
        "ludo_invite", "ludo_accept", "ludo_reject", "ludo_action",
        "ludo_chat", "ludo_end", "ludo_rematch", "ludo_state", "ludo_sync"
    )
    private val supportedTypes = groupTypes + ludoTypes + "broadcast"

    fun isValid(
        raw: String,
        authenticatedSenderId: String,
        allowLegacySocketLudo: Boolean = false
    ): Boolean {
        if (raw.isBlank() || raw.length > MAX_PACKET_LENGTH) return false
        val packet = try { JSONObject(raw) } catch (_: Exception) { return false }
        val type = packet.optString("type")
        if (type !in supportedTypes) return false

        val stableId = NeaRerIdentity.normalize(authenticatedSenderId)
        val legacyLudo = allowLegacySocketLudo && type in ludoTypes && authenticatedSenderId.isBlank()
        if (stableId == null && !legacyLudo) return false

        val sender = packet.optString("sender")
        if (sender.length > MAX_SENDER_NAME_LENGTH) return false

        return when (type) {
            "group_created" -> packet.optString("code").matches(GROUP_CODE) &&
                packet.optString("name").isNotBlank() &&
                packet.optString("name").length <= 32 &&
                packet.optString("ownerPeerId") == stableId
            "group_join" -> packet.optString("code").matches(GROUP_CODE) &&
                sender.isNotBlank()
            "group_members" -> {
                val ids = packet.optJSONArray("memberPeerIds")
                val names = packet.optJSONArray("members")
                val owner = NeaRerIdentity.normalize(packet.optString("ownerPeerId"))
                val memberIds = ids?.toListOfStrings().orEmpty()
                val memberNames = names?.toListOfStrings().orEmpty()
                ids != null && names != null && owner == stableId &&
                    memberIds.size in 1..MAX_GROUP_MEMBERS &&
                    memberIds.distinct().size == memberIds.size &&
                    memberIds.all { NeaRerIdentity.normalize(it) == it && it.isNotBlank() } &&
                    memberNames.size in 1..MAX_GROUP_MEMBERS &&
                    memberNames.all { it.isNotBlank() && it.length <= MAX_SENDER_NAME_LENGTH } &&
                    packet.optString("code").matches(GROUP_CODE)
            }
            "group_read_receipt" -> {
                val ids = packet.optJSONArray("messageIds")
                val messageIds = ids?.let { array -> (0 until array.length()).map { array.optString(it) } }.orEmpty()
                packet.optString("code").matches(GROUP_CODE) &&
                    packet.optString("readerPeerId") == stableId &&
                    CommunityReadReceiptPolicy.isValid(
                        messageIds, packet.optLong("readAt", 0L), System.currentTimeMillis()
                    )
            }
            "group_message" -> packet.optString("code").matches(GROUP_CODE) &&
                packet.optString("id").isNotBlank() && packet.optString("id").length <= 80 &&
                packet.optString("body").isNotBlank() && packet.optString("body").length <= 1_500
            "broadcast" -> packet.optString("body").isNotBlank() &&
                packet.optString("body").length <= 500
            "ludo_invite", "ludo_accept", "ludo_reject", "ludo_end", "ludo_rematch", "ludo_sync" ->
                validMatchId(packet)
            "ludo_action" -> validMatchId(packet) &&
                packet.optString("action") in setOf("roll", "move", "pass") &&
                packet.optInt("token", -1) in -1..3 &&
                packet.optLong("revision", -1L) >= 0L
            "ludo_chat" -> validMatchId(packet) &&
                packet.optString("body").isNotBlank() && packet.optString("body").length <= 300
            "ludo_state" -> validMatchId(packet) &&
                packet.optLong("revision", -1L) >= 0L &&
                packet.optJSONObject("state") != null &&
                packet.optJSONObject("state").toString().length <= MAX_STATE_LENGTH
            else -> false
        }
    }

    private fun validMatchId(packet: JSONObject): Boolean =
        packet.optString("matchId").isNotBlank() && packet.optString("matchId").length <= 80

    private fun org.json.JSONArray.toListOfStrings(): List<String> =
        (0 until length()).map { optString(it) }

    private const val MAX_PACKET_LENGTH = 24 * 1024
    private const val MAX_STATE_LENGTH = 16 * 1024
    private const val MAX_SENDER_NAME_LENGTH = 32
    private const val MAX_GROUP_MEMBERS = 100
    private val GROUP_CODE = Regex("[0-9]{6}")
}
