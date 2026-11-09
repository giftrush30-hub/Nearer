package com.nearer.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

class FastShareResumePolicyTest {
    private fun millis(date: String): Long =
        SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss 'GMT'", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("GMT")
            isLenient = false
        }.parse(date)!!.time

    @Test fun emitsRfc1123LastModifiedInGmt() {
        assertEquals(
            "Mon, 09 Nov 2026 12:34:56 GMT",
            FastShareResumePolicy.formatHttpDate(millis("Mon, 09 Nov 2026 12:34:56 GMT"))
        )
    }

    @Test fun noIfRangeAllowsOrdinaryRangeRequests() {
        assertTrue(FastShareResumePolicy.shouldHonorRange("bytes=10-", null, null))
    }

    @Test fun matchingIfRangeDateAllowsResume() {
        val modified = millis("Mon, 09 Nov 2026 12:34:56 GMT")
        assertTrue(
            FastShareResumePolicy.shouldHonorRange(
                "bytes=100-",
                "Mon, 09 Nov 2026 12:34:56 GMT",
                modified + 800L
            )
        )
    }

    @Test fun staleIfRangeDateForcesFullRepresentation() {
        val modified = millis("Mon, 09 Nov 2026 12:34:56 GMT")
        assertFalse(
            FastShareResumePolicy.shouldHonorRange(
                "bytes=100-",
                "Mon, 09 Nov 2026 12:34:55 GMT",
                modified
            )
        )
    }

    @Test fun missingSourceTimestampForcesFullRepresentation() {
        assertFalse(
            FastShareResumePolicy.shouldHonorRange(
                "bytes=100-",
                "Mon, 09 Nov 2026 12:34:56 GMT",
                null
            )
        )
    }

    @Test fun malformedOrEntityTagIfRangeCannotBeMatchedWithoutAnEntityTag() {
        val modified = millis("Mon, 09 Nov 2026 12:34:56 GMT")
        assertFalse(FastShareResumePolicy.shouldHonorRange("bytes=1-", "not-a-date", modified))
        assertFalse(FastShareResumePolicy.shouldHonorRange("bytes=1-", "\"etag\"", modified))
    }

    @Test fun absentRangeNeverRequestsPartialRepresentation() {
        assertFalse(FastShareResumePolicy.shouldHonorRange(null, null, 1L))
        assertFalse(FastShareResumePolicy.shouldHonorRange("", null, 1L))
    }

    @Test fun invalidTimestampDoesNotProduceLastModifiedHeaderValue() {
        assertEquals(null, FastShareResumePolicy.formatHttpDate(null))
        assertEquals(null, FastShareResumePolicy.formatHttpDate(-1L))
    }
}
