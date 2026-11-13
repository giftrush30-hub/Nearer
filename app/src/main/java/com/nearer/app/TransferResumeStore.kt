package com.nearer.app

import java.io.File
import java.io.FileInputStream
import java.io.RandomAccessFile
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
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
        bytesSynced: Long,
        sourcePeerId: String? = null
    ): TransferResumeCheckpoint {
        require(transferId.isNotBlank() && transferId.length <= 128) { "Invalid transfer ID" }
        require(item.id.isNotBlank() && item.id.length <= 128) { "Invalid bundle item ID" }
        require(item.relativePath.isNotBlank() && item.relativePath.length <= TransferBundleManifest.MAX_PATH_LENGTH) {
            "Invalid bundle item path"
        }
        require(sourceModifiedAtMillis == null || sourceModifiedAtMillis >= 0L) {
            "Invalid source modification time"
        }
        require(sourcePeerId == null || NeaRerIdentity.normalize(sourcePeerId) == sourcePeerId) {
            "Invalid authenticated source peer identity"
        }
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
            prefixSha256 = digest,
            sourcePeerId = sourcePeerId
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
        sourceModifiedAtMillis: Long?,
        expectedSourcePeerId: String? = null
    ): Candidate? {
        val checkpoint = journal.load(transferId, item.id) ?: return null
        val partial = partialFileFor(transferId, item.id)
        // A checkpoint tied to a changed source is never reusable. Remove both
        // halves so stale bytes cannot linger and later be mistaken for a valid
        // candidate if the same transfer identity is retried.
        if (!checkpoint.matches(transferId, item, sourceModifiedAtMillis) ||
            (expectedSourcePeerId != null && checkpoint.sourcePeerId != expectedSourcePeerId)) {
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

    /**
     * Re-discovers receiver-owned partials after process death.
     *
     * The original manifest may no longer be in memory when the app restarts,
     * so the bounded journal is the source of candidate identities. Every
     * candidate is re-verified against its on-disk prefix before it is returned;
     * damaged or missing partials are discarded. This does not authorize a
     * resume by itself: the sender must still confirm the same transfer/item,
     * source size and modification time, and the negotiated prefix digest before
     * any transport is allowed to continue at the returned offset.
     */
    @Synchronized
    fun discoverVerifiedCandidates(): List<Candidate> {
        return journal.loadAll().mapNotNull { checkpoint ->
            val item = TransferBundleItem(
                id = checkpoint.itemId,
                relativePath = checkpoint.relativePath,
                displayName = checkpoint.relativePath.substringAfterLast('/'),
                sizeBytes = checkpoint.expectedSizeBytes,
                mimeType = null,
                kind = TransferBundleItem.Kind.FILE
            )
            findValid(
                transferId = checkpoint.transferId,
                item = item,
                sourceModifiedAtMillis = checkpoint.sourceModifiedAtMillis,
                expectedSourcePeerId = checkpoint.sourcePeerId
            )?.takeIf { candidate ->
                // Legacy checkpoints without a stable sender binding cannot be
                // offered after process death: the new endpoint ID is unrelated.
                // A zero-byte checkpoint cannot save any retransmission work,
                // while an EOF checkpoint is complete rather than resumable.
                // Leave valid journal entries intact for a future finalization
                // coordinator, but do not present either as resumable work.
                candidate.checkpoint.sourcePeerId != null &&
                    candidate.checkpoint.committedOffsetBytes > 0L &&
                    candidate.checkpoint.committedOffsetBytes < candidate.checkpoint.expectedSizeBytes
            }
        }.sortedWith(
            compareBy<Candidate> { it.checkpoint.transferId }
                .thenBy { it.checkpoint.relativePath }
        )
    }

    /**
     * Publishes a fully received partial only after its durable EOF checkpoint and
     * whole-file digest have been verified. The final move is atomic; if the
     * filesystem cannot provide an atomic move, the partial is left untouched.
     *
     * The destination must be chosen by the trusted receive flow, not from an
     * untrusted manifest path. Existing destination files are never overwritten.
     */
    @Synchronized
    fun finalizeVerifiedComplete(
        transferId: String,
        item: TransferBundleItem,
        sourceModifiedAtMillis: Long?,
        destination: File,
        expectedSha256: String
    ): File {
        require(Regex("^[a-fA-F0-9]{64}$").matches(expectedSha256)) {
            "A valid whole-file SHA-256 is required"
        }
        val candidate = findValid(transferId, item, sourceModifiedAtMillis)
            ?: throw IllegalStateException("No verified partial is available to finalize")
        require(candidate.checkpoint.committedOffsetBytes == item.sizeBytes) {
            "The partial file has not reached its declared end"
        }
        require(candidate.partialFile.length() == item.sizeBytes) {
            "The completed partial has an unexpected size"
        }
        val actualSha256 = sha256Prefix(candidate.partialFile, item.sizeBytes)
        require(MessageDigest.isEqual(
            actualSha256.toByteArray(Charsets.US_ASCII),
            expectedSha256.lowercase().toByteArray(Charsets.US_ASCII)
        )) { "Completed file digest does not match the sender's digest" }

        val target = destination.absoluteFile
        require(target != candidate.partialFile.absoluteFile) {
            "Final destination must differ from the partial file"
        }
        val parent = target.parentFile
            ?: throw IllegalArgumentException("Final destination must have a parent folder")
        if (!parent.exists() && !parent.mkdirs()) {
            throw IllegalStateException("Could not create the final destination folder")
        }
        require(parent.isDirectory) { "Final destination parent is not a folder" }
        require(!target.exists()) { "Final destination already exists" }

        try {
            Files.move(candidate.partialFile.toPath(), target.toPath(), StandardCopyOption.ATOMIC_MOVE)
        } catch (error: AtomicMoveNotSupportedException) {
            throw IllegalStateException("Atomic finalization is not supported on this storage", error)
        }
        // If this journal cleanup fails, the next discovery sees the missing
        // partial and removes the stale record; the already-published file stays safe.
        journal.remove(transferId, item.id)
        return target
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
