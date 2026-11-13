package com.nearer.app

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HyperTransferStreamAdmissionPolicyTest {
    private val peerId = "ABCDEF234567"
    private val payloadId = 73L
    private val offset = TransferResumeCheckpoint.CHECKPOINT_GRANULARITY_BYTES

    private fun proposal() = TransferResumeNegotiation.Proposal(
        transferId = "transfer-1",
        itemId = "item-1",
        relativePath = "Videos/clip.mp4",
        expectedSizeBytes = offset * 2,
        sourceModifiedAtMillis = 1234L,
        committedOffsetBytes = offset,
        prefixSha256 = "a".repeat(64)
    )

    private fun mayConsume(
        peer: String? = peerId,
        expectedId: Long = payloadId,
        actualId: Long = payloadId,
        isStream: Boolean = true,
        proposal: TransferResumeNegotiation.Proposal? = proposal(),
        decision: TransferResumeNegotiation.Decision = TransferResumeNegotiation.Decision(
            offset, TransferResumeNegotiation.Reason.RESUME_ACCEPTED
        )
    ) = HyperTransferStreamAdmissionPolicy.mayConsume(
        peer, expectedId, actualId, isStream, proposal, decision
    )

    @Test fun acceptsOnlyExactPeerPayloadAndApprovedOffset() {
        assertTrue(mayConsume())
        assertFalse(mayConsume(peer = null))
        assertFalse(mayConsume(peer = "NOTVALID"))
        assertFalse(mayConsume(expectedId = payloadId + 1))
        assertFalse(mayConsume(actualId = payloadId + 1))
        assertFalse(mayConsume(isStream = false))
        assertFalse(mayConsume(decision = TransferResumeNegotiation.Decision(
            offset * 2, TransferResumeNegotiation.Reason.RESUME_ACCEPTED
        )))
        assertFalse(mayConsume(proposal = null))
    }

    @Test fun allowsCleanRestartOnlyForExplicitZeroOffsetOutcomes() {
        val restartReasons = listOf(
            TransferResumeNegotiation.Reason.NO_VALID_CHECKPOINT,
            TransferResumeNegotiation.Reason.SOURCE_CHANGED,
            TransferResumeNegotiation.Reason.PEER_UNSUPPORTED
        )
        restartReasons.forEach { reason ->
            assertTrue(mayConsume(
                proposal = null,
                decision = TransferResumeNegotiation.Decision(0L, reason)
            ))
            assertFalse(mayConsume(
                proposal = proposal(),
                decision = TransferResumeNegotiation.Decision(offset, reason)
            ))
        }
    }

    @Test fun rejectsInvalidOffsetAndAlreadyCompleteDecisions() {
        assertFalse(mayConsume(
            decision = TransferResumeNegotiation.Decision(
                0L, TransferResumeNegotiation.Reason.OFFSET_INVALID
            )
        ))
        assertFalse(mayConsume(
            decision = TransferResumeNegotiation.Decision(
                0L, TransferResumeNegotiation.Reason.ALREADY_COMPLETE
            )
        ))
    }
}
