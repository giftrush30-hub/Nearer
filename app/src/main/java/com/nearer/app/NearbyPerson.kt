package com.nearer.app

/**
 * A locally observed peer and the last time this device saw it.
 *
 * This is UI-facing local discovery history, not a remote presence guarantee.
 */
internal data class NearbyPerson(
    val name: String,
    val lastSeenAt: Long
)

/**
 * Formats a local last-seen timestamp for the nearby people list.
 *
 * [nowMillis] is injectable so boundary behaviour can be tested deterministically.
 */
internal fun seenAgo(timestamp: Long, nowMillis: Long = System.currentTimeMillis()): String {
    val seconds = (nowMillis - timestamp).coerceAtLeast(0L) / 1000L
    return when {
        seconds < 60 -> "Seen just now"
        seconds < 3600 -> "Seen ${seconds / 60}m ago"
        seconds < 86400 -> "Seen ${seconds / 3600}h ago"
        else -> "Seen ${seconds / 86400}d ago"
    }
}
