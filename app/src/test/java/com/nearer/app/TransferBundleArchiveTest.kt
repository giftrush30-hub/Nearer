package com.nearer.app

import java.io.File
import java.nio.file.Files
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.junit.Assert.*
import org.junit.Test

class TransferBundleArchiveTest {
    @Test
    fun packsAndExtractsNestedFilesAndUnicodeNames() {
        val root = Files.createTempDirectory("nearer-bundle-roundtrip").toFile()
        try {
            val a = File(root, "a.jpg").apply { writeBytes(ByteArray(32_000) { (it % 251).toByte() }) }
            val b = File(root, "b.txt").apply { writeText("NeaRer folder transfer — Ọjà") }
            val archive = File(root, "bundle.zip")
            val manifest = TransferBundleArchive.pack(
                "bundle-1",
                listOf(
                    TransferBundleArchive.Source(a, "Photos/Ọjà.jpg", "image/jpeg"),
                    TransferBundleArchive.Source(b, "Documents/readme.txt", "text/plain")
                ),
                archive
            )
            assertEquals(2, manifest.items.count { it.kind == TransferBundleItem.Kind.FILE })
            assertEquals(2, manifest.items.count { it.kind == TransferBundleItem.Kind.DIRECTORY })

            val destination = File(root, "received/Bundle")
            val decoded = TransferBundleArchive.extract(archive, destination)
            assertEquals(manifest, decoded)
            assertArrayEquals(a.readBytes(), File(destination, "Photos/Ọjà.jpg").readBytes())
            assertEquals(b.readText(), File(destination, "Documents/readme.txt").readText())
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun rejectsArchiveWithUnlistedExtraEntryAndCleansPartialOutput() {
        val root = Files.createTempDirectory("nearer-bundle-extra").toFile()
        try {
            val source = File(root, "one.txt").apply { writeText("one") }
            val archive = File(root, "bundle.zip")
            TransferBundleArchive.pack(
                "bundle-extra",
                listOf(TransferBundleArchive.Source(source, "one.txt", "text/plain")),
                archive
            )
            val rewritten = File(root, "extra.zip")
            java.util.zip.ZipFile(archive).use { original ->
                ZipOutputStream(rewritten.outputStream()).use { output ->
                    val entries = original.entries()
                    while (entries.hasMoreElements()) {
                        val entry = entries.nextElement()
                        output.putNextEntry(ZipEntry(entry.name))
                        original.getInputStream(entry).use { it.copyTo(output) }
                        output.closeEntry()
                    }
                    output.putNextEntry(ZipEntry("files/secret.txt"))
                    output.write("not in manifest".toByteArray())
                    output.closeEntry()
                }
            }
            val destination = File(root, "received")
            try {
                TransferBundleArchive.extract(rewritten, destination)
                fail("Unlisted archive entry must be rejected")
            } catch (_: IllegalArgumentException) { }
            assertFalse(destination.exists())
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun rejectsTraversalBeforeWritingAnything() {
        val root = Files.createTempDirectory("nearer-bundle-traversal").toFile()
        try {
            val source = File(root, "secret.txt").apply { writeText("secret") }
            val archive = File(root, "bundle.zip")
            try {
                TransferBundleArchive.pack(
                    "bundle-traversal",
                    listOf(TransferBundleArchive.Source(source, "../escape.txt")),
                    archive
                )
                fail("Unsafe relative path must be rejected")
            } catch (_: IllegalArgumentException) { }
            assertFalse(File(root.parentFile, "escape.txt").exists())
        } finally {
            root.deleteRecursively()
        }
    }
}
