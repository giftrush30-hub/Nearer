package com.nearer.app

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import org.junit.Assert.*
import org.junit.Test

/** Exercises unknown-length chunked file bodies across a real loopback TCP socket. */
class FastShareChunkedSocketTest {
    private data class WireResult(val response: String, val complete: Boolean, val bytesCopied: Long)

    private fun exchange(continueAfterBytes: Long? = null): WireResult {
        val payload = "unknown-size-stream-payload".toByteArray(Charsets.UTF_8)
        ServerSocket(0, 1, InetAddress.getLoopbackAddress()).use { listener ->
            listener.soTimeout = 3000
            val executor = Executors.newSingleThreadExecutor()
            try {
                val server = executor.submit<ChunkedCopyResult> {
                    listener.accept().use { peer ->
                        peer.soTimeout = 3000
                        val parsed = FastShareHttpSession.readRequest(peer.getInputStream(), peer.getOutputStream())
                        checkNotNull(parsed)
                        val output = peer.getOutputStream()
                        output.write(
                            "HTTP/1.1 200 OK\r\nTransfer-Encoding: chunked\r\nConnection: close\r\n\r\n"
                                .toByteArray(Charsets.US_ASCII)
                        )
                        var copied = 0L
                        val result = ChunkedStreamCopy.copy(
                            input = ByteArrayInputStream(payload),
                            output = output,
                            shouldContinue = { continueAfterBytes == null || copied < continueAfterBytes },
                            onBytesCopied = { copied = it },
                            bufferSize = 5
                        )
                        output.flush()
                        result
                    }
                }
                Socket(InetAddress.getLoopbackAddress(), listener.localPort).use { client ->
                    client.soTimeout = 3000
                    client.getOutputStream().apply {
                        write("GET /download HTTP/1.1\r\nHost: localhost\r\n\r\n".toByteArray(Charsets.US_ASCII))
                        flush()
                        client.shutdownOutput()
                    }
                    val response = ByteArrayOutputStream()
                    client.getInputStream().copyTo(response)
                    val result = server.get(3, TimeUnit.SECONDS)
                    return WireResult(response.toString("UTF-8"), result.complete, result.bytesCopied)
                }
            } finally {
                executor.shutdownNow()
            }
        }
    }

    private fun decodeChunks(body: String, allowMissingTerminator: Boolean = false): ByteArray {
        val output = ByteArrayOutputStream()
        var cursor = 0
        while (cursor < body.length) {
            val lineEnd = body.indexOf("\r\n", cursor)
            require(lineEnd >= 0) { "Missing chunk-size line terminator" }
            val size = body.substring(cursor, lineEnd).toInt(16)
            cursor = lineEnd + 2
            if (size == 0) {
                require(body.substring(cursor) == "\r\n") { "Invalid final chunk terminator" }
                return output.toByteArray()
            }
            require(cursor + size + 2 <= body.length) { "Truncated chunk data" }
            output.write(body.substring(cursor, cursor + size).toByteArray(Charsets.ISO_8859_1))
            cursor += size
            require(body.substring(cursor, cursor + 2) == "\r\n") { "Missing chunk data terminator" }
            cursor += 2
        }
        require(allowMissingTerminator) { "Missing final chunk terminator" }
        return output.toByteArray()
    }

    @Test fun unknownLengthTcpResponseUsesValidChunksAndCleanTerminator() {
        val result = exchange()
        val wire = result.response
        assertTrue(result.complete)
        assertEquals("unknown-size-stream-payload".length.toLong(), result.bytesCopied)
        assertTrue(wire.startsWith("HTTP/1.1 200 OK\r\n"))
        assertTrue(wire.contains("Transfer-Encoding: chunked\r\n"))
        val body = wire.substringAfter("\r\n\r\n")
        assertTrue(body.endsWith("0\r\n\r\n"))
        assertArrayEquals("unknown-size-stream-payload".toByteArray(Charsets.UTF_8), decodeChunks(body))
    }

    @Test fun interruptedUnknownLengthTcpResponseOmitsCompletionTerminator() {
        val result = exchange(continueAfterBytes = 5L)
        val body = result.response.substringAfter("\r\n\r\n")
        assertFalse(result.complete)
        assertEquals(5L, result.bytesCopied)
        assertFalse(body.endsWith("0\r\n\r\n"))
        assertEquals("unkno", decodeChunks(body, allowMissingTerminator = true).toString(Charsets.UTF_8))
    }
}
