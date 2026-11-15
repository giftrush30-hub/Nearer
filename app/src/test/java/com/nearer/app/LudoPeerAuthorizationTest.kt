package com.nearer.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LudoPeerAuthorizationTest {
    @Test fun stableIdentityWinsOverDisplayName() {
        assertTrue(LudoPeerAuthorization.matches("peer-a", "peer-a", "Alex", "Alex", false))
        assertFalse(LudoPeerAuthorization.matches("peer-a", "peer-b", "Alex", "Alex", true))
    }

    @Test fun knownIdentityNeverFallsBackToMatchingName() {
        assertFalse(LudoPeerAuthorization.matches("peer-a", "", "Alex", "Alex", true))
    }

    @Test fun legacyNameFallbackRequiresBothIdsToBeAbsent() {
        assertTrue(LudoPeerAuthorization.matches("", "", "Alex", "Alex", true))
        assertFalse(LudoPeerAuthorization.matches("", "peer-a", "Alex", "Alex", true))
        assertFalse(LudoPeerAuthorization.matches("", "", "Alex", "Alex", false))
    }

    @Test fun sameDisplayNamePeersResolveByStableId() {
        val names = mapOf(LudoColor.RED to "Alex", LudoColor.BLUE to "Alex")
        val ids = mapOf(LudoColor.RED to "peer-a", LudoColor.BLUE to "peer-b")
        assertEquals(LudoColor.BLUE, LudoPeerAuthorization.findRemoteColor(ids, names, "peer-b", "Alex", true))
        assertEquals(null, LudoPeerAuthorization.findRemoteColor(emptyMap(), names, "", "Alex", true))
    }

    @Test fun legacyNameLookupRejectsAmbiguousDuplicateNames() {
        val names = mapOf(LudoColor.RED to "Alex", LudoColor.BLUE to "Alex")
        assertEquals(null, LudoPeerAuthorization.findRemoteColor(emptyMap(), names, "", "Alex", true))
    }
}
