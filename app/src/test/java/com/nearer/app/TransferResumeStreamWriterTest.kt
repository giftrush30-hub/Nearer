package com.nearer.app

import java.io.ByteArrayInputStream
import java.io.File
import java.nio.file.Files
import java.security.MessageDigest
import org.junit.Assert.*
import org.junit.Test

class TransferResumeStreamWriterTest {
    private val chunk = TransferResumeCheckpoint.CHECKPOINT_GRANULARITY_BYTES.toInt()

    private fun item(size: Long) = TransferBundleItem(
        id = "stream-item",
        relativePath = "Videos/long-clip.mp4",
        displayName = "long-clip.mp4",
        sizeBytes = size,
        mimeType = "video/mp4"
    )

    private fun store(root: File) = TransferResumeStore(
        File(root, "partials"),
        TransferResumeJournal(File(root, "journal"))
    )

    private fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes)
            .joinToString("") { "%02x".format(it) }

    @Test fun writesAndVerifiesCompleteReceiverOwnedPartial() {
        val root = Files.createTempDirectory("nearer-resume-writer-complete").toFile()
        try {
            val data = ByteArray(chunk + 321) { (it % 239).toByte() }
            val result = TransferResumeStreamWriter(store(root)).write(
                "transfer-stream", item(data.size.toLong()), 123L, ByteArrayInputStream(data)
            )
            assertEquals(data.size.toLong(), result.bytesReceived)
            assertEquals(0L, result.resumedFromBytes)
            assertEquals(sha256(data), result.sha256)
            assertArrayEquals(data, result.partialFile.readBytes())
            assertNotNull(store(root).findValid("transfer-stream", item(data.size.toLong()), 123L))
        } finally {
            root.deleteRecursively()
        }
    }

    @Test fun resumesFromVerifiedCheckpointAndTrimsUncommittedTail() {
        val root = Files.createTempDirectory("nearer-resume-writer-restart").toFile()
        try {
            val data = ByteArray(chunk * 2 + 100) { (it % 251).toByte() }
            val selected = item(data.size.toLong())
            val store = store(root)
            val partial = store.partialFileFor("transfer-stream", selected.id).apply {
                parentFile?.mkdirs()
                writeBytes(data.copyOfRange(0, chunk + 50))
            }
            store.checkpoint("transfer-stream", selected, 123L, (chunk + 50).toLong())
            assertEquals((chunk + 50).toLong(), partial.length())

            val result = TransferResumeStreamWriter(store).write(
                "transfer-stream", selected, 123L,
                ByteArrayInputStream(data.copyOfRange(chunk, data.size))
            )
            assertEquals(chunk.toLong(), result.resumedFromBytes)
            assertEquals(data.size.toLong(), result.bytesReceived)
            assertEquals(sha256(data), result.sha256)
            assertArrayEquals(data, result.partialFile.readBytes())
        } finally {
            root.deleteRecursively()
        }
    }

    @Test fun interruptionKeepsOnlyLastDurableCheckpointReusable() {
        val root = Files.createTempDirectory("nearer-resume-writer-interrupt").toFile()
        try {
            val selected = item((chunk * 2L) + 100L)
            val firstAttempt = ByteArray(chunk + 75) { (it % 197).toByte() }
            val store = store(root)
            try {
                TransferResumeStreamWriter(store).write(
                    "transfer-stream", selected, null, ByteArrayInputStream(firstAttempt)
                )
                fail("Expected premature end of stream")
            } catch (_: IllegalStateException) { }

            val candidate = store.findValid("transfer-stream", selected, null)
            assertNotNull(candidate)
            assertEquals(chunk.toLong(), candidate!!.partialFile.length())
            assertEquals(chunk.toLong(), candidate.checkpoint.committedOffsetBytes)
        } finally {
            root.deleteRecursively()
        }
    }

    @Test fun rejectsStreamLongerThanDeclaredSize() {
        val root = Files.createTempDirectory("nearer-resume-writer-overflow").toFile()
        try {
            try {
                TransferResumeStreamWriter(store(root)).write(
                    "transfer-stream", item(3L), null, ByteArrayInputStream(byteArrayOf(1, 2, 3, 4))
                )
                fail("Expected excess stream bytes to be rejected")
            } catch (error: IllegalStateException) {
                assertTrue(error.message.orEmpty().contains("exceeded"))
            }
        } finally {
            root.deleteRecursively()
        }
    }
}
