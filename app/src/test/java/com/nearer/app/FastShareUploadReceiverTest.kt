package com.nearer.app

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.EOFException
import java.io.InterruptedIOException

class FastShareUploadReceiverTest {
    @Test fun copiesExactlyDeclaredBodyAndLeavesFollowingBytesUnread() {
        val source = ByteArrayInputStream("file-bodyNEXT".toByteArray())
        val destination = ByteArrayOutputStream()
        val progress = mutableListOf<Long>()

        val copied = FastShareUploadReceiver.copy(
            input = source,
            output = destination,
            expectedBytes = 9L,
            onBytesCopied = { progress += it }
        )

        assertEquals(9L, copied)
        assertArrayEquals("file-body".toByteArray(), destination.toByteArray())
        assertEquals('N'.code, source.read())
        assertTrue(progress.isNotEmpty())
        assertEquals(9L, progress.last())
    }

    @Test fun supportsEmptyUploadsWithoutReadingTheNextRequestByte() {
        val source = ByteArrayInputStream(byteArrayOf(42))
        val destination = ByteArrayOutputStream()
        assertEquals(0L, FastShareUploadReceiver.copy(source, destination, 0L))
        assertEquals(42, source.read())
        assertEquals(0, destination.size())
    }

    @Test(expected = EOFException::class)
    fun rejectsBodyShorterThanDeclaredContentLength() {
        FastShareUploadReceiver.copy(
            ByteArrayInputStream(byteArrayOf(1, 2, 3)),
            ByteArrayOutputStream(),
            4L
        )
    }

    @Test(expected = InterruptedIOException::class)
    fun stopsWhenServerIsStopping() {
        FastShareUploadReceiver.copy(
            ByteArrayInputStream(ByteArray(16)),
            ByteArrayOutputStream(),
            16L,
            shouldContinue = { false }
        )
    }
}
