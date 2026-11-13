package com.nearer.app

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream

/**
 * Compact Nearby BYTES sidecar carrying an opaque signed packet and its
 * independently verifiable forwarding-acceptance chain without Base64 expansion.
 */
internal object MeshPacketEnvelopeV2 {
    data class Decoded(val packetBytes: ByteArray, val proofChainBytes: ByteArray)

    private const val MAGIC = 0x4E524D32 // NRM2
    private const val HEADER_BYTES = 12
    const val MAX_PROOF_BYTES = 5 * 1024

    fun encode(packetBytes: ByteArray, proofChainBytes: ByteArray): ByteArray {
        require(packetBytes.size in 1..MeshRelayPacketV1.MAX_NEARBY_BYTES)
        require(proofChainBytes.size <= MAX_PROOF_BYTES)
        val out = ByteArrayOutputStream(HEADER_BYTES + packetBytes.size + proofChainBytes.size)
        DataOutputStream(out).use { data ->
            data.writeInt(MAGIC)
            data.writeInt(packetBytes.size)
            data.write(packetBytes)
            data.writeInt(proofChainBytes.size)
            data.write(proofChainBytes)
        }
        return out.toByteArray().also {
            require(it.size <= MeshRelayPacketV1.MAX_NEARBY_BYTES) {
                "Packet and proof sidecar exceed Nearby's 32 KiB BYTES limit."
            }
        }
    }

    fun isEnvelope(bytes: ByteArray): Boolean =
        bytes.size >= 4 && java.nio.ByteBuffer.wrap(bytes, 0, 4).int == MAGIC

    fun decode(bytes: ByteArray): Decoded {
        require(bytes.size in HEADER_BYTES..MeshRelayPacketV1.MAX_NEARBY_BYTES)
        val input = DataInputStream(ByteArrayInputStream(bytes))
        require(input.readInt() == MAGIC)
        val packetSize = input.readInt()
        require(packetSize in 1..MeshRelayPacketV1.MAX_NEARBY_BYTES)
        require(packetSize <= input.available() - 4)
        val packet = ByteArray(packetSize).also(input::readFully)
        val proofSize = input.readInt()
        require(proofSize in 0..MAX_PROOF_BYTES && proofSize == input.available())
        val proof = ByteArray(proofSize).also(input::readFully)
        return Decoded(packet, proof)
    }
}
