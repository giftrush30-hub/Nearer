package com.nearer.app

import org.junit.Assert.*
import org.junit.Test

class CommunityAccessPolicyTest {
    private val owner = "owner-id"
    private val moderator = "moderator-id"
    private val member = "member-id"
    private val outsider = "outsider-id"
    private val members = mapOf(owner to "Owner", moderator to "Mod", member to "Member")
    private val moderators = setOf(moderator)

    @Test fun unknownUsersCannotManageOrPost() {
        assertFalse(CommunityAccessPolicy.canManage(owner, moderators, outsider))
        assertFalse(CommunityAccessPolicy.canPost(members, outsider))
    }

    @Test fun moderatorsCanManageButCannotPromoteOrRemoveOtherModerators() {
        assertTrue(CommunityAccessPolicy.canManage(owner, moderators, moderator))
        assertFalse(CommunityAccessPolicy.canPromote(owner, members, moderators, moderator, member))
        assertFalse(CommunityAccessPolicy.canRemove(owner, moderators, members, moderator, moderator))
        assertTrue(CommunityAccessPolicy.canRemove(owner, moderators, members, moderator, member))
    }

    @Test fun onlyOwnerCanPromoteOrTransferOwnership() {
        assertTrue(CommunityAccessPolicy.canPromote(owner, members, moderators, owner, member))
        assertFalse(CommunityAccessPolicy.canPromote(owner, members, moderators, moderator, member))
        assertTrue(CommunityAccessPolicy.canTransferOwnership(owner, members, owner, member))
        assertFalse(CommunityAccessPolicy.canTransferOwnership(owner, members, moderator, member))
        assertFalse(CommunityAccessPolicy.canTransferOwnership(owner, members, owner, outsider))
    }

    @Test fun ownerCannotRemoveThemselfOrTransferToUnknownMember() {
        assertFalse(CommunityAccessPolicy.canRemove(owner, moderators, members, owner, owner))
        assertFalse(CommunityAccessPolicy.canTransferOwnership(owner, members, owner, owner))
        assertFalse(CommunityAccessPolicy.canRemove(owner, moderators, members, owner, outsider))
    }

    @Test fun groupMessagesRequireMatchingMemberIdentityAndValidContent() {
        assertTrue(CommunityAccessPolicy.isValidGroupMessage(members, member, "Member", "msg-1", "Hello"))
        assertFalse(CommunityAccessPolicy.isValidGroupMessage(members, member, "Pretending", "msg-2", "Hello"))
        assertFalse(CommunityAccessPolicy.isValidGroupMessage(members, outsider, "Outsider", "msg-3", "Hello"))
        assertFalse(CommunityAccessPolicy.isValidGroupMessage(members, member, "Member", "", "Hello"))
        assertFalse(CommunityAccessPolicy.isValidGroupMessage(members, member, "Member", "msg-4", ""))
        assertFalse(CommunityAccessPolicy.isValidGroupMessage(members, member, "Member", "msg-5", "x".repeat(1501)))
    }
}
