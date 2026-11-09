package com.nearer.app

import org.junit.Assert.*
import org.junit.Test

class FastShareHttpRequestStrictLineTest {
    @Test fun acceptsCanonicalHttpRequestLine() {
        val request = FastShareHttpRequest.parse("GET /download/2?key=abc HTTP/1.1")
        assertNotNull(request)
        assertEquals("GET", request!!.method)
        assertEquals("/download/2", request.path)
        assertEquals("key=abc", request.query)
    }

    @Test fun rejectsLeadingWhitespace() {
        assertNull(FastShareHttpRequest.parse(" GET /download HTTP/1.1"))
    }

    @Test fun rejectsTrailingWhitespace() {
        assertNull(FastShareHttpRequest.parse("GET /download HTTP/1.1 "))
    }

    @Test fun rejectsTabSeparators() {
        assertNull(FastShareHttpRequest.parse("GET\t/download\tHTTP/1.1"))
    }

    @Test fun rejectsCollapsedMultipleSpaces() {
        assertNull(FastShareHttpRequest.parse("GET  /download HTTP/1.1"))
        assertNull(FastShareHttpRequest.parse("GET /download  HTTP/1.1"))
    }

    @Test fun rejectsRequestTargetFragments() {
        assertNull(FastShareHttpRequest.parse("GET /download#fragment HTTP/1.1"))
    }

    @Test fun rejectsNonAsciiRequestTargetCharacters() {
        assertNull(FastShareHttpRequest.parse("GET /downlοad HTTP/1.1"))
    }

    @Test fun rejectsAbsoluteFormRequestTargets() {
        assertNull(FastShareHttpRequest.parse("GET http://phone/download HTTP/1.1"))
    }
}
