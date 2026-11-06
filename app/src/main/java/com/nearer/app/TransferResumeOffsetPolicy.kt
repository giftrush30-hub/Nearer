package com.nearer.app

/**
 * Final guard between a decoded resume decision and applying an offset to a
 * Nearby Payload. A decision is meaningful only for the exact logical item and
 * the exact payload whose proposal was negotiated.
 *
 * This policy deliberately returns byte zero for every rejected or mismatched
 * decision. Callers must apply the returned offset before sendPayload(); they
 * must never apply an unvalidated offset directly from a control message.
 */
internal object TransferResumeOffsetPolicy {
    enum class Rejection {
        NONE,
        PEER_REQUESTED_RESTART,
        PAYLOAD_ID_MISMATCH,
        SOURCE_IDENTITY_MISMATCH,
        DECISION_OFFSET_MISMATCH,
        OFFSET_OUT_OF_RANGE,
        OFFSET_NOT_DURABLE
    }

    data class Result(
        val offsetBytes: Long,
        val rejection: Rejection
    ) {
        val accepted: Boolean get() = rejection == Rejection.NONE
    }

    fun validate(
        expectedPayloadId: Long,
        receivedPayloadId: Long,
        transferId: String,
        item: TransferBundleItem,
        sourceModifiedAtMillis: Long?,
        proposal: TransferResumeNegotiation.Proposal,
        decision: TransferResumeNegotiation.Decision
    ): Result {
        fun reject(reason: Rejection) = Result(0L, reason)

        if (expectedPayloadId < 0L || receivedPayloadId != expectedPayloadId) {
            return reject(Rejection.PAYLOAD_ID_MISMATCH)
        }
        if (decision.reason != TransferResumeNegotiation.Reason.RESUME_ACCEPTED) {
            return reject(Rejection.PEER_REQUESTED_RESTART)
        }
        if (transferId.isBlank() || item.kind != TransferBundleItem.Kind.FILE ||
            proposal.transferId != transferId ||
            proposal.itemId != item.id ||
            proposal.relativePath != item.relativePath ||
            proposal.expectedSizeBytes != item.sizeBytes ||
            proposal.sourceModifiedAtMillis != sourceModifiedAtMillis
        ) {
            return reject(Rejection.SOURCE_IDENTITY_MISMATCH)
        }
        if (decision.acceptedOffsetBytes != proposal.committedOffsetBytes) {
            return reject(Rejection.DECISION_OFFSET_MISMATCH)
        }
        val offset = decision.acceptedOffsetBytes
        if (item.sizeBytes <= 0L || offset <= 0L || offset >= item.sizeBytes) {
            return reject(Rejection.OFFSET_OUT_OF_RANGE)
        }
        if (offset % TransferResumeCheckpoint.CHECKPOINT_GRANULARITY_BYTES != 0L ||
            !proposal.prefixSha256.matches(Regex("^[a-fA-F0-9]{64}$"))
        ) {
            return reject(Rejection.OFFSET_NOT_DURABLE)
        }
        return Result(offset, Rejection.NONE)
    }
}
