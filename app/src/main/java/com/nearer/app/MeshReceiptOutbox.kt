package com.nearer.app

import android.content.Context
import org.json.JSONObject

/**
 * Durable encrypted-at-rest queue for destination-signed receipts in transit.
 * A receipt is removed only after the next authenticated hop proves durable acceptance.
 */
internal class MeshReceiptOutbox(context: Context) {
    data class Pending(val receipt: MeshDeliveryReceipt, val previousHopPeerId: String, val expectedNextHopPeerId: String)

    private val app = context.applicationContext
    private val prefs = app.getSharedPreferences("nearer_mesh_receipt_outbox_v1", Context.MODE_PRIVATE)

    @Synchronized
    fun enqueue(receipt: MeshDeliveryReceipt, previousHopPeerId: String): Boolean {
        if (previousHopPeerId.isNotEmpty() &&
            NeaRerIdentity.normalize(previousHopPeerId) != previousHopPeerId) return false
        val entries = read() ?: return false
        val existing = entries[receipt.packetId]
        if (existing != null) {
            val clear = LocalDataCipher.decrypt(app, existing.encrypted) ?: return false
            val sameReceipt = runCatching {
                val old = MeshDeliveryReceipt.decode(clear)
                old.encode().contentEquals(receipt.encode())
            }.getOrDefault(false)
            if (!sameReceipt) return false
            // A duplicate may arrive over a different authenticated neighbor after
            // route recovery. Remember the newest ingress so the receipt is not
            // forwarded straight back along a stale reverse path.
            if (previousHopPeerId.isNotEmpty() && existing.previousHopPeerId != previousHopPeerId) {
                entries[receipt.packetId] = existing.copy(previousHopPeerId = previousHopPeerId, expectedNextHopPeerId = "")
                return write(entries)
            }
            return true
        }
        if (entries.size >= MAX_ITEMS) return false
        val encoded = try { receipt.encode() } catch (_: Exception) { return false }
        val encrypted = try { LocalDataCipher.encrypt(app, encoded) } catch (_: Exception) { return false }
        if (entries.values.sumOf { it.encrypted.length } + encrypted.length > MAX_TOTAL_CHARS) return false
        entries[receipt.packetId] = Entry(encrypted, previousHopPeerId)
        return write(entries)
    }

    @Synchronized
    fun pending(limit: Int = MAX_ITEMS): List<Pending> {
        if (limit !in 1..MAX_ITEMS) return emptyList()
        val entries = read() ?: return emptyList()
        return entries.entries.take(limit).mapNotNull { (_, entry) ->
            val clear = LocalDataCipher.decrypt(app, entry.encrypted) ?: return@mapNotNull null
            val receipt = runCatching { MeshDeliveryReceipt.decode(clear) }.getOrNull() ?: return@mapNotNull null
            Pending(receipt, entry.previousHopPeerId, entry.expectedNextHopPeerId)
        }
    }

    /** Persist the selected outgoing hop before a receipt is put on the wire. */
    @Synchronized
    fun setExpectedNextHop(packetId: String, nextHopPeerId: String): Boolean {
        if (!packetId.matches(Regex("[A-Za-z0-9._:-]{1,80}")) ||
            NeaRerIdentity.normalize(nextHopPeerId) != nextHopPeerId) return false
        val entries = read() ?: return false
        val old = entries[packetId] ?: return false
        if (old.expectedNextHopPeerId == nextHopPeerId) return true
        entries[packetId] = old.copy(expectedNextHopPeerId = nextHopPeerId)
        return write(entries)
    }

    /** Only the exact next hop selected and persisted before send may retire a receipt. */
    @Synchronized
    fun acknowledge(packetId: String, acceptingPeerId: String): Boolean {
        if (!packetId.matches(Regex("[A-Za-z0-9._:-]{1,80}")) ||
            NeaRerIdentity.normalize(acceptingPeerId) != acceptingPeerId) return false
        val entries = read() ?: return false
        val old = entries[packetId] ?: return false
        if (old.expectedNextHopPeerId != acceptingPeerId) return false
        entries.remove(packetId)
        return write(entries)
    }

    @Synchronized
    fun remove(packetId: String): Boolean {
        if (!packetId.matches(Regex("[A-Za-z0-9._:-]{1,80}"))) return false
        val entries = read() ?: return false
        if (entries.remove(packetId) == null) return false
        return write(entries)
    }

    @Synchronized
    fun size(): Int = read()?.size ?: 0

    private data class Entry(val encrypted: String, val previousHopPeerId: String, val expectedNextHopPeerId: String = "")

    private fun read(): LinkedHashMap<String, Entry>? = try {
        val raw = prefs.getString(KEY, null) ?: return LinkedHashMap()
        val json = JSONObject(raw)
        if (json.length() > MAX_ITEMS) return null
        val result = LinkedHashMap<String, Entry>()
        val keys = json.keys()
        while (keys.hasNext()) {
            val id = keys.next()
            if (!id.matches(Regex("[A-Za-z0-9._:-]{1,80}"))) return null
            val item = json.getJSONObject(id)
            val encrypted = item.getString("encrypted")
            val previousHop = item.optString("previousHopPeerId", "")
            val expectedNextHop = item.optString("expectedNextHopPeerId", "")
            if (encrypted.isBlank() || encrypted.length > MAX_TOTAL_CHARS ||
                (previousHop.isNotEmpty() && NeaRerIdentity.normalize(previousHop) != previousHop) ||
                (expectedNextHop.isNotEmpty() && NeaRerIdentity.normalize(expectedNextHop) != expectedNextHop)) return null
            result[id] = Entry(encrypted, previousHop, expectedNextHop)
        }
        if (result.values.sumOf { it.encrypted.length } > MAX_TOTAL_CHARS) return null
        result
    } catch (_: Exception) { null }

    private fun write(entries: Map<String, Entry>): Boolean {
        if (entries.size > MAX_ITEMS || entries.values.sumOf { it.encrypted.length } > MAX_TOTAL_CHARS) return false
        val json = JSONObject()
        entries.forEach { (id, entry) ->
            json.put(id, JSONObject()
                .put("encrypted", entry.encrypted)
                .put("previousHopPeerId", entry.previousHopPeerId)
                .put("expectedNextHopPeerId", entry.expectedNextHopPeerId))
        }
        return prefs.edit().putString(KEY, json.toString()).commit()
    }

    private companion object {
        const val KEY = "receipts_v1"
        const val MAX_ITEMS = 64
        const val MAX_TOTAL_CHARS = 256 * 1024
    }
}
