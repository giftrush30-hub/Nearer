package com.nearer.app

/**
 * Pure community authorization rules. These checks are also applied to incoming
 * packets; UI visibility is never treated as authorization.
 */
object CommunityAccessPolicy {
    fun canManage(ownerId: String, moderators: Set<String>, actorId: String): Boolean =
        actorId.isNotBlank() && (actorId == ownerId || actorId in moderators)

    fun canPost(memberIds: Map<String, String>, actorId: String): Boolean =
        actorId.isNotBlank() && actorId in memberIds

    fun canPromote(ownerId: String, memberIds: Map<String, String>, moderators: Set<String>, actorId: String, targetId: String): Boolean =
        actorId.isNotBlank() && actorId == ownerId && targetId.isNotBlank() &&
            targetId != ownerId && targetId in memberIds && targetId !in moderators

    fun canRemove(ownerId: String, moderators: Set<String>, memberIds: Map<String, String>, actorId: String, targetId: String): Boolean =
        canManage(ownerId, moderators, actorId) && targetId.isNotBlank() &&
            targetId != ownerId && targetId in memberIds &&
            (actorId == ownerId || targetId !in moderators)

    fun canTransferOwnership(ownerId: String, memberIds: Map<String, String>, actorId: String, targetId: String): Boolean =
        actorId.isNotBlank() && actorId == ownerId && targetId.isNotBlank() &&
            targetId != ownerId && targetId in memberIds

    fun isKnownMember(memberIds: Map<String, String>, senderId: String, senderName: String): Boolean =
        senderId.isNotBlank() && senderName.isNotBlank() && memberIds[senderId] == senderName

    fun isValidGroupMessage(memberIds: Map<String, String>, senderId: String, senderName: String, messageId: String, body: String): Boolean =
        isKnownMember(memberIds, senderId, senderName) &&
            messageId.isNotBlank() && messageId.length <= 80 && body.isNotBlank() && body.length <= 1500
}
