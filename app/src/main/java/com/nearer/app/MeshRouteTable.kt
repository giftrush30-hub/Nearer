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
    val signature: ByteArray,
    /** Signed path vector: advertiser, each relay, then destination. */
    val path: List<String> = listOf(advertiserId, destinationId)
) {
    fun signedBytes(): ByteArray {
        val out = ByteArrayOutputStream()
        DataOutputStream(out).use { data ->
            data.write("NeaRer-route-advert-v1".toByteArray(Charsets.US_ASCII))
            writeString(data, advertiserId)
            writeString(data, destinationId)
            data.writeByte(path.size)
            path.forEach { writeString(data, it) }
            data.writeInt(destinationKeyFingerprint.size)
            data.write(destinationKeyFingerprint)
            data.writeLong(sequence)
            data.writeByte(hopCount)
            data.writeLong(issuedAtMs)
            data.writeLong(expiresAtMs)
        }
        return out.toByteArray()
    }

    fun encode(): ByteArray {
        val out = ByteArrayOutputStream()
        DataOutputStream(out).use { data ->
            data.writeInt(0x4E525431) // NRT1
            writeString(data, advertiserId)
            writeString(data, destinationId)
            data.writeByte(path.size)
            path.forEach { writeString(data, it) }
            data.writeInt(destinationKeyFingerprint.size)
            data.write(destinationKeyFingerprint)
            data.writeLong(sequence)
            data.writeInt(hopCount)
            data.writeLong(issuedAtMs)
            data.writeLong(expiresAtMs)
            data.writeInt(signature.size)
            data.write(signature)
        }
        return out.toByteArray()
    }

    private fun localPathDoesNotLoop(): Boolean = path.none { it == destinationId && it != path.last() }

    fun verify(trustedAdvertiserKey: ByteArray, nowMs: Long): Boolean = try {
        require(NeaRerIdentity.normalize(advertiserId) == advertiserId)
        require(NeaRerIdentity.normalize(destinationId) == destinationId)
        require(advertiserId != destinationId)
        require(path.size in 2..5 && path.first() == advertiserId && path.last() == destinationId)
        require(path.all { NeaRerIdentity.normalize(it) == it } && path.distinct().size == path.size)
        require(path.size == hopCount + 2 && localPathDoesNotLoop())
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

        fun decode(encoded: ByteArray): SignedRouteAdvertisement {
            require(encoded.size in 1..2048)
            val input = java.io.DataInputStream(encoded.inputStream())
            require(input.readInt() == 0x4E525431)
            val advertiser = readString(input)
            val destination = readString(input)
            val pathSize = input.readUnsignedByte()
            require(pathSize in 2..5)
            val path = List(pathSize) { readString(input) }
            val fingerprintSize = input.readInt()
            require(fingerprintSize == 32)
            val fingerprint = ByteArray(fingerprintSize).also { input.readFully(it) }
            val sequence = input.readLong()
            val hops = input.readInt()
            val issued = input.readLong()
            val expires = input.readLong()
            val signatureSize = input.readInt()
            require(signatureSize in 8..128)
            val signature = ByteArray(signatureSize).also { input.readFully(it) }
            require(input.available() == 0)
            return SignedRouteAdvertisement(advertiser, destination, fingerprint, sequence, hops, issued, expires, signature, path)
        }

        private fun readString(input: java.io.DataInputStream): String {
            val size = input.readUnsignedShort()
            require(size in 1..64)
            val bytes = ByteArray(size).also(input::readFully)
            return bytes.toString(Charsets.UTF_8)
        }

        fun create(
            advertiserId: String,
            destinationId: String,
            destinationPublicKey: ByteArray,
            sequence: Long,
            hopCount: Int,
            issuedAtMs: Long,
            expiresAtMs: Long,
            path: List<String> = listOf(advertiserId, destinationId),
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
                sequence, hopCount, issuedAtMs, expiresAtMs, ByteArray(0), path
            )
            require(unsigned.verifyHeader(issuedAtMs))
            require(path.size in 2..5 && path.first() == advertiserId && path.last() == destinationId)
            require(path.size == hopCount + 2 && path.distinct().size == path.size)
            require(path.all { NeaRerIdentity.normalize(it) == it })
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
        /** Authenticated direct neighbor used as the next hop and route advertiser. */
        val nextHopPeerId: String,
        val hopCount: Int,
        val sequence: Long,
        val issuedAtMs: Long,
        val destinationKeyFingerprint: ByteArray,
        val expiresAtMs: Long,
        /** Complete path from this node to the destination, including both ends. */
        val path: List<String>
    )

    private val routes = LinkedHashMap<String, Route>()

    @Synchronized
    fun update(
        advertisement: SignedRouteAdvertisement,
        authenticatedSenderId: String,
        trustedSenderSigningKey: ByteArray,
        nowMs: Long,
        knownDestinationEncryptionKey: ByteArray? = null
    ): Boolean {
        if (NeaRerIdentity.normalize(localId) != localId) return false
        if (NeaRerIdentity.normalize(authenticatedSenderId) != authenticatedSenderId) return false
        if (authenticatedSenderId == localId || authenticatedSenderId != advertisement.advertiserId) return false
        if (advertisement.destinationId == localId || advertisement.destinationId == authenticatedSenderId) return false
        if (localId in advertisement.path) return false
        if (!advertisement.verify(trustedSenderSigningKey, nowMs)) return false
        if (knownDestinationEncryptionKey != null) {
            val expected = MessageDigest.getInstance("SHA-256").digest(knownDestinationEncryptionKey)
            if (!MessageDigest.isEqual(expected, advertisement.destinationKeyFingerprint)) return false
        }
        val candidate = Route(
            advertisement.destinationId,
            authenticatedSenderId,
            advertisement.hopCount + 1,
            advertisement.sequence,
            advertisement.issuedAtMs,
            advertisement.destinationKeyFingerprint.copyOf(),
            advertisement.expiresAtMs,
            listOf(localId) + advertisement.path
        )
        if (candidate.hopCount !in 1..4 || candidate.path.size != candidate.hopCount + 2) return false
        val previous = routes[candidate.destinationId]
        if (previous != null && previous.expiresAtMs > nowMs) {
            // Sequence numbers belong to the advertiser, not globally to a
            // destination. Compare them only when the same neighbor advertised
            // both routes; otherwise compare signed freshness, then path length.
            if (candidate.nextHopPeerId == previous.nextHopPeerId) {
                if (candidate.sequence < previous.sequence) return false
                if (candidate.sequence == previous.sequence && candidate.hopCount >= previous.hopCount) return false
            } else {
                // Prefer a shorter loop-free path. Freshness breaks ties between
                // equally short routes; timestamps never make a longer route win.
                if (candidate.hopCount > previous.hopCount) return false
                if (candidate.hopCount == previous.hopCount && candidate.issuedAtMs < previous.issuedAtMs) return false
                if (candidate.hopCount == previous.hopCount && candidate.issuedAtMs == previous.issuedAtMs &&
                    candidate.nextHopPeerId >= previous.nextHopPeerId) return false
            }
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
