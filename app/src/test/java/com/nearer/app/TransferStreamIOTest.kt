package com.nearer.app

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.EOFException
import java.io.InputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class TransferStreamIOTest {
    @Test
    fun copiesExactBytesAndReturnsSha256() {
        val source = "NeaRer HyperTransfer Ọjà".toByteArray(Charsets.UTF_8)
        val output = ByteArrayOutputStream()
        val result = TransferStreamIO.copyExactly(ByteArrayInputStream(source), output, source.size.toLong(), 4096)
        assertArrayEquals(source, output.toByteArray())
        assertEquals(source.size.toLong(), result.bytesCopied)
        assertEquals("7c29824111af28755e63a3b8061aad8540f0f681083a2d844b702c57453e0355",
            TransferStreamIO.sha256Hex(result.sha256))
    }

    @Test
    fun recoversWhenBulkReadMakesNoProgress() {
        val source = byteArrayOf(10, 20, 30, 40)
        val input = object : InputStream() {
            private var index = 0
            private var firstBulkRead = true

            override fun read(): Int =
                if (index >= source.size) -1 else source[index++].toInt() and 0xff

            override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
                if (firstBulkRead) {
                    firstBulkRead = false
                    return 0
                }
                if (index >= source.size) return -1
                val count = minOf(length, source.size - index)
                System.arraycopy(source, index, buffer, offset, count)
                index += count
                return count
            }
        }
        val output = ByteArrayOutputStream()
        val result = TransferStreamIO.copyExactly(input, output, source.size.toLong(), 4096)
        assertArrayEquals(source, output.toByteArray())
        assertEquals(source.size.toLong(), result.bytesCopied)
    }

    @Test
    fun rejectsPrematureEndOfStream() {
        assertThrows(EOFException::class.java) {
            TransferStreamIO.copyExactly(ByteArrayInputStream(byteArrayOf(1, 2)), ByteArrayOutputStream(), 3L, 4096)
        }
    }

    @Test
    fun cancellationStopsCopy() {
        assertThrows(TransferCancelledException::class.java) {
            TransferStreamIO.copyExactly(ByteArrayInputStream(ByteArray(8192)), ByteArrayOutputStream(), 8192L, 4096) { true }
        }
    }

    @Test
    fun zeroByteFileIsSupported() {
        val output = ByteArrayOutputStream()
        val result = TransferStreamIO.copyExactly(ByteArrayInputStream(byteArrayOf(1)), output, 0L, 4096)
        assertEquals(0L, result.bytesCopied)
        assertEquals(0, output.size())
    }

    @Test
    fun rejectsUnsafeBufferSize() {
        assertThrows(IllegalArgumentException::class.java) {
            TransferStreamIO.copyExactly(ByteArrayInputStream(byteArrayOf()), ByteArrayOutputStream(), 0L, 1024)
        }
    }
}
