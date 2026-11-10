package com.nearer.app

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FastShareUploadDestinationTest {
    @Test fun keepsExtensionAndSkipsExistingNumberedNames() {
        val directory = createTempDir(prefix = "nearer-upload-destination-")
        try {
            File(directory, "video.mp4").writeText("one")
            File(directory, "video (2).mp4").writeText("two")

            val next = FastShareUploadDestination.nextAvailable(directory, "video.mp4")

            assertEquals("video (3).mp4", next.name)
            assertFalse(next.exists())
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test fun treatsDotfilesAsNamesRatherThanExtensions() {
        val directory = createTempDir(prefix = "nearer-upload-destination-")
        try {
            File(directory, ".profile").writeText("one")

            val next = FastShareUploadDestination.nextAvailable(directory, ".profile")

            assertEquals(".profile (2)", next.name)
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test fun leavesUnusedNameUnchanged() {
        val directory = createTempDir(prefix = "nearer-upload-destination-")
        try {
            val next = FastShareUploadDestination.nextAvailable(directory, "notes.txt")
            assertEquals("notes.txt", next.name)
            assertTrue(!next.exists())
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test fun rejectsPathSeparatorsAndDotTraversalSegments() {
        val directory = createTempDir(prefix = "nearer-upload-destination-")
        try {
            listOf("../escape.txt", "folder/file.txt", "folder\\file.txt", ".", "..", "bad\u0000name").forEach { unsafe ->
                try {
                    FastShareUploadDestination.nextAvailable(directory, unsafe)
                    throw AssertionError("Expected unsafe filename to be rejected: $unsafe")
                } catch (_: IllegalArgumentException) {
                    // Expected: this helper must remain safe even if a caller
                    // accidentally skips the upstream filename sanitizer.
                }
            }
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsBlankSafeName() {
        FastShareUploadDestination.nextAvailable(createTempDir(), " ")
    }
}
