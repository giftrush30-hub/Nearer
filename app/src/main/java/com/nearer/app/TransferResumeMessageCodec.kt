package com.nearer.app

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction

/** Bounded, versioned binary envelope for a receiver's resume-offset proposal. */
internal object TransferResumeMessageCodec {
    private const val MAGIC = 0x4E525253 // NRRS
    private const val VERSION = 1
    private const val MAX_MESSAGE_BYTES = 4_096
    private const val MAX_ID_BYTES = 128
    private const val MAX_PATH_BYTES = TransferBundleManifest.MAX_PATH_LENGTH * 4

    data class Envelope(
        val payloadId: Long,
        val proposal: TransferResumeNegotiation.Proposal
    )

    fun encode(payloadId: Long, proposal: TransferResumeNegotiation.Proposal): ByteArray {
        require(payloadId >= 0L) { "Invalid Nearby payload ID" }
        require(proposal.transferId.isNotBlank() && proposal.transferId.length <= 128)
        require(proposal.itemId.isNotBlank() && proposal.itemId.length <= 128)
        require(proposal.relativePath.isNotBlank() && proposal.relativePath.length <= TransferBundleManifest.MAX_PATH_LENGTH)
        require(proposal.expectedSizeBytes >= 0L)
        require(proposal.sourceModifiedAtMillis == null || proposal.sourceModifiedAtMillis >= 0L)
        require(proposal.committedOffsetBytes > 0L &&
            proposal.committedOffsetBytes < proposal.expectedSizeBytes &&
            proposal.committedOffsetBytes % TransferResumeCheckpoint.CHECKPOINT_GRANULARITY_BYTES == 0L)
        require(proposal.prefixSha256.matches(Regex("^[a-fA-F0-9]{64}$")))
        val output = ByteArrayOutputStream()
        DataOutputStream(output).use { data ->
            data.writeInt(MAGIC)
            data.writeByte(VERSION)
            data.writeLong(payloadId)
            writeString(data, proposal.transferId, MAX_ID_BYTES)
            writeString(data, proposal.itemId, MAX_ID_BYTES)
            writeString(data, proposal.relativePath, MAX_PATH_BYTES)
            data.writeLong(proposal.expectedSizeBytes)
            data.writeBoolean(proposal.sourceModifiedAtMillis != null)
            proposal.sourceModifiedAtMillis?.let(data::writeLong)
            data.writeLong(proposal.committedOffsetBytes)
            writeString(data, proposal.prefixSha256.lowercase(), 64)
        }
        return output.toByteArray().also {
            require(it.size <= MAX_MESSAGE_BYTES) { "Resume proposal is too large" }
        }
    }

    fun decode(bytes: ByteArray): Envelope {
        require(bytes.size in MIN_MESSAGE_BYTES..MAX_MESSAGE_BYTES) { "Invalid resume proposal length" }
        return try { DataInputStream(ByteArrayInputStream(bytes)).use { data ->
            require(data.readInt() == MAGIC) { "Invalid resume proposal signature" }
            require(data.readUnsignedByte() == VERSION) { "Unsupported resume proposal version" }
            val payloadId = data.readLong()
            require(payloadId >= 0L) { "Invalid Nearby payload ID" }
            val transferId = readString(data, MAX_ID_BYTES)
            val itemId = readString(data, MAX_ID_BYTES)
            val path = readString(data, MAX_PATH_BYTES)
            val size = data.readLong()
            val modifiedAt = if (data.readBoolean()) data.readLong() else null
            val offset = data.readLong()
            val digest = readString(data, 64)
            require(data.available() == 0) { "Unexpected trailing resume proposal data" }
            val proposal = TransferResumeNegotiation.Proposal(
                transferId, itemId, path, size, modifiedAt, offset, digest
            )
            require(size >= 0L && (modifiedAt == null || modifiedAt >= 0L))
            require(offset > 0L && offset < size &&
                offset % TransferResumeCheckpoint.CHECKPOINT_GRANULARITY_BYTES == 0L)
            require(digest.matches(Regex("^[a-fA-F0-9]{64}$")))
            require(transferId.isNotBlank() && itemId.isNotBlank() && path.isNotBlank())
            Envelope(payloadId, proposal.copy(prefixSha256 = digest.lowercase()))
        } } catch (error: IllegalArgumentException) {
            throw error
        } catch (error: Exception) {
            throw IllegalArgumentException("Malformed resume proposal", error)
        }
    }

    private fun writeString(output: DataOutputStream, value: String, maxBytes: Int) {
        val encoded = value.toByteArray(Charsets.UTF_8)
        require(encoded.isNotEmpty() && encoded.size <= maxBytes) { "Resume field is too large or empty" }
        output.writeShort(encoded.size)
        output.write(encoded)
    }

    private fun readString(input: DataInputStream, maxBytes: Int): String {
        val length = input.readUnsignedShort()
        require(length in 1..maxBytes && length <= input.available()) { "Invalid resume field length" }
        val bytes = ByteArray(length)
        input.readFully(bytes)
        return Charsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
            .decode(ByteBuffer.wrap(bytes)).toString()
    }

    private const val MIN_MESSAGE_BYTES = 4 + 1 + 8 + 2 * 4 + 8 + 1 + 8 + 2 + 64
}
