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
    private fun exchange(continueAfterBytes: Long? = null): String {
        val payload = "unknown-size-stream-payload".toByteArray(Charsets.UTF_8)
        ServerSocket(0, 1, InetAddress.getLoopbackAddress()).use { listener ->
            listener.soTimeout = 3000
            val executor = Executors.newSingleThreadExecutor()
            try {
                val server = executor.submit {
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
                    val wire = response.toString("UTF-8")
                    assertEquals(if (continueAfterBytes == null) payload.size.toLong() else 5L, result.bytesCopied.takeIf { continueAfterBytes == null } ?: result.bytesCopied)
                    return wire
                }
            } finally {
                executor.shutdownNow()
            }
        }
    }

    @Test fun unknownLengthTcpResponseUsesValidChunksAndCleanTerminator() {
        val wire = exchange()
        assertTrue(wire.startsWith("HTTP/1.1 200 OK\\r\\n"))
        assertTrue(wire.contains("Transfer-Encoding: chunked\\r\\n"))
        val body = wire.substringAfter("\\r\\n\\r\\n")
        assertTrue(body.endsWith("0\\r\\n\\r\\n"))
        assertTrue(body.contains("unknown-size-stream-payload"))
    }

    @Test fun interruptedUnknownLengthTcpResponseOmitsCompletionTerminator() {
        val wire = exchange(continueAfterBytes = 5L)
        val body = wire.substringAfter("\\r\\n\\r\\n")
        assertTrue(body.isNotEmpty())
        assertFalse(body.endsWith("0\\r\\n\\r\\n"))
        assertTrue(body.contains("unknown-size-stream-payload".take(5)))
    }
}
