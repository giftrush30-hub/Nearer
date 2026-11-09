package com.nearer.app

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.io.IOException
import org.json.JSONObject

/**
 * Durable, unbounded chat archive. The UI pages recent records into memory;
 * SQLite remains the source of truth so older conversations are not discarded.
 */
internal class NeaRerChatHistoryDatabase(context: Context) :
    SQLiteOpenHelper(context.applicationContext, "nearer_chat_history.db", null, 1) {

    companion object {
        private const val MAX_BACKUP_MESSAGES = 200_000
        private const val MAX_BACKUP_RECORD_CHARS = 2 * 1024 * 1024
        private const val MAX_BACKUP_BODY_CHARS = 1 * 1024 * 1024
        private const val MAX_BACKUP_ARCHIVE_BYTES = 64L * 1024L * 1024L
    }

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
                val savedRow = db.insertWithOnConflict("messages", null, values, SQLiteDatabase.CONFLICT_REPLACE)
                if (savedRow == -1L) throw IllegalStateException("Could not save a chat message")
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

    fun loadBefore(oldest: ChatMessage, limit: Int = 500): List<ChatMessage> = query(
        "SELECT id, body, outgoing, delivery, timestamp, conversation_id, conversation_name FROM messages WHERE timestamp < ? OR (timestamp = ? AND id < ?) ORDER BY timestamp DESC, id DESC LIMIT ?",
        arrayOf(oldest.timestamp.toString(), oldest.timestamp.toString(), oldest.id, limit.coerceIn(1, 5000).toString())
    ).asReversed()

    fun hasBefore(oldest: ChatMessage): Boolean {
        readableDatabase.rawQuery(
            "SELECT 1 FROM messages WHERE timestamp < ? OR (timestamp = ? AND id < ?) LIMIT 1",
            arrayOf(oldest.timestamp.toString(), oldest.timestamp.toString(), oldest.id)
        ).use { return it.moveToFirst() }
    }

    fun count(): Long {
        readableDatabase.rawQuery("SELECT COUNT(*) FROM messages", null).use {
            return if (it.moveToFirst()) it.getLong(0) else 0L
        }
    }

    /**
     * Writes every archived message as one UTF-8 JSON object per line.
     * A line-oriented format avoids loading the complete chat archive into memory.
     */
    @Throws(IOException::class)
    fun exportPortableArchive(destination: File): Long {
        val parent = destination.absoluteFile.parentFile ?: throw IOException("Chat export has no parent folder")
        if (!parent.exists() && !parent.mkdirs()) throw IOException("Could not create chat export folder")
        if (destination.exists()) throw IOException("Chat export destination already exists")
        val temporary = File.createTempFile(".nearer-chat-export-", ".tmp", parent)
        var exported = 0L
        try {
            BufferedWriter(OutputStreamWriter(FileOutputStream(temporary), Charsets.UTF_8)).use { writer ->
                readableDatabase.rawQuery(
                    "SELECT id, body, outgoing, delivery, timestamp, conversation_id, conversation_name FROM messages ORDER BY timestamp ASC, id ASC",
                    null
                ).use { cursor ->
                    while (cursor.moveToNext()) {
                        val record = JSONObject()
                            .put("id", cursor.getString(0))
                            .put("body", cursor.getString(1))
                            .put("outgoing", cursor.getInt(2) != 0)
                            .put("delivery", cursor.getString(3))
                            .put("timestamp", cursor.getLong(4))
                            .put("conversationId", cursor.getString(5))
                            .put("conversationName", cursor.getString(6))
                        writer.write(record.toString())
                        writer.newLine()
                        exported++
                    }
                }
                writer.flush()
            }
            if (temporary.length() > MAX_BACKUP_ARCHIVE_BYTES) throw IOException("Chat history is too large for the current backup format")
            if (!temporary.renameTo(destination)) throw IOException("Could not publish chat archive")
            return exported
        } catch (failure: Exception) {
            temporary.delete()
            if (failure is IOException) throw failure
            throw IOException("Could not export chat history", failure)
        }
    }

    /**
     * Validates the complete portable archive before opening a database transaction.
     * Existing IDs are updated and new IDs are added; unrelated local messages remain.
     */
    @Throws(IOException::class)
    fun mergePortableArchive(source: File): Long {
        if (!source.isFile || !source.canRead()) throw IOException("Chat archive is not readable")
        if (source.length() > MAX_BACKUP_ARCHIVE_BYTES) throw IOException("Chat archive exceeds the supported size")
        val records = mutableListOf<ChatMessage>()
        val seenIds = HashSet<String>()
        try {
            BufferedReader(InputStreamReader(FileInputStream(source), Charsets.UTF_8)).use { reader ->
                var lineNumber = 0
                while (true) {
                    val line = reader.readLine() ?: break
                    lineNumber++
                    if (line.isBlank() || line.length > MAX_BACKUP_RECORD_CHARS) {
                        throw IOException("Chat archive record $lineNumber is invalid or too large")
                    }
                    val json = try { JSONObject(line) } catch (failure: Exception) {
                        throw IOException("Chat archive record $lineNumber is not valid JSON", failure)
                    }
                    val id = json.optString("id")
                    val body = json.optString("body")
                    val delivery = json.optString("delivery")
                    val conversationId = json.optString("conversationId")
                    val conversationName = json.optString("conversationName")
                    if (id.isBlank() || id.length > 160 || !seenIds.add(id) ||
                        body.length > MAX_BACKUP_BODY_CHARS || delivery.length > 40 ||
                        conversationId.length > 200 || conversationName.length > 160 ||
                        !json.has("timestamp") || !json.has("outgoing")
                    ) throw IOException("Chat archive record $lineNumber has invalid fields")
                    records.add(
                        ChatMessage(
                            id = id,
                            body = body,
                            outgoing = json.optBoolean("outgoing"),
                            delivery = delivery,
                            timestamp = json.optLong("timestamp"),
                            conversationId = conversationId,
                            conversationName = conversationName
                        )
                    )
                    if (records.size > MAX_BACKUP_MESSAGES) throw IOException("Chat archive contains too many messages")
                }
            }
        } catch (failure: IOException) {
            throw failure
        } catch (failure: Exception) {
            throw IOException("Could not read chat archive", failure)
        }

        if (records.isEmpty()) return 0L
        if (!upsert(records)) throw IOException("Could not import chat archive")
        return records.size.toLong()
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
