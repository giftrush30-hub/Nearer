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

    @Test fun configuresAHighBandwidthSocketWindowWithinSafeBounds() {
        val tuned = WifiDirectTcpTransferEngine(
            bufferBytes = 1024 * 1024,
            socketBufferBytes = 4 * 1024 * 1024
        )
        assertNotNull(tuned)

        try {
            WifiDirectTcpTransferEngine(socketBufferBytes = 32 * 1024)
            fail("Socket buffer below the supported minimum must be rejected")
        } catch (_: IllegalArgumentException) { }

        try {
            WifiDirectTcpTransferEngine(socketBufferBytes = 32 * 1024 * 1024)
            fail("Socket buffer above the supported maximum must be rejected")
        } catch (_: IllegalArgumentException) { }
    }

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
                // The receiver must reject as soon as it reads the mismatched ID.
                // Do not race that early close by writing a payload the receiver
                // is required never to consume.
                out.writeInt(0x4E525450); out.writeByte(1); out.writeShort(wrong.size)
                out.write(wrong); out.flush()
                val rejection = future.get(5, TimeUnit.SECONDS)
                assertTrue(rejection is java.io.IOException)
                assertEquals("Transfer ID mismatch", rejection?.message)
            }
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

    @Test fun receiveTimesOutWhenPeerStallsBeforeHeaderAndCleansUp() {
        val root = java.nio.file.Files.createTempDirectory("nearer-tcp-timeout").toFile()
        val server = ServerSocket(0)
        val executor = Executors.newSingleThreadExecutor()
        try {
            val destination = File(root, "stalled.bin")
            val shortTimeoutEngine = WifiDirectTcpTransferEngine(
                bufferBytes = 16 * 1024,
                socketReadTimeoutMillis = 150
            )
            val future = executor.submit<Throwable?> {
                try {
                    server.accept().use { shortTimeoutEngine.receive(it, destination, "stalled-session") }
                    null
                } catch (error: Throwable) {
                    error
                }
            }
            Socket("127.0.0.1", server.localPort).use {
                val error = future.get(5, TimeUnit.SECONDS)
                assertTrue(error is java.net.SocketTimeoutException)
            }
            assertFalse(destination.exists())
            assertTrue(root.listFiles().orEmpty().none { it.name.endsWith(".part") })
        } finally {
            server.close()
            executor.shutdownNow()
            root.deleteRecursively()
        }
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


    @Test fun encryptedTcpTransferPublishesOnlyAuthenticatedFileBytes() {
        val root = java.nio.file.Files.createTempDirectory("nearer-tcp-secure").toFile()
        val server = ServerSocket(0)
        val executor = Executors.newSingleThreadExecutor()
        val id = "a".repeat(64)
        val transcript = MessageDigest.getInstance("SHA-256").digest("trusted-session-transcript".toByteArray())
        val senderKeys = NearbyMessageCrypto.newEphemeralKeyPair()
        val receiverKeys = NearbyMessageCrypto.newEphemeralKeyPair()
        val senderChannel = HyperTransferSecureChannel.establish(
            senderKeys.private, receiverKeys.public, transcript, id,
            "23456789ABCD", "EFGHJKMNPQRS", true
        )
        val receiverChannel = HyperTransferSecureChannel.establish(
            receiverKeys.private, senderKeys.public, transcript, id,
            "23456789ABCD", "EFGHJKMNPQRS", false
        )
        try {
            val source = File(root, "secure-source.bin").apply {
                writeBytes(ByteArray(768 * 1024) { (it % 239).toByte() })
            }
            val destination = File(root, "secure-received.bin")
            val future = executor.submit<WifiDirectTcpTransferEngine.ReceiveResult> {
                server.accept().use { engine.receiveSecure(it, destination, id, receiverChannel) }
            }
            Socket("127.0.0.1", server.localPort).use {
                assertEquals(sha256(source), engine.sendSecure(it, source, id, senderChannel))
            }
            val result = future.get(10, TimeUnit.SECONDS)
            assertEquals(source.length(), result.bytesReceived)
            assertArrayEquals(source.readBytes(), destination.readBytes())
            assertEquals(sha256(source), result.sha256)
        } finally {
            server.close()
            executor.shutdownNow()
            root.deleteRecursively()
        }
    }


    @Test fun secureReceiveRejectsSizeThatDiffersFromTheApprovedOfferBeforeCreatingTempFile() {
        val root = java.nio.file.Files.createTempDirectory("nearer-tcp-offer-size").toFile()
        val server = ServerSocket(0)
        val executor = Executors.newSingleThreadExecutor()
        val id = "e".repeat(64)
        val senderKeys = NearbyMessageCrypto.newEphemeralKeyPair()
        val receiverKeys = NearbyMessageCrypto.newEphemeralKeyPair()
        val transcript = MessageDigest.getInstance("SHA-256").digest("offer-bound-size".toByteArray())
        val senderChannel = HyperTransferSecureChannel.establish(
            senderKeys.private, receiverKeys.public, transcript, id,
            "23456789ABCD", "EFGHJKMNPQRS", true
        )
        val receiverChannel = HyperTransferSecureChannel.establish(
            receiverKeys.private, senderKeys.public, transcript, id,
            "23456789ABCD", "EFGHJKMNPQRS", false
        )
        try {
            val source = File(root, "source.bin").apply { writeBytes("approved bytes".toByteArray()) }
            val destination = File(root, "must-not-publish.bin")
            val receive = executor.submit<Throwable?> {
                try {
                    server.accept().use {
                        engine.receiveSecure(
                            it, destination, id, receiverChannel,
                            expectedSha256 = sha256(source),
                            expectedSizeBytes = source.length() + 1L
                        )
                    }
                    null
                } catch (error: Throwable) { error }
            }
            try {
                Socket("127.0.0.1", server.localPort).use {
                    WifiDirectTcpTransferEngine(socketReadTimeoutMillis = 200)
                        .sendSecure(it, source, id, senderChannel)
                }
            } catch (_: Exception) { }
            val error = receive.get(5, TimeUnit.SECONDS)
            assertTrue(error is java.io.IOException)
            assertTrue(error?.message.orEmpty().contains("size did not match the approved HyperTransfer offer"))
            assertFalse(destination.exists())
            assertTrue(root.listFiles().orEmpty().none { it.name.endsWith(".part") })
        } finally {
            server.close()
            executor.shutdownNow()
            senderChannel.close()
            root.deleteRecursively()
        }
    }

    @Test fun secureReceiveRejectsBytesThatDoNotMatchTheApprovedOfferHash() {
        val root = java.nio.file.Files.createTempDirectory("nearer-tcp-offer-hash").toFile()
        val server = ServerSocket(0)
        val executor = Executors.newSingleThreadExecutor()
        val id = "d".repeat(64)
        val senderKeys = NearbyMessageCrypto.newEphemeralKeyPair()
        val receiverKeys = NearbyMessageCrypto.newEphemeralKeyPair()
        val transcript = MessageDigest.getInstance("SHA-256").digest("offer-bound-session".toByteArray())
        val senderChannel = HyperTransferSecureChannel.establish(
            senderKeys.private, receiverKeys.public, transcript, id,
            "23456789ABCD", "EFGHJKMNPQRS", true
        )
        val receiverChannel = HyperTransferSecureChannel.establish(
            receiverKeys.private, senderKeys.public, transcript, id,
            "23456789ABCD", "EFGHJKMNPQRS", false
        )
        try {
            val source = File(root, "source.bin").apply { writeBytes("approved bytes".toByteArray()) }
            val destination = File(root, "must-not-publish.bin")
            val receive = executor.submit<Throwable?> {
                try {
                    server.accept().use {
                        engine.receiveSecure(
                            it, destination, id, receiverChannel,
                            expectedSha256 = "0".repeat(64)
                        )
                    }
                    null
                } catch (error: Throwable) { error }
            }
            try {
                Socket("127.0.0.1", server.localPort).use {
                    WifiDirectTcpTransferEngine(socketReadTimeoutMillis = 200)
                        .sendSecure(it, source, id, senderChannel)
                }
            } catch (_: Exception) { }
            val error = receive.get(5, TimeUnit.SECONDS)
            assertTrue(error is java.io.IOException)
            assertTrue(error?.message.orEmpty().contains("approved HyperTransfer offer"))
            assertFalse(destination.exists())
            assertTrue(root.listFiles().orEmpty().none { it.name.endsWith(".part") })
        } finally {
            server.close()
            executor.shutdownNow()
            root.deleteRecursively()
        }
    }

    @Test fun secureSenderTimesOutWhenReceiverStallsBeforeAck() {
        val root = java.nio.file.Files.createTempDirectory("nearer-tcp-secure-timeout").toFile()
        val server = ServerSocket(0)
        val executor = Executors.newSingleThreadExecutor()
        val peerReady = java.util.concurrent.CountDownLatch(1)
        val releasePeer = java.util.concurrent.CountDownLatch(1)
        val id = "b".repeat(64)
        val senderKeys = NearbyMessageCrypto.newEphemeralKeyPair()
        val receiverKeys = NearbyMessageCrypto.newEphemeralKeyPair()
        val senderChannel = HyperTransferSecureChannel.establish(
            senderKeys.private, receiverKeys.public,
            MessageDigest.getInstance("SHA-256").digest("timeout-session".toByteArray()),
            id, "23456789ABCD", "EFGHJKMNPQRS", true
        )
        val shortTimeoutEngine = WifiDirectTcpTransferEngine(16 * 1024, 150)
        try {
            val source = File(root, "empty.bin").apply { writeBytes(byteArrayOf()) }
            val peer = executor.submit {
                server.accept().use { socket ->
                    val input = java.io.DataInputStream(socket.getInputStream())
                    assertEquals(0x4E525345, input.readInt())
                    assertEquals(1, input.readUnsignedByte())
                    // Empty files contain an authenticated manifest and digest record.
                    repeat(2) {
                        val length = input.readInt()
                        assertTrue(length in HyperTransferSecureChannel.MIN_ENCRYPTED_RECORD_BYTES..
                            HyperTransferSecureChannel.MAX_ENCRYPTED_RECORD_BYTES)
                        val record = ByteArray(length)
                        input.readFully(record)
                    }
                    peerReady.countDown()
                    releasePeer.await(2, TimeUnit.SECONDS)
                }
            }
            val error = try {
                Socket("127.0.0.1", server.localPort).use { socket ->
                    shortTimeoutEngine.sendSecure(socket, source, id, senderChannel)
                }
                null
            } catch (caught: Throwable) { caught }
            assertTrue("Expected an idle ACK read to time out, got $error",
                error is java.net.SocketTimeoutException)
            assertTrue(peerReady.await(2, TimeUnit.SECONDS))
            peer.get(3, TimeUnit.SECONDS)
        } finally {
            releasePeer.countDown()
            server.close()
            executor.shutdownNow()
            senderChannel.close()
            root.deleteRecursively()
        }
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
