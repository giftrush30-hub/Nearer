package com.nearer.app

import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import java.io.InterruptedIOException
import java.io.RandomAccessFile
import java.security.MessageDigest

/**
 * Writes an incoming stream into a receiver-owned partial file.
 *
 * Every durable checkpoint is recorded only after the corresponding bytes have
 * been synced. On restart, TransferResumeStore verifies the retained prefix
 * before this writer appends to it. This is a storage primitive; transport
 * negotiation and Nearby STREAM wiring remain the caller's responsibility.
 */
internal class TransferResumeStreamWriter(
    private val store: TransferResumeStore,
    private val bufferBytes: Int = DEFAULT_BUFFER_BYTES
) {
    data class Result(
        val partialFile: File,
        val bytesReceived: Long,
        val resumedFromBytes: Long,
        val sha256: String
    )

    init {
        require(bufferBytes in 4 * 1024..1024 * 1024) { "Invalid resume stream buffer size" }
    }

    fun write(
        transferId: String,
        item: TransferBundleItem,
        sourceModifiedAtMillis: Long?,
        input: InputStream,
        isCancelled: () -> Boolean = { false }
    ): Result = write(
        transferId = transferId,
        item = item,
        sourceModifiedAtMillis = sourceModifiedAtMillis,
        input = input,
        sourcePeerId = null,
        isCancelled = isCancelled
    )

    /**
     * Peer-bound variant for transports that have authenticated the sender.
     * The compatibility overload above preserves the original trailing-lambda
     * cancellation API for existing callers.
     */
    fun write(
        transferId: String,
        item: TransferBundleItem,
        sourceModifiedAtMillis: Long?,
        input: InputStream,
        sourcePeerId: String?,
        isCancelled: () -> Boolean = { false }
    ): Result {
        require(item.kind == TransferBundleItem.Kind.FILE) { "Only files can be resumed" }
        val candidate = store.findValid(transferId, item, sourceModifiedAtMillis, sourcePeerId)
        val partial = candidate?.partialFile ?: store.partialFileFor(transferId, item.id)
        val offset = candidate?.checkpoint?.committedOffsetBytes ?: 0L

        if (candidate == null) {
            if (partial.exists() && !partial.delete()) {
                throw IllegalStateException("Could not reset an incomplete partial file")
            }
            partial.parentFile?.let { parent ->
                if (!parent.exists() && !parent.mkdirs()) {
                    throw IllegalStateException("Could not create the partial-transfer folder")
                }
            }
        }

        val digest = MessageDigest.getInstance("SHA-256")
        if (offset > 0L) {
            FileInputStream(partial).use { prefix ->
                val prefixBuffer = ByteArray(bufferBytes)
                var remaining = offset
                while (remaining > 0L) {
                    checkCancelled(isCancelled)
                    val read = prefix.read(prefixBuffer, 0, minOf(prefixBuffer.size.toLong(), remaining).toInt())
                    if (read <= 0) throw IllegalStateException("Verified resume prefix ended unexpectedly")
                    digest.update(prefixBuffer, 0, read)
                    remaining -= read.toLong()
                }
            }
        }

        var received = offset
        var nextCheckpoint = nextCheckpointAfter(offset, item.sizeBytes)
        RandomAccessFile(partial, "rw").use { output ->
            output.setLength(offset)
            output.seek(offset)
            // Empty files never enter the read loop, but still need a durable
            // EOF checkpoint so the normal whole-file verification/finalization
            // path can publish them safely.
            if (item.sizeBytes == 0L && candidate == null) {
                output.fd.sync()
                store.checkpoint(transferId, item, sourceModifiedAtMillis, 0L, sourcePeerId)
            }
            val buffer = ByteArray(bufferBytes)
            while (received < item.sizeBytes) {
                checkCancelled(isCancelled)
                val untilCheckpoint = (nextCheckpoint - received).coerceAtLeast(1L)
                val wanted = minOf(buffer.size.toLong(), item.sizeBytes - received, untilCheckpoint).toInt()
                val read = input.read(buffer, 0, wanted)
                if (read < 0) throw IllegalStateException("Incoming stream ended before the declared file size")
                if (read == 0) {
                    // A few wrapped/custom streams can legally make no progress
                    // on a bulk read. Fall back to one byte so a broken provider
                    // cannot pin this worker in a zero-progress busy loop.
                    val singleByte = input.read()
                    if (singleByte < 0) {
                        throw IllegalStateException("Incoming stream ended before the declared file size")
                    }
                    output.write(singleByte)
                    digest.update(singleByte.toByte())
                    received++
                } else {
                    output.write(buffer, 0, read)
                    digest.update(buffer, 0, read)
                    received += read.toLong()
                }

                if (received == nextCheckpoint || received == item.sizeBytes) {
                    output.fd.sync()
                    store.checkpoint(transferId, item, sourceModifiedAtMillis, received, sourcePeerId)
                    nextCheckpoint = nextCheckpointAfter(received, item.sizeBytes)
                }
            }
            checkCancelled(isCancelled)
            if (input.read() != -1) throw IllegalStateException("Incoming stream exceeded the declared file size")
            output.fd.sync()
        }
        return Result(partial, received, offset, digest.digest().toHex())
    }

    private fun nextCheckpointAfter(offset: Long, size: Long): Long =
        TransferResumeCheckpoint.nextCheckpointOffset(offset, size)

    private fun checkCancelled(isCancelled: () -> Boolean) {
        if (Thread.currentThread().isInterrupted || isCancelled()) {
            throw InterruptedIOException("Transfer was cancelled")
        }
    }

    private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }

    private companion object {
        const val DEFAULT_BUFFER_BYTES = 256 * 1024
    }
}
