package com.nearer.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class TransferBundleManifestCodecTest {
    @Test
    fun roundTripsUnicodePathsAndDirectoryEntries() {
        val manifest = TransferBundleManifest("transfer-123", listOf(
            TransferBundleItem("folder", "Photos", "Photos", 0L, null, TransferBundleItem.Kind.DIRECTORY),
            TransferBundleItem("photo", "Photos/Ọjà.jpg", "Ọjà.jpg", 4096L, "image/jpeg")
        ))
        val decoded = TransferBundleManifestCodec.decode(TransferBundleManifestCodec.encode(manifest))
        assertEquals(manifest, decoded)
    }

    @Test
    fun rejectsOversizedWireManifestBeforeParsing() {
        assertThrows(IllegalArgumentException::class.java) {
            TransferBundleManifestCodec.decode(ByteArray(TransferBundleManifestCodec.MAX_ENCODED_BYTES + 1))
        }
    }

    @Test
    fun rejectsMalformedAndUnsafeWireManifest() {
        val unsafe = """{"version":1,"transferId":"x","items":[{"id":"1","path":"../escape.txt","name":"escape.txt","size":1,"kind":"FILE","mime":"text/plain"}]}"""
        assertThrows(IllegalArgumentException::class.java) {
            TransferBundleManifestCodec.decode(unsafe.toByteArray(Charsets.UTF_8))
        }
    }

    @Test
    fun rejectsUnknownItemKind() {
        val unknownKind = """{"version":1,"transferId":"x","items":[{"id":"1","path":"file.txt","name":"file.txt","size":1,"kind":"SYMLINK","mime":null}]}"""
        assertThrows(IllegalArgumentException::class.java) {
            TransferBundleManifestCodec.decode(unknownKind.toByteArray(Charsets.UTF_8))
        }
    }
}
