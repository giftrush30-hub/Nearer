package com.nearer.app

import java.io.File
import java.nio.file.Files
import java.security.MessageDigest
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
            firstStore.checkpoint("transfer-a", firstItem, 100L, chunk.toLong(), "ABCDEF234567")

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
            firstStore.checkpoint("transfer-b", secondItem, 200L, chunk.toLong(), "BCDEFG234567")

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
            firstStore.checkpoint("transfer-valid", validItem, 100L, chunk.toLong(), "ABCDEF234567")

            val corruptItem = validItem.copy(id = "corrupt-item", relativePath = "Videos/corrupt.mp4", displayName = "corrupt.mp4")
            val corruptPartial = firstStore.partialFileFor("transfer-corrupt", corruptItem.id).apply {
                parentFile?.mkdirs()
                writeBytes(ByteArray(chunk) { 7 })
            }
            firstStore.checkpoint("transfer-corrupt", corruptItem, 200L, chunk.toLong(), "BCDEFG234567")
            corruptPartial.writeBytes(ByteArray(chunk) { 8 })

            val missingItem = validItem.copy(id = "missing-item", relativePath = "Videos/missing.mp4", displayName = "missing.mp4")
            val missingPartial = firstStore.partialFileFor("transfer-missing", missingItem.id).apply {
                parentFile?.mkdirs()
                writeBytes(ByteArray(chunk) { 9 })
            }
            firstStore.checkpoint("transfer-missing", missingItem, 300L, chunk.toLong(), "CDEFGH234567")
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


    @Test fun finalizesOnlyCompleteDigestVerifiedFilesWithAtomicMove() {
        val root = Files.createTempDirectory("nearer-resume-finalize").toFile()
        try {
            val store = store(root)
            val selected = item(chunk.toLong())
            val bytes = ByteArray(chunk) { (it % 233).toByte() }
            val partial = store.partialFileFor("transfer-final", selected.id).apply {
                parentFile?.mkdirs()
                writeBytes(bytes)
            }
            store.checkpoint("transfer-final", selected, 321L, selected.sizeBytes)
            val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
                .joinToString("") { "%02x".format(it) }
            val destination = File(root, "received/clip.mp4")

            val finalized = store.finalizeVerifiedComplete(
                "transfer-final", selected, 321L, destination, digest
            )

            assertEquals(destination.absolutePath, finalized.absolutePath)
            assertArrayEquals(bytes, destination.readBytes())
            assertFalse("partial must be moved, not copied", partial.exists())
            assertNull(TransferResumeJournal(File(root, "journal")).load("transfer-final", selected.id))
        } finally {
            root.deleteRecursively()
        }
    }

    @Test fun refusesIncompleteOrDigestMismatchedFinalizationWithoutPublishing() {
        val root = Files.createTempDirectory("nearer-resume-finalize-reject").toFile()
        try {
            val store = store(root)
            val selected = item(chunk.toLong() * 2L)
            val partial = store.partialFileFor("transfer-final", selected.id).apply {
                parentFile?.mkdirs()
                writeBytes(ByteArray(chunk) { 11 })
            }
            store.checkpoint("transfer-final", selected, null, chunk.toLong())
            val destination = File(root, "received/clip.mp4")
            val arbitraryDigest = "0".repeat(64)

            try {
                store.finalizeVerifiedComplete("transfer-final", selected, null, destination, arbitraryDigest)
                fail("Incomplete partial must not be published")
            } catch (_: IllegalArgumentException) {
                // A non-EOF checkpoint cannot be finalized.
            }
            assertFalse(destination.exists())
            assertTrue(partial.exists())

            val completeItem = item(chunk.toLong())
            val completePartial = store.partialFileFor("transfer-complete", completeItem.id).apply {
                parentFile?.mkdirs()
                writeBytes(ByteArray(chunk) { 12 })
            }
            store.checkpoint("transfer-complete", completeItem, null, completeItem.sizeBytes)
            try {
                store.finalizeVerifiedComplete(
                    "transfer-complete", completeItem, null,
                    File(root, "received/wrong.mp4"), arbitraryDigest
                )
                fail("Wrong whole-file digest must be rejected")
            } catch (_: IllegalArgumentException) { }
            assertTrue(completePartial.exists())
            assertFalse(File(root, "received/wrong.mp4").exists())
        } finally {
            root.deleteRecursively()
        }
    }

    @Test fun neverOverwritesAnExistingFinalDestination() {
        val root = Files.createTempDirectory("nearer-resume-finalize-existing").toFile()
        try {
            val store = store(root)
            val selected = item(1024L)
            val bytes = ByteArray(1024) { (it % 199).toByte() }
            val partial = store.partialFileFor("transfer-existing", selected.id).apply {
                parentFile?.mkdirs()
                writeBytes(bytes)
            }
            store.checkpoint("transfer-existing", selected, null, selected.sizeBytes)
            val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
                .joinToString("") { "%02x".format(it) }
            val destination = File(root, "received/clip.mp4").apply {
                parentFile?.mkdirs()
                writeText("keep existing file")
            }

            try {
                store.finalizeVerifiedComplete(
                    "transfer-existing", selected, null, destination, digest
                )
                fail("Existing destination must not be overwritten")
            } catch (_: IllegalArgumentException) { }
            assertEquals("keep existing file", destination.readText())
            assertTrue(partial.exists())
        } finally {
            root.deleteRecursively()
        }
    }


    @Test fun restartDiscoveryRequiresAuthenticatedStablePeerBinding() {
        val root = Files.createTempDirectory("nearer-resume-peer-binding").toFile()
        try {
            val store = store(root)
            val selected = item()
            val bytes = ByteArray(chunk) { (it % 211).toByte() }
            val partial = store.partialFileFor("transfer-peer", selected.id).apply {
                parentFile?.mkdirs()
                writeBytes(bytes)
            }
            // Legacy/unbound checkpoint remains readable for migration but is not
            // exposed as a restart candidate because endpoint IDs are ephemeral.
            store.checkpoint("transfer-peer", selected, 100L, chunk.toLong())
            assertTrue(store(root).discoverVerifiedCandidates().isEmpty())

            store.discard("transfer-peer", selected.id)
            partial.writeBytes(bytes)
            store.checkpoint("transfer-peer", selected, 100L, chunk.toLong(), "ABCDEF234567")
            val restarted = store(root)
            assertEquals(1, restarted.discoverVerifiedCandidates().size)
            assertNull(restarted.findValid("transfer-peer", selected, 100L, "BCDEFG234567"))
            assertNotNull(restarted.findValid("transfer-peer", selected, 100L, "ABCDEF234567"))
        } finally {
            root.deleteRecursively()
        }
    }

    @Test fun checkpointCodecReadsLegacyVersionOneAndRoundTripsPeerBinding() {
        val legacy = java.io.ByteArrayOutputStream().also { bytes ->
            java.io.DataOutputStream(bytes).use { data ->
                data.writeInt(0x4E524331)
                data.writeInt(1)
                fun write(value: String) {
                    val encoded = value.toByteArray(Charsets.UTF_8)
                    data.writeInt(encoded.size)
                    data.write(encoded)
                }
                write("legacy-transfer")
                write("item-1")
                write("Videos/clip.mp4")
                data.writeLong(chunk.toLong() * 2L)
                data.writeBoolean(false)
                data.writeLong(chunk.toLong())
                write("a".repeat(64))
            }
        }.toByteArray()
        val decodedLegacy = TransferResumeCheckpointCodec.decode(legacy)
        assertNull(decodedLegacy.sourcePeerId)

        val root = Files.createTempDirectory("nearer-resume-peer-codec").toFile()
        try {
            val store = store(root)
            val selected = item()
            val partial = store.partialFileFor("bound-transfer", selected.id).apply {
                parentFile?.mkdirs()
                writeBytes(ByteArray(chunk) { 3 })
            }
            val checkpoint = store.checkpoint(
                "bound-transfer", selected, 456L, chunk.toLong(), "ABCDEF234567"
            )
            val encoded = TransferResumeCheckpointCodec.encode(checkpoint)
            val decoded = TransferResumeCheckpointCodec.decode(encoded)
            assertEquals("ABCDEF234567", decoded.sourcePeerId)
            assertEquals(checkpoint, decoded)
            assertTrue(partial.exists())
        } finally {
            root.deleteRecursively()
        }
    }

}
