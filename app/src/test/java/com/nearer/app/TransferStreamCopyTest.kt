package com.nearer.app

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class TransferStreamCopyTest {
    @Test
    fun copiesBytesAndReturnsLongByteCount() {
        val source = "large-file-stream".toByteArray()
        val output = ByteArrayOutputStream()
        val copied = TransferStreamCopy.copy(ByteArrayInputStream(source), output, bufferSize = 3)
        assertEquals(source.size.toLong(), copied)
        assertArrayEquals(source, output.toByteArray())
    }

    @Test
    fun fallsBackToSingleByteReadsWhenBulkReadReturnsZero() {
        val source = "provider-stream".toByteArray()
        val input = object : InputStream() {
            private val delegate = ByteArrayInputStream(source)
            override fun read(): Int = delegate.read()
            override fun read(buffer: ByteArray, offset: Int, length: Int): Int = 0
        }
        val output = ByteArrayOutputStream()
        val copied = TransferStreamCopy.copy(input, output, bufferSize = 8)
        assertEquals(source.size.toLong(), copied)
        assertArrayEquals(source, output.toByteArray())
    }

    @Test
    fun handlesEmptyInput() {
        val output = ByteArrayOutputStream()
        assertEquals(0L, TransferStreamCopy.copy(ByteArrayInputStream(byteArrayOf()), output))
        assertArrayEquals(byteArrayOf(), output.toByteArray())
    }

    @Test
    fun rejectsInvalidBufferSize() {
        assertThrows(IllegalArgumentException::class.java) {
            TransferStreamCopy.copy(ByteArrayInputStream(byteArrayOf(1)), ByteArrayOutputStream(), 0)
        }
    }
}
