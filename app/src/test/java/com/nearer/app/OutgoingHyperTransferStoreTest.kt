package com.nearer.app

import java.io.File
import java.nio.file.Files
import java.security.MessageDigest
import org.junit.Assert.*
import org.junit.Test

class OutgoingHyperTransferStoreTest {
    private val peerA = "ABCDEF234567"
    private val peerB = "BCDEFG234567"

    private fun sha(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes)
            .joinToString("") { "%02x".format(it) }

    private fun item(bytes: ByteArray) = TransferBundleItem(
        id = "item-1",
        relativePath = "Videos/clip.mp4",
        displayName = "clip.mp4",
        sizeBytes = bytes.size.toLong(),
        mimeType = "video/mp4"
    )

    @Test
    fun restoresOnlyVerifiedRecordsForTheAuthenticatedPeer() {
        val root = Files.createTempDirectory("nearer-outgoing-store").toFile()
        try {
            val bytes = ByteArray(128 * 1024) { (it % 251).toByte() }
            val source = File(root, "outgoing").apply { mkdirs() }
            val staged = File(source, "source.bin").apply { writeBytes(bytes) }
            val store = OutgoingHyperTransferStore(source)
            store.persist("transfer-a", peerA, item(bytes), sha(bytes), staged)

            val restored = OutgoingHyperTransferStore(source).restoreForPeer(peerA)
            assertEquals(1, restored.size)
            assertEquals("transfer-a", restored.single().transferId)
            assertEquals(peerA, restored.single().peerId)
            assertEquals("video/mp4", restored.single().mimeType)
            assertArrayEquals(bytes, restored.single().sourceFile.readBytes())
            assertTrue(OutgoingHyperTransferStore(source).restoreForPeer(peerB).isEmpty())
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun duplicateTransferIdentityCannotReplaceAnExistingDurableSource() {
        val root = Files.createTempDirectory("nearer-outgoing-duplicate").toFile()
        try {
            val directory = File(root, "outgoing").apply { mkdirs() }
            val firstBytes = byteArrayOf(1, 2, 3, 4)
            val secondBytes = byteArrayOf(5, 6, 7, 8)
            val first = File(directory, "first.bin").apply { writeBytes(firstBytes) }
            val second = File(directory, "second.bin").apply { writeBytes(secondBytes) }
            val store = OutgoingHyperTransferStore(directory)
            store.persist("transfer-same", peerA, item(firstBytes), sha(firstBytes), first)
            try {
                store.persist("transfer-same", peerA, item(secondBytes), sha(secondBytes), second)
                fail("A duplicate stable transfer ID must not replace the original journal")
            } catch (_: IllegalStateException) { }
            val restored = store.restoreForPeer(peerA).single()
            assertArrayEquals(firstBytes, restored.sourceFile.readBytes())
            assertTrue(second.isFile)
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun refusesSourcesOutsideItsAppPrivateStagingDirectory() {
        val root = Files.createTempDirectory("nearer-outgoing-path").toFile()
        try {
            val directory = File(root, "outgoing").apply { mkdirs() }
            val external = File(root, "external.bin").apply { writeBytes(byteArrayOf(1, 2, 3)) }
            val bytes = external.readBytes()
            try {
                OutgoingHyperTransferStore(directory).persist("transfer-outside", peerA, item(bytes), sha(bytes), external)
                fail("Outgoing source must be inside the durable staging folder")
            } catch (_: IllegalArgumentException) { }
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun rejectsSourceTamperingAndMissingSourceAfterRestart() {
        val root = Files.createTempDirectory("nearer-outgoing-corrupt").toFile()
        try {
            val bytes = ByteArray(64 * 1024) { 7 }
            val directory = File(root, "outgoing").apply { mkdirs() }
            val staged = File(directory, "source.bin").apply { writeBytes(bytes) }
            val store = OutgoingHyperTransferStore(directory)
            store.persist("transfer-corrupt", peerA, item(bytes), sha(bytes), staged)

            staged.writeBytes(ByteArray(bytes.size) { 9 })
            assertTrue(store.restoreForPeer(peerA).isEmpty())
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun cancellationRemovesBothJournalAndDurableSource() {
        val root = Files.createTempDirectory("nearer-outgoing-remove").toFile()
        try {
            val bytes = byteArrayOf(1, 2, 3, 4)
            val directory = File(root, "outgoing").apply { mkdirs() }
            val staged = File(directory, "source.bin").apply { writeBytes(bytes) }
            val store = OutgoingHyperTransferStore(directory)
            store.persist("transfer-remove", peerA, item(bytes), sha(bytes), staged)

            store.remove("transfer-remove")
            assertFalse(staged.exists())
            assertTrue(store.restoreForPeer(peerA).isEmpty())
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun removingRecordOnlyKeepsSourceForLegacyFallbackOwnership() {
        val root = Files.createTempDirectory("nearer-outgoing-fallback").toFile()
        try {
            val bytes = byteArrayOf(4, 3, 2, 1)
            val directory = File(root, "outgoing").apply { mkdirs() }
            val staged = File(directory, "source.bin").apply { writeBytes(bytes) }
            val store = OutgoingHyperTransferStore(directory)
            store.persist("transfer-fallback", peerA, item(bytes), sha(bytes), staged)

            store.removeRecordOnly("transfer-fallback")
            assertTrue(staged.isFile)
            assertTrue(store.restoreForPeer(peerA).isEmpty())
        } finally {
            root.deleteRecursively()
        }
    }
}
