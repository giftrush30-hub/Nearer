package com.nearer.app.bible

enum class BibleCoverage { COMPLETE_BIBLE, OLD_TESTAMENT, NEW_TESTAMENT, PARTIAL }

data class BiblePackManifest(
    val packId:String,
    val version:Int,
    val translationId:String,
    val translationName:String,
    val languageTag:String,
    val languageName:String,
    val abbreviation:String,
    val canonId:String,
    val sourceId:String,
    val licenseId:String,
    val copyrightNotice:String?,
    val coverage:BibleCoverage,
    val bookCount:Int,
    val verseCount:Int,
    val sha256:String,
    val minimumReaderVersion:Int,
    val attribution:String,
    val packFileName:String
)

data class BiblePackValidation(val valid:Boolean,val errors:List<String>)

/** Strict metadata validation before a pack is opened or imported. */
object BiblePackManifestValidator {
    private val safeId=Regex("^[A-Za-z0-9._-]{2,100}$")
    private val sha256=Regex("^[a-fA-F0-9]{64}$")
    private val safeFile=Regex("^[A-Za-z0-9._-]{1,120}\\.(zip|db|sqlite)$",RegexOption.IGNORE_CASE)

    fun validate(manifest:BiblePackManifest,currentReaderVersion:Int):BiblePackValidation {
        val errors=mutableListOf<String>()
        if(!safeId.matches(manifest.packId)) errors += "Invalid pack ID."
        if(manifest.version<1) errors += "Pack version must be positive."
        if(!safeId.matches(manifest.translationId)) errors += "Invalid translation ID."
        if(manifest.languageTag.isBlank() || !manifest.languageTag.contains("-") && manifest.languageTag.length !in 2..3)
            errors += "Language tag is missing or malformed."
        if(manifest.translationName.isBlank() || manifest.languageName.isBlank() || manifest.abbreviation.isBlank())
            errors += "Translation display name, language name and abbreviation are required."
        if(manifest.canonId.isBlank() || manifest.sourceId.isBlank() || manifest.licenseId.isBlank())
            errors += "Canon, source and licence identifiers are required."
        if(manifest.bookCount !in 1..100) errors += "Book count is outside the supported range."
        if(manifest.verseCount !in 1..100_000) errors += "Verse count is outside the supported range."
        if(!sha256.matches(manifest.sha256)) errors += "A valid SHA-256 checksum is required."
        if(manifest.minimumReaderVersion>currentReaderVersion) errors += "This pack requires a newer NeaRer version."
        if(manifest.attribution.isBlank()) errors += "Source attribution is required."
        if(!safeFile.matches(manifest.packFileName) || manifest.packFileName.contains(".."))
            errors += "Unsafe pack filename."
        if(manifest.licenseId.startsWith("copyright",ignoreCase=true) && manifest.copyrightNotice.isNullOrBlank())
            errors += "Copyrighted translations require a copyright notice."
        if(manifest.coverage==BibleCoverage.COMPLETE_BIBLE) {
            val expectedBooks=when(manifest.canonId.lowercase()) {
                "protestant-66" -> 66
                else -> null
            }
            if(expectedBooks==null) errors += "Complete coverage currently requires the chapter-validated Protestant 66-book canon."
            else if(manifest.bookCount!=expectedBooks)
                errors += "The " + manifest.canonId + " canon requires exactly " + expectedBooks + " books."
        }
        if(manifest.coverage==BibleCoverage.OLD_TESTAMENT && manifest.bookCount !in 1..50)
            errors += "Old Testament pack book count is outside the supported range."
        if(manifest.coverage==BibleCoverage.NEW_TESTAMENT && manifest.bookCount !in 1..40)
            errors += "New Testament pack book count is outside the supported range."
        return BiblePackValidation(errors.isEmpty(),errors)
    }
}
