package com.nearer.app

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class TransferDigestTest {
    @Test
    fun copiesBytesAndCalculatesSha256InOnePass() {
        val source = "abc".toByteArray(Charsets.UTF_8)
        val destination = ByteArrayOutputStream()

        val result = TransferDigest.copyAndSha256(
            ByteArrayInputStream(source),
            destination,
            bufferSize = 2
        )

        assertEquals(3L, result.bytesCopied)
        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad", result.sha256)
        assertArrayEquals(source, destination.toByteArray())
    }

    @Test
    fun handlesEmptyInput() {
        val destination = ByteArrayOutputStream()

        val result = TransferDigest.copyAndSha256(ByteArrayInputStream(byteArrayOf()), destination)

        assertEquals(0L, result.bytesCopied)
        assertEquals("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855", result.sha256)
        assertArrayEquals(byteArrayOf(), destination.toByteArray())
    }

    @Test
    fun rejectsInvalidBufferSize() {
        assertThrows(IllegalArgumentException::class.java) {
            TransferDigest.copyAndSha256(ByteArrayInputStream(byteArrayOf(1)), ByteArrayOutputStream(), 0)
        }
    }
}
