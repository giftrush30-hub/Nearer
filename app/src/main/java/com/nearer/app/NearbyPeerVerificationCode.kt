package com.nearer.app

import java.security.MessageDigest

/** Shared short-authentication-string derivation for first-contact LAN pairing. */
internal object NearbyPeerVerificationCode {
    fun fromTranscript(transcript: ByteArray): String {
        require(transcript.isNotEmpty()) { "Handshake transcript cannot be empty" }
        val digest = MessageDigest.getInstance("SHA-256").digest(transcript)
        val value = ((digest[0].toLong() and 0xff) shl 24) or
            ((digest[1].toLong() and 0xff) shl 16) or
            ((digest[2].toLong() and 0xff) shl 8) or
            (digest[3].toLong() and 0xff)
        return (value % 1_000_000L).toString().padStart(6, '0')
    }
}
