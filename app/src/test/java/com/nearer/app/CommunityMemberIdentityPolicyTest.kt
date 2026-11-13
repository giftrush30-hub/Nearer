package com.nearer.app

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CommunityMemberIdentityPolicyTest {
    @Test fun acceptsOnlyTheStableIdentityStoredInTheRoster() {
        assertTrue(CommunityMemberIdentityPolicy.isAuthorizedMember(listOf("PEER1234"), "PEER1234"))
        assertFalse(CommunityMemberIdentityPolicy.isAuthorizedMember(listOf("PEER1234"), "OTHER567"))
        assertFalse(CommunityMemberIdentityPolicy.isAuthorizedMember(listOf("Alice"), "PEER1234"))
    }

    @Test fun rejectsBlankAndOversizedPeerIds() {
        assertFalse(CommunityMemberIdentityPolicy.isValidPeerId(""))
        assertFalse(CommunityMemberIdentityPolicy.isValidPeerId("x".repeat(65)))
    }

    @Test fun firstRosterSyncCanBindOnlyItsAnnouncedOwnerIdentity() {
        assertTrue(CommunityMemberIdentityPolicy.canAcceptRoster("", "OWNER123", "OWNER123"))
        assertFalse(CommunityMemberIdentityPolicy.canAcceptRoster("", "OTHER123", "OWNER123"))
        assertFalse(CommunityMemberIdentityPolicy.canAcceptRoster("", "", ""))
    }

    @Test fun laterRosterSyncMustComeFromThePinnedOwner() {
        assertTrue(CommunityMemberIdentityPolicy.canAcceptRoster("OWNER123", "OWNER123", "OWNER123"))
        assertFalse(CommunityMemberIdentityPolicy.canAcceptRoster("OWNER123", "OTHER123", "OWNER123"))
        assertFalse(CommunityMemberIdentityPolicy.canAcceptRoster("OWNER123", "OWNER123", "OTHER123"))
    }
}
