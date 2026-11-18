package com.nearer.app

import org.junit.Assert.*
import org.junit.Test

class HyperTransferApprovalLedgerTest {
    private fun offer(id: String = "a".repeat(64), name: String = "video.mp4", size: Long = 42) =
        HyperTransferTcpOfferCodec.Offer(id, name, size, "b".repeat(64))

    @Test fun incomingApprovalIsExactAndCanOnlyBeConsumedOnce() {
        val ledger = HyperTransferApprovalLedger()
        val item = offer()
        assertEquals(HyperTransferApprovalLedger.OfferResult.ADDED, ledger.recordIncomingOffer("ep1", "peer-one", item))
        assertTrue(ledger.decideIncoming("ep1", "peer-one", item.transferId, true))
        assertFalse(ledger.consumeApproval("ep1", "peer-one", item.copy(name = "other.mp4"), HyperTransferApprovalLedger.Direction.INCOMING))
        assertTrue(ledger.consumeApproval("ep1", "peer-one", item, HyperTransferApprovalLedger.Direction.INCOMING))
        assertFalse(ledger.consumeApproval("ep1", "peer-one", item, HyperTransferApprovalLedger.Direction.INCOMING))
    }

    @Test fun approvalCannotCrossEndpointOrPeerIdentity() {
        val ledger = HyperTransferApprovalLedger()
        val item = offer()
        ledger.recordIncomingOffer("ep1", "peer-one", item)
        ledger.decideIncoming("ep1", "peer-one", item.transferId, true)
        assertFalse(ledger.consumeApproval("ep2", "peer-one", item, HyperTransferApprovalLedger.Direction.INCOMING))
        assertFalse(ledger.consumeApproval("ep1", "peer-two", item, HyperTransferApprovalLedger.Direction.INCOMING))
    }

    @Test fun senderRequiresMatchingRemoteApproval() {
        val ledger = HyperTransferApprovalLedger()
        val item = offer()
        ledger.recordOutgoingOffer("ep1", "peer-one", item)
        assertFalse(ledger.consumeApproval("ep1", "peer-one", item, HyperTransferApprovalLedger.Direction.OUTGOING))
        assertFalse(ledger.recordRemoteDecision("ep1", "peer-one", HyperTransferTcpOfferCodec.Decision("c".repeat(64), true)))
        assertTrue(ledger.recordRemoteDecision("ep1", "peer-one", HyperTransferTcpOfferCodec.Decision(item.transferId, true)))
        assertTrue(ledger.consumeApproval("ep1", "peer-one", item, HyperTransferApprovalLedger.Direction.OUTGOING))
    }

    @Test fun declineAndConflictingDuplicateNeverAuthorize() {
        val ledger = HyperTransferApprovalLedger()
        val item = offer()
        ledger.recordIncomingOffer("ep1", "peer-one", item)
        assertEquals(HyperTransferApprovalLedger.OfferResult.CONFLICT,
            ledger.recordIncomingOffer("ep1", "peer-one", item.copy(name = "changed.mp4")))
        assertTrue(ledger.decideIncoming("ep1", "peer-one", item.transferId, false))
        assertFalse(ledger.consumeApproval("ep1", "peer-one", item, HyperTransferApprovalLedger.Direction.INCOMING))
    }

    @Test fun entriesExpireAndEndpointCleanupRemovesPendingAuthorization() {
        var now = 1000L
        val ledger = HyperTransferApprovalLedger(nowMillis = { now }, lifetimeMillis = 100, maximumEntries = 2)
        val first = offer()
        ledger.recordIncomingOffer("ep1", "peer-one", first)
        ledger.decideIncoming("ep1", "peer-one", first.transferId, true)
        ledger.clearEndpoint("ep1")
        assertFalse(ledger.consumeApproval("ep1", "peer-one", first, HyperTransferApprovalLedger.Direction.INCOMING))
        val second = offer("c".repeat(64))
        ledger.recordIncomingOffer("ep2", "peer-two", second)
        ledger.decideIncoming("ep2", "peer-two", second.transferId, true)
        now += 101
        assertFalse(ledger.consumeApproval("ep2", "peer-two", second, HyperTransferApprovalLedger.Direction.INCOMING))
    }
}
