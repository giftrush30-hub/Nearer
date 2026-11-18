package com.nearer.app

import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.net.ServerSocket
import java.net.Socket
import java.security.MessageDigest
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class WifiDirectTcpTransferEngineTest {
    private val engine = WifiDirectTcpTransferEngine(16 * 1024)

    @Test fun transfersAndVerifiesLargeFileBeforePublishing() {
        val root = java.nio.file.Files.createTempDirectory("nearer-tcp-test").toFile()
        try {
            val source = File(root, "source.bin").apply { writeBytes(ByteArray(512 * 1024) { (it % 251).toByte() }) }
            val destination = File(root, "received.bin")
            val result = runTransfer(source, destination, "session-123")
            assertEquals(source.length(), result.bytesReceived)
            assertArrayEquals(source.readBytes(), destination.readBytes())
            assertEquals(sha256(source), result.sha256)
        } finally { root.deleteRecursively() }
    }

    @Test fun supportsEmptyFiles() {
        val root = java.nio.file.Files.createTempDirectory("nearer-tcp-empty").toFile()
        try {
            val source = File(root, "empty.bin").apply { writeBytes(byteArrayOf()) }
            val destination = File(root, "received-empty.bin")
            val result = runTransfer(source, destination, "empty-session")
            assertEquals(0L, result.bytesReceived)
            assertTrue(destination.exists())
            assertEquals(sha256(source), result.sha256)
        } finally { root.deleteRecursively() }
    }

    @Test fun rejectsWrongTransferIdWithoutPublishingPartialFile() {
        val root = java.nio.file.Files.createTempDirectory("nearer-tcp-wrong-id").toFile()
        val server = ServerSocket(0)
        val executor = Executors.newSingleThreadExecutor()
        try {
            val destination = File(root, "must-not-exist.bin")
            val future = executor.submit<Throwable?> {
                try { server.accept().use { engine.receive(it, destination, "expected-id") }; null }
                catch (error: Throwable) { error }
            }
            Socket("127.0.0.1", server.localPort).use { socket ->
                val out = java.io.DataOutputStream(socket.getOutputStream())
                val wrong = "wrong-id".toByteArray()
                out.writeInt(0x4E525450); out.writeByte(1); out.writeShort(wrong.size)
                out.write(wrong); out.writeLong(3L); out.write(byteArrayOf(1, 2, 3))
                out.write(ByteArray(32)); out.flush()
            }
            assertNotNull(future.get(5, TimeUnit.SECONDS))
            assertFalse(destination.exists())
            assertTrue(root.listFiles().orEmpty().none { it.name.endsWith(".part") })
        } finally { server.close(); executor.shutdownNow(); root.deleteRecursively() }
    }

    @Test fun rejectsCorruptedPayloadWithoutPublishingFile() {
        val root = java.nio.file.Files.createTempDirectory("nearer-tcp-corrupt").toFile()
        val server = ServerSocket(0)
        val executor = Executors.newSingleThreadExecutor()
        try {
            val destination = File(root, "corrupted.bin")
            val future = executor.submit<Throwable?> {
                try { server.accept().use { engine.receive(it, destination, "valid-id") }; null }
                catch (error: Throwable) { error }
            }
            Socket("127.0.0.1", server.localPort).use { socket ->
                val out = java.io.DataOutputStream(socket.getOutputStream())
                val id = "valid-id".toByteArray()
                out.writeInt(0x4E525450); out.writeByte(1); out.writeShort(id.size); out.write(id)
                out.writeLong(3L); out.write(byteArrayOf(1, 2, 3)); out.write(ByteArray(32)); out.flush()
                assertEquals(0x15, java.io.DataInputStream(socket.getInputStream()).readUnsignedByte())
            }
            assertNotNull(future.get(5, TimeUnit.SECONDS))
            assertFalse(destination.exists())
            assertTrue(root.listFiles().orEmpty().none { it.name.endsWith(".part") })
        } finally { server.close(); executor.shutdownNow(); root.deleteRecursively() }
    }

    @Test fun refusesToOverwriteExistingDestination() {
        val root = java.nio.file.Files.createTempDirectory("nearer-tcp-overwrite").toFile()
        try {
            val source = File(root, "source.bin").apply { writeText("new") }
            val destination = File(root, "existing.bin").apply { writeText("keep") }
            try {
                runTransfer(source, destination, "overwrite-session")
                fail("Expected destination conflict")
            } catch (_: Exception) { }
            assertEquals("keep", destination.readText())
        } finally { root.deleteRecursively() }
    }

    private fun runTransfer(source: File, destination: File, id: String): WifiDirectTcpTransferEngine.ReceiveResult {
        val server = ServerSocket(0)
        val executor = Executors.newSingleThreadExecutor()
        try {
            val received = executor.submit<WifiDirectTcpTransferEngine.ReceiveResult> {
                server.accept().use { engine.receive(it, destination, id) }
            }
            Socket("127.0.0.1", server.localPort).use { engine.send(it, source, id) }
            return received.get(10, TimeUnit.SECONDS)
        } finally { server.close(); executor.shutdownNow() }
    }

    private fun sha256(file: File): String =
        MessageDigest.getInstance("SHA-256").digest(file.readBytes())
            .joinToString("") { "%02x".format(it) }
}
