package com.nearer.app.bible

import android.content.Context

/** Shared repository facade for Bible pack installation and private study data. */
class BibleStudyRepository(context:Context) {
    private val helper=BibleLocalDatabase(context)

    fun installTarget():BiblePackInstallTarget = SqliteBiblePackInstallTarget(helper)
    fun saveBookmark(bookmark:BibleBookmark) = helper.saveBookmark(bookmark)
    fun listBookmarks(translationId:String?=null) = helper.listBookmarks(translationId)
    fun deleteBookmark(id:String) = helper.deleteBookmark(id)
    fun saveNote(note:BibleStudyNote) = helper.saveNote(note)
    fun listNotes(translationId:String?=null) = helper.listNotes(translationId)
    fun deleteNote(noteId:String) = helper.deleteNote(noteId)
    fun saveHighlight(highlight:BibleVerseHighlight) = helper.saveHighlight(highlight)
    fun listHighlights(translationId:String?=null) = helper.listHighlights(translationId)
    fun deleteHighlight(id:String) = helper.deleteHighlight(id)
}
