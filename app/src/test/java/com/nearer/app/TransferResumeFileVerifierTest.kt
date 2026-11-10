package com.nearer.app

import java.io.File
import java.nio.file.Files
import java.security.MessageDigest
import org.junit.Assert.*
import org.junit.Test

class TransferResumeFileVerifierTest {
    private val boundary = TransferResumeCheckpoint.CHECKPOINT_GRANULARITY_BYTES.toInt()

    private fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes)
            .joinToString("") { "%02x".format(it) }

    private fun checkpoint(prefix: ByteArray, expectedSize: Long = prefix.size.toLong()) =
        TransferResumeCheckpoint(
            "transfer", "item", "file.bin", expectedSize, 1L,
            prefix.size.toLong(), sha256(prefix)
        )

    @Test fun acceptsMatchingPrefixAndTrimsUncommittedTail() {
        val directory = Files.createTempDirectory("nearer-resume-verify").toFile()
        try {
            val committed = ByteArray(boundary) { (it % 251).toByte() }
            val file = File(directory, "partial.bin").apply {
                writeBytes(committed + "uncommitted-tail".toByteArray())
            }
            assertTrue(TransferResumeFileVerifier.verifyAndTrim(file, checkpoint(committed, committed.size.toLong() + 100L)))
            assertArrayEquals(committed, file.readBytes())
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test fun rejectsChangedPrefixWithoutTrimmingFile() {
        val directory = Files.createTempDirectory("nearer-resume-mismatch").toFile()
        try {
            val original = ByteArray(boundary) { (it % 239).toByte() }
            val changed = original.copyOf().also { it[boundary / 2] = (it[boundary / 2] + 1).toByte() }
            val file = File(directory, "partial.bin").apply {
                writeBytes(changed + "tail".toByteArray())
            }
            val before = file.readBytes()
            assertFalse(TransferResumeFileVerifier.verifyAndTrim(file, checkpoint(original, original.size.toLong() + 100L)))
            assertArrayEquals(before, file.readBytes())
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test fun rejectsShortOrOversizedPartialFiles() {
        val directory = Files.createTempDirectory("nearer-resume-size").toFile()
        try {
            val prefix = ByteArray(boundary) { 7 }
            val file = File(directory, "partial.bin").apply { writeText("tiny") }
            assertFalse(TransferResumeFileVerifier.verifyAndTrim(file, checkpoint(prefix, prefix.size.toLong() + 100L)))
            file.writeBytes(prefix + byteArrayOf(1))
            assertFalse(TransferResumeFileVerifier.verifyAndTrim(file, checkpoint(prefix)))
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test fun verifiesZeroOffsetUsingEmptyPrefixDigest() {
        val directory = Files.createTempDirectory("nearer-resume-empty").toFile()
        try {
            val empty = ByteArray(0)
            val file = File(directory, "partial.bin").apply { writeText("not-yet-committed") }
            assertTrue(TransferResumeFileVerifier.verifyAndTrim(file, checkpoint(empty, 50L)))
            assertEquals(0L, file.length())
        } finally {
            directory.deleteRecursively()
        }
    }
}
