package com.nearer.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.security.KeyPairGenerator
import java.security.Signature
import java.security.spec.ECGenParameterSpec

class MeshRelayPacketTest {
    private fun pair() = KeyPairGenerator.getInstance("EC").apply {
        initialize(ECGenParameterSpec("secp256r1"))
    }.generateKeyPair()

    private fun sign(privateKey: java.security.PrivateKey, bytes: ByteArray): ByteArray =
        Signature.getInstance("SHA256withECDSA").run {
            initSign(privateKey); update(bytes); sign()
        }

    @Test fun onlyRecipientCanOpenAndOriginSignatureSurvivesForwarding() {
        val recipient = pair()
        val otherRecipient = pair()
        val origin = pair()
        val encrypted = RecipientMessageCrypto.encrypt(
            "AAAAAA", "BBBBBB", "packet-1", 1000L, 5000L, 3,
            recipient.public.encoded, "private body".toByteArray()
        )
        val unsigned = MeshRelayPacketV1(
            "packet-1", "AAAAAA", "BBBBBB", 1000L, 5000L, 3, 0,
            encrypted, origin.public.encoded, ByteArray(0)
        )
        val packet = unsigned.copy(originSignature = sign(origin.private, unsigned.signedBytes()))
        assertTrue(packet.verify(origin.public.encoded, 2000L))
        val forwarded = packet.forwarded()
        assertNotNull(forwarded)
        assertEquals(1, forwarded!!.hopsTaken)
        assertTrue(forwarded.verify(origin.public.encoded, 2000L))
        assertEquals(packet.signedBytes().toList(), forwarded.signedBytes().toList())
        assertTrue(packet.validate(2000L))
        assertEquals("private body", RecipientMessageCrypto.decrypt(
            recipient.private, "AAAAAA", "BBBBBB", "packet-1", 1000L, 5000L, 3, encrypted
        ).toString(Charsets.UTF_8))
        assertTrue(runCatching {
            RecipientMessageCrypto.decrypt(
                otherRecipient.private, "AAAAAA", "BBBBBB", "packet-1", 1000L, 5000L, 3, encrypted
            )
        }.isFailure)
        assertFalse(packet.copy(destinationId = "CCCCCC").verify(origin.public.encoded, 2000L))
        assertFalse(packet.verify(otherRecipient.public.encoded, 2000L))
    }

    @Test fun embeddedOriginSignatureProvesIntegrityButNotTrustedIdentity() {
        val recipient = pair()
        val origin = pair()
        val encrypted = RecipientMessageCrypto.encrypt(
            "AAAAAA", "BBBBBB", "packet-self-check", 1000L, 5000L, 2,
            recipient.public.encoded, "payload".toByteArray()
        )
        val unsigned = MeshRelayPacketV1(
            "packet-self-check", "AAAAAA", "BBBBBB", 1000L, 5000L, 2, 0,
            encrypted, origin.public.encoded, ByteArray(0)
        )
        val packet = unsigned.copy(originSignature = sign(origin.private, unsigned.signedBytes()))
        assertTrue(packet.verifyEmbeddedOrigin(2000L))
        assertFalse(packet.verify(origin.public.encoded.copyOf().also { it[0] = (it[0].toInt() xor 1).toByte() }, 2000L))
        assertTrue(packet.sameImmutablePacket(packet.forwarded()!!))
        assertFalse(packet.sameImmutablePacket(packet.copy(packetId = "different-id")))
        assertFalse(packet.copy(originSignature = packet.originSignature.copyOf().also { it[0] = (it[0].toInt() xor 1).toByte() }).verifyEmbeddedOrigin(2000L))
    }

    @Test fun decoderRejectsPacketsBeyondNearbyBytesPayloadLimit() {
        assertFalse(runCatching {
            MeshRelayPacketV1.decode(ByteArray(MeshRelayPacketV1.MAX_NEARBY_BYTES + 1))
        }.isSuccess)
    }

    @Test fun packetCannotExceedSignedHopLimitOrOutliveItsExpiry() {
        val recipient = pair()
        val origin = pair()
        val encrypted = RecipientMessageCrypto.encrypt(
            "AAAAAA", "BBBBBB", "packet-2", 1000L, 5000L, 1,
            recipient.public.encoded, "opaque".toByteArray()
        )
        val unsigned = MeshRelayPacketV1(
            "packet-2", "AAAAAA", "BBBBBB", 1000L, 5000L, 1, 0,
            encrypted, origin.public.encoded, ByteArray(0)
        )
        val packet = unsigned.copy(originSignature = sign(origin.private, unsigned.signedBytes()))
        assertNull(packet.forwarded()?.forwarded())
        assertTrue(packet.validate(2000L))
        assertFalse(packet.validate(5001L))
        assertFalse(packet.copy(hopsTaken = 2).validate(2000L))
        assertFalse(packet.copy(hopLimit = 5).validate(2000L))
    }
}
