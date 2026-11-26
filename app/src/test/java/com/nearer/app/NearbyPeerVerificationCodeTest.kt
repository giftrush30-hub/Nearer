package com.nearer.app

import org.junit.Assert.assertEquals
import org.junit.Test

class NearbyPeerVerificationCodeTest {
    @Test
    fun matchesSwiftShortAuthenticationStringVector() {
        val aliceEphemeral = hex("3059301306072a8648ce3d020106082a8648ce3d030107034200045ecbe4d1a6330a44c8f7ef951d4bf165e6c6b721efada985fb41661bc6e7fd6c8734640c4998ff7e374b06ce1a64a2ecd82ab036384fb83d9a79b127a27d5032")
        val bobEphemeral = hex("3059301306072a8648ce3d020106082a8648ce3d03010703420004e2534a3532d08fbba02dde659ee62bd0031fe2db785596ef509302446b030852e0f1575a4c633cc719dfee5fda862d764efc96c3f30ee0055c42c23f184ed8c6")
        val aliceRecipient = hex("3059301306072a8648ce3d020106082a8648ce3d030107034200047cf27b188d034f7e8a52380304b51ac3c08969e277f21b35a60b48fc4766997807775510db8ed040293d9ac69f7430dbba7dade63ce982299e04b79d227873d1")
        val bobRecipient = hex("3059301306072a8648ce3d020106082a8648ce3d030107034200046b17d1f2e12c4247f8bce6e563a440f277037d812deb33a0f4a13945d898c2964fe342e2fe1a7f9b8ee7eb4a7c0f9e162bce33576b315ececbb6406837bf51f5")
        val transcript = NearbyMessageCrypto.transcript(
            "AAAAAB", ByteArray(32) { 0x11 }, aliceEphemeral, aliceRecipient,
            "BBBBBB", ByteArray(32) { 0x22 }, bobEphemeral, bobRecipient
        )
        assertEquals("129961", NearbyPeerVerificationCode.fromTranscript(transcript))
    }

    private fun hex(value: String): ByteArray =
        value.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
}
