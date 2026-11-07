package com.nearer.app

import org.junit.Assert.*
import org.junit.Test

class FastShareHttpRequestTest {
    @Test fun getDownloadWithQuerySelectsDownloadRoute() {
        val request = FastShareHttpRequest.parse("GET /download?source=phone HTTP/1.1")
        assertNotNull(request)
        assertEquals("GET", request!!.method)
        assertEquals("/download", request.path)
        assertEquals(FastShareHttpRequest.Route.DOWNLOAD, request.route)
        assertTrue(request.supportsMethod)
        assertFalse(request.isHeadOnly)
    }

    @Test fun headDownloadUsesSameRouteWithoutRequestingABody() {
        val request = FastShareHttpRequest.parse("HEAD /download HTTP/1.1")
        assertNotNull(request)
        assertEquals(FastShareHttpRequest.Route.DOWNLOAD, request!!.route)
        assertTrue(request.supportsMethod)
        assertTrue(request.isHeadOnly)
    }

    @Test fun receiverPageAndIndexAliasAreRecognized() {
        assertEquals(
            FastShareHttpRequest.Route.RECEIVER_PAGE,
            FastShareHttpRequest.parse("GET / HTTP/1.1")!!.route
        )
        assertEquals(
            FastShareHttpRequest.Route.RECEIVER_PAGE,
            FastShareHttpRequest.parse("GET /index.html HTTP/1.0")!!.route
        )
    }

    @Test fun unsupportedMethodIsParsedButNotAccepted() {
        val request = FastShareHttpRequest.parse("POST /download HTTP/1.1")
        assertNotNull(request)
        assertFalse(request!!.supportsMethod)
        assertEquals(FastShareHttpRequest.Route.DOWNLOAD, request.route)
    }

    @Test fun unknownPathSelectsNotFoundRoute() {
        assertEquals(
            FastShareHttpRequest.Route.NOT_FOUND,
            FastShareHttpRequest.parse("GET /missing HTTP/1.1")!!.route
        )
    }

    @Test fun malformedRequestLinesAreRejected() {
        assertNull(FastShareHttpRequest.parse("GET /download"))
        assertNull(FastShareHttpRequest.parse("GET /download HTTP/2.0"))
        assertNull(FastShareHttpRequest.parse("GET download HTTP/1.1"))
        assertNull(FastShareHttpRequest.parse("GET  /download HTTP/1.1 extra"))
    }

    @Test fun methodMatchingIsCaseInsensitive() {
        assertEquals("GET", FastShareHttpRequest.parse("get /download HTTP/1.1")!!.method)
    }
    @Test fun indexedDownloadRoutesExposeZeroBasedIndex() {
        val first = FastShareHttpRequest.parse("GET /download/0 HTTP/1.1")!!
        val third = FastShareHttpRequest.parse("HEAD /download/2?name=clip HTTP/1.1")!!
        assertEquals(FastShareHttpRequest.Route.DOWNLOAD, first.route)
        assertEquals(0, first.downloadIndex)
        assertEquals(FastShareHttpRequest.Route.DOWNLOAD, third.route)
        assertEquals(2, third.downloadIndex)
        assertTrue(third.isHeadOnly)
    }

    @Test fun malformedIndexedDownloadPathsAreNotDownloadRoutes() {
        listOf("/download/-1", "/download/nope", "/download/1/extra", "/download/12345").forEach { path ->
            val request = FastShareHttpRequest.parse("GET $path HTTP/1.1")!!
            assertEquals(FastShareHttpRequest.Route.NOT_FOUND, request.route)
        }
    }

}
