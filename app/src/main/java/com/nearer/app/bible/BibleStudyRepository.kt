package com.nearer.app.bible

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase

/** Transaction adapter and private study-data repository for NeaRer Word. */
class BibleStudyRepository(context:Context) {
    private val helper=BibleLocalDatabase(context)

    fun installTarget():BiblePackInstallTarget = object:BiblePackInstallTarget {
        private var db:SQLiteDatabase?=null
        override fun begin() {
            check(db==null) { "Import transaction already active." }
            db=helper.writableDatabase.also { it.beginTransaction() }
        }
        override fun putVerse(verse:BibleVerse) {
            val active=checkNotNull(db) { "Import transaction has not started." }
            val bookExists=active.query("books",arrayOf("id"),"id=? AND translation_id=?",
                arrayOf(verse.bookId,verse.translationId),null,null,null,"1").use { it.moveToFirst() }
            require(bookExists) { "Pack references unknown book: "+verse.bookId }
            active.insertWithOnConflict("verses",null,ContentValues().apply {
                put("translation_id",verse.translationId); put("book_id",verse.bookId)
                put("chapter",verse.chapter); put("verse",verse.verse); put("text",verse.text)
            },SQLiteDatabase.CONFLICT_REPLACE)
            active.delete("verse_search","translation_id=? AND book_id=? AND chapter=? AND verse=?",
                arrayOf(verse.translationId,verse.bookId,verse.chapter.toString(),verse.verse.toString()))
            active.insert("verse_search",null,ContentValues().apply {
                put("translation_id",verse.translationId); put("book_id",verse.bookId)
                put("chapter",verse.chapter); put("verse",verse.verse); put("text",verse.text)
            })
        }
        override fun commit() {
            val active=checkNotNull(db)
            active.setTransactionSuccessful()
            active.endTransaction()
            db=null
        }
        override fun rollback() {
            db?.endTransaction()
            db=null
        }
    }

    fun saveBookmark(bookmark:BibleBookmark) {
        helper.writableDatabase.insertWithOnConflict("bookmarks",null,ContentValues().apply {
            put("id",bookmark.id); put("translation_id",bookmark.translationId)
            put("book_id",bookmark.bookId); put("chapter",bookmark.chapter)
            put("verse",bookmark.verse); put("created_at",bookmark.createdAt)
        },SQLiteDatabase.CONFLICT_REPLACE)
    }
    fun saveNote(note:BibleStudyNote) {
        helper.writableDatabase.insertWithOnConflict("study_notes",null,ContentValues().apply {
            put("id",note.id); put("translation_id",note.translationId); put("book_id",note.bookId)
            put("chapter",note.chapter); put("verse",note.verse); put("title",note.title)
            put("body",note.body); put("color_argb",note.colorArgb); put("updated_at",note.updatedAt)
        },SQLiteDatabase.CONFLICT_REPLACE)
    }
    fun deleteNote(noteId:String) {
        helper.writableDatabase.delete("study_notes","id=?",arrayOf(noteId))
    }
    fun listNotes():List<BibleStudyNote> {
        val result=mutableListOf<BibleStudyNote>()
        helper.readableDatabase.query("study_notes",null,null,null,null,null,"updated_at DESC").use { c ->
            while(c.moveToNext()) {
                fun nullableString(name:String):String? {
                    val index=c.getColumnIndexOrThrow(name)
                    return if(c.isNull(index)) null else c.getString(index)
                }
                fun nullableInt(name:String):Int? {
                    val index=c.getColumnIndexOrThrow(name)
                    return if(c.isNull(index)) null else c.getInt(index)
                }
                result += BibleStudyNote(
                    id=c.getString(c.getColumnIndexOrThrow("id")),
                    translationId=nullableString("translation_id"),bookId=nullableString("book_id"),
                    chapter=nullableInt("chapter"),verse=nullableInt("verse"),
                    title=c.getString(c.getColumnIndexOrThrow("title")),
                    body=c.getString(c.getColumnIndexOrThrow("body")),
                    colorArgb=c.getLong(c.getColumnIndexOrThrow("color_argb")),
                    updatedAt=c.getLong(c.getColumnIndexOrThrow("updated_at"))
                )
            }
        }
        return result
    }
}
