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
        // A journal entry is tiny by design. Check its on-disk length before
        // allocating a byte array so a damaged or externally modified file cannot
        // trigger an unbounded read during resume discovery.
        if (!file.isFile || file.length() !in 1..MAX_ENCODED_BYTES.toLong()) return null
        return try {
            TransferResumeCheckpointCodec.decode(file.readBytes()).takeIf {
                it.transferId == transferId && it.itemId == itemId &&
                    it.validationError() == null
            }
        } catch (_: Exception) {
            null
        }
    }

    /** Enumerate valid checkpoints after process restart; corrupt or misnamed entries are ignored. */
    @Synchronized
    fun loadAll(): List<TransferResumeCheckpoint> {
        if (!directory.isDirectory) return emptyList()
        return directory.listFiles()
            ?.asSequence()
            ?.filter { it.isFile && it.name.endsWith(".nrc") && it.length() in 1..MAX_ENCODED_BYTES.toLong() }
            ?.take(MAX_DISCOVERED_ENTRIES)
            ?.mapNotNull { file ->
                try {
                    TransferResumeCheckpointCodec.decode(file.readBytes()).takeIf { checkpoint ->
                        checkpointFile(checkpoint.transferId, checkpoint.itemId).name == file.name
                    }
                } catch (_: Exception) {
                    null
                }
            }
            ?.sortedWith(compareBy<TransferResumeCheckpoint> { it.transferId }.thenBy { it.itemId })
            ?.toList()
            ?: emptyList()
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
    }

    private fun checkpointFile(transferId: String, itemId: String): File {
        require(transferId.isNotBlank() && itemId.isNotBlank()) { "Checkpoint identity is required" }
        val digest = MessageDigest.getInstance("SHA-256")
            .digest("$transferId\u0000$itemId".toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
        return File(directory, "$digest.nrc")
    }
}
