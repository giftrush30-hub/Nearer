package com.nearer.app

import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NeaRerAppBundleValidatorTest {
    @Test
    fun acceptsZipAndNearerappArchivesWithBothRequiredEntries() {
        val zip = createArchive(".zip", "INSTALLATION.txt", "base.apk")
        val nearerApp = createArchive(".nearerapp", "INSTALLATION.txt", "base.apk")

        try {
            assertTrue(isNeaRerAppBundle(zip))
            assertTrue(isNeaRerAppBundle(nearerApp))
        } finally {
            zip.delete()
            nearerApp.delete()
        }
    }

    @Test
    fun rejectsArchivesMissingEitherRequiredEntry() {
        val missingInstructions = createArchive(".zip", "base.apk")
        val missingApk = createArchive(".nearerapp", "INSTALLATION.txt")

        try {
            assertFalse(isNeaRerAppBundle(missingInstructions))
            assertFalse(isNeaRerAppBundle(missingApk))
        } finally {
            missingInstructions.delete()
            missingApk.delete()
        }
    }

    @Test
    fun rejectsWrongExtensionsMissingFilesAndMalformedArchives() {
        val wrongExtension = createArchive(".txt", "INSTALLATION.txt", "base.apk")
        val malformed = File.createTempFile("badbundle", ".zip").apply {
            writeText("not a zip archive")
        }
        val missing = File(malformed.parentFile, "nearer-missing-" + System.nanoTime() + ".zip")

        try {
            assertFalse(isNeaRerAppBundle(wrongExtension))
            assertFalse(isNeaRerAppBundle(malformed))
            assertFalse(isNeaRerAppBundle(missing))
        } finally {
            wrongExtension.delete()
            malformed.delete()
        }
    }

    private fun createArchive(extension: String, vararg entries: String): File {
        val file = File.createTempFile("nearerbundle", extension)
        ZipOutputStream(FileOutputStream(file)).use { archive ->
            entries.forEach { name ->
                archive.putNextEntry(ZipEntry(name))
                archive.write("test".toByteArray())
                archive.closeEntry()
            }
        }
        return file
    }
}
