package com.nearer.app

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

/**
 * Durable, unbounded chat archive. The UI pages recent records into memory;
 * SQLite remains the source of truth so older conversations are not discarded.
 */
internal class NeaRerChatHistoryDatabase(context: Context) :
    SQLiteOpenHelper(context.applicationContext, "nearer_chat_history.db", null, 1) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE messages (
                id TEXT PRIMARY KEY NOT NULL,
                body TEXT NOT NULL,
                outgoing INTEGER NOT NULL,
                delivery TEXT NOT NULL,
                timestamp INTEGER NOT NULL,
                conversation_id TEXT NOT NULL,
                conversation_name TEXT NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX messages_by_time ON messages(timestamp DESC, id DESC)")
        db.execSQL("CREATE INDEX messages_by_conversation ON messages(conversation_id, timestamp DESC)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // Future schema migrations must preserve the existing message table.
    }

    fun upsert(messages: List<ChatMessage>): Boolean {
        if (messages.isEmpty()) return true
        val db = writableDatabase
        return try {
            db.beginTransaction()
            messages.forEach { message ->
                val values = ContentValues().apply {
                    put("id", message.id)
                    put("body", message.body)
                    put("outgoing", if (message.outgoing) 1 else 0)
                    put("delivery", message.delivery)
                    put("timestamp", message.timestamp)
                    put("conversation_id", message.conversationId)
                    put("conversation_name", message.conversationName)
                }
                db.insertWithOnConflict("messages", null, values, SQLiteDatabase.CONFLICT_REPLACE)
            }
            db.setTransactionSuccessful()
            true
        } catch (_: Exception) {
            false
        } finally {
            if (db.inTransaction()) db.endTransaction()
        }
    }

    fun loadLatest(limit: Int = 500): List<ChatMessage> = query(
        "SELECT id, body, outgoing, delivery, timestamp, conversation_id, conversation_name FROM messages ORDER BY timestamp DESC, id DESC LIMIT ?",
        arrayOf(limit.coerceIn(1, 5000).toString())
    ).asReversed()

    fun loadBefore(timestampExclusive: Long, limit: Int = 500): List<ChatMessage> = query(
        "SELECT id, body, outgoing, delivery, timestamp, conversation_id, conversation_name FROM messages WHERE timestamp < ? ORDER BY timestamp DESC, id DESC LIMIT ?",
        arrayOf(timestampExclusive.toString(), limit.coerceIn(1, 5000).toString())
    ).asReversed()

    fun hasBefore(timestampExclusive: Long): Boolean {
        readableDatabase.rawQuery(
            "SELECT 1 FROM messages WHERE timestamp < ? LIMIT 1",
            arrayOf(timestampExclusive.toString())
        ).use { return it.moveToFirst() }
    }

    fun count(): Long {
        readableDatabase.rawQuery("SELECT COUNT(*) FROM messages", null).use {
            return if (it.moveToFirst()) it.getLong(0) else 0L
        }
    }

    private fun query(sql: String, args: Array<String>): List<ChatMessage> {
        val result = mutableListOf<ChatMessage>()
        readableDatabase.rawQuery(sql, args).use { cursor ->
            while (cursor.moveToNext()) {
                result.add(
                    ChatMessage(
                        id = cursor.getString(0),
                        body = cursor.getString(1),
                        outgoing = cursor.getInt(2) != 0,
                        delivery = cursor.getString(3),
                        timestamp = cursor.getLong(4),
                        conversationId = cursor.getString(5),
                        conversationName = cursor.getString(6)
                    )
                )
            }
        }
        return result
    }
}
