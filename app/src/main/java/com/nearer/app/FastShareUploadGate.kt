package com.nearer.app

/** Bounds PC-to-phone uploads and reserves their declared bytes against free storage. */
internal class FastShareUploadGate(
    private val maxConcurrentUploads: Int = MAX_CONCURRENT_UPLOADS
) {
    private var activeUploads = 0
    private var reservedBytes = 0L

    init { require(maxConcurrentUploads > 0) }

    @Synchronized
    fun tryAcquire(contentLength: Long, availableBytes: Long): Admission {
        if (contentLength < 0L || availableBytes < 0L) return Admission.INVALID_SIZE
        if (activeUploads >= maxConcurrentUploads) return Admission.BUSY
        // Free space can shrink after an earlier request reserved its bytes
        // (for example, another app writes to storage). Fail closed rather
        // than treating a stale reservation as available capacity.
        if (reservedBytes > availableBytes || contentLength > availableBytes - reservedBytes) {
            return Admission.INSUFFICIENT_STORAGE
        }
        activeUploads++
        reservedBytes += contentLength
        return Admission.ACCEPTED
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

    enum class Admission { ACCEPTED, BUSY, INSUFFICIENT_STORAGE, INVALID_SIZE }
    data class Snapshot(val activeUploads: Int, val reservedBytes: Long)

    companion object { const val MAX_CONCURRENT_UPLOADS = 2 }
}
