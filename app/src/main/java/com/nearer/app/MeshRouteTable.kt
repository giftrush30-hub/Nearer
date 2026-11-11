package com.nearer.app

import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.security.MessageDigest
import java.security.PublicKey
import java.security.Signature

/** Signed assertion made by a peer about a destination it can reach. */
internal data class SignedRouteAdvertisement(
    val advertiserId: String,
    val destinationId: String,
    val destinationKeyFingerprint: ByteArray,
    val sequence: Long,
    val hopCount: Int,
    val issuedAtMs: Long,
    val expiresAtMs: Long,
    val signature: ByteArray
) {
    fun signedBytes(): ByteArray {
        val out = ByteArrayOutputStream()
        DataOutputStream(out).use { data ->
            data.write("NeaRer-route-advert-v1".toByteArray(Charsets.US_ASCII))
            writeString(data, advertiserId)
            writeString(data, destinationId)
            data.writeInt(destinationKeyFingerprint.size)
            data.write(destinationKeyFingerprint)
            data.writeLong(sequence)
            data.writeByte(hopCount)
            data.writeLong(issuedAtMs)
            data.writeLong(expiresAtMs)
        }
        return out.toByteArray()
    }

    fun verify(trustedAdvertiserKey: ByteArray, nowMs: Long): Boolean = try {
        require(NeaRerIdentity.normalize(advertiserId) == advertiserId)
        require(NeaRerIdentity.normalize(destinationId) == destinationId)
        require(advertiserId != destinationId)
        require(destinationKeyFingerprint.size == 32)
        require(sequence >= 0L && hopCount in 0..3)
        require(issuedAtMs <= nowMs + MAX_CLOCK_SKEW_MS)
        require(expiresAtMs > nowMs && expiresAtMs > issuedAtMs)
        require(expiresAtMs - issuedAtMs <= MAX_ROUTE_LIFETIME_MS)
        require(signature.size in 8..128)
        val key = java.security.KeyFactory.getInstance("EC")
            .generatePublic(java.security.spec.X509EncodedKeySpec(trustedAdvertiserKey))
        require((key as? java.security.interfaces.ECPublicKey)?.params?.curve?.field?.fieldSize == 256)
        val verifier = Signature.getInstance("SHA256withECDSA")
        verifier.initVerify(key)
        verifier.update(signedBytes())
        verifier.verify(signature)
    } catch (_: Exception) { false }

    companion object {
        private const val MAX_CLOCK_SKEW_MS = 5L * 60L * 1000L
        private const val MAX_ROUTE_LIFETIME_MS = 10L * 60L * 1000L

        fun create(
            advertiserId: String,
            destinationId: String,
            destinationPublicKey: ByteArray,
            sequence: Long,
            hopCount: Int,
            issuedAtMs: Long,
            expiresAtMs: Long,
            sign: (ByteArray) -> ByteArray
        ): SignedRouteAdvertisement {
            require(NeaRerIdentity.normalize(advertiserId) == advertiserId)
            require(NeaRerIdentity.normalize(destinationId) == destinationId)
            require(advertiserId != destinationId)
            val destinationKey = java.security.KeyFactory.getInstance("EC")
                .generatePublic(java.security.spec.X509EncodedKeySpec(destinationPublicKey))
            require((destinationKey as? java.security.interfaces.ECPublicKey)?.params?.curve?.field?.fieldSize == 256)
            val unsigned = SignedRouteAdvertisement(
                advertiserId, destinationId,
                MessageDigest.getInstance("SHA-256").digest(destinationPublicKey),
                sequence, hopCount, issuedAtMs, expiresAtMs, ByteArray(0)
            )
            require(unsigned.verifyHeader(issuedAtMs))
            return unsigned.copy(signature = sign(unsigned.signedBytes()))
        }

        private fun SignedRouteAdvertisement.verifyHeader(nowMs: Long): Boolean =
            sequence >= 0L && hopCount in 0..3 && expiresAtMs > nowMs &&
                expiresAtMs - issuedAtMs <= MAX_ROUTE_LIFETIME_MS
    }

    private fun writeString(data: DataOutputStream, value: String) {
        val bytes = value.toByteArray(Charsets.UTF_8)
        data.writeShort(bytes.size)
        data.write(bytes)
    }
}

/**
 * A received advertisement is accepted only from the authenticated peer whose
 * signing key verified it. The local next hop is always that direct peer;
 * remote-provided next-hop IDs are never trusted.
 */
internal class MeshRouteTable(private val localId: String) {
    data class Route(
        val destinationId: String,
        val nextHopPeerId: String,
        val hopCount: Int,
        val sequence: Long,
        val destinationKeyFingerprint: ByteArray,
        val expiresAtMs: Long
    )

    private val routes = LinkedHashMap<String, Route>()

    @Synchronized
    fun update(
        advertisement: SignedRouteAdvertisement,
        authenticatedSenderId: String,
        trustedSenderSigningKey: ByteArray,
        nowMs: Long
    ): Boolean {
        if (NeaRerIdentity.normalize(localId) != localId) return false
        if (authenticatedSenderId != advertisement.advertiserId) return false
        if (advertisement.destinationId == localId) return false
        if (!advertisement.verify(trustedSenderSigningKey, nowMs)) return false
        val candidate = Route(
            advertisement.destinationId,
            authenticatedSenderId,
            advertisement.hopCount + 1,
            advertisement.sequence,
            advertisement.destinationKeyFingerprint.copyOf(),
            advertisement.expiresAtMs
        )
        if (candidate.hopCount !in 1..4) return false
        val previous = routes[candidate.destinationId]
        if (previous != null && previous.expiresAtMs > nowMs) {
            if (candidate.sequence < previous.sequence) return false
            if (candidate.sequence == previous.sequence && candidate.hopCount >= previous.hopCount) return false
        }
        routes[candidate.destinationId] = candidate
        return true
    }

    @Synchronized
    fun find(destinationId: String, nowMs: Long): Route? {
        routes.entries.removeAll { it.value.expiresAtMs <= nowMs }
        return routes[destinationId]
    }

    @Synchronized
    fun removeNextHop(peerId: String) {
        routes.entries.removeAll { it.value.nextHopPeerId == peerId }
    }

    @Synchronized
    fun snapshot(nowMs: Long): List<Route> {
        routes.entries.removeAll { it.value.expiresAtMs <= nowMs }
        return routes.values.toList()
    }
}
