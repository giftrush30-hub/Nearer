package com.nearer.app

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.security.KeyFactory
import java.security.Signature
import java.security.spec.X509EncodedKeySpec

/**
 * Signed proof that the next hop durably accepted an opaque packet.
 * This is deliberately distinct from a destination delivery receipt.
 */
internal data class MeshHopAcknowledgement(
    val packetId: String,
    val acceptingPeerId: String,
    val previousHopPeerId: String,
    val acceptedAtMs: Long,
    val signature: ByteArray
) {
    fun signedBytes(): ByteArray {
        val bytes = ByteArrayOutputStream()
        DataOutputStream(bytes).use { out ->
            out.write(DOMAIN.toByteArray(Charsets.US_ASCII))
            writeString(out, packetId)
            writeString(out, acceptingPeerId)
            writeString(out, previousHopPeerId)
            out.writeLong(acceptedAtMs)
        }
        return bytes.toByteArray()
    }

    fun verify(trustedAcceptingPeerKey: ByteArray, expectedPreviousHopId: String, nowMs: Long): Boolean = try {
        require(isValidHeader())
        require(previousHopPeerId == expectedPreviousHopId)
        require(acceptedAtMs <= nowMs || acceptedAtMs - nowMs <= MAX_CLOCK_SKEW_MS)
        require(signature.size in MIN_SIGNATURE_BYTES..MAX_SIGNATURE_BYTES)
        val key = KeyFactory.getInstance("EC").generatePublic(X509EncodedKeySpec(trustedAcceptingPeerKey))
        require((key as? java.security.interfaces.ECPublicKey)?.params?.curve?.field?.fieldSize == 256)
        Signature.getInstance("SHA256withECDSA").run {
            initVerify(key)
            update(signedBytes())
            verify(signature)
        }
    } catch (_: Exception) { false }

    fun encode(): ByteArray {
        require(isValidHeader())
        require(signature.size in MIN_SIGNATURE_BYTES..MAX_SIGNATURE_BYTES)
        val bytes = ByteArrayOutputStream()
        DataOutputStream(bytes).use { out ->
            out.writeInt(MAGIC)
            writeString(out, packetId)
            writeString(out, acceptingPeerId)
            writeString(out, previousHopPeerId)
            out.writeLong(acceptedAtMs)
            out.writeShort(signature.size)
            out.write(signature)
        }
        return bytes.toByteArray().also { require(it.size <= MAX_ENCODED_BYTES) }
    }

    private fun isValidHeader(): Boolean =
        packetId.matches(PACKET_ID_PATTERN) &&
            NeaRerIdentity.normalize(acceptingPeerId) == acceptingPeerId &&
            NeaRerIdentity.normalize(previousHopPeerId) == previousHopPeerId &&
            acceptingPeerId != previousHopPeerId && acceptedAtMs > 0L

    companion object {
        private const val MAGIC = 0x4E524131 // NRA1
        private const val MAX_ENCODED_BYTES = 1024
        private const val MAX_CLOCK_SKEW_MS = 300_000L
        private const val MIN_SIGNATURE_BYTES = 8
        private const val MAX_SIGNATURE_BYTES = 128
        private const val DOMAIN = "NeaRer-hop-accepted-v1"
        private val PACKET_ID_PATTERN = Regex("[A-Za-z0-9._:-]{1,80}")

        private fun writeString(out: DataOutputStream, value: String) {
            val bytes = value.toByteArray(Charsets.UTF_8)
            require(bytes.isNotEmpty() && bytes.size <= 80)
            out.writeShort(bytes.size)
            out.write(bytes)
        }

        private fun readString(input: DataInputStream): String {
            val size = input.readUnsignedShort()
            require(size in 1..80)
            return ByteArray(size).also(input::readFully).toString(Charsets.UTF_8)
        }

        fun decode(bytes: ByteArray): MeshHopAcknowledgement {
            require(bytes.size in 1..MAX_ENCODED_BYTES)
            val input = DataInputStream(ByteArrayInputStream(bytes))
            require(input.readInt() == MAGIC)
            val packetId = readString(input)
            val acceptingPeerId = readString(input)
            val previousHopPeerId = readString(input)
            val acceptedAtMs = input.readLong()
            val signatureSize = input.readUnsignedShort()
            require(signatureSize in MIN_SIGNATURE_BYTES..MAX_SIGNATURE_BYTES)
            val signature = ByteArray(signatureSize).also(input::readFully)
            require(input.available() == 0)
            return MeshHopAcknowledgement(packetId, acceptingPeerId, previousHopPeerId, acceptedAtMs, signature)
                .also { require(it.isValidHeader()) }
        }

        fun create(
            packetId: String,
            acceptingPeerId: String,
            previousHopPeerId: String,
            acceptedAtMs: Long,
            sign: (ByteArray) -> ByteArray
        ): MeshHopAcknowledgement {
            val unsigned = MeshHopAcknowledgement(
                packetId, acceptingPeerId, previousHopPeerId, acceptedAtMs, ByteArray(0)
            )
            require(unsigned.isValidHeader())
            val signature = sign(unsigned.signedBytes())
            require(signature.size in MIN_SIGNATURE_BYTES..MAX_SIGNATURE_BYTES)
            return unsigned.copy(signature = signature)
        }
    }
}
