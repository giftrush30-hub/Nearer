package com.nearer.app

/**
 * Fail-closed gate between the BYTES resume handshake and a Nearby STREAM.
 * Payload callbacks and control messages may arrive in either order, so the
 * transport must correlate them by payload ID before invoking the stream bridge.
 */
internal object HyperTransferStreamAdmissionPolicy {
    fun mayConsume(
        authenticatedSourcePeerId: String?,
        expectedPayloadId: Long,
        actualPayloadId: Long,
        isStreamPayload: Boolean,
        proposal: TransferResumeNegotiation.Proposal?,
        decision: TransferResumeNegotiation.Decision
    ): Boolean {
        if (NeaRerIdentity.normalize(authenticatedSourcePeerId) == null) return false
        if (expectedPayloadId < 0L || actualPayloadId != expectedPayloadId || !isStreamPayload) return false

        return when (decision.reason) {
            TransferResumeNegotiation.Reason.RESUME_ACCEPTED ->
                proposal != null &&
                    decision.acceptedOffsetBytes > 0L &&
                    decision.acceptedOffsetBytes == proposal.committedOffsetBytes &&
                    decision.acceptedOffsetBytes < proposal.expectedSizeBytes &&
                    decision.acceptedOffsetBytes % TransferResumeCheckpoint.CHECKPOINT_GRANULARITY_BYTES == 0L

            // A missing/changed checkpoint can safely restart from byte zero.
            // An invalid offset is a protocol violation and must not be silently
            // converted into a full transfer.
            TransferResumeNegotiation.Reason.NO_VALID_CHECKPOINT,
            TransferResumeNegotiation.Reason.SOURCE_CHANGED,
            TransferResumeNegotiation.Reason.PEER_UNSUPPORTED ->
                decision.acceptedOffsetBytes == 0L

            TransferResumeNegotiation.Reason.OFFSET_INVALID,
            TransferResumeNegotiation.Reason.ALREADY_COMPLETE -> false
        }
    }
}
