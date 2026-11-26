package com.nearer.app

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertThrows
import org.junit.Test
import java.io.File
import java.security.MessageDigest
import java.util.UUID

class NearbyLanFileReceiverTest {
    @Test
    fun receivesSequentialChunksAndAtomicallyFinalizesVerifiedFile() {
        val root = File(System.getProperty("java.io.tmpdir"), "nearer-lan-${UUID.randomUUID()}")
        val destination = File(root, "received")
        val bytes = ByteArray(1_300_123) { (it % 251).toByte() }
        val digest = MessageDigest.getInstance("SHA-256").digest(bytes).toHex()
        val metadata = TransferResumeStreamMetadataCodec.Metadata(
            payloadId = 7L,
            transferId = "transfer-1",
            item = TransferBundleItem("item-1", "folder/sample.bin", "sample.bin",
                bytes.size.toLong(), "application/octet-stream"),
            sourceModifiedAtMillis = null,
            wholeFileSha256 = digest
        )
        val receiver = NearbyLanFileReceiver(metadata, destination, checkpointIntervalBytes = 512L * 1024L)
        var offset = 0L
        while (offset < bytes.size) {
            val count = minOf(128 * 1024, bytes.size - offset.toInt())
            val chunk = NearbyTransferFileChunkCodec.Chunk(
                7L, "transfer-1", "item-1", offset,
                bytes.copyOfRange(offset.toInt(), offset.toInt() + count)
            )
            receiver.append(chunk)
            offset += count
        }
        val finalized = receiver.finish(
            NearbyTransferFileChunkCodec.End(7L, "transfer-1", "item-1", bytes.size.toLong(), digest)
        )
        assertArrayEquals(bytes, finalized.file.readBytes())
        assertEquals(bytes.size.toLong(), finalized.finalAck.committedOffsetBytes)
        assertEquals(digest, finalized.finalAck.prefixSha256)
        root.deleteRecursively()
    }

    @Test
    fun rejectsOutOfOrderAndWrongTransferChunks() {
        val root = File(System.getProperty("java.io.tmpdir"), "nearer-lan-${UUID.randomUUID()}")
        val metadata = TransferResumeStreamMetadataCodec.Metadata(
            1L, "x", TransferBundleItem("i", "a.bin", "a.bin", 5L, null), null,
            MessageDigest.getInstance("SHA-256").digest("hello".toByteArray()).toHex()
        )
        val receiver = NearbyLanFileReceiver(metadata, root)
        assertThrows(IllegalArgumentException::class.java) {
            receiver.append(NearbyTransferFileChunkCodec.Chunk(1L, "x", "i", 1L, byteArrayOf(1)))
        }
        assertThrows(IllegalArgumentException::class.java) {
            receiver.append(NearbyTransferFileChunkCodec.Chunk(2L, "x", "i", 0L, byteArrayOf(1)))
        }
        receiver.close()
        root.deleteRecursively()
    }

    @Test
    fun emitsDurablePrefixAcknowledgementAtCheckpoint() {
        val root = File(System.getProperty("java.io.tmpdir"), "nearer-lan-${UUID.randomUUID()}")
        val bytes = ByteArray(16) { it.toByte() }
        val digest = MessageDigest.getInstance("SHA-256").digest(bytes).toHex()
        val metadata = TransferResumeStreamMetadataCodec.Metadata(
            2L, "t", TransferBundleItem("i", "f.bin", "f.bin", bytes.size.toLong(), null), null, digest
        )
        val receiver = NearbyLanFileReceiver(metadata, root, checkpointIntervalBytes = 8L)
        val firstAck = receiver.append(
            NearbyTransferFileChunkCodec.Chunk(2L, "t", "i", 0L, bytes.copyOfRange(0, 8))
        )
        assertNotNull(firstAck)
        assertEquals(8L, firstAck!!.committedOffsetBytes)
        receiver.append(
            NearbyTransferFileChunkCodec.Chunk(2L, "t", "i", 8L, bytes.copyOfRange(8, 16))
        )
        receiver.finish(NearbyTransferFileChunkCodec.End(2L, "t", "i", 16L, digest))
        root.deleteRecursively()
    }

    private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }
}
