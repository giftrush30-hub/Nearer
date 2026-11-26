package com.nearer.app

import org.junit.Assert.assertEquals
import org.junit.Test

/** Keeps the Swift and Kotlin v3 transcript encoders locked to the same wire bytes. */
class NearbyPeerHandshakeGoldenVectorTest {
    @Test
    fun transcriptMatchesSwiftGoldenVector() {
        val firstNonce = ByteArray(32) { 0 }
        val secondNonce = ByteArray(32) { 1 }
        val transcript = NearbyMessageCrypto.transcript(
            firstId = "AAAAAA",
            firstNonce = firstNonce,
            firstEphemeralKey = byteArrayOf(1, 2),
            firstRecipientKey = byteArrayOf(3),
            secondId = "BBBBBB",
            secondNonce = secondNonce,
            secondEphemeralKey = byteArrayOf(4, 5),
            secondRecipientKey = byteArrayOf(6)
        )
        val actual = transcript.joinToString("") { "%02x".format(it) }
        val expected = "4e65615265722d4e65617262792d506565722d48616e647368616b652d7633000641414141414120000000000000000000000000000000000000000000000000000000000000000000020102000103000642424242424220010101010101010101010101010101010101010101010101010101010101010100020405000106"
        assertEquals(expected, actual)
    }
}
