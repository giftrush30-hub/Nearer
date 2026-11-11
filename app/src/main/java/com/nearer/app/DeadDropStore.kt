package com.nearer.app

import android.content.Context
import org.json.JSONObject

/**
 * Encrypted-at-rest queue for opaque recipient-encrypted packets.
 * Caller must verify the origin signature against a pinned identity before enqueue.
 */
internal class DeadDropStore(context: Context) {
    data class Pending(val packetId: String, val bytes: ByteArray, val attempts: Int)

    private data class Entry(
        val encrypted: String,
        val attempts: Int,
        val state: String,
        val dueAt: Long,
        val leaseUntil: Long
    )

    private val app = context.applicationContext
    private val prefs = app.getSharedPreferences("nearer_dead_drop_v1", Context.MODE_PRIVATE)

    @Synchronized
    fun enqueueVerified(packet: MeshRelayPacketV1, trustedOriginKey: ByteArray, now: Long): Boolean {
        if (!packet.verify(trustedOriginKey, now)) return false
        val entries = read() ?: return false
        if (entries.containsKey(packet.packetId)) return true
        if (entries.size >= MAX_ITEMS) return false
        val bytes = packet.encode()
        if (bytes.size > MAX_PACKET_BYTES) return false
        val encrypted = try { LocalDataCipher.encrypt(app, bytes) } catch (_: Exception) { return false }
        if (entries.values.sumOf { it.encrypted.length } + encrypted.length > MAX_TOTAL_CHARS) return false
        entries[packet.packetId] = Entry(encrypted, 0, QUEUED, now, 0L)
        return write(entries)
    }

    /** Claims due items; an expired forwarding lease is recovered after process death. */
    @Synchronized
    fun claimDue(now: Long, limit: Int = 8): List<Pending> {
        if (limit !in 1..MAX_ITEMS) return emptyList()
        val entries = read() ?: return emptyList()
        val result = mutableListOf<Pending>()
        entries.toMap().forEach { (id, entry) ->
            if (result.size >= limit || entry.attempts >= MAX_ATTEMPTS) return@forEach
            val due = entry.dueAt <= now && (
                entry.state == QUEUED || entry.state == AWAITING_DESTINATION ||
                (entry.state == FORWARDING && entry.leaseUntil <= now)
            )
            if (!due) return@forEach
            val clear = LocalDataCipher.decrypt(app, entry.encrypted) ?: return@forEach
            val packet = try { MeshRelayPacketV1.decode(clear) } catch (_: Exception) { return@forEach }
            if (!packet.validate(now)) {
                entries.remove(id)
                return@forEach
            }
            val attempts = entry.attempts + 1
            entries[id] = entry.copy(
                attempts = attempts, state = FORWARDING,
                dueAt = now + backoff(attempts), leaseUntil = now + LEASE_MS
            )
            result += Pending(id, clear, attempts)
        }
        write(entries)
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
        if (old.state != FORWARDING) return false
        entries[acknowledgement.packetId] = old.copy(
            state = AWAITING_DESTINATION,
            dueAt = now + backoff(old.attempts),
            leaseUntil = 0L
        )
        return write(entries)
    }

    /** Only a destination-signed receipt can remove a stored packet. */
    @Synchronized
    fun markDelivered(receipt: MeshDeliveryReceipt, trustedDestinationKey: ByteArray, now: Long): Boolean {
        if (!receipt.verify(trustedDestinationKey, now)) return false
        val entries = read() ?: return false
        val old = entries[receipt.packetId] ?: return false
        val clear = LocalDataCipher.decrypt(app, old.encrypted) ?: return false
        val packet = try { MeshRelayPacketV1.decode(clear) } catch (_: Exception) { return false }
        if (packet.originId != receipt.originId || packet.destinationId != receipt.destinationId) return false
        entries.remove(receipt.packetId)
        return write(entries)
    }

    @Synchronized
    fun purgeExpired(now: Long): Int {
        val entries = read() ?: return 0
        val before = entries.size
        entries.toMap().forEach { (id, entry) ->
            val clear = LocalDataCipher.decrypt(app, entry.encrypted) ?: return@forEach
            val packet = try { MeshRelayPacketV1.decode(clear) } catch (_: Exception) { return@forEach }
            if (packet.expiresAtMs <= now) entries.remove(id)
        }
        val removed = before - entries.size
        if (removed > 0) write(entries)
        return removed
    }

    @Synchronized fun size(): Int = read()?.size ?: 0

    private fun read(): LinkedHashMap<String, Entry>? = try {
        val raw = prefs.getString(KEY, null) ?: return LinkedHashMap()
        val json = JSONObject(raw)
        val result = LinkedHashMap<String, Entry>()
        val keys = json.keys()
        while (keys.hasNext()) {
            val id = keys.next()
            val item = json.getJSONObject(id)
            result[id] = Entry(item.getString("encrypted"), item.getInt("attempts"),
                item.getString("state"), item.getLong("dueAt"), item.optLong("leaseUntil", 0L))
        }
        result
    } catch (_: Exception) { null }

    private fun write(entries: Map<String, Entry>): Boolean {
        val json = JSONObject()
        entries.forEach { (id, e) -> json.put(id, JSONObject()
            .put("encrypted", e.encrypted).put("attempts", e.attempts)
            .put("state", e.state).put("dueAt", e.dueAt).put("leaseUntil", e.leaseUntil)) }
        return prefs.edit().putString(KEY, json.toString()).commit()
    }

    private fun backoff(attempt: Int): Long = minOf(3_600_000L, 5_000L * (1L shl (attempt - 1).coerceIn(0, 10)))

    companion object {
        private const val KEY = "queue_v1"
        private const val MAX_ITEMS = 64
        private const val MAX_PACKET_BYTES = 40 * 1024
        private const val MAX_TOTAL_CHARS = 2 * 1024 * 1024
        private const val MAX_ATTEMPTS = 8
        private const val LEASE_MS = 30_000L
        private const val QUEUED = "queued"
        private const val FORWARDING = "forwarding"
        private const val AWAITING_DESTINATION = "awaiting_destination"
    }
}
