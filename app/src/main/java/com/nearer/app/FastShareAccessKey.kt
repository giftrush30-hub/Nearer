package com.nearer.app

import java.security.MessageDigest
import java.security.SecureRandom

/** Creates and verifies a high-entropy bearer key for one local Fast Share session. */
internal object FastShareAccessKey {
    private val random = SecureRandom()
    fun create(): String {
        val bytes = ByteArray(32)
        random.nextBytes(bytes)
        return bytes.joinToString("") { byte -> "%02x".format(byte) }
    }
    fun matches(expected: String, supplied: String?): Boolean {
        if (supplied == null || supplied.length != expected.length) return false
        return MessageDigest.isEqual(expected.toByteArray(Charsets.US_ASCII), supplied.toByteArray(Charsets.US_ASCII))
    }
}
