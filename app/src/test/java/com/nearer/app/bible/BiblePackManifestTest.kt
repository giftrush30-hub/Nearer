package com.nearer.app.bible

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BiblePackManifestTest {
    private fun valid()=BiblePackManifest(
        packId="yoruba-bm",version=1,translationId="YCE",translationName="Yoruba Bible",
        languageTag="yo-NG",languageName="Yoruba",abbreviation="YCE",
        canonId="protestant-66",sourceId="bible-society-nigeria",licenseId="open-test",
        copyrightNotice=null,coverage=BibleCoverage.COMPLETE_BIBLE,bookCount=66,verseCount=31102,
        sha256="a".repeat(64),minimumReaderVersion=1,attribution="Publisher attribution",
        packFileName="yoruba-bm.zip"
    )
    @Test fun acceptsWellFormedPackManifest() {
        assertTrue(BiblePackManifestValidator.validate(valid(),1).valid)
    }
    @Test fun rejectsPathTraversalFilename() {
        val result=BiblePackManifestValidator.validate(valid().copy(packFileName="../payload.zip"),1)
        assertFalse(result.valid)
    }
    @Test fun rejectsCopyrightPackWithoutNotice() {
        val result=BiblePackManifestValidator.validate(valid().copy(licenseId="copyright-standard"),1)
        assertFalse(result.valid)
    }
    @Test fun rejectsFalseCompleteBibleClaim() {
        val result=BiblePackManifestValidator.validate(valid().copy(bookCount=27),1)
        assertFalse(result.valid)
    }
    @Test fun rejectsNewerReaderRequirement() {
        val result=BiblePackManifestValidator.validate(valid().copy(minimumReaderVersion=4),1)
        assertFalse(result.valid)
    }

    @Test fun rejectsCompleteBibleWithUnknownCanon() {
        val result=BiblePackManifestValidator.validate(valid().copy(canonId="unspecified",bookCount=66),1)
        assertFalse(result.valid)
    }

    @Test fun acceptsChapterValidatedProtestantCanon() {
        val result=BiblePackManifestValidator.validate(valid().copy(canonId="protestant-66",bookCount=66),1)
        assertTrue(result.valid)
    }

    @Test fun defersCompleteCatholicCanonUntilChapterMetadataExists() {
        val result=BiblePackManifestValidator.validate(valid().copy(canonId="catholic-73",bookCount=73),1)
        assertFalse(result.valid)
    }
}
