package com.nearer.app.bible

import android.content.Context
import org.json.JSONObject
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream

interface BiblePackInstallTarget {
    fun begin()
    fun installTranslation(manifest:BiblePackManifest)
    fun putBook(book:BibleBook)
    fun putVerse(verse:BibleVerse)
    fun commit()
    fun rollback()
}
data class BiblePackImportResult(val packId:String,val importedVerses:Int,val bytesVerified:Long)

/**
 * Imports a provider-neutral pack containing manifest.json and scripture.ndjson.
 * Archive paths are never used as filesystem paths; content is staged under generated
 * app-private names, checksum-verified, then imported in one database transaction.
 */
class BiblePackImporter(
    context:Context,
    private val target:BiblePackInstallTarget,
    private val readerVersion:Int=1,
    private val maxArchiveBytes:Long=100L*1024*1024,
    private val maxScriptureBytes:Long=300L*1024*1024,
    private val maxEntries:Int=8
) {
    private val staging=File(context.cacheDir,"nearer-word-import").apply { mkdirs() }

    fun import(zipFile:File):BiblePackImportResult {
        require(zipFile.isFile && zipFile.length() in 1..maxArchiveBytes) {
            "Bible pack is missing or exceeds the permitted archive size."
        }
        val extracted=extractSafely(zipFile)
        try {
            val manifestFile=extracted["manifest.json"] ?: error("Pack manifest is missing.")
            val booksFile=extracted["books.ndjson"] ?: error("Book catalogue is missing.")
            val scriptureFile=extracted["scripture.ndjson"] ?: error("Scripture data is missing.")
            require(manifestFile.length() in 1..256*1024) { "Manifest exceeds its size limit." }
            require(booksFile.length() in 1..4*1024*1024) { "Book catalogue exceeds its size limit." }
            require(scriptureFile.length()<=maxScriptureBytes) { "Scripture payload exceeds permitted size." }
            val manifest=parseManifest(manifestFile)
            val validation=BiblePackManifestValidator.validate(manifest,readerVersion)
            require(validation.valid) { validation.errors.joinToString(" ") }
            require(sha256(scriptureFile).equals(manifest.sha256,ignoreCase=true)) {
                "Scripture checksum does not match the pack manifest."
            }

            var count=0
            target.begin()
            try {
                target.installTranslation(manifest)
                var booksImported=0
                BufferedReader(InputStreamReader(booksFile.inputStream(),Charsets.UTF_8)).use { reader ->
                    reader.forEachLine { line ->
                        if(line.isBlank()) return@forEachLine
                        val json=JSONObject(line)
                        target.putBook(BibleBook(
                            id=json.getString("bookId"),
                            translationId=manifest.translationId,
                            canonicalNumber=json.getInt("canonicalNumber"),
                            name=json.getString("name"),
                            testament=json.getString("testament"),
                            chapterCount=json.getInt("chapterCount")
                        ))
                        booksImported++
                        require(booksImported<=manifest.bookCount) { "Pack contains more books than declared." }
                    }
                }
                require(booksImported==manifest.bookCount) { "Book count does not match manifest." }
                BufferedReader(InputStreamReader(scriptureFile.inputStream(),Charsets.UTF_8)).use { reader ->
                    reader.forEachLine { line ->
                        if(line.isBlank()) return@forEachLine
                        val json=JSONObject(line)
                        val translationId=json.getString("translationId")
                        require(translationId==manifest.translationId) { "Verse belongs to a different translation." }
                        val chapter=json.getInt("chapter")
                        val verseNo=json.getInt("verse")
                        require(chapter in 1..200 && verseNo in 1..300) { "Invalid verse reference in pack." }
                        val verseText=json.getString("text")
                        require(verseText.isNotBlank() && verseText.length<=20_000) { "Invalid verse text." }
                        target.putVerse(BibleVerse(
                            translationId=translationId,
                            bookId=json.getString("bookId"),
                            bookName=json.getString("bookName"),
                            chapter=chapter,
                            verse=verseNo,
                            text=verseText
                        ))
                        count++
                        require(count<=manifest.verseCount) { "Pack contains more verses than declared." }
                    }
                }
                require(count==manifest.verseCount) { "Verse count does not match manifest." }
                target.commit()
            } catch(error:Throwable) {
                target.rollback()
                throw error
            }
            return BiblePackImportResult(manifest.packId,count,scriptureFile.length())
        } finally {
            extracted.values.forEach { it.delete() }
        }
    }

    private fun extractSafely(zipFile:File):Map<String,File> {
        val allowed=setOf("manifest.json","books.ndjson","scripture.ndjson")
        val outputs=linkedMapOf<String,File>()
        var total=0L
        try {
            ZipInputStream(zipFile.inputStream().buffered()).use { zip ->
                var entry:ZipEntry?=zip.nextEntry
                var entries=0
                while(entry!=null) {
                entries++
                require(entries<=maxEntries) { "Pack contains too many archive entries." }
                val name=entry.name
                require(!entry.isDirectory && name in allowed && !name.contains("/") &&
                    !name.contains("\\") && !name.contains("..")) { "Unexpected or unsafe archive entry." }
                require(!outputs.containsKey(name)) { "Duplicate archive entry." }
                val out=File(staging,System.nanoTime().toString()+"-"+name)
                out.outputStream().buffered().use { stream ->
                    val buffer=ByteArray(16*1024)
                    while(true) {
                        val read=zip.read(buffer)
                        if(read<0) break
                        total+=read
                        require(total<=maxScriptureBytes+2*1024*1024) { "Expanded pack exceeds permitted size." }
                        stream.write(buffer,0,read)
                    }
                }
                outputs[name]=out
                zip.closeEntry()
                    entry=zip.nextEntry
                }
            }
            return outputs
        } catch(error:Throwable) {
            outputs.values.forEach { it.delete() }
            throw error
        }
    }

    private fun parseManifest(file:File):BiblePackManifest {
        val json=JSONObject(file.readText(Charsets.UTF_8))
        return BiblePackManifest(
            packId=json.getString("packId"), version=json.getInt("version"),
            translationId=json.getString("translationId"), translationName=json.getString("translationName"),
            languageTag=json.getString("languageTag"), languageName=json.getString("languageName"),
            abbreviation=json.getString("abbreviation"),
            canonId=json.getString("canonId"), sourceId=json.getString("sourceId"),
            licenseId=json.getString("licenseId"),
            copyrightNotice=json.optString("copyrightNotice").takeIf { it.isNotBlank() },
            coverage=BibleCoverage.valueOf(json.getString("coverage")),
            bookCount=json.getInt("bookCount"), verseCount=json.getInt("verseCount"),
            sha256=json.getString("sha256"), minimumReaderVersion=json.getInt("minimumReaderVersion"),
            attribution=json.getString("attribution"), packFileName=json.getString("packFileName")
        )
    }

    private fun sha256(file:File):String {
        val digest=MessageDigest.getInstance("SHA-256")
        file.inputStream().buffered().use { input ->
            val buffer=ByteArray(32*1024)
            while(true) {
                val read=input.read(buffer)
                if(read<0) break
                digest.update(buffer,0,read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}
