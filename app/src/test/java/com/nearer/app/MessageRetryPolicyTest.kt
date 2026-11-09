package com.nearer.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class MessageRetryPolicyTest {
    @Test fun timeoutBackoffGrowsAndCaps() {
        assertEquals(1800L, MessageRetryPolicy.delayMillis(1, 1800L))
        assertEquals(3600L, MessageRetryPolicy.delayMillis(2, 1800L))
        assertEquals(5000L, MessageRetryPolicy.delayMillis(3, 1800L))
        assertEquals(5000L, MessageRetryPolicy.delayMillis(8, 1800L))
    }

    @Test fun sendFailureBackoffGrowsAndCaps() {
        assertEquals(1200L, MessageRetryPolicy.delayMillis(1, 1200L))
        assertEquals(2400L, MessageRetryPolicy.delayMillis(2, 1200L))
        assertEquals(4800L, MessageRetryPolicy.delayMillis(3, 1200L))
        assertEquals(5000L, MessageRetryPolicy.delayMillis(4, 1200L))
    }

    @Test fun rejectsInvalidAttemptAndBaseDelay() {
        assertThrows(IllegalArgumentException::class.java) {
            MessageRetryPolicy.delayMillis(0, 1000L)
        }
        assertThrows(IllegalArgumentException::class.java) {
            MessageRetryPolicy.delayMillis(1, 0L)
        }
    }

    @Test fun retryBudgetRemainsThreeAttempts() {
        assertEquals(3, MessageRetryPolicy.MAX_ATTEMPTS)
    }
}
