package com.nearer.app

internal object StudioParticlePresets {
    fun defaultEmitter(
        id: String,
        seed: Long,
        preset: StudioParticlePreset,
        startTimeUs: Long,
        endTimeUs: Long
    ): StudioParticleEmitter {
        val values = when (preset) {
            StudioParticlePreset.FIRE -> EmitterDefaults(
                rate = 36f, maxParticles = 1_000, originY = -0.84f,
                velocityX = 0f, velocityY = 0.95f, spread = 0.72f, gravity = -0.12f,
                minLife = 600, maxLife = 1_300, minSize = 0.018f, maxSize = 0.052f
            )
            StudioParticlePreset.SMOKE -> EmitterDefaults(
                rate = 14f, maxParticles = 700, originY = -0.84f,
                velocityX = 0f, velocityY = 0.48f, spread = 0.9f, gravity = -0.035f,
                minLife = 1_600, maxLife = 5_000, minSize = 0.028f, maxSize = 0.09f
            )
            StudioParticlePreset.EXPLOSION -> EmitterDefaults(
                rate = 180f, maxParticles = 2_500, originY = 0f,
                velocityX = 0f, velocityY = 2.8f, spread = 6.283185f, gravity = -1.3f,
                minLife = 300, maxLife = 1_200, minSize = 0.008f, maxSize = 0.038f
            )
            StudioParticlePreset.SPARKS -> EmitterDefaults(
                rate = 80f, maxParticles = 1_200, originY = 0f,
                velocityX = 0f, velocityY = 2.2f, spread = 6.283185f, gravity = -2f,
                minLife = 250, maxLife = 900, minSize = 0.004f, maxSize = 0.02f
            )
            StudioParticlePreset.LIGHTNING -> EmitterDefaults(
                rate = 8f, maxParticles = 64, originY = 0f,
                velocityX = 0f, velocityY = 1.2f, spread = 0.42f, gravity = 0f,
                minLife = 80, maxLife = 180, minSize = 0.012f, maxSize = 0.04f
            )
        }
        return StudioParticleEmitter(
            id = id,
            seed = seed,
            preset = preset,
            startTimeUs = startTimeUs,
            endTimeUs = endTimeUs,
            particlesPerSecond = values.rate,
            maxParticles = values.maxParticles,
            originX = 0f,
            originY = values.originY,
            velocityX = values.velocityX,
            velocityY = values.velocityY,
            spread = values.spread,
            gravity = values.gravity,
            minLifetimeMs = values.minLife,
            maxLifetimeMs = values.maxLife,
            minSize = values.minSize,
            maxSize = values.maxSize
        )
    }

    private data class EmitterDefaults(
        val rate: Float,
        val maxParticles: Int,
        val originY: Float,
        val velocityX: Float,
        val velocityY: Float,
        val spread: Float,
        val gravity: Float,
        val minLife: Int,
        val maxLife: Int,
        val minSize: Float,
        val maxSize: Float
    )
}
