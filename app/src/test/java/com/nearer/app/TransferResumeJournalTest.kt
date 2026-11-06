package com.nearer.app

import java.io.File
import java.nio.file.Files
import org.junit.Assert.*
import org.junit.Test

class TransferResumeJournalTest {
    private fun checkpoint() = TransferResumeCheckpoint(
        "transfer-1", "item-1", "Movies/film.mp4",
        20L * 1024L * 1024L, 1234L,
        8L * 1024L * 1024L, "A".repeat(64)
    )

    @Test fun codecRoundTripsCheckpointAndNormalizesDigest() {
        val decoded = TransferResumeCheckpointCodec.decode(
            TransferResumeCheckpointCodec.encode(checkpoint())
        )
        assertEquals(checkpoint().copy(prefixSha256 = "a".repeat(64)), decoded)
    }

    @Test fun codecRejectsTruncatedAndTrailingData() {
        val encoded = TransferResumeCheckpointCodec.encode(checkpoint())
        assertThrows(IllegalArgumentException::class.java) {
            TransferResumeCheckpointCodec.decode(encoded.copyOf(encoded.size - 1))
        }
        assertThrows(IllegalArgumentException::class.java) {
            TransferResumeCheckpointCodec.decode(encoded + byteArrayOf(1))
        }
    }

    @Test fun journalPersistsReplacesLoadsAndRemovesCheckpoint() {
        val directory = Files.createTempDirectory("nearer-resume").toFile()
        try {
            val journal = TransferResumeJournal(directory)
            journal.save(checkpoint())
            assertEquals(checkpoint().copy(prefixSha256 = "a".repeat(64)),
                journal.load("transfer-1", "item-1"))

            val updated = checkpoint().copy(
                committedOffsetBytes = 12L * 1024L * 1024L,
                prefixSha256 = "b".repeat(64)
            )
            journal.save(updated)
            assertEquals(updated, journal.load("transfer-1", "item-1"))
            assertTrue(journal.remove("transfer-1", "item-1"))
            assertNull(journal.load("transfer-1", "item-1"))
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test fun journalDoesNotReturnCheckpointForDifferentIdentity() {
        val directory = Files.createTempDirectory("nearer-resume-id").toFile()
        try {
            val journal = TransferResumeJournal(directory)
            journal.save(checkpoint())
            assertNull(journal.load("transfer-1", "other-item"))
            assertNull(journal.load("other-transfer", "item-1"))
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test fun corruptJournalEntryIsIgnoredWithoutCrashing() {
        val directory = Files.createTempDirectory("nearer-resume-corrupt").toFile()
        try {
            val journal = TransferResumeJournal(directory)
            journal.save(checkpoint())
            directory.listFiles()!!.single().writeBytes(byteArrayOf(0, 1, 2))
            assertNull(journal.load("transfer-1", "item-1"))
        } finally {
            directory.deleteRecursively()
        }
    }
}
