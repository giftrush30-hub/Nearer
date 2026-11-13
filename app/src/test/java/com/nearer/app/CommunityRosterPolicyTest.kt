package com.nearer.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CommunityRosterPolicyTest {
    @Test fun firstPinnedPeerCanEstablishRosterCoordinator() {
        assertEquals("ABCD23456789", CommunityRosterPolicy.resolveOwner("", "ABCD23456789"))
    }

    @Test fun laterRosterUpdatesMustComeFromSameCoordinator() {
        assertEquals("ABCD23456789", CommunityRosterPolicy.resolveOwner("ABCD23456789", "ABCD23456789"))
        assertNull(CommunityRosterPolicy.resolveOwner("ABCD23456789", "NPQR23456789"))
        assertNull(CommunityRosterPolicy.resolveOwner("abcd23456789", "ABCD23456789"))
    }

    @Test fun rosterMustIncludeCoordinatorAndLocalIdentity() {
        assertEquals(
            listOf("ABCD23456789", "EFGH23456789", "JKLM23456789"),
            CommunityRosterPolicy.validateRoster(
                "ABCD23456789", "ABCD23456789",
                listOf("ABCD23456789", "EFGH23456789", "JKLM23456789"),
                "EFGH23456789"
            )
        )
        assertNull(
            CommunityRosterPolicy.validateRoster(
                "ABCD23456789", "ABCD23456789",
                listOf("ABCD23456789", "JKLM23456789"),
                "EFGH23456789"
            )
        )
    }

    @Test fun rosterRejectsInvalidOrUnnormalizedPeerIds() {
        assertNull(CommunityRosterPolicy.validateRoster("ABCD23456789", "ABCD23456789", listOf("ABCD23456789", "bad-id"), "ABCD23456789"))
        assertNull(CommunityRosterPolicy.validateRoster("ABCD23456789", "ABCD23456789", listOf("ABCD23456789", "owner234"), "ABCD23456789"))
        assertNull(CommunityRosterPolicy.resolveOwner("", "bad-id"))
    }

    @Test fun rosterRejectsWrongSignerAndOversizedMembership() {
        assertNull(CommunityRosterPolicy.validateRoster("ABCD23456789", "NPQR23456789", listOf("ABCD23456789", "EFGH23456789"), "EFGH23456789"))
        assertNull(CommunityRosterPolicy.validateRoster("ABCD23456789", "ABCD23456789", List(101) { NeaRerIdentity.generate() }, "ABCD23456789"))
    }

    @Test fun onlyRosteredStableIdentitiesMaySendGroupMessages() {
        assertTrue(CommunityRosterPolicy.mayAcceptGroupMessage("JKLM23456789", listOf("ABCD23456789", "JKLM23456789")))
        assertFalse(CommunityRosterPolicy.mayAcceptGroupMessage("STUV23456789", listOf("ABCD23456789", "JKLM23456789")))
        assertFalse(CommunityRosterPolicy.mayAcceptGroupMessage("member2", listOf("ABCD23456789", "JKLM23456789")))
    }
    @Test fun rosterPacketAckRequiresExactValidIncomingRoster() {
        val roster = listOf("ABCD23456789", "EFGH23456789", "JKLM23456789")
        assertTrue(
            CommunityRosterPolicy.mayAcknowledgeRosterUpdate(
                "ABCD23456789", "ABCD23456789", roster, "EFGH23456789",
                "ABCD23456789", roster
            )
        )
        // An invalid packet cannot borrow authorization from an older stored roster.
        assertFalse(
            CommunityRosterPolicy.mayAcknowledgeRosterUpdate(
                "NPQR23456789", "NPQR23456789", roster, "EFGH23456789",
                "ABCD23456789", roster
            )
        )
        assertFalse(
            CommunityRosterPolicy.mayAcknowledgeRosterUpdate(
                "ABCD23456789", "ABCD23456789", listOf("ABCD23456789", "JKLM23456789"),
                "EFGH23456789", "ABCD23456789", roster
            )
        )
        assertFalse(
            CommunityRosterPolicy.mayAcknowledgeRosterUpdate(
                "ABCD23456789", "NPQR23456789", roster, "EFGH23456789",
                "ABCD23456789", roster
            )
        )
    }

}
