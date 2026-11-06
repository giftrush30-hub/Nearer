package com.nearer.app

import java.io.File
import java.io.FileInputStream
import java.io.RandomAccessFile
import java.security.MessageDigest

/**
 * Coordinates durable partial-file checkpoints. It syncs the partial bytes before
 * recording their offset, and verifies the committed prefix before exposing a
 * candidate after restart. The transport still has to negotiate and apply the
 * returned offset to a Nearby FILE payload.
 */
internal class TransferResumeStore(
    private val partialDirectory: File,
    private val journal: TransferResumeJournal
) {
    data class Candidate(
        val checkpoint: TransferResumeCheckpoint,
        val partialFile: File
    )

    @Synchronized
    fun checkpoint(
        transferId: String,
        item: TransferBundleItem,
        sourceModifiedAtMillis: Long?,
        bytesSynced: Long
    ): TransferResumeCheckpoint {
        require(item.kind == TransferBundleItem.Kind.FILE) { "Only files can be resumed" }
        require(item.sizeBytes >= 0L && bytesSynced in 0L..item.sizeBytes) {
            "Synced byte count is outside the source file"
        }
        if (!partialDirectory.exists() && !partialDirectory.mkdirs()) {
            throw IllegalStateException("Could not create the partial-transfer folder")
        }
        require(partialDirectory.isDirectory) { "Partial-transfer path is not a folder" }

        val partial = partialFileFor(transferId, item.id)
        require(partial.isFile && partial.length() >= bytesSynced) {
            "Partial file does not contain all reported synced bytes"
        }
        val committedOffset = TransferResumeCheckpoint.alignedOffset(bytesSynced, item.sizeBytes)
        val digest = RandomAccessFile(partial, "rw").use { file ->
            file.fd.sync()
            if (file.length() > committedOffset) {
                file.setLength(committedOffset)
                file.fd.sync()
            }
            sha256Prefix(partial, committedOffset)
        }
        val checkpoint = TransferResumeCheckpoint(
            transferId = transferId,
            itemId = item.id,
            relativePath = item.relativePath,
            expectedSizeBytes = item.sizeBytes,
            sourceModifiedAtMillis = sourceModifiedAtMillis,
            committedOffsetBytes = committedOffset,
            prefixSha256 = digest
        )
        val error = checkpoint.validationError()
        require(error == null) { error ?: "Invalid resume checkpoint" }
        journal.save(checkpoint)
        return checkpoint
    }

    @Synchronized
    fun findValid(
        transferId: String,
        item: TransferBundleItem,
        sourceModifiedAtMillis: Long?
    ): Candidate? {
        val checkpoint = journal.load(transferId, item.id) ?: return null
        val partial = partialFileFor(transferId, item.id)
        // A checkpoint tied to a changed source is never reusable. Remove both
        // halves so stale bytes cannot linger and later be mistaken for a valid
        // candidate if the same transfer identity is retried.
        if (!checkpoint.matches(transferId, item, sourceModifiedAtMillis)) {
            discard(transferId, item.id)
            return null
        }
        // Treat a missing, truncated, oversized, or digest-mismatched partial as
        // a failed resume candidate and clean it up before a fresh transfer.
        if (!TransferResumeFileVerifier.verifyAndTrim(partial, checkpoint)) {
            discard(transferId, item.id)
            return null
        }
        return Candidate(checkpoint, partial)
    }

    @Synchronized
    fun discard(transferId: String, itemId: String): Boolean {
        val partialDeleted = !partialFileFor(transferId, itemId).exists() ||
            partialFileFor(transferId, itemId).delete()
        val checkpointDeleted = journal.remove(transferId, itemId)
        return partialDeleted && checkpointDeleted
    }

    internal fun partialFileFor(transferId: String, itemId: String): File {
        require(transferId.isNotBlank() && itemId.isNotBlank())
        val key = MessageDigest.getInstance("SHA-256")
            .digest("$transferId\u0000$itemId".toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
        return File(partialDirectory, "$key.part")
    }

    private fun sha256Prefix(file: File, length: Long): String {
        val digest = MessageDigest.getInstance("SHA-256")
        FileInputStream(file).use { input ->
            val buffer = ByteArray(BUFFER_SIZE)
            var remaining = length
            while (remaining > 0L) {
                val read = input.read(buffer, 0, minOf(buffer.size.toLong(), remaining).toInt())
                if (read <= 0) throw IllegalStateException("Partial file ended before its checkpoint offset")
                digest.update(buffer, 0, read)
                remaining -= read.toLong()
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private companion object {
        const val BUFFER_SIZE = 64 * 1024
    }
}
