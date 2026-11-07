package com.nearer.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FlashSignalPatternTest {
    @Test fun decodesAllPresetPatterns() {
        assertEquals("PING", FlashSignalPattern.decode(listOf(1_000L, 6_000L)))
        assertEquals("COME", FlashSignalPattern.decode(listOf(1_000L, 12_000L, 20_000L)))
        assertEquals("URGENT", FlashSignalPattern.decode(listOf(1_000L, 15_000L, 29_000L, 43_000L)))
        assertEquals("CALL_ME", FlashSignalPattern.decode(listOf(1_000L, 21_000L, 38_000L)))
    }

    @Test fun mapsDecodedPresetsToHumanReadableChatMessages() {
        assertEquals("Ping", FlashSignalPattern.toChatMessage("PING"))
        assertEquals("Please come", FlashSignalPattern.toChatMessage("COME"))
        assertEquals("Urgent — please check this", FlashSignalPattern.toChatMessage("URGENT"))
        assertEquals("Please call me", FlashSignalPattern.toChatMessage("CALL_ME"))
        assertEquals("NeaRer Flash signal", FlashSignalPattern.toChatMessage("UNKNOWN"))
    }

    @Test fun rejectsAmbiguousOrMalformedSequences() {
        assertNull(FlashSignalPattern.decode(listOf(1_000L)))
        assertNull(FlashSignalPattern.decode(listOf(1_000L, 8_000L)))
        assertNull(FlashSignalPattern.decode(listOf(2_000L, 1_000L)))
        assertNull(FlashSignalPattern.decode(listOf(1_000L, 6_000L, 11_000L)))
        assertNull(FlashSignalPattern.decode(listOf(1_000L, 1_000L)))
    }

    @Test fun timingToleranceDoesNotCausePresetCollisions() {
        assertEquals("PING", FlashSignalPattern.decode(listOf(1_000L, 5_100L), 1_000L))
        assertNull(FlashSignalPattern.decode(listOf(1_000L, 6_000L), 900L))
        assertNull(FlashSignalPattern.decode(listOf(1_000L, 8_000L), 1_000L))
        assertNull(FlashSignalPattern.decode(listOf(1_000L, 15_000L, 23_000L), 1_000L))
    }

    @Test fun acceptsTimingVariationWithinOneSecond() {
        assertEquals("PING", FlashSignalPattern.decode(listOf(1_000L, 5_850L)))
        assertEquals("COME", FlashSignalPattern.decode(listOf(1_000L, 11_900L, 19_100L)))
        assertEquals("URGENT", FlashSignalPattern.decode(listOf(1_000L, 14_900L, 28_100L, 43_000L)))
        assertEquals("CALL_ME", FlashSignalPattern.decode(listOf(1_000L, 20_900L, 37_100L)))
    }
}
