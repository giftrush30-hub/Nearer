package com.nearer.app

import java.security.SecureRandom

/**
 * Public, non-secret nearby identity. New installations use 60-bit IDs;
 * six-character IDs remain accepted so existing installations keep their
 * established peer identity and conversation history.
 */
internal object NeaRerIdentity {
    private const val ALPHABET = "23456789ABCDEFGHJKLMNPQRSTUVWXYZ"
    const val ID_LENGTH = 12
    private const val LEGACY_ID_LENGTH = 6

    fun normalize(value: String?): String? {
        val candidate = value?.trim()?.uppercase() ?: return null
        if (candidate.length == LEGACY_ID_LENGTH) {
            // Original IDs were the first six hexadecimal characters of a UUID.
            return candidate.takeIf { id -> id.all { it in "0123456789ABCDEF" } }
        }
        if (candidate.length != ID_LENGTH) return null
        return candidate.takeIf { id -> id.all { it in ALPHABET } }
    }

    fun generate(random: SecureRandom = SecureRandom()): String {
        val entropy = ByteArray(ID_LENGTH)
        random.nextBytes(entropy)
        return buildString(ID_LENGTH) {
            entropy.forEach { byte -> append(ALPHABET[byte.toInt() and 31]) }
        }
    }

    fun getOrCreate(existing: String?, random: SecureRandom = SecureRandom()): String =
        normalize(existing) ?: generate(random)
}
