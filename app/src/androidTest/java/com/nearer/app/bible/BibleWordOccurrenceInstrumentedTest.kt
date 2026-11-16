package com.nearer.app.bible

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import android.content.Context
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BibleWordOccurrenceInstrumentedTest {
    @Test
    fun oneTranslatedTokenCanResolveToMultipleOriginalLanguageEntries() {
        val context=ApplicationProvider.getApplicationContext<Context>()
        context.deleteDatabase("nearer_word.db")
        val database=BibleLocalDatabase(context)
        try {
            database.installTranslation(BibleTranslation(
                id="BSB",name="Berean Standard Bible",languageTag="en",languageName="English",
                abbreviation="BSB",sourceId="test",licenseId="public-domain"
            ))
            database.insertBook(BibleBook(
                id="GEN",translationId="BSB",canonicalNumber=1,name="Genesis",testament="OT",chapterCount=50
            ))
            val verse=BibleVerse("BSB","GEN","Genesis",1,1,"In the beginning God created")
            database.insertVerse(verse)
            database.insertLexicalEntry(BibleWordStudyEntry(
                id="H7225",language=BiblicalLanguage.HEBREW,lemma="רֵאשִׁית",transliteration="reshit",
                pronunciation=null,strongNumber="H7225",partOfSpeech="noun",gloss="beginning",
                definition="Beginning or first part.",sourceName="test",sourceUrl="https://example.org",
                licenseId="CC-BY-4.0",attribution="test attribution"
            ))
            database.insertLexicalEntry(BibleWordStudyEntry(
                id="G746",language=BiblicalLanguage.GREEK,lemma="ἀρχή",transliteration="arche",
                pronunciation=null,strongNumber="G746",partOfSpeech="noun",gloss="beginning",
                definition="Beginning or origin.",sourceName="test",sourceUrl="https://example.org",
                licenseId="CC-BY-4.0",attribution="test attribution"
            ))
            database.insertWordOccurrences(listOf(
                BibleWordOccurrence("BSB","GEN",1,1,2,"beginning",null,"H7225",null),
                BibleWordOccurrence("BSB","GEN",1,1,2,"beginning",null,"G746",null)
            ))
            val results=database.findAlignedLexicalEntries(verse,"beginning",2)
            assertEquals(2,results.size)
            assertEquals(setOf("H7225","G746"),results.mapNotNull{it.strongNumber}.toSet())
        } finally {
            database.close()
        }
    }
}
