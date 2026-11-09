package com.nearer.app

import org.junit.Assert.*
import org.junit.Test

class FastShareHttpRequestTest {
    @Test fun getDownloadWithQuerySelectsDownloadRoute() {
        val request = FastShareHttpRequest.parse("GET /download?source=phone HTTP/1.1")
        assertNotNull(request)
        assertEquals("GET", request!!.method)
        assertEquals("/download", request.path)
        assertEquals("source=phone", request.query)
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

    @Test fun downloadAllRouteIsRecognizedForGetAndHead() {
        val get = FastShareHttpRequest.parse("GET /download-all?key=secret HTTP/1.1")!!
        val head = FastShareHttpRequest.parse("HEAD /download-all HTTP/1.1")!!
        assertEquals(FastShareHttpRequest.Route.DOWNLOAD_ALL, get.route)
        assertEquals(FastShareHttpRequest.Route.DOWNLOAD_ALL, head.route)
        assertTrue(head.isHeadOnly)
    }
    @Test fun putUploadRouteAndEncodedFileNameAreRecognized() {
        val request = FastShareHttpRequest.parse("PUT /upload?key=abc&name=holiday%20video%2B1.mp4 HTTP/1.1")!!
        assertEquals(FastShareHttpRequest.Route.UPLOAD, request.route)
        assertTrue(request.supportsMethod)
        assertEquals("holiday video+1.mp4", request.queryParameter("name"))
    }

    @Test fun uploadNameMustAppearExactlyOnceAndDecodeSafely() {
        val duplicate = FastShareHttpRequest.parse("PUT /upload?name=one&name=two HTTP/1.1")!!
        val malformed = FastShareHttpRequest.parse("PUT /upload?name=%ZZ HTTP/1.1")!!
        assertNull(duplicate.queryParameter("name"))
        assertNull(malformed.queryParameter("name"))
    }

    @Test fun uploadRouteAcceptsOnlyPut() {
        val get = FastShareHttpRequest.parse("GET /upload HTTP/1.1")!!
        val putDownload = FastShareHttpRequest.parse("PUT /download HTTP/1.1")!!
        assertFalse(get.supportsMethod)
        assertFalse(putDownload.supportsMethod)
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


    @Test fun indexedDownloadQueryDoesNotChangeSelectedFileIndex() {
        val request = FastShareHttpRequest.parse("GET /download/12?name=holiday.mp4 HTTP/1.1")!!
        assertEquals(FastShareHttpRequest.Route.DOWNLOAD, request.route)
        assertEquals(12, request.downloadIndex)
        assertEquals("/download/12", request.path)
    }

    @Test fun encodedAndNestedDownloadPathsDoNotMatchDownloadRoute() {
        listOf("/download%2F1", "/download/1/../2", "/download//1").forEach { path ->
            val request = FastShareHttpRequest.parse("GET $path HTTP/1.1")!!
            assertEquals(FastShareHttpRequest.Route.NOT_FOUND, request.route)
        }
    }

    @Test fun requestTargetContainingControlCharactersIsRejected() {
        assertNull(FastShareHttpRequest.parse("GET /download\u0001bad HTTP/1.1"))
        assertNull(FastShareHttpRequest.parse("GET /download\u007f HTTP/1.1"))
    }

    @Test fun accessKeyMustAppearExactlyOnceInQuery() {
        val key = "a".repeat(64)
        val valid = FastShareHttpRequest.parse("GET /?key=$key HTTP/1.1")!!
        val missing = FastShareHttpRequest.parse("GET / HTTP/1.1")!!
        val duplicate = FastShareHttpRequest.parse("GET /?key=$key&key=$key HTTP/1.1")!!
        val wrong = FastShareHttpRequest.parse("GET /?key=${"b".repeat(64)} HTTP/1.1")!!
        assertTrue(valid.hasAccessKey(key))
        assertFalse(missing.hasAccessKey(key))
        assertFalse(duplicate.hasAccessKey(key))
        assertFalse(wrong.hasAccessKey(key))
    }

    @Test fun accessKeyMayAppearAlongsideOtherQueryParameters() {
        val key = "c".repeat(64)
        val request = FastShareHttpRequest.parse("GET /download/4?name=clip.mp4&key=" + key + "&source=nearer HTTP/1.1")!!
        assertEquals(4, request.downloadIndex)
        assertTrue(request.hasAccessKey(key))
    }

    @Test fun accessKeyCannotBeSmuggledThroughSimilarParameterNames() {
        val key = "d".repeat(64)
        val request = FastShareHttpRequest.parse("GET /?Key=" + key + "&access_key=" + key + " HTTP/1.1")!!
        assertFalse(request.hasAccessKey(key))
    }

    @Test fun malformedAndRepeatedAccessKeyParametersAreRejected() {
        val key = "e".repeat(64)
        listOf(
            "key=",
            "key=" + key + "x",
            "key=" + key + "&key=",
            "key=" + key + "&key=" + key,
            "key=" + key.uppercase(java.util.Locale.ROOT)
        ).forEach { query ->
            val request = FastShareHttpRequest.parse("GET /?" + query + " HTTP/1.1")!!
            assertFalse("Query should not authenticate: " + query, request.hasAccessKey(key))
        }
    }

    @Test fun generatedAccessKeysHaveTheExpectedHighEntropyWireFormat() {
        val first = FastShareAccessKey.create()
        val second = FastShareAccessKey.create()

        assertEquals(64, first.length)
        assertTrue(first.matches(Regex("[0-9a-f]{64}")))
        assertNotEquals(first, second)
    }

    @Test fun accessKeyComparisonRejectsNullWrongLengthAndNonHexValues() {
        val key = FastShareAccessKey.create()

        assertTrue(FastShareAccessKey.matches(key, key))
        assertFalse(FastShareAccessKey.matches(key, null))
        assertFalse(FastShareAccessKey.matches(key, ""))
        assertFalse(FastShareAccessKey.matches(key, key.dropLast(1)))
        assertFalse(FastShareAccessKey.matches(key, key + "0"))
        assertFalse(FastShareAccessKey.matches(key, "z".repeat(64)))
        assertFalse(FastShareAccessKey.matches(key, key.uppercase(java.util.Locale.ROOT)))
    }

}
