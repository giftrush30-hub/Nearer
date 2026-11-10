package com.nearer.app

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyFactory
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.MessageDigest
import java.security.spec.ECGenParameterSpec
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Persists the local P-256 signing and ECDH identity. Private key encodings are
 * encrypted with an AES-GCM key held by Android Keystore; they are never written
 * to preferences in plaintext. This does not establish trust in another user's key.
 */
internal class MeshIdentityVault(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences("nearer_mesh_identity", Context.MODE_PRIVATE)
    private val keyStore: KeyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }

    @Synchronized
    fun loadOrCreate(): MeshIdentityKeys {
        val signingPublic = preferences.getString("signing_public", null)
        val agreementPublic = preferences.getString("agreement_public", null)
        val signingPrivate = preferences.getString("signing_private", null)
        val agreementPrivate = preferences.getString("agreement_private", null)
        if (signingPublic != null && agreementPublic != null &&
            signingPrivate != null && agreementPrivate != null
        ) {
            return MeshIdentityKeys(
                signing = KeyPair(
                    KeyFactory.getInstance("EC").generatePublic(X509EncodedKeySpec(MeshCrypto.decode(signingPublic))),
                    KeyFactory.getInstance("EC").generatePrivate(PKCS8EncodedKeySpec(decrypt(signingPrivate)))
                ),
                agreement = KeyPair(
                    KeyFactory.getInstance("EC").generatePublic(X509EncodedKeySpec(MeshCrypto.decode(agreementPublic))),
                    KeyFactory.getInstance("EC").generatePrivate(PKCS8EncodedKeySpec(decrypt(agreementPrivate)))
                )
            )
        }

        val identity = MeshCrypto.generateIdentity()
        val editor = preferences.edit()
            .putString("signing_public", MeshCrypto.encode(identity.signing.public.encoded))
            .putString("agreement_public", MeshCrypto.encode(identity.agreement.public.encoded))
            .putString("signing_private", encrypt(identity.signing.private.encoded))
            .putString("agreement_private", encrypt(identity.agreement.private.encoded))
        check(editor.commit()) { "Could not persist the NeaRer mesh identity" }
        return identity
    }

    fun signingFingerprint(identity: MeshIdentityKeys): String = fingerprint(identity.signing.public.encoded)
    fun agreementFingerprint(identity: MeshIdentityKeys): String = fingerprint(identity.agreement.public.encoded)

    private fun fingerprint(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).take(16)
            .joinToString(":") { "%02X".format(it) }

    private fun encryptionKey(): SecretKey {
        keyStore.getKey(KEY_ALIAS, null)?.let { return it as SecretKey }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true)
                .build()
        )
        return generator.generateKey()
    }

    private fun encrypt(plaintext: ByteArray): String {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, encryptionKey())
        val encrypted = cipher.doFinal(plaintext)
        return MeshCrypto.encode(cipher.iv) + "." + MeshCrypto.encode(encrypted)
    }

    private fun decrypt(value: String): ByteArray {
        val parts = value.split(".", limit = 2)
        require(parts.size == 2) { "Stored mesh identity is malformed" }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, encryptionKey(), GCMParameterSpec(128, MeshCrypto.decode(parts[0])))
        return cipher.doFinal(MeshCrypto.decode(parts[1]))
    }

    companion object {
        private const val KEY_ALIAS = "nearer.mesh.identity.wrap.v1"
    }
}
