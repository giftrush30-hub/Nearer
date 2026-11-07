package com.nearer.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FlashSignalPatternTest {
    @Test fun decodesAllPresetPatterns() {
        assertEquals("PING", FlashSignalPattern.decode(listOf(1_000L, 1_900L)))
        assertEquals("COME", FlashSignalPattern.decode(listOf(1_000L, 2_400L, 3_100L)))
        assertEquals("URGENT", FlashSignalPattern.decode(listOf(1_000L, 1_450L, 1_900L, 2_350L)))
        assertEquals("CALL_ME", FlashSignalPattern.decode(listOf(1_000L, 2_800L, 3_300L)))
    }

    @Test fun rejectsAmbiguousOrMalformedSequences() {
        assertNull(FlashSignalPattern.decode(listOf(1_000L)))
        assertNull(FlashSignalPattern.decode(listOf(1_000L, 2_500L)))
        assertNull(FlashSignalPattern.decode(listOf(2_000L, 1_000L)))
        assertNull(FlashSignalPattern.decode(listOf(1_000L, 1_900L, 2_800L)))
    }

    @Test fun allowsConfiguredTimingTolerance() {
        assertEquals("PING", FlashSignalPattern.decode(listOf(1_000L, 2_300L), 450L))
        assertNull(FlashSignalPattern.decode(listOf(1_000L, 2_500L), 450L))
    }
}
