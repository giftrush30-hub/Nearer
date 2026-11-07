package com.nearer.app

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FastShareHttpWireTest {
    @Test fun responseHeadUsesActualHttpCrLfDelimiters() {
        val bytes = FastShareHttpWire.responseHead(
            "HTTP/1.1 200 OK",
            listOf(
                "Content-Type" to "text/html; charset=utf-8",
                "Content-Length" to "42",
                "Connection" to "close"
            )
        )

        assertArrayEquals(
            ("HTTP/1.1 200 OK\r\n" +
                "Content-Type: text/html; charset=utf-8\r\n" +
                "Content-Length: 42\r\n" +
                "Connection: close\r\n\r\n").toByteArray(Charsets.US_ASCII),
            bytes
        )
        assertTrue(bytes.takeLast(4).toByteArray().contentEquals(byteArrayOf(13, 10, 13, 10)))
    }

    @Test fun responseHeadRejectsHeaderLineInjection() {
        try {
            FastShareHttpWire.responseHead("HTTP/1.1 200 OK", listOf("X-Test" to "ok\r\nInjected: yes"))
            throw AssertionError("CRLF header injection must be rejected")
        } catch (_: IllegalArgumentException) {
            // Expected.
        }
    }
}
