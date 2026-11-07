package com.nearer.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FastShareDownloadCatalogTest {
    @Test fun resolvesOnlyExistingZeroBasedItems() {
        val catalog = FastShareDownloadCatalog(
            listOf(
                FastShareDownloadItem(0, "photo.jpg", "image/jpeg", 1024L),
                FastShareDownloadItem(1, "video.mp4", "video/mp4", 2048L)
            )
        )

        assertEquals("photo.jpg", catalog.itemAt(0)?.fileName)
        assertEquals("video.mp4", catalog.itemAt(1)?.fileName)
        assertNull(catalog.itemAt(-1))
        assertNull(catalog.itemAt(2))
    }

    @Test fun receiverPageListsEachFileWithItsOwnDownloadRoute() {
        val page = FastShareDownloadCatalog(
            listOf(
                FastShareDownloadItem(0, "photo.jpg", "image/jpeg", 1024L),
                FastShareDownloadItem(1, "video.mp4", "video/mp4", -1L)
            )
        ).receiverPage()

        assertTrue(page.contains("href=\"/download/0\""))
        assertTrue(page.contains("href=\"/download/1\""))
        assertTrue(page.contains("photo.jpg"))
        assertTrue(page.contains("video.mp4"))
        assertTrue(page.contains("Size unavailable"))
    }

    @Test fun receiverPageEscapesUntrustedFileNames() {
        val page = FastShareDownloadCatalog(
            listOf(FastShareDownloadItem(0, "<script>alert('x')</script>.txt", "text/plain", 1L))
        ).receiverPage()

        assertTrue(page.contains("&lt;script&gt;"))
        assertTrue(!page.contains("<script>alert"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsNonContiguousIndexes() {
        FastShareDownloadCatalog(listOf(FastShareDownloadItem(1, "second.txt", "text/plain", 1L)))
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsMoreThanOneHundredFiles() {
        FastShareDownloadCatalog((0..100).map {
            FastShareDownloadItem(it, "file-$it", "application/octet-stream", 1L)
        })
    }
}
