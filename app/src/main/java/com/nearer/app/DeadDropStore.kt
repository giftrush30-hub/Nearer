package com.nearer.app

import android.content.Context
import org.json.JSONObject

/**
 * Encrypted-at-rest queue for opaque recipient-encrypted packets.
 * Caller must verify the origin signature against a pinned identity before enqueue.
 */
internal class DeadDropStore(context: Context) {
    data class Pending(
        val packetId: String,
        val bytes: ByteArray,
        val attempts: Int,
        val previousHopPeerId: String,
        /** Verified, locally appended acceptance chain; empty only at the origin. */
        val proofChain: ByteArray = ByteArray(0)
    )

    private data class Entry(
        val encrypted: String,
        val attempts: Int,
        val state: String,
        val dueAt: Long,
        val leaseUntil: Long,
        val previousHopPeerId: String = "",
        val preparedForNextHop: Boolean = false,
        val expectedNextHopPeerId: String = "",
        /** Stored inside the encrypted queue wrapper and committed with the packet. */
        val proofChain: String = ""
    )

    private val app = context.applicationContext
    private val prefs = app.getSharedPreferences("nearer_dead_drop_v1", Context.MODE_PRIVATE)

    @Synchronized
    fun enqueueVerified(packet: MeshRelayPacketV1, trustedOriginKey: ByteArray, now: Long): Boolean {
        if (!packet.verify(trustedOriginKey, now)) return false
        return enqueueValidatedPacket(packet, now)
    }

    /**
     * Stores an opaque packet for transit after verifying its origin signature
     * against the key embedded in the packet. This proves packet integrity only;
     * it does NOT establish that the claimed origin identity is trusted.
     * The final recipient must compare the origin key with its pinned identity.
     */
    @Synchronized
    fun enqueueTransit(packet: MeshRelayPacketV1, previousHopPeerId: String, now: Long): Boolean {
        if (!packet.verifyEmbeddedOrigin(now) ||
            NeaRerIdentity.normalize(previousHopPeerId) != previousHopPeerId) return false
        return enqueueValidatedPacket(packet, now, previousHopPeerId)
    }

    /**
     * Verifies the authenticated previous-hop proof prefix, appends this node's
     * signed acceptance, and commits packet + chain in one encrypted preference
     * transaction. The caller may send a hop ACK only after this returns true.
     */
    @Synchronized
    fun enqueueTransitWithProof(
        packet: MeshRelayPacketV1,
        previousHopPeerId: String,
        incomingProofBytes: ByteArray,
        trustedAcceptingSigningKey: (String) -> ByteArray?,
        localPeerId: String,
        localSigningPublicKey: ByteArray,
        now: Long,
        sign: (ByteArray) -> ByteArray
    ): Boolean {
        if (!packet.verifyEmbeddedOrigin(now) ||
            NeaRerIdentity.normalize(previousHopPeerId) != previousHopPeerId ||
            NeaRerIdentity.normalize(localPeerId) != localPeerId ||
            localPeerId == packet.originId) return false
        return try {
            val incoming = if (incomingProofBytes.isEmpty()) {
                MeshForwardingProofChain(emptyList())
            } else {
                MeshForwardingProofChain.decode(incomingProofBytes)
            }
            if (!incoming.verifyPrefix(
                    packet.packetId, packet.immutablePacketDigest(), packet.originId,
                    previousHopPeerId, trustedAcceptingSigningKey, now
                )) return false

            val entries = read() ?: return false
            val existing = entries[packet.packetId]
            val accepted = incoming.appendAcceptance(
                packet.packetId, packet.immutablePacketDigest(), packet.originId,
                localPeerId, now, sign
            )
            if (existing != null) {
                val oldBytes = LocalDataCipher.decrypt(app, existing.encrypted) ?: return false
                val oldPacket = MeshRelayPacketV1.decode(oldBytes)
                if (!oldPacket.sameImmutablePacket(packet) || packet.hopsTaken < oldPacket.hopsTaken) return false
                val oldProof = if (existing.proofChain.isBlank()) null else try {
                    MeshForwardingProofChain.decode(
                        android.util.Base64.decode(existing.proofChain, android.util.Base64.NO_WRAP)
                    )
                } catch (_: Exception) { return false }
                if (oldProof != null &&
                    oldProof.verifyPrefix(
                        packet.packetId, packet.immutablePacketDigest(), packet.originId,
                        localPeerId,
                        { peerId -> if (peerId == localPeerId) localSigningPublicKey else trustedAcceptingSigningKey(peerId) },
                        now
                    ) &&
                    oldProof.proofs.lastOrNull()?.previousHopPeerId == previousHopPeerId &&
                    oldProof.proofs.size >= accepted.proofs.size) {
                    // Same durable acceptance already exists: safe idempotent ACK.
                    return true
                }
                if (oldProof != null && oldProof.proofs.size > accepted.proofs.size) return false
                val updatedEncrypted = try { LocalDataCipher.encrypt(app, packet.encode()) } catch (_: Exception) { return false }
                val proofText = android.util.Base64.encodeToString(accepted.encode(), android.util.Base64.NO_WRAP)
                entries[packet.packetId] = existing.copy(
                    encrypted = updatedEncrypted,
                    state = QUEUED,
                    dueAt = now,
                    leaseUntil = 0L,
                    previousHopPeerId = previousHopPeerId,
                    preparedForNextHop = false,
                    expectedNextHopPeerId = "",
                    proofChain = proofText
                )
                return write(entries)
            }
            // Verify the newly appended local signature too; do not rely on the
            // signing callback merely returning a syntactically sized byte array.
            if (!accepted.verifyPrefix(
                    packet.packetId, packet.immutablePacketDigest(), packet.originId,
                    localPeerId,
                    { peerId -> if (peerId == localPeerId) localSigningPublicKey else trustedAcceptingSigningKey(peerId) },
                    now
                )) return false
            val encodedProof = accepted.encode()
            val encodedPacket = packet.encode()
            if (encodedPacket.size > MAX_PACKET_BYTES || entries.size >= MAX_ITEMS) return false
            var originItems = 0
            var originChars = 0
            entries.values.forEach { entry ->
                val clear = LocalDataCipher.decrypt(app, entry.encrypted) ?: return false
                val old = MeshRelayPacketV1.decode(clear)
                if (old.originId == packet.originId) {
                    originItems++
                    originChars += entry.encrypted.length
                }
            }
            if (originItems >= MAX_PER_ORIGIN_ITEMS || originChars >= MAX_PER_ORIGIN_CHARS) return false
            val encryptedPacket = LocalDataCipher.encrypt(app, encodedPacket)
            if (originChars + encryptedPacket.length > MAX_PER_ORIGIN_CHARS ||
                entries.values.sumOf { it.encrypted.length } + encryptedPacket.length > MAX_TOTAL_CHARS) return false
            val proofText = android.util.Base64.encodeToString(encodedProof, android.util.Base64.NO_WRAP)
            entries[packet.packetId] = Entry(
                encryptedPacket, 0, QUEUED, now, 0L, previousHopPeerId,
                proofChain = proofText
            )
            write(entries)
        } catch (_: Exception) { false }
    }

    private fun enqueueValidatedPacket(
        packet: MeshRelayPacketV1,
        now: Long,
        previousHopPeerId: String = ""
    ): Boolean {
        // Expired items must not occupy scarce queue or per-origin quota.
        purgeExpired(now)
        val entries = read() ?: return false
        val existing = entries[packet.packetId]
        if (existing != null) {
            val oldBytes = LocalDataCipher.decrypt(app, existing.encrypted) ?: return false
            val oldPacket = try { MeshRelayPacketV1.decode(oldBytes) } catch (_: Exception) { return false }
            // Never ACK a reused packet ID carrying different signed content.
            if (!oldPacket.sameImmutablePacket(packet) || packet.hopsTaken < oldPacket.hopsTaken) return false
            // A duplicate arriving through another authenticated neighbor may provide
            // a better path, but it may never roll back the observed hop counter.
            if (packet.hopsTaken > oldPacket.hopsTaken) {
                val encrypted = try { LocalDataCipher.encrypt(app, packet.encode()) } catch (_: Exception) { return false }
                entries[packet.packetId] = existing.copy(
                    encrypted = encrypted,
                    previousHopPeerId = previousHopPeerId.ifEmpty { existing.previousHopPeerId },
                    preparedForNextHop = false
                )
                return write(entries)
            }
            if (previousHopPeerId.isNotEmpty() && existing.previousHopPeerId != previousHopPeerId) {
                entries[packet.packetId] = existing.copy(previousHopPeerId = previousHopPeerId)
                return write(entries)
            }
            return true
        }
        if (entries.size >= MAX_ITEMS) return false
        var originItems = 0
        var originEncryptedChars = 0
        entries.values.forEach { entry ->
            val existingClear = LocalDataCipher.decrypt(app, entry.encrypted) ?: return false
            val existingPacket = try { MeshRelayPacketV1.decode(existingClear) } catch (_: Exception) { return false }
            if (existingPacket.originId == packet.originId) {
                originItems += 1
                originEncryptedChars += entry.encrypted.length
            }
        }
        if (originItems >= MAX_PER_ORIGIN_ITEMS ||
            originEncryptedChars >= MAX_PER_ORIGIN_CHARS) return false
        val bytes = packet.encode()
        if (bytes.size > MAX_PACKET_BYTES) return false
        val encrypted = try { LocalDataCipher.encrypt(app, bytes) } catch (_: Exception) { return false }
        if (originEncryptedChars + encrypted.length > MAX_PER_ORIGIN_CHARS ||
            entries.values.sumOf { it.encrypted.length } + encrypted.length > MAX_TOTAL_CHARS) return false
        entries[packet.packetId] = Entry(encrypted, 0, QUEUED, now, 0L, previousHopPeerId)
        return write(entries)
    }

    /** Claims due items; an expired forwarding lease is recovered after process death. */
    @Synchronized
    fun claimDue(
        now: Long,
        limit: Int = 8,
        eligible: (MeshRelayPacketV1, String) -> Boolean = { _, _ -> true }
    ): List<Pending> {
        if (limit !in 1..MAX_ITEMS) return emptyList()
        val entries = read() ?: return emptyList()
        val result = mutableListOf<Pending>()
        entries.toMap().forEach { (id, entry) ->
            if (result.size >= limit) return@forEach
            val due = entry.dueAt <= now && (
                entry.state == QUEUED || entry.state == AWAITING_DESTINATION ||
                (entry.state == FORWARDING && entry.leaseUntil <= now)
            )
            if (!due) return@forEach
            val clear = LocalDataCipher.decrypt(app, entry.encrypted) ?: return@forEach
            val packet = try { MeshRelayPacketV1.decode(clear) } catch (_: Exception) {
                entries.remove(id)
                return@forEach
            }
            if (packet.packetId != id || !packet.verifyEmbeddedOrigin(now)) {
                entries.remove(id)
                return@forEach
            }
            if (!eligible(packet, entry.previousHopPeerId)) return@forEach
            // Retry until packet expiry. The counter saturates only to bound
            // persisted metadata; it is not a terminal attempt limit.
            val attempts = (entry.attempts + 1).coerceAtMost(MAX_RETRY_LEVEL)
            entries[id] = entry.copy(
                attempts = attempts, state = FORWARDING,
                dueAt = now + backoff(attempts), leaseUntil = now + LEASE_MS
            )
            result += Pending(
                id, clear, attempts, entry.previousHopPeerId,
                if (entry.proofChain.isBlank()) ByteArray(0)
                else android.util.Base64.decode(entry.proofChain, android.util.Base64.NO_WRAP)
            )
        }
        // A lease is useful only if its state reached durable storage. Never
        // transmit a packet whose claim could be lost on process death.
        if (!write(entries)) return emptyList()
        return result
    }

    /**
     * A hop ACK is accepted only when signed by the expected next hop and
     * explicitly addressed to this device. It never deletes the packet.
     */
    @Synchronized
    fun recordHopAccepted(
        acknowledgement: MeshHopAcknowledgement,
        trustedAcceptingPeerKey: ByteArray,
        expectedAcceptingPeerId: String,
        localPeerId: String,
        now: Long
    ): Boolean {
        if (acknowledgement.acceptingPeerId != expectedAcceptingPeerId ||
            !acknowledgement.verify(trustedAcceptingPeerKey, localPeerId, now)) return false
        val entries = read() ?: return false
        val old = entries[acknowledgement.packetId] ?: return false
        // An authenticated neighbor is not necessarily the neighbor selected for
        // this packet. Bind the ACK to the durable next-hop choice made before send.
        if (old.state != FORWARDING || old.expectedNextHopPeerId != expectedAcceptingPeerId) return false
        entries[acknowledgement.packetId] = old.copy(
            state = AWAITING_DESTINATION,
            dueAt = now + backoff(old.attempts),
            leaseUntil = 0L
        )
        return write(entries)
    }

    /** Removes a destination's local transit copy only after its app-level save succeeds. */
    @Synchronized
    fun markLocallyDelivered(packetId: String): Boolean {
        if (!packetId.matches(Regex("[A-Za-z0-9._:-]{1,80}"))) return false
        val entries = read() ?: return false
        if (!entries.containsKey(packetId)) return false
        entries.remove(packetId)
        return write(entries)
    }

    /** Only a destination-signed receipt can remove a stored packet. */
    @Synchronized
    fun markDelivered(receipt: MeshDeliveryReceipt, trustedDestinationKey: ByteArray, now: Long): Boolean {
        if (!receipt.verify(trustedDestinationKey, now)) return false
        val entries = read() ?: return false
        // Idempotent duplicate receipt: the packet was already removed after a
        // previously verified final receipt, so the sender may safely be ACKed.
        val old = entries[receipt.packetId] ?: return true
        val clear = LocalDataCipher.decrypt(app, old.encrypted) ?: return false
        val packet = try { MeshRelayPacketV1.decode(clear) } catch (_: Exception) { return false }
        if (packet.packetId != receipt.packetId ||
            packet.originId != receipt.originId || packet.destinationId != receipt.destinationId) return false
        entries.remove(receipt.packetId)
        return write(entries)
    }

    @Synchronized
    fun purgeExpired(now: Long): Int {
        val entries = read() ?: return 0
        val before = entries.size
        entries.toMap().forEach { (id, entry) ->
            val clear = LocalDataCipher.decrypt(app, entry.encrypted) ?: return@forEach
            val packet = try { MeshRelayPacketV1.decode(clear) } catch (_: Exception) {
                entries.remove(id)
                return@forEach
            }
            if (packet.packetId != id || packet.expiresAtMs <= now) entries.remove(id)
        }
        val removed = before - entries.size
        if (removed > 0) write(entries)
        return removed
    }

    /** Returns locally addressed packets waiting for the recipient's identity trust or app save. */
    @Synchronized
    fun pendingForDestination(destinationId: String, now: Long, limit: Int = 16): List<MeshRelayPacketV1> {
        if (NeaRerIdentity.normalize(destinationId) != destinationId || limit !in 1..MAX_ITEMS) return emptyList()
        val entries = read() ?: return emptyList()
        val result = mutableListOf<MeshRelayPacketV1>()
        entries.forEach { (packetId, entry) ->
            if (result.size >= limit) return@forEach
            val clear = LocalDataCipher.decrypt(app, entry.encrypted) ?: return@forEach
            val packet = try { MeshRelayPacketV1.decode(clear) } catch (_: Exception) { return@forEach }
            if (packet.packetId == packetId &&
                packet.destinationId == destinationId && packet.verifyEmbeddedOrigin(now)) result += packet
        }
        return result
    }

    /**
     * Persists this node's hop increment exactly once before transmission.
     * Retries reuse the same prepared packet; process death cannot reset the counter.
     */
    @Synchronized
    fun prepareForForwarding(
        packetId: String,
        localPeerId: String,
        now: Long,
        expectedNextHopPeerId: String = ""
    ): MeshRelayPacketV1? {
        if (NeaRerIdentity.normalize(localPeerId) != localPeerId ||
            (expectedNextHopPeerId.isNotEmpty() && NeaRerIdentity.normalize(expectedNextHopPeerId) != expectedNextHopPeerId)) return null
        val entries = read() ?: return null
        val entry = entries[packetId] ?: return null
        val clear = LocalDataCipher.decrypt(app, entry.encrypted) ?: return null
        val packet = try { MeshRelayPacketV1.decode(clear) } catch (_: Exception) { return null }
        if (packet.packetId != packetId || !packet.verifyEmbeddedOrigin(now)) return null
        if (entry.preparedForNextHop) {
            if (expectedNextHopPeerId.isNotEmpty() && entry.expectedNextHopPeerId != expectedNextHopPeerId) {
                entries[packetId] = entry.copy(expectedNextHopPeerId = expectedNextHopPeerId)
                if (!write(entries)) return null
            }
            return packet
        }
        val prepared = if (packet.originId == localPeerId) packet else packet.forwarded() ?: return null
        val encrypted = try { LocalDataCipher.encrypt(app, prepared.encode()) } catch (_: Exception) { return null }
        entries[packetId] = entry.copy(
            encrypted = encrypted,
            preparedForNextHop = true,
            expectedNextHopPeerId = expectedNextHopPeerId
        )
        return if (write(entries)) prepared else null
    }

    /** Earliest due/lease expiry for a foreground retry scheduler. */
    @Synchronized
    fun nextWakeAt(): Long? {
        val entries = read() ?: return null
        // Retry remains eligible until the signed packet expiry. Attempts saturate
        // only to bound persisted metadata; they are never a terminal cutoff.
        return entries.values
            .map { entry ->
                if (entry.state == FORWARDING) maxOf(entry.dueAt, entry.leaseUntil)
                else entry.dueAt
            }
            .minOrNull()
    }

    @Synchronized fun size(): Int = read()?.size ?: 0

    private fun read(): LinkedHashMap<String, Entry>? = try {
        val raw = prefs.getString(KEY, null) ?: return LinkedHashMap()
        if (raw.length > MAX_STORED_CHARS) return null
        // Migrate older development entries that encrypted packet bytes but
        // left queue metadata in a plaintext JSON wrapper. New writes encrypt
        // the entire wrapper, including packet and peer identifiers.
        val clear = if (raw.trimStart().startsWith("{")) {
            raw.toByteArray(Charsets.UTF_8)
        } else {
            LocalDataCipher.decrypt(app, raw) ?: return null
        }
        val json = JSONObject(clear.toString(Charsets.UTF_8))
        val result = LinkedHashMap<String, Entry>()
        val keys = json.keys()
        while (keys.hasNext()) {
            val id = keys.next()
            if (id.isBlank() || id.length > 80 || result.size >= MAX_ITEMS) return null
            val item = json.getJSONObject(id)
            val encrypted = item.getString("encrypted")
            val attempts = item.getInt("attempts")
            val state = item.getString("state")
            val dueAt = item.getLong("dueAt")
            val leaseUntil = item.optLong("leaseUntil", 0L)
            val previousHopPeerId = item.optString("previousHopPeerId", "")
            val preparedForNextHop = item.optBoolean("preparedForNextHop", false)
            val expectedNextHopPeerId = item.optString("expectedNextHopPeerId", "")
            val proofChain = item.optString("proofChain", "")
            if (encrypted.isBlank() || encrypted.length > MAX_TOTAL_CHARS ||
                attempts !in 0..MAX_RETRY_LEVEL || state !in setOf(QUEUED, FORWARDING, AWAITING_DESTINATION) ||
                dueAt < 0L || leaseUntil < 0L ||
                (previousHopPeerId.isNotEmpty() && NeaRerIdentity.normalize(previousHopPeerId) != previousHopPeerId) ||
                (expectedNextHopPeerId.isNotEmpty() && NeaRerIdentity.normalize(expectedNextHopPeerId) != expectedNextHopPeerId) ||
                proofChain.length > 8 * 1024) return null
            if (proofChain.isNotEmpty()) {
                val decodedProof = android.util.Base64.decode(proofChain, android.util.Base64.NO_WRAP)
                MeshForwardingProofChain.decode(decodedProof)
            }
            result[id] = Entry(encrypted, attempts, state, dueAt, leaseUntil, previousHopPeerId, preparedForNextHop, expectedNextHopPeerId, proofChain)
        }
        if (result.values.sumOf { it.encrypted.length } > MAX_TOTAL_CHARS) return null
        result
    } catch (_: Exception) { null }

    private fun write(entries: Map<String, Entry>): Boolean {
        if (entries.size > MAX_ITEMS || entries.values.sumOf { it.encrypted.length } > MAX_TOTAL_CHARS) return false
        val json = JSONObject()
        entries.forEach { (id, e) -> json.put(id, JSONObject()
            .put("encrypted", e.encrypted).put("attempts", e.attempts)
            .put("state", e.state).put("dueAt", e.dueAt).put("leaseUntil", e.leaseUntil)
            .put("previousHopPeerId", e.previousHopPeerId)
            .put("preparedForNextHop", e.preparedForNextHop)
            .put("expectedNextHopPeerId", e.expectedNextHopPeerId)
            .put("proofChain", e.proofChain)) }
        val clear = json.toString().toByteArray(Charsets.UTF_8)
        if (clear.size > MAX_TOTAL_CHARS + MAX_METADATA_CHARS) return false
        return try {
            val encrypted = LocalDataCipher.encrypt(app, clear)
            prefs.edit().putString(KEY, encrypted).commit()
        } catch (_: Exception) { false }
    }

    private fun backoff(attempt: Int): Long = minOf(3_600_000L, 5_000L * (1L shl (attempt - 1).coerceIn(0, 10)))

    companion object {
        private const val KEY = "queue_v1"
        private const val MAX_ITEMS = 64
        private const val MAX_PER_ORIGIN_ITEMS = 16
        private const val MAX_PER_ORIGIN_CHARS = 512 * 1024
        private const val MAX_PACKET_BYTES = MeshRelayPacketV1.MAX_NEARBY_BYTES
        private const val MAX_TOTAL_CHARS = 2 * 1024 * 1024
        private const val MAX_METADATA_CHARS = 64 * 1024
        private const val MAX_STORED_CHARS = 3 * 1024 * 1024
        private const val MAX_RETRY_LEVEL = 16
        private const val LEASE_MS = 30_000L
        private const val QUEUED = "queued"
        private const val FORWARDING = "forwarding"
        private const val AWAITING_DESTINATION = "awaiting_destination"
    }
}
