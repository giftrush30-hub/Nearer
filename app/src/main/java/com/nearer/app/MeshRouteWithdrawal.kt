package com.nearer.app

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.security.KeyFactory
import java.security.Signature
import java.security.spec.X509EncodedKeySpec

/** Signed hop-by-hop withdrawal of a route previously advertised by this peer. */
internal data class MeshRouteWithdrawal(
    val advertiserId: String,
    val destinationId: String,
    val sequence: Long,
    val issuedAtMs: Long,
    val signature: ByteArray
) {
    fun signedBytes(): ByteArray {
        val bytes = ByteArrayOutputStream()
        DataOutputStream(bytes).use { out ->
            out.write(DOMAIN.toByteArray(Charsets.US_ASCII))
            writeString(out, advertiserId)
            writeString(out, destinationId)
            out.writeLong(sequence)
            out.writeLong(issuedAtMs)
        }
        return bytes.toByteArray()
    }

    fun verify(trustedAdvertiserKey: ByteArray, nowMs: Long): Boolean = try {
        require(NeaRerIdentity.normalize(advertiserId) == advertiserId)
        require(NeaRerIdentity.normalize(destinationId) == destinationId)
        require(advertiserId != destinationId && sequence >= 0L && issuedAtMs > 0L)
        require(issuedAtMs <= nowMs + MAX_CLOCK_SKEW_MS)
        require(signature.size in 8..128)
        val key = KeyFactory.getInstance("EC").generatePublic(X509EncodedKeySpec(trustedAdvertiserKey))
        require((key as? java.security.interfaces.ECPublicKey)?.params?.curve?.field?.fieldSize == 256)
        Signature.getInstance("SHA256withECDSA").run {
            initVerify(key)
            update(signedBytes())
            verify(signature)
        }
    } catch (_: Exception) { false }

    fun encode(): ByteArray {
        require(signature.size in 8..128)
        val bytes = ByteArrayOutputStream()
        DataOutputStream(bytes).use { out ->
            out.writeInt(MAGIC)
            writeString(out, advertiserId)
            writeString(out, destinationId)
            out.writeLong(sequence)
            out.writeLong(issuedAtMs)
            out.writeShort(signature.size)
            out.write(signature)
        }
        return bytes.toByteArray().also { require(it.size <= MAX_BYTES) }
    }

    companion object {
        private const val MAGIC = 0x4E525731 // NRW1
        private const val DOMAIN = "NeaRer-route-withdrawal-v1"
        private const val MAX_CLOCK_SKEW_MS = 300_000L
        private const val MAX_BYTES = 512

        private fun writeString(out: DataOutputStream, value: String) {
            val encoded = value.toByteArray(Charsets.UTF_8)
            require(encoded.isNotEmpty() && encoded.size <= 64)
            out.writeShort(encoded.size)
            out.write(encoded)
        }

        private fun readString(input: DataInputStream): String {
            val size = input.readUnsignedShort()
            require(size in 1..64)
            return ByteArray(size).also(input::readFully).toString(Charsets.UTF_8)
        }

        fun decode(bytes: ByteArray): MeshRouteWithdrawal {
            require(bytes.size in 1..MAX_BYTES)
            val input = DataInputStream(ByteArrayInputStream(bytes))
            require(input.readInt() == MAGIC)
            val advertiser = readString(input)
            val destination = readString(input)
            val sequence = input.readLong()
            val issuedAt = input.readLong()
            val signatureSize = input.readUnsignedShort()
            require(signatureSize in 8..128)
            val signature = ByteArray(signatureSize).also(input::readFully)
            require(input.available() == 0)
            return MeshRouteWithdrawal(advertiser, destination, sequence, issuedAt, signature)
        }

        fun create(
            advertiserId: String,
            destinationId: String,
            sequence: Long,
            issuedAtMs: Long,
            sign: (ByteArray) -> ByteArray
        ): MeshRouteWithdrawal {
            val unsigned = MeshRouteWithdrawal(advertiserId, destinationId, sequence, issuedAtMs, ByteArray(0))
            require(NeaRerIdentity.normalize(advertiserId) == advertiserId)
            require(NeaRerIdentity.normalize(destinationId) == destinationId)
            require(advertiserId != destinationId && sequence >= 0L && issuedAtMs > 0L)
            return unsigned.copy(signature = sign(unsigned.signedBytes()))
        }
    }
}
