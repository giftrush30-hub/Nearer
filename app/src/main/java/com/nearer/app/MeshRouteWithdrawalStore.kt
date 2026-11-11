package com.nearer.app

import android.content.Context
import org.json.JSONObject

/** Encrypted, bounded persistence for signed route-withdrawal replay protection. */
internal class MeshRouteWithdrawalStore(context: Context) {
    private val app = context.applicationContext
    private val prefs = app.getSharedPreferences("nearer_mesh_route_withdrawals_v1", Context.MODE_PRIVATE)

    @Synchronized
    fun record(tombstone: MeshRouteTable.WithdrawalTombstone): Boolean {
        if (!valid(tombstone)) return false
        if (isPermanentlyDisabled()) return false
        val entries = read() ?: return disableRouting()
        val key = key(tombstone.destinationId, tombstone.advertiserId)
        val old = entries[key] ?: -1L
        // Older/equal verified withdrawals are harmless replays: keep the
        // higher watermark and report a healthy store rather than treating the
        // replay itself as a storage failure.
        if (tombstone.sequence <= old) return true
        // Never evict a live replay-protection watermark to make room. If the
        // bounded store is full, fail closed and pause multi-hop instead.
        if (old < 0L && entries.size >= MAX_TOMBSTONES) return disableRouting()
        entries[key] = tombstone.sequence
        return if (write(entries)) true else disableRouting()
    }

    @Synchronized
    fun saveAll(tombstones: List<MeshRouteTable.WithdrawalTombstone>): Boolean {
        if (isPermanentlyDisabled()) return false
        val entries = read() ?: return disableRouting()
        tombstones.forEach { tombstone ->
            if (!valid(tombstone)) return false
            val key = key(tombstone.destinationId, tombstone.advertiserId)
            entries[key] = maxOf(entries[key] ?: -1L, tombstone.sequence)
        }
        // Overflow is a safety failure, not an eviction policy: deleting an
        // unexpired watermark could permit a stale signed route to reappear.
        if (entries.size > MAX_TOMBSTONES) return disableRouting()
        return if (write(entries)) true else disableRouting()
    }

    @Synchronized
    fun restore(table: MeshRouteTable): Int {
        if (isPermanentlyDisabled()) return -1
        val entries = read() ?: return disableRoutingInt()
        var restored = 0
        val validEntries = LinkedHashMap<String, Long>()
        entries.forEach { (compoundKey, sequence) ->
            val split = compoundKey.split("|")
            if (split.size != 2) return disableRoutingInt()
            val tombstone = MeshRouteTable.WithdrawalTombstone(split[0], split[1], sequence)
            if (!valid(tombstone)) return disableRoutingInt()
            validEntries[compoundKey] = sequence
            if (!table.rememberWithdrawal(tombstone.destinationId, tombstone.advertiserId, sequence)) return disableRoutingInt()
            restored += 1
        }
        if (validEntries.size != entries.size && !write(validEntries)) return disableRoutingInt()
        return restored
    }

    /** Permanently fail closed after replay-protection data cannot be trusted or saved. */
    @Synchronized
    fun disableRouting(): Boolean = try {
        prefs.edit().putBoolean(DISABLED_KEY, true).commit()
    } catch (_: Exception) { false }

    private fun disableRoutingInt(): Int {
        disableRouting()
        return -1
    }

    private fun isPermanentlyDisabled(): Boolean =
        try { prefs.getBoolean(DISABLED_KEY, false) } catch (_: Exception) { true }

    private fun valid(value: MeshRouteTable.WithdrawalTombstone): Boolean =
        NeaRerIdentity.normalize(value.destinationId) == value.destinationId &&
            NeaRerIdentity.normalize(value.advertiserId) == value.advertiserId &&
            value.destinationId != value.advertiserId && value.sequence >= 0L

    private fun key(destinationId: String, advertiserId: String) = "$destinationId|$advertiserId"

    private fun read(): LinkedHashMap<String, Long>? = try {
        val encrypted = prefs.getString(KEY, null) ?: return LinkedHashMap()
        val clear = LocalDataCipher.decrypt(app, encrypted) ?: return null
        val json = JSONObject(clear.toString(Charsets.UTF_8))
        if (json.length() > MAX_TOMBSTONES) return null
        val entries = LinkedHashMap<String, Long>()
        val keys = json.keys()
        while (keys.hasNext()) {
            val compoundKey = keys.next()
            val split = compoundKey.split("|")
            val sequence = json.getLong(compoundKey)
            if (split.size != 2 || sequence < 0L) return null
            entries[compoundKey] = sequence
        }
        entries
    } catch (_: Exception) { null }

    private fun write(entries: Map<String, Long>): Boolean {
        if (entries.size > MAX_TOMBSTONES || entries.values.any { it < 0L }) return false
        val json = JSONObject()
        entries.forEach { (compoundKey, sequence) -> json.put(compoundKey, sequence) }
        return try {
            val encrypted = LocalDataCipher.encrypt(app, json.toString().toByteArray(Charsets.UTF_8))
            prefs.edit().putString(KEY, encrypted).commit()
        } catch (_: Exception) { false }
    }

    private companion object {
        const val KEY = "withdrawals_encrypted_v1"
        const val DISABLED_KEY = "routing_permanently_disabled_v1"
        const val MAX_TOMBSTONES = 512
    }
}
