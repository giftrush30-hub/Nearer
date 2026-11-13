package com.nearer.app

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CommunityMemberIdentityPolicyTest {
    @Test fun acceptsOnlyTheStableIdentityStoredInTheRoster() {
        assertTrue(CommunityMemberIdentityPolicy.isAuthorizedMember(listOf("ABCD23456789"), "ABCD23456789"))
        assertFalse(CommunityMemberIdentityPolicy.isAuthorizedMember(listOf("ABCD23456789"), "EFGH23456789"))
        assertFalse(CommunityMemberIdentityPolicy.isAuthorizedMember(listOf("Alice"), "ABCD23456789"))
    }

    @Test fun rejectsBlankAndOversizedPeerIds() {
        assertFalse(CommunityMemberIdentityPolicy.isValidPeerId(""))
        assertFalse(CommunityMemberIdentityPolicy.isValidPeerId("x".repeat(65)))
    }

    @Test fun onlyThePinnedLocalOwnerAcknowledgesAJoinRequest() {
        assertTrue(CommunityMemberIdentityPolicy.canAcceptJoin("OWNER123", "OWNER123", "MEMBER12"))
        assertFalse(CommunityMemberIdentityPolicy.canAcceptJoin("OWNER123", "OTHER123", "MEMBER12"))
        assertFalse(CommunityMemberIdentityPolicy.canAcceptJoin("OWNER123", "OWNER123", "OWNER123"))
        assertFalse(CommunityMemberIdentityPolicy.canAcceptJoin("", "", "MEMBER12"))
    }

    @Test fun firstRosterSyncCanBindOnlyItsAnnouncedOwnerIdentity() {
        assertTrue(CommunityMemberIdentityPolicy.canAcceptRoster("", "ABCD23456789", "ABCD23456789"))
        assertFalse(CommunityMemberIdentityPolicy.canAcceptRoster("", "EFGH23456789", "ABCD23456789"))
        assertFalse(CommunityMemberIdentityPolicy.canAcceptRoster("", "", ""))
    }

    @Test fun laterRosterSyncMustComeFromThePinnedOwner() {
        assertTrue(CommunityMemberIdentityPolicy.canAcceptRoster("ABCD23456789", "ABCD23456789", "ABCD23456789"))
        assertFalse(CommunityMemberIdentityPolicy.canAcceptRoster("ABCD23456789", "EFGH23456789", "ABCD23456789"))
        assertFalse(CommunityMemberIdentityPolicy.canAcceptRoster("ABCD23456789", "ABCD23456789", "EFGH23456789"))
    }
}
