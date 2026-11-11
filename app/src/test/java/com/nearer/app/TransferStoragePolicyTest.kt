package com.nearer.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TransferStoragePolicyTest {
    @Test
    fun requiredBytesSumsFilesAndIgnoresDirectories() {
        val items = listOf(
            TransferBundleItem("folder", "Photos", "Photos", 0L, null, TransferBundleItem.Kind.DIRECTORY),
            TransferBundleItem("one", "Photos/a.jpg", "a.jpg", 120L, "image/jpeg"),
            TransferBundleItem("two", "Photos/b.jpg", "b.jpg", 80L, "image/jpeg")
        )
        assertEquals(200L, TransferStoragePolicy.requiredBytes(items))
    }

    @Test
    fun bundleFitsWhenRequiredBytesEqualAvailableBytes() {
        assertTrue(TransferStoragePolicy.canFit(500L, 500L))
    }

    @Test
    fun bundleDoesNotFitWhenRequiredBytesExceedAvailableBytes() {
        assertFalse(TransferStoragePolicy.canFit(501L, 500L))
    }

    @Test(expected = IllegalArgumentException::class)
    fun negativeRequiredBytesAreRejected() {
        TransferStoragePolicy.canFit(-1L, 500L)
    }

    @Test(expected = IllegalArgumentException::class)
    fun negativeAvailableBytesAreRejected() {
        TransferStoragePolicy.canFit(1L, -1L)
    }

    @Test(expected = ArithmeticException::class)
    fun requiredByteSumRejectsOverflow() {
        TransferStoragePolicy.requiredBytes(
            listOf(
                TransferBundleItem("one", "a.bin", "a.bin", Long.MAX_VALUE, null),
                TransferBundleItem("two", "b.bin", "b.bin", 1L, null)
            )
        )
    }
}
