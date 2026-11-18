package com.nearer.app

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction

/** NRFC / NRFA / NRFE v1 records; byte-compatible with NeaRerTransferFileChunkCodecV1. */
internal object NearbyTransferFileChunkCodec {
    const val MAX_CHUNK_BYTES = 512 * 1024
    const val MAX_RECORD_BYTES = 1_048_576
    private const val CHUNK_MAGIC = 0x4E524643
    private const val ACK_MAGIC = 0x4E524641
    private const val END_MAGIC = 0x4E524645

    data class Chunk(
        val payloadId: Long,
        val transferId: String,
        val itemId: String,
        val offsetBytes: Long,
        val data: ByteArray
    ) {
        override fun equals(other: Any?): Boolean =
            other is Chunk && payloadId == other.payloadId && transferId == other.transferId &&
                itemId == other.itemId && offsetBytes == other.offsetBytes && data.contentEquals(other.data)
        override fun hashCode(): Int = 31 * payloadId.hashCode() + data.contentHashCode()
    }

    data class Ack(
        val payloadId: Long,
        val transferId: String,
        val itemId: String,
        val committedOffsetBytes: Long,
        val prefixSha256: String
    )

    data class End(
        val payloadId: Long,
        val transferId: String,
        val itemId: String,
        val totalSizeBytes: Long,
        val wholeFileSha256: String
    )

    fun encodeChunk(value: Chunk): ByteArray {
        validateIds(value.payloadId, value.transferId, value.itemId)
        require(value.offsetBytes >= 0L && value.data.isNotEmpty() &&
            value.data.size <= MAX_CHUNK_BYTES &&
            value.offsetBytes <= Long.MAX_VALUE - value.data.size.toLong())
        return writer {
            writeInt(CHUNK_MAGIC); writeByte(1); writeLong(value.payloadId)
            writeString16(value.transferId, 128); writeString16(value.itemId, 128)
            writeLong(value.offsetBytes); writeInt(value.data.size); write(value.data)
        }
    }

    fun decodeChunk(bytes: ByteArray): Chunk {
        require(bytes.size <= MAX_RECORD_BYTES)
        val input = reader(bytes, CHUNK_MAGIC)
        val payloadId = input.readLong()
        val transferId = input.readString16(128)
        val itemId = input.readString16(128)
        val offset = input.readLong()
        val length = input.readInt()
        require(payloadId >= 0L && offset >= 0L && length in 1..MAX_CHUNK_BYTES &&
            length == input.available() && offset <= Long.MAX_VALUE - length)
        val data = ByteArray(length).also(input::readFully)
        require(input.available() == 0)
        validateIds(payloadId, transferId, itemId)
        return Chunk(payloadId, transferId, itemId, offset, data)
    }

    fun encodeAck(value: Ack): ByteArray {
        validateIds(value.payloadId, value.transferId, value.itemId)
        require(value.committedOffsetBytes >= 0L && isSha256(value.prefixSha256))
        return writer {
            writeInt(ACK_MAGIC); writeByte(1); writeLong(value.payloadId)
            writeString16(value.transferId, 128); writeString16(value.itemId, 128)
            writeLong(value.committedOffsetBytes); writeString16(value.prefixSha256.lowercase(), 64)
        }
    }

    fun decodeAck(bytes: ByteArray): Ack {
        val input = reader(bytes, ACK_MAGIC)
        val payloadId = input.readLong()
        val transferId = input.readString16(128)
        val itemId = input.readString16(128)
        val offset = input.readLong()
        val digest = input.readString16(64)
        require(input.available() == 0 && payloadId >= 0L && offset >= 0L && isSha256(digest))
        validateIds(payloadId, transferId, itemId)
        return Ack(payloadId, transferId, itemId, offset, digest)
    }

    fun encodeEnd(value: End): ByteArray {
        validateIds(value.payloadId, value.transferId, value.itemId)
        require(value.totalSizeBytes >= 0L && isSha256(value.wholeFileSha256))
        return writer {
            writeInt(END_MAGIC); writeByte(1); writeLong(value.payloadId)
            writeString16(value.transferId, 128); writeString16(value.itemId, 128)
            writeLong(value.totalSizeBytes); writeString16(value.wholeFileSha256.lowercase(), 64)
        }
    }

    fun decodeEnd(bytes: ByteArray): End {
        val input = reader(bytes, END_MAGIC)
        val payloadId = input.readLong()
        val transferId = input.readString16(128)
        val itemId = input.readString16(128)
        val size = input.readLong()
        val digest = input.readString16(64)
        require(input.available() == 0 && payloadId >= 0L && size >= 0L && isSha256(digest))
        validateIds(payloadId, transferId, itemId)
        return End(payloadId, transferId, itemId, size, digest)
    }

    private fun reader(bytes: ByteArray, magic: Int): DataInputStream {
        require(bytes.size >= 5)
        val input = DataInputStream(ByteArrayInputStream(bytes))
        require(input.readInt() == magic) { "Invalid file stream record magic" }
        require(input.readUnsignedByte() == 1) { "Unsupported file stream record version" }
        return input
    }

    private fun writer(block: DataOutputStream.() -> Unit): ByteArray {
        val bytes = ByteArrayOutputStream()
        DataOutputStream(bytes).use(block)
        return bytes.toByteArray()
    }

    private fun DataOutputStream.writeString16(value: String, maxBytes: Int) {
        val encoded = value.toByteArray(Charsets.UTF_8)
        require(encoded.isNotEmpty() && encoded.size <= maxBytes && encoded.size <= 0xffff)
        writeShort(encoded.size)
        write(encoded)
    }

    private fun DataInputStream.readString16(maxBytes: Int): String {
        val length = readUnsignedShort()
        require(length in 1..maxBytes && length <= available())
        val bytes = ByteArray(length).also { readFully(it) }
        return Charsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
            .decode(ByteBuffer.wrap(bytes)).toString()
    }

    private fun validateIds(payloadId: Long, transferId: String, itemId: String) {
        require(payloadId >= 0L && transferId.isNotEmpty() &&
            transferId.toByteArray(Charsets.UTF_8).size <= 128 &&
            itemId.isNotEmpty() && itemId.toByteArray(Charsets.UTF_8).size <= 128)
    }

    private fun isSha256(value: String): Boolean = value.matches(Regex("^[a-f0-9]{64}$"))
}
