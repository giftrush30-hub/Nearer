package com.nearer.app

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Encrypts locally queued message payloads with an Android Keystore AES-GCM key.
 * The key is non-exportable; ciphertext is safe to persist in app preferences.
 */
internal object LocalDataCipher {
    private const val KEY_ALIAS = "nearer.local.outbox.aes.v1"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private const val PREFIX = "v1:"

    @Synchronized
    private fun getOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }

        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true)
                .setKeySize(256)
                .build()
        )
        return generator.generateKey()
    }

    fun encrypt(context: Context, plaintext: ByteArray): String {
        require(plaintext.isNotEmpty()) { "Cannot encrypt an empty message payload." }
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        val ciphertext = cipher.doFinal(plaintext)
        val combined = cipher.iv + ciphertext
        return PREFIX + Base64.encodeToString(combined, Base64.NO_WRAP)
    }

    fun decrypt(context: Context, stored: String): ByteArray? {
        if (!stored.startsWith(PREFIX)) return null
        return try {
            val combined = Base64.decode(stored.removePrefix(PREFIX), Base64.NO_WRAP)
            if (combined.size <= 12 + 16) return null
            val iv = combined.copyOfRange(0, 12)
            val ciphertext = combined.copyOfRange(12, combined.size)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), GCMParameterSpec(128, iv))
            cipher.doFinal(ciphertext)
        } catch (_: Exception) {
            null
        }
    }
}
