package com.nearer.app

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class NearbyTransferFileChunkCodecTest {
    private val digest = "0".repeat(64)

    @Test
    fun chunkMatchesSwiftGoldenVector() {
        val chunk = NearbyTransferFileChunkCodec.Chunk(7L, "x", "i", 0L, "hello".toByteArray())
        val encoded = NearbyTransferFileChunkCodec.encodeChunk(chunk)
        assertEquals("4e52464301000000000000000700017800016900000000000000000000000568656c6c6f",
            encoded.joinToString("") { "%02x".format(it) })
        assertEquals(chunk, NearbyTransferFileChunkCodec.decodeChunk(encoded))
    }

    @Test
    fun ackAndEndMatchSwiftGoldenVectors() {
        val ack = NearbyTransferFileChunkCodec.Ack(7L, "x", "i", 5L, digest)
        val expectedAck = "4e52464101000000000000000700017800016900000000000000050040" + "30".repeat(64)
        val ackBytes = NearbyTransferFileChunkCodec.encodeAck(ack)
        assertEquals(expectedAck, ackBytes.joinToString("") { "%02x".format(it) })
        assertEquals(ack, NearbyTransferFileChunkCodec.decodeAck(ackBytes))

        val end = NearbyTransferFileChunkCodec.End(7L, "x", "i", 5L, digest)
        val expectedEnd = "4e52464501000000000000000700017800016900000000000000050040" + "30".repeat(64)
        val endBytes = NearbyTransferFileChunkCodec.encodeEnd(end)
        assertEquals(expectedEnd, endBytes.joinToString("") { "%02x".format(it) })
        assertEquals(end, NearbyTransferFileChunkCodec.decodeEnd(endBytes))
    }

    @Test
    fun rejectsEmptyOversizedAndOverflowChunks() {
        assertThrows(IllegalArgumentException::class.java) {
            NearbyTransferFileChunkCodec.encodeChunk(
                NearbyTransferFileChunkCodec.Chunk(1L, "x", "i", 0L, byteArrayOf())
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            NearbyTransferFileChunkCodec.encodeChunk(
                NearbyTransferFileChunkCodec.Chunk(1L, "x", "i", 0L,
                    ByteArray(NearbyTransferFileChunkCodec.MAX_CHUNK_BYTES + 1))
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            NearbyTransferFileChunkCodec.encodeChunk(
                NearbyTransferFileChunkCodec.Chunk(1L, "x", "i", Long.MAX_VALUE, byteArrayOf(1))
            )
        }
    }

    @Test
    fun chunkBytesAreNotAliasedByDecoder() {
        val source = "hello".toByteArray()
        val decoded = NearbyTransferFileChunkCodec.decodeChunk(
            NearbyTransferFileChunkCodec.encodeChunk(
                NearbyTransferFileChunkCodec.Chunk(7L, "x", "i", 0L, source)
            )
        )
        source[0] = 0
        assertArrayEquals("hello".toByteArray(), decoded.data)
    }
}
