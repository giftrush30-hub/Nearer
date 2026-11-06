package com.nearer.app

import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
import org.junit.Test

class TransferBundleManifestTest {
    private fun item(
        id: String = "file-1",
        path: String = "Photos/Ọjà.jpg",
        name: String = "Ọjà.jpg",
        size: Long = 1_024L
    ) = TransferBundleItem(id, path, name, size, "image/jpeg")

    @Test
    fun acceptsValidUnicodeBundleManifest() {
        val manifest = TransferBundleManifest("transfer-1", listOf(item()))
        assertTrue(manifest.validationErrors().isEmpty())
    }

    @Test
    fun rejectsPathTraversal() {
        val manifest = TransferBundleManifest(
            "transfer-1",
            listOf(item(path = "../private/secret.txt", name = "secret.txt"))
        )
        assertTrue(manifest.validationErrors().any { it.contains("unsafe relative path") })
    }

    @Test
    fun rejectsAbsoluteAndBackslashPaths() {
        val absolute = TransferBundleManifest("transfer-1", listOf(item(path = "/storage/file.txt", name = "file.txt")))
        val windows = TransferBundleManifest("transfer-1", listOf(item(path = "folder\\..\\file.txt", name = "file.txt")))
        assertFalse(absolute.validationErrors().isEmpty())
        assertFalse(windows.validationErrors().isEmpty())
    }

    @Test
    fun rejectsDuplicateIdsAndDestinationPaths() {
        val manifest = TransferBundleManifest(
            "transfer-1",
            listOf(item(id = "same"), item(id = "same"))
        )
        val errors = manifest.validationErrors()
        assertTrue(errors.any { it.contains("duplicate ID") })
        assertTrue(errors.any { it.contains("duplicates another destination path") })
    }

    @Test
    fun rejectsNegativeSizesAndUnsupportedVersions() {
        val manifest = TransferBundleManifest("transfer-1", listOf(item(size = -1L)), protocolVersion = 99)
        val errors = manifest.validationErrors()
        assertTrue(errors.contains("Unsupported transfer manifest version"))
        assertTrue(errors.any { it.contains("negative file size") })
    }

    @Test
    fun rejectsDirectoryWithNonzeroSize() {
        val directory = TransferBundleItem(
            id = "folder-1",
            relativePath = "Documents",
            displayName = "Documents",
            sizeBytes = 10L,
            mimeType = null,
            kind = TransferBundleItem.Kind.DIRECTORY
        )
        val manifest = TransferBundleManifest("transfer-1", listOf(directory))
        assertTrue(manifest.validationErrors().any { it.contains("directory entries must have zero size") })
    }
}
