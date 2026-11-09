package com.nearer.app

import java.security.SecureRandom
import java.util.Base64

/** Generates a fresh CSP nonce for each rendered Fast Share receiver page. */
internal object FastSharePageNonce {
    private val random = SecureRandom()
    fun create(): String {
        val bytes = ByteArray(18)
        random.nextBytes(bytes)
        return Base64.getEncoder().withoutPadding().encodeToString(bytes)
    }
}
