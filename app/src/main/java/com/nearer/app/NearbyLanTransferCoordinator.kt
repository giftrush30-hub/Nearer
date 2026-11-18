package com.nearer.app

import java.io.File
import java.io.IOException
import java.util.ArrayDeque
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit

/**
 * Optional authenticated LAN transfer protocol router. Metadata is surfaced for
 * explicit user approval before the initial ACK is sent; file bytes cannot start
 * until that ACK reaches the sender.
 */
internal class NearbyLanTransferCoordinator(
    private val destinationDirectory: File,
    private val isPeerAuthenticated: (String) -> Boolean,
    private val sendFrame: (String, ByteArray, (Throwable?) -> Unit) -> Unit,
    private val listener: Listener,
    private val ackTimeoutSeconds: Long = 30L
) : AutoCloseable {
    interface Listener {
        fun onIncomingTransferRequest(endpointId: String, metadata: TransferResumeStreamMetadataCodec.Metadata)
        fun onFileReceived(endpointId: String, metadata: TransferResumeStreamMetadataCodec.Metadata, file: File)
        fun onTransferError(endpointId: String, message: String)
    }

    private data class TransferKey(val endpointId: String, val payloadId: Long)
    private data class AckKey(val endpointId: String, val payloadId: Long, val transferId: String, val itemId: String)
    private data class PendingAck(
        val expected: NearbyTransferFileChunkCodec.Ack,
        val completion: (Result<NearbyTransferFileChunkCodec.Ack>) -> Unit,
        val timeout: ScheduledFuture<*>
    )

    private val pendingMetadata = ConcurrentHashMap<TransferKey, TransferResumeStreamMetadataCodec.Metadata>()
    private val receivers = ConcurrentHashMap<TransferKey, NearbyLanFileReceiver>()
    private val ackLock = Any()
    private val pendingAcks = HashMap<AckKey, PendingAck>()
    private val queuedAcks = HashMap<AckKey, ArrayDeque<NearbyTransferFileChunkCodec.Ack>>()
    private val scheduler = Executors.newSingleThreadScheduledExecutor()
    @Volatile private var closed = false

    fun receiveFrame(endpointId: String, frame: ByteArray): Boolean {
        if (closed || !isPeerAuthenticated(endpointId) || frame.size < 4) return false
        return try {
            when (readMagic(frame)) {
                0x4E52534D -> receiveMetadata(endpointId, frame)
                0x4E524643 -> receiveChunk(endpointId, frame)
                0x4E524645 -> receiveEnd(endpointId, frame)
                0x4E524641 -> receiveAck(endpointId, frame)
                else -> false
            }
        } catch (error: Exception) {
            listener.onTransferError(endpointId, error.message ?: "Nearby file transfer protocol error")
            false
        }
    }

    /** Call only after the user explicitly accepts the incoming file request. */
    fun approveIncoming(endpointId: String, payloadId: Long): Boolean {
        val key = TransferKey(endpointId, payloadId)
        val metadata = pendingMetadata[key] ?: return false
        return try {
            val receiver = NearbyLanFileReceiver(metadata, destinationDirectory)
            receivers[key] = receiver
            pendingMetadata.remove(key, metadata)
            sendFrame(endpointId, NearbyTransferFileChunkCodec.encodeAck(receiver.initialAck())) { error ->
                if (error != null) {
                    receivers.remove(key)?.close()
                    listener.onTransferError(endpointId, "Could not acknowledge accepted transfer: ${error.message}")
                }
            }
            true
        } catch (error: Exception) {
            listener.onTransferError(endpointId, error.message ?: "Could not prepare incoming file")
            false
        }
    }

    fun rejectIncoming(endpointId: String, payloadId: Long) {
        pendingMetadata.remove(TransferKey(endpointId, payloadId))
    }

    fun ackWaiter(endpointId: String): NearbyLanAckWaiter =
        NearbyLanAckWaiter { expected, completion ->
            awaitAck(endpointId, expected, completion)
        }

    /** Waits for one exact ACK; unmatched early ACKs are safely queued by transfer key. */
    fun awaitAck(endpointId: String, expected: NearbyTransferFileChunkCodec.Ack,
                 completion: (Result<NearbyTransferFileChunkCodec.Ack>) -> Unit) {
        val key = AckKey(endpointId, expected.payloadId, expected.transferId, expected.itemId)
        var immediate: NearbyTransferFileChunkCodec.Ack? = null
        var duplicateWaiter = false
        synchronized(ackLock) {
            val queue = queuedAcks[key]
            if (queue != null && queue.isNotEmpty()) {
                immediate = queue.removeFirst()
                if (queue.isEmpty()) queuedAcks.remove(key)
            } else if (pendingAcks.containsKey(key)) {
                duplicateWaiter = true
            } else {
                val timeout = scheduler.schedule({
                    expireAck(key, expected)
                }, ackTimeoutSeconds, TimeUnit.SECONDS)
                pendingAcks[key] = PendingAck(expected, completion, timeout)
            }
        }
        if (immediate != null) {
            if (immediate == expected) completion(Result.success(immediate!!))
            else completion(Result.failure(IOException("Received ACK did not match expected prefix")))
        } else if (duplicateWaiter) {
            completion(Result.failure(IOException("An ACK is already pending for this transfer")))
        }
    }

    private fun receiveMetadata(endpointId: String, frame: ByteArray): Boolean {
        val metadata = TransferResumeStreamMetadataCodec.decode(frame)
        val key = TransferKey(endpointId, metadata.payloadId)
        if (pendingMetadata.putIfAbsent(key, metadata) != null || receivers.containsKey(key)) {
            throw IOException("Duplicate incoming transfer metadata")
        }
        listener.onIncomingTransferRequest(endpointId, metadata)
        return true
    }

    private fun receiveChunk(endpointId: String, frame: ByteArray): Boolean {
        val chunk = NearbyTransferFileChunkCodec.decodeChunk(frame)
        val key = TransferKey(endpointId, chunk.payloadId)
        val receiver = receivers[key] ?: throw IOException("File bytes arrived before transfer approval")
        val ack = receiver.append(chunk)
        if (ack != null) sendFrame(endpointId, NearbyTransferFileChunkCodec.encodeAck(ack)) { error ->
            if (error != null) listener.onTransferError(endpointId, "Checkpoint ACK failed: ${error.message}")
        }
        return true
    }

    private fun receiveEnd(endpointId: String, frame: ByteArray): Boolean {
        val end = NearbyTransferFileChunkCodec.decodeEnd(frame)
        val key = TransferKey(endpointId, end.payloadId)
        val receiver = receivers[key] ?: throw IOException("Transfer end arrived without an approved receiver")
        val finalized = receiver.finish(end)
        receivers.remove(key, receiver)
        sendFrame(endpointId, NearbyTransferFileChunkCodec.encodeAck(finalized.finalAck)) { error ->
            if (error != null) listener.onTransferError(endpointId, "Final transfer ACK failed: ${error.message}")
            else listener.onFileReceived(endpointId, receiver.metadata, finalized.file)
        }
        return true
    }

    private fun receiveAck(endpointId: String, frame: ByteArray): Boolean {
        val ack = NearbyTransferFileChunkCodec.decodeAck(frame)
        val key = AckKey(endpointId, ack.payloadId, ack.transferId, ack.itemId)
        val waiter = synchronized(ackLock) {
            val found = pendingAcks.remove(key)
            if (found == null) {
                val queue = queuedAcks.getOrPut(key) { ArrayDeque() }
                if (queue.size >= 8) queue.removeFirst()
                queue.addLast(ack)
            }
            found
        }
        if (waiter != null) {
            waiter.timeout.cancel(false)
            if (ack == waiter.expected) waiter.completion(Result.success(ack))
            else waiter.completion(Result.failure(IOException("Received ACK did not match expected committed prefix")))
        }
        return true
    }

    private fun expireAck(key: AckKey, expected: NearbyTransferFileChunkCodec.Ack) {
        val waiter = synchronized(ackLock) {
            val current = pendingAcks[key]
            if (current?.expected == expected) pendingAcks.remove(key) else null
        }
        waiter?.completion(Result.failure(IOException("Timed out waiting for receiver checkpoint ACK")))
    }

    fun peerDisconnected(endpointId: String) {
        pendingMetadata.keys.filter { it.endpointId == endpointId }.forEach(pendingMetadata::remove)
        receivers.keys.filter { it.endpointId == endpointId }.forEach { key ->
            receivers.remove(key)?.close()
        }
        val cancelled = synchronized(ackLock) {
            val keys = pendingAcks.keys.filter { it.endpointId == endpointId }
            keys.mapNotNull { key -> pendingAcks.remove(key) }.also {
                keys.forEach { queuedAcks.remove(it) }
            }
        }
        cancelled.forEach {
            it.timeout.cancel(false)
            it.completion(Result.failure(IOException("Peer disconnected during file transfer")))
        }
    }

    override fun close() {
        if (closed) return
        closed = true
        receivers.values.forEach(NearbyLanFileReceiver::close)
        receivers.clear()
        pendingMetadata.clear()
        val cancelled = synchronized(ackLock) {
            val values = pendingAcks.values.toList()
            pendingAcks.clear()
            queuedAcks.clear()
            values
        }
        cancelled.forEach {
            it.timeout.cancel(false)
            it.completion(Result.failure(IOException("File transfer coordinator closed")))
        }
        scheduler.shutdownNow()
    }

    private fun readMagic(bytes: ByteArray): Int =
        ((bytes[0].toInt() and 0xff) shl 24) or
            ((bytes[1].toInt() and 0xff) shl 16) or
            ((bytes[2].toInt() and 0xff) shl 8) or
            (bytes[3].toInt() and 0xff)
}
