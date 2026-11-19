package com.nearer.app

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.DataInputStream

class NearbyLanFrameCodecTest {
    @Test
    fun frameMatchesSwiftBigEndianGoldenVector() {
        val actual = NearbyLanFrameCodec.encode("hello".toByteArray())
        assertEquals("0000000568656c6c6f", actual.joinToString("") { "%02x".format(it) })
    }

    @Test
    fun readHandlesMultipleFramesFromOneStream() {
        val bytes = NearbyLanFrameCodec.encode("one".toByteArray()) +
            NearbyLanFrameCodec.encode("two".toByteArray())
        val input = DataInputStream(ByteArrayInputStream(bytes))
        assertArrayEquals("one".toByteArray(), NearbyLanFrameCodec.read(input))
        assertArrayEquals("two".toByteArray(), NearbyLanFrameCodec.read(input))
    }

    @Test
    fun rejectsEmptyAndOversizedFrames() {
        assertThrows(IllegalArgumentException::class.java) {
            NearbyLanFrameCodec.encode(byteArrayOf())
        }
        assertThrows(IllegalArgumentException::class.java) {
            NearbyLanFrameCodec.encode(ByteArray(NearbyLanFrameCodec.MAX_FRAME_BYTES + 1))
        }
    }
}
