package com.nearer.app

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import org.junit.Assert.*
import org.junit.Test

class ChunkedStreamCopyTest {
    @Test fun writesValidChunksAndFinalTerminatorForCleanEof() {
        val output = ByteArrayOutputStream()
        val result = ChunkedStreamCopy.copy(
            input = ByteArrayInputStream("hello world".toByteArray()),
            output = output,
            shouldContinue = { true },
            bufferSize = 5
        )

        assertTrue(result.complete)
        assertEquals(11L, result.bytesCopied)
        assertEquals("5\r\nhello\r\n5\r\n worl\r\n1\r\nd\r\n0\r\n\r\n", output.toString("UTF-8"))
    }

    @Test fun shortReadsAreFramedAsSeparateValidChunks() {
        val output = ByteArrayOutputStream()
        val input = object : InputStream() {
            private val source = ByteArrayInputStream("abcdefg".toByteArray())
            override fun read(): Int = source.read()
            override fun read(buffer: ByteArray, offset: Int, length: Int): Int =
                source.read(buffer, offset, minOf(length, 2))
        }

        val result = ChunkedStreamCopy.copy(input, output, { true }, bufferSize = 8)

        assertTrue(result.complete)
        assertEquals(7L, result.bytesCopied)
        assertEquals("2\r\nab\r\n2\r\ncd\r\n2\r\nef\r\n1\r\ng\r\n0\r\n\r\n", output.toString("UTF-8"))
    }

    @Test fun interruptedStreamDoesNotWriteFalseCompletionTerminator() {
        val output = ByteArrayOutputStream()
        var copied = 0L
        val result = ChunkedStreamCopy.copy(
            input = ByteArrayInputStream("abcdefghij".toByteArray()),
            output = output,
            shouldContinue = { copied < 5L },
            onBytesCopied = { copied = it },
            bufferSize = 3
        )

        assertFalse(result.complete)
        assertEquals(6L, result.bytesCopied)
        assertEquals("3\r\nabc\r\n3\r\ndef\r\n", output.toString("UTF-8"))
        assertFalse(output.toString("UTF-8").endsWith("0\r\n\r\n"))
    }

    @Test fun emptyInputWritesOnlyFinalTerminator() {
        val output = ByteArrayOutputStream()
        val result = ChunkedStreamCopy.copy(ByteArrayInputStream(byteArrayOf()), output, { true })

        assertTrue(result.complete)
        assertEquals(0L, result.bytesCopied)
        assertEquals("0\r\n\r\n", output.toString("UTF-8"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsNonPositiveBufferSize() {
        ChunkedStreamCopy.copy(ByteArrayInputStream(byteArrayOf()), ByteArrayOutputStream(), { true }, bufferSize = 0)
    }
}
