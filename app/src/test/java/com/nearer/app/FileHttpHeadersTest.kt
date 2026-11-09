package com.nearer.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FileHttpHeadersTest {
    @Test fun acceptsOrdinaryMimeType() {
        assertEquals("video/mp4", FileHttpHeaders.contentType("video/mp4"))
    }

    @Test fun fallsBackForEmptyOrMalformedMimeType() {
        assertEquals("application/octet-stream", FileHttpHeaders.contentType(""))
        assertEquals("application/octet-stream", FileHttpHeaders.contentType("video/mp4\r\nX-Injected: yes"))
        assertEquals("application/octet-stream", FileHttpHeaders.contentType("not a mime type"))
    }

    @Test fun encodesFileNameAsRfc5987ValueWithoutHeaderBreaks() {
        val header = FileHttpHeaders.contentDisposition("holiday video\r\nX-Injected: yes.mp4")
        assertEquals(
            "Content-Disposition: attachment; filename*=UTF-8''holiday%20video%0D%0AX-Injected%3A%20yes.mp4\r\n",
            header
        )
        assertEquals(-1, header.indexOf("\nX-Injected"))
    }

    @Test fun fileHeadersContainOnlyValidatedValues() {
        val headers = FileHttpHeaders.fileHeaders("application/pdf\r\nX: y", "report.pdf")
        assertTrue(headers.startsWith(
            "Content-Type: application/octet-stream\r\n" +
                "Content-Disposition: attachment; filename*=UTF-8''report.pdf\r\n"
        ))
        assertTrue(headers.contains("Cache-Control: no-store\r\n"))
        assertTrue(headers.contains("X-Content-Type-Options: nosniff\r\n"))
        assertTrue(headers.contains("Referrer-Policy: no-referrer\r\n"))
        assertEquals(1, Regex("(?m)^Cache-Control:").findAll(headers).count())
    }

    @Test fun receiverPageHeadersPreventCachingFramingSniffingAndExternalExecution() {
        val headers = FileHttpHeaders.receiverPageHeaders(123L).toMap()
        assertEquals("text/html; charset=utf-8", headers["Content-Type"])
        assertEquals("123", headers["Content-Length"])
        assertEquals("no-store", headers["Cache-Control"])
        assertEquals("no-cache", headers["Pragma"])
        assertEquals("nosniff", headers["X-Content-Type-Options"])
        assertEquals("no-referrer", headers["Referrer-Policy"])
        assertEquals("DENY", headers["X-Frame-Options"])
        assertEquals(
            "default-src 'none'; style-src 'unsafe-inline'; base-uri 'none'; form-action 'none'; frame-ancestors 'none'",
            headers["Content-Security-Policy"]
        )
        assertEquals("camera=(), microphone=(), geolocation=()", headers["Permissions-Policy"])
        assertEquals("close", headers["Connection"])
    }

    @Test(expected = IllegalArgumentException::class)
    fun receiverPageHeadersRejectNegativeContentLength() {
        FileHttpHeaders.receiverPageHeaders(-1L)
    }


    @Test fun unsatisfiedRangeHeadersArePrivateAndDescribeTheWholeFile() {
        val headers = FileHttpHeaders.unsatisfiedRangeHeaders(987654321L)
        assertTrue(headers.startsWith("Accept-Ranges: bytes\\r\\n"))
        assertTrue(headers.contains("Content-Range: bytes */987654321\\r\\n"))
        assertTrue(headers.contains("Content-Length: 0\\r\\n"))
        assertTrue(headers.contains("Cache-Control: no-store\\r\\n"))
        assertTrue(headers.contains("X-Content-Type-Options: nosniff\\r\\n"))
        assertTrue(headers.contains("Referrer-Policy: no-referrer\\r\\n"))
        assertTrue(headers.endsWith("Connection: close\\r\\n\\r\\n"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun unsatisfiedRangeHeadersRejectNegativeFileLength() {
        FileHttpHeaders.unsatisfiedRangeHeaders(-1L)
    }

}
