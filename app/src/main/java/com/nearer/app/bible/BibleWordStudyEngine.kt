package com.nearer.app.bible

/** Lookup layer for imported, attributed original-language data. */
class BibleWordStudyEngine(private val database:BibleLocalDatabase) {
    fun findByStrongNumber(number:String)=database.findLexicalEntryByStrong(number.trim().uppercase())
    fun findAllForVerseWord(verse:BibleVerse,surfaceForm:String,tokenIndex:Int):List<BibleWordStudyEntry> =
        database.findAlignedLexicalEntries(verse,surfaceForm,tokenIndex)
    fun findForVerseWord(verse:BibleVerse,surfaceForm:String,tokenIndex:Int):BibleWordStudyEntry? =
        findAllForVerseWord(verse,surfaceForm,tokenIndex).firstOrNull()
    fun search(language: BiblicalLanguage,query:String):List<BibleWordStudyEntry> {
        val q=query.trim()
        if(q.length<2) return emptyList()
        return database.findLexicalEntries(language.code,q)
    }
    fun summarize(entry:BibleWordStudyEntry)=BibleWordStudySummary(
        entry.lemma,entry.transliteration,entry.pronunciation,entry.language.displayName,
        entry.strongNumber,entry.partOfSpeech,entry.gloss,entry.definition,
        entry.attribution,entry.sourceUrl,entry.licenseId)
}
data class BibleWordStudySummary(
    val originalScript:String,val transliteration:String,val pronunciation:String?,
    val language:String,val strongNumber:String?,val grammar:String?,val gloss:String,
    val definition:String,val attribution:String,val sourceUrl:String,val licenseId:String
)
object BibleWordStudyCopy {
    const val CONTEXT_NOTICE="A word may carry different senses in different passages. Consider its grammar and context before deciding which sense applies here."
}
