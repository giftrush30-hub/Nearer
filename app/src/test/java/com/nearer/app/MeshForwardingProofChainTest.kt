package com.nearer.app

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.security.KeyPairGenerator
import java.security.Signature
import java.security.spec.ECGenParameterSpec

class MeshForwardingProofChainTest {
    private fun pair() = KeyPairGenerator.getInstance("EC").apply {
        initialize(ECGenParameterSpec("secp256r1"))
    }.generateKeyPair()

    private fun sign(key: java.security.PrivateKey, bytes: ByteArray): ByteArray =
        Signature.getInstance("SHA256withECDSA").run {
            initSign(key)
            update(bytes)
            sign()
        }

    @Test fun completeMultiHopChainVerifiesAndRoundTrips() {
        val b = pair()
        val c = pair()
        val d = pair()
        val first = MeshForwardingProof.create(
            "packet-1", "AAAAAA", "BBBBBB", "AAAAAA", 1, 10_000L,
            MeshForwardingProof.GENESIS_HASH
        ) { sign(b.private, it) }
        val second = MeshForwardingProof.create(
            "packet-1", "AAAAAA", "CCCCCC", "BBBBBB", 2, 10_100L, first.hash()
        ) { sign(c.private, it) }
        val third = MeshForwardingProof.create(
            "packet-1", "AAAAAA", "DDDDDD", "CCCCCC", 3, 10_200L, second.hash()
        ) { sign(d.private, it) }
        val chain = MeshForwardingProofChain(listOf(first, second, third))
        val decoded = MeshForwardingProofChain.decode(chain.encode())
        val keys = mapOf("BBBBBB" to b.public.encoded, "CCCCCC" to c.public.encoded, "DDDDDD" to d.public.encoded)
        assertTrue(decoded.verify("packet-1", "AAAAAA", "DDDDDD", { keys[it] }, 10_500L))
    }

    @Test fun chainRejectsUnknownSignerWrongPacketBrokenLinkAndWrongDestination() {
        val b = pair()
        val c = pair()
        val first = MeshForwardingProof.create(
            "packet-1", "AAAAAA", "BBBBBB", "AAAAAA", 1, 10_000L,
            MeshForwardingProof.GENESIS_HASH
        ) { sign(b.private, it) }
        val second = MeshForwardingProof.create(
            "packet-1", "AAAAAA", "CCCCCC", "BBBBBB", 2, 10_100L, first.hash()
        ) { sign(c.private, it) }
        val keys = mapOf("BBBBBB" to b.public.encoded, "CCCCCC" to c.public.encoded)
        assertFalse(MeshForwardingProofChain(listOf(first, second))
            .verify("other-packet", "AAAAAA", "CCCCCC", { keys[it] }, 10_500L))
        assertFalse(MeshForwardingProofChain(listOf(first, second))
            .verify("packet-1", "AAAAAA", "CCCCCC", { null }, 10_500L))
        assertFalse(MeshForwardingProofChain(listOf(first, second))
            .verify("packet-1", "AAAAAA", "DDDDDD", { keys[it] }, 10_500L))
        assertFalse(MeshForwardingProofChain(listOf(first, second.copy(previousProofHash = ByteArray(32))))
            .verify("packet-1", "AAAAAA", "CCCCCC", { keys[it] }, 10_500L))
    }

    @Test fun chainRejectsReplayMutationAndTrailingBytes() {
        val b = pair()
        val proof = MeshForwardingProof.create(
            "packet-1", "AAAAAA", "BBBBBB", "AAAAAA", 1, 10_000L,
            MeshForwardingProof.GENESIS_HASH
        ) { sign(b.private, it) }
        val keys = mapOf("BBBBBB" to b.public.encoded)
        assertFalse(MeshForwardingProofChain(listOf(proof.copy(acceptingPeerId = "CCCCCC")))
            .verify("packet-1", "AAAAAA", "BBBBBB", { keys[it] }, 10_500L))
        assertFalse(MeshForwardingProofChain(listOf(proof.copy(acceptedAtMs = 20_000L)))
            .verify("packet-1", "AAAAAA", "BBBBBB", { keys[it] }, 10_500L))
        assertFalse(runCatching {
            MeshForwardingProofChain.decode(proof.encode() + byteArrayOf(0))
        }.isSuccess)
    }
}
