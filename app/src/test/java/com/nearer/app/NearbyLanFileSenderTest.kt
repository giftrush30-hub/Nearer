package com.nearer.app

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.security.MessageDigest
import java.util.UUID

class NearbyLanFileSenderTest {
    @Test
    fun sendsMetadataChunksAndEndWithBoundedMemoryAndBackpressure() {
        val root = File(System.getProperty("java.io.tmpdir"), "nearer-send-${UUID.randomUUID()}")
        assertTrue(root.mkdirs())
        val source = File(root, "source.bin")
        val content = ByteArray(4 * 1024 * 1024 + 123) { (it % 251).toByte() }
        source.writeBytes(content)
        val digest = MessageDigest.getInstance("SHA-256").digest(content).toHex()
        val metadata = TransferResumeStreamMetadataCodec.Metadata(
            9L, "send-1",
            TransferBundleItem("item-1", "source.bin", "source.bin",
                content.size.toLong(), "application/octet-stream"),
            source.lastModified(), digest
        )
        val frames = mutableListOf<ByteArray>()
        val acknowledgements = mutableListOf<NearbyTransferFileChunkCodec.Ack>()
        var completed = false
        NearbyLanFileSender.send(
            source, metadata,
            NearbyLanFrameSink { frame, callback ->
                frames += frame
                callback(null)
            },
            NearbyLanAckWaiter { expected, callback ->
                acknowledgements += expected
                callback(Result.success(expected))
            }
        ) { result ->
            assertTrue(result.isSuccess)
            completed = true
        }
        assertTrue(completed)
        assertTrue(frames.size >= 5)
        assertEquals(metadata, TransferResumeStreamMetadataCodec.decode(frames.first()))
        val chunks = frames.drop(1).dropLast(1).map(NearbyTransferFileChunkCodec::decodeChunk)
        assertTrue(chunks.all { it.data.size <= NearbyTransferFileChunkCodec.MAX_CHUNK_BYTES })
        assertEquals(content.size.toLong(), chunks.sumOf { it.data.size.toLong() })
        val reconstructed = chunks.flatMap { it.data.asIterable() }.toByteArray()
        assertArrayEquals(content, reconstructed)
        val end = NearbyTransferFileChunkCodec.decodeEnd(frames.last())
        assertEquals(content.size.toLong(), end.totalSizeBytes)
        assertEquals(digest, end.wholeFileSha256)
        assertEquals(listOf(0L, 4L * 1024L * 1024L, content.size.toLong()),
            acknowledgements.map { it.committedOffsetBytes })
        root.deleteRecursively()
    }

    @Test
    fun rejectsSourceThatDoesNotMatchMetadataBeforeSendingAnything() {
        val root = File(System.getProperty("java.io.tmpdir"), "nearer-send-${UUID.randomUUID()}")
        assertTrue(root.mkdirs())
        val source = File(root, "source.bin")
        source.writeText("actual")
        val metadata = TransferResumeStreamMetadataCodec.Metadata(
            1L, "x", TransferBundleItem("i", "source.bin", "source.bin", 6L, null),
            null, "0".repeat(64)
        )
        val frames = mutableListOf<ByteArray>()
        var failed = false
        NearbyLanFileSender.send(
            source, metadata,
            NearbyLanFrameSink { frame, callback ->
                frames += frame
                callback(null)
            },
            NearbyLanAckWaiter { expected, callback ->
                callback(Result.success(expected.copy(committedOffsetBytes = expected.committedOffsetBytes + 1)))
            }
        ) { result -> failed = result.isFailure }
        assertTrue(failed)
        assertTrue(frames.isEmpty())
        root.deleteRecursively()
    }

    private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }
}
