package com.nearer.app

internal data class CommunityPacketDescriptor(
    val rawLength: Int,
    val type: String,
    val senderId: String,
    val authenticatedSenderId: String,
    val code: String = "",
    val senderName: String = "",
    val ownerId: String = "",
    val name: String = "",
    val messageId: String = "",
    val body: String = "",
    val memberCount: Int = 0,
    val pendingMemberCount: Int = 0,
    val moderatorCount: Int = 0,
    val memberId: String = "",
    val matchId: String = "",
    val symbol: String = "",
    val moveIndex: Int = -1
)

/**
 * Pure packet bounds and sender checks. Membership/role authorization remains
 * in CommunityAccessPolicy and the state-changing packet handler.
 */
internal object CommunityPacketPolicy {
    private val stableIdPattern = Regex("([A-F0-9]{6}|[A-F0-9]{32})")
    private val groupTypes = setOf(
        "group_created", "group_join_request", "group_join_approved", "group_leave",
        "group_members", "ownership_transfer", "group_member_removed", "group_message"
    )
    private val supportedTypes = groupTypes + setOf(
        "broadcast", "game_invite", "game_accept", "game_move", "game_chat", "game_end"
    )

    fun isValidStableId(value: String): Boolean = stableIdPattern.matches(value)

    fun isValid(packet: CommunityPacketDescriptor): Boolean {
        if (packet.rawLength !in 1..8_192 ||
            !isValidStableId(packet.authenticatedSenderId) ||
            packet.senderId != packet.authenticatedSenderId ||
            packet.type !in supportedTypes ||
            packet.senderName.length > 32
        ) return false

        if (packet.type in groupTypes && !packet.code.matches(Regex("[0-9]{6}"))) return false

        return when (packet.type) {
            "group_created" -> packet.ownerId == packet.authenticatedSenderId && packet.name.length <= 32
            "group_message" -> packet.messageId.isNotBlank() && packet.messageId.length <= 80 &&
                packet.body.isNotBlank() && packet.body.length <= 1_500
            "group_members" -> packet.memberCount in 0..100 &&
                packet.pendingMemberCount in 0..100 && packet.moderatorCount in 0..100
            "group_join_request", "group_leave" -> packet.senderName.isNotBlank()
            "group_join_approved" -> isValidStableId(packet.ownerId)
            "ownership_transfer" -> isValidStableId(packet.ownerId)
            "group_member_removed" -> isValidStableId(packet.memberId)
            "broadcast" -> packet.body.isNotBlank() && packet.body.length <= 500
            "game_invite", "game_accept", "game_end" -> packet.matchId.isNotBlank() && packet.matchId.length <= 80
            "game_move" -> packet.matchId.isNotBlank() && packet.matchId.length <= 80 &&
                packet.symbol in setOf("X", "O") && packet.moveIndex in 0..8
            "game_chat" -> packet.matchId.isNotBlank() && packet.matchId.length <= 80 &&
                packet.body.length <= 300
            else -> false
        }
    }
}
