package com.nearer.app

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FastShareTempFileCleanupTest {
    @Test fun removesOnlyPrivateUploadStagingFiles() {
        val directory = createTempDir(prefix = "nearer-cleanup-")
        try {
            val abandoned = File(directory, ".nearer-upload-old.part").apply { writeText("partial") }
            val ordinary = File(directory, "holiday.jpg").apply { writeText("keep") }
            val unrelatedPart = File(directory, ".other-upload-old.part").apply { writeText("keep") }
            val similarlyNamed = File(directory, ".nearer-upload-final.jpg").apply { writeText("keep") }

            assertEquals(1, FastShareTempFileCleanup.removeAbandoned(directory))
            assertFalse(abandoned.exists())
            assertTrue(ordinary.exists())
            assertTrue(unrelatedPart.exists())
            assertTrue(similarlyNamed.exists())
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test fun missingDirectoryIsSafeAndDoesNothing() {
        val directory = File(createTempDir(prefix = "nearer-cleanup-parent-"), "missing")
        assertEquals(0, FastShareTempFileCleanup.removeAbandoned(directory))
        directory.parentFile?.deleteRecursively()
    }

    @Test fun emptyDirectoryReturnsZero() {
        val directory = createTempDir(prefix = "nearer-cleanup-empty-")
        try {
            assertEquals(0, FastShareTempFileCleanup.removeAbandoned(directory))
        } finally {
            directory.deleteRecursively()
        }
    }
}
