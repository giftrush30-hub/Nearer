package com.nearer.app

/**
 * Pure decision layer for negotiating a safe resume offset between two peers.
 *
 * The receiver may propose only bytes it has durably synced and re-verified.
 * The sender independently checks source identity before accepting that offset.
 * This class deliberately does not call Nearby Payload.setOffset: transport
 * integration must still apply the accepted offset before sending the FILE.
 */
internal object TransferResumeNegotiation {
    enum class Reason {
        RESUME_ACCEPTED,
        PEER_UNSUPPORTED,
        NO_VALID_CHECKPOINT,
        SOURCE_CHANGED,
        OFFSET_INVALID,
        ALREADY_COMPLETE
    }

    data class Proposal(
        val transferId: String,
        val itemId: String,
        val relativePath: String,
        val expectedSizeBytes: Long,
        val sourceModifiedAtMillis: Long?,
        val committedOffsetBytes: Long,
        val prefixSha256: String
    )

    data class Decision(
        val acceptedOffsetBytes: Long,
        val reason: Reason
    )

    fun propose(
        checkpoint: TransferResumeCheckpoint?,
        receiverPrefixVerified: Boolean,
        senderSupportsOffsets: Boolean,
        receiverSupportsOffsets: Boolean
    ): Proposal? {
        if (!senderSupportsOffsets || !receiverSupportsOffsets || !receiverPrefixVerified) return null
        checkpoint ?: return null
        if (checkpoint.validationError() != null || checkpoint.committedOffsetBytes <= 0L ||
            checkpoint.committedOffsetBytes >= checkpoint.expectedSizeBytes) return null
        return Proposal(
            checkpoint.transferId,
            checkpoint.itemId,
            checkpoint.relativePath,
            checkpoint.expectedSizeBytes,
            checkpoint.sourceModifiedAtMillis,
            checkpoint.committedOffsetBytes,
            checkpoint.prefixSha256.lowercase()
        )
    }

    fun decide(
        proposal: Proposal?,
        transferId: String,
        item: TransferBundleItem,
        sourceModifiedAtMillis: Long?,
        senderSupportsOffsets: Boolean,
        receiverSupportsOffsets: Boolean,
        senderPrefixSha256: String?
    ): Decision {
        if (!senderSupportsOffsets || !receiverSupportsOffsets) {
            return Decision(0L, Reason.PEER_UNSUPPORTED)
        }
        if (proposal == null) return Decision(0L, Reason.NO_VALID_CHECKPOINT)
        if (proposal.transferId != transferId || item.kind != TransferBundleItem.Kind.FILE ||
            proposal.itemId != item.id || proposal.relativePath != item.relativePath ||
            proposal.expectedSizeBytes != item.sizeBytes ||
            proposal.sourceModifiedAtMillis != sourceModifiedAtMillis) {
            return Decision(0L, Reason.SOURCE_CHANGED)
        }
        val offset = proposal.committedOffsetBytes
        if (offset == item.sizeBytes) return Decision(0L, Reason.ALREADY_COMPLETE)
        if (offset <= 0L || offset > item.sizeBytes ||
            offset % TransferResumeCheckpoint.CHECKPOINT_GRANULARITY_BYTES != 0L ||
            !Regex("^[a-fA-F0-9]{64}$").matches(proposal.prefixSha256)) {
            return Decision(0L, Reason.OFFSET_INVALID)
        }
        if (senderPrefixSha256 == null ||
            !Regex("^[a-fA-F0-9]{64}$").matches(senderPrefixSha256) ||
            !senderPrefixSha256.equals(proposal.prefixSha256, ignoreCase = true)) {
            return Decision(0L, Reason.OFFSET_INVALID)
        }
        return Decision(offset, Reason.RESUME_ACCEPTED)
    }
}
