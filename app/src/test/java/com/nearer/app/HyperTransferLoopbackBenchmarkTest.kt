package com.nearer.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.net.ServerSocket
import java.net.Socket
import java.security.MessageDigest
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * CPU/JVM loopback smoke benchmark for the exact encrypted bulk data plane.
 * These figures validate pipeline behavior only; loopback is not Wi-Fi Direct
 * and must never be presented as phone-to-phone or radio throughput.
 */
class HyperTransferLoopbackBenchmarkTest {
    @Test
    fun encryptedLargeFileTransfersVerifyInBothDirections() {
        val root = java.nio.file.Files.createTempDirectory("nearer-hypertransfer-bench").toFile()
        val source = File(root, "benchmark-source.bin")
        val bytes = 32L * 1024L * 1024L
        source.outputStream().buffered(1024 * 1024).use { output ->
            val buffer = ByteArray(1024 * 1024)
            var written = 0L
            while (written < bytes) {
                val count = minOf(buffer.size.toLong(), bytes - written).toInt()
                for (index in 0 until count) buffer[index] = ((written + index) % 251L).toByte()
                output.write(buffer, 0, count)
                written += count
            }
        }
        val digest = sha256(source)
        try {
            val forward = runSecureLoopback(root, source, "a".repeat(64), "23456789ABCD", "EFGHJKMNPQRS")
            val reverse = runSecureLoopback(root, source, "b".repeat(64), "EFGHJKMNPQRS", "23456789ABCD")

            assertEquals(bytes, forward.first)
            assertEquals(bytes, reverse.first)
            assertEquals(digest, forward.second)
            assertEquals(digest, reverse.second)
            assertTrue(forward.third > 0.0)
            assertTrue(reverse.third > 0.0)

            System.out.println(
                "HyperTransfer encrypted loopback smoke: 32 MiB A->B %.2f MB/s, B->A %.2f MB/s. " +
                    "Not a Wi-Fi Direct benchmark.".format(
                        java.util.Locale.US, forward.third, reverse.third
                    )
            )
        } finally {
            root.deleteRecursively()
        }
    }

    private fun runSecureLoopback(
        root: File,
        source: File,
        transferId: String,
        senderId: String,
        receiverId: String
    ): Triple<Long, String, Double> {
        val server = ServerSocket(0)
        val executor = Executors.newSingleThreadExecutor()
        val transcript = MessageDigest.getInstance("SHA-256")
            .digest(("loopback-" + transferId).toByteArray())
        val senderKeys = NearbyMessageCrypto.newEphemeralKeyPair()
        val receiverKeys = NearbyMessageCrypto.newEphemeralKeyPair()
        val senderChannel = HyperTransferSecureChannel.establish(
            senderKeys.private, receiverKeys.public, transcript, transferId,
            senderId, receiverId, true
        )
        val receiverChannel = HyperTransferSecureChannel.establish(
            receiverKeys.private, senderKeys.public, transcript, transferId,
            senderId, receiverId, false
        )
        val destination = File(root, "received-$senderId.bin")
        val engine = WifiDirectTcpTransferEngine(
            bufferBytes = 1024 * 1024,
            socketReadTimeoutMillis = 120_000,
            socketBufferBytes = 8 * 1024 * 1024
        )
        try {
            val received = executor.submit<WifiDirectTcpTransferEngine.ReceiveResult> {
                server.accept().use {
                    engine.receiveSecure(it, destination, transferId, receiverChannel)
                }
            }
            val started = System.nanoTime()
            Socket("127.0.0.1", server.localPort).use {
                engine.sendSecure(it, source, transferId, senderChannel)
            }
            val result = received.get(120, TimeUnit.SECONDS)
            val elapsedSeconds = (System.nanoTime() - started) / 1_000_000_000.0
            val megabytesPerSecond = source.length() / elapsedSeconds / 1_000_000.0
            assertEquals(source.length(), result.bytesReceived)
            assertEquals(sha256(source), result.sha256)
            assertEquals(sha256(source), sha256(destination))
            return Triple(result.bytesReceived, result.sha256, megabytesPerSecond)
        } finally {
            server.close()
            executor.shutdownNow()
            senderChannel.close()
            receiverChannel.close()
        }
    }

    private fun sha256(file: File): String =
        file.inputStream().buffered(1024 * 1024).use { input ->
            val digest = MessageDigest.getInstance("SHA-256")
            val buffer = ByteArray(1024 * 1024)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                if (count > 0) digest.update(buffer, 0, count)
            }
            digest.digest().joinToString("") { "%02x".format(it) }
        }
}
