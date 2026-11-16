package com.nearer.app.bible

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

/** Offline store. Scripture and lexicon content enter only through validated, licensed packs. */
class BibleLocalDatabase(context: Context) : SQLiteOpenHelper(
    context.applicationContext, "nearer_word.db", null, 2
) {
    override fun onConfigure(db: SQLiteDatabase) {
        super.onConfigure(db)
        db.setForeignKeyConstraintsEnabled(true)
    }
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("""CREATE TABLE translations(
            id TEXT PRIMARY KEY NOT NULL, name TEXT NOT NULL, language_tag TEXT NOT NULL,
            language_name TEXT NOT NULL, abbreviation TEXT NOT NULL, canon_id TEXT NOT NULL,
            source_id TEXT NOT NULL, license_id TEXT NOT NULL, copyright_notice TEXT, installed_at INTEGER NOT NULL)""")
        db.execSQL("""CREATE TABLE books(
            id TEXT NOT NULL, translation_id TEXT NOT NULL REFERENCES translations(id) ON DELETE CASCADE,
            canonical_number INTEGER NOT NULL, name TEXT NOT NULL, testament TEXT NOT NULL,
            chapter_count INTEGER NOT NULL, PRIMARY KEY(id,translation_id))""")
        db.execSQL("""CREATE TABLE verses(
            translation_id TEXT NOT NULL, book_id TEXT NOT NULL, chapter INTEGER NOT NULL,
            verse INTEGER NOT NULL, text TEXT NOT NULL,
            PRIMARY KEY(translation_id,book_id,chapter,verse),
            FOREIGN KEY(book_id,translation_id) REFERENCES books(id,translation_id) ON DELETE CASCADE)""")
        db.execSQL("CREATE VIRTUAL TABLE verse_search USING fts4(translation_id UNINDEXED, book_id UNINDEXED, chapter UNINDEXED, verse UNINDEXED, text)")
        db.execSQL("""CREATE TABLE lexical_entries(
            id TEXT PRIMARY KEY NOT NULL, language_code TEXT NOT NULL, lemma TEXT NOT NULL,
            transliteration TEXT NOT NULL, pronunciation TEXT, strong_number TEXT, part_of_speech TEXT,
            gloss TEXT NOT NULL, definition TEXT NOT NULL, source_name TEXT NOT NULL,
            source_url TEXT NOT NULL, license_id TEXT NOT NULL, attribution TEXT NOT NULL)""")
        db.execSQL("CREATE INDEX lexical_strongs_idx ON lexical_entries(strong_number)")
        db.execSQL("CREATE INDEX lexical_lemma_idx ON lexical_entries(language_code,lemma)")
        db.execSQL("""CREATE TABLE word_occurrences(
            id INTEGER PRIMARY KEY AUTOINCREMENT, translation_id TEXT NOT NULL, book_id TEXT NOT NULL,
            chapter INTEGER NOT NULL, verse INTEGER NOT NULL, token_index INTEGER NOT NULL,
            surface_form TEXT NOT NULL, lemma_id TEXT REFERENCES lexical_entries(id),
            strong_number TEXT, morphology_code TEXT)""")
        db.execSQL("CREATE INDEX word_occurrence_ref_idx ON word_occurrences(translation_id,book_id,chapter,verse)")
        db.execSQL("""CREATE TABLE bookmarks(
            id TEXT PRIMARY KEY NOT NULL, translation_id TEXT NOT NULL, book_id TEXT NOT NULL,
            chapter INTEGER NOT NULL, verse INTEGER, created_at INTEGER NOT NULL)""")
        db.execSQL("""CREATE TABLE study_notes(
            id TEXT PRIMARY KEY NOT NULL, translation_id TEXT, book_id TEXT, chapter INTEGER,
            verse INTEGER, title TEXT NOT NULL, body TEXT NOT NULL, color_argb INTEGER NOT NULL,
            updated_at INTEGER NOT NULL)""")
    }
    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) {
            db.execSQL("CREATE TABLE IF NOT EXISTS installed_packs(pack_id TEXT PRIMARY KEY NOT NULL, translation_id TEXT NOT NULL, version INTEGER NOT NULL, installed_at INTEGER NOT NULL, verse_count INTEGER NOT NULL)")
            db.execSQL("CREATE INDEX IF NOT EXISTS bookmarks_ref_idx ON bookmarks(translation_id,book_id,chapter,verse)")
            db.execSQL("CREATE INDEX IF NOT EXISTS notes_ref_idx ON study_notes(translation_id,book_id,chapter,verse)")
        }
    }

    fun recordInstalledPack(packId:String,translationId:String,version:Int,verseCount:Int) {
        writableDatabase.insertWithOnConflict("installed_packs",null,ContentValues().apply {
            put("pack_id",packId); put("translation_id",translationId); put("version",version)
            put("installed_at",System.currentTimeMillis()); put("verse_count",verseCount)
        },SQLiteDatabase.CONFLICT_REPLACE)
    }
    fun saveBookmark(bookmark:BibleBookmark) {
        writableDatabase.insertWithOnConflict("bookmarks",null,ContentValues().apply {
            put("id",bookmark.id); put("translation_id",bookmark.translationId); put("book_id",bookmark.bookId)
            put("chapter",bookmark.chapter); put("verse",bookmark.verse); put("created_at",bookmark.createdAt)
        },SQLiteDatabase.CONFLICT_REPLACE)
    }
    fun listBookmarks(translationId:String?=null):List<BibleBookmark> {
        val out=mutableListOf<BibleBookmark>()
        readableDatabase.query("bookmarks",null,translationId?.let { "translation_id=?" },
            translationId?.let { arrayOf(it) },null,null,"created_at DESC").use { c ->
            while(c.moveToNext()) out += BibleBookmark(
                c.getString(c.getColumnIndexOrThrow("id")),c.getString(c.getColumnIndexOrThrow("translation_id")),
                c.getString(c.getColumnIndexOrThrow("book_id")),c.getInt(c.getColumnIndexOrThrow("chapter")),
                if(c.isNull(c.getColumnIndexOrThrow("verse"))) null else c.getInt(c.getColumnIndexOrThrow("verse")),
                c.getLong(c.getColumnIndexOrThrow("created_at")))
        }
        return out
    }
    fun deleteBookmark(id:String) { writableDatabase.delete("bookmarks","id=?",arrayOf(id)) }
    fun saveNote(note:BibleStudyNote) {
        writableDatabase.insertWithOnConflict("study_notes",null,ContentValues().apply {
            put("id",note.id); put("translation_id",note.translationId); put("book_id",note.bookId)
            put("chapter",note.chapter); put("verse",note.verse); put("title",note.title)
            put("body",note.body); put("color_argb",note.colorArgb); put("updated_at",note.updatedAt)
        },SQLiteDatabase.CONFLICT_REPLACE)
    }
    fun listNotes(translationId:String?=null):List<BibleStudyNote> {
        val out=mutableListOf<BibleStudyNote>()
        readableDatabase.query("study_notes",null,translationId?.let { "translation_id=?" },
            translationId?.let { arrayOf(it) },null,null,"updated_at DESC").use { c ->
            while(c.moveToNext()) out += BibleStudyNote(
                c.getString(c.getColumnIndexOrThrow("id")),c.getString(c.getColumnIndexOrThrow("translation_id")),
                c.getString(c.getColumnIndexOrThrow("book_id")),
                if(c.isNull(c.getColumnIndexOrThrow("chapter"))) null else c.getInt(c.getColumnIndexOrThrow("chapter")),
                if(c.isNull(c.getColumnIndexOrThrow("verse"))) null else c.getInt(c.getColumnIndexOrThrow("verse")),
                c.getString(c.getColumnIndexOrThrow("title")),c.getString(c.getColumnIndexOrThrow("body")),
                c.getLong(c.getColumnIndexOrThrow("color_argb")),c.getLong(c.getColumnIndexOrThrow("updated_at")))
        }
        return out
    }
    fun deleteNote(id:String) { writableDatabase.delete("study_notes","id=?",arrayOf(id)) }

    fun installTranslation(t: BibleTranslation) {
        writableDatabase.insertWithOnConflict("translations", null, ContentValues().apply {
            put("id",t.id); put("name",t.name); put("language_tag",t.languageTag)
            put("language_name",t.languageName); put("abbreviation",t.abbreviation)
            put("canon_id",t.canonId); put("source_id",t.sourceId); put("license_id",t.licenseId)
            put("copyright_notice",t.copyrightNotice); put("installed_at",System.currentTimeMillis())
        }, SQLiteDatabase.CONFLICT_REPLACE)
    }
    fun insertBook(b: BibleBook) {
        writableDatabase.insertWithOnConflict("books",null,ContentValues().apply {
            put("id",b.id); put("translation_id",b.translationId); put("canonical_number",b.canonicalNumber)
            put("name",b.name); put("testament",b.testament); put("chapter_count",b.chapterCount)
        },SQLiteDatabase.CONFLICT_REPLACE)
    }
    fun insertVerse(v: BibleVerse) {
        val db=writableDatabase; db.beginTransaction()
        try {
            val values=ContentValues().apply {
                put("translation_id",v.translationId); put("book_id",v.bookId)
                put("chapter",v.chapter); put("verse",v.verse); put("text",v.text)
            }
            db.insertWithOnConflict("verses",null,values,SQLiteDatabase.CONFLICT_REPLACE)
            // FTS4 has no composite uniqueness constraint: delete the old indexed row
            // before inserting, otherwise a pack refresh would create duplicate hits.
            db.delete("verse_search",
                "translation_id=? AND book_id=? AND chapter=? AND verse=?",
                arrayOf(v.translationId,v.bookId,v.chapter.toString(),v.verse.toString()))
            db.insert("verse_search",null,values)
            db.setTransactionSuccessful()
        } finally { db.endTransaction() }
    }
    fun readChapter(translationId:String,bookId:String,chapter:Int):List<BibleVerse> {
        val out=mutableListOf<BibleVerse>()
        readableDatabase.rawQuery("""SELECT v.translation_id,v.book_id,b.name,v.chapter,v.verse,v.text
            FROM verses v JOIN books b ON b.id=v.book_id AND b.translation_id=v.translation_id
            WHERE v.translation_id=? AND v.book_id=? AND v.chapter=? ORDER BY v.verse""",
            arrayOf(translationId,bookId,chapter.toString())).use { c ->
            while(c.moveToNext()) out += c.toVerse()
        }
        return out
    }
    fun searchVerses(translationId:String,query:String,limit:Int=100):List<BibleVerse> {
        val q=query.trim().replace(""",""""); if(q.isBlank()) return emptyList()
        val out=mutableListOf<BibleVerse>()
        readableDatabase.rawQuery("""SELECT s.translation_id,s.book_id,b.name,s.chapter,s.verse,s.text
            FROM verse_search s JOIN books b ON b.id=s.book_id AND b.translation_id=s.translation_id
            WHERE verse_search MATCH ? AND s.translation_id=?
            ORDER BY s.book_id,CAST(s.chapter AS INTEGER),CAST(s.verse AS INTEGER) LIMIT ?""",
            arrayOf(""$q"",translationId,limit.coerceIn(1,500).toString())).use { c ->
            while(c.moveToNext()) out += c.toVerse()
        }
        return out
    }
    fun findLexicalEntryByStrong(number:String):BibleWordStudyEntry? {
        readableDatabase.query("lexical_entries",null,"strong_number=?",arrayOf(number.trim().uppercase()),null,null,null,"1").use { c ->
            return if(c.moveToFirst()) c.toLexicalEntry() else null
        }
    }
    fun findLexicalEntries(languageCode:String,query:String,limit:Int=50):List<BibleWordStudyEntry> {
        val out=mutableListOf<BibleWordStudyEntry>()
        readableDatabase.query("lexical_entries",null,
            "language_code=? AND (lemma LIKE ? OR transliteration LIKE ? OR gloss LIKE ?)",
            arrayOf(languageCode,"%$query%","%$query%","%$query%"),null,null,"lemma",
            limit.coerceIn(1,200).toString()).use { c -> while(c.moveToNext()) out += c.toLexicalEntry() }
        return out
    }
    private fun Cursor.toVerse()=BibleVerse(getString(0),getString(1),getString(2),getInt(3),getInt(4),getString(5))
    private fun Cursor.toLexicalEntry():BibleWordStudyEntry {
        val code=getString(getColumnIndexOrThrow("language_code"))
        val language=when(code){"hbo"->BiblicalLanguage.HEBREW;"arc"->BiblicalLanguage.ARAMAIC;else->BiblicalLanguage.GREEK}
        fun s(name:String)=getString(getColumnIndexOrThrow(name))
        return BibleWordStudyEntry(s("id"),language,s("lemma"),s("transliteration"),
            s("pronunciation"),s("strong_number"),s("part_of_speech"),s("gloss"),s("definition"),
            s("source_name"),s("source_url"),s("license_id"),s("attribution"))
    }
}
