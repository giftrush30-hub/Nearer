package com.nearer.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StudioOfflineParticleEngineTest {
    private fun emitter(
        id: String = "fire",
        preset: StudioParticlePreset = StudioParticlePreset.FIRE,
        seed: Long = 42L,
        rate: Float = 20f
    ) = StudioParticleEmitter(
        id = id, seed = seed, preset = preset,
        startTimeUs = 0L, endTimeUs = 10_000_000L,
        particlesPerSecond = rate, maxParticles = 100,
        originX = 0f, originY = 0f, velocityX = 0f, velocityY = -1f,
        spread = 0.8f, gravity = -0.2f,
        minLifetimeMs = 500, maxLifetimeMs = 1_000,
        minSize = 0.01f, maxSize = 0.05f
    )

    @Test fun regeneratesIdenticalFrameAtSameTimestamp() {
        val emitters = listOf(emitter(), emitter("smoke", StudioParticlePreset.SMOKE, 99L))
        val first = StudioOfflineParticleEngine.frameAt(750_000L, emitters)
        val second = StudioOfflineParticleEngine.frameAt(750_000L, emitters)
        assertTrue(first.isValid)
        assertEquals(first.particles, second.particles)
        assertTrue(first.particles.isNotEmpty())
    }

    @Test fun frameAtDoesNotDependOnEarlierFrameEvaluationOrder() {
        val source = listOf(emitter())
        val laterFirst = StudioOfflineParticleEngine.frameAt(900_000L, source)
        StudioOfflineParticleEngine.frameAt(100_000L, source)
        val laterSecond = StudioOfflineParticleEngine.frameAt(900_000L, source)
        assertEquals(laterFirst.particles, laterSecond.particles)
    }

    @Test fun seedChangesParticleMotionAndAppearanceDeterministically() {
        val a = StudioOfflineParticleEngine.frameAt(500_000L, listOf(emitter(seed = 7L)))
        val b = StudioOfflineParticleEngine.frameAt(500_000L, listOf(emitter(seed = 8L)))
        assertTrue(a.isValid && b.isValid)
        assertFalse(a.particles == b.particles)
    }

    @Test fun supportsAllFiveOfflineEffectPresets() {
        val emitters = StudioParticlePreset.entries.mapIndexed { index, preset ->
            emitter(id = preset.name, preset = preset, seed = index.toLong())
        }
        val frame = StudioOfflineParticleEngine.frameAt(300_000L, emitters)
        assertTrue(frame.isValid)
        assertEquals(5, frame.particles.map { it.preset }.distinct().size)
    }

    @Test fun rejectsUnsafeAndDuplicateEmitterConfiguration() {
        val invalid = emitter().copy(maxParticles = 20_000)
        val frame = StudioOfflineParticleEngine.frameAt(100_000L, listOf(invalid, invalid))
        assertFalse(frame.isValid)
        assertTrue(frame.particles.isEmpty())
        assertTrue(frame.errors.any { it.contains("unique") })
        assertTrue(frame.errors.any { it.contains("10000") })
    }

    @Test fun emitterContinuesSpawningAfterItsActiveParticleCapHasBeenReached() {
        val capped = emitter(rate = 20f).copy(
            maxParticles = 5,
            minLifetimeMs = 100,
            maxLifetimeMs = 100
        )
        val frame = StudioOfflineParticleEngine.frameAt(500_000L, listOf(capped))
        assertTrue(frame.isValid)
        assertTrue(frame.particles.isNotEmpty())
        assertTrue(frame.particles.all { it.particleIndex >= 8 })
        assertTrue(frame.particles.size <= 5)
    }

    @Test fun particlesExpireAndEmitterStopsAtEndTime() {
        val short = emitter(rate = 0.1f).copy(minLifetimeMs = 100, maxLifetimeMs = 100, endTimeUs = 200_000L)
        val active = StudioOfflineParticleEngine.frameAt(50_000L, listOf(short))
        val expired = StudioOfflineParticleEngine.frameAt(150_000L, listOf(short))
        val ended = StudioOfflineParticleEngine.frameAt(250_000L, listOf(short))
        assertTrue(active.particles.isNotEmpty())
        assertTrue(expired.particles.isEmpty())
        assertTrue(ended.particles.isEmpty())
    }
}
