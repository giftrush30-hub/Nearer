package com.nearer.app

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatTypingPolicyTest {
    @Test fun acceptsShortLivedTypingAndStopSignals() {
        assertTrue(ChatTypingPolicy.isValid(1, true, 2_000L, 1_000L))
        assertTrue(ChatTypingPolicy.isValid(1, false, 1_000L, 1_000L))
    }

    @Test fun rejectsExpiredOrUnboundedTypingSignals() {
        assertFalse(ChatTypingPolicy.isValid(2, true, 2_000L, 1_000L))
        assertFalse(ChatTypingPolicy.isValid(1, true, 1_000L, 1_000L))
        assertFalse(ChatTypingPolicy.isValid(1, true, 10_000L, 1_000L))
        assertFalse(ChatTypingPolicy.isValid(1, false, 0L, 1_000L))
    }
}
