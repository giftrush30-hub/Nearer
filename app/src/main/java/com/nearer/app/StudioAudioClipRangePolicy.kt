package com.nearer.app

internal object StudioAudioClipRangePolicy {
    const val MIN_CLIP_MS = 500L

    fun moveStart(currentStartMs: Long, currentEndMs: Long, requestedStartMs: Long, durationMs: Long): Long {
        if (durationMs <= 0L) return 0L
        val minimum = MIN_CLIP_MS.coerceAtMost(durationMs)
        val safeEnd = currentEndMs.coerceIn(minimum, durationMs)
        return requestedStartMs.coerceIn(0L, (safeEnd - minimum).coerceAtLeast(0L))
    }

    fun moveEnd(currentStartMs: Long, currentEndMs: Long, requestedEndMs: Long, durationMs: Long): Long {
        if (durationMs <= 0L) return 0L
        val minimum = MIN_CLIP_MS.coerceAtMost(durationMs)
        val safeStart = currentStartMs.coerceIn(0L, (durationMs - minimum).coerceAtLeast(0L))
        return requestedEndMs.coerceIn((safeStart + minimum).coerceAtMost(durationMs), durationMs)
    }
}
