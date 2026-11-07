package com.nearer.app

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class StreamRangeCopyTest {
    @Test fun resumedRangeStartsAtExactRequestedByteAfterInterruptedPrefix() {
        val original = "NeaRer-transfer-resume-check".toByteArray(Charsets.UTF_8)
        val firstAttempt = ByteArrayOutputStream()
        val limitedSource = object : InputStream() {
            private val delegate = ByteArrayInputStream(original)
            override fun read(): Int = delegate.read()
            override fun read(buffer: ByteArray, offset: Int, length: Int): Int =
                delegate.read(buffer, offset, minOf(length, 7 - firstAttempt.size()).coerceAtLeast(0))
        }
        val interrupted = StreamCopy.copy(
            limitedSource,
            firstAttempt,
            expectedBytes = original.size.toLong(),
            shouldContinue = { firstAttempt.size() < 7 }
        )
        assertEquals(7L, interrupted.bytesCopied)
        assertEquals(false, interrupted.complete)

        val resumed = ByteArrayOutputStream()
        val offset = firstAttempt.size().toLong()
        val range = ByteRange.resolve("bytes=$offset-", original.size.toLong())!!
        val retryStream = ByteArrayInputStream(original)
        StreamRangeCopy.skipFully(retryStream, range.start)
        val retry = StreamCopy.copy(
            retryStream,
            resumed,
            expectedBytes = range.endInclusive - range.start + 1
        )

        val assembled = firstAttempt.toByteArray() + resumed.toByteArray()
        assertEquals(true, retry.complete)
        assertArrayEquals(original, assembled)
        assertArrayEquals(original.copyOfRange(offset.toInt(), original.size), resumed.toByteArray())
    }

    @Test fun fallsBackToReadingWhenStreamCannotSkip() {
        val source = object : InputStream() {
            private val delegate = ByteArrayInputStream(byteArrayOf(10, 20, 30, 40, 50))
            override fun read(): Int = delegate.read()
            override fun skip(n: Long): Long = 0L
        }

        StreamRangeCopy.skipFully(source, 3)

        assertEquals(40, source.read())
    }

    @Test fun rejectsNegativeOffsets() {
        assertThrows(IllegalArgumentException::class.java) {
            StreamRangeCopy.skipFully(ByteArrayInputStream(byteArrayOf(1)), -1)
        }
    }

    @Test fun failsWhenRequestedOffsetIsPastEndOfStream() {
        assertThrows(IllegalStateException::class.java) {
            StreamRangeCopy.skipFully(ByteArrayInputStream(byteArrayOf(1, 2)), 3)
        }
    }
}
