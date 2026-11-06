package com.nearer.app

/**
 * Durable identity and byte boundary for an interrupted bundle item.
 *
 * A checkpoint is only a claim about bytes already written to a temporary file.
 * Callers must persist it only after syncing those bytes, and must re-hash the
 * retained prefix before trusting it after process death.
 */
data class TransferResumeCheckpoint(
    val transferId: String,
    val itemId: String,
    val relativePath: String,
    val expectedSizeBytes: Long,
    val sourceModifiedAtMillis: Long?,
    val committedOffsetBytes: Long,
    val prefixSha256: String
) {
    fun validationError(): String? {
        if (transferId.isBlank() || transferId.length > 128) return "Invalid transfer ID"
        if (itemId.isBlank() || itemId.length > 128) return "Invalid item ID"
        if (relativePath.isBlank() || relativePath.length > TransferBundleManifest.MAX_PATH_LENGTH) return "Invalid relative path"
        if (!TransferBundleManifest(transferId, listOf(
                TransferBundleItem(itemId, relativePath, relativePath.substringAfterLast('/'),
                    expectedSizeBytes, null)
            )).validationErrors().isEmpty()) return "Invalid bundle item identity"
        if (expectedSizeBytes < 0L) return "Negative expected size"
        if (sourceModifiedAtMillis != null && sourceModifiedAtMillis < 0L) return "Invalid source modification time"
        if (committedOffsetBytes < 0L || committedOffsetBytes > expectedSizeBytes) return "Checkpoint offset is outside the file"
        if (committedOffsetBytes > 0L && committedOffsetBytes < expectedSizeBytes &&
            committedOffsetBytes % CHECKPOINT_GRANULARITY_BYTES != 0L) {
            return "Checkpoint offset is not aligned to a durable boundary"
        }
        if (!SHA256.matches(prefixSha256)) return "Invalid prefix SHA-256"
        return null
    }

    fun matches(
        transferId: String,
        item: TransferBundleItem,
        sourceModifiedAtMillis: Long?
    ): Boolean = validationError() == null &&
        this.transferId == transferId &&
        item.kind == TransferBundleItem.Kind.FILE &&
        this.itemId == item.id &&
        this.relativePath == item.relativePath &&
        this.expectedSizeBytes == item.sizeBytes &&
        this.sourceModifiedAtMillis == sourceModifiedAtMillis

    companion object {
        const val CHECKPOINT_GRANULARITY_BYTES = 4L * 1024L * 1024L
        private val SHA256 = Regex("^[a-fA-F0-9]{64}$")

        /** Return the greatest safe checkpoint not beyond the bytes actually synced. */
        fun alignedOffset(bytesSynced: Long, fileSizeBytes: Long): Long {
            require(bytesSynced >= 0L) { "Synced byte count cannot be negative" }
            require(fileSizeBytes >= 0L) { "File size cannot be negative" }
            require(bytesSynced <= fileSizeBytes) { "Synced byte count exceeds file size" }
            if (bytesSynced == fileSizeBytes) return fileSizeBytes
            return (bytesSynced / CHECKPOINT_GRANULARITY_BYTES) * CHECKPOINT_GRANULARITY_BYTES
        }
    }
}
