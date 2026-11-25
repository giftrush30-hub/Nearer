package com.nearer.app

import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StudioExportFilesTest {
    private fun mp4LikeFile(file: File) {
        file.parentFile?.mkdirs()
        file.writeBytes(byteArrayOf(
            0, 0, 0, 24, 'f'.code.toByte(), 't'.code.toByte(), 'y'.code.toByte(), 'p'.code.toByte(),
            'i'.code.toByte(), 's'.code.toByte(), 'o'.code.toByte(), 'm'.code.toByte(),
            0, 0, 0, 0
        ))
    }

    @Test fun publishesOnlyNonEmptyMp4AndRemovesPendingName() {
        val directory = Files.createTempDirectory("nearer-export-test").toFile()
        try {
            val pending = StudioExportFiles.pendingFile(directory, "test-1")
            val destination = StudioExportFiles.finalFile(directory, "test-1")
            mp4LikeFile(pending)

            assertTrue(StudioExportFiles.publish(pending, destination))
            assertFalse(pending.exists())
            assertTrue(destination.isFile)
            assertTrue(destination.length() > 0L)
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test fun refusesEmptyMalformedAndCollidingOutputs() {
        val directory = Files.createTempDirectory("nearer-export-test").toFile()
        try {
            val empty = StudioExportFiles.pendingFile(directory, "empty")
            empty.createNewFile()
            assertFalse(StudioExportFiles.publish(empty, StudioExportFiles.finalFile(directory, "empty")))

            val malformed = StudioExportFiles.pendingFile(directory, "bad")
            malformed.writeBytes(ByteArray(20))
            assertFalse(StudioExportFiles.publish(malformed, StudioExportFiles.finalFile(directory, "bad")))

            val pending = StudioExportFiles.pendingFile(directory, "collision")
            val destination = StudioExportFiles.finalFile(directory, "collision")
            mp4LikeFile(pending)
            destination.writeText("keep existing file")
            assertFalse(StudioExportFiles.publish(pending, destination))
            assertEquals("keep existing file", destination.readText())
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test fun cleanupRemovesOnlyAbandonedPendingExports() {
        val directory = Files.createTempDirectory("nearer-export-test").toFile()
        try {
            val pending = StudioExportFiles.pendingFile(directory, "orphan")
            pending.writeText("partial")
            val finished = StudioExportFiles.finalFile(directory, "finished")
            finished.writeText("keep")
            val unrelated = File(directory, "user-video.mp4")
            unrelated.writeText("keep")

            assertEquals(1, StudioExportFiles.cleanupAbandoned(directory))
            assertFalse(pending.exists())
            assertTrue(finished.exists())
            assertTrue(unrelated.exists())
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test fun exportIdsCannotEscapeTheExportDirectory() {
        val directory = Files.createTempDirectory("nearer-export-test").toFile()
        try {
            assertTrue(runCatching { StudioExportFiles.pendingFile(directory, "../outside") }.isFailure)
        } finally {
            directory.deleteRecursively()
        }
    }
}
