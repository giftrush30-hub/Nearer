package com.nearer.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CommunityRosterPolicyTest {
    @Test fun firstPinnedPeerCanEstablishRosterCoordinator() {
        assertEquals("OWNER234", CommunityRosterPolicy.resolveOwner("", "OWNER234"))
    }

    @Test fun laterRosterUpdatesMustComeFromSameCoordinator() {
        assertEquals("OWNER234", CommunityRosterPolicy.resolveOwner("OWNER234", "OWNER234"))
        assertNull(CommunityRosterPolicy.resolveOwner("OWNER234", "OTHER234"))
    }

    @Test fun rosterMustIncludeCoordinatorAndLocalIdentity() {
        assertEquals(
            listOf("OWNER234", "LOCAL234", "MEMBER2"),
            CommunityRosterPolicy.validateRoster(
                "OWNER234", "OWNER234",
                listOf("OWNER234", "LOCAL234", "MEMBER2"),
                "LOCAL234"
            )
        )
        assertNull(
            CommunityRosterPolicy.validateRoster(
                "OWNER234", "OWNER234",
                listOf("OWNER234", "MEMBER2"),
                "LOCAL234"
            )
        )
    }

    @Test fun rosterRejectsInvalidOrUnnormalizedPeerIds() {
        assertNull(CommunityRosterPolicy.validateRoster("OWNER234", "OWNER234", listOf("OWNER234", "bad-id"), "OWNER234"))
        assertNull(CommunityRosterPolicy.validateRoster("OWNER234", "OWNER234", listOf("OWNER234", "owner234"), "OWNER234"))
        assertNull(CommunityRosterPolicy.resolveOwner("", "bad-id"))
    }

    @Test fun rosterRejectsWrongSignerAndOversizedMembership() {
        assertNull(CommunityRosterPolicy.validateRoster("OWNER234", "OTHER234", listOf("OWNER234", "LOCAL234"), "LOCAL234"))
        assertNull(CommunityRosterPolicy.validateRoster("OWNER234", "OWNER234", (0..100).map { "PEER${it}234" }, "OWNER234"))
    }

    @Test fun onlyRosteredStableIdentitiesMaySendGroupMessages() {
        assertTrue(CommunityRosterPolicy.mayAcceptGroupMessage("MEMBER2", listOf("OWNER234", "MEMBER2")))
        assertFalse(CommunityRosterPolicy.mayAcceptGroupMessage("MEMBER3", listOf("OWNER234", "MEMBER2")))
        assertFalse(CommunityRosterPolicy.mayAcceptGroupMessage("member2", listOf("OWNER234", "MEMBER2")))
    }
}
