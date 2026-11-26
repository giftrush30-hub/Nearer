package com.nearer.app.bible

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class BibleCanonChapterCountsTest {
    @Test fun containsEveryProtestantBookInCanonicalOrder() {
        assertEquals(66,BibleCanonChapterCounts.protestant66.size)
        assertEquals("GEN",BibleCanonChapterCounts.protestant66[1]?.usfmCode)
        assertEquals("REV",BibleCanonChapterCounts.protestant66[66]?.usfmCode)
    }

    @Test fun validatesKnownChapterCounts() {
        assertEquals(50,BibleCanonChapterCounts.protestant66[1]?.chapterCount)
        assertEquals(150,BibleCanonChapterCounts.protestant66[19]?.chapterCount)
        assertEquals(22,BibleCanonChapterCounts.protestant66[66]?.chapterCount)
    }

    @Test fun noCanonSlotIsMissing() {
        for(number in 1..66) assertNotNull(BibleCanonChapterCounts.protestant66[number])
    }
}
