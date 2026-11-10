package com.nearer.app

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TransferBundleManifestTest {
    private fun item(
        id: String = "file-1",
        path: String = "Photos/Ọjà.jpg",
        name: String = "Ọjà.jpg",
        size: Long = 1_024L,
        kind: TransferBundleItem.Kind = TransferBundleItem.Kind.FILE,
        mimeType: String? = "image/jpeg"
    ) = TransferBundleItem(id, path, name, size, mimeType, kind)

    @Test
    fun acceptsValidUnicodeBundleManifest() {
        val manifest = TransferBundleManifest("transfer-1", listOf(item()))
        assertTrue(manifest.validationErrors().isEmpty())
    }

    @Test
    fun rejectsPathTraversal() {
        val manifest = TransferBundleManifest("transfer-1", listOf(item(path = "../private/secret.txt", name = "secret.txt")))
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
        val manifest = TransferBundleManifest("transfer-1", listOf(item(id = "same"), item(id = "same")))
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
        val manifest = TransferBundleManifest("transfer-1", listOf(item(
            path = "Documents", name = "Documents", size = 10L,
            kind = TransferBundleItem.Kind.DIRECTORY, mimeType = null
        )))
        assertTrue(manifest.validationErrors().any { it.contains("directory entries must have zero size") })
    }

    @Test
    fun rejectsFileUsedAsParentDirectory() {
        val manifest = TransferBundleManifest("transfer-1", listOf(
            item(id = "parent", path = "Photos", name = "Photos"),
            item(id = "child", path = "Photos/a.jpg", name = "a.jpg")
        ))
        assertTrue(manifest.validationErrors().any { it.contains("parent directory") || it.contains("child paths") })
    }

    @Test
    fun rejectsControlCharactersInTransferMetadata() {
        val manifest = TransferBundleManifest("bad\u0001id", listOf(item(mimeType = "image/jpeg\u0000")))
        val errors = manifest.validationErrors()
        assertTrue(errors.contains("Invalid transfer ID"))
        assertTrue(errors.any { it.contains("invalid MIME metadata") })
    }
}
