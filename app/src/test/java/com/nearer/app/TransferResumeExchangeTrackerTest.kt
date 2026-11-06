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
        val proposalTracker = TransferResumeExchangeTracker()
        assertEquals(
            TransferResumeExchangeTracker.Update.STORED,
            proposalTracker.recordProposal("peer", proposalEnvelope())
        )
        assertEquals(
            TransferResumeExchangeTracker.Update.DUPLICATE,
            proposalTracker.recordProposal("peer", proposalEnvelope())
        )
        assertEquals(
            TransferResumeExchangeTracker.Update.CONFLICT,
            proposalTracker.recordProposal("peer", proposalEnvelope().copy(
                proposal = proposal.copy(prefixSha256 = "c".repeat(64))
            ))
        )
        assertEquals(
            "A conflicting proposal terminally invalidates this exchange",
            TransferResumeExchangeTracker.Update.DUPLICATE,
            proposalTracker.recordDecision("peer", decisionEnvelope())
        )
        assertEquals(0, proposalTracker.pendingCount())

        val decisionTracker = TransferResumeExchangeTracker()
        decisionTracker.recordProposal("peer", proposalEnvelope())
        assertEquals(
            TransferResumeExchangeTracker.Update.STORED,
            decisionTracker.recordDecision("peer", decisionEnvelope())
        )
        assertEquals(
            TransferResumeExchangeTracker.Update.DUPLICATE,
            decisionTracker.recordDecision("peer", decisionEnvelope())
        )
        assertEquals(
            TransferResumeExchangeTracker.Update.CONFLICT,
            decisionTracker.recordDecision("peer", decisionEnvelope().copy(
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
        assertNull("The peer-a exchange still needs its own data callback", tracker.takeReady("peer-a", 7L))
        tracker.recordDataPayload("peer-a", 7L)
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
        tracker.recordProposal("peer-b", proposalEnvelope(2L))
        tracker.recordDecision("peer-b", decisionEnvelope(2L))
        tracker.recordDataPayload("peer-b", 2L)
        tracker.clearEndpoint("peer-a")
        assertEquals(1, tracker.pendingCount())
        assertNotNull(tracker.takeReady("peer-b", 2L))
        tracker.clear()
        assertEquals(0, tracker.pendingCount())
    }

    @Test fun conflictingDuplicateInvalidatesTheWholeExchange() {
        val tracker = TransferResumeExchangeTracker()
        tracker.recordProposal("peer", proposalEnvelope())
        tracker.recordDecision("peer", decisionEnvelope())
        tracker.recordDataPayload("peer", 7L)
        val conflicting = proposal.copy(prefixSha256 = "d".repeat(64))
        assertEquals(
            TransferResumeExchangeTracker.Update.CONFLICT,
            tracker.recordProposal("peer", proposalEnvelope().copy(proposal = conflicting))
        )
        assertNull("Conflicting exchange must not be applied", tracker.takeReady("peer", 7L))
        assertEquals(0, tracker.pendingCount())
    }

    @Test fun incompleteExchangesExpireAndReleaseCapacity() {
        var now = 100L
        val tracker = TransferResumeExchangeTracker(
            maxPendingExchanges = 1,
            nowMillis = { now },
            entryTtlMillis = 50L
        )
        tracker.recordProposal("peer", proposalEnvelope())
        now = 149L
        assertEquals(1, tracker.pendingCount())
        now = 150L
        assertEquals(0, tracker.pendingCount())
        assertEquals(
            TransferResumeExchangeTracker.Update.STORED,
            tracker.recordDataPayload("peer", 8L)
        )
    }

    @Test fun invalidClockRollbackExpiresEntriesSafely() {
        var now = 100L
        val tracker = TransferResumeExchangeTracker(nowMillis = { now }, entryTtlMillis = 100L)
        tracker.recordDataPayload("peer", 9L)
        now = 90L
        assertEquals(0, tracker.pendingCount())
    }

    @Test fun expiryUsesElapsedTimeAcrossSignedLongWraparound() {
        var now = Long.MAX_VALUE - 10L
        val tracker = TransferResumeExchangeTracker(nowMillis = { now }, entryTtlMillis = 50L)
        tracker.recordDataPayload("peer", 41L)
        now = Long.MIN_VALUE + 10L
        assertEquals("Forward elapsed time remains valid across wraparound", 1, tracker.pendingCount())
        now = Long.MIN_VALUE + 40L
        assertEquals("Elapsed time past TTL expires after wraparound", 0, tracker.pendingCount())
    }

    @Test fun terminalTombstoneExpiryUsesElapsedTimeAcrossSignedLongWraparound() {
        var now = Long.MAX_VALUE - 10L
        val tracker = TransferResumeExchangeTracker(nowMillis = { now }, entryTtlMillis = 50L)
        tracker.recordProposal("peer", proposalEnvelope())
        tracker.recordDecision("peer", decisionEnvelope())
        tracker.recordDataPayload("peer", 7L)
        assertNotNull(tracker.takeReady("peer", 7L))
        now = Long.MIN_VALUE + 10L
        assertEquals(TransferResumeExchangeTracker.Update.DUPLICATE, tracker.recordDataPayload("peer", 7L))
        now = Long.MIN_VALUE + 40L
        assertEquals(TransferResumeExchangeTracker.Update.STORED, tracker.recordDataPayload("peer", 7L))
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
    @Test fun lateCallbacksCannotRecreateAConsumedExchange() {
        val tracker = TransferResumeExchangeTracker()
        tracker.recordProposal("peer", proposalEnvelope())
        tracker.recordDecision("peer", decisionEnvelope())
        tracker.recordDataPayload("peer", 7L)
        assertNotNull(tracker.takeReady("peer", 7L))

        assertEquals(
            TransferResumeExchangeTracker.Update.DUPLICATE,
            tracker.recordDataPayload("peer", 7L)
        )
        assertEquals(
            TransferResumeExchangeTracker.Update.DUPLICATE,
            tracker.recordProposal("peer", proposalEnvelope())
        )
        assertEquals(
            TransferResumeExchangeTracker.Update.DUPLICATE,
            tracker.recordDecision("peer", decisionEnvelope())
        )
        assertEquals("Late callbacks must not allocate a fresh pending slot", 0, tracker.pendingCount())
    }

    @Test fun lateCallbacksCannotResurrectAConflictedExchange() {
        val tracker = TransferResumeExchangeTracker()
        tracker.recordProposal("peer", proposalEnvelope())
        val conflicting = proposal.copy(prefixSha256 = "e".repeat(64))
        assertEquals(
            TransferResumeExchangeTracker.Update.CONFLICT,
            tracker.recordProposal("peer", proposalEnvelope().copy(proposal = conflicting))
        )
        assertEquals(
            TransferResumeExchangeTracker.Update.DUPLICATE,
            tracker.recordDecision("peer", decisionEnvelope())
        )
        assertNull(tracker.takeReady("peer", 7L))
        assertEquals(0, tracker.pendingCount())
    }

    @Test fun terminalTombstonesExpireAndEndpointCleanupClearsThem() {
        var now = 100L
        val tracker = TransferResumeExchangeTracker(nowMillis = { now }, entryTtlMillis = 50L)
        tracker.recordProposal("peer", proposalEnvelope())
        tracker.recordDecision("peer", decisionEnvelope())
        tracker.recordDataPayload("peer", 7L)
        assertNotNull(tracker.takeReady("peer", 7L))
        assertEquals(
            TransferResumeExchangeTracker.Update.DUPLICATE,
            tracker.recordDataPayload("peer", 7L)
        )
        now = 150L
        assertEquals(
            TransferResumeExchangeTracker.Update.STORED,
            tracker.recordDataPayload("peer", 7L)
        )
        tracker.clearEndpoint("peer")
        assertEquals(0, tracker.pendingCount())
    }

    @Test fun duplicateCallbacksDoNotKeepIncompleteEntriesAlive() {
        var now = 100L
        val tracker = TransferResumeExchangeTracker(nowMillis = { now }, entryTtlMillis = 50L)
        tracker.recordProposal("peer", proposalEnvelope())
        now = 140L
        assertEquals(
            TransferResumeExchangeTracker.Update.DUPLICATE,
            tracker.recordProposal("peer", proposalEnvelope())
        )
        now = 150L
        assertEquals("An identical retry must not extend the original exchange lease", 0, tracker.pendingCount())
    }

}
