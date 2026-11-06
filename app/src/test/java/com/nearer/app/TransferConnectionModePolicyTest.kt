package com.nearer.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertThrows
import org.junit.Test

class TransferConnectionModePolicyTest {
    @Test fun multiPeerModeAlwaysKeepsClusterTopology() {
        val decision = TransferConnectionModePolicy.decide(
            TransferConnectionModePolicy.RequestedMode.MULTI_PEER,
            connectedPeerCount = 3,
            peerSupportsHighSpeed = true,
            bothPeersReady = true
        )
        assertEquals(TransferConnectionModePolicy.NearbyTopology.CLUSTER, decision.topology)
        assertEquals(TransferConnectionModePolicy.FallbackReason.NONE, decision.fallbackReason)
    }

    @Test fun highSpeedRequiresExactlyOnePeer() {
        listOf(0, 2, 3).forEach { peerCount ->
            val decision = TransferConnectionModePolicy.decide(
                TransferConnectionModePolicy.RequestedMode.HIGH_SPEED_ONE_TO_ONE,
                peerCount, true, true
            )
            assertEquals(TransferConnectionModePolicy.NearbyTopology.CLUSTER, decision.topology)
            assertEquals(TransferConnectionModePolicy.FallbackReason.PEER_COUNT_NOT_ONE, decision.fallbackReason)
            assertFalse(decision.highSpeedEnabled)
        }
    }

    @Test fun highSpeedRequiresPeerCapabilityAndMutualReadiness() {
        val unsupported = TransferConnectionModePolicy.decide(
            TransferConnectionModePolicy.RequestedMode.HIGH_SPEED_ONE_TO_ONE, 1, false, true
        )
        assertEquals(TransferConnectionModePolicy.FallbackReason.PEER_DOES_NOT_SUPPORT_MODE, unsupported.fallbackReason)

        val notReady = TransferConnectionModePolicy.decide(
            TransferConnectionModePolicy.RequestedMode.HIGH_SPEED_ONE_TO_ONE, 1, true, false
        )
        assertEquals(TransferConnectionModePolicy.FallbackReason.BOTH_PEERS_NOT_READY, notReady.fallbackReason)
        assertFalse(notReady.highSpeedEnabled)
    }

    @Test fun mutuallyReadySinglePeerCanUsePointToPoint() {
        val decision = TransferConnectionModePolicy.decide(
            TransferConnectionModePolicy.RequestedMode.HIGH_SPEED_ONE_TO_ONE, 1, true, true
        )
        assertEquals(TransferConnectionModePolicy.NearbyTopology.POINT_TO_POINT, decision.topology)
        assertTrue(decision.highSpeedEnabled)
        assertEquals(TransferConnectionModePolicy.FallbackReason.NONE, decision.fallbackReason)
    }

    @Test fun negativePeerCountIsRejected() {
        assertThrows(IllegalArgumentException::class.java) {
            TransferConnectionModePolicy.decide(
                TransferConnectionModePolicy.RequestedMode.HIGH_SPEED_ONE_TO_ONE, -1, true, true
            )
        }
    }
}
