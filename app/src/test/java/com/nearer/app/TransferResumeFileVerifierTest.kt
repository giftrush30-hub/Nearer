package com.nearer.app

import java.io.File
import java.nio.file.Files
import java.security.MessageDigest
import org.junit.Assert.*
import org.junit.Test

class TransferResumeFileVerifierTest {
    private fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes)
            .joinToString("") { "%02x".format(it) }

    private fun checkpoint(bytes: ByteArray, expectedSize: Long = bytes.size.toLong()) =
        TransferResumeCheckpoint(
            "transfer", "item", "file.bin", expectedSize, 1L,
            bytes.size.toLong(), sha256(bytes)
        )

    @Test fun acceptsMatchingPrefixAndTrimsUncommittedTail() {
        val directory = Files.createTempDirectory("nearer-resume-verify").toFile()
        try {
            val committed = "durable-prefix".toByteArray()
            val file = File(directory, "partial.bin").apply {
                writeBytes(committed + "uncommitted-tail".toByteArray())
            }
            assertTrue(TransferResumeFileVerifier.verifyAndTrim(file, checkpoint(committed, 100L)))
            assertArrayEquals(committed, file.readBytes())
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test fun rejectsChangedPrefixWithoutTrimmingFile() {
        val directory = Files.createTempDirectory("nearer-resume-mismatch").toFile()
        try {
            val file = File(directory, "partial.bin").apply { writeText("changed-prefix-and-tail") }
            val before = file.readBytes()
            val cp = checkpoint("original-prefix".toByteArray(), 100L)
            assertFalse(TransferResumeFileVerifier.verifyAndTrim(file, cp))
            assertArrayEquals(before, file.readBytes())
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test fun rejectsShortOrOversizedPartialFiles() {
        val directory = Files.createTempDirectory("nearer-resume-size").toFile()
        try {
            val file = File(directory, "partial.bin").apply { writeText("tiny") }
            assertFalse(TransferResumeFileVerifier.verifyAndTrim(file, checkpoint("longer-prefix".toByteArray(), 100L)))
            file.writeText("x".repeat(20))
            val cp = checkpoint("x".repeat(8).toByteArray(), 10L)
            assertFalse(TransferResumeFileVerifier.verifyAndTrim(file, cp))
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
