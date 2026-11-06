package com.nearer.app

import org.junit.Assert.*
import org.junit.Test

class TransferResumeMessageCodecTest {
    private val chunk = TransferResumeCheckpoint.CHECKPOINT_GRANULARITY_BYTES
    private val proposal = TransferResumeNegotiation.Proposal(
        "transfer-α", "item-1", "Videos/clip.mp4", chunk * 2,
        1_700_000_000_000L, chunk, "A".repeat(64)
    )

    @Test fun roundTripsUnicodeIdentityAndPayloadBinding() {
        val decoded = TransferResumeMessageCodec.decode(TransferResumeMessageCodec.encode(42L, proposal))
        assertEquals(42L, decoded.payloadId)
        assertEquals(proposal.copy(prefixSha256 = "a".repeat(64)), decoded.proposal)
    }

    @Test fun rejectsTruncatedAndTrailingData() {
        val encoded = TransferResumeMessageCodec.encode(7L, proposal)
        try {
            TransferResumeMessageCodec.decode(encoded.copyOf(encoded.size - 1))
            fail("Expected truncated message rejection")
        } catch (_: IllegalArgumentException) { }
        try {
            TransferResumeMessageCodec.decode(encoded + byteArrayOf(0))
            fail("Expected trailing data rejection")
        } catch (_: IllegalArgumentException) { }
    }

    @Test fun rejectsInvalidOffsetAndPayloadIdentity() {
        try {
            TransferResumeMessageCodec.encode(1L, proposal.copy(committedOffsetBytes = chunk + 1L))
            fail("Expected unaligned offset rejection")
        } catch (_: IllegalArgumentException) { }
        try {
            TransferResumeMessageCodec.encode(-1L, proposal)
            fail("Expected negative payload ID rejection")
        } catch (_: IllegalArgumentException) { }
    }

    @Test fun rejectsMalformedUtf8InIdentityFields() {
        val encoded = TransferResumeMessageCodec.encode(9L, proposal)
        // Header (magic + version + payload ID) and the first string length
        // occupy 15 bytes. Replace the first UTF-8 byte with an incomplete
        // leading byte so the decoder must reject malformed identity data.
        val malformed = encoded.copyOf().also { it[15] = 0xC3.toByte() }
        try {
            TransferResumeMessageCodec.decode(malformed)
            fail("Expected malformed UTF-8 rejection")
        } catch (_: IllegalArgumentException) { }
    }

    @Test fun rejectsOverlongIdentityFieldLength() {
        val encoded = TransferResumeMessageCodec.encode(10L, proposal)
        val malformed = encoded.copyOf().also {
            it[13] = 0x7f
            it[14] = 0xff.toByte()
        }
        try {
            TransferResumeMessageCodec.decode(malformed)
            fail("Expected oversized identity length rejection")
        } catch (_: IllegalArgumentException) { }
    }

    @Test fun roundTripsAcceptedResumeDecisionBoundToPayload() {
        val decision = TransferResumeNegotiation.Decision(
            chunk, TransferResumeNegotiation.Reason.RESUME_ACCEPTED
        )
        val decoded = TransferResumeMessageCodec.decodeDecision(
            TransferResumeMessageCodec.encodeDecision(77L, decision)
        )
        assertEquals(77L, decoded.payloadId)
        assertEquals(decision, decoded.decision)
    }

    @Test fun roundTripsSafeRestartDecisionWithZeroOffset() {
        val decision = TransferResumeNegotiation.Decision(
            0L, TransferResumeNegotiation.Reason.SOURCE_CHANGED
        )
        val decoded = TransferResumeMessageCodec.decodeDecision(
            TransferResumeMessageCodec.encodeDecision(78L, decision)
        )
        assertEquals(decision, decoded.decision)
    }

    @Test fun rejectsDecisionWithOffsetThatContradictsOutcome() {
        try {
            TransferResumeMessageCodec.encodeDecision(
                1L, TransferResumeNegotiation.Decision(
                    chunk, TransferResumeNegotiation.Reason.SOURCE_CHANGED
                )
            )
            fail("Expected rejected decision with nonzero offset")
        } catch (_: IllegalArgumentException) { }
        try {
            TransferResumeMessageCodec.encodeDecision(
                1L, TransferResumeNegotiation.Decision(
                    0L, TransferResumeNegotiation.Reason.RESUME_ACCEPTED
                )
            )
            fail("Expected accepted decision without offset to be rejected")
        } catch (_: IllegalArgumentException) { }
    }

    @Test fun rejectsMalformedDecisionAndProposalCrossDecoding() {
        val decision = TransferResumeNegotiation.Decision(
            chunk, TransferResumeNegotiation.Reason.RESUME_ACCEPTED
        )
        val encodedDecision = TransferResumeMessageCodec.encodeDecision(3L, decision)
        try {
            TransferResumeMessageCodec.decodeDecision(encodedDecision.copyOf(encodedDecision.size - 1))
            fail("Expected truncated decision rejection")
        } catch (_: IllegalArgumentException) { }
        try {
            TransferResumeMessageCodec.decode(encodedDecision)
            fail("Proposal decoder must reject a decision envelope")
        } catch (_: IllegalArgumentException) { }
        try {
            TransferResumeMessageCodec.decodeDecision(TransferResumeMessageCodec.encode(3L, proposal))
            fail("Decision decoder must reject a proposal envelope")
        } catch (_: IllegalArgumentException) { }
    }

    @Test fun rejectsBadMagicAndUnsupportedVersion() {
        val encoded = TransferResumeMessageCodec.encode(1L, proposal)
        val badMagic = encoded.copyOf().also { it[0] = 0 }
        try {
            TransferResumeMessageCodec.decode(badMagic)
            fail("Expected bad magic rejection")
        } catch (_: IllegalArgumentException) { }
        val badVersion = encoded.copyOf().also { it[4] = 99 }
        try {
            TransferResumeMessageCodec.decode(badVersion)
            fail("Expected version rejection")
        } catch (_: IllegalArgumentException) { }
    }
}
