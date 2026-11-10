package com.nearer.app

import android.util.Base64
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.security.SecureRandom

/** Wire helpers for the post-approval Nearby P-256 proof-of-possession handshake. */
internal object NearbyPeerHandshake {
    data class Hello(val peerId: String, val publicKey: ByteArray, val nonce: ByteArray)

    fun newNonce(): ByteArray = ByteArray(32).also { SecureRandom().nextBytes(it) }

    fun hello(peerId: String, publicKey: ByteArray, nonce: ByteArray): ByteArray =
        JSONObject()
            .put("kind", "peer_hello_v1")
            .put("peerId", peerId)
            .put("publicKey", Base64.encodeToString(publicKey, Base64.NO_WRAP))
            .put("nonce", Base64.encodeToString(nonce, Base64.NO_WRAP))
            .toString().toByteArray(Charsets.UTF_8)

    fun decodeHello(json: JSONObject): Hello {
        require(json.optString("kind") == "peer_hello_v1")
        val id = json.optString("peerId")
        require(NeaRerIdentity.normalize(id) != null)
        val key = Base64.decode(json.getString("publicKey"), Base64.NO_WRAP)
        val nonce = Base64.decode(json.getString("nonce"), Base64.NO_WRAP)
        require(SecurePeerIdentity.isValidPublicKey(key) && nonce.size == 32)
        return Hello(id, key, nonce)
    }

    /** Both peers derive identical bytes regardless of who initiated the connection. */
    fun transcriptChallenge(firstId: String, firstNonce: ByteArray, secondId: String, secondNonce: ByteArray): ByteArray {
        require(firstId != secondId && firstNonce.size == 32 && secondNonce.size == 32)
        val ordered = if (firstId < secondId) {
            listOf(firstId to firstNonce, secondId to secondNonce)
        } else {
            listOf(secondId to secondNonce, firstId to firstNonce)
        }
        val output = ByteArrayOutputStream()
        DataOutputStream(output).use { data ->
            data.write("NeaRer-Nearby-Peer-Handshake-v1".toByteArray(Charsets.US_ASCII))
            ordered.forEach { (id, nonce) ->
                val encoded = id.toByteArray(Charsets.UTF_8)
                require(encoded.isNotEmpty() && encoded.size <= 64)
                data.writeShort(encoded.size)
                data.write(encoded)
                data.writeByte(nonce.size)
                data.write(nonce)
            }
        }
        return output.toByteArray()
    }

    fun proof(peerId: String, signature: ByteArray): ByteArray =
        JSONObject()
            .put("kind", "peer_proof_v1")
            .put("peerId", peerId)
            .put("signature", Base64.encodeToString(signature, Base64.NO_WRAP))
            .toString().toByteArray(Charsets.UTF_8)

    fun decodeProof(json: JSONObject): Pair<String, ByteArray> {
        require(json.optString("kind") == "peer_proof_v1")
        val id = json.optString("peerId")
        require(NeaRerIdentity.normalize(id) != null)
        val signature = Base64.decode(json.getString("signature"), Base64.NO_WRAP)
        require(signature.isNotEmpty() && signature.size <= 128)
        return id to signature
    }
}
