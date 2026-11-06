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
