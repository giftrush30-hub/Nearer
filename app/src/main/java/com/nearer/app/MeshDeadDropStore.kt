package com.nearer.app

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

/**
 * Durable store-and-forward queue. Only opaque, already-encrypted packet strings
 * belong here. This store does not choose routes or transmit packets by itself.
 */
internal class MeshDeadDropStore(context: Context) :
    SQLiteOpenHelper(context.applicationContext, "nearer_mesh_dead_drop.db", null, 1) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE dead_drop (
                message_id TEXT PRIMARY KEY NOT NULL,
                destination_id TEXT NOT NULL,
                packet TEXT NOT NULL,
                created_at INTEGER NOT NULL,
                expires_at INTEGER NOT NULL,
                attempts INTEGER NOT NULL DEFAULT 0,
                last_attempt_at INTEGER NOT NULL DEFAULT 0
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX dead_drop_destination_expiry ON dead_drop(destination_id, expires_at)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // Versioned migrations will be added before the schema changes.
    }

    @Synchronized
    fun putIfAbsent(messageId: String, destinationId: String, encryptedPacket: String, now: Long, ttlMillis: Long): Boolean {
        if (messageId.isBlank() || destinationId.isBlank() || encryptedPacket.isBlank()) return false
        if (messageId.length > 128 || destinationId.length > 64 || encryptedPacket.length > 96 * 1024) return false
        if (now <= 0L || ttlMillis <= 0L || now > Long.MAX_VALUE - ttlMillis) return false
        val values = ContentValues().apply {
            put("message_id", messageId)
            put("destination_id", destinationId)
            put("packet", encryptedPacket)
            put("created_at", now)
            put("expires_at", now + ttlMillis)
        }
        return writableDatabase.insertWithOnConflict("dead_drop", null, values, SQLiteDatabase.CONFLICT_IGNORE) != -1L
    }

    @Synchronized
    fun pendingFor(destinationId: String, now: Long, limit: Int = 50): List<DeadDropPacket> {
        if (destinationId.isBlank() || limit !in 1..200) return emptyList()
        pruneExpired(now)
        val result = ArrayList<DeadDropPacket>()
        readableDatabase.query(
            "dead_drop",
            arrayOf("message_id", "destination_id", "packet", "created_at", "expires_at", "attempts"),
            "destination_id = ? AND expires_at > ?",
            arrayOf(destinationId, now.toString()),
            null, null, "created_at ASC", limit.toString()
        ).use { cursor ->
            while (cursor.moveToNext()) {
                result += DeadDropPacket(
                    cursor.getString(0), cursor.getString(1), cursor.getString(2),
                    cursor.getLong(3), cursor.getLong(4), cursor.getInt(5)
                )
            }
        }
        return result
    }

    @Synchronized
    fun recordAttempt(messageId: String, now: Long): Boolean {
        val values = ContentValues().apply {
            put("attempts", "attempts + 1")
            put("last_attempt_at", now)
        }
        // Use SQL arithmetic rather than storing a literal expression in ContentValues.
        return writableDatabase.execSQL(
            "UPDATE dead_drop SET attempts = attempts + 1, last_attempt_at = ? WHERE message_id = ?",
            arrayOf(now, messageId)
        ).let { true }
    }

    @Synchronized
    fun acknowledgeDelivered(messageId: String): Boolean =
        writableDatabase.delete("dead_drop", "message_id = ?", arrayOf(messageId)) > 0

    @Synchronized
    fun pruneExpired(now: Long): Int =
        writableDatabase.delete("dead_drop", "expires_at <= ?", arrayOf(now.toString()))

    @Synchronized
    fun count(now: Long): Int {
        pruneExpired(now)
        readableDatabase.rawQuery("SELECT COUNT(*) FROM dead_drop", null).use { cursor ->
            return if (cursor.moveToFirst()) cursor.getInt(0) else 0
        }
    }
}

internal data class DeadDropPacket(
    val messageId: String,
    val destinationId: String,
    val encryptedPacket: String,
    val createdAtMillis: Long,
    val expiresAtMillis: Long,
    val attempts: Int
)
