package com.nearer.app

import org.junit.Assert.*
import org.junit.Test

class FileHttpResponsesTest {
    @Test fun ordinaryGetAndHeadShareFullFileRepresentationMetadata() {
        val response = FileHttpResponses.resolve(null, 100)
        assertEquals(200, response.statusCode)
        assertEquals("OK", response.statusText)
        assertEquals(100L, response.contentLength)
        assertNull(response.contentRange)
        assertEquals(ResolvedByteRange(0, 99, false), response.range)
    }

    @Test fun resumedRequestProduces206AndExactContentRange() {
        val response = FileHttpResponses.resolve("bytes=25-49", 100)
        assertEquals(206, response.statusCode)
        assertEquals("Partial Content", response.statusText)
        assertEquals(25L, response.contentLength)
        assertEquals("bytes 25-49/100", response.contentRange)
    }

    @Test fun openEndedRangeProduces206ThroughEndOfFile() {
        val response = FileHttpResponses.resolve("bytes=90-", 100)
        assertEquals(206, response.statusCode)
        assertEquals(10L, response.contentLength)
        assertEquals("bytes 90-99/100", response.contentRange)
    }

    @Test fun unsatisfiableRangeProduces416AndWildcardContentRange() {
        val response = FileHttpResponses.resolve("bytes=100-", 100)
        assertEquals(416, response.statusCode)
        assertEquals("Range Not Satisfiable", response.statusText)
        assertEquals(0L, response.contentLength)
        assertEquals("bytes */100", response.contentRange)
        assertNull(response.range)
    }

    @Test fun emptyFileProducesZeroLength200Response() {
        val response = FileHttpResponses.resolve(null, 0)
        assertEquals(200, response.statusCode)
        assertEquals(0L, response.contentLength)
        assertNull(response.contentRange)
    }

    @Test(expected = IllegalArgumentException::class)
    fun unknownLengthMustUseChunkedResponsePath() {
        FileHttpResponses.resolve(null, -1)
    }

    @Test fun suffixRangeProducesExactPartialRepresentation() {
        val response = FileHttpResponses.resolve("bytes=-10", 100)
        assertEquals(206, response.statusCode)
        assertEquals(10L, response.contentLength)
        assertEquals("bytes 90-99/100", response.contentRange)
    }

    @Test fun oversizedRangeEndIsClampedToLastFileByte() {
        val response = FileHttpResponses.resolve("bytes=95-9999", 100)
        assertEquals(206, response.statusCode)
        assertEquals(5L, response.contentLength)
        assertEquals("bytes 95-99/100", response.contentRange)
    }

    @Test fun rangeStartingAtEndOfFileReturns416() {
        val response = FileHttpResponses.resolve("bytes=100-100", 100)
        assertEquals(416, response.statusCode)
        assertEquals(0L, response.contentLength)
        assertEquals("bytes */100", response.contentRange)
    }


    @Test fun zeroLengthSuffixRangeIsUnsatisfiable() {
        val response = FileHttpResponses.resolve("bytes=-0", 100)
        assertEquals(416, response.statusCode)
        assertEquals("bytes */100", response.contentRange)
    }

    @Test fun reversedRangeIsUnsatisfiable() {
        val response = FileHttpResponses.resolve("bytes=80-20", 100)
        assertEquals(416, response.statusCode)
        assertEquals(0L, response.contentLength)
    }

    @Test fun overflowingRangePositionIsRejected() {
        val response = FileHttpResponses.resolve("bytes=999999999999999999999999-", 100)
        assertEquals(416, response.statusCode)
        assertEquals("bytes */100", response.contentRange)
    }

    @Test fun unsupportedRangeUnitFallsBackToFullRepresentation() {
        val response = FileHttpResponses.resolve("items=1-2", 100)
        assertEquals(200, response.statusCode)
        assertEquals(100L, response.contentLength)
    }

    @Test fun multipartRangeFallsBackToFullRepresentation() {
        val response = FileHttpResponses.resolve("bytes=0-9,20-29", 100)
        assertEquals(200, response.statusCode)
        assertEquals(100L, response.contentLength)
    }

    @Test fun rangeAgainstEmptyFileIsUnsatisfiable() {
        val response = FileHttpResponses.resolve("bytes=0-", 0)
        assertEquals(416, response.statusCode)
        assertEquals("bytes */0", response.contentRange)
    }
}
