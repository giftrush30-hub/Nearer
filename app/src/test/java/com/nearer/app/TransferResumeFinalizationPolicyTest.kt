package com.nearer.app

import org.junit.Assert.*
import org.junit.Test

class TransferResumeFinalizationPolicyTest {
    private val digest = "a".repeat(64)
    private val item = TransferBundleItem(
        id = "item-1",
        relativePath = "Videos/clip.mp4",
        displayName = "clip.mp4",
        sizeBytes = 4096L,
        mimeType = "video/mp4"
    )

    @Test fun acceptsExactLengthAndMatchingDigest() {
        val result = TransferResumeFinalizationPolicy.evaluate(item, 4096L, digest, digest.uppercase())
        assertTrue(result.accepted)
        assertNull(result.rejection)
    }

    @Test fun rejectsTruncatedAndOversizedFiles() {
        assertEquals(
            TransferResumeFinalizationPolicy.Rejection.SIZE_MISMATCH,
            TransferResumeFinalizationPolicy.evaluate(item, 4095L, digest, digest).rejection
        )
        assertEquals(
            TransferResumeFinalizationPolicy.Rejection.SIZE_MISMATCH,
            TransferResumeFinalizationPolicy.evaluate(item, 4097L, digest, digest).rejection
        )
    }

    @Test fun rejectsMissingOrMalformedExpectedDigest() {
        assertEquals(
            TransferResumeFinalizationPolicy.Rejection.MISSING_EXPECTED_DIGEST,
            TransferResumeFinalizationPolicy.evaluate(item, 4096L, digest, null).rejection
        )
        assertEquals(
            TransferResumeFinalizationPolicy.Rejection.MALFORMED_EXPECTED_DIGEST,
            TransferResumeFinalizationPolicy.evaluate(item, 4096L, digest, "not-a-digest").rejection
        )
    }

    @Test fun rejectsMalformedActualDigest() {
        assertEquals(
            TransferResumeFinalizationPolicy.Rejection.MALFORMED_ACTUAL_DIGEST,
            TransferResumeFinalizationPolicy.evaluate(item, 4096L, "broken", digest).rejection
        )
    }

    @Test fun rejectsAValidButDifferentDigest() {
        assertEquals(
            TransferResumeFinalizationPolicy.Rejection.DIGEST_MISMATCH,
            TransferResumeFinalizationPolicy.evaluate(item, 4096L, "b".repeat(64), digest).rejection
        )
    }

    @Test fun refusesDirectoryEntries() {
        val directory = item.copy(kind = TransferBundleItem.Kind.DIRECTORY, sizeBytes = 0L)
        assertEquals(
            TransferResumeFinalizationPolicy.Rejection.NOT_A_FILE,
            TransferResumeFinalizationPolicy.evaluate(directory, 0L, digest, digest).rejection
        )
    }

    @Test fun rejectsNegativeLengths() {
        assertEquals(
            TransferResumeFinalizationPolicy.Rejection.SIZE_MISMATCH,
            TransferResumeFinalizationPolicy.evaluate(item, -1L, digest, digest).rejection
        )
    }
}
