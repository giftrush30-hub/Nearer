package com.nearer.app

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.security.KeyPairGenerator
import java.security.spec.ECGenParameterSpec

class MeshRelayPacketTest {
    @Test fun forwardingOnlyChangesUnsignedHopCounter() {
        val recipient = KeyPairGenerator.getInstance("EC").apply {
            initialize(ECGenParameterSpec("secp256r1"))
        }.generateKeyPair()
        val signer = KeyPairGenerator.getInstance("EC").apply {
            initialize(ECGenParameterSpec("secp256r1"))
        }.generateKeyPair()
        val encrypted = RecipientMessageCrypto.encrypt(
            "AAAAAA", "BBBBBB", "packet-1", 1000L, 5000L, 3,
            recipient.public.encoded, "opaque".toByteArray()
        )
        val packet = MeshRelayPacketV1(
            "packet-1", "AAAAAA", "BBBBBB", 1000L, 5000L, 3, 0,
            encrypted, signer.public.encoded, ByteArray(64) { 1 }
        )
        val forwarded = packet.forwarded()
        assertNotNull(forwarded)
        assertEquals(1, forwarded!!.hopsTaken)
        assertArrayEquals(packet.signedBytes(), forwarded.signedBytes())
        assertEquals(3, forwarded.hopLimit)
    }

    @Test fun packetCannotExceedSignedHopLimitOrOutliveItsExpiry() {
        val recipient = KeyPairGenerator.getInstance("EC").apply {
            initialize(ECGenParameterSpec("secp256r1"))
        }.generateKeyPair()
        val signer = KeyPairGenerator.getInstance("EC").apply {
            initialize(ECGenParameterSpec("secp256r1"))
        }.generateKeyPair()
        val encrypted = RecipientMessageCrypto.encrypt(
            "AAAAAA", "BBBBBB", "packet-2", 1000L, 5000L, 1,
            recipient.public.encoded, "opaque".toByteArray()
        )
        val packet = MeshRelayPacketV1(
            "packet-2", "AAAAAA", "BBBBBB", 1000L, 5000L, 1, 0,
            encrypted, signer.public.encoded, ByteArray(64) { 2 }
        )
        assertNull(packet.forwarded()?.forwarded())
        assertTrue(packet.validate(2000L))
        assertFalse(packet.validate(5001L))
        assertFalse(packet.copy(hopsTaken = 2).validate(2000L))
        assertFalse(packet.copy(hopLimit = 5).validate(2000L))
    }
}
