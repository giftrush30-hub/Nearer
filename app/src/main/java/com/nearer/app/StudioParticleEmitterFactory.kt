package com.nearer.app

internal object StudioParticleEmitterFactory {
    fun forClip(
        preset: StudioParticlePreset,
        durationUs: Long,
        particlesPerSecond: Float,
        seed: Long
    ): StudioParticleEmitter {
        val safeDuration = durationUs.coerceIn(1L, 86_400_000_000L)
        val rate = particlesPerSecond.coerceIn(1f, 120f)
        val base = StudioParticleEmitter(
            id = "timeline-particles",
            seed = seed,
            preset = preset,
            startTimeUs = 0L,
            endTimeUs = safeDuration,
            particlesPerSecond = rate,
            maxParticles = 2_500,
            originX = 0f,
            originY = -0.65f,
            velocityX = 0f,
            velocityY = 0.5f,
            spread = 0.9f,
            gravity = -0.12f,
            minLifetimeMs = 500,
            maxLifetimeMs = 1_200,
            minSize = 0.012f,
            maxSize = 0.045f
        )
        return when (preset) {
            StudioParticlePreset.FIRE -> base.copy(
                originY = -0.82f, velocityY = 0.75f, spread = 0.55f,
                gravity = -0.18f, minLifetimeMs = 450, maxLifetimeMs = 950,
                minSize = 0.012f, maxSize = 0.035f
            )
            StudioParticlePreset.SMOKE -> base.copy(
                originY = -0.75f, velocityY = 0.35f, spread = 0.9f,
                gravity = -0.04f, minLifetimeMs = 1_200, maxLifetimeMs = 2_600,
                minSize = 0.025f, maxSize = 0.065f
            )
            StudioParticlePreset.EXPLOSION -> base.copy(
                originY = 0f, velocityX = 0.65f, velocityY = 0.65f,
                spread = 6.28318f, gravity = -0.35f,
                minLifetimeMs = 450, maxLifetimeMs = 1_400,
                minSize = 0.012f, maxSize = 0.05f
            )
            StudioParticlePreset.SPARKS -> base.copy(
                originY = -0.1f, velocityY = 0.6f, spread = 1.4f,
                gravity = -0.4f, minLifetimeMs = 250, maxLifetimeMs = 750,
                minSize = 0.006f, maxSize = 0.02f
            )
            StudioParticlePreset.LIGHTNING -> base.copy(
                originY = 0f, velocityY = 0f, spread = 0.18f,
                gravity = 0f, minLifetimeMs = 90, maxLifetimeMs = 220,
                minSize = 0.008f, maxSize = 0.022f
            )
        }
    }
}
