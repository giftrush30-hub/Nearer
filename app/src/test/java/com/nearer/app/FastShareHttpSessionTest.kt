package com.nearer.app

import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

class FastShareHttpSessionTest {
    private fun read(raw: String): Pair<FastShareHttpExchange?, String> {
        val output = ByteArrayOutputStream()
        val exchange = FastShareHttpSession.readRequest(
            ByteArrayInputStream(raw.toByteArray(Charsets.UTF_8)),
            output
        )
        return exchange to output.toString("UTF-8")
    }

    @Test fun getRequestReadsRangeHeaderCaseInsensitively() {
        val (exchange, response) = read(
            "GET /download HTTP/1.1\r\nHost: 192.168.43.1\r\nrAnGe: bytes=10-29\r\n\r\n"
        )
        assertNotNull(exchange)
        assertEquals(FastShareHttpRequest.Route.DOWNLOAD, exchange!!.request.route)
        assertEquals("bytes=10-29", exchange.rangeHeader)
        assertTrue(response.isEmpty())
    }

    @Test fun headRequestIsReturnedAsHeadOnlyExchange() {
        val (exchange, response) = read("HEAD /download HTTP/1.1\r\n\r\n")
        assertNotNull(exchange)
        assertTrue(exchange!!.request.isHeadOnly)
        assertTrue(response.isEmpty())
    }

    @Test fun unsupportedMethodWrites405AndAllowHeader() {
        val (exchange, response) = read("POST /download HTTP/1.1\r\n\r\n")
        assertNull(exchange)
        assertTrue(response.startsWith("HTTP/1.1 405 Method Not Allowed\r\n"))
        assertTrue(response.contains("Allow: GET, HEAD\r\n"))
    }

    @Test fun unknownPathWrites404WithoutCallingFileRoute() {
        val (exchange, response) = read("GET /private HTTP/1.1\r\n\r\n")
        assertNull(exchange)
        assertTrue(response.startsWith("HTTP/1.1 404 Not Found\r\n"))
    }

    @Test fun malformedRequestWrites400() {
        val (exchange, response) = read("GET /download HTTP/2.0\r\n\r\n")
        assertNull(exchange)
        assertTrue(response.startsWith("HTTP/1.1 400 Bad Request\r\n"))
    }

    @Test fun oversizedHeaderWrites400InsteadOfBeingPartiallyAccepted() {
        val oversized = "X-Long: " + "a".repeat(9000) + "\r\n"
        val (exchange, response) = read("GET /download HTTP/1.1\r\n$oversized\r\n")
        assertNull(exchange)
        assertTrue(response.startsWith("HTTP/1.1 400 Bad Request\r\n"))
    }
}
