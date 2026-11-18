package com.nearer.app.bible

import android.content.Context
import org.json.JSONObject
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream

data class BibleLexiconArchiveImportResult(val entries:Int,val alignments:Int)

/** Imports a source-attributed lexicon/alignment ZIP without trusting archive paths. */
class BibleLexiconArchiveImporter(
    context:Context,
    private val importer:BibleLexiconDatasetImporter,
    private val maxArchiveBytes:Long=100L*1024*1024,
    private val maxExpandedBytes:Long=300L*1024*1024
) {
    private val staging=File(context.cacheDir,"nearer-word-study-import").apply { mkdirs() }

    fun import(zipFile:File):BibleLexiconArchiveImportResult {
        require(zipFile.isFile && zipFile.length() in 1..maxArchiveBytes) {
            "Word-study pack is missing or exceeds the archive size limit."
        }
        val files=extract(zipFile)
        try {
            val manifestFile=files["manifest.json"] ?: error("Word-study manifest is missing.")
            val entriesFile=files["entries.ndjson"] ?: error("Lexicon entries file is missing.")
            val alignmentsFile=files["alignments.ndjson"] ?: error("Word alignments file is missing.")
            require(manifestFile.length() in 1L..(256L*1024L)) { "Word-study manifest is too large." }
            val manifest=JSONObject(manifestFile.readText(Charsets.UTF_8))
            val sourceName=manifest.getString("sourceName")
            val sourceUrl=manifest.getString("sourceUrl")
            val licenseId=manifest.getString("licenseId")
            val attribution=manifest.getString("attribution")
            require(sourceUrl.startsWith("https://")) { "Source URL must use HTTPS." }
            require(sourceName.isNotBlank() && licenseId.isNotBlank() && attribution.isNotBlank()) {
                "Source, licence and attribution are required."
            }
            require(sha256(entriesFile).equals(manifest.getString("entriesSha256"),true)) {
                "Lexicon entries checksum does not match."
            }
            require(sha256(alignmentsFile).equals(manifest.getString("alignmentsSha256"),true)) {
                "Word alignments checksum does not match."
            }
            val entryCount=countLines(entriesFile)
            val alignmentCount=countLines(alignmentsFile)
            require(entryCount>0 || alignmentCount>0) { "Word-study pack contains no entries or alignments." }
            require(entryCount==manifest.getInt("entryCount")) { "Lexicon entry count does not match manifest." }
            require(alignmentCount==manifest.getInt("alignmentCount")) { "Alignment count does not match manifest." }
            val result=importer.import(
                entriesFile.inputStream(),alignmentsFile.inputStream(),
                sourceName,sourceUrl,licenseId,attribution
            )
            return BibleLexiconArchiveImportResult(result.entries,result.alignments)
        } finally {
            files.values.forEach { it.delete() }
        }
    }

    private fun extract(zipFile:File):Map<String,File> {
        val allowed=setOf("manifest.json","entries.ndjson","alignments.ndjson")
        val out=linkedMapOf<String,File>()
        var expanded=0L
        try {
            ZipInputStream(zipFile.inputStream().buffered()).use { zip ->
                var entry:ZipEntry?=zip.nextEntry
                var count=0
                while(entry!=null) {
                    count++
                    require(count<=3) { "Word-study pack contains too many archive entries." }
                    val name=entry.name
                    require(!entry.isDirectory && name in allowed && !name.contains("/") &&
                        !name.contains("\\") && !name.contains("..")) {
                        "Unexpected or unsafe word-study archive entry."
                    }
                    require(!out.containsKey(name)) { "Duplicate word-study archive entry." }
                    val file=File(staging,System.nanoTime().toString()+"-"+name)
                    out[name]=file
                    file.outputStream().buffered().use { output ->
                        val buffer=ByteArray(16*1024)
                        while(true) {
                            val read=zip.read(buffer)
                            if(read<0) break
                            expanded+=read
                            require(expanded<=maxExpandedBytes) { "Expanded word-study pack exceeds its size limit." }
                            output.write(buffer,0,read)
                        }
                    }
                    zip.closeEntry()
                    entry=zip.nextEntry
                }
            }
            require(out.keys==allowed) { "Word-study pack must contain exactly the manifest, entries and alignments files." }
            return out
        } catch(error:Throwable) {
            out.values.forEach { it.delete() }
            throw error
        }
    }

    private fun countLines(file:File):Int =
        BufferedReader(InputStreamReader(file.inputStream(),Charsets.UTF_8)).use { reader ->
            var count=0
            while(reader.readLine()!=null) count++
            count
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
