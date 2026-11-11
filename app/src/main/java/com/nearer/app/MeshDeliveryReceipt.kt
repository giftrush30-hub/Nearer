package com.nearer.app

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.security.KeyFactory
import java.security.Signature
import java.security.spec.X509EncodedKeySpec

/** Destination-signed final-delivery proof; a hop acknowledgement is not this receipt. */
internal data class MeshDeliveryReceipt(
    val packetId: String,
    val originId: String,
    val destinationId: String,
    val deliveredAtMs: Long,
    val signature: ByteArray
) {
    fun signedBytes(): ByteArray = listOf(
        "NeaRer-delivery-receipt-v1", packetId, originId, destinationId, deliveredAtMs.toString()
    ).joinToString("\n").toByteArray(Charsets.UTF_8)

    fun verify(trustedDestinationKey: ByteArray, nowMs: Long): Boolean = try {
        require(isValidHeader())
        require(deliveredAtMs <= nowMs || deliveredAtMs - nowMs <= MAX_CLOCK_SKEW_MS)
        require(signature.size in MIN_SIGNATURE_BYTES..MAX_SIGNATURE_BYTES)
        val key = KeyFactory.getInstance("EC").generatePublic(X509EncodedKeySpec(trustedDestinationKey))
        require((key as? java.security.interfaces.ECPublicKey)?.params?.curve?.field?.fieldSize == 256)
        val verifier = Signature.getInstance("SHA256withECDSA")
        verifier.initVerify(key)
        verifier.update(signedBytes())
        verifier.verify(signature)
    } catch (_: Exception) { false }

    /** Bounded binary transport format; decoding never implies authenticity. */
    fun encode(): ByteArray {
        require(isValidHeader())
        require(signature.size in MIN_SIGNATURE_BYTES..MAX_SIGNATURE_BYTES)
        val bytes = ByteArrayOutputStream()
        DataOutputStream(bytes).use { out ->
            out.writeInt(MAGIC)
            writeString(out, packetId)
            writeString(out, originId)
            writeString(out, destinationId)
            out.writeLong(deliveredAtMs)
            out.writeShort(signature.size)
            out.write(signature)
        }
        return bytes.toByteArray().also { require(it.size <= MAX_ENCODED_BYTES) }
    }

    private fun isValidHeader(): Boolean =
        packetId.matches(PACKET_ID_PATTERN) &&
            NeaRerIdentity.normalize(originId) == originId &&
            NeaRerIdentity.normalize(destinationId) == destinationId &&
            originId != destinationId && deliveredAtMs > 0L

    companion object {
        private const val MAGIC = 0x4E524431 // NRD1
        private const val MAX_CLOCK_SKEW_MS = 300_000L
        private const val MAX_ENCODED_BYTES = 2048
        private const val MIN_SIGNATURE_BYTES = 8
        private const val MAX_SIGNATURE_BYTES = 128
        private val PACKET_ID_PATTERN = Regex("[A-Za-z0-9._:-]{1,80}")

        private fun writeString(out: DataOutputStream, value: String) {
            val encoded = value.toByteArray(Charsets.UTF_8)
            require(encoded.isNotEmpty() && encoded.size <= 80)
            out.writeShort(encoded.size)
            out.write(encoded)
        }

        private fun readString(input: DataInputStream): String {
            val size = input.readUnsignedShort()
            require(size in 1..80)
            val encoded = ByteArray(size)
            input.readFully(encoded)
            return encoded.toString(Charsets.UTF_8)
        }

        fun decode(bytes: ByteArray): MeshDeliveryReceipt {
            require(bytes.size in 1..MAX_ENCODED_BYTES)
            val input = DataInputStream(ByteArrayInputStream(bytes))
            require(input.readInt() == MAGIC)
            val packetId = readString(input)
            val originId = readString(input)
            val destinationId = readString(input)
            val deliveredAtMs = input.readLong()
            val signatureSize = input.readUnsignedShort()
            require(signatureSize in MIN_SIGNATURE_BYTES..MAX_SIGNATURE_BYTES)
            val signature = ByteArray(signatureSize)
            input.readFully(signature)
            require(input.available() == 0)
            return MeshDeliveryReceipt(packetId, originId, destinationId, deliveredAtMs, signature)
                .also { require(it.isValidHeader()) }
        }

        fun create(
            packetId: String, originId: String, destinationId: String,
            deliveredAtMs: Long, sign: (ByteArray) -> ByteArray
        ): MeshDeliveryReceipt {
            val unsigned = MeshDeliveryReceipt(packetId, originId, destinationId, deliveredAtMs, ByteArray(0))
            require(unsigned.isValidHeader())
            val signature = sign(unsigned.signedBytes())
            require(signature.size in MIN_SIGNATURE_BYTES..MAX_SIGNATURE_BYTES)
            return unsigned.copy(signature = signature)
        }
    }
}
