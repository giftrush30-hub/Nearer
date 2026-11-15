package com.nearer.app

import org.junit.Assert.assertTrue
import org.junit.Test

class StudioVocalProfileTest {
    @Test
    fun everyProfileUsesSafeProcessingRanges() {
        StudioVocalProfile.values().forEach { profile ->
            assertTrue(profile.lowCutHz in 60f..140f)
            assertTrue(profile.presence in 0f..0.35f)
            assertTrue(profile.deEssAmount in 0f..0.6f)
            assertTrue(profile.compressorThreshold in 0.2f..0.5f)
            assertTrue(profile.compressorRatio in 1.5f..5f)
            assertTrue(profile.gateThreshold in 0f..0.01f)
            assertTrue(profile.roomMix in 0f..0.1f)
            assertTrue(profile.drive in 1f..1.2f)
        }
    }

    @Test
    fun warmCondenserProfileIsAvailable() {
        assertTrue(StudioVocalProfile.values().contains(StudioVocalProfile.WARM_CONDENSER))
        assertTrue(StudioVocalProfile.WARM_CONDENSER.title.contains("Condenser"))
    }
}
