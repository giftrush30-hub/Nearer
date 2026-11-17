package com.nearer.app

/** Validates short-lived typing hints; these packets must never enter the durable outbox. */
internal object ChatTypingPolicy {
    fun isValid(version: Int, typing: Boolean, expiresAt: Long, now: Long): Boolean =
        version == 1 &&
            expiresAt > 0L &&
            expiresAt <= now + 5_000L &&
            (!typing || expiresAt > now)
}
