package com.nearer.app

import org.junit.Assert.assertEquals
import org.junit.Test

/** Shared Android/iOS golden vector for one-file transfer stream metadata. */
class TransferStreamMetadataGoldenVectorTest {
    @Test
    fun metadataMatchesSwiftGoldenVector() {
        val metadata = TransferResumeStreamMetadataCodec.Metadata(
            payloadId = 7L,
            transferId = "x",
            item = TransferBundleItem(
                id = "i", relativePath = "a", displayName = "a",
                sizeBytes = 10L, mimeType = null
            ),
            sourceModifiedAtMillis = null,
            wholeFileSha256 = "0".repeat(64)
        )
        val actual = TransferResumeStreamMetadataCodec.encode(metadata)
            .joinToString("") { "%02x".format(it) }
        val expected = "4e52534d010000000000000007000178000169000161000161000000000000000a00000040" + "30".repeat(64)
        assertEquals(expected, actual)
        assertEquals(metadata, TransferResumeStreamMetadataCodec.decode(expected.hexToBytes()))
    }

    private fun String.hexToBytes(): ByteArray =
        chunked(2).map { it.toInt(16).toByte() }.toByteArray()
}
