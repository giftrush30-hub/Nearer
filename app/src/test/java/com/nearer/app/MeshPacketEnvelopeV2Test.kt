package com.nearer.app

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MeshPacketEnvelopeV2Test {
    @Test fun packetAndProofSidecarRoundTripWithoutBase64Expansion() {
        val packet = ByteArray(24_000) { (it % 251).toByte() }
        val proof = ByteArray(1_024) { (it % 127).toByte() }
        val encoded = MeshPacketEnvelopeV2.encode(packet, proof)
        assertTrue(encoded.size < packet.size + proof.size + 16)
        assertTrue(MeshPacketEnvelopeV2.isEnvelope(encoded))
        val decoded = MeshPacketEnvelopeV2.decode(encoded)
        assertArrayEquals(packet, decoded.packetBytes)
        assertArrayEquals(proof, decoded.proofChainBytes)
    }

    @Test fun envelopeRejectsWrongMagicTrailingBytesAndOversizedProof() {
        assertFalse(MeshPacketEnvelopeV2.isEnvelope(byteArrayOf(1, 2, 3, 4)))
        val valid = MeshPacketEnvelopeV2.encode(byteArrayOf(1, 2, 3), byteArrayOf())
        assertFalse(runCatching {
            MeshPacketEnvelopeV2.decode(valid + byteArrayOf(0))
        }.isSuccess)
        assertFalse(runCatching {
            MeshPacketEnvelopeV2.encode(byteArrayOf(1), ByteArray(MeshPacketEnvelopeV2.MAX_PROOF_BYTES + 1))
        }.isSuccess)
    }

    @Test fun envelopeEnforcesNearbyPayloadLimit() {
        assertFalse(runCatching {
            MeshPacketEnvelopeV2.encode(
                ByteArray(MeshRelayPacketV1.MAX_NEARBY_BYTES - 8),
                ByteArray(16)
            )
        }.isSuccess)
    }
}
