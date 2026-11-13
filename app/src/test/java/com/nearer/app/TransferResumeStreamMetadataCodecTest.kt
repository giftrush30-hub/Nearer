package com.nearer.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TransferResumeStreamMetadataCodecTest {
    private fun metadata() = TransferResumeStreamMetadataCodec.Metadata(
        payloadId = 91L,
        transferId = "transfer-2026-11-13",
        item = TransferBundleItem(
            id = "item-video-1",
            relativePath = "Videos/holiday clip.mp4",
            displayName = "holiday clip.mp4",
            sizeBytes = 9_876_543_210L,
            mimeType = "video/mp4"
        ),
        sourceModifiedAtMillis = 1_794_568_000_000L,
        wholeFileSha256 = "ABCDEF".repeat(10) + "ABCD"
    )

    @Test fun roundTripsPayloadIdentityManifestAndDigest() {
        val encoded = TransferResumeStreamMetadataCodec.encode(metadata())
        assertTrue(TransferResumeStreamMetadataCodec.isEnvelope(encoded))
        val decoded = TransferResumeStreamMetadataCodec.decode(encoded)
        assertEquals(metadata().copy(wholeFileSha256 = metadata().wholeFileSha256.lowercase()), decoded)
    }

    @Test fun supportsProvidersWithoutModificationTimestamp() {
        val original = metadata().copy(
            sourceModifiedAtMillis = null,
            wholeFileSha256 = metadata().wholeFileSha256.lowercase()
        )
        assertEquals(original, TransferResumeStreamMetadataCodec.decode(
            TransferResumeStreamMetadataCodec.encode(original)
        ))
    }

    @Test fun rejectsUnsafePathsAndInvalidDigests() {
        val unsafe = metadata().copy(item = metadata().item.copy(relativePath = "../private.bin"))
        try {
            TransferResumeStreamMetadataCodec.encode(unsafe)
            throw AssertionError("Unsafe relative paths must be rejected")
        } catch (_: IllegalArgumentException) { }

        val badDigest = metadata().copy(wholeFileSha256 = "not-a-digest")
        try {
            TransferResumeStreamMetadataCodec.encode(badDigest)
            throw AssertionError("Invalid whole-file digests must be rejected")
        } catch (_: IllegalArgumentException) { }
    }

    @Test fun rejectsTrailingBytesAndWrongPayloadId() {
        val encoded = TransferResumeStreamMetadataCodec.encode(metadata())
        val withTrailing = encoded + byteArrayOf(1)
        try {
            TransferResumeStreamMetadataCodec.decode(withTrailing)
            throw AssertionError("Trailing bytes must be rejected")
        } catch (_: IllegalArgumentException) { }

        val invalidId = metadata().copy(payloadId = -1L)
        try {
            TransferResumeStreamMetadataCodec.encode(invalidId)
            throw AssertionError("Negative payload IDs must be rejected")
        } catch (_: IllegalArgumentException) { }
        assertFalse(TransferResumeStreamMetadataCodec.isEnvelope(byteArrayOf(1, 2, 3)))
    }
}
