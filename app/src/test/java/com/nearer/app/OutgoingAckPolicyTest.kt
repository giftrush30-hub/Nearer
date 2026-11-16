package com.nearer.app

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OutgoingAckPolicyTest {
    @Test fun acceptsAckOnlyFromExpectedEndpointAndStablePeer() {
        assertTrue(OutgoingAckPolicy.matchesExpectedRecipient("endpoint-1", "endpoint-1", "ABCDEF234567", "ABCDEF234567"))
        assertTrue(OutgoingAckPolicy.matchesExpectedRecipient("endpoint-1", "endpoint-1", "abcdef234567", " ABCDEF234567 "))
    }

    @Test fun rejectsAckFromRecycledEndpointOwnedByDifferentPeer() {
        assertFalse(OutgoingAckPolicy.matchesExpectedRecipient("endpoint-1", "endpoint-1", "ABCDEF234567", "BCDEFG345678"))
    }

    @Test fun rejectsAckFromDifferentEndpointEvenWhenPeerMatches() {
        assertFalse(OutgoingAckPolicy.matchesExpectedRecipient("endpoint-1", "endpoint-2", "ABCDEF234567", "ABCDEF234567"))
    }

    @Test fun rejectsMissingOrMalformedRecipientIdentity() {
        assertFalse(OutgoingAckPolicy.matchesExpectedRecipient(null, "endpoint-1", "ABCDEF234567", "ABCDEF234567"))
        assertFalse(OutgoingAckPolicy.matchesExpectedRecipient("endpoint-1", "endpoint-1", null, "ABCDEF234567"))
        assertFalse(OutgoingAckPolicy.matchesExpectedRecipient("endpoint-1", "endpoint-1", "ABCDEF234567", "not-a-peer"))
    }
}
