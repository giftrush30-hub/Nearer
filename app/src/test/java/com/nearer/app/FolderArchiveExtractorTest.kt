package com.nearer.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class FolderArchiveExtractorTest {
    @Test fun restoresNestedFolderStructureWithoutLoadingArchiveIntoMemory() {
        val root = Files.createTempDirectory("nearer-folder-extract").toFile()
        try {
            val archive = File(root, "shared.zip")
            writeZip(archive, listOf(
                "Photos/" to null,
                "Photos/2026/" to null,
                "Photos/2026/photo one.jpg" to "photo-bytes".toByteArray(),
                "Notes/readme.txt" to "hello".toByteArray()
            ))
            val result = FolderArchiveExtractor.extract(archive, File(root, "received"))
            assertEquals(2, result.fileCount)
            assertEquals(2, result.directoryCount)
            assertEquals(16L, result.bytesExtracted)
            assertEquals("photo-bytes", File(result.directory, "Photos/2026/photo one.jpg").readText())
            assertEquals("hello", File(result.directory, "Notes/readme.txt").readText())
        } finally {
            root.deleteRecursively()
        }
    }

    @Test fun rejectsZipSlipAndRemovesPartialExtraction() {
        val root = Files.createTempDirectory("nearer-folder-slip").toFile()
        try {
            val archive = File(root, "unsafe.zip")
            writeZip(archive, listOf("../outside.txt" to "bad".toByteArray()))
            val received = File(root, "received")
            val error = runCatching { FolderArchiveExtractor.extract(archive, received) }.exceptionOrNull()
            assertTrue(error is java.io.IOException)
            assertFalse(File(root, "outside.txt").exists())
            assertTrue(received.listFiles().orEmpty().isEmpty())
        } finally {
            root.deleteRecursively()
        }
    }

    @Test fun rejectsCaseInsensitiveDuplicatePaths() {
        val root = Files.createTempDirectory("nearer-folder-duplicate").toFile()
        try {
            val archive = File(root, "duplicate.zip")
            writeZip(archive, listOf(
                "Docs/one.txt" to byteArrayOf(1),
                "docs/ONE.txt" to byteArrayOf(2)
            ))
            val error = runCatching { FolderArchiveExtractor.extract(archive, File(root, "received")) }.exceptionOrNull()
            assertTrue(error is java.io.IOException)
        } finally {
            root.deleteRecursively()
        }
    }

    @Test fun rejectsCaseConflictingImplicitParentFolders() {
        val root = Files.createTempDirectory("nearer-folder-case-parent").toFile()
        try {
            val archive = File(root, "case-conflict.zip")
            writeZip(archive, listOf(
                "Photos/one.jpg" to byteArrayOf(1),
                "photos/two.jpg" to byteArrayOf(2)
            ))
            val error = runCatching { FolderArchiveExtractor.extract(archive, File(root, "received")) }.exceptionOrNull()
            assertTrue(error is java.io.IOException)
        } finally {
            root.deleteRecursively()
        }
    }

    @Test fun rejectsFileUsedAsParentFolder() {
        val root = Files.createTempDirectory("nearer-folder-parent").toFile()
        try {
            val archive = File(root, "conflict.zip")
            writeZip(archive, listOf(
                "Docs" to byteArrayOf(1),
                "Docs/one.txt" to byteArrayOf(2)
            ))
            val error = runCatching { FolderArchiveExtractor.extract(archive, File(root, "received")) }.exceptionOrNull()
            assertTrue(error is java.io.IOException)
        } finally {
            root.deleteRecursively()
        }
    }

    @Test fun rejectsDriveAndBackslashPaths() {
        listOf("C:/private.txt", "folder\\private.txt", "/absolute.txt").forEachIndexed { index, name ->
            val root = Files.createTempDirectory("nearer-folder-path-$index").toFile()
            try {
                val archive = File(root, "unsafe.zip")
                writeZip(archive, listOf(name to byteArrayOf(1)))
                val error = runCatching { FolderArchiveExtractor.extract(archive, File(root, "received")) }.exceptionOrNull()
                assertTrue(error is java.io.IOException)
            } finally {
                root.deleteRecursively()
            }
        }
    }

    private fun writeZip(file: File, entries: List<Pair<String, ByteArray?>>) {
        ZipOutputStream(file.outputStream().buffered()).use { zip ->
            entries.forEach { (name, bytes) ->
                zip.putNextEntry(ZipEntry(name))
                if (bytes != null) zip.write(bytes)
                zip.closeEntry()
            }
        }
    }
}
