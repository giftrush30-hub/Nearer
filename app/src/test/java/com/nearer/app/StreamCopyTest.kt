package com.nearer.app

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
import org.junit.Test

class StreamCopyTest {
    @Test fun defaultBufferIsLargeEnoughForEfficientLocalStreaming() {
        assertEquals(512 * 1024, StreamCopy.DEFAULT_BUFFER_SIZE)
        val source = ByteArray(700 * 1024) { (it % 239).toByte() }
        val output = ByteArrayOutputStream()
        val result = StreamCopy.copy(ByteArrayInputStream(source), output, source.size.toLong())
        assertTrue(result.complete)
        assertArrayEquals(source, output.toByteArray())
    }

    @Test fun copiesExactlyTheRequestedBytes() {
        val source = byteArrayOf(1, 2, 3, 4, 5)
        val output = ByteArrayOutputStream()

        val result = StreamCopy.copy(ByteArrayInputStream(source), output, 5)

        assertEquals(StreamCopyResult(5, true), result)
        assertArrayEquals(source, output.toByteArray())
    }

    @Test fun zeroLengthBulkReadFallsBackToSingleByteRead() {
        val source = ByteArrayInputStream(byteArrayOf(7, 8, 9))
        val input = object : InputStream() {
            private var returnZero = true
            override fun read(): Int = source.read()
            override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
                if (returnZero) {
                    returnZero = false
                    return 0
                }
                return source.read(buffer, offset, length)
            }
        }
        val output = ByteArrayOutputStream()

        val result = StreamCopy.copy(input, output, 3, bufferSize = 2)

        assertEquals(StreamCopyResult(3, true), result)
        assertArrayEquals(byteArrayOf(7, 8, 9), output.toByteArray())
    }

    @Test fun earlyEndOfSourceIsReportedAsIncomplete() {
        val output = ByteArrayOutputStream()

        val result = StreamCopy.copy(ByteArrayInputStream(byteArrayOf(1, 2, 3)), output, 5)

        assertEquals(StreamCopyResult(3, false), result)
        assertArrayEquals(byteArrayOf(1, 2, 3), output.toByteArray())
    }

    @Test fun stoppedTransferIsReportedAsIncomplete() {
        val source = ByteArray(10 * 1024 * 1024) { (it % 251).toByte() }
        val output = ByteArrayOutputStream()
        var keepGoing = true

        val result = StreamCopy.copy(
            input = ByteArrayInputStream(source),
            output = output,
            expectedBytes = source.size.toLong(),
            shouldContinue = { keepGoing },
            onProgress = { keepGoing = false }
        )

        assertFalse(result.complete)
        assertEquals(StreamCopy.PROGRESS_INTERVAL_BYTES, result.bytesCopied)
        assertTrue(result.bytesCopied < source.size.toLong())
        assertEquals(result.bytesCopied.toInt(), output.size())
    }

    @Test fun zeroByteFileCompletesWithoutWritingData() {
        val output = ByteArrayOutputStream()

        val result = StreamCopy.copy(ByteArrayInputStream(byteArrayOf()), output, 0)

        assertEquals(StreamCopyResult(0, true), result)
        assertEquals(0, output.size())
    }

    @Test fun negativeExpectedLengthIsRejected() {
        try {
            StreamCopy.copy(ByteArrayInputStream(byteArrayOf()), ByteArrayOutputStream(), -1)
            throw AssertionError("Negative expected length should be rejected")
        } catch (_: IllegalArgumentException) {
            assertTrue(true)
        }
    }
}
