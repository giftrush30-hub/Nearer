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
        assertEquals(manifest, TransferBundleManifestCodec.decode(TransferBundleManifestCodec.encode(manifest)))
    }

    @Test
    fun rejectsOversizedWireManifestBeforeParsing() {
        assertThrows(IllegalArgumentException::class.java) {
            TransferBundleManifestCodec.decode(ByteArray(TransferBundleManifestCodec.MAX_ENCODED_BYTES + 1))
        }
    }

    @Test
    fun rejectsMalformedAndUnsafeWireManifest() {
        assertThrows(Exception::class.java) {
            TransferBundleManifestCodec.decode(byteArrayOf(1, 2, 3))
        }
        val unsafe = TransferBundleManifest("x", listOf(
            TransferBundleItem("1", "../escape.txt", "escape.txt", 1L, "text/plain")
        ))
        assertThrows(IllegalArgumentException::class.java) { TransferBundleManifestCodec.encode(unsafe) }
    }

    @Test
    fun rejectsMalformedUtf8InsteadOfSilentlyReplacingIt() {
        val encoded = TransferBundleManifestCodec.encode(TransferBundleManifest("abc", listOf(
            TransferBundleItem("1", "file.txt", "file.txt", 1L, null)
        )))
        // Header is magic, version, transfer-ID byte length; the first ID byte
        // begins at offset 12. 0xC3 followed by ASCII is not valid UTF-8.
        encoded[12] = 0xC3.toByte()
        assertThrows(IllegalArgumentException::class.java) {
            TransferBundleManifestCodec.decode(encoded)
        }
    }

    @Test
    fun rejectsTrailingBytes() {
        val bytes = TransferBundleManifestCodec.encode(TransferBundleManifest("x", listOf(
            TransferBundleItem("1", "file.txt", "file.txt", 1L, null)
        ))) + byteArrayOf(0)
        assertThrows(IllegalArgumentException::class.java) { TransferBundleManifestCodec.decode(bytes) }
    }
}
