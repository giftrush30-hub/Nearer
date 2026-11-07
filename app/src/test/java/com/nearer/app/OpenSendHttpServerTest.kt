package com.nearer.app

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

class OpenSendHttpServerTest {
    @Test
    fun landingPageRequiresSessionTokenAndListsSharedFile() {
        val folder = createTempDir(prefix = "nearer-opensend-test")
        val file = File(folder, "lecture.pdf").apply { writeText("sample lecture") }
        val server = OpenSendHttpServer(
            listOf(OpenSendFile(id = "lecture", displayName = "Lecture.pdf", file = file)),
            accessToken = "a".repeat(32)
        )
        try {
            val info = server.start()
            val denied = request(info.port, "/")
            assertEquals(403, denied.first)
            val allowed = request(info.port, server.landingPath())
            assertEquals(200, allowed.first)
            assertTrue(String(allowed.second).contains("Lecture.pdf"))
            assertTrue(String(allowed.second).contains("No NeaRer installation is required"))
        } finally {
            server.close()
            folder.deleteRecursively()
        }
    }

    @Test
    fun downloadSupportsByteRangeForResume() {
        val folder = createTempDir(prefix = "nearer-opensend-range")
        val bytes = "0123456789abcdefghijklmnopqrstuvwxyz".toByteArray()
        val file = File(folder, "sample.bin").apply { writeBytes(bytes) }
        val server = OpenSendHttpServer(
            listOf(OpenSendFile(id = "sample", displayName = "sample.bin", file = file)),
            accessToken = "b".repeat(32)
        )
        try {
            val info = server.start()
            val connection = URL("http://127.0.0.1:${info.port}/download/sample?token=${info.accessToken}")
                .openConnection() as HttpURLConnection
            connection.setRequestProperty("Range", "bytes=4-10")
            assertEquals(206, connection.responseCode)
            assertEquals("bytes 4-10/${bytes.size}", connection.getHeaderField("Content-Range"))
            assertArrayEquals(bytes.copyOfRange(4, 11), connection.inputStream.use { it.readBytes() })
            connection.disconnect()
        } finally {
            server.close()
            folder.deleteRecursively()
        }
    }

    @Test
    fun suffixRangeReturnsLastBytesAndUnsatisfiableRangeReturns416() {
        val folder = createTempDir(prefix = "nearer-opensend-suffix")
        val bytes = "abcdefghij".toByteArray()
        val file = File(folder, "sample.bin").apply { writeBytes(bytes) }
        val server = OpenSendHttpServer(
            listOf(OpenSendFile(id = "sample", displayName = "sample.bin", file = file)),
            accessToken = "c".repeat(32)
        )
        try {
            val info = server.start()
            val suffix = URL("http://127.0.0.1:${info.port}/download/sample?token=${info.accessToken}")
                .openConnection() as HttpURLConnection
            suffix.setRequestProperty("Range", "bytes=-4")
            assertEquals(206, suffix.responseCode)
            assertEquals("bytes 6-9/10", suffix.getHeaderField("Content-Range"))
            assertArrayEquals("ghij".toByteArray(), suffix.inputStream.use { it.readBytes() })
            suffix.disconnect()

            val invalid = URL("http://127.0.0.1:${info.port}/download/sample?token=${info.accessToken}")
                .openConnection() as HttpURLConnection
            invalid.setRequestProperty("Range", "bytes=20-30")
            assertEquals(416, invalid.responseCode)
            assertEquals("bytes */10", invalid.getHeaderField("Content-Range"))
            invalid.disconnect()
        } finally {
            server.close()
            folder.deleteRecursively()
        }
    }

    @Test
    fun headRequestReturnsFileHeadersWithoutBody() {
        val folder = createTempDir(prefix = "nearer-opensend-head")
        val file = File(folder, "sample.txt").apply { writeText("hello world") }
        val server = OpenSendHttpServer(
            listOf(OpenSendFile(id = "head", displayName = "sample.txt", file = file, mimeType = "text/plain")),
            accessToken = "d".repeat(32)
        )
        try {
            val info = server.start()
            val connection = URL("http://127.0.0.1:${info.port}/download/head?token=${info.accessToken}")
                .openConnection() as HttpURLConnection
            connection.requestMethod = "HEAD"
            assertEquals(200, connection.responseCode)
            assertEquals("11", connection.getHeaderField("Content-Length"))
            assertEquals("bytes", connection.getHeaderField("Accept-Ranges"))
            assertEquals(0, connection.inputStream.use { it.readBytes().size })
            connection.disconnect()
        } finally {
            server.close()
            folder.deleteRecursively()
        }
    }

    private fun request(port: Int, path: String): Pair<Int, ByteArray> {
        val connection = URL("http://127.0.0.1:$port$path").openConnection() as HttpURLConnection
        return try {
            val status = connection.responseCode
            val stream = if (status >= 400) connection.errorStream else connection.inputStream
            status to (stream?.use { it.readBytes() } ?: ByteArray(0))
        } finally {
            connection.disconnect()
        }
    }
}
