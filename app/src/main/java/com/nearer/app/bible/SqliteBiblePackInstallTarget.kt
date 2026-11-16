package com.nearer.app.bible

/** Production adapter joining the validated archive importer to the local SQLite store. */
class SqliteBiblePackInstallTarget(private val database:BibleLocalDatabase):BiblePackInstallTarget {
    private var activeManifest:BiblePackManifest?=null
    private var importedVerses=0

    override fun begin() {
        check(activeManifest==null) { "A Bible pack install is already active." }
        database.beginPackTransaction()
        importedVerses=0
    }
    override fun installTranslation(manifest:BiblePackManifest) {
        check(activeManifest==null || activeManifest?.translationId==manifest.translationId)
        activeManifest=manifest
        database.installTranslation(BibleTranslation(
            id=manifest.translationId,name=manifest.translationName,languageTag=manifest.languageTag,
            languageName=manifest.languageName,abbreviation=manifest.abbreviation,
            canonId=manifest.canonId,sourceId=manifest.sourceId,licenseId=manifest.licenseId,
            copyrightNotice=manifest.copyrightNotice,isDownloaded=true
        ))
    }
    override fun putBook(book:BibleBook) {
        check(activeManifest?.translationId==book.translationId) { "Book does not belong to active translation." }
        require(book.id.isNotBlank() && book.name.isNotBlank())
        require(book.canonicalNumber in 1..100 && book.chapterCount in 1..200)
        require(book.testament in setOf("OT","NT","DEUTEROCANONICAL"))
        database.insertBook(book)
    }
    override fun putVerse(verse:BibleVerse) {
        check(activeManifest?.translationId==verse.translationId) { "Verse does not belong to active translation." }
        database.insertVerse(verse)
        importedVerses++
    }
    override fun commit() {
        val manifest=checkNotNull(activeManifest) { "No active Bible pack." }
        check(importedVerses==manifest.verseCount) { "Imported verse count differs from manifest." }
        database.recordInstalledPack(manifest.packId,manifest.translationId,manifest.version,importedVerses)
        database.commitPackTransaction()
        activeManifest=null
    }
    override fun rollback() {
        database.rollbackPackTransaction()
        activeManifest=null
        importedVerses=0
    }
}
