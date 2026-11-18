package com.nearer.app

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.nio.ByteBuffer
import java.security.MessageDigest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class HyperTransferSecureChannelTest {
    private data class PairChannels(
        val sender: HyperTransferSecureChannel,
        val receiver: HyperTransferSecureChannel
    )

    private fun channels(transfer: String = "a".repeat(64)): PairChannels {
        val senderEphemeral = NearbyMessageCrypto.newEphemeralKeyPair()
        val receiverEphemeral = NearbyMessageCrypto.newEphemeralKeyPair()
        val transcript = MessageDigest.getInstance("SHA-256")
            .digest(("authenticated-transcript|" + transfer).toByteArray())
        val sender = HyperTransferSecureChannel.establish(
            senderEphemeral.private, receiverEphemeral.public, transcript, transfer,
            "23456789ABCD", "EFGHJKMNPQRS", true
        )
        val receiver = HyperTransferSecureChannel.establish(
            receiverEphemeral.private, senderEphemeral.public, transcript, transfer,
            "23456789ABCD", "EFGHJKMNPQRS", false
        )
        return PairChannels(sender, receiver)
    }

    @Test
    fun peersDeriveComplementaryKeysAndExchangeRecordsBothDirections() {
        val pair = channels()
        val payload = ByteArray(128 * 1024) { (it % 251).toByte() }
        assertArrayEquals(payload, pair.receiver.open(pair.sender.seal(payload)))
        val response = "ACK".toByteArray()
        assertArrayEquals(response, pair.sender.open(pair.receiver.seal(response)))
    }

    @Test
    fun recordIsBoundToTransferAndPeerRoles() {
        val first = channels("b".repeat(64))
        val second = channels("c".repeat(64))
        val sealed = first.sender.seal("file bytes".toByteArray())
        assertThrows(Exception::class.java) { second.receiver.open(sealed) }
    }

    @Test
    fun tamperedCiphertextIsRejectedAndDoesNotAdvanceSequence() {
        val pair = channels()
        val sealed = pair.sender.seal("secret".toByteArray())
        val tampered = sealed.copyOf().also { it[it.lastIndex] = (it.last().toInt() xor 1).toByte() }
        assertThrows(Exception::class.java) { pair.receiver.open(tampered) }
        assertArrayEquals("secret".toByteArray(), pair.receiver.open(sealed))
    }

    @Test
    fun replayAndOutOfOrderRecordsAreRejected() {
        val pair = channels()
        val first = pair.sender.seal(byteArrayOf(1))
        val second = pair.sender.seal(byteArrayOf(2))
        assertThrows(SecurityException::class.java) { pair.receiver.open(second) }
        assertArrayEquals(byteArrayOf(1), pair.receiver.open(first))
        assertArrayEquals(byteArrayOf(2), pair.receiver.open(second))
        assertThrows(SecurityException::class.java) { pair.receiver.open(second) }
    }

    @Test
    fun recordSizeIsBounded() {
        val pair = channels()
        assertThrows(IllegalArgumentException::class.java) {
            pair.sender.seal(ByteArray(HyperTransferSecureChannel.MAX_PLAINTEXT_BYTES + 1))
        }
    }

    @Test
    fun framedStreamRoundTripAndOversizedLengthRejection() {
        val pair = channels()
        val bytes = ByteArrayOutputStream()
        val output = DataOutputStream(bytes)
        pair.sender.writeRecord(output, "bulk payload".toByteArray())
        output.flush()
        val input = DataInputStream(ByteArrayInputStream(bytes.toByteArray()))
        assertArrayEquals("bulk payload".toByteArray(), pair.receiver.readRecord(input))

        val malformed = ByteArrayOutputStream()
        DataOutputStream(malformed).writeInt(Int.MAX_VALUE)
        assertThrows(java.io.IOException::class.java) {
            pair.receiver.readRecord(DataInputStream(ByteArrayInputStream(malformed.toByteArray())))
        }
    }



    @Test
    fun encryptsOnlyTheRequestedBufferSliceWithoutChangingItsSurroundings() {
        val pair = channels()
        val backing = byteArrayOf(99, 1, 2, 3, 88, 77)
        val record = pair.sender.seal(backing, 1, 3)
        assertArrayEquals(byteArrayOf(1, 2, 3), pair.receiver.open(record))
        assertArrayEquals(byteArrayOf(99, 1, 2, 3, 88, 77), backing)
    }

    @Test
    fun rejectsInvalidBufferSlicesBeforeAdvancingSequence() {
        val pair = channels()
        val bytes = byteArrayOf(1, 2, 3)
        assertThrows(IllegalArgumentException::class.java) { pair.sender.seal(bytes, -1, 2) }
        assertThrows(IllegalArgumentException::class.java) { pair.sender.seal(bytes, 2, 2) }
        assertArrayEquals(byteArrayOf(1), pair.receiver.open(pair.sender.seal(bytes, 0, 1)))
    }

    @Test
    fun closedChannelRejectsFurtherEncryptionAndDecryption() {
        val pair = channels()
        val record = pair.sender.seal(byteArrayOf(9))
        pair.receiver.close()
        assertThrows(IllegalStateException::class.java) { pair.receiver.open(record) }
        pair.sender.close()
        assertThrows(IllegalStateException::class.java) { pair.sender.seal(byteArrayOf(1)) }
    }

    @Test
    fun nonceSequenceIsEncodedAsUnsignedMonotonicLong() {
        val pair = channels()
        val record = pair.sender.seal(byteArrayOf(42))
        assertEquals(0L, ByteBuffer.wrap(record).long)
        assertTrue(record.size > 8)
    }
}
