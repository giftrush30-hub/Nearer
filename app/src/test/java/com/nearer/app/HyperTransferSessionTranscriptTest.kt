package com.nearer.app

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class HyperTransferSessionTranscriptTest {
    private fun peer(id: String, seed: Int) = HyperTransferSessionTranscript.PeerMaterial(
        peerId = id,
        signingPublicKey = ByteArray(64) { (seed + it).toByte() },
        nonce = ByteArray(32) { (seed * 2 + it).toByte() },
        ephemeralPublicKey = ByteArray(91) { (seed * 3 + it).toByte() },
        encryptionPublicKey = ByteArray(91) { (seed * 4 + it).toByte() }
    )

    @Test
    fun transcriptIsDeterministicForTheSameTransferRolesAndPeerMaterial() {
        val sender = peer("23456789ABCD", 1)
        val receiver = peer("EFGHJKMNPQRS", 2)
        val fromSender = HyperTransferSessionTranscript.create("a".repeat(64), sender, receiver, localIsSender = true)
        val fromReceiver = HyperTransferSessionTranscript.create("a".repeat(64), receiver, sender, localIsSender = false)
        assertArrayEquals(fromSender, fromReceiver)
    }

    @Test
    fun transferIdIsBoundIntoTranscript() {
        val sender = peer("23456789ABCD", 1)
        val receiver = peer("EFGHJKMNPQRS", 2)
        val first = HyperTransferSessionTranscript.create("a".repeat(64), sender, receiver)
        val second = HyperTransferSessionTranscript.create("b".repeat(64), sender, receiver, localIsSender = true)
        assertFalse(first.contentEquals(second))
    }

    @Test
    fun senderAndReceiverRolesAreBoundIntoTranscript() {
        val first = peer("23456789ABCD", 1)
        val second = peer("EFGHJKMNPQRS", 2)
        val forward = HyperTransferSessionTranscript.create("c".repeat(64), first, second, localIsSender = true)
        val reverse = HyperTransferSessionTranscript.create("c".repeat(64), second, first, localIsSender = true)
        assertFalse(forward.contentEquals(reverse))
    }

    @Test
    fun peerKeysAndNoncesAreBoundIntoTranscript() {
        val sender = peer("23456789ABCD", 1)
        val receiver = peer("EFGHJKMNPQRS", 2)
        val baseline = HyperTransferSessionTranscript.create("d".repeat(64), sender, receiver, localIsSender = true)
        val changed = receiver.copy(nonce = ByteArray(32) { 99 })
        val altered = HyperTransferSessionTranscript.create("d".repeat(64), sender, changed, localIsSender = true)
        assertNotEquals(baseline.toList(), altered.toList())
    }

    @Test
    fun rejectsInvalidTransferIdsAndSamePeer() {
        val sender = peer("23456789ABCD", 1)
        assertThrows(IllegalArgumentException::class.java) {
            HyperTransferSessionTranscript.create("not-a-transfer-id", sender, peer("EFGHJKMNPQRS", 2), localIsSender = true)
        }
        assertThrows(IllegalArgumentException::class.java) {
            HyperTransferSessionTranscript.create("e".repeat(64), sender, sender, localIsSender = true)
        }
    }

    @Test
    fun rejectsInvalidNonceLengthAndUnsupportedVersion() {
        val sender = peer("23456789ABCD", 1)
        val receiver = peer("EFGHJKMNPQRS", 2).copy(nonce = byteArrayOf(1, 2))
        assertThrows(IllegalArgumentException::class.java) {
            HyperTransferSessionTranscript.create("f".repeat(64), sender, receiver, localIsSender = true)
        }
        assertThrows(IllegalArgumentException::class.java) {
            HyperTransferSessionTranscript.create("f".repeat(64), sender, peer("EFGHJKMNPQRS", 2), localIsSender = true, protocolVersion = 2)
        }
    }
}
