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

    @Test fun discoversVerifiedPartialsAfterStoreRecreation() {
        val root = Files.createTempDirectory("nearer-resume-discovery-restart").toFile()
        try {
            val firstStore = store(root)
            val firstItem = item()
            val firstBytes = ByteArray(chunk) { (it % 241).toByte() }
            val firstPartial = firstStore.partialFileFor("transfer-a", firstItem.id).apply {
                parentFile?.mkdirs()
                writeBytes(firstBytes)
            }
            firstStore.checkpoint("transfer-a", firstItem, 100L, chunk.toLong())

            val secondItem = firstItem.copy(
                id = "item-2",
                relativePath = "Pictures/photo.jpg",
                displayName = "photo.jpg"
            )
            val secondBytes = ByteArray(chunk) { (it % 239).toByte() }
            val secondPartial = firstStore.partialFileFor("transfer-b", secondItem.id).apply {
                parentFile?.mkdirs()
                writeBytes(secondBytes)
            }
            firstStore.checkpoint("transfer-b", secondItem, 200L, chunk.toLong())

            // Simulate process death: use a new store and journal instance.
            val restartedStore = store(root)
            val discovered = restartedStore.discoverVerifiedCandidates()
            assertEquals(2, discovered.size)
            assertEquals(listOf("transfer-a", "transfer-b"), discovered.map { it.checkpoint.transferId })
            assertEquals(chunk.toLong(), firstPartial.length())
            assertEquals(chunk.toLong(), secondPartial.length())
            assertArrayEquals(firstBytes, discovered[0].partialFile.readBytes())
            assertArrayEquals(secondBytes, discovered[1].partialFile.readBytes())
        } finally {
            root.deleteRecursively()
        }
    }

    @Test fun restartDiscoveryDiscardsMissingOrCorruptPartials() {
        val root = Files.createTempDirectory("nearer-resume-discovery-invalid").toFile()
        try {
            val firstStore = store(root)
            val validItem = item()
            val validBytes = ByteArray(chunk) { (it % 227).toByte() }
            val validPartial = firstStore.partialFileFor("transfer-valid", validItem.id).apply {
                parentFile?.mkdirs()
                writeBytes(validBytes)
            }
            firstStore.checkpoint("transfer-valid", validItem, 100L, chunk.toLong())

            val corruptItem = validItem.copy(id = "corrupt-item", relativePath = "Videos/corrupt.mp4", displayName = "corrupt.mp4")
            val corruptPartial = firstStore.partialFileFor("transfer-corrupt", corruptItem.id).apply {
                parentFile?.mkdirs()
                writeBytes(ByteArray(chunk) { 7 })
            }
            firstStore.checkpoint("transfer-corrupt", corruptItem, 200L, chunk.toLong())
            corruptPartial.writeBytes(ByteArray(chunk) { 8 })

            val missingItem = validItem.copy(id = "missing-item", relativePath = "Videos/missing.mp4", displayName = "missing.mp4")
            val missingPartial = firstStore.partialFileFor("transfer-missing", missingItem.id).apply {
                parentFile?.mkdirs()
                writeBytes(ByteArray(chunk) { 9 })
            }
            firstStore.checkpoint("transfer-missing", missingItem, 300L, chunk.toLong())
            assertTrue(missingPartial.delete())

            val restartedStore = store(root)
            val discovered = restartedStore.discoverVerifiedCandidates()
            assertEquals(1, discovered.size)
            assertEquals("transfer-valid", discovered.single().checkpoint.transferId)
            assertTrue(validPartial.exists())
            assertFalse("corrupt partial must be removed", corruptPartial.exists())
            assertFalse("missing partial checkpoint must be removed", TransferResumeJournal(File(root, "journal"))
                .load("transfer-missing", "missing-item") != null)
            assertNull(TransferResumeJournal(File(root, "journal")).load("transfer-corrupt", "corrupt-item"))
        } finally {
            root.deleteRecursively()
        }
    }


    @Test fun discoveryDoesNotOfferZeroOffsetOrAlreadyCompleteFilesAsResumable() {
        val root = Files.createTempDirectory("nearer-resume-discovery-boundaries").toFile()
        try {
            val firstStore = store(root)
            val tinyItem = item(100L)
            val tinyPartial = firstStore.partialFileFor("transfer-zero", tinyItem.id).apply {
                parentFile?.mkdirs()
                writeBytes(ByteArray(10) { 1 })
            }
            firstStore.checkpoint("transfer-zero", tinyItem, null, 10L)

            val completeItem = item(chunk.toLong())
            val completeBytes = ByteArray(chunk) { (it % 223).toByte() }
            val completePartial = firstStore.partialFileFor("transfer-complete", completeItem.id).apply {
                parentFile?.mkdirs()
                writeBytes(completeBytes)
            }
            firstStore.checkpoint("transfer-complete", completeItem, null, chunk.toLong())

            val discovered = store(root).discoverVerifiedCandidates()
            assertTrue(discovered.isEmpty())
            // These are valid local records, not corrupt data. They are simply
            // not resumable offsets and are left for a future completion path.
            assertTrue(tinyPartial.exists())
            assertTrue(completePartial.exists())
            assertNotNull(TransferResumeJournal(File(root, "journal")).load("transfer-zero", tinyItem.id))
            assertNotNull(TransferResumeJournal(File(root, "journal")).load("transfer-complete", completeItem.id))
        } finally {
            root.deleteRecursively()
        }
    }

}
