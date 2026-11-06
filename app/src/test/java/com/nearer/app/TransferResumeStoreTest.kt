package com.nearer.app

import java.io.File
import java.nio.file.Files
import org.junit.Assert.*
import org.junit.Test

class TransferResumeStoreTest {
    private val chunk = TransferResumeCheckpoint.CHECKPOINT_GRANULARITY_BYTES.toInt()

    private fun item(size: Long = chunk.toLong() * 2) = TransferBundleItem(
        id = "item-1",
        relativePath = "Videos/clip.mp4",
        displayName = "clip.mp4",
        sizeBytes = size,
        mimeType = "video/mp4"
    )

    private fun store(root: File): TransferResumeStore =
        TransferResumeStore(File(root, "partials"), TransferResumeJournal(File(root, "journal")))

    @Test fun syncsAndPersistsOnlyAlignedCheckpointThenFindsIt() {
        val root = Files.createTempDirectory("nearer-resume-store").toFile()
        try {
            val store = store(root)
            val selected = item()
            val partial = store.partialFileFor("transfer-1", selected.id).apply { parentFile?.mkdirs() }
            partial.writeBytes(ByteArray(chunk + 123) { (it % 251).toByte() })
            val checkpoint = store.checkpoint("transfer-1", selected, 100L, chunk.toLong() + 123L)
            assertEquals(chunk.toLong(), checkpoint.committedOffsetBytes)
            val candidate = store.findValid("transfer-1", selected, 100L)
            assertNotNull(candidate)
            assertEquals(chunk.toLong(), candidate!!.partialFile.length())
        } finally {
            root.deleteRecursively()
        }
    }

    @Test fun refusesChangedSourceIdentityAndCorruptPrefix() {
        val root = Files.createTempDirectory("nearer-resume-store-identity").toFile()
        try {
            val store = store(root)
            val selected = item()
            val partial = store.partialFileFor("transfer-1", selected.id).apply { parentFile?.mkdirs() }
            partial.writeBytes(ByteArray(chunk) { 4 })
            // Store derives its own filename, so use the checkpoint operation to
            // establish the canonical partial path.
            val checkpoint = store.checkpoint("transfer-1", selected, 100L, chunk.toLong())
            assertNull(store.findValid("transfer-1", selected, 101L))
            assertFalse("changed-source partial must be discarded", partial.exists())
            assertNull(store.findValid("transfer-1", selected, 100L))

            // A fresh checkpoint whose bytes are later corrupted is also removed.
            partial.parentFile?.mkdirs()
            partial.writeBytes(ByteArray(chunk) { 4 })
            store.checkpoint("transfer-1", selected, 100L, chunk.toLong())
            partial.writeBytes(ByteArray(chunk) { 9 })
            assertNull(store.findValid("transfer-1", selected, 100L))
            assertFalse("corrupt partial must be discarded", partial.exists())
            assertEquals(chunk.toLong(), checkpoint.committedOffsetBytes)
        } finally {
            root.deleteRecursively()
        }
    }

    @Test fun rejectsInvalidIdentityBeforeMutatingPartialFile() {
        val root = Files.createTempDirectory("nearer-resume-store-invalid-identity").toFile()
        try {
            val store = store(root)
            val selected = item()
            val partial = store.partialFileFor("transfer-1", selected.id).apply {
                parentFile?.mkdirs()
                writeBytes(byteArrayOf(1, 2, 3, 4))
            }
            val original = partial.readBytes()
            try {
                store.checkpoint("x".repeat(129), selected, null, 4L)
                fail("Expected overlong transfer identity to be rejected")
            } catch (_: IllegalArgumentException) { }
            assertArrayEquals("Invalid identity must not truncate or rewrite partial data", original, partial.readBytes())
            assertTrue(TransferResumeJournal(File(root, "journal")).loadAll().isEmpty())

            try {
                store.checkpoint("transfer-1", selected, -1L, 4L)
                fail("Expected invalid source timestamp to be rejected")
            } catch (_: IllegalArgumentException) { }
            assertArrayEquals(original, partial.readBytes())
        } finally {
            root.deleteRecursively()
        }
    }

    @Test fun discardRemovesPartialAndJournalEntry() {
        val root = Files.createTempDirectory("nearer-resume-store-discard").toFile()
        try {
            val store = store(root)
            val selected = item()
            val partial = store.partialFileFor("transfer-1", selected.id).apply {
                parentFile?.mkdirs()
                writeBytes(ByteArray(chunk) { 1 })
            }
            store.checkpoint("transfer-1", selected, null, chunk.toLong())
            val candidate = store.findValid("transfer-1", selected, null)!!
            assertTrue(store.discard("transfer-1", selected.id))
            assertFalse(candidate.partialFile.exists())
            assertNull(store.findValid("transfer-1", selected, null))
        } finally {
            root.deleteRecursively()
        }
    }
}
