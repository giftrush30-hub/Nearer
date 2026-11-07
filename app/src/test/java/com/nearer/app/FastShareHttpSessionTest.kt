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

    @Test fun headRequestIgnoresRangeHeaderAndDescribesWholeFile() {
        val (exchange, response) = read(
            "HEAD /download HTTP/1.1\r\nRange: bytes=10-19\r\n\r\n"
        )
        assertNotNull(exchange)
        assertTrue(exchange!!.request.isHeadOnly)
        assertNull(exchange.rangeHeader)
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

    @Test fun oversizedHeaderLineWrites400() {
        val oversized = "X-Long: " + "a".repeat(9000) + "\r\n"
        val (exchange, response) = read("GET /download HTTP/1.1\r\n$oversized\r\n")
        assertNull(exchange)
        assertTrue(response.startsWith("HTTP/1.1 400 Bad Request\r\n"))
    }

    @Test fun unterminatedRequestLineAtEofWrites400() {
        val (exchange, response) = read("GET /download HTTP/1.1")
        assertNull(exchange)
        assertTrue(response.startsWith("HTTP/1.1 400 Bad Request\r\n"))
    }

    @Test fun missingHeaderTerminatorAtEofWrites400() {
        val (exchange, response) = read("GET /download HTTP/1.1\r\nHost: phone")
        assertNull(exchange)
        assertTrue(response.startsWith("HTTP/1.1 400 Bad Request\r\n"))
    }

    @Test fun excessiveAggregateHeadersWrite400() {
        val headers = (1..5).joinToString("") { "X-$it: " + "a".repeat(7000) + "\r\n" }
        val (exchange, response) = read("GET /download HTTP/1.1\r\n$headers\r\n")
        assertNull(exchange)
        assertTrue(response.startsWith("HTTP/1.1 400 Bad Request\r\n"))
    }

    @Test fun excessiveHeaderCountWrites400() {
        val headers = (1..101).joinToString("") { "X-$it: ok\r\n" }
        val (exchange, response) = read("GET /download HTTP/1.1\r\n$headers\r\n")
        assertNull(exchange)
        assertTrue(response.startsWith("HTTP/1.1 400 Bad Request\r\n"))
    }

    @Test fun malformedHeaderWithoutColonWrites400() {
        val (exchange, response) = read("GET /download HTTP/1.1\r\nNot-A-Header\r\n\r\n")
        assertNull(exchange)
        assertTrue(response.startsWith("HTTP/1.1 400 Bad Request\r\n"))
    }

    @Test fun duplicateRangeHeadersAreRejected() {
        val (exchange, response) = read(
            "GET /download HTTP/1.1\r\nRange: bytes=0-9\r\nrange: bytes=10-19\r\n\r\n"
        )
        assertNull(exchange)
        assertTrue(response.startsWith("HTTP/1.1 400 Bad Request\r\n"))
    }

    @Test fun nonAsciiHeaderNameIsRejected() {
        val (exchange, response) = read("GET /download HTTP/1.1\r\nRangé: bytes=0-9\r\n\r\n")
        assertNull(exchange)
        assertTrue(response.startsWith("HTTP/1.1 400 Bad Request\r\n"))
    }

    @Test fun controlCharacterInHeaderValueIsRejected() {
        val raw = "GET /download HTTP/1.1\r\nX-Test: ok" + 1.toChar() + "bad\r\n\r\n"
        val (exchange, response) = read(raw)
        assertNull(exchange)
        assertTrue(response.startsWith("HTTP/1.1 400 Bad Request\r\n"))
    }

    @Test fun horizontalTabInHeaderValueRemainsAllowed() {
        val (exchange, response) = read("GET /download HTTP/1.1\r\nX-Test:\tok\r\n\r\n")
        assertNotNull(exchange)
        assertTrue(response.isEmpty())
    }
}
