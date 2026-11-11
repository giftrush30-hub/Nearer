package com.nearer.app

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.Signature
import java.security.spec.ECGenParameterSpec
import java.security.spec.X509EncodedKeySpec
import java.security.MessageDigest

/**
 * Device-bound P-256 signing identity for proving possession of the private key
 * during nearby handshakes. This proves key possession, not a real-world person's identity.
 */
internal object SecurePeerIdentity {
    private const val KEY_ALIAS = "nearer.peer.identity.ecdsa.v1"

    @Synchronized
    private fun ensureKeyPair() {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        if (store.containsAlias(KEY_ALIAS)) return
        val generator = KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_EC, "AndroidKeyStore")
        generator.initialize(
            KeyGenParameterSpec.Builder(KEY_ALIAS, KeyProperties.PURPOSE_SIGN)
                .setAlgorithmParameterSpec(ECGenParameterSpec("secp256r1"))
                .setDigests(KeyProperties.DIGEST_SHA256)
                .build()
        )
        generator.generateKeyPair()
    }

    fun publicKeyEncoded(): ByteArray {
        ensureKeyPair()
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        return store.getCertificate(KEY_ALIAS).publicKey.encoded
    }

    fun signChallenge(publicId: String, publicKey: ByteArray, challenge: ByteArray): ByteArray {
        ensureKeyPair()
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        val privateKey = store.getKey(KEY_ALIAS, null) as java.security.PrivateKey
        val signer = Signature.getInstance("SHA256withECDSA")
        signer.initSign(privateKey)
        signer.update(proofMessage(publicId, publicKey, challenge))
        return signer.sign()
    }

    fun verifyChallenge(publicId: String, publicKey: ByteArray, challenge: ByteArray, signature: ByteArray): Boolean {
        return try {
            val key = KeyFactory.getInstance("EC").generatePublic(X509EncodedKeySpec(publicKey))
            val verifier = Signature.getInstance("SHA256withECDSA")
            verifier.initVerify(key)
            verifier.update(proofMessage(publicId, publicKey, challenge))
            verifier.verify(signature)
        } catch (_: Exception) { false }
    }

    /** Signs an already domain-separated protocol transcript without altering its bytes. */
    fun signDetached(challenge: ByteArray): ByteArray {
        ensureKeyPair()
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        val privateKey = store.getKey(KEY_ALIAS, null) as java.security.PrivateKey
        return Signature.getInstance("SHA256withECDSA").run {
            initSign(privateKey)
            update(challenge)
            sign()
        }
    }

    fun verifyDetached(publicKey: ByteArray, challenge: ByteArray, signature: ByteArray): Boolean = try {
        val key = KeyFactory.getInstance("EC").generatePublic(X509EncodedKeySpec(publicKey))
        Signature.getInstance("SHA256withECDSA").run {
            initVerify(key)
            update(challenge)
            verify(signature)
        }
    } catch (_: Exception) { false }

    fun isValidPublicKey(encoded: ByteArray): Boolean {
        if (encoded.isEmpty() || encoded.size > 512) return false
        return try {
            val key = KeyFactory.getInstance("EC").generatePublic(X509EncodedKeySpec(encoded))
            key.algorithm.equals("EC", ignoreCase = true)
        } catch (_: Exception) { false }
    }

    private fun proofMessage(publicId: String, publicKey: ByteArray, challenge: ByteArray): ByteArray {
        val fingerprint = MessageDigest.getInstance("SHA-256").digest(publicKey)
            .joinToString("") { "%02x".format(it) }
        val nonce = android.util.Base64.encodeToString(challenge, android.util.Base64.NO_WRAP)
        return ("NeaRer-peer-proof-v1\n" + publicId + "\n" + fingerprint + "\n" + nonce).toByteArray(Charsets.UTF_8)
    }
}
