package com.nearer.app

import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FastShareTempFileCleanupTest {
    @Test fun removesOnlyAbandonedPrivateUploadParts() {
        val folder = Files.createTempDirectory("nearer-upload-cleanup").toFile()
        try {
            val first = File(folder, ".nearer-upload-one.part").apply { writeText("partial") }
            val second = File(folder, ".nearer-upload-two.part").apply { writeText("partial") }
            val ordinary = File(folder, "important.part").apply { writeText("keep") }
            val wrongSuffix = File(folder, ".nearer-upload-three.tmp").apply { writeText("keep") }
            val directory = File(folder, ".nearer-upload-folder.part").apply { mkdirs() }

            assertEquals(2, FastShareTempFileCleanup.removeAbandoned(folder))
            assertFalse(first.exists())
            assertFalse(second.exists())
            assertTrue(ordinary.exists())
            assertTrue(wrongSuffix.exists())
            assertTrue(directory.exists())
        } finally { folder.deleteRecursively() }
    }

    @Test fun missingDirectoryIsSafe() {
        val folder = File(Files.createTempDirectory("nearer-upload-missing").toFile(), "not-created")
        assertEquals(0, FastShareTempFileCleanup.removeAbandoned(folder))
        folder.parentFile?.deleteRecursively()
    }
}
