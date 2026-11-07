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
}
