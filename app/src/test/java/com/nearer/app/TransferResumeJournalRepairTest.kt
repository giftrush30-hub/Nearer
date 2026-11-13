package com.nearer.app

import java.io.File
import java.nio.file.Files
import org.junit.Assert.*
import org.junit.Test

class TransferResumeJournalRepairTest {
    private fun checkpoint() = TransferResumeCheckpoint(
        transferId = "journal-transfer",
        itemId = "journal-item",
        relativePath = "files/item.bin",
        expectedSizeBytes = 10L,
        sourceModifiedAtMillis = null,
        committedOffsetBytes = 0L,
        prefixSha256 = "0".repeat(64)
    )

    @Test fun loadDeletesCorruptCheckpointInsteadOfLeavingDeadJournalEntry() {
        val root = Files.createTempDirectory("nearer-journal-corrupt").toFile()
        try {
            val directory = File(root, "journal")
            val journal = TransferResumeJournal(directory)
            val checkpoint = checkpoint()
            journal.save(checkpoint)
            val saved = directory.listFiles()!!.single { it.name.endsWith(".nrc") }
            saved.writeBytes(byteArrayOf(1, 2, 3, 4))

            assertNull(journal.load(checkpoint.transferId, checkpoint.itemId))
            assertFalse("corrupt checkpoint should be removed", saved.exists())
        } finally {
            root.deleteRecursively()
        }
    }

    @Test fun discoveryRemovesOversizedMisnamedAndCrashLeftoverTemporaryEntries() {
        val root = Files.createTempDirectory("nearer-journal-repair").toFile()
        try {
            val directory = File(root, "journal")
            val journal = TransferResumeJournal(directory)
            val checkpoint = checkpoint()
            journal.save(checkpoint)
            val saved = directory.listFiles()!!.single { it.name.endsWith(".nrc") }
            val misnamed = File(directory, "misnamed.nrc")
            assertTrue(saved.renameTo(misnamed))
            val oversized = File(directory, "oversized.nrc").apply { writeBytes(ByteArray(6_145)) }
            val temporary = File(directory, ".interrupted-save.tmp").apply { writeText("partial") }

            assertTrue(journal.loadAll().isEmpty())
            assertFalse("misnamed checkpoint should be removed", misnamed.exists())
            assertFalse("oversized checkpoint should be removed", oversized.exists())
            assertFalse("uncommitted temporary file should be removed", temporary.exists())
        } finally {
            root.deleteRecursively()
        }
    }
}
