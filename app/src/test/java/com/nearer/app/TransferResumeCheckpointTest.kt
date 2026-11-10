package com.nearer.app

import org.junit.Assert.*
import org.junit.Test

class TransferResumeCheckpointTest {
    private val digest = "a".repeat(64)
    private val file = TransferBundleItem("item-1", "Videos/movie.mp4", "movie.mp4", 12L * 1024L * 1024L, "video/mp4")

    private fun checkpoint(offset: Long = 4L * 1024L * 1024L) = TransferResumeCheckpoint(
        "transfer-1", "item-1", "Videos/movie.mp4", file.sizeBytes, 1234L, offset, digest
    )

    @Test fun alignedOffsetRoundsDownToDurableBoundary() {
        assertEquals(4L * 1024L * 1024L,
            TransferResumeCheckpoint.alignedOffset(5L * 1024L * 1024L, file.sizeBytes))
    }

    @Test fun completedFileMayUseExactNonAlignedSize() {
        assertEquals(12L * 1024L * 1024L + 17L,
            TransferResumeCheckpoint.alignedOffset(12L * 1024L * 1024L + 17L, 12L * 1024L * 1024L + 17L))
    }

    @Test fun checkpointMatchesOnlySameTransferItemPathSizeAndSourceVersion() {
        assertTrue(checkpoint().matches("transfer-1", file, 1234L))
        assertFalse(checkpoint().matches("another-transfer", file, 1234L))
        assertFalse(checkpoint().matches("transfer-1", file.copy(sizeBytes = file.sizeBytes + 1), 1234L))
        assertFalse(checkpoint().matches("transfer-1", file, 9999L))
        assertFalse(checkpoint().matches("transfer-1", file.copy(relativePath = "Other/movie.mp4"), 1234L))
    }

    @Test fun rejectsUnalignedPartialOffset() {
        assertEquals("Checkpoint offset is not aligned to a durable boundary",
            checkpoint(123L).validationError())
    }

    @Test fun rejectsOffsetBeyondFileAndMalformedDigest() {
        assertEquals("Checkpoint offset is outside the file",
            checkpoint(file.sizeBytes + 1).validationError())
        assertEquals("Invalid prefix SHA-256", checkpoint().copy(prefixSha256 = "xyz").validationError())
    }

    @Test fun zeroByteFileCanHaveZeroOffset() {
        val empty = checkpoint(0).copy(expectedSizeBytes = 0)
        assertNull(empty.validationError())
    }

    @Test fun rejectsNegativeOrOversizedSyncCounts() {
        assertThrows(IllegalArgumentException::class.java) {
            TransferResumeCheckpoint.alignedOffset(-1, file.sizeBytes)
        }
        assertThrows(IllegalArgumentException::class.java) {
            TransferResumeCheckpoint.alignedOffset(file.sizeBytes + 1, file.sizeBytes)
        }
    }
}
