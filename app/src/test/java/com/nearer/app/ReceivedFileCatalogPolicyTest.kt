package com.nearer.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReceivedFileCatalogPolicyTest {
    @Test fun roundTripsNamesPathsSizesAndBundleFlag() {
        val entries = listOf(
            ReceivedFileCatalogEntry("旅行影片.mp4", "1712345678-My_video.mp4", 987654321L, 1712345678000L, false),
            ReceivedFileCatalogEntry("NeaRer app.nearerapp", "1712345680-app.nearerapp", 1234L, 1712345680000L, true)
        )
        assertEquals(entries, ReceivedFileCatalogPolicy.decode(ReceivedFileCatalogPolicy.encode(entries)))
    }

    @Test fun restoresLegacyNamesWithAndWithoutUuidPrefixes() {
        assertEquals("movie.mp4", ReceivedFileCatalogPolicy.legacyDisplayName("1712345678-movie.mp4"))
        assertEquals("movie.mp4", ReceivedFileCatalogPolicy.legacyDisplayName("1712345678-a1b2c3d4-movie.mp4"))
    }

    @Test fun preservesUnicodeDisplayNames() {
        val entry = ReceivedFileCatalogEntry("日本語の動画.mp4", "safe-file.mp4", 12L, 34L, false)
        assertEquals(entry, ReceivedFileCatalogPolicy.decode(ReceivedFileCatalogPolicy.encode(listOf(entry))).single())
    }

    @Test fun encodeKeepsOnlyLatestEntryForEachPath() {
        val first = ReceivedFileCatalogEntry("Old name.txt", "same.txt", 4L, 10L, false)
        val second = ReceivedFileCatalogEntry("New name.txt", "same.txt", 8L, 20L, true)
        assertEquals(listOf(second), ReceivedFileCatalogPolicy.decode(ReceivedFileCatalogPolicy.encode(listOf(first, second))))
    }

    @Test fun rejectsTraversalAndInvalidMetadata() {
        assertNull(ReceivedFileCatalogPolicy.create("file.txt", "../outside.txt", 1L, 1L, false))
        assertNull(ReceivedFileCatalogPolicy.create("file.txt", "folder/file.txt", 1L, 1L, false))
        assertNull(ReceivedFileCatalogPolicy.create("file.txt", "file.txt", -1L, 1L, false))
        assertNull(ReceivedFileCatalogPolicy.create("file.txt", "file.txt", 1L, -1L, false))
    }

    @Test fun ignoresCorruptAndOversizedCatalogs() {
        assertTrue(ReceivedFileCatalogPolicy.decode("not-a-catalog").isEmpty())
        assertTrue(ReceivedFileCatalogPolicy.decode("x".repeat(300 * 1024)).isEmpty())
    }

    @Test fun boundsCatalogToTwoHundredEntries() {
        val entries = (0 until 250).map {
            ReceivedFileCatalogEntry("file-$it.txt", "path-$it", it.toLong(), it.toLong(), false)
        }
        assertEquals(200, ReceivedFileCatalogPolicy.decode(ReceivedFileCatalogPolicy.encode(entries)).size)
    }

    @Test fun storagePreflightAcceptsExactFitAndRejectsShortfallOrInvalidValues() {
        assertTrue(ReceivedFileStoragePolicy.hasEnoughSpace(100L, 100L))
        assertFalse(ReceivedFileStoragePolicy.hasEnoughSpace(101L, 100L))
        assertFalse(ReceivedFileStoragePolicy.hasEnoughSpace(-1L, 100L))
        assertFalse(ReceivedFileStoragePolicy.hasEnoughSpace(1L, -1L))
    }
}
