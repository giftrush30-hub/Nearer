package com.nearer.app

import android.util.Base64
import org.json.JSONObject
import java.security.MessageDigest
import java.security.SecureRandom

/** Wire helpers for the authenticated ephemeral ECDH handshake. */
internal object NearbyPeerHandshake {
    data class Hello(
        val peerId: String,
        val publicKey: ByteArray,
        val nonce: ByteArray,
        val ephemeralPublicKey: ByteArray
    )

    fun newNonce(): ByteArray = ByteArray(32).also { SecureRandom().nextBytes(it) }

    fun hello(peerId: String, publicKey: ByteArray, nonce: ByteArray, ephemeralPublicKey: ByteArray): ByteArray =
        JSONObject()
            .put("kind", "peer_hello_v2")
            .put("peerId", peerId)
            .put("publicKey", Base64.encodeToString(publicKey, Base64.NO_WRAP))
            .put("nonce", Base64.encodeToString(nonce, Base64.NO_WRAP))
            .put("ephemeralPublicKey", Base64.encodeToString(ephemeralPublicKey, Base64.NO_WRAP))
            .toString().toByteArray(Charsets.UTF_8)

    fun decodeHello(json: JSONObject): Hello {
        require(json.optString("kind") == "peer_hello_v2")
        val id = json.optString("peerId")
        require(NeaRerIdentity.normalize(id) != null)
        val key = Base64.decode(json.getString("publicKey"), Base64.NO_WRAP)
        val nonce = Base64.decode(json.getString("nonce"), Base64.NO_WRAP)
        val ephemeral = Base64.decode(json.getString("ephemeralPublicKey"), Base64.NO_WRAP)
        require(SecurePeerIdentity.isValidPublicKey(key) && nonce.size == 32)
        NearbyMessageCrypto.decodeEphemeralPublicKey(ephemeral)
        return Hello(id, key, nonce, ephemeral)
    }

    /** The signed transcript binds both long-term identities, fresh nonces and ECDH keys. */
    fun transcriptChallenge(
        firstId: String, firstNonce: ByteArray, firstEphemeralKey: ByteArray,
        secondId: String, secondNonce: ByteArray, secondEphemeralKey: ByteArray
    ): ByteArray = NearbyMessageCrypto.transcript(
        firstId, firstNonce, firstEphemeralKey,
        secondId, secondNonce, secondEphemeralKey
    )

    fun proof(peerId: String, signature: ByteArray): ByteArray =
        JSONObject()
            .put("kind", "peer_proof_v2")
            .put("peerId", peerId)
            .put("signature", Base64.encodeToString(signature, Base64.NO_WRAP))
            .toString().toByteArray(Charsets.UTF_8)

    fun decodeProof(json: JSONObject): Pair<String, ByteArray> {
        require(json.optString("kind") == "peer_proof_v2")
        val id = json.optString("peerId")
        require(NeaRerIdentity.normalize(id) != null)
        val signature = Base64.decode(json.getString("signature"), Base64.NO_WRAP)
        require(signature.isNotEmpty() && signature.size <= 128)
        return id to signature
    }
}
