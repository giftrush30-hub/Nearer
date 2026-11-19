package com.nearer.app

import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.EOFException
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.net.Socket
import java.nio.file.Files
import java.security.MessageDigest
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean

/** Framed TCP file engine for a socket created by a separate Wi-Fi Direct coordinator. */
internal class WifiDirectTcpTransferEngine(
    private val bufferBytes: Int = DEFAULT_BUFFER_BYTES,
    private val socketReadTimeoutMillis: Int = DEFAULT_SOCKET_READ_TIMEOUT_MILLIS,
    private val socketBufferBytes: Int = DEFAULT_SOCKET_BUFFER_BYTES
) {
    init {
        require(bufferBytes in MIN_BUFFER_BYTES..MAX_BUFFER_BYTES)
        require(socketReadTimeoutMillis > 0) { "Socket read timeout must be positive" }
        require(socketBufferBytes in MIN_SOCKET_BUFFER_BYTES..MAX_SOCKET_BUFFER_BYTES)
    }

    /**
     * Request a larger TCP window for high-bandwidth local links.
     * Android/OEM kernels may clamp these hints; inspect effective values on-device.
     * Keep Nagle enabled for bulk streaming; interactive chat stays on Nearby.
     */
    private fun configureBulkSocket(socket: Socket) {
        socket.setSoTimeout(socketReadTimeoutMillis)
        socket.setSendBufferSize(socketBufferBytes)
        socket.setReceiveBufferSize(socketBufferBytes)
        socket.tcpNoDelay = false
        socket.keepAlive = true
    }

    data class Progress(val transferredBytes: Long, val totalBytes: Long) {
        val fraction: Double get() = if (totalBytes == 0L) 1.0 else transferredBytes.toDouble() / totalBytes
    }
    fun interface ProgressListener { fun onProgress(progress: Progress) }

    class Cancellation {
        private val cancelled = AtomicBoolean(false)
        @Volatile private var socket: Socket? = null
        val isCancelled: Boolean get() = cancelled.get()
        internal fun attach(value: Socket) { socket = value; if (cancelled.get()) close() }
        fun cancel() { cancelled.set(true); close() }
        internal fun check() { if (cancelled.get()) throw IOException("Transfer cancelled") }
        internal fun detach() { socket = null }
        private fun close() { try { socket?.close() } catch (_: IOException) { } }
    }
    data class ReceiveResult(val file: File, val bytesReceived: Long, val sha256: String)

    /** Frame: magic, version, transfer ID, byte count, bytes, SHA-256 trailer, receiver ACK. */
    fun send(socket: Socket, source: File, transferId: String, cancellation: Cancellation = Cancellation(), listener: ProgressListener = ProgressListener { }): String {
        require(transferId.isNotBlank()) { "Transfer ID cannot be blank" }
        require(source.isFile && source.canRead()) { "Source file is not readable" }
        val id = transferId.toByteArray(Charsets.UTF_8)
        require(id.size in 1..MAX_TRANSFER_ID_BYTES) { "Transfer ID is too long" }
        cancellation.attach(socket)
        try {
            configureBulkSocket(socket)
            cancellation.check()
            val size = source.length()
            val input = DataInputStream(BufferedInputStream(socket.getInputStream(), bufferBytes))
            val output = DataOutputStream(BufferedOutputStream(socket.getOutputStream(), bufferBytes))
            output.writeInt(MAGIC); output.writeByte(VERSION); output.writeShort(id.size); output.write(id); output.writeLong(size); output.flush()
            val digest = MessageDigest.getInstance("SHA-256")
            var sent = 0L
            FileInputStream(source).use { fileInput ->
                val buffer = ByteArray(bufferBytes)
                while (true) {
                    cancellation.check()
                    val count = fileInput.read(buffer)
                    if (count < 0) break
                    if (count == 0) continue
                    output.write(buffer, 0, count); digest.update(buffer, 0, count); sent += count
                    listener.onProgress(Progress(sent, size))
                }
            }
            if (sent != size) throw IOException("Source file changed during transfer")
            val hash = digest.digest()
            output.write(hash); output.flush(); cancellation.check()
            return when (val ack = input.readUnsignedByte()) {
                ACK -> hex(hash)
                NACK -> throw IOException("Receiver rejected integrity verification or file finalization")
                else -> throw IOException("Invalid receiver acknowledgement: $ack")
            }
        } catch (error: Exception) {
            if (error is IOException) throw error
            throw IOException("TCP send failed", error)
        } finally { cancellation.detach(); try { socket.close() } catch (_: IOException) { } }
    }

    fun receive(socket: Socket, destination: File, expectedTransferId: String, cancellation: Cancellation = Cancellation(), listener: ProgressListener = ProgressListener { }): ReceiveResult {
        require(expectedTransferId.isNotBlank()) { "Expected transfer ID cannot be blank" }
        val parent = destination.absoluteFile.parentFile ?: throw IOException("Destination must have a parent directory")
        if (!parent.exists() && !parent.mkdirs()) throw IOException("Could not create destination directory")
        if (destination.exists()) throw IOException("Destination already exists")
        cancellation.attach(socket)
        configureBulkSocket(socket)
        val part = File(parent, ".${destination.name}.${UUID.randomUUID()}.part")
        var published = false
        try {
            cancellation.check()
            val input = DataInputStream(BufferedInputStream(socket.getInputStream(), bufferBytes))
            val output = DataOutputStream(BufferedOutputStream(socket.getOutputStream(), bufferBytes))
            if (input.readInt() != MAGIC) throw IOException("Unrecognized transfer frame")
            if (input.readUnsignedByte() != VERSION) throw IOException("Unsupported protocol version")
            val idLength = input.readUnsignedShort()
            if (idLength !in 1..MAX_TRANSFER_ID_BYTES) throw IOException("Invalid transfer ID length")
            val id = ByteArray(idLength); input.readFully(id)
            if (String(id, Charsets.UTF_8) != expectedTransferId) throw IOException("Transfer ID mismatch")
            val size = input.readLong()
            if (size < 0L) throw IOException("Invalid negative payload size")
            if (size > parent.usableSpace) throw IOException("Not enough free storage")
            if (!part.createNewFile()) throw IOException("Could not create temporary transfer file")
            val digest = MessageDigest.getInstance("SHA-256")
            var received = 0L
            FileOutputStream(part).use { fileOutput ->
                BufferedOutputStream(fileOutput, bufferBytes).use { buffered ->
                    val buffer = ByteArray(bufferBytes)
                    while (received < size) {
                        cancellation.check()
                        val wanted = minOf(buffer.size.toLong(), size - received).toInt()
                        val count = input.read(buffer, 0, wanted)
                        if (count < 0) throw EOFException("Connection ended before file completed")
                        if (count == 0) continue
                        buffered.write(buffer, 0, count); digest.update(buffer, 0, count); received += count
                        listener.onProgress(Progress(received, size))
                    }
                }
            }
            val expectedHash = ByteArray(SHA256_BYTES); input.readFully(expectedHash)
            val actualHash = digest.digest()
            if (!MessageDigest.isEqual(expectedHash, actualHash)) { output.writeByte(NACK); output.flush(); throw IOException("SHA-256 verification failed") }
            if (destination.exists()) { output.writeByte(NACK); output.flush(); throw IOException("Destination appeared during transfer") }
            // Do not request REPLACE_EXISTING or ATOMIC_MOVE: on some providers an
            // atomic move may replace a destination created after the earlier
            // existence check. The default move contract fails if the target exists.
            Files.move(part.toPath(), destination.toPath())
            published = true
            output.writeByte(ACK); output.flush()
            return ReceiveResult(destination, received, hex(actualHash))
        } catch (error: Exception) {
            if (error is IOException) throw error
            throw IOException("TCP receive failed", error)
        } finally {
            cancellation.detach(); if (!published) part.delete()
            try { socket.close() } catch (_: IOException) { }
        }
    }


    /**
     * Encrypted variant. Caller must complete pinned-identity authentication and
     * user approval before invoking this method. Header metadata, file chunks,
     * digest and final acknowledgement are all carried inside AEAD records.
     */
    fun sendSecure(
        socket: Socket,
        source: File,
        transferId: String,
        channel: HyperTransferSecureChannel,
        cancellation: Cancellation = Cancellation(),
        listener: ProgressListener = ProgressListener { }
    ): String {
        require(transferId.matches(Regex("^[a-fA-F0-9]{64}$"))) { "Invalid transfer ID" }
        require(source.isFile && source.canRead()) { "Source file is not readable" }
        cancellation.attach(socket)
        try {
            configureBulkSocket(socket)
            cancellation.check()
            val size = source.length()
            val input = DataInputStream(BufferedInputStream(socket.getInputStream(), bufferBytes))
            val output = DataOutputStream(BufferedOutputStream(socket.getOutputStream(), bufferBytes))
            output.writeInt(SECURE_MAGIC)
            output.writeByte(SECURE_VERSION)
            output.flush()

            val metadata = java.nio.ByteBuffer.allocate(8 + 32)
                .putLong(size)
                .put(transferId.lowercase().chunked(2).map { it.toInt(16).toByte() }.toByteArray())
                .array()
            channel.writeRecord(output, metadata)
            val digest = MessageDigest.getInstance("SHA-256")
            var sent = 0L
            FileInputStream(source).use { fileInput ->
                val buffer = ByteArray(minOf(bufferBytes, HyperTransferSecureChannel.MAX_PLAINTEXT_BYTES))
                while (true) {
                    cancellation.check()
                    val count = fileInput.read(buffer)
                    if (count < 0) break
                    if (count == 0) continue
                    channel.writeRecord(output, buffer, 0, count)
                    digest.update(buffer, 0, count)
                    sent += count
                    listener.onProgress(Progress(sent, size))
                }
            }
            if (sent != size) throw IOException("Source file changed during transfer")
            val hash = digest.digest()
            channel.writeRecord(output, hash)
            output.flush()
            cancellation.check()
            val ack = channel.readRecord(input)
            if (!ack.contentEquals(ACK_BYTES)) throw IOException("Receiver rejected encrypted transfer finalization")
            return hex(hash)
        } catch (error: Exception) {
            if (error is IOException) throw error
            throw IOException("Encrypted TCP send failed", error)
        } finally {
            cancellation.detach()
            try { socket.close() } catch (_: IOException) { }
            channel.close()
        }
    }

    /** Receives only AEAD-authenticated metadata/data and publishes after digest + encrypted ACK. */
    fun receiveSecure(
        socket: Socket,
        destination: File,
        expectedTransferId: String,
        channel: HyperTransferSecureChannel,
        cancellation: Cancellation = Cancellation(),
        listener: ProgressListener = ProgressListener { },
        expectedSha256: String? = null,
        expectedSizeBytes: Long? = null
    ): ReceiveResult {
        require(expectedTransferId.matches(Regex("^[a-fA-F0-9]{64}$"))) { "Invalid expected transfer ID" }
        require(expectedSha256 == null || expectedSha256.matches(Regex("^[a-fA-F0-9]{64}$"))) {
            "Invalid expected SHA-256"
        }
        require(expectedSizeBytes == null || expectedSizeBytes >= 0L) {
            "Invalid expected transfer size"
        }
        val parent = destination.absoluteFile.parentFile ?: throw IOException("Destination must have a parent directory")
        if (!parent.exists() && !parent.mkdirs()) throw IOException("Could not create destination directory")
        if (destination.exists()) throw IOException("Destination already exists")
        cancellation.attach(socket)
        configureBulkSocket(socket)
        val part = File(parent, ".${destination.name}.${UUID.randomUUID()}.part")
        var published = false
        try {
            cancellation.check()
            val input = DataInputStream(BufferedInputStream(socket.getInputStream(), bufferBytes))
            val output = DataOutputStream(BufferedOutputStream(socket.getOutputStream(), bufferBytes))
            if (input.readInt() != SECURE_MAGIC || input.readUnsignedByte() != SECURE_VERSION) {
                throw IOException("Unrecognized encrypted HyperTransfer stream")
            }
            val metadata = channel.readRecord(input)
            if (metadata.size != 40) throw IOException("Invalid encrypted transfer metadata")
            val meta = java.nio.ByteBuffer.wrap(metadata)
            val size = meta.long
            if (size < 0L) throw IOException("Invalid negative payload size")
            if (expectedSizeBytes != null && size != expectedSizeBytes) {
                throw IOException("Encrypted transfer size did not match the approved HyperTransfer offer")
            }
            val transferBytes = ByteArray(32).also(meta::get)
            val expectedBytes = expectedTransferId.lowercase().chunked(2).map { it.toInt(16).toByte() }.toByteArray()
            if (!MessageDigest.isEqual(transferBytes, expectedBytes)) throw IOException("Authenticated transfer ID mismatch")
            if (size > parent.usableSpace) throw IOException("Not enough free storage")
            if (!part.createNewFile()) throw IOException("Could not create temporary transfer file")

            val digest = MessageDigest.getInstance("SHA-256")
            var received = 0L
            FileOutputStream(part).use { fileOutput ->
                BufferedOutputStream(fileOutput, bufferBytes).use { buffered ->
                    while (received < size) {
                        cancellation.check()
                        val record = channel.readRecord(input)
                        if (record.isEmpty() || record.size.toLong() > size - received) {
                            throw IOException("Invalid encrypted file chunk length")
                        }
                        buffered.write(record)
                        digest.update(record)
                        received += record.size
                        listener.onProgress(Progress(received, size))
                    }
                }
            }
            val receivedHash = channel.readRecord(input)
            if (receivedHash.size != SHA256_BYTES) throw IOException("Invalid encrypted integrity record")
            val actualHash = digest.digest()
            if (!MessageDigest.isEqual(receivedHash, actualHash)) throw IOException("Encrypted transfer integrity verification failed")
            if (expectedSha256 != null) {
                val offeredHash = expectedSha256.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
                if (!MessageDigest.isEqual(offeredHash, actualHash)) {
                    throw IOException("Received file did not match the approved HyperTransfer offer")
                }
            }
            if (destination.exists()) throw IOException("Destination appeared during transfer")
            Files.move(part.toPath(), destination.toPath())
            published = true
            channel.writeRecord(output, ACK_BYTES)
            output.flush()
            return ReceiveResult(destination, received, hex(actualHash))
        } catch (error: Exception) {
            if (error is IOException) throw error
            throw IOException("Encrypted TCP receive failed", error)
        } finally {
            cancellation.detach()
            if (!published) part.delete()
            try { socket.close() } catch (_: IOException) { }
            channel.close()
        }
    }

    private fun hex(bytes: ByteArray): String = bytes.joinToString("") { "%02x".format(it) }
    companion object {
        const val DEFAULT_BUFFER_BYTES = 256 * 1024
        const val MIN_BUFFER_BYTES = 8 * 1024
        const val MAX_BUFFER_BYTES = 1024 * 1024
        const val DEFAULT_SOCKET_BUFFER_BYTES = 4 * 1024 * 1024
        const val MIN_SOCKET_BUFFER_BYTES = 64 * 1024
        const val MAX_SOCKET_BUFFER_BYTES = 16 * 1024 * 1024
        const val DEFAULT_SOCKET_READ_TIMEOUT_MILLIS = 30_000
        private const val SECURE_MAGIC = 0x4E525345
        private const val SECURE_VERSION = 1
        private val ACK_BYTES = byteArrayOf(0x06)
        private const val MAGIC = 0x4E525450
        private const val VERSION = 1
        private const val MAX_TRANSFER_ID_BYTES = 128
        private const val SHA256_BYTES = 32
        private const val ACK = 0x06
        private const val NACK = 0x15
    }
}