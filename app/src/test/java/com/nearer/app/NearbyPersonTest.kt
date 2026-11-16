package com.nearer.app

import org.junit.Assert.assertEquals
import org.junit.Test

class NearbyPersonTest {
    @Test
    fun formatsRecentPresence() {
        val now = 10_000_000L
        assertEquals("Seen just now", seenAgo(now - 59_000L, now))
    }

    @Test
    fun formatsMinuteAndHourBoundaries() {
        val now = 10_000_000L
        assertEquals("Seen 1m ago", seenAgo(now - 60_000L, now))
        assertEquals("Seen 1h ago", seenAgo(now - 3_600_000L, now))
    }

    @Test
    fun formatsDaysAndClampsFutureTimestamps() {
        val now = 10_000_000L
        assertEquals("Seen 1d ago", seenAgo(now - 86_400_000L, now))
        assertEquals("Seen just now", seenAgo(now + 5_000L, now))
    }
}
