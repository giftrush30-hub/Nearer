package com.nearer.app

import java.io.File
import java.nio.file.Files
import java.security.MessageDigest
import org.junit.Assert.*
import org.junit.Test

class VerifiedTransferReceiptStoreTest {
    private val peer = "ABCDEF234567"

    private fun sha(bytes: ByteArray) =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    @Test
    fun restoredStoreRecognizesSamePeerAndExactVerifiedTransfer() {
        val root = Files.createTempDirectory("nearer-verified-receipt").toFile()
        try {
            val received = File(root, "received").apply { mkdirs() }
            val journal = File(root, "hypertransfer-completed")
            val bytes = ByteArray(4096) { (it % 239).toByte() }
            val completed = File(received, "received.bin").apply { writeBytes(bytes) }
            val item = TransferBundleItem("item-1", "clip.bin", "clip.bin", bytes.size.toLong(), "application/octet-stream")
            val metadata = TransferResumeStreamMetadataCodec.Metadata(
                101L, "transfer-1", item, null, sha(bytes)
            )
            VerifiedTransferReceiptStore(journal).record(peer, metadata, completed, sha(bytes))

            val restored = VerifiedTransferReceiptStore(journal)
            assertTrue(restored.containsVerified(peer, metadata))
            assertFalse(restored.containsVerified("BCDEFG234567", metadata))
            val changed = metadata.copy(wholeFileSha256 = "a".repeat(64))
            assertFalse(restored.containsVerified(peer, changed))
            val changedPath = metadata.copy(item = metadata.item.copy(relativePath = "different/clip.bin"))
            assertFalse(restored.containsVerified(peer, changedPath))
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun receiptIsNotAcceptedWhenPublishedFileDisappearsOrChangesSize() {
        val root = Files.createTempDirectory("nearer-verified-receipt-stale").toFile()
        try {
            val received = File(root, "received").apply { mkdirs() }
            val journal = File(root, "hypertransfer-completed")
            val bytes = byteArrayOf(1, 2, 3, 4, 5)
            val completed = File(received, "received.bin").apply { writeBytes(bytes) }
            val metadata = TransferResumeStreamMetadataCodec.Metadata(
                202L, "transfer-2",
                TransferBundleItem("item-2", "clip.bin", "clip.bin", bytes.size.toLong(), null),
                null, sha(bytes)
            )
            val store = VerifiedTransferReceiptStore(journal)
            store.record(peer, metadata, completed, sha(bytes))
            completed.writeBytes(byteArrayOf(9, 9))
            assertFalse(store.containsVerified(peer, metadata))
            completed.delete()
            assertFalse(store.containsVerified(peer, metadata))
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun receiptIsRejectedWhenPublishedBytesAreTamperedWithoutChangingSize() {
        val root = Files.createTempDirectory("nearer-verified-receipt-tamper").toFile()
        try {
            val received = File(root, "received").apply { mkdirs() }
            val journal = File(root, "hypertransfer-completed")
            val original = byteArrayOf(1, 2, 3, 4, 5)
            val completed = File(received, "received.bin").apply { writeBytes(original) }
            val metadata = TransferResumeStreamMetadataCodec.Metadata(
                252L, "transfer-tamper",
                TransferBundleItem("item-tamper", "clip.bin", "clip.bin", original.size.toLong(), null),
                null, sha(original)
            )
            val store = VerifiedTransferReceiptStore(journal)
            store.record(peer, metadata, completed, sha(original))
            completed.writeBytes(byteArrayOf(9, 9, 9, 9, 9))
            assertFalse(store.containsVerified(peer, metadata))
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun refusesToJournalFilesOutsideThePrivateReceivedDirectory() {
        val root = Files.createTempDirectory("nearer-verified-receipt-path").toFile()
        try {
            val outside = File(root, "outside.bin").apply { writeBytes(byteArrayOf(1, 2, 3)) }
            val metadata = TransferResumeStreamMetadataCodec.Metadata(
                404L, "transfer-outside",
                TransferBundleItem("item-outside", "clip.bin", "clip.bin", 3L, null),
                null, sha(byteArrayOf(1, 2, 3))
            )
            try {
                VerifiedTransferReceiptStore(File(root, "hypertransfer-completed"))
                    .record(peer, metadata, outside, sha(byteArrayOf(1, 2, 3)))
                fail("A receipt must not reference files outside received/")
            } catch (_: IllegalArgumentException) { }
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun refusesToRecordDigestThatWasNotVerifiedForThePublishedFile() {
        val root = Files.createTempDirectory("nearer-verified-receipt-bad").toFile()
        try {
            val received = File(root, "received").apply { mkdirs() }
            val completed = File(received, "received.bin").apply { writeBytes(byteArrayOf(1, 2, 3)) }
            val metadata = TransferResumeStreamMetadataCodec.Metadata(
                303L, "transfer-3",
                TransferBundleItem("item-3", "clip.bin", "clip.bin", 3L, null),
                null, "a".repeat(64)
            )
            try {
                VerifiedTransferReceiptStore(File(root, "hypertransfer-completed"))
                    .record(peer, metadata, completed, "b".repeat(64))
                fail("Mismatched verified digest must not be journaled")
            } catch (_: IllegalArgumentException) { }
        } finally {
            root.deleteRecursively()
        }
    }
}
