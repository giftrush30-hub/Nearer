package com.nearer.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FastShareUploadDeadlineTest {
    @Test fun zeroAndSmallUploadsReceiveBaseGracePeriod() {
        assertEquals(FastShareUploadDeadline.BASE_MILLIS, FastShareUploadDeadline.timeoutMillis(0L))
        assertEquals(FastShareUploadDeadline.BASE_MILLIS + 1000L, FastShareUploadDeadline.timeoutMillis(1L))
    }

    @Test fun deadlineScalesWithLargeUploadSize() {
        val oneMiB = FastShareUploadDeadline.timeoutMillis(1024L * 1024L)
        assertEquals(FastShareUploadDeadline.BASE_MILLIS + 16_000L, oneMiB)
        assertTrue(FastShareUploadDeadline.timeoutMillis(4L * 1024L * 1024L * 1024L) > oneMiB)
    }

    @Test fun maximumDeadlineIsBoundedWithoutImposingAFileSizeLimit() {
        assertEquals(FastShareUploadDeadline.MAX_MILLIS, FastShareUploadDeadline.timeoutMillis(Long.MAX_VALUE))
        assertEquals(FastShareUploadDeadline.MAX_MILLIS, FastShareUploadDeadline.timeoutMillis(100L * 1024L * 1024L * 1024L))
    }

    @Test(expected = IllegalArgumentException::class)
    fun negativeLengthIsRejected() {
        FastShareUploadDeadline.timeoutMillis(-1L)
    }
}
