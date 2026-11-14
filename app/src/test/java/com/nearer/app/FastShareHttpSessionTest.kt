package com.nearer.app

import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

class FastShareHttpSessionTest {
    private fun read(raw: String, addDefaultHost: Boolean = true): Pair<FastShareHttpExchange?, String> {
        val request = if (addDefaultHost && raw.startsWith("GET ") || addDefaultHost && raw.startsWith("HEAD ") ||
            addDefaultHost && raw.startsWith("POST ") || addDefaultHost && raw.startsWith("PUT ")) {
            val firstLineEnd = raw.indexOf("\r\n")
            val head = if (firstLineEnd >= 0) raw.substring(0, firstLineEnd + 2) else ""
            if (head.endsWith("HTTP/1.1\r\n") && !raw.substring(firstLineEnd + 2).startsWith("Host:", ignoreCase = true) &&
                !raw.contains("\r\nHost:", ignoreCase = true) && !raw.contains("\r\nhost:", ignoreCase = true)) {
                raw.replaceFirst("\r\n", "\r\nHost: test.local\r\n")
            } else raw
        } else raw
        val output = ByteArrayOutputStream()
        val exchange = FastShareHttpSession.readRequest(
            ByteArrayInputStream(request.toByteArray(Charsets.UTF_8)),
            output
        )
        return exchange to output.toString("UTF-8")
    }

    @Test fun overloadResponseClearlyAsksReceiverToRetry() {
        val response = FastShareHttpErrors.tooManyRequests().toString(Charsets.UTF_8)
        assertTrue(response.startsWith("HTTP/1.1 429 Too Many Requests\r\n"))
        assertTrue(response.contains("Retry-After: 2\r\n"))
        assertTrue(response.contains("Connection: close\r\n"))
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

    @Test fun uploadRequestReadsDeclaredBodyLengthAndContentTypeWithoutConsumingBody() {
        val raw = "PUT /upload?name=photo.jpg HTTP/1.1\r\nHost: phone.local\r\nContent-Length: 4\r\nContent-Type: image/jpeg\r\n\r\nDATA"
        val input = ByteArrayInputStream(raw.toByteArray(Charsets.UTF_8))
        val output = ByteArrayOutputStream()
        val exchange = FastShareHttpSession.readRequest(input, output)
        assertNotNull(exchange)
        assertEquals(FastShareHttpRequest.Route.UPLOAD, exchange!!.request.route)
        assertEquals(4L, exchange.contentLength)
        assertEquals("image/jpeg", exchange.contentType)
        assertEquals('D'.code, input.read())
        assertTrue(output.toByteArray().isEmpty())
    }

    @Test fun uploadRequiresContentLength() {
        val (exchange, response) = read("PUT /upload?name=photo.jpg HTTP/1.1\r\n\r\n")
        assertNull(exchange)
        assertTrue(response.startsWith("HTTP/1.1 411 Length Required\r\n"))
    }

    @Test fun uploadRejectsDuplicateLengthAndTransferEncoding() {
        val duplicate = read("PUT /upload?name=photo.jpg HTTP/1.1\r\nContent-Length: 1\r\nContent-Length: 1\r\n\r\n")
        val chunked = read("PUT /upload?name=photo.jpg HTTP/1.1\r\nTransfer-Encoding: chunked\r\n\r\n")
        assertTrue(duplicate.second.startsWith("HTTP/1.1 400 Bad Request\r\n"))
        assertTrue(chunked.second.startsWith("HTTP/1.1 400 Bad Request\r\n"))
    }

    @Test fun uploadRejectsMissingOrRepeatedFileMimeHeaders() {
        val duplicate = read("PUT /upload?name=photo.jpg HTTP/1.1\r\nContent-Length: 0\r\nContent-Type: image/jpeg\r\ncontent-type: image/png\r\n\r\n")
        assertTrue(duplicate.second.startsWith("HTTP/1.1 400 Bad Request\r\n"))
    }

    @Test fun wrongMethodOnUploadRouteAdvertisesPutOnly() {
        val (exchange, response) = read("GET /upload HTTP/1.1\r\n\r\n")
        assertNull(exchange)
        assertTrue(response.contains("Allow: PUT\r\n"))
    }
    @Test fun getWithPositiveContentLengthRemainsRejected() {
        val (_, response) = read("GET /download HTTP/1.1\r\nContent-Length: 1\r\n\r\n")
        assertTrue(response.startsWith("HTTP/1.1 400 Bad Request\r\n"))
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

    @Test fun duplicateRangeHeadersAreRejectedEvenWhenFirstValueIsEmpty() {
        val (exchange, response) = read(
            "GET /download HTTP/1.1\r\nRange:\r\nrange: bytes=10-19\r\n\r\n"
        )
        assertNull(exchange)
        assertTrue(response.startsWith("HTTP/1.1 400 Bad Request\r\n"))
    }

    @Test fun bareLfLineEndingIsRejected() {
        val (exchange, response) = read("GET /download HTTP/1.1\n\n")
        assertNull(exchange)
        assertTrue(response.startsWith("HTTP/1.1 400 Bad Request\r\n"))
    }

    @Test fun embeddedCarriageReturnInHeaderIsRejected() {
        val (exchange, response) = read("GET /download HTTP/1.1\r\nX-Test: one\rtwo\r\n\r\n")
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


    @Test fun duplicateHostHeadersAreRejectedCaseInsensitively() {
        val (exchange, response) = read(
            "GET /download HTTP/1.1\r\nHost: phone-a\r\nhost: phone-b\r\n\r\n"
        )
        assertNull(exchange)
        assertTrue(response.startsWith("HTTP/1.1 400 Bad Request\r\n"))
    }

    @Test fun emptyHostHeaderIsRejected() {
        val (exchange, response) = read("GET /download HTTP/1.1\r\nHost:   \r\n\r\n")
        assertNull(exchange)
        assertTrue(response.startsWith("HTTP/1.1 400 Bad Request\r\n"))
    }

    @Test fun hostHeaderWithWhitespaceInsideAuthorityIsRejected() {
        val (exchange, response) = read("GET /download HTTP/1.1\r\nHost: phone name\r\n\r\n")
        assertNull(exchange)
        assertTrue(response.startsWith("HTTP/1.1 400 Bad Request\r\n"))
    }

    @Test fun commaSeparatedHostAuthoritiesAreRejected() {
        val (exchange, response) = read("GET /download HTTP/1.1\r\nHost: phone-a, phone-b\r\n\r\n")
        assertNull(exchange)
        assertTrue(response.startsWith("HTTP/1.1 400 Bad Request\r\n"))
    }

    @Test fun http11RequestWithoutHostIsRejected() {
        val (exchange, response) = read("GET /download HTTP/1.1\r\n\r\n", addDefaultHost = false)
        assertNull(exchange)
        assertTrue(response.startsWith("HTTP/1.1 400 Bad Request\r\n"))
    }

    @Test fun http10RequestMayOmitHostHeader() {
        val (exchange, response) = read("GET /download HTTP/1.0\r\n\r\n", addDefaultHost = false)
        assertNotNull(exchange)
        assertTrue(response.isEmpty())
    }

    @Test fun oneNonEmptyHostHeaderIsAccepted() {
        val (exchange, response) = read("GET /download HTTP/1.1\r\nHost: 192.168.43.1:8765\r\n\r\n")
        assertNotNull(exchange)
        assertTrue(response.isEmpty())
    }


    @Test fun explicitZeroContentLengthIsAccepted() {
        val (exchange, response) = read("GET /download HTTP/1.1\r\nContent-Length: 0\r\n\r\n")
        assertNotNull(exchange)
        assertTrue(response.isEmpty())
    }

    @Test fun duplicateContentLengthIsRejected() {
        val (exchange, response) = read(
            "GET /download HTTP/1.1\r\nContent-Length: 0\r\ncontent-length: 0\r\n\r\n"
        )
        assertNull(exchange)
        assertTrue(response.startsWith("HTTP/1.1 400 Bad Request\r\n"))
    }

    @Test fun nonzeroContentLengthIsRejected() {
        val (exchange, response) = read("GET /download HTTP/1.1\r\nContent-Length: 1\r\n\r\n")
        assertNull(exchange)
        assertTrue(response.startsWith("HTTP/1.1 400 Bad Request\r\n"))
    }

    @Test fun malformedContentLengthIsRejected() {
        val (exchange, response) = read("GET /download HTTP/1.1\r\nContent-Length: +0\r\n\r\n")
        assertNull(exchange)
        assertTrue(response.startsWith("HTTP/1.1 400 Bad Request\r\n"))
    }

    @Test fun overflowingContentLengthIsRejected() {
        val (exchange, response) = read(
            "GET /download HTTP/1.1\r\nContent-Length: 999999999999999999999999\r\n\r\n"
        )
        assertNull(exchange)
        assertTrue(response.startsWith("HTTP/1.1 400 Bad Request\r\n"))
    }

    @Test fun transferEncodingIsRejected() {
        val (exchange, response) = read(
            "GET /download HTTP/1.1\r\nTransfer-Encoding: chunked\r\n\r\n"
        )
        assertNull(exchange)
        assertTrue(response.startsWith("HTTP/1.1 400 Bad Request\r\n"))
    }

    @Test fun rangeHeaderOuterWhitespaceIsTrimmedBeforeResolution() {
        val (exchange, response) = read(
            "GET /download HTTP/1.1\r\nRange:   bytes=10-19  \r\n\r\n"
        )
        assertNotNull(exchange)
        assertEquals("bytes=10-19", exchange!!.rangeHeader)
        assertTrue(response.isEmpty())
    }

    @Test fun ordinaryHeadersMayAppearBeforeOrAfterRangeHeader() {
        val (exchange, response) = read(
            "GET /download HTTP/1.1\r\nX-Device: phone\r\nRange: bytes=2-4\r\nAccept: */*\r\n\r\n"
        )
        assertNotNull(exchange)
        assertEquals("bytes=2-4", exchange!!.rangeHeader)
        assertTrue(response.isEmpty())
    }

    @Test fun emptyNonRangeHeaderValueIsAccepted() {
        val (exchange, response) = read("GET /download HTTP/1.1\r\nX-Optional:\r\n\r\n")
        assertNotNull(exchange)
        assertTrue(response.isEmpty())
    }

    @Test fun nonAsciiHostAuthorityIsRejected() {
        val (exchange, response) = read("GET /download HTTP/1.1\r\nHost: telé.local\r\n\r\n")
        assertNull(exchange)
        assertTrue(response.startsWith("HTTP/1.1 400 Bad Request\r\n"))
    }

    @Test fun hostAuthorityContainingUrlPathDelimiterIsRejected() {
        val (exchange, response) = read("GET /download HTTP/1.1\r\nHost: phone.local/path\r\n\r\n")
        assertNull(exchange)
        assertTrue(response.startsWith("HTTP/1.1 400 Bad Request\r\n"))
    }

    @Test fun ifRangeIsPreservedForSourceAwareValidation() {
        val (exchange, response) = read(
            "GET /download HTTP/1.1\r\nHost: test.local\r\nRange: bytes=10-19\r\nIf-Range: Wed, 21 Oct 2015 07:28:00 GMT\r\n\r\n"
        )

        assertTrue(response.isEmpty())
        assertNotNull(exchange)
        assertEquals("bytes=10-19", exchange!!.rangeHeader)
        assertEquals("Wed, 21 Oct 2015 07:28:00 GMT", exchange.ifRangeHeader)
    }

    @Test fun ifRangeWithoutAValidatorIsNotSilentlyConvertedIntoAParserDecision() {
        val (exchange, response) = read(
            "GET /download HTTP/1.1\r\nHost: test.local\r\nRange: bytes=10-19\r\nIf-Range: \"stale-validator\"\r\n\r\n"
        )

        assertTrue(response.isEmpty())
        assertNotNull(exchange)
        assertEquals("bytes=10-19", exchange!!.rangeHeader)
        assertEquals("\"stale-validator\"", exchange.ifRangeHeader)
    }

    @Test fun duplicateIfRangeHeadersAreRejectedAsAmbiguous() {
        val (exchange, response) = read(
            "GET /download HTTP/1.1\r\nRange: bytes=10-19\r\nIf-Range: \"a\"\r\nIf-Range: \"b\"\r\n\r\n"
        )

        assertNull(exchange)
        assertTrue(response.startsWith("HTTP/1.1 400"))
    }

}
