package com.nearer.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StudioParticlePresetsTest {
    @Test fun eachPresetProducesValidDistinctDefaults() {
        val presets = StudioParticlePreset.entries.map { preset ->
            StudioParticlePresets.defaultEmitter(
                id = preset.name,
                seed = preset.ordinal.toLong(),
                preset = preset,
                startTimeUs = 0L,
                endTimeUs = 10_000_000L
            )
        }
        presets.forEach { assertTrue(StudioOfflineParticleEngine.validate(listOf(it)).isEmpty()) }
        assertEquals(5, presets.map { it.preset }.distinct().size)
        assertTrue(presets.first { it.preset == StudioParticlePreset.SMOKE }.minLifetimeMs >
            presets.first { it.preset == StudioParticlePreset.FIRE }.minLifetimeMs)
        assertTrue(presets.first { it.preset == StudioParticlePreset.EXPLOSION }.spread >
            presets.first { it.preset == StudioParticlePreset.LIGHTNING }.spread)
    }

    @Test fun risingPresetsMoveUpwardInNormalizedDeviceCoordinates() {
        val fire = StudioParticlePresets.defaultEmitter("fire", 1L, StudioParticlePreset.FIRE, 0L, 5_000_000L)
        val smoke = StudioParticlePresets.defaultEmitter("smoke", 2L, StudioParticlePreset.SMOKE, 0L, 5_000_000L)
        val fireParticle = StudioOfflineParticleEngine.frameAt(500_000L, listOf(fire)).particles.first()
        val smokeParticle = StudioOfflineParticleEngine.frameAt(500_000L, listOf(smoke)).particles.first()
        assertTrue(fireParticle.y > fire.originY)
        assertTrue(smokeParticle.y > smoke.originY)
    }

    @Test fun explosionAndSparksSpreadAcrossMultipleDirections() {
        val explosion = StudioParticlePresets.defaultEmitter("explosion", 12L, StudioParticlePreset.EXPLOSION, 0L, 2_000_000L)
        val particles = StudioOfflineParticleEngine.frameAt(200_000L, listOf(explosion)).particles
        assertTrue(particles.size > 5)
        assertTrue(particles.any { it.x > explosion.originX })
        assertTrue(particles.any { it.x < explosion.originX })
    }
}
