package com.nearer.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FlashSignalPatternTest {
    @Test fun decodesKnownPresetsWithinTolerance() {
        assertEquals("PING", FlashSignalPattern.decode(listOf(1_000L, 2_000L)))
        assertEquals("COME", FlashSignalPattern.decode(listOf(1_000L, 2_000L, 3_000L)))
        assertEquals("URGENT", FlashSignalPattern.decode(listOf(1_000L, 1_500L, 2_000L, 2_500L)))
        assertEquals("CALL_ME", FlashSignalPattern.decode(listOf(1_000L, 2_500L, 3_000L)))
    }

    @Test fun rejectsUnknownOrUnorderedSignals() {
        assertNull(FlashSignalPattern.decode(listOf(1_000L)))
        assertNull(FlashSignalPattern.decode(listOf(2_000L, 1_000L)))
        assertNull(FlashSignalPattern.decode(listOf(1_000L, 9_000L)))
    }
}
