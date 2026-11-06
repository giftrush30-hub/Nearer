package com.nearer.app

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction

/**
 * Small, versioned on-disk representation of a resume checkpoint.
 * This is local journal data, not a network wire message.
 */
internal object TransferResumeCheckpointCodec {
    private const val MAGIC = 0x4E524331 // "NRC1"
    private const val VERSION = 1
    private const val MAX_ENCODED_BYTES = 4_096
    private const val MAX_ID_BYTES = 512
    private const val MAX_PATH_BYTES = TransferBundleManifest.MAX_PATH_LENGTH * 4

    fun encode(checkpoint: TransferResumeCheckpoint): ByteArray {
        require(checkpoint.validationError() == null) { checkpoint.validationError() ?: "Invalid checkpoint" }
        val output = ByteArrayOutputStream()
        DataOutputStream(output).use { data ->
            data.writeInt(MAGIC)
            data.writeInt(VERSION)
            writeString(data, checkpoint.transferId, MAX_ID_BYTES)
            writeString(data, checkpoint.itemId, MAX_ID_BYTES)
            writeString(data, checkpoint.relativePath, MAX_PATH_BYTES)
            data.writeLong(checkpoint.expectedSizeBytes)
            data.writeBoolean(checkpoint.sourceModifiedAtMillis != null)
            checkpoint.sourceModifiedAtMillis?.let(data::writeLong)
            data.writeLong(checkpoint.committedOffsetBytes)
            writeString(data, checkpoint.prefixSha256.lowercase(), 64)
        }
        return output.toByteArray().also {
            require(it.size <= MAX_ENCODED_BYTES) { "Resume checkpoint is too large" }
        }
    }

    fun decode(bytes: ByteArray): TransferResumeCheckpoint {
        require(bytes.isNotEmpty() && bytes.size <= MAX_ENCODED_BYTES) {
            "Resume checkpoint size is invalid"
        }
        val data = DataInputStream(ByteArrayInputStream(bytes))
        require(data.readInt() == MAGIC) { "Invalid resume checkpoint signature" }
        require(data.readInt() == VERSION) { "Unsupported resume checkpoint version" }
        val transferId = readString(data, MAX_ID_BYTES)
        val itemId = readString(data, MAX_ID_BYTES)
        val path = readString(data, MAX_PATH_BYTES)
        val size = data.readLong()
        val modifiedAt = if (data.readBoolean()) data.readLong() else null
        val offset = data.readLong()
        val digest = readString(data, 64)
        require(data.available() == 0) { "Unexpected trailing resume checkpoint data" }
        val checkpoint = TransferResumeCheckpoint(
            transferId, itemId, path, size, modifiedAt, offset, digest
        )
        val error = checkpoint.validationError()
        require(error == null) { error ?: "Invalid resume checkpoint" }
        return checkpoint
    }

    private fun writeString(data: DataOutputStream, value: String, maxBytes: Int) {
        val bytes = value.toByteArray(Charsets.UTF_8)
        require(bytes.size <= maxBytes) { "Resume checkpoint text field is too large" }
        data.writeInt(bytes.size)
        data.write(bytes)
    }

    private fun readString(data: DataInputStream, maxBytes: Int): String {
        val length = data.readInt()
        require(length in 0..maxBytes && length <= data.available()) {
            "Invalid resume checkpoint text length"
        }
        val bytes = ByteArray(length)
        data.readFully(bytes)
        return Charsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
            .decode(ByteBuffer.wrap(bytes))
            .toString()
    }
}
