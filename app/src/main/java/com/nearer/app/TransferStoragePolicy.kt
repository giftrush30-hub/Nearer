package com.nearer.app

/**
 * Preflight storage guard for a complete bundle.
 *
 * The check is advisory: other apps can consume storage after it runs, so the
 * receiver must still re-check free space immediately before each file save.
 */
internal object TransferStoragePolicy {
    fun requiredBytes(items: List<TransferBundleItem>): Long =
        items.asSequence()
            .filter { it.kind == TransferBundleItem.Kind.FILE }
            .fold(0L) { total, item -> Math.addExact(total, item.sizeBytes) }

    fun canFit(requiredBytes: Long, availableBytes: Long): Boolean {
        require(requiredBytes >= 0L) { "Required storage cannot be negative" }
        require(availableBytes >= 0L) { "Available storage cannot be negative" }
        return requiredBytes <= availableBytes
    }
}
