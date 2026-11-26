package com.nearer.app.bible

data class BibleTranslation(
    val id: String, val name: String, val languageTag: String, val languageName: String,
    val abbreviation: String, val canonId: String = "protestant-66", val sourceId: String,
    val licenseId: String, val copyrightNotice: String? = null, val isDownloaded: Boolean = false,
    val attribution:String?=null
)
data class BibleBook(
    val id: String, val translationId: String, val canonicalNumber: Int,
    val name: String, val testament: String, val chapterCount: Int
)
data class BibleVerse(
    val translationId: String, val bookId: String, val bookName: String,
    val chapter: Int, val verse: Int, val text: String
) { val reference: String get() = "$bookName $chapter:$verse" }

enum class BibleReadingMode { BOOK_PAGE, SCROLL, STUDY }
enum class BiblicalLanguage(val code: String, val displayName: String) {
    HEBREW("hbo", "Biblical Hebrew"), ARAMAIC("arc", "Biblical Aramaic"),
    GREEK("grc", "Koine / Biblical Greek")
}
data class BibleWordStudyEntry(
    val id: String, val language: BiblicalLanguage, val lemma: String,
    val transliteration: String, val pronunciation: String?, val strongNumber: String?,
    val partOfSpeech: String?, val gloss: String, val definition: String,
    val sourceName: String, val sourceUrl: String, val licenseId: String, val attribution: String
)
data class BibleBookmark(
    val id: String, val translationId: String, val bookId: String,
    val chapter: Int, val verse: Int?, val createdAt: Long
)
data class BibleStudyNote(
    val id: String, val translationId: String?, val bookId: String?,
    val chapter: Int?, val verse: Int?, val title: String, val body: String,
    val colorArgb: Long, val updatedAt: Long
)

data class BibleWordOccurrence(
    val translationId:String,
    val bookId:String,
    val chapter:Int,
    val verse:Int,
    val tokenIndex:Int,
    val surfaceForm:String,
    val lemmaId:String?,
    val strongNumber:String?,
    val morphologyCode:String?
)

data class BibleVerseHighlight(
    val id:String,
    val translationId:String,
    val bookId:String,
    val chapter:Int,
    val verse:Int,
    val colorArgb:Long,
    val createdAt:Long
)
