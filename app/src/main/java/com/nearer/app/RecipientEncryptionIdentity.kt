package com.nearer.app

import android.content.Context
import android.util.Base64
import org.json.JSONObject
import java.security.KeyFactory
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.MessageDigest
import java.security.PrivateKey
import java.security.PublicKey
import java.security.interfaces.ECPrivateKey
import java.security.interfaces.ECPublicKey
import java.security.spec.ECGenParameterSpec
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec
import javax.crypto.KeyAgreement

/**
 * Stable recipient ECDH identity for future recipient-addressed mesh packets.
 * Private key bytes are persisted only inside LocalDataCipher ciphertext.
 */
internal object RecipientEncryptionIdentity {
    private const val STORAGE_KEY = "nearer_recipient_ecdh_identity_v1"

    data class Identity(val publicKeyEncoded: ByteArray, val privateKey: PrivateKey)

    @Synchronized
    fun getOrCreate(context: Context): Identity {
        val app = context.applicationContext
        val prefs = app.getSharedPreferences("nearer_local", Context.MODE_PRIVATE)
        val stored = prefs.getString(STORAGE_KEY, null)
        if (stored != null) {
            val plaintext = LocalDataCipher.decrypt(app, stored)
                ?: throw IllegalStateException("NeaRer could not unlock the saved recipient encryption identity. It was preserved unchanged.")
            return decodeAndValidate(plaintext)
        }

        val pair = KeyPairGenerator.getInstance("EC").apply {
            initialize(ECGenParameterSpec("secp256r1"))
        }.generateKeyPair()
        val identity = Identity(pair.public.encoded, pair.private)
        validatePair(pair.public, pair.private)

        val serialized = JSONObject()
            .put("version", 1)
            .put("publicKey", Base64.encodeToString(pair.public.encoded, Base64.NO_WRAP))
            .put("privateKey", Base64.encodeToString(pair.private.encoded, Base64.NO_WRAP))
            .toString().toByteArray(Charsets.UTF_8)
        val encrypted = LocalDataCipher.encrypt(app, serialized)
        if (!prefs.edit().putString(STORAGE_KEY, encrypted).commit()) {
            throw IllegalStateException("NeaRer could not save the recipient encryption identity.")
        }
        return identity
    }

    private fun decodeAndValidate(plaintext: ByteArray): Identity {
        val json = JSONObject(plaintext.toString(Charsets.UTF_8))
        require(json.optInt("version", -1) == 1) { "Unsupported recipient identity version." }
        val publicBytes = Base64.decode(json.getString("publicKey"), Base64.NO_WRAP)
        val privateBytes = Base64.decode(json.getString("privateKey"), Base64.NO_WRAP)
        require(publicBytes.isNotEmpty() && publicBytes.size <= 512 && privateBytes.isNotEmpty() && privateBytes.size <= 512)
        val factory = KeyFactory.getInstance("EC")
        val publicKey = factory.generatePublic(X509EncodedKeySpec(publicBytes))
        val privateKey = factory.generatePrivate(PKCS8EncodedKeySpec(privateBytes))
        validatePair(publicKey, privateKey)
        return Identity(publicBytes, privateKey)
    }

    private fun validatePair(publicKey: PublicKey, privateKey: PrivateKey) {
        val ecPublic = publicKey as? ECPublicKey ?: throw IllegalArgumentException("Recipient public key is not EC.")
        val ecPrivate = privateKey as? ECPrivateKey ?: throw IllegalArgumentException("Recipient private key is not EC.")
        require(ecPublic.params.curve.field.fieldSize == 256 && ecPrivate.params.curve.field.fieldSize == 256) {
            "Recipient encryption keys must use P-256."
        }
        val probe = KeyPairGenerator.getInstance("EC").apply {
            initialize(ECGenParameterSpec("secp256r1"))
        }.generateKeyPair()
        val left = KeyAgreement.getInstance("ECDH").run {
            init(privateKey)
            doPhase(probe.public, true)
            generateSecret()
        }
        val right = KeyAgreement.getInstance("ECDH").run {
            init(probe.private)
            doPhase(publicKey, true)
            generateSecret()
        }
        val matches = MessageDigest.isEqual(left, right)
        left.fill(0)
        right.fill(0)
        require(matches) { "Recipient public and private keys do not match." }
    }
}
