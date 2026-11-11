package com.nearer.app

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MeshRelayParticipationPolicyTest {
    @Test fun disabledRelayStillAllowsDirectRecipientDeliveryAndOwnOutgoingMessages() {
        assertTrue(MeshRelayParticipationPolicy.mayAcceptPacket(false, "LOCAL", "LOCAL"))
        assertTrue(MeshRelayParticipationPolicy.mayForwardPacket(false, "LOCAL", "LOCAL"))
    }

    @Test fun disabledRelayRejectsThirdPartyPacketsAndReceiptsWithoutTransit() {
        assertFalse(MeshRelayParticipationPolicy.mayAcceptPacket(false, "OTHER", "LOCAL"))
        assertFalse(MeshRelayParticipationPolicy.mayForwardPacket(false, "OTHER", "LOCAL"))
        assertFalse(MeshRelayParticipationPolicy.mayAcceptReceipt(false, "OTHER", "LOCAL"))
        assertFalse(MeshRelayParticipationPolicy.mayForwardReceipt(false, "OTHER", "LOCAL"))
        assertFalse(MeshRelayParticipationPolicy.mayAdvertiseRoutes(false))
    }

    @Test fun explicitRelayParticipationEnablesTransitActions() {
        assertTrue(MeshRelayParticipationPolicy.mayAcceptPacket(true, "OTHER", "LOCAL"))
        assertTrue(MeshRelayParticipationPolicy.mayForwardPacket(true, "OTHER", "LOCAL"))
        assertTrue(MeshRelayParticipationPolicy.mayAcceptReceipt(true, "OTHER", "LOCAL"))
        assertTrue(MeshRelayParticipationPolicy.mayForwardReceipt(true, "OTHER", "LOCAL"))
        assertTrue(MeshRelayParticipationPolicy.mayAdvertiseRoutes(true))
    }
}
