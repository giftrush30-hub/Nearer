package com.nearer.app

import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream
import java.nio.file.Files

class FileTransferIntegrityTest {
    @Test fun commitsVerifiedFileAndPreservesContents() {
        val directory = Files.createTempDirectory("nearer-integrity").toFile()
        try {
            val bytes = "NeaRer offline file".toByteArray()
            val result = FileTransferIntegrity.copyAndVerify(ByteArrayInputStream(bytes), directory, "note.txt", bytes.size.toLong(), FileTransferIntegrity.sha256(bytes))
            assertTrue(result.isFile)
            assertEquals("NeaRer offline file", result.readText())
            assertFalse(directory.listFiles().orEmpty().any { it.name.endsWith(".part") })
        } finally { directory.deleteRecursively() }
    }

    @Test fun rejectsWrongChecksumWithoutLeavingPartialFile() {
        val directory = Files.createTempDirectory("nearer-integrity").toFile()
        try {
            val bytes = "original".toByteArray()
            try {
                FileTransferIntegrity.copyAndVerify(ByteArrayInputStream(bytes), directory, "note.txt", bytes.size.toLong(), "0".repeat(64))
                fail("Expected checksum mismatch")
            } catch (expected: java.io.IOException) { assertTrue(expected.message!!.contains("checksum")) }
            assertTrue(directory.listFiles().orEmpty().isEmpty())
        } finally { directory.deleteRecursively() }
    }

    @Test fun rejectsTruncatedAndOversizedFilesWithoutPublishingThem() {
        val directory = Files.createTempDirectory("nearer-integrity").toFile()
        try {
            try {
                FileTransferIntegrity.copyAndVerify(ByteArrayInputStream(byteArrayOf(1, 2)), directory, "short.bin", 3)
                fail("Expected truncated-file rejection")
            } catch (expected: java.io.IOException) { assertTrue(expected.message!!.contains("size")) }
            try {
                FileTransferIntegrity.copyAndVerify(ByteArrayInputStream(byteArrayOf(1, 2, 3)), directory, "long.bin", 2)
                fail("Expected oversized-file rejection")
            } catch (expected: java.io.IOException) { assertTrue(expected.message!!.contains("exceeded")) }
            assertTrue(directory.listFiles().orEmpty().isEmpty())
        } finally { directory.deleteRecursively() }
    }
    @Test fun streamingChecksumMatchesByteArrayChecksumAndChecksDeclaredLength() {
        val bytes = "large-file-checksum-path".toByteArray()
        val streamed = FileTransferIntegrity.sha256(ByteArrayInputStream(bytes), bytes.size.toLong())
        assertEquals(FileTransferIntegrity.sha256(bytes), streamed)
        try {
            FileTransferIntegrity.sha256(ByteArrayInputStream(bytes), bytes.size.toLong() + 1L)
            fail("Expected changed-size rejection")
        } catch (expected: java.io.IOException) {
            assertTrue(expected.message!!.contains("size changed"))
        }
    }

}
