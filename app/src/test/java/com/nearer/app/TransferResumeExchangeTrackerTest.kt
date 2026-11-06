package com.nearer.app

import org.junit.Assert.*
import org.junit.Test

class TransferResumeExchangeTrackerTest {
    private val chunk = TransferResumeCheckpoint.CHECKPOINT_GRANULARITY_BYTES
    private val proposal = TransferResumeNegotiation.Proposal(
        "bundle-1", "item-1", "Docs/report.pdf", chunk * 3L, 123L,
        chunk, "b".repeat(64)
    )
    private val decision = TransferResumeNegotiation.Decision(
        chunk, TransferResumeNegotiation.Reason.RESUME_ACCEPTED
    )

    private fun proposalEnvelope(id: Long = 7L) =
        TransferResumeMessageCodec.Envelope(id, proposal)

    private fun decisionEnvelope(id: Long = 7L) =
        TransferResumeMessageCodec.DecisionEnvelope(id, decision)

    @Test fun waitsUntilProposalDecisionAndDataPayloadHaveAllArrived() {
        val tracker = TransferResumeExchangeTracker()
        assertEquals(
            TransferResumeExchangeTracker.Update.STORED,
            tracker.recordDataPayload("peer-a", 7L)
        )
        assertNull(tracker.takeReady("peer-a", 7L))
        tracker.recordDecision("peer-a", decisionEnvelope())
        assertNull(tracker.takeReady("peer-a", 7L))
        tracker.recordProposal("peer-a", proposalEnvelope())

        val ready = tracker.takeReady("peer-a", 7L)
        assertNotNull(ready)
        assertEquals(proposal, ready!!.proposal)
        assertEquals(decision, ready.decision)
        assertNull("A completed exchange must be consumed once", tracker.takeReady("peer-a", 7L))
        assertEquals(0, tracker.pendingCount())
    }

    @Test fun acceptsEveryCallbackOrdering() {
        val orders = listOf(
            listOf("proposal", "decision", "payload"),
            listOf("proposal", "payload", "decision"),
            listOf("decision", "proposal", "payload"),
            listOf("decision", "payload", "proposal"),
            listOf("payload", "proposal", "decision"),
            listOf("payload", "decision", "proposal")
        )
        orders.forEach { order ->
            val tracker = TransferResumeExchangeTracker()
            order.forEach { event ->
                when (event) {
                    "proposal" -> tracker.recordProposal("peer", proposalEnvelope())
                    "decision" -> tracker.recordDecision("peer", decisionEnvelope())
                    "payload" -> tracker.recordDataPayload("peer", 7L)
                }
            }
            assertNotNull("Failed callback ordering: $order", tracker.takeReady("peer", 7L))
        }
    }

    @Test fun treatsIdenticalDuplicatesAsIdempotentAndRejectsConflicts() {
        val tracker = TransferResumeExchangeTracker()
        assertEquals(
            TransferResumeExchangeTracker.Update.STORED,
            tracker.recordProposal("peer", proposalEnvelope())
        )
        assertEquals(
            TransferResumeExchangeTracker.Update.DUPLICATE,
            tracker.recordProposal("peer", proposalEnvelope())
        )
        assertEquals(
            TransferResumeExchangeTracker.Update.CONFLICT,
            tracker.recordProposal("peer", proposalEnvelope().copy(
                proposal = proposal.copy(prefixSha256 = "c".repeat(64))
            ))
        )
        tracker.recordDecision("peer", decisionEnvelope())
        assertEquals(
            TransferResumeExchangeTracker.Update.DUPLICATE,
            tracker.recordDecision("peer", decisionEnvelope())
        )
        assertEquals(
            TransferResumeExchangeTracker.Update.CONFLICT,
            tracker.recordDecision("peer", decisionEnvelope().copy(
                decision = TransferResumeNegotiation.Decision(
                    0L, TransferResumeNegotiation.Reason.SOURCE_CHANGED
                )
            ))
        )
    }

    @Test fun scopesTheSamePayloadIdToItsEndpoint() {
        val tracker = TransferResumeExchangeTracker()
        tracker.recordProposal("peer-a", proposalEnvelope())
        tracker.recordDecision("peer-a", decisionEnvelope())
        tracker.recordDataPayload("peer-b", 7L)
        assertNull(tracker.takeReady("peer-b", 7L))
        assertNotNull(tracker.takeReady("peer-a", 7L))
    }

    @Test fun boundsPendingEntriesAndRejectsInvalidKeys() {
        val tracker = TransferResumeExchangeTracker(maxPendingExchanges = 2)
        assertEquals(
            TransferResumeExchangeTracker.Update.INVALID_KEY,
            tracker.recordDataPayload("", 1L)
        )
        assertEquals(
            TransferResumeExchangeTracker.Update.INVALID_KEY,
            tracker.recordDataPayload("peer", -1L)
        )
        assertEquals(TransferResumeExchangeTracker.Update.STORED, tracker.recordDataPayload("peer", 1L))
        assertEquals(TransferResumeExchangeTracker.Update.STORED, tracker.recordDataPayload("peer", 2L))
        assertEquals(
            TransferResumeExchangeTracker.Update.CAPACITY_REACHED,
            tracker.recordDataPayload("peer", 3L)
        )
        assertEquals(2, tracker.pendingCount())
    }

    @Test fun endpointCleanupRemovesOnlyItsPendingExchanges() {
        val tracker = TransferResumeExchangeTracker()
        tracker.recordProposal("peer-a", proposalEnvelope(1L))
        tracker.recordDecision("peer-b", decisionEnvelope(2L))
        tracker.recordDataPayload("peer-b", 2L)
        tracker.clearEndpoint("peer-a")
        assertEquals(1, tracker.pendingCount())
        assertNotNull(tracker.takeReady("peer-b", 2L))
        tracker.clear()
        assertEquals(0, tracker.pendingCount())
    }

    @Test fun validatesConfiguredCapacity() {
        try {
            TransferResumeExchangeTracker(0)
            fail("Zero capacity must be rejected")
        } catch (_: IllegalArgumentException) { }
        try {
            TransferResumeExchangeTracker(257)
            fail("Excessive capacity must be rejected")
        } catch (_: IllegalArgumentException) { }
    }
}
