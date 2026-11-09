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
}
