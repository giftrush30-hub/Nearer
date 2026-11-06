package com.nearer.app

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream

/**
 * Compact, bounded binary wire codec for bundle metadata.
 * Payload bytes are transferred separately; this header contains metadata only.
 */
internal object TransferBundleManifestCodec {
    private const val MAGIC = 0x4E524231 // "NRB1"
    const val MAX_ENCODED_BYTES = 1_048_576
    private const val MAX_ID_BYTES = 512
    private const val MAX_NAME_BYTES = 1_024
    private const val MAX_MIME_BYTES = 1_024

    fun encode(manifest: TransferBundleManifest): ByteArray {
        val errors = manifest.validationErrors()
        require(errors.isEmpty()) { errors.joinToString("; ") }

        val output = ByteArrayOutputStream()
        DataOutputStream(output).use { data ->
            data.writeInt(MAGIC)
            data.writeInt(manifest.protocolVersion)
            writeString(data, manifest.transferId, MAX_ID_BYTES)
            data.writeInt(manifest.items.size)
            manifest.items.forEach { item ->
                writeString(data, item.id, MAX_ID_BYTES)
                writeString(data, item.relativePath, MAX_NAME_BYTES)
                writeString(data, item.displayName, MAX_NAME_BYTES)
                data.writeLong(item.sizeBytes)
                data.writeBoolean(item.mimeType != null)
                item.mimeType?.let { writeString(data, it, MAX_MIME_BYTES) }
                data.writeByte(item.kind.ordinal)
                require(output.size() <= MAX_ENCODED_BYTES) { "Transfer manifest is too large" }
            }
        }
        val bytes = output.toByteArray()
        require(bytes.size <= MAX_ENCODED_BYTES) { "Transfer manifest is too large" }
        return bytes
    }

    fun decode(bytes: ByteArray): TransferBundleManifest {
        require(bytes.isNotEmpty() && bytes.size <= MAX_ENCODED_BYTES) {
            "Transfer manifest size is invalid"
        }
        val data = DataInputStream(ByteArrayInputStream(bytes))
        require(data.readInt() == MAGIC) { "Invalid transfer manifest signature" }
        val version = data.readInt()
        val transferId = readString(data, MAX_ID_BYTES)
        val count = data.readInt()
        require(count in 1..TransferBundleManifest.MAX_ITEMS) { "Transfer bundle item count is invalid" }

        val items = ArrayList<TransferBundleItem>(count)
        repeat(count) {
            val id = readString(data, MAX_ID_BYTES)
            val path = readString(data, MAX_NAME_BYTES)
            val name = readString(data, MAX_NAME_BYTES)
            val size = data.readLong()
            val mime = if (data.readBoolean()) readString(data, MAX_MIME_BYTES) else null
            val kindOrdinal = data.readUnsignedByte()
            val kind = TransferBundleItem.Kind.values().getOrNull(kindOrdinal)
                ?: throw IllegalArgumentException("Unknown transfer item kind")
            items += TransferBundleItem(id, path, name, size, mime, kind)
        }
        require(data.available() == 0) { "Unexpected trailing transfer manifest data" }

        val manifest = TransferBundleManifest(transferId, items, version)
        val errors = manifest.validationErrors()
        require(errors.isEmpty()) { errors.joinToString("; ") }
        return manifest
    }

    private fun writeString(data: DataOutputStream, value: String, maxBytes: Int) {
        val bytes = value.toByteArray(Charsets.UTF_8)
        require(bytes.size <= maxBytes) { "Transfer manifest text field is too large" }
        data.writeInt(bytes.size)
        data.write(bytes)
    }

    private fun readString(data: DataInputStream, maxBytes: Int): String {
        val length = data.readInt()
        require(length in 0..maxBytes && length <= data.available()) { "Invalid transfer manifest text length" }
        val bytes = ByteArray(length)
        data.readFully(bytes)
        return bytes.toString(Charsets.UTF_8)
    }
}
