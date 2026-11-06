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

    @Test fun journalEnumeratesValidEntriesAndRemovesWholeTransfer() {
        val directory = Files.createTempDirectory("nearer-resume-list").toFile()
        try {
            val journal = TransferResumeJournal(directory)
            val first = checkpoint()
            val second = checkpoint().copy(
                transferId = "transfer-2",
                itemId = "item-2",
                relativePath = "Pictures/photo.jpg",
                expectedSizeBytes = 4L * 1024L * 1024L,
                committedOffsetBytes = 4L * 1024L * 1024L,
                prefixSha256 = "b".repeat(64)
            )
            journal.save(first)
            journal.save(second)
            assertEquals(2, journal.loadAll().size)
            assertEquals(1, journal.removeTransfer("transfer-1"))
            assertEquals(listOf(second.copy(prefixSha256 = "b".repeat(64))), journal.loadAll())
        } finally {
            directory.deleteRecursively()
        }
    }


    @Test fun journalRejectsOversizedEntryBeforeReadingIt() {
        val directory = Files.createTempDirectory("nearer-resume-oversized").toFile()
        try {
            val journal = TransferResumeJournal(directory)
            journal.save(checkpoint())
            val entry = directory.listFiles()!!.single()
            // The normal codec envelope is only a few hundred bytes. A file
            // larger than the journal's hard limit must never be decoded.
            entry.writeBytes(ByteArray(7 * 1024) { 0x5a })
            assertNull(journal.load("transfer-1", "item-1"))
            assertTrue(journal.loadAll().isEmpty())
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
            assertTrue(journal.loadAll().isEmpty())
        } finally {
            directory.deleteRecursively()
        }
    }
}
