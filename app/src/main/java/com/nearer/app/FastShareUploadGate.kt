package com.nearer.app

/** Bounds PC-to-phone uploads and reserves their declared bytes against free storage. */
internal class FastShareUploadGate(
    private val maxConcurrentUploads: Int = MAX_CONCURRENT_UPLOADS
) {
    private var activeUploads = 0
    private var reservedBytes = 0L

    init { require(maxConcurrentUploads > 0) }

    @Synchronized
    fun tryAcquire(contentLength: Long, availableBytes: Long): Boolean {
        if (contentLength < 0L || availableBytes < 0L) return false
        if (activeUploads >= maxConcurrentUploads) return false
        if (contentLength > availableBytes - reservedBytes) return false
        activeUploads++
        reservedBytes += contentLength
        return true
    }

    @Synchronized
    fun release(contentLength: Long) {
        require(contentLength >= 0L)
        check(activeUploads > 0 && contentLength <= reservedBytes) { "Upload reservation is not active" }
        activeUploads--
        reservedBytes -= contentLength
    }

    @Synchronized
    fun snapshot(): Snapshot = Snapshot(activeUploads, reservedBytes)

    data class Snapshot(val activeUploads: Int, val reservedBytes: Long)

    companion object { const val MAX_CONCURRENT_UPLOADS = 2 }
}
