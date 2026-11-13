package com.nearer.app

import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest

/**
 * Crash-conscious local checkpoint store.
 *
 * A checkpoint is written to a sibling temporary file, flushed to disk, then
 * atomically renamed into place. The caller must only save offsets after the
 * corresponding partial file has itself been synced.
 */
internal class TransferResumeJournal(private val directory: File) {
    @Synchronized
    fun save(checkpoint: TransferResumeCheckpoint) {
        val encoded = TransferResumeCheckpointCodec.encode(checkpoint)
        if (!directory.exists() && !directory.mkdirs()) {
            throw IllegalStateException("Could not create the transfer-resume journal")
        }
        require(directory.isDirectory) { "Transfer-resume journal path is not a directory" }
        val destination = checkpointFile(checkpoint.transferId, checkpoint.itemId)
        val temporary = File(directory, ".${destination.name}.${System.nanoTime()}.tmp")
        try {
            FileOutputStream(temporary).use { output ->
                output.write(encoded)
                output.fd.sync()
            }
            if (!temporary.renameTo(destination)) {
                throw IllegalStateException("Could not commit the transfer-resume checkpoint")
            }
        } finally {
            if (temporary.exists()) temporary.delete()
        }
    }

    @Synchronized
    fun load(transferId: String, itemId: String): TransferResumeCheckpoint? {
        val file = checkpointFile(transferId, itemId)
        if (!file.isFile) return null
        // Corrupt and oversized entries are not resumable. Remove them now so
        // repeated app launches do not keep rediscovering the same dead record.
        if (file.length() !in 1..MAX_ENCODED_BYTES.toLong()) {
            file.delete()
            return null
        }
        val checkpoint = try {
            TransferResumeCheckpointCodec.decode(file.readBytes())
        } catch (_: Exception) {
            null
        }
        if (checkpoint == null || checkpoint.transferId != transferId ||
            checkpoint.itemId != itemId || checkpoint.validationError() != null) {
            file.delete()
            return null
        }
        return checkpoint
    }

    /** Enumerate valid checkpoints after process restart; repair corrupt journal debris. */
    @Synchronized
    fun loadAll(): List<TransferResumeCheckpoint> {
        if (!directory.isDirectory) return emptyList()
        val files = directory.listFiles() ?: return emptyList()
        val valid = ArrayList<TransferResumeCheckpoint>(MAX_DISCOVERED_ENTRIES)
        var inspected = 0
        for (file in files) {
            if (!file.isFile) continue
            if (inspected >= MAX_SCANNED_ENTRIES) break
            inspected++
            // A temp file is never a committed checkpoint: save() only publishes
            // after fsync + rename. It is safe to remove crash-leftover temp files.
            if (file.name.startsWith(".") && file.name.endsWith(".tmp")) {
                file.delete()
                continue
            }
            if (!file.name.endsWith(".nrc")) continue
            if (file.length() !in 1..MAX_ENCODED_BYTES.toLong()) {
                file.delete()
                continue
            }
            val checkpoint = try {
                TransferResumeCheckpointCodec.decode(file.readBytes())
            } catch (_: Exception) {
                null
            }
            if (checkpoint == null || checkpoint.validationError() != null ||
                checkpointFile(checkpoint.transferId, checkpoint.itemId).name != file.name) {
                file.delete()
                continue
            }
            valid.add(checkpoint)
            if (valid.size >= MAX_DISCOVERED_ENTRIES) break
        }
        return valid.sortedWith(compareBy<TransferResumeCheckpoint> { it.transferId }.thenBy { it.itemId })
    }

    @Synchronized
    fun removeTransfer(transferId: String): Int {
        require(transferId.isNotBlank()) { "Transfer ID is required" }
        return loadAll().filter { it.transferId == transferId }
            .count { remove(it.transferId, it.itemId) }
    }

    @Synchronized
    fun remove(transferId: String, itemId: String): Boolean {
        val file = checkpointFile(transferId, itemId)
        return !file.exists() || file.delete()
    }

    private companion object {
        const val MAX_ENCODED_BYTES = 6_144
        const val MAX_DISCOVERED_ENTRIES = 10_000
        const val MAX_SCANNED_ENTRIES = 20_000
    }

    private fun checkpointFile(transferId: String, itemId: String): File {
        require(transferId.isNotBlank() && itemId.isNotBlank()) { "Checkpoint identity is required" }
        val digest = MessageDigest.getInstance("SHA-256")
            .digest("$transferId\u0000$itemId".toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
        return File(directory, "$digest.nrc")
    }
}
