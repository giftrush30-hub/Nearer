package com.nearer.app

import org.junit.Assert.*
import org.junit.Test

class TransferResumeNegotiationTest {
    private val chunk = TransferResumeCheckpoint.CHECKPOINT_GRANULARITY_BYTES
    private val item = TransferBundleItem("item-1", "Videos/clip.mp4", "clip.mp4", chunk * 3, "video/mp4")
    private val checkpoint = TransferResumeCheckpoint(
        "transfer-1", item.id, item.relativePath, item.sizeBytes, 123L,
        chunk, "a".repeat(64)
    )

    @Test fun proposesOnlyVerifiedCompatiblePartialCheckpoints() {
        assertNotNull(TransferResumeNegotiation.propose(checkpoint, true, true, true))
        assertNull(TransferResumeNegotiation.propose(checkpoint, false, true, true))
        assertNull(TransferResumeNegotiation.propose(checkpoint, true, true, false))
        assertNull(TransferResumeNegotiation.propose(checkpoint.copy(committedOffsetBytes = 0L), true, true, true))
        assertNull(TransferResumeNegotiation.propose(checkpoint.copy(committedOffsetBytes = item.sizeBytes), true, true, true))
    }

    @Test fun acceptsOnlyMatchingSourceAndAlignedOffset() {
        val proposal = TransferResumeNegotiation.propose(checkpoint, true, true, true)
        val accepted = TransferResumeNegotiation.decide(proposal, "transfer-1", item, 123L, true, true, "a".repeat(64))
        assertEquals(chunk, accepted.acceptedOffsetBytes)
        assertEquals(TransferResumeNegotiation.Reason.RESUME_ACCEPTED, accepted.reason)

        val changed = TransferResumeNegotiation.decide(proposal, "transfer-1", item, 124L, true, true, "a".repeat(64))
        assertEquals(0L, changed.acceptedOffsetBytes)
        assertEquals(TransferResumeNegotiation.Reason.SOURCE_CHANGED, changed.reason)
    }

    @Test fun rejectsUnsupportedPeersAndMalformedOffsets() {
        val proposal = TransferResumeNegotiation.propose(checkpoint, true, true, true)!!
        val unsupported = TransferResumeNegotiation.decide(proposal, "transfer-1", item, 123L, true, false, "a".repeat(64))
        assertEquals(TransferResumeNegotiation.Reason.PEER_UNSUPPORTED, unsupported.reason)

        val malformed = TransferResumeNegotiation.decide(
            proposal.copy(committedOffsetBytes = chunk + 1L),
            "transfer-1", item, 123L, true, true, "a".repeat(64)
        )
        assertEquals(TransferResumeNegotiation.Reason.OFFSET_INVALID, malformed.reason)
        assertEquals(0L, malformed.acceptedOffsetBytes)

        val mismatchedPrefix = TransferResumeNegotiation.decide(
            proposal, "transfer-1", item, 123L, true, true, "b".repeat(64)
        )
        assertEquals(TransferResumeNegotiation.Reason.OFFSET_INVALID, mismatchedPrefix.reason)
        assertEquals(0L, mismatchedPrefix.acceptedOffsetBytes)
    }

    @Test fun neverTreatsACompleteCheckpointAsAnAppendOffset() {
        val proposal = checkpoint.copy(committedOffsetBytes = item.sizeBytes)
        val decision = TransferResumeNegotiation.decide(
            TransferResumeNegotiation.Proposal(
                proposal.transferId, proposal.itemId, proposal.relativePath,
                proposal.expectedSizeBytes, proposal.sourceModifiedAtMillis,
                proposal.committedOffsetBytes, proposal.prefixSha256
            ), "transfer-1", item, 123L, true, true, "a".repeat(64)
        )
        assertEquals(TransferResumeNegotiation.Reason.ALREADY_COMPLETE, decision.reason)
        assertEquals(0L, decision.acceptedOffsetBytes)
    }
}
