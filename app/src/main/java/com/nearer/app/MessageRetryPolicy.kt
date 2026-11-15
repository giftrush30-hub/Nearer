package com.nearer.app

/**
 * Small, deterministic retry schedule for nearby chat delivery.
 * Backoff prevents rapid repeated sends while keeping recovery responsive.
 */
internal object MessageRetryPolicy {
    const val MAX_ATTEMPTS = 3
    private const val MAX_DELAY_MS = 5000L
    const val CONNECTED_RETRY_DELAY_MS = 15_000L

    /** Keep retrying a message on the same link only while its authenticated session is usable. */
    fun shouldRetryOnActiveSession(authenticated: Boolean, active: Boolean, sessionReady: Boolean): Boolean =
        authenticated && active && sessionReady

    fun delayMillis(attemptNumber: Int, baseDelayMs: Long): Long {
        require(attemptNumber >= 1) { "attemptNumber must be positive" }
        require(baseDelayMs > 0L) { "baseDelayMs must be positive" }
        var delay = baseDelayMs
        repeat(attemptNumber - 1) {
            delay = if (delay >= MAX_DELAY_MS / 2L) MAX_DELAY_MS else delay * 2L
        }
        return delay.coerceAtMost(MAX_DELAY_MS)
    }
}
