package com.nearer.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.security.KeyPairGenerator
import java.security.Signature
import java.security.spec.ECGenParameterSpec

class MeshHopAcknowledgementTest {
    private fun pair() = KeyPairGenerator.getInstance("EC").apply {
        initialize(ECGenParameterSpec("secp256r1"))
    }.generateKeyPair()

    private fun sign(privateKey: java.security.PrivateKey, bytes: ByteArray): ByteArray =
        Signature.getInstance("SHA256withECDSA").run {
            initSign(privateKey)
            update(bytes)
            sign()
        }

    @Test fun acknowledgementRoundTripsAndMustBeSignedByTheAcceptingNextHop() {
        val accepting = pair()
        val acknowledgement = MeshHopAcknowledgement.create(
            "packet-123", "BBBBBB", "AAAAAA", 400_000L
        ) { sign(accepting.private, it) }

        val decoded = MeshHopAcknowledgement.decode(acknowledgement.encode())
        assertTrue(decoded.verify(accepting.public.encoded, "AAAAAA", 400_500L))
        assertFalse(decoded.verify(pair().public.encoded, "AAAAAA", 400_500L))
        assertFalse(decoded.verify(accepting.public.encoded, "CCCCCC", 400_500L))
        assertFalse(decoded.verify(accepting.public.encoded, "AAAAAA", 1_000L))
        assertEquals("packet-123", decoded.packetId)
        assertFalse(runCatching {
            MeshHopAcknowledgement.decode(acknowledgement.encode() + byteArrayOf(0))
        }.isSuccess)
    }

    @Test fun acknowledgementCannotBeReplayedForAnotherPacketOrPeer() {
        val accepting = pair()
        val acknowledgement = MeshHopAcknowledgement.create(
            "packet-456", "BBBBBB", "AAAAAA", 400_000L
        ) { sign(accepting.private, it) }

        assertFalse(acknowledgement.copy(packetId = "packet-other")
            .verify(accepting.public.encoded, "AAAAAA", 400_500L))
        assertFalse(acknowledgement.copy(acceptingPeerId = "DDDDDD")
            .verify(accepting.public.encoded, "AAAAAA", 400_500L))
        assertFalse(acknowledgement.copy(previousHopPeerId = "EEEEEE")
            .verify(accepting.public.encoded, "AAAAAA", 400_500L))
    }
}
