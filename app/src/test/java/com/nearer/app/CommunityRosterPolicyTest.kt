package com.nearer.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CommunityRosterPolicyTest {
    @Test fun firstPinnedPeerCanEstablishRosterCoordinator() {
        assertEquals("ABCD234", CommunityRosterPolicy.resolveOwner("", "ABCD234"))
    }

    @Test fun laterRosterUpdatesMustComeFromSameCoordinator() {
        assertEquals("ABCD234", CommunityRosterPolicy.resolveOwner("ABCD234", "ABCD234"))
        assertNull(CommunityRosterPolicy.resolveOwner("ABCD234", "NPQR234"))
    }

    @Test fun rosterMustIncludeCoordinatorAndLocalIdentity() {
        assertEquals(
            listOf("ABCD234", "EFGH234", "JKLM234"),
            CommunityRosterPolicy.validateRoster(
                "ABCD234", "ABCD234",
                listOf("ABCD234", "EFGH234", "JKLM234"),
                "EFGH234"
            )
        )
        assertNull(
            CommunityRosterPolicy.validateRoster(
                "ABCD234", "ABCD234",
                listOf("ABCD234", "JKLM234"),
                "EFGH234"
            )
        )
    }

    @Test fun rosterRejectsInvalidOrUnnormalizedPeerIds() {
        assertNull(CommunityRosterPolicy.validateRoster("ABCD234", "ABCD234", listOf("ABCD234", "bad-id"), "ABCD234"))
        assertNull(CommunityRosterPolicy.validateRoster("ABCD234", "ABCD234", listOf("ABCD234", "owner234"), "ABCD234"))
        assertNull(CommunityRosterPolicy.resolveOwner("", "bad-id"))
    }

    @Test fun rosterRejectsWrongSignerAndOversizedMembership() {
        assertNull(CommunityRosterPolicy.validateRoster("ABCD234", "NPQR234", listOf("ABCD234", "EFGH234"), "EFGH234"))
        assertNull(CommunityRosterPolicy.validateRoster("ABCD234", "ABCD234", (0..100).map { "PEER${it}234" }, "ABCD234"))
    }

    @Test fun onlyRosteredStableIdentitiesMaySendGroupMessages() {
        assertTrue(CommunityRosterPolicy.mayAcceptGroupMessage("JKLM234", listOf("ABCD234", "JKLM234")))
        assertFalse(CommunityRosterPolicy.mayAcceptGroupMessage("STUV234", listOf("ABCD234", "JKLM234")))
        assertFalse(CommunityRosterPolicy.mayAcceptGroupMessage("member2", listOf("ABCD234", "JKLM234")))
    }
}
