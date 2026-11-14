package com.nearer.app

import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FastShareUploadDestinationTest {
    @Test fun keepsOriginalNameWhenDestinationIsFree() {
        val folder = Files.createTempDirectory("nearer-upload-destination").toFile()
        try {
            assertEquals(File(folder, "movie.mp4"), FastShareUploadDestination.nextAvailable(folder, "movie.mp4"))
        } finally { folder.deleteRecursively() }
    }

    @Test fun addsCollisionSuffixBeforeExtension() {
        val folder = Files.createTempDirectory("nearer-upload-collision").toFile()
        try {
            File(folder, "movie.mp4").writeText("first")
            assertEquals("movie (2).mp4", FastShareUploadDestination.nextAvailable(folder, "movie.mp4").name)
            File(folder, "movie (2).mp4").writeText("second")
            assertEquals("movie (3).mp4", FastShareUploadDestination.nextAvailable(folder, "movie.mp4").name)
        } finally { folder.deleteRecursively() }
    }

    @Test fun rejectsPathSegmentsAndControlCharacters() {
        val folder = Files.createTempDirectory("nearer-upload-path").toFile()
        try {
            listOf("../secret.txt", "folder/file.txt", "folder\\file.txt", ".", "bad\nname.txt").forEach { name ->
                assertTrue(runCatching { FastShareUploadDestination.nextAvailable(folder, name) }.isFailure)
            }
        } finally { folder.deleteRecursively() }
    }
}
