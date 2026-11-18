package com.nearer.app

import java.io.IOException
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class HyperTransferSocketHandshakeTest {
    private fun hello() = HyperTransferSocketHandshake.Hello(
        transferId = "a".repeat(64),
        peerId = "23456789ABCD",
        isSender = true,
        nonce = ByteArray(32) { it.toByte() },
        signingPublicKey = byteArrayOf(1, 2, 3),
        ephemeralPublicKey = byteArrayOf(4, 5, 6),
        encryptionPublicKey = byteArrayOf(7, 8, 9)
    )

    @Test
    fun helloRoundTripsAllTranscriptBoundFields() {
        val expected = hello()
        val actual = HyperTransferSocketHandshake.decodeHello(
            HyperTransferSocketHandshake.encodeHello(expected)
        )
        assertEquals(expected.transferId, actual.transferId)
        assertEquals(expected.peerId, actual.peerId)
        assertEquals(expected.isSender, actual.isSender)
        assertArrayEquals(expected.nonce, actual.nonce)
        assertArrayEquals(expected.signingPublicKey, actual.signingPublicKey)
        assertArrayEquals(expected.ephemeralPublicKey, actual.ephemeralPublicKey)
        assertArrayEquals(expected.encryptionPublicKey, actual.encryptionPublicKey)
    }

    @Test
    fun rejectsInvalidTransferIdsAndOversizedKeysBeforeEncoding() {
        val invalidId = hello().copy(transferId = "not-a-transfer-id")
        assertThrows(IllegalArgumentException::class.java) {
            HyperTransferSocketHandshake.encodeHello(invalidId)
        }
        val oversized = hello().copy(signingPublicKey = ByteArray(513))
        assertThrows(IllegalArgumentException::class.java) {
            HyperTransferSocketHandshake.encodeHello(oversized)
        }
    }

    @Test
    fun rejectsBadMagicAndUnsupportedVersion() {
        val encoded = HyperTransferSocketHandshake.encodeHello(hello())
        val badMagic = encoded.copyOf().also { it[0] = 0 }
        assertThrows(IOException::class.java) {
            HyperTransferSocketHandshake.decodeHello(badMagic)
        }
        val badVersion = encoded.copyOf().also { it[4] = 2 }
        assertThrows(IOException::class.java) {
            HyperTransferSocketHandshake.decodeHello(badVersion)
        }
    }

    @Test
    fun rejectsTruncatedAndTrailingHandshakeBytes() {
        val encoded = HyperTransferSocketHandshake.encodeHello(hello())
        assertThrows(IOException::class.java) {
            HyperTransferSocketHandshake.decodeHello(encoded.copyOf(encoded.size - 2))
        }
        assertThrows(IOException::class.java) {
            HyperTransferSocketHandshake.decodeHello(encoded + byteArrayOf(0))
        }
    }

    @Test
    fun rejectsInvalidPeerIdentityAndNonce() {
        assertThrows(IllegalArgumentException::class.java) {
            HyperTransferSocketHandshake.encodeHello(hello().copy(peerId = ""))
        }
        assertThrows(IllegalArgumentException::class.java) {
            HyperTransferSocketHandshake.encodeHello(hello().copy(nonce = ByteArray(31)))
        }
    }
}
