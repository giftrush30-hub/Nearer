package com.nearer.app

import org.junit.Assert.*
import org.junit.Test

class TransferResumeOffsetPolicyTest {
    private val chunk = TransferResumeCheckpoint.CHECKPOINT_GRANULARITY_BYTES
    private val item = TransferBundleItem(
        "item-7", "Movies/clip.mp4", "clip.mp4", chunk * 3L, "video/mp4"
    )
    private val proposal = TransferResumeNegotiation.Proposal(
        "bundle-9", item.id, item.relativePath, item.sizeBytes, 1234L,
        chunk, "a".repeat(64)
    )
    private val accepted = TransferResumeNegotiation.Decision(
        chunk, TransferResumeNegotiation.Reason.RESUME_ACCEPTED
    )

    private fun validate(
        payloadId: Long = 81L,
        transferId: String = "bundle-9",
        selectedItem: TransferBundleItem = item,
        modifiedAt: Long? = 1234L,
        selectedProposal: TransferResumeNegotiation.Proposal = proposal,
        decision: TransferResumeNegotiation.Decision = accepted
    ) = TransferResumeOffsetPolicy.validate(
        expectedPayloadId = 81L,
        receivedPayloadId = payloadId,
        transferId = transferId,
        item = selectedItem,
        sourceModifiedAtMillis = modifiedAt,
        proposal = selectedProposal,
        decision = decision
    )

    @Test fun acceptsOnlyTheExactNegotiatedPayloadAndDurableOffset() {
        val result = validate()
        assertTrue(result.accepted)
        assertEquals(chunk, result.offsetBytes)
        assertEquals(TransferResumeOffsetPolicy.Rejection.NONE, result.rejection)
    }

    @Test fun rejectsAResumeDecisionBoundToAnotherPayload() {
        val result = validate(payloadId = 82L)
        assertFalse(result.accepted)
        assertEquals(0L, result.offsetBytes)
        assertEquals(TransferResumeOffsetPolicy.Rejection.PAYLOAD_ID_MISMATCH, result.rejection)
    }

    @Test fun safelyUsesZeroWhenPeerRequestsRestart() {
        val restart = TransferResumeNegotiation.Decision(
            0L, TransferResumeNegotiation.Reason.SOURCE_CHANGED
        )
        val result = validate(decision = restart)
        assertFalse(result.accepted)
        assertEquals(0L, result.offsetBytes)
        assertEquals(TransferResumeOffsetPolicy.Rejection.PEER_REQUESTED_RESTART, result.rejection)
    }

    @Test fun rejectsChangedBundleOrSourceIdentity() {
        assertEquals(
            TransferResumeOffsetPolicy.Rejection.SOURCE_IDENTITY_MISMATCH,
            validate(transferId = "different-bundle").rejection
        )
        assertEquals(
            TransferResumeOffsetPolicy.Rejection.SOURCE_IDENTITY_MISMATCH,
            validate(modifiedAt = 5678L).rejection
        )
        assertEquals(
            TransferResumeOffsetPolicy.Rejection.SOURCE_IDENTITY_MISMATCH,
            validate(selectedItem = item.copy(relativePath = "Movies/other.mp4")).rejection
        )
    }

    @Test fun rejectsDecisionOffsetDifferentFromTheProposedOffset() {
        val result = validate(
            decision = TransferResumeNegotiation.Decision(
                chunk * 2L, TransferResumeNegotiation.Reason.RESUME_ACCEPTED
            )
        )
        assertEquals(0L, result.offsetBytes)
        assertEquals(
            TransferResumeOffsetPolicy.Rejection.DECISION_OFFSET_MISMATCH,
            result.rejection
        )
    }

    @Test fun rejectsOffsetsOutsideTheFileAndNonDurableBoundaries() {
        val atEnd = validate(
            selectedProposal = proposal.copy(committedOffsetBytes = item.sizeBytes),
            decision = TransferResumeNegotiation.Decision(
                item.sizeBytes, TransferResumeNegotiation.Reason.RESUME_ACCEPTED
            )
        )
        assertEquals(TransferResumeOffsetPolicy.Rejection.OFFSET_OUT_OF_RANGE, atEnd.rejection)

        val unaligned = validate(
            selectedProposal = proposal.copy(committedOffsetBytes = chunk + 1L),
            decision = TransferResumeNegotiation.Decision(
                chunk + 1L, TransferResumeNegotiation.Reason.RESUME_ACCEPTED
            )
        )
        assertEquals(TransferResumeOffsetPolicy.Rejection.OFFSET_NOT_DURABLE, unaligned.rejection)
        assertEquals(0L, unaligned.offsetBytes)
    }

    @Test fun rejectsMalformedPrefixDigestAndNonFileItems() {
        val malformedDigest = validate(
            selectedProposal = proposal.copy(prefixSha256 = "not-a-digest")
        )
        assertEquals(TransferResumeOffsetPolicy.Rejection.OFFSET_NOT_DURABLE, malformedDigest.rejection)

        val directory = item.copy(kind = TransferBundleItem.Kind.DIRECTORY, sizeBytes = 0L)
        val invalidKind = validate(
            selectedItem = directory,
            selectedProposal = proposal.copy(expectedSizeBytes = 0L, committedOffsetBytes = 0L),
            decision = TransferResumeNegotiation.Decision(
                0L, TransferResumeNegotiation.Reason.RESUME_ACCEPTED
            )
        )
        assertEquals(TransferResumeOffsetPolicy.Rejection.SOURCE_IDENTITY_MISMATCH, invalidKind.rejection)
    }
}
