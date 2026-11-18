package com.nearer.app

import java.io.File
import java.io.FileInputStream
import java.security.MessageDigest

internal fun interface NearbyLanFrameSink {
    /** Completes after the bounded frame has been flushed to the transport. */
    fun send(frame: ByteArray, completion: (Throwable?) -> Unit)
}

internal fun interface NearbyLanAckWaiter {
    /** Waits for a peer ACK matching the expected durable offset and prefix digest. */
    fun await(expected: NearbyTransferFileChunkCodec.Ack, completion: (Result<NearbyTransferFileChunkCodec.Ack>) -> Unit)
}

/**
 * Two-pass sender: hashes the source before advertising metadata, then streams
 * one 512 KiB chunk at a time. It waits for an explicit receiver-acceptance ACK,
 * durable 4 MiB checkpoint ACKs and a final whole-file verification ACK.
 */
internal object NearbyLanFileSender {
    private const val CHECKPOINT_INTERVAL_BYTES = 4L * 1024L * 1024L

    fun send(
        source: File,
        metadata: TransferResumeStreamMetadataCodec.Metadata,
        sink: NearbyLanFrameSink,
        ackWaiter: NearbyLanAckWaiter,
        completion: (Result<Unit>) -> Unit
    ) {
        try {
            TransferResumeStreamMetadataCodec.validate(metadata)
            require(source.isFile && source.length() == metadata.item.sizeBytes) {
                "Source file does not match transfer metadata"
            }
            val initialDigest = hashFile(source)
            require(initialDigest == metadata.wholeFileSha256.lowercase()) {
                "Source SHA-256 does not match transfer metadata"
            }
            val input = FileInputStream(source)
            val streamingDigest = MessageDigest.getInstance("SHA-256")
            val buffer = ByteArray(NearbyTransferFileChunkCodec.MAX_CHUNK_BYTES)
            var offset = 0L
            var completed = false

            fun fail(error: Throwable) {
                if (completed) return
                completed = true
                runCatching { input.close() }
                completion(Result.failure(error))
            }

            fun awaitExpected(expected: NearbyTransferFileChunkCodec.Ack, next: () -> Unit) {
                ackWaiter.await(expected) { result ->
                    result.fold(
                        onSuccess = { received ->
                            if (received == expected) next()
                            else fail(IllegalStateException("Receiver ACK did not match the expected committed prefix"))
                        },
                        onFailure = ::fail
                    )
                }
            }

            fun sendNext() {
                if (completed) return
                try {
                    val count = input.read(buffer)
                    if (count < 0) {
                        require(offset == metadata.item.sizeBytes) { "Source size changed during transfer" }
                        val actualDigest = streamingDigest.digest().toHex()
                        require(actualDigest == metadata.wholeFileSha256.lowercase()) {
                            "Source file changed while streaming"
                        }
                        val end = NearbyTransferFileChunkCodec.End(
                            metadata.payloadId, metadata.transferId, metadata.item.id,
                            offset, actualDigest
                        )
                        sink.send(NearbyTransferFileChunkCodec.encodeEnd(end)) { error ->
                            if (error != null) fail(error)
                            else {
                                val finalAck = NearbyTransferFileChunkCodec.Ack(
                                    metadata.payloadId, metadata.transferId, metadata.item.id,
                                    offset, actualDigest
                                )
                                awaitExpected(finalAck) {
                                    if (!completed) {
                                        completed = true
                                        runCatching { input.close() }
                                        completion(Result.success(Unit))
                                    }
                                }
                            }
                        }
                        return
                    }
                    if (count == 0) {
                        sendNext()
                        return
                    }
                    require(offset <= metadata.item.sizeBytes - count.toLong()) {
                        "Source file grew during transfer"
                    }
                    val chunkBytes = buffer.copyOf(count)
                    streamingDigest.update(chunkBytes)
                    val chunk = NearbyTransferFileChunkCodec.Chunk(
                        metadata.payloadId, metadata.transferId, metadata.item.id,
                        offset, chunkBytes
                    )
                    offset += count.toLong()
                    val checkpointAck = if (
                        offset < metadata.item.sizeBytes &&
                        offset % CHECKPOINT_INTERVAL_BYTES == 0L
                    ) {
                        val snapshot = (streamingDigest.clone() as MessageDigest).digest().toHex()
                        NearbyTransferFileChunkCodec.Ack(
                            metadata.payloadId, metadata.transferId, metadata.item.id, offset, snapshot
                        )
                    } else null
                    sink.send(NearbyTransferFileChunkCodec.encodeChunk(chunk)) { error ->
                        if (error != null) fail(error)
                        else if (checkpointAck != null) awaitExpected(checkpointAck, ::sendNext)
                        else sendNext()
                    }
                } catch (error: Throwable) {
                    fail(error)
                }
            }

            val metadataBytes = TransferResumeStreamMetadataCodec.encode(metadata)
            sink.send(metadataBytes) { error ->
                if (error != null) fail(error)
                else {
                    val emptyDigest = MessageDigest.getInstance("SHA-256").digest().toHex()
                    val initialAck = NearbyTransferFileChunkCodec.Ack(
                        metadata.payloadId, metadata.transferId, metadata.item.id, 0L, emptyDigest
                    )
                    awaitExpected(initialAck, ::sendNext)
                }
            }
        } catch (error: Throwable) {
            completion(Result.failure(error))
        }
    }

    private fun hashFile(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        FileInputStream(file).use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                if (count > 0) digest.update(buffer, 0, count)
            }
        }
        return digest.digest().toHex()
    }

    private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }
}
