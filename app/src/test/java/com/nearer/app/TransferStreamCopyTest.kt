package com.nearer.app

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class TransferStreamCopyTest {
    @Test fun copiesBytesAndReturnsLongByteCount() {
        val source = "large-file-stream".toByteArray()
        val output = ByteArrayOutputStream()
        val copied = TransferStreamCopy.copy(ByteArrayInputStream(source), output, bufferSize = 3)
        assertEquals(source.size.toLong(), copied)
        assertArrayEquals(source, output.toByteArray())
    }

    @Test fun copiesAndHashesInOnePass() {
        val source = "abc".toByteArray()
        val output = ByteArrayOutputStream()
        val result = TransferStreamCopy.copyAndSha256(ByteArrayInputStream(source), output, bufferSize = 2)
        assertEquals(3L, result.bytesCopied)
        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad", result.sha256)
        assertArrayEquals(source, output.toByteArray())
    }

    @Test fun fallsBackToSingleByteReadsWhenBulkReadReturnsZero() {
        val source = "provider-stream".toByteArray()
        val input = object : InputStream() {
            private val delegate = ByteArrayInputStream(source)
            override fun read(): Int = delegate.read()
            override fun read(buffer: ByteArray, offset: Int, length: Int): Int = 0
        }
        val output = ByteArrayOutputStream()
        val result = TransferStreamCopy.copyAndSha256(input, output, bufferSize = 8)
        val expected = java.security.MessageDigest.getInstance("SHA-256").digest(source)
            .joinToString("") { "%02x".format(it.toInt() and 0xff) }
        assertEquals(source.size.toLong(), result.bytesCopied)
        assertEquals(expected, result.sha256)
        assertArrayEquals(source, output.toByteArray())
    }

    @Test fun handlesEmptyInput() {
        val output = ByteArrayOutputStream()
        val result = TransferStreamCopy.copyAndSha256(ByteArrayInputStream(byteArrayOf()), output)
        assertEquals(0L, result.bytesCopied)
        assertEquals("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855", result.sha256)
        assertArrayEquals(byteArrayOf(), output.toByteArray())
    }

    @Test fun rejectsInvalidBufferSize() {
        assertThrows(IllegalArgumentException::class.java) {
            TransferStreamCopy.copy(ByteArrayInputStream(byteArrayOf(1)), ByteArrayOutputStream(), 0)
        }
    }
}
