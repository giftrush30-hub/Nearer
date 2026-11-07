package com.nearer.app

import org.junit.Assert.*
import org.junit.Test

class FastShareHttpErrorsTest {
    private fun headersAndBody(response: ByteArray): Pair<String, String> {
        val text = response.toString(Charsets.UTF_8)
        val split = text.split("\r\n\r\n", limit = 2)
        assertEquals("HTTP response must contain a header/body separator", 2, split.size)
        return split[0] to split[1]
    }

    private fun assertContentLength(headers: String, body: String) {
        val declared = Regex("(?im)^Content-Length: (\\d+)$")
            .find(headers)?.groupValues?.get(1)?.toInt()
        assertNotNull("Content-Length must be present", declared)
        assertEquals(body.toByteArray(Charsets.UTF_8).size, declared)
    }

    @Test fun malformedRequestReturns400WithEmptyBody() {
        val (headers, body) = headersAndBody(FastShareHttpErrors.badRequest())
        assertTrue(headers.startsWith("HTTP/1.1 400 Bad Request"))
        assertEquals("", body)
        assertContentLength(headers, body)
    }

    @Test fun unsupportedMethodReturns405AndAllowHeader() {
        val (headers, body) = headersAndBody(FastShareHttpErrors.methodNotAllowed())
        assertTrue(headers.startsWith("HTTP/1.1 405 Method Not Allowed"))
        assertTrue(headers.contains("\r\nAllow: GET, HEAD\r\n"))
        assertEquals("", body)
        assertContentLength(headers, body)
    }

    @Test fun concurrentDownloadReturns429WithRetryAndAccurateBodyLength() {
        val (headers, body) = headersAndBody(FastShareHttpErrors.tooManyRequests())
        assertTrue(headers.startsWith("HTTP/1.1 429 Too Many Requests"))
        assertTrue(headers.contains("\r\nRetry-After: 2\r\n"))
        assertEquals("Another download is already in progress.\n", body)
        assertContentLength(headers, body)
    }

    @Test fun unknownRouteReturns404WithEmptyBody() {
        val (headers, body) = headersAndBody(FastShareHttpErrors.notFound())
        assertTrue(headers.startsWith("HTTP/1.1 404 Not Found"))
        assertEquals("", body)
        assertContentLength(headers, body)
    }
    @Test fun missingOrInvalidSessionKeyReturns403() {
        val (headers, body) = headersAndBody(FastShareHttpErrors.forbidden())
        assertTrue(headers.startsWith("HTTP/1.1 403 Forbidden"))
        assertTrue(headers.contains("Content-Type: text/plain; charset=utf-8"))
        assertTrue(body.contains("invalid"))
        assertContentLength(headers, body)
    }

}
