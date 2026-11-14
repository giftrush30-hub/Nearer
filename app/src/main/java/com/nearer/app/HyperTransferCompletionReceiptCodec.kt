package com.nearer.app

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream

/**
 * Authenticated application-level confirmation that a HyperTransfer STREAM
 * was fully verified and published (or could not be published) by its receiver.
 * The surrounding Nearby control envelope provides peer authentication/encryption.
 */
internal object HyperTransferCompletionReceiptCodec {
    const val MAGIC: Int = 0x4E525243 // NRRC
    private const val MAX_ID_LENGTH = 256

    data class Receipt(
        val payloadId: Long,
        val transferId: String,
        val itemId: String,
        val sha256: String,
        val saved: Boolean
    )

    fun encode(receipt: Receipt): ByteArray {
        validate(receipt)
        val bytes = ByteArrayOutputStream()
        DataOutputStream(bytes).use { out ->
            out.writeInt(MAGIC)
            out.writeLong(receipt.payloadId)
            out.writeUTF(receipt.transferId)
            out.writeUTF(receipt.itemId)
            out.writeUTF(receipt.sha256.lowercase())
            out.writeBoolean(receipt.saved)
        }
        return bytes.toByteArray()
    }

    fun decode(bytes: ByteArray): Receipt {
        require(bytes.size in 4..1024) { "Invalid completion receipt size" }
        val input = DataInputStream(ByteArrayInputStream(bytes))
        val magic = input.readInt()
        require(magic == MAGIC) { "Invalid completion receipt header" }
        val receipt = Receipt(
            payloadId = input.readLong(),
            transferId = input.readUTF(),
            itemId = input.readUTF(),
            sha256 = input.readUTF(),
            saved = input.readBoolean()
        )
        require(input.available() == 0) { "Unexpected completion receipt data" }
        validate(receipt)
        return receipt.copy(sha256 = receipt.sha256.lowercase())
    }

    private fun validate(receipt: Receipt) {
        require(receipt.payloadId > 0L) { "Invalid completion receipt payload ID" }
        require(receipt.transferId.isNotBlank() && receipt.transferId.length <= MAX_ID_LENGTH) {
            "Invalid completion receipt transfer ID"
        }
        require(receipt.itemId.isNotBlank() && receipt.itemId.length <= MAX_ID_LENGTH) {
            "Invalid completion receipt item ID"
        }
        require(receipt.sha256.matches(Regex("[0-9a-fA-F]{64}"))) {
            "Invalid completion receipt digest"
        }
    }
}
