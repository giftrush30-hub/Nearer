package com.nearer.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StudioParticleEmitterFactoryTest {
    @Test fun createsValidEmitterForEveryPreset() {
        StudioParticlePreset.entries.forEach { preset ->
            val emitter = StudioParticleEmitterFactory.forClip(preset, 8_000_000L, 35f, 123L)
            assertTrue(StudioOfflineParticleEngine.validate(listOf(emitter)).isEmpty())
            assertEquals(preset, emitter.preset)
            assertEquals(0L, emitter.startTimeUs)
            assertEquals(8_000_000L, emitter.endTimeUs)
        }
    }

    @Test fun clampsUnsafeDurationAndRateToEngineLimits() {
        val emitter = StudioParticleEmitterFactory.forClip(
            StudioParticlePreset.FIRE, Long.MAX_VALUE, Float.POSITIVE_INFINITY, 1L
        )
        assertEquals(86_400_000_000L, emitter.endTimeUs)
        assertEquals(1f, emitter.particlesPerSecond, 0f)
        assertTrue(StudioOfflineParticleEngine.validate(listOf(emitter)).isEmpty())
    }

    @Test fun differentSeedsProduceDifferentFrames() {
        val a = StudioParticleEmitterFactory.forClip(StudioParticlePreset.SPARKS, 3_000_000L, 40f, 1L)
        val b = StudioParticleEmitterFactory.forClip(StudioParticlePreset.SPARKS, 3_000_000L, 40f, 2L)
        assertTrue(StudioOfflineParticleEngine.frameAt(500_000L, listOf(a)).particles !=
            StudioOfflineParticleEngine.frameAt(500_000L, listOf(b)).particles)
    }
}
