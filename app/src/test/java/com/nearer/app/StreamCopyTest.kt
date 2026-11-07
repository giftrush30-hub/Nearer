package com.nearer.app

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StreamCopyTest {
    @Test fun copiesExactlyTheRequestedBytes() {
        val source = byteArrayOf(1, 2, 3, 4, 5)
        val output = ByteArrayOutputStream()

        val result = StreamCopy.copy(ByteArrayInputStream(source), output, 5)

        assertEquals(StreamCopyResult(5, true), result)
        assertArrayEquals(source, output.toByteArray())
    }

    @Test fun earlyEndOfSourceIsReportedAsIncomplete() {
        val output = ByteArrayOutputStream()

        val result = StreamCopy.copy(ByteArrayInputStream(byteArrayOf(1, 2, 3)), output, 5)

        assertEquals(StreamCopyResult(3, false), result)
        assertArrayEquals(byteArrayOf(1, 2, 3), output.toByteArray())
    }

    @Test fun stoppedTransferIsReportedAsIncomplete() {
        val output = ByteArrayOutputStream()
        var keepGoing = true

        val result = StreamCopy.copy(
            input = ByteArrayInputStream(byteArrayOf(1, 2, 3, 4, 5)),
            output = output,
            expectedBytes = 5,
            shouldContinue = { keepGoing },
            onProgress = { keepGoing = false }
        )

        assertEquals(StreamCopyResult(5, false), result)
        assertArrayEquals(byteArrayOf(1, 2, 3, 4, 5), output.toByteArray())
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
