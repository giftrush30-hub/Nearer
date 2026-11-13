package com.nearer.app

/** Identity-based authorization for offline community membership and roster sync. */
internal object CommunityMemberIdentityPolicy {
    fun isValidPeerId(peerId: String): Boolean =
        peerId.isNotBlank() && peerId.length <= 64

    fun isAuthorizedMember(memberIds: Collection<String>, senderPeerId: String): Boolean =
        isValidPeerId(senderPeerId) && senderPeerId in memberIds

    /**
     * Existing groups accept roster changes only from their pinned owner. A newly
     * joined device may bind its first owner only when the sender and announced
     * owner IDs match; this is TOFU, not proof of a real-world identity.
     */
    fun canAcceptRoster(ownerId: String, senderPeerId: String, announcedOwnerId: String): Boolean {
        if (!isValidPeerId(senderPeerId)) return false
        return if (ownerId.isBlank()) {
            announcedOwnerId == senderPeerId
        } else {
            ownerId == senderPeerId && announcedOwnerId == ownerId
        }
    }
}
