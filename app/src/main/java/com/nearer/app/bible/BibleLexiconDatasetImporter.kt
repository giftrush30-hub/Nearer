package com.nearer.app.bible

import android.database.sqlite.SQLiteConstraintException
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader

data class BibleLexiconImportResult(val entries:Int,val alignments:Int)

/**
 * Imports attributed lexical data and word-to-lemma alignment.
 * A source word is only linked when its token index and surface form match the installed verse.
 * This prevents guessed translations from being presented as original-language facts.
 */
class BibleLexiconDatasetImporter(
    private val database:BibleLocalDatabase,
    private val maxEntries:Int=250_000,
    private val maxAlignments:Int=2_000_000
) {
    fun import(
        entriesStream:InputStream,
        alignmentsStream:InputStream,
        sourceName:String,
        sourceUrl:String,
        licenseId:String,
        attribution:String
    ):BibleLexiconImportResult {
        require(sourceName.isNotBlank() && attribution.isNotBlank() && licenseId.isNotBlank()) {
            "Source, licence and attribution are required."
        }
        require(sourceUrl.startsWith("https://")) { "Lexicon source must use HTTPS." }
        var entries=0
        var alignments=0
        val verseCache=hashMapOf<String,BibleVerse>()
        val tokenCache=hashMapOf<String,List<String>>()
        val validatedLemmaIds=hashSetOf<String>()
        val alignmentBatch=ArrayList<BibleWordOccurrence>(1000)
        database.beginPackTransaction()
        try {
            BufferedReader(InputStreamReader(entriesStream,Charsets.UTF_8)).use { reader ->
                reader.forEachLine { line ->
                    if(line.isBlank()) return@forEachLine
                    require(++entries<=maxEntries) { "Lexicon dataset exceeds its entry limit." }
                    val json=JSONObject(line)
                    val language=when(json.getString("languageCode")) {
                        "hbo"->BiblicalLanguage.HEBREW
                        "arc"->BiblicalLanguage.ARAMAIC
                        "grc"->BiblicalLanguage.GREEK
                        else->error("Unsupported biblical source language.")
                    }
                    val lemma=json.getString("lemma").trim()
                    val gloss=json.getString("gloss").trim()
                    val definition=json.getString("definition").trim()
                    require(lemma.isNotBlank() && gloss.isNotBlank() && definition.isNotBlank())
                    val lexicalStrong=json.optString("strongNumber").takeIf{it.isNotBlank()}?.uppercase()
                    require(lexicalStrong==null || Regex("^[GH][0-9]{1,5}$").matches(lexicalStrong)) { "Invalid Strong number in lexical entry." }
                    database.insertLexicalEntry(BibleWordStudyEntry(
                        id=json.getString("id"),language=language,lemma=lemma,
                        transliteration=json.getString("transliteration"),
                        pronunciation=json.optString("pronunciation").takeIf{it.isNotBlank()},
                        strongNumber=lexicalStrong,
                        partOfSpeech=json.optString("partOfSpeech").takeIf{it.isNotBlank()},
                        gloss=gloss,definition=definition,sourceName=sourceName,
                        sourceUrl=sourceUrl,licenseId=licenseId,attribution=attribution
                    ))
                }
            }
            BufferedReader(InputStreamReader(alignmentsStream,Charsets.UTF_8)).use { reader ->
                reader.forEachLine { line ->
                    if(line.isBlank()) return@forEachLine
                    require(++alignments<=maxAlignments) { "Word alignment dataset exceeds its limit." }
                    val json=JSONObject(line)
                    val translationId=json.getString("translationId")
                    val bookId=json.getString("bookId")
                    val chapter=json.getInt("chapter")
                    val verseNo=json.getInt("verse")
                    val tokenIndex=json.getInt("tokenIndex")
                    val surface=json.getString("surfaceForm")
                    require(chapter>0 && verseNo>0 && tokenIndex>=0 && surface.isNotBlank())
                    val cacheKey="$translationId|$bookId|$chapter|$verseNo"
                    val verse=verseCache.getOrPut(cacheKey) {
                        database.readChapter(translationId,bookId,chapter)
                            .firstOrNull{it.verse==verseNo}
                            ?: error("Install the matching Bible translation pack before importing this word-alignment dataset.")
                    }
                    val tokens=tokenCache.getOrPut(cacheKey) { Regex("\\S+").findAll(verse.text).map{it.value}.toList() }
                    require(tokenIndex in tokens.indices) { "Alignment token index is outside the verse." }
                    require(tokens[tokenIndex].trim(' ',',','.',';',':','!','?','(',')','[',']','"','“','”')
                        .equals(surface.trim(' ',',','.',';',':','!','?','(',')','[',']','"','“','”'),true)) {
                        "Alignment surface form does not match the installed translation."
                    }
                    val lemmaId=json.optString("lemmaId").takeIf{it.isNotBlank()}
                    val strongNumber=json.optString("strongNumber").takeIf{it.isNotBlank()}?.uppercase()
                    require(lemmaId!=null || strongNumber!=null) { "Alignment must identify a lexical entry or Strong number." }
                    require(strongNumber==null || Regex("^[GH][0-9]{1,5}$").matches(strongNumber)) { "Invalid Strong number in word alignment." }
                    if(lemmaId!=null && validatedLemmaIds.add(lemmaId)) require(database.findLexicalEntryById(lemmaId)!=null) {
                        "Alignment references a missing lexical entry."
                    }
                    alignmentBatch += BibleWordOccurrence(
                        translationId,bookId,chapter,verseNo,tokenIndex,surface,lemmaId,
                        strongNumber,json.optString("morphologyCode").takeIf{it.isNotBlank()}
                    )
                    if(alignmentBatch.size>=1000) {
                        database.insertWordOccurrences(alignmentBatch)
                        alignmentBatch.clear()
                    }
                }
            }
            database.insertWordOccurrences(alignmentBatch)
            alignmentBatch.clear()
            database.commitPackTransaction()
            return BibleLexiconImportResult(entries,alignments)
        } catch(error:Throwable) {
            database.rollbackPackTransaction()
            throw error
        }
    }
}
