package com.nearer.app

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException

/** Transfer offer and approval messages carried inside the authenticated Nearby control channel. */
internal object HyperTransferTcpOfferCodec {
    private const val OFFER_MAGIC = 0x4E524F31 // NRO1
    private const val DECISION_MAGIC = 0x4E524431 // NRD1
    private const val VERSION = 1
    private const val MAX_NAME_BYTES = 512
    private const val MAX_TRANSFER_ID_BYTES = 64
    private const val SHA256_BYTES = 64

    data class Offer(val transferId: String, val name: String, val sizeBytes: Long, val sha256: String)
    data class Decision(val transferId: String, val accepted: Boolean)

    fun encodeOffer(offer: Offer): ByteArray {
        validate(offer)
        val bytes = ByteArrayOutputStream()
        DataOutputStream(bytes).use { output ->
            output.writeInt(OFFER_MAGIC)
            output.writeByte(VERSION)
            writeString(output, offer.transferId.lowercase(), MAX_TRANSFER_ID_BYTES)
            writeString(output, offer.name, MAX_NAME_BYTES)
            output.writeLong(offer.sizeBytes)
            writeString(output, offer.sha256.lowercase(), SHA256_BYTES)
        }
        return bytes.toByteArray()
    }

    fun decodeOffer(bytes: ByteArray): Offer {
        val input = DataInputStream(ByteArrayInputStream(bytes))
        if (input.readInt() != OFFER_MAGIC) throw IOException("Invalid HyperTransfer offer")
        if (input.readUnsignedByte() != VERSION) throw IOException("Unsupported HyperTransfer offer version")
        val offer = Offer(
            readString(input, MAX_TRANSFER_ID_BYTES),
            readString(input, MAX_NAME_BYTES),
            input.readLong(),
            readString(input, SHA256_BYTES)
        )
        if (input.available() != 0) throw IOException("Unexpected bytes after HyperTransfer offer")
        try { validate(offer) } catch (error: IllegalArgumentException) {
            throw IOException("Invalid HyperTransfer offer fields", error)
        }
        return offer
    }

    fun encodeDecision(decision: Decision): ByteArray {
        require(decision.transferId.matches(Regex("^[a-fA-F0-9]{64}$")))
        val bytes = ByteArrayOutputStream()
        DataOutputStream(bytes).use { output ->
            output.writeInt(DECISION_MAGIC)
            output.writeByte(VERSION)
            writeString(output, decision.transferId.lowercase(), MAX_TRANSFER_ID_BYTES)
            output.writeBoolean(decision.accepted)
        }
        return bytes.toByteArray()
    }

    fun decodeDecision(bytes: ByteArray): Decision {
        val input = DataInputStream(ByteArrayInputStream(bytes))
        if (input.readInt() != DECISION_MAGIC) throw IOException("Invalid HyperTransfer decision")
        if (input.readUnsignedByte() != VERSION) throw IOException("Unsupported HyperTransfer decision version")
        val decision = Decision(readString(input, MAX_TRANSFER_ID_BYTES), input.readBoolean())
        if (input.available() != 0 || !decision.transferId.matches(Regex("^[a-fA-F0-9]{64}$"))) {
            throw IOException("Invalid HyperTransfer decision fields")
        }
        return decision
    }

    fun isOffer(bytes: ByteArray): Boolean = bytes.size >= 4 && readMagic(bytes) == OFFER_MAGIC
    fun isDecision(bytes: ByteArray): Boolean = bytes.size >= 4 && readMagic(bytes) == DECISION_MAGIC

    private fun readMagic(bytes: ByteArray): Int =
        ((bytes[0].toInt() and 0xff) shl 24) or
            ((bytes[1].toInt() and 0xff) shl 16) or
            ((bytes[2].toInt() and 0xff) shl 8) or
            (bytes[3].toInt() and 0xff)

    private fun validate(offer: Offer) {
        require(offer.transferId.matches(Regex("^[a-fA-F0-9]{64}$")))
        require(offer.name.isNotBlank() && offer.name.toByteArray(Charsets.UTF_8).size <= MAX_NAME_BYTES)
        require(offer.sizeBytes >= 0L)
        require(offer.sha256.matches(Regex("^[a-fA-F0-9]{64}$")))
    }

    private fun writeString(output: DataOutputStream, value: String, maximum: Int) {
        val bytes = value.toByteArray(Charsets.UTF_8)
        require(bytes.isNotEmpty() && bytes.size <= maximum)
        output.writeShort(bytes.size)
        output.write(bytes)
    }

    private fun readString(input: DataInputStream, maximum: Int): String {
        val length = input.readUnsignedShort()
        if (length !in 1..maximum) throw IOException("Invalid HyperTransfer text length")
        val bytes = ByteArray(length)
        input.readFully(bytes)
        return String(bytes, Charsets.UTF_8)
    }
}
