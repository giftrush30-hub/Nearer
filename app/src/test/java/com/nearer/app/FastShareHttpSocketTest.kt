package com.nearer.app

import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/** Exercises the real TCP stream boundary used by Fast Share's HTTP request parser. */
class FastShareHttpSocketTest {
    private val payload = "0123456789".toByteArray(Charsets.UTF_8)

    private fun exchange(vararg requestParts: String): String {
        ServerSocket(0, 1, InetAddress.getLoopbackAddress()).use { listener ->
            listener.soTimeout = 3000
            val executor = Executors.newSingleThreadExecutor()
            try {
                val serverResult = executor.submit<String> {
                    listener.accept().use { peer ->
                        peer.soTimeout = 3000
                        val output = peer.getOutputStream()
                        val parsed = FastShareHttpSession.readRequest(peer.getInputStream(), output)
                        if (parsed != null) {
                            val response = FileHttpResponses.resolve(parsed.rangeHeader, payload.size.toLong())
                            val headers = buildString {
                                append("HTTP/1.1 ").append(response.statusCode).append(' ')
                                    .append(response.statusText).append("\r\n")
                                append("Accept-Ranges: bytes\r\n")
                                append("Content-Length: ").append(response.contentLength).append("\r\n")
                                response.contentRange?.let { append("Content-Range: ").append(it).append("\r\n") }
                                append("Connection: close\r\n\r\n")
                            }
                            output.write(headers.toByteArray(Charsets.UTF_8))
                            if (!parsed.request.isHeadOnly && response.range != null) {
                                val start = response.range.start.toInt()
                                val endExclusive = (response.range.endInclusive + 1).toInt()
                                if (endExclusive > start) {
                                    StreamCopy.copy(
                                        input = ByteArrayInputStream(payload, start, endExclusive - start),
                                        output = output,
                                        expectedBytes = (endExclusive - start).toLong()
                                    )
                                }
                            }
                            output.flush()
                        }
                    }
                    "closed"
                }

                Socket(InetAddress.getLoopbackAddress(), listener.localPort).use { client ->
                    client.soTimeout = 3000
                    val requestOutput = client.getOutputStream()
                    requestParts.forEach {
                        requestOutput.write(it.toByteArray(Charsets.UTF_8))
                        requestOutput.flush()
                    }
                    client.shutdownOutput()
                    val response = ByteArrayOutputStream()
                    client.getInputStream().copyTo(response)
                    assertEquals("closed", serverResult.get(3, TimeUnit.SECONDS))
                    return response.toString("UTF-8")
                }
            } finally {
                executor.shutdownNow()
            }
        }
    }

    @Test fun fragmentedTcpRequestReturnsExactResumedFileSection() {
        val response = exchange(
            "GET /download HTTP/1.1\r\nHo",
            "st: localhost\r\nRange: bytes=2-5\r\n",
            "\r\n"
        )
        assertTrue(response.startsWith("HTTP/1.1 206 Partial Content\r\n"))
        assertTrue(response.contains("Content-Range: bytes 2-5/10\r\n"))
        assertTrue(response.endsWith("\r\n\r\n2345"))
    }

    @Test fun interruptedHeaderConnectionReturnsBadRequest() {
        val response = exchange("GET /download HTTP/1.1\r\nHost: phone")
        assertTrue(response.startsWith("HTTP/1.1 400 Bad Request\r\n"))
    }

    @Test fun duplicateRangeHeadersOverTcpAreRejected() {
        val response = exchange(
            "GET /download HTTP/1.1\r\nHost: localhost\r\nRange: bytes=0-2\r\nrange: bytes=3-5\r\n\r\n"
        )
        assertTrue(response.startsWith("HTTP/1.1 400 Bad Request\r\n"))
    }

    @Test fun unsatisfiableRangeReturns416WithUnsatisfiedContentRange() {
        val response = exchange("GET /download HTTP/1.1\r\nHost: localhost\r\nRange: bytes=99-120\r\n\r\n")
        assertTrue(response.startsWith("HTTP/1.1 416 Range Not Satisfiable\r\n"))
        assertTrue(response.contains("Content-Length: 0\r\n"))
        assertTrue(response.contains("Accept-Ranges: bytes\r\n"))
        assertTrue(response.contains("Content-Range: bytes */10\r\n"))
        assertTrue(response.endsWith("\r\n\r\n"))
    }


    @Test fun nonzeroContentLengthIsRejectedOverTcp() {
        val response = exchange(
            "GET /download HTTP/1.1\r\nHost: localhost\r\nContent-Length: 1\r\n\r\nx"
        )
        assertTrue(response.startsWith("HTTP/1.1 400 Bad Request\r\n"))
    }

    @Test fun transferEncodingIsRejectedOverTcp() {
        val response = exchange(
            "GET /download HTTP/1.1\r\nHost: localhost\r\nTransfer-Encoding: chunked\r\n\r\n0\r\n\r\n"
        )
        assertTrue(response.startsWith("HTTP/1.1 400 Bad Request\r\n"))
    }

    @Test fun zeroContentLengthIsAcceptedOverTcp() {
        val response = exchange(
            "GET /download HTTP/1.1\r\nHost: localhost\r\nContent-Length: 0\r\n\r\n"
        )
        assertTrue(response.startsWith("HTTP/1.1 200 OK\r\n"))
        assertTrue(response.endsWith("\r\n\r\n0123456789"))
    }

    @Test fun headRequestReturnsMetadataWithoutFileBody() {
        val response = exchange("HEAD /download HTTP/1.1\r\nHost: localhost\r\n\r\n")
        assertTrue(response.startsWith("HTTP/1.1 200 OK\r\n"))
        assertTrue(response.contains("Content-Length: 10\r\n"))
        assertTrue(response.endsWith("\r\n\r\n"))
    }

    @Test fun headRequestWithRangeReturnsWholeFileMetadataWithoutBody() {
        val response = exchange(
            "HEAD /download HTTP/1.1\r\nHost: localhost\r\nRange: bytes=4-7\r\n\r\n"
        )
        assertTrue(response.startsWith("HTTP/1.1 200 OK\r\n"))
        assertTrue(response.contains("Content-Length: 10\r\n"))
        assertFalse(response.contains("Content-Range:"))
        assertTrue(response.endsWith("\r\n\r\n"))
    }
}
