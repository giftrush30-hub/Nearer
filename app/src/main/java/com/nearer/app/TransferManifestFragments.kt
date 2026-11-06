package com.nearer.app

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream

/**
 * Splits a bounded bundle manifest into Nearby BYTES payloads.
 * A 24 KiB ceiling stays comfortably below Nearby's small-message limit.
 */
internal object TransferManifestFragments {
    const val MAX_FRAGMENT_BYTES = 24 * 1024
    const val MAX_MANIFEST_BYTES = TransferBundleManifestCodec.MAX_ENCODED_BYTES
    private const val MAGIC = 0x4E524631 // NRF1
    private const val MAX_TRANSFER_ID_BYTES = 128

    data class Fragment(
        val transferId: String,
        val index: Int,
        val count: Int,
        val totalBytes: Int,
        val bytes: ByteArray
    )

    fun split(transferId: String, manifestBytes: ByteArray): List<ByteArray> {
        require(transferId.isNotBlank() && transferId.length <= MAX_TRANSFER_ID_BYTES)
        require(manifestBytes.isNotEmpty() && manifestBytes.size <= MAX_MANIFEST_BYTES)
        val idBytes = transferId.toByteArray(Charsets.UTF_8)
        require(idBytes.size <= MAX_TRANSFER_ID_BYTES)
        val headerBytes = 4 + 2 + idBytes.size + 4 + 4 + 4 + 4
        val maxPartBytes = MAX_FRAGMENT_BYTES - headerBytes
        require(maxPartBytes > 0)
        val count = (manifestBytes.size + maxPartBytes - 1) / maxPartBytes
        return (0 until count).map { index ->
            val start = index * maxPartBytes
            val part = manifestBytes.copyOfRange(start, minOf(start + maxPartBytes, manifestBytes.size))
            val output = ByteArrayOutputStream(headerBytes + part.size)
            DataOutputStream(output).use { data ->
                data.writeInt(MAGIC)
                data.writeShort(idBytes.size)
                data.write(idBytes)
                data.writeInt(index)
                data.writeInt(count)
                data.writeInt(manifestBytes.size)
                data.writeInt(part.size)
                data.write(part)
            }
            output.toByteArray().also { require(it.size <= MAX_FRAGMENT_BYTES) }
        }
    }

    fun decode(bytes: ByteArray): Fragment {
        require(bytes.size in 1..MAX_FRAGMENT_BYTES) { "Manifest fragment size is invalid" }
        val input = DataInputStream(ByteArrayInputStream(bytes))
        require(input.readInt() == MAGIC) { "Invalid manifest fragment signature" }
        val idLength = input.readUnsignedShort()
        require(idLength in 1..MAX_TRANSFER_ID_BYTES && idLength <= input.available())
        val idBytes = ByteArray(idLength)
        input.readFully(idBytes)
        val transferId = idBytes.toString(Charsets.UTF_8)
        require(transferId.isNotBlank() && !transferId.any { it.isISOControl() })
        val index = input.readInt()
        val count = input.readInt()
        val totalBytes = input.readInt()
        val partLength = input.readInt()
        require(totalBytes in 1..MAX_MANIFEST_BYTES)
        require(count in 1..totalBytes)
        require(index in 0 until count)
        require(partLength in 1..input.available() && partLength == input.available())
        val part = ByteArray(partLength)
        input.readFully(part)
        return Fragment(transferId, index, count, totalBytes, part)
    }
}

/**
 * Bounded, order-independent manifest reassembly. The caller should create one
 * instance per connected sender/transfer and expire it on disconnect or timeout.
 */
internal class TransferManifestReassembler {
    private var transferId: String? = null
    private var expectedCount = 0
    private var expectedBytes = 0
    private val parts = HashMap<Int, ByteArray>()

    @Synchronized
    fun accept(encodedFragment: ByteArray): ByteArray? {
        val fragment = TransferManifestFragments.decode(encodedFragment)
        if (transferId == null) {
            transferId = fragment.transferId
            expectedCount = fragment.count
            expectedBytes = fragment.totalBytes
        }
        require(transferId == fragment.transferId &&
            expectedCount == fragment.count && expectedBytes == fragment.totalBytes) {
            "Manifest fragment does not belong to this transfer"
        }
        val previous = parts[fragment.index]
        if (previous != null) {
            require(previous.contentEquals(fragment.bytes)) { "Conflicting duplicate manifest fragment" }
            return null
        }
        parts[fragment.index] = fragment.bytes
        if (parts.size != expectedCount) return null

        val output = ByteArrayOutputStream(expectedBytes)
        for (index in 0 until expectedCount) {
            output.write(parts[index] ?: return null)
        }
        val assembled = output.toByteArray()
        require(assembled.size == expectedBytes) { "Reassembled manifest length mismatch" }
        reset()
        return assembled
    }

    @Synchronized
    fun reset() {
        parts.clear()
        transferId = null
        expectedCount = 0
        expectedBytes = 0
    }
}
