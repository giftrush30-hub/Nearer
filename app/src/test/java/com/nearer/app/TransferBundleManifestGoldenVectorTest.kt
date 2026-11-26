package com.nearer.app

import org.junit.Assert.assertEquals
import org.junit.Test

/** Same NRB1 manifest fixture asserted by the iOS Swift package. */
class TransferBundleManifestGoldenVectorTest {
    @Test
    fun manifestMatchesSwiftGoldenVector() {
        val manifest = TransferBundleManifest(
            transferId = "x",
            items = listOf(
                TransferBundleItem(
                    id = "i",
                    relativePath = "a",
                    displayName = "a",
                    sizeBytes = 1L,
                    mimeType = null,
                    kind = TransferBundleItem.Kind.FILE
                )
            )
        )
        val actual = TransferBundleManifestCodec.encode(manifest)
            .joinToString("") { "%02x".format(it) }
        val expected = "4e5242310000000100000001780000000100000001690000000161000000016100000000000000010000"
        assertEquals(expected, actual)
    }
}
