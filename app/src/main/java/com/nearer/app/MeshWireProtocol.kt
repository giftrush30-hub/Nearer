package com.nearer.app

import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.security.KeyFactory
import java.security.KeyPair
import java.security.MessageDigest
import java.security.PublicKey
import java.security.Signature
import java.security.spec.X509EncodedKeySpec

/**
 * Versioned JSON wire contract for mesh control/data frames.
 * Identity hello proves possession of the advertised signing key; it does NOT
 * prove the human identity behind that key. The displayed fingerprint must be
 * compared through a trusted channel before a peer is authorised to relay.
 */
internal object MeshWireProtocol {
    const val VERSION = 1
    const val MAX_FRAME_BYTES = 128 * 1024
    const val KIND_IDENTITY_HELLO = "mesh_identity_hello"
    const val KIND_ROUTE_ADVERTISEMENT = "mesh_route"
    const val KIND_RELAY_PACKET = "mesh_relay"
    const val KIND_RELAY_ACK = "mesh_relay_ack"

    data class IdentityHello(
        val peerId: String,
        val signingPublicKey: String,
        val agreementPublicKey: String,
        val nonce: String,
        val signature: String
    ) {
        val fingerprint: String get() = fingerprint(signingPublicKey)
    }

    data class RouteAdvertisement(
        val originPeerId: String,
        val destinationPeerId: String,
        val hopCount: Int,
        val issuedAtMillis: Long,
        val signature: String
    )

    fun createIdentityHello(peerId: String, identity: MeshIdentityKeys): IdentityHello {
        require(peerId.isNotBlank() && peerId.length <= 64)
        val nonce = MeshCrypto.encode(ByteArray(24).also { java.security.SecureRandom().nextBytes(it) })
        val unsigned = IdentityHello(
            peerId, identity.signingPublicKey, identity.agreementPublicKey, nonce, ""
        )
        val signer = Signature.getInstance("SHA256withECDSA")
        signer.initSign(identity.signing.private)
        signer.update(helloCanonical(unsigned))
        return unsigned.copy(signature = MeshCrypto.encode(signer.sign()))
    }

    fun verifyIdentityHello(hello: IdentityHello): Boolean {\n        return try {
        if (hello.peerId.isBlank() || hello.peerId.length > 64 ||
            hello.signingPublicKey.length !in 1..512 ||
            hello.agreementPublicKey.length !in 1..512 ||
            hello.nonce.length !in 1..128 ||
            hello.signature.length !in 1..256 ||
            MeshCrypto.decode(hello.nonce).size != 24
        ) return false
        val publicKey = decodeEcPublicKey(hello.signingPublicKey)
        decodeEcPublicKey(hello.agreementPublicKey)
        val verifier = Signature.getInstance("SHA256withECDSA")
        verifier.initVerify(publicKey)
        verifier.update(helloCanonical(hello.copy(signature = "")))
        verifier.verify(MeshCrypto.decode(hello.signature))
    } catch (_: Exception) {
        false
    }

    fun encodeIdentityHello(hello: IdentityHello): ByteArray = JSONObject()
        .put("kind", KIND_IDENTITY_HELLO)
        .put("version", VERSION)
        .put("peerId", hello.peerId)
        .put("signingKey", hello.signingPublicKey)
        .put("agreementKey", hello.agreementPublicKey)
        .put("nonce", hello.nonce)
        .put("signature", hello.signature)
        .toString().toByteArray(Charsets.UTF_8)
        .also { require(it.size <= MAX_FRAME_BYTES) }

    fun decodeIdentityHello(json: JSONObject): IdentityHello? {\n        return try {
        if (json.optString("kind") != KIND_IDENTITY_HELLO ||
            json.optInt("version", -1) != VERSION
        ) return null
        IdentityHello(
            json.getString("peerId"),
            json.getString("signingKey"),
            json.getString("agreementKey"),
            json.getString("nonce"),
            json.getString("signature")
        ).takeIf(::verifyIdentityHello)
    } catch (_: Exception) {
        null
    }

    fun createRouteAdvertisement(
        originPeerId: String,
        destinationPeerId: String,
        hopCount: Int,
        issuedAtMillis: Long,
        signingKeys: KeyPair
    ): RouteAdvertisement {
        require(originPeerId.isNotBlank() && originPeerId.length <= 64)
        require(destinationPeerId.isNotBlank() && destinationPeerId.length <= 64)
        require(originPeerId != destinationPeerId)
        require(hopCount in 0 until MeshRelayPolicy.MAX_HOPS)
        require(issuedAtMillis > 0)
        val unsigned = RouteAdvertisement(originPeerId, destinationPeerId, hopCount, issuedAtMillis, "")
        val signer = Signature.getInstance("SHA256withECDSA")
        signer.initSign(signingKeys.private)
        signer.update(routeCanonical(unsigned))
        return unsigned.copy(signature = MeshCrypto.encode(signer.sign()))
    }

    fun verifyRouteAdvertisement(
        route: RouteAdvertisement,
        trustedOriginSigningKey: String,
        nowMillis: Long,
        maxAgeMillis: Long = 90_000L
    ): Boolean = try {
        if (route.originPeerId.isBlank() || route.destinationPeerId.isBlank() ||
            route.originPeerId == route.destinationPeerId ||
            route.hopCount !in 0 until MeshRelayPolicy.MAX_HOPS ||
            route.issuedAtMillis <= 0 || nowMillis < route.issuedAtMillis ||
            nowMillis - route.issuedAtMillis > maxAgeMillis
        ) return false
        val verifier = Signature.getInstance("SHA256withECDSA")
        verifier.initVerify(decodeEcPublicKey(trustedOriginSigningKey))
        verifier.update(routeCanonical(route.copy(signature = "")))
        verifier.verify(MeshCrypto.decode(route.signature))
    } catch (_: Exception) {
        false
    }

    fun encodeRoute(route: RouteAdvertisement): ByteArray = JSONObject()
        .put("kind", KIND_ROUTE_ADVERTISEMENT)
        .put("version", VERSION)
        .put("origin", route.originPeerId)
        .put("destination", route.destinationPeerId)
        .put("hops", route.hopCount)
        .put("issuedAt", route.issuedAtMillis)
        .put("signature", route.signature)
        .toString().toByteArray(Charsets.UTF_8)
        .also { require(it.size <= MAX_FRAME_BYTES) }

    fun decodeRoute(json: JSONObject): RouteAdvertisement? = try {
        if (json.optString("kind") != KIND_ROUTE_ADVERTISEMENT ||
            json.optInt("version", -1) != VERSION
        ) return null
        RouteAdvertisement(
            json.getString("origin"),
            json.getString("destination"),
            json.getInt("hops"),
            json.getLong("issuedAt"),
            json.getString("signature")
        )
    } catch (_: Exception) {
        null
    }

    fun fingerprint(publicKey: String): String =
        MessageDigest.getInstance("SHA-256").digest(MeshCrypto.decode(publicKey)).take(16)
            .joinToString(":") { "%02X".format(it) }

    private fun decodeEcPublicKey(value: String): PublicKey =
        KeyFactory.getInstance("EC").generatePublic(X509EncodedKeySpec(MeshCrypto.decode(value)))

    private fun helloCanonical(hello: IdentityHello): ByteArray =
        canonical("NEARER-MESH-IDENTITY-V1", hello.peerId, hello.signingPublicKey,
            hello.agreementPublicKey, hello.nonce)

    private fun routeCanonical(route: RouteAdvertisement): ByteArray =
        canonical("NEARER-MESH-ROUTE-V1", route.originPeerId, route.destinationPeerId,
            route.hopCount.toString(), route.issuedAtMillis.toString())

    private fun canonical(vararg values: String): ByteArray = ByteArrayOutputStream().use { bytes ->
        DataOutputStream(bytes).use { out -> values.forEach(out::writeUTF) }
        bytes.toByteArray()
    }
}
