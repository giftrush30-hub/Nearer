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

    @Test fun chainRejectsWrongTrustedKeyInvalidSignatureAndNonMonotonicTime() {
        val b = pair()
        val c = pair()
        val first = MeshForwardingProof.create(
            "packet-2", "AAAAAA", "BBBBBB", "AAAAAA", 1, 10_200L,
            MeshForwardingProof.GENESIS_HASH
        ) { sign(b.private, it) }
        val second = MeshForwardingProof.create(
            "packet-2", "AAAAAA", "CCCCCC", "BBBBBB", 2, 10_300L, first.hash()
        ) { sign(c.private, it) }
        val keys = mapOf("BBBBBB" to b.public.encoded, "CCCCCC" to c.public.encoded)
        assertTrue(MeshForwardingProofChain(listOf(first, second))
            .verify("packet-2", "AAAAAA", "CCCCCC", { keys[it] }, 10_500L))
        val nonMonotonicSecond = MeshForwardingProof.create(
            "packet-2", "AAAAAA", "CCCCCC", "BBBBBB", 2, 10_100L, first.hash()
        ) { sign(c.private, it) }
        assertFalse(MeshForwardingProofChain(listOf(first, nonMonotonicSecond))
            .verify("packet-2", "AAAAAA", "CCCCCC", { keys[it] }, 10_500L))
        assertFalse(MeshForwardingProofChain(listOf(first))
            .verify("packet-2", "AAAAAA", "BBBBBB", { pair().public.encoded }, 10_500L))
        val corruptedSignature = first.signature.copyOf().also { it[it.lastIndex] = (it.last().toInt() xor 1).toByte() }
        assertFalse(MeshForwardingProofChain(listOf(first.copy(signature = corruptedSignature)))
            .verify("packet-2", "AAAAAA", "BBBBBB", { keys[it] }, 10_500L))
    }

    @Test fun chainRejectsDuplicatePeersAndTooManyHops() {
        val b = pair()
        val first = MeshForwardingProof.create(
            "packet-3", "AAAAAA", "BBBBBB", "AAAAAA", 1, 10_000L,
            MeshForwardingProof.GENESIS_HASH
        ) { sign(b.private, it) }
        val repeated = MeshForwardingProof.create(
            "packet-3", "AAAAAA", "BBBBBB", "CCCCCC", 2, 10_100L, first.hash()
        ) { sign(b.private, it) }
        assertFalse(MeshForwardingProofChain(listOf(first, repeated))
            .verify("packet-3", "AAAAAA", "BBBBBB", { b.public.encoded }, 10_500L))
        assertFalse(runCatching {
            MeshForwardingProofChain.decode(byteArrayOf(0x4e, 0x52, 0x43, 0x31, 5))
        }.isSuccess)
    }

}
