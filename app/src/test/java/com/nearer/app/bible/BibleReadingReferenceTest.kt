package com.nearer.app.bible

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BibleReadingReferenceTest {
    @Test fun parsesSingleVerseAndRange() {
        assertEquals(BibleReadingReference("Genesis", 1, 1), parseBibleReadingReference("Genesis 1:1–31"))
        assertEquals(BibleReadingReference("1 Thessalonians", 5, 12), parseBibleReadingReference("1 Thessalonians 5:12-24"))
    }

    @Test fun supportsMultiWordBooksAndPsalmAlias() {
        assertEquals(BibleReadingReference("Song of Solomon", 1, 1), parseBibleReadingReference("Song of Solomon 1:1"))
        assertEquals(BibleReadingReference("Psalms", 13, null), parseBibleReadingReference("Psalm 13"))
    }

    @Test fun rejectsMalformedAndOutOfRangeReferences() {
        assertNull(parseBibleReadingReference("Genesis"))
        assertNull(parseBibleReadingReference("Genesis 0:1"))
        assertNull(parseBibleReadingReference("Genesis 1:301"))
        assertNull(parseBibleReadingReference("Genesis 1:1–"))
    }
}
