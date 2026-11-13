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

            val opened = coordinator.openValidatedSourceAtOffset(
                offers.single().proposal, "transfer-1", item, source, source.lastModified(),
                "BCDEFG234567", true, true
            ) ?: throw AssertionError("A verified source should open at the accepted offset")
            // Simulate the source growing after the descriptor is opened. The
            // bounded stream must expose only the declared transfer remainder.
            source.appendBytes(byteArrayOf(88, 89))
            opened.use { stream ->
                assertEquals(chunk.toLong(), stream.acceptedOffsetBytes)
                assertEquals((bytes.size - chunk).toLong(), stream.remainingBytes)
                assertEquals(bytes.size.toLong(), stream.totalBytes)
                assertArrayEquals(bytes.copyOfRange(chunk, bytes.size), stream.input.readBytes())
                assertEquals("the source stream must stop at the declared size", -1, stream.input.read())
            }

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

    @Test fun opensVerifiedResumeWhenProviderHasNoModificationTimestamp() {
        val root = Files.createTempDirectory("nearer-resume-no-mtime").toFile()
        try {
            val store = store(root)
            val coordinator = TransferResumeCoordinator(store, TransferResumeStreamWriter(store))
            val bytes = ByteArray(chunk * 2 + 19) { (it % 211).toByte() }
            val item = item(bytes.size.toLong())
            val partial = store.partialFileFor("transfer-no-mtime", item.id).apply {
                parentFile?.mkdirs()
                writeBytes(bytes.copyOfRange(0, chunk))
            }
            store.checkpoint("transfer-no-mtime", item, null, chunk.toLong(), peerId)
            val offer = coordinator.offersForAuthenticatedSender(peerId, true, true).single()
            val source = File(root, "source-no-mtime.bin").apply { writeBytes(bytes) }
            val opened = coordinator.openValidatedSourceAtOffset(
                offer.proposal, "transfer-no-mtime", item, source, null,
                "BCDEFG234567", true, true
            ) ?: throw AssertionError("A size- and prefix-verified source should open without mtime metadata")
            opened.use { stream ->
                assertEquals(chunk.toLong(), stream.acceptedOffsetBytes)
                assertArrayEquals(bytes.copyOfRange(chunk, bytes.size), stream.input.readBytes())
            }
            assertTrue(partial.isFile)
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
                fail("A resume offset without its checkpoint must be rejected")
            } catch (_: SecurityException) { }

            try {
                coordinator.receiveStreamAndFinalize(
                    "transfer-3", item, 456L, peerId, 0L,
                    ByteArrayInputStream(bytes), destination, sha256(bytes)
                )
                fail("Existing destination must never be replaced")
            } catch (_: IllegalArgumentException) { }
        } finally {
            root.deleteRecursively()
        }
    }

    @Test fun rejectsTransportOffsetThatDiffersFromLocalCheckpointBeforeMutatingIt() {
        val root = Files.createTempDirectory("nearer-resume-coordinator-offset").toFile()
        try {
            val store = store(root)
            val coordinator = TransferResumeCoordinator(store, TransferResumeStreamWriter(store))
            val bytes = ByteArray(chunk * 3) { (it % 223).toByte() }
            val item = item(bytes.size.toLong())
            val partial = store.partialFileFor("transfer-offset", item.id).apply {
                parentFile?.mkdirs()
                writeBytes(bytes.copyOfRange(0, chunk * 2))
            }
            store.checkpoint("transfer-offset", item, 321L, (chunk * 2).toLong(), peerId)
            try {
                coordinator.receiveStreamAndFinalize(
                    "transfer-offset", item, 321L, peerId, chunk.toLong(),
                    ByteArrayInputStream(bytes.copyOfRange(chunk, bytes.size)), File(root, "received/file.bin"), sha256(bytes)
                )
                fail("Offset disagreement must be rejected before writing")
            } catch (_: SecurityException) { }
            assertTrue("existing verified partial must not be deleted or overwritten", partial.isFile)
            assertEquals((chunk * 2).toLong(), partial.length())
            assertEquals((chunk * 2).toLong(), store.findValid("transfer-offset", item, 321L, peerId)!!.checkpoint.committedOffsetBytes)
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

    @Test fun rejectsConcurrentReceiveForTheSameTransferItemWithoutTouchingTheActiveWrite() {
        val root = Files.createTempDirectory("nearer-resume-concurrent-receive").toFile()
        val entered = java.util.concurrent.CountDownLatch(1)
        val release = java.util.concurrent.CountDownLatch(1)
        val executor = java.util.concurrent.Executors.newSingleThreadExecutor()
        try {
            val store = store(root)
            val coordinator = TransferResumeCoordinator(store, TransferResumeStreamWriter(store))
            val bytes = ByteArray(2048) { (it % 199).toByte() }
            val item = item(bytes.size.toLong())
            val destination = File(root, "received/concurrent.bin")
            val blockingInput = object : java.io.InputStream() {
                private var offset = 0
                private var blocked = false
                override fun read(): Int {
                    val buffer = ByteArray(1)
                    val count = read(buffer, 0, 1)
                    return if (count < 0) -1 else buffer[0].toInt() and 0xff
                }
                override fun read(buffer: ByteArray, off: Int, len: Int): Int {
                    if (!blocked) {
                        blocked = true
                        entered.countDown()
                        if (!release.await(5, java.util.concurrent.TimeUnit.SECONDS)) {
                            throw java.io.IOException("Timed out waiting for test release")
                        }
                    }
                    if (offset >= bytes.size) return -1
                    val count = minOf(len, bytes.size - offset)
                    System.arraycopy(bytes, offset, buffer, off, count)
                    offset += count
                    return count
                }
            }

            val first = executor.submit<TransferResumeCoordinator.ReceiveResult> {
                coordinator.receiveStreamAndFinalize(
                    "transfer-concurrent", item, 123L, peerId, 0L,
                    blockingInput, destination, sha256(bytes)
                )
            }
            assertTrue("first receive should enter its stream read", entered.await(5, java.util.concurrent.TimeUnit.SECONDS))
            try {
                coordinator.receiveStreamAndFinalize(
                    "transfer-concurrent", item, 123L, peerId, 0L,
                    ByteArrayInputStream(bytes), destination, sha256(bytes)
                )
                fail("a duplicate concurrent receive must be rejected")
            } catch (_: IllegalStateException) { }
            release.countDown()
            val completed = first.get(5, java.util.concurrent.TimeUnit.SECONDS)
            assertArrayEquals(bytes, completed.completedFile.readBytes())
            assertEquals(bytes.size.toLong(), completed.totalBytes)
        } finally {
            release.countDown()
            executor.shutdownNow()
            root.deleteRecursively()
        }
    }

}
