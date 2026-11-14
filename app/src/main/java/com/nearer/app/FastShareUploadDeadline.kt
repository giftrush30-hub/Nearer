package com.nearer.app

/**
 * Gives large browser uploads a size-aware overall body deadline.
 *
 * Socket SO_TIMEOUT already protects individual idle reads; this longer,
 * size-proportional deadline also prevents a peer from pinning an upload slot
 * forever by trickling tiny amounts of data just before each idle timeout.
 */
internal object FastShareUploadDeadline {
    const val BASE_MILLIS = 2 * 60 * 1000L
    const val MIN_BYTES_PER_SECOND = 64 * 1024L
    const val MAX_MILLIS = 24 * 60 * 60 * 1000L

    fun timeoutMillis(contentLength: Long): Long {
        require(contentLength >= 0L) { "Upload length cannot be negative" }
        // Divide before multiplying to avoid overflowing for very large files.
        val seconds = contentLength / MIN_BYTES_PER_SECOND +
            if (contentLength % MIN_BYTES_PER_SECOND == 0L) 0L else 1L
        val transferMillis = if (seconds > (Long.MAX_VALUE - BASE_MILLIS) / 1000L) {
            Long.MAX_VALUE
        } else {
            seconds * 1000L
        }
        return (BASE_MILLIS + transferMillis).coerceAtMost(MAX_MILLIS)
    }
}
