package com.nearer.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FlashSignalPatternTest {
    @Test fun decodesAllPresetPatterns() {
        assertEquals("PING", FlashSignalPattern.decode(listOf(1_000L, 6_000L, 18_000L)))
        assertEquals("COME", FlashSignalPattern.decode(listOf(1_000L, 12_000L, 20_000L)))
        assertEquals("URGENT", FlashSignalPattern.decode(listOf(1_000L, 15_000L, 29_000L, 43_000L)))
        assertEquals("CALL_ME", FlashSignalPattern.decode(listOf(1_000L, 21_000L, 38_000L)))
    }

    @Test fun mapsDecodedPresetsToHumanReadableChatMessages() {
        assertEquals("Flash call received", FlashSignalPattern.toChatMessage("FLASH_CALL"))
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
        assertNull(FlashSignalPattern.decode(listOf(1_000L, 6_000L, 18_000L), 0L))
        assertNull(FlashSignalPattern.decode(listOf(1_000L, 1_000L)))
    }

    @Test fun timingToleranceDoesNotCausePresetCollisions() {
        assertEquals("PING", FlashSignalPattern.decode(listOf(1_000L, 6_100L, 18_900L), 1_000L))
        assertNull(FlashSignalPattern.decode(listOf(1_000L, 6_000L), 1_000L))
        assertNull(FlashSignalPattern.decode(listOf(1_000L, 8_000L), 1_000L))
        // A single pair of ordinary calls must never become the shortest preset.
        assertNull(FlashSignalPattern.decode(listOf(1_000L, 6_000L), 1_000L))
        assertNull(FlashSignalPattern.decode(listOf(1_000L, 15_000L, 23_000L), 1_000L))
    }

    @Test fun normalizesNigerianLocalAndInternationalPhoneFormats() {
        assertEquals("2348031234567", FlashTrustedSenders.normalize("0803 123 4567"))
        assertEquals("2348031234567", FlashTrustedSenders.normalize("+234 803 123 4567"))
        assertEquals("447700900123", FlashTrustedSenders.normalize("+44 7700 900123"))
    }

    @Test fun acceptsTimingVariationWithinOneSecond() {
        assertEquals("PING", FlashSignalPattern.decode(listOf(1_000L, 5_850L)))
        assertEquals("COME", FlashSignalPattern.decode(listOf(1_000L, 11_900L, 19_100L)))
        assertEquals("URGENT", FlashSignalPattern.decode(listOf(1_000L, 14_900L, 28_100L, 43_000L)))
        assertEquals("CALL_ME", FlashSignalPattern.decode(listOf(1_000L, 20_900L, 37_100L)))
    }
}
