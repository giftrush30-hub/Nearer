package com.nearer.app.bible

/** Production adapter joining the validated archive importer to the local SQLite store. */
class SqliteBiblePackInstallTarget(private val database:BibleLocalDatabase):BiblePackInstallTarget {
    private var activeManifest:BiblePackManifest?=null
    private var importedVerses=0
    private val booksById=linkedMapOf<String,BibleBook>()
    private val seenCanonicalNumbers=mutableSetOf<Int>()
    private val seenVerseKeys=mutableSetOf<String>()
    private val seenChapterKeys=mutableSetOf<String>()

    override fun begin() {
        check(activeManifest==null) { "A Bible pack install is already active." }
        database.beginPackTransaction()
        importedVerses=0
        booksById.clear();seenCanonicalNumbers.clear();seenVerseKeys.clear();seenChapterKeys.clear()
    }
    override fun installTranslation(manifest:BiblePackManifest) {
        check(activeManifest==null || activeManifest?.translationId==manifest.translationId)
        activeManifest=manifest
        database.installTranslation(BibleTranslation(
            id=manifest.translationId,name=manifest.translationName,languageTag=manifest.languageTag,
            languageName=manifest.languageName,abbreviation=manifest.abbreviation,
            canonId=manifest.canonId,sourceId=manifest.sourceId,licenseId=manifest.licenseId,
            copyrightNotice=manifest.copyrightNotice,isDownloaded=true,attribution=manifest.attribution
        ))
    }
    override fun putBook(book:BibleBook) {
        check(activeManifest?.translationId==book.translationId) { "Book does not belong to active translation." }
        require(book.id.isNotBlank() && book.name.isNotBlank())
        require(book.canonicalNumber in 1..100 && book.chapterCount in 1..200)
        require(book.testament in setOf("OT","NT","DEUTEROCANONICAL"))
        val manifest=checkNotNull(activeManifest)
        if(manifest.coverage==BibleCoverage.COMPLETE_BIBLE) {
            require(manifest.canonId=="protestant-66") { "This reader build only validates the Protestant 66-book complete canon." }
            val expected=BibleCanonChapterCounts.protestant66[book.canonicalNumber]
                ?: error("Book number is outside the validated Protestant canon.")
            require(expected.usfmCode==book.id && expected.chapterCount==book.chapterCount) {
                "Book identity or chapter count does not match the validated Protestant canon."
            }
        }
        require(booksById.put(book.id,book)==null) { "Duplicate book ID in Bible pack." }
        require(seenCanonicalNumbers.add(book.canonicalNumber)) { "Duplicate canonical book number in Bible pack." }
        database.insertBook(book)
    }
    override fun putVerse(verse:BibleVerse) {
        check(activeManifest?.translationId==verse.translationId) { "Verse does not belong to active translation." }
        val book=booksById[verse.bookId] ?: error("Verse references an unknown book.")
        require(verse.chapter in 1..book.chapterCount && verse.verse in 1..300)
        val key=verse.bookId+":"+verse.chapter+":"+verse.verse
        require(seenVerseKeys.add(key)) { "Duplicate verse reference in Bible pack." }
        seenChapterKeys.add(verse.bookId+":"+verse.chapter)
        database.insertVerse(verse)
        importedVerses++
    }
    override fun commit() {
        val manifest=checkNotNull(activeManifest) { "No active Bible pack." }
        check(importedVerses==manifest.verseCount) { "Imported verse count differs from manifest." }
        if(manifest.coverage==BibleCoverage.COMPLETE_BIBLE) {
            val expectedChapters=booksById.values.sumOf{it.chapterCount}
            check(seenChapterKeys.size==expectedChapters) { "A complete Bible pack is missing one or more chapters." }
        }
        database.recordInstalledPack(manifest.packId,manifest.translationId,manifest.version,importedVerses)
        database.commitPackTransaction()
        activeManifest=null
    }
    override fun rollback() {
        database.rollbackPackTransaction()
        activeManifest=null
        importedVerses=0
        booksById.clear();seenCanonicalNumbers.clear();seenVerseKeys.clear();seenChapterKeys.clear()
    }
}
