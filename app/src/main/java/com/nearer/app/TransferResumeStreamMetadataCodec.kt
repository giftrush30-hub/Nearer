package com.nearer.app

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction

/**
 * Bounded metadata envelope that binds a Nearby STREAM payload ID to the exact
 * one-file manifest and whole-file digest used by restart recovery.
 */
internal object TransferResumeStreamMetadataCodec {
    private const val MAGIC = 0x4E52534D // NRSM
    private const val VERSION = 1
    private const val MAX_MESSAGE_BYTES = 4_096
    private const val MAX_ID_BYTES = 128
    private const val MAX_NAME_BYTES = 255 * 4
    private const val MAX_PATH_BYTES = TransferBundleManifest.MAX_PATH_LENGTH * 4
    private const val MAX_MIME_BYTES = 255

    data class Metadata(
        val payloadId: Long,
        val transferId: String,
        val item: TransferBundleItem,
        val sourceModifiedAtMillis: Long?,
        val wholeFileSha256: String
    )

    fun isEnvelope(bytes: ByteArray): Boolean =
        bytes.size >= 4 && runCatching { DataInputStream(ByteArrayInputStream(bytes)).use { it.readInt() == MAGIC } }
            .getOrDefault(false)

    fun encode(metadata: Metadata): ByteArray {
        validate(metadata)
        val output = ByteArrayOutputStream()
        DataOutputStream(output).use { data ->
            data.writeInt(MAGIC)
            data.writeByte(VERSION)
            data.writeLong(metadata.payloadId)
            writeString(data, metadata.transferId, MAX_ID_BYTES)
            writeString(data, metadata.item.id, MAX_ID_BYTES)
            writeString(data, metadata.item.relativePath, MAX_PATH_BYTES)
            writeString(data, metadata.item.displayName, MAX_NAME_BYTES)
            data.writeLong(metadata.item.sizeBytes)
            data.writeBoolean(metadata.item.mimeType != null)
            metadata.item.mimeType?.let { writeString(data, it, MAX_MIME_BYTES) }
            data.writeBoolean(metadata.sourceModifiedAtMillis != null)
            metadata.sourceModifiedAtMillis?.let(data::writeLong)
            writeString(data, metadata.wholeFileSha256.lowercase(), 64)
        }
        return output.toByteArray().also {
            require(it.size <= MAX_MESSAGE_BYTES) { "Stream metadata is too large" }
        }
    }

    fun decode(bytes: ByteArray): Metadata {
        require(bytes.size in MIN_MESSAGE_BYTES..MAX_MESSAGE_BYTES) { "Invalid stream metadata length" }
        return try {
            DataInputStream(ByteArrayInputStream(bytes)).use { data ->
                require(data.readInt() == MAGIC) { "Invalid stream metadata signature" }
                require(data.readUnsignedByte() == VERSION) { "Unsupported stream metadata version" }
                val payloadId = data.readLong()
                val transferId = readString(data, MAX_ID_BYTES)
                val itemId = readString(data, MAX_ID_BYTES)
                val path = readString(data, MAX_PATH_BYTES)
                val name = readString(data, MAX_NAME_BYTES)
                val size = data.readLong()
                val mime = if (data.readBoolean()) readString(data, MAX_MIME_BYTES) else null
                val modifiedAt = if (data.readBoolean()) data.readLong() else null
                val digest = readString(data, 64)
                require(data.available() == 0) { "Unexpected trailing stream metadata" }
                val metadata = Metadata(
                    payloadId,
                    transferId,
                    TransferBundleItem(itemId, path, name, size, mime),
                    modifiedAt,
                    digest
                )
                validate(metadata)
                metadata.copy(wholeFileSha256 = digest.lowercase())
            }
        } catch (error: IllegalArgumentException) {
            throw error
        } catch (error: Exception) {
            throw IllegalArgumentException("Malformed stream metadata", error)
        }
    }

    fun validate(metadata: Metadata) {
        require(metadata.payloadId >= 0L) { "Invalid Nearby payload ID" }
        require(metadata.transferId.isNotBlank() && metadata.transferId.length <= MAX_ID_BYTES) {
            "Invalid transfer ID"
        }
        require(metadata.item.kind == TransferBundleItem.Kind.FILE) { "A stream must describe one file" }
        require(TransferBundleManifest(metadata.transferId, listOf(metadata.item)).validationErrors().isEmpty()) {
            "Invalid stream file metadata"
        }
        require(metadata.sourceModifiedAtMillis == null || metadata.sourceModifiedAtMillis >= 0L) {
            "Invalid source modification time"
        }
        require(metadata.wholeFileSha256.matches(Regex("^[a-fA-F0-9]{64}$"))) {
            "Invalid whole-file SHA-256"
        }
    }

    private fun writeString(output: DataOutputStream, value: String, maxBytes: Int) {
        val encoded = value.toByteArray(Charsets.UTF_8)
        require(encoded.isNotEmpty() && encoded.size <= maxBytes) { "Invalid stream metadata field length" }
        output.writeShort(encoded.size)
        output.write(encoded)
    }

    private fun readString(input: DataInputStream, maxBytes: Int): String {
        val length = input.readUnsignedShort()
        require(length in 1..maxBytes && length <= input.available()) { "Invalid stream metadata field length" }
        val bytes = ByteArray(length)
        input.readFully(bytes)
        return Charsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
            .decode(ByteBuffer.wrap(bytes)).toString()
    }

    private const val MIN_MESSAGE_BYTES = 4 + 1 + 8 + 2 * 4 + 8 + 1 + 1 + 2 + 64
}
