package com.nearer.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FlashSignalPatternTest {
    @Test fun decodesAllPresetPatterns() {
        assertEquals("PING", FlashSignalPattern.decode(listOf(1_000L, 1_900L)))
        assertEquals("COME", FlashSignalPattern.decode(listOf(1_000L, 2_400L, 2_700L)))
        assertEquals("URGENT", FlashSignalPattern.decode(listOf(1_000L, 1_450L, 1_900L, 2_350L)))
        assertEquals("CALL_ME", FlashSignalPattern.decode(listOf(1_000L, 3_200L, 4_100L)))
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
        assertNull(FlashSignalPattern.decode(listOf(1_000L, 2_500L)))
        assertNull(FlashSignalPattern.decode(listOf(2_000L, 1_000L)))
        assertNull(FlashSignalPattern.decode(listOf(1_000L, 1_900L, 2_800L)))
    }

    @Test fun timingToleranceDoesNotCausePresetCollisions() {
        assertEquals("PING", FlashSignalPattern.decode(listOf(1_000L, 1_700L), 200L))
        assertNull(FlashSignalPattern.decode(listOf(1_000L, 1_650L), 200L))
        assertNull(FlashSignalPattern.decode(listOf(1_000L, 2_800L, 3_300L), 200L))
    }
}

