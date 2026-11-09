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
        ).receiverPage("a".repeat(64))

        assertTrue(page.contains("href=\"/download/0?key=aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa\""))
        assertTrue(page.contains("href=\"/download/1?key=aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa\""))
        assertTrue(page.contains("photo.jpg"))
        assertTrue(page.contains("video.mp4"))
        assertTrue(page.contains("Size unavailable"))
    }

    @Test fun receiverPageEscapesUntrustedFileNames() {
        val page = FastShareDownloadCatalog(
            listOf(FastShareDownloadItem(0, "<script>alert('x')</script>.txt", "text/plain", 1L))
        ).receiverPage("b".repeat(64))

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
    @Test fun receiverPageOffersBrowserToPhoneUploads() {
        val page = FastShareDownloadCatalog(
            listOf(FastShareDownloadItem(0, "photo.jpg", "image/jpeg", 1024L))
        ).receiverPage("e".repeat(64))
        assertTrue(page.contains("id=\\"uploads\\" type=\\"file\\" multiple"))
        assertTrue(page.contains("fetch(\\"/upload?key=e"))
        assertTrue(page.contains("Send files to NeaRer"))
    }

    @Test fun receiverPageUsesNeaRerBlueAndGoldBrandColors() {
        val page = FastShareDownloadCatalog(
            listOf(FastShareDownloadItem(0, "photo.jpg", "image/jpeg", 1024L))
        ).receiverPage("c".repeat(64))

        assertTrue(page.contains("background:#ffd12d"))
        assertTrue(page.contains("color:#6ea8ff"))
        assertTrue(page.contains("outline:3px solid #6ea8ff"))
        assertTrue(page.contains("Download"))
    }

    @Test fun receiverPageDownloadActionHasKeyboardFocusTreatment() {
        val page = FastShareDownloadCatalog(
            listOf(FastShareDownloadItem(0, "document.pdf", "application/pdf", 2048L))
        ).receiverPage("d".repeat(64))

        assertTrue(page.contains(".download:focus-visible"))
        assertTrue(page.contains("outline-offset:3px"))
    }


    @Test fun receiverPageOffersProgressCancelAndReceiptValidationForUploads() {
        val page = FastShareDownloadCatalog(listOf(
            FastShareDownloadItem(0, "sample.txt", "text/plain", 5L)
        )).receiverPage("test-key")
        assertTrue(page.contains("id=\"cancel\""))
        assertTrue(page.contains("xhr.upload.onprogress"))
        assertTrue(page.contains("X-Received-Bytes"))
        assertTrue(page.contains("X-Content-SHA256"))
        assertTrue(page.contains("xhr.abort()"))
    }
}
