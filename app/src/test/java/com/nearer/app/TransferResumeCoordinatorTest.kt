package com.nearer.app

import java.io.ByteArrayInputStream
import java.io.File
import java.nio.file.Files
import java.security.MessageDigest
import org.junit.Assert.*
import org.junit.Test

class TransferResumeCoordinatorTest {
    private val chunk = TransferResumeCheckpoint.CHECKPOINT_GRANULARITY_BYTES.toInt()
    private val peerId = "ABCDEF234567"

    private fun store(root: File) = TransferResumeStore(
        File(root, "partials"),
        TransferResumeJournal(File(root, "journal"))
    )

    private fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes)
            .joinToString("") { "%02x".format(it) }

    private fun item(size: Long) = TransferBundleItem(
        id = "item-1",
        relativePath = "Videos/clip.bin",
        displayName = "clip.bin",
        sizeBytes = size,
        mimeType = "application/octet-stream"
    )

    @Test fun discoversOnlyTheAuthenticatedSendersVerifiedPartialAndValidatesSourcePrefix() {
        val root = Files.createTempDirectory("nearer-resume-coordinator-offer").toFile()
        try {
            val store = store(root)
            val coordinator = TransferResumeCoordinator(store, TransferResumeStreamWriter(store))
            val bytes = ByteArray(chunk * 2) { (it % 239).toByte() }
            val item = item(bytes.size.toLong())
            val partial = store.partialFileFor("transfer-1", item.id).apply {
                parentFile?.mkdirs()
                writeBytes(bytes.copyOfRange(0, chunk))
            }
            store.checkpoint("transfer-1", item, 123L, chunk.toLong(), peerId)

            val offers = coordinator.offersForAuthenticatedSender(peerId, true, true)
            assertEquals(1, offers.size)
            assertEquals(chunk.toLong(), offers.single().proposal.committedOffsetBytes)
            assertTrue(coordinator.offersForAuthenticatedSender("BCDEFG234567", true, true).isEmpty())
            assertTrue(coordinator.offersForAuthenticatedSender(peerId, false, true).isEmpty())

            val source = File(root, "source.bin").apply { writeBytes(bytes); setLastModified(123L) }
            val decision = coordinator.validateOfferAtSender(
                offers.single().proposal, "transfer-1", item, source, source.lastModified(),
                "BCDEFG234567", true, true
            )
            assertEquals(TransferResumeNegotiation.Reason.RESUME_ACCEPTED, decision.reason)
            assertEquals(chunk.toLong(), decision.acceptedOffsetBytes)

            source.writeBytes(ByteArray(bytes.size) { 7 })
            source.setLastModified(123L) // Keep manifest metadata constant to isolate prefix-digest rejection.
            val changed = coordinator.validateOfferAtSender(
                offers.single().proposal, "transfer-1", item, source, source.lastModified(),
                "BCDEFG234567", true, true
            )
            assertEquals(TransferResumeNegotiation.Reason.OFFSET_INVALID, changed.reason)
            assertTrue("the verified receiver partial must remain intact", partial.exists())
        } finally {
            root.deleteRecursively()
        }
    }

    @Test fun receivesRemainingStreamVerifiesWholeDigestAndPublishesWithoutOverwriting() {
        val root = Files.createTempDirectory("nearer-resume-coordinator-complete").toFile()
        try {
            val store = store(root)
            val coordinator = TransferResumeCoordinator(store, TransferResumeStreamWriter(store))
            val bytes = ByteArray(chunk * 2 + 37) { (it % 251).toByte() }
            val item = item(bytes.size.toLong())
            val partial = store.partialFileFor("transfer-2", item.id).apply {
                parentFile?.mkdirs()
                writeBytes(bytes.copyOfRange(0, chunk))
            }
            store.checkpoint("transfer-2", item, 456L, chunk.toLong(), peerId)
            val destination = File(root, "received/clip.bin")
            val remainder = bytes.copyOfRange(chunk, bytes.size)

            val result = coordinator.receiveStreamAndFinalize(
                transferId = "transfer-2",
                item = item,
                sourceModifiedAtMillis = 456L,
                authenticatedSourcePeerId = peerId,
                acceptedOffsetBytes = chunk.toLong(),
                input = ByteArrayInputStream(remainder),
                destination = destination,
                expectedWholeFileSha256 = sha256(bytes)
            )
            assertTrue(destination.isFile)
            assertArrayEquals(bytes, destination.readBytes())
            assertFalse("private partial should be removed after publication", partial.exists())
            assertEquals(chunk.toLong(), result.resumedFromBytes)
            assertEquals(bytes.size.toLong(), result.totalBytes)
            assertEquals(sha256(bytes), result.sha256)

            try {
                coordinator.receiveStreamAndFinalize(
                    "transfer-2", item, 456L, peerId, chunk.toLong(),
                    ByteArrayInputStream(remainder), destination, sha256(bytes)
                )
                fail("Existing destination must never be replaced")
            } catch (_: IllegalArgumentException) { }
        } finally {
            root.deleteRecursively()
        }
    }

    @Test fun rejectsWrongWholeFileDigestAndDiscardsUntrustedCompletedPartial() {
        val root = Files.createTempDirectory("nearer-resume-coordinator-digest").toFile()
        try {
            val store = store(root)
            val coordinator = TransferResumeCoordinator(store, TransferResumeStreamWriter(store))
            val bytes = ByteArray(1024) { (it % 197).toByte() }
            val item = item(bytes.size.toLong())
            val destination = File(root, "received/clip.bin")
            try {
                coordinator.receiveStreamAndFinalize(
                    "transfer-3", item, 789L, peerId, 0L,
                    ByteArrayInputStream(bytes), destination, "0".repeat(64)
                )
                fail("Wrong whole-file digest must be rejected")
            } catch (_: SecurityException) { }
            assertFalse(destination.exists())
            assertFalse(store.partialFileFor("transfer-3", item.id).exists())
        } finally {
            root.deleteRecursively()
        }
    }

    @Test fun zeroByteFilesReceiveDurableEofCheckpointAndFinalize() {
        val root = Files.createTempDirectory("nearer-resume-coordinator-empty").toFile()
        try {
            val store = store(root)
            val coordinator = TransferResumeCoordinator(store, TransferResumeStreamWriter(store))
            val item = item(0L)
            val destination = File(root, "received/empty.bin")
            val result = coordinator.receiveStreamAndFinalize(
                "transfer-empty", item, 1000L, peerId, 0L,
                ByteArrayInputStream(byteArrayOf()), destination, sha256(byteArrayOf())
            )
            assertTrue(destination.isFile)
            assertEquals(0L, destination.length())
            assertEquals(0L, result.totalBytes)
            assertEquals(sha256(byteArrayOf()), result.sha256)
        } finally {
            root.deleteRecursively()
        }
    }

    @Test fun refusesUnverifiedPeerIdentityBeforeWritingAnyBytes() {
        val root = Files.createTempDirectory("nearer-resume-coordinator-peer").toFile()
        try {
            val store = store(root)
            val coordinator = TransferResumeCoordinator(store, TransferResumeStreamWriter(store))
            val bytes = byteArrayOf(1, 2, 3)
            try {
                coordinator.receiveStreamAndFinalize(
                    "transfer-peer", item(bytes.size.toLong()), 1L, "not-a-valid-peer", 0L,
                    ByteArrayInputStream(bytes), File(root, "received/file.bin"), sha256(bytes)
                )
                fail("Unauthenticated peer must be rejected")
            } catch (_: SecurityException) { }
            assertFalse(store.partialFileFor("transfer-peer", "item-1").exists())
        } finally {
            root.deleteRecursively()
        }
    }
}
