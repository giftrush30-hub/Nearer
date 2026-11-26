package com.nearer.app

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

internal enum class StudioParticlePreset { FIRE, SMOKE, EXPLOSION, SPARKS, LIGHTNING }

internal data class StudioParticleEmitter(
    val id: String,
    val seed: Long,
    val preset: StudioParticlePreset,
    val startTimeUs: Long,
    val endTimeUs: Long,
    val particlesPerSecond: Float,
    val maxParticles: Int,
    val originX: Float,
    val originY: Float,
    val velocityX: Float,
    val velocityY: Float,
    val spread: Float,
    val gravity: Float,
    val minLifetimeMs: Int,
    val maxLifetimeMs: Int,
    val minSize: Float,
    val maxSize: Float
)

internal data class StudioParticleInstance(
    val emitterId: String,
    val particleIndex: Int,
    val x: Float,
    val y: Float,
    val size: Float,
    val opacity: Float,
    val rotationDegrees: Float,
    val colorArgb: Int,
    val preset: StudioParticlePreset
)

internal data class StudioParticleFrame(
    val timestampUs: Long,
    val particles: List<StudioParticleInstance>,
    val errors: List<String>
) {
    val isValid: Boolean get() = errors.isEmpty()
}

/**
 * Stateless, deterministic particle evaluation. A frame can be regenerated at any timestamp
 * without simulating earlier frames, which makes seeking and offline export reproducible.
 */
internal object StudioOfflineParticleEngine {
    const val MAX_EMITTERS = 16
    const val MAX_PARTICLES_PER_EMITTER = 10_000
    const val MAX_PARTICLES_PER_FRAME = 50_000
    private const val MAX_TIMELINE_US = 86_400_000_000L
    private const val MICROS_PER_SECOND = 1_000_000.0

    fun validate(emitters: List<StudioParticleEmitter>): List<String> {
        val errors = mutableListOf<String>()
        if (emitters.size > MAX_EMITTERS) errors += "Composition exceeds the 16-emitter limit."
        val ids = mutableSetOf<String>()
        emitters.forEach { emitter ->
            if (emitter.id.isBlank() || !ids.add(emitter.id)) errors += "Particle emitter IDs must be non-empty and unique."
            if (emitter.startTimeUs < 0L || emitter.endTimeUs <= emitter.startTimeUs || emitter.endTimeUs > MAX_TIMELINE_US) errors += "${emitter.id}: invalid emitter time range."
            if (!emitter.particlesPerSecond.isFinite() || emitter.particlesPerSecond !in 0.1f..2_000f) {
                errors += "${emitter.id}: particle rate must be within 0.1..2000 per second."
            }
            if (emitter.maxParticles !in 1..MAX_PARTICLES_PER_EMITTER) errors += "${emitter.id}: particle cap must be within 1..10000."
            val floats = listOf(emitter.originX, emitter.originY, emitter.velocityX, emitter.velocityY, emitter.spread, emitter.gravity, emitter.minSize, emitter.maxSize)
            if (floats.any { !it.isFinite() }) errors += "${emitter.id}: particle settings contain a non-finite value."
            if (emitter.originX !in -4f..4f || emitter.originY !in -4f..4f) errors += "${emitter.id}: origin is outside the safe normalized range."
            if (emitter.spread !in 0f..6.2832f) errors += "${emitter.id}: spread must be within 0..2π radians."
            if (emitter.gravity !in -20f..20f) errors += "${emitter.id}: gravity is outside the safe range."
            if (emitter.minLifetimeMs !in 1..60_000 || emitter.maxLifetimeMs !in emitter.minLifetimeMs..60_000) {
                errors += "${emitter.id}: lifetime range is invalid."
            }
            if (emitter.minSize <= 0f || emitter.maxSize < emitter.minSize || emitter.maxSize > 2f) {
                errors += "${emitter.id}: particle size range is invalid."
            }
        }
        return errors.distinct()
    }

    fun frameAt(timestampUs: Long, emitters: List<StudioParticleEmitter>): StudioParticleFrame {
        if (timestampUs < 0L) return StudioParticleFrame(timestampUs, emptyList(), listOf("Frame timestamp cannot be negative."))
        val errors = validate(emitters)
        if (errors.isNotEmpty()) return StudioParticleFrame(timestampUs, emptyList(), errors)
        val output = ArrayList<StudioParticleInstance>()
        for (emitter in emitters) {
            if (timestampUs < emitter.startTimeUs || timestampUs >= emitter.endTimeUs) continue
            val elapsedUs = timestampUs - emitter.startTimeUs
            val emitted = (elapsedUs.toDouble() * emitter.particlesPerSecond.toDouble() / MICROS_PER_SECOND).toLong() + 1L
            // Keep only the most recent bounded candidate window, but continue emitting for
            // the full emitter duration after older particles expire.
            val firstPossible = max(0L, emitted - emitter.maxParticles.toLong()).toInt()
            val endExclusive = emitted.toInt()
            for (index in firstPossible until endExclusive) {
                if (output.size >= MAX_PARTICLES_PER_FRAME) {
                    return StudioParticleFrame(timestampUs, output, listOf("Particle frame exceeds the 50000-instance safety limit."))
                }
                val birthUs = emitter.startTimeUs + (index.toDouble() * MICROS_PER_SECOND / emitter.particlesPerSecond).toLong()
                val ageUs = timestampUs - birthUs
                if (ageUs < 0L) continue
                val lifetimeMs = lerpInt(emitter.minLifetimeMs, emitter.maxLifetimeMs, random01(emitter.seed, index, 1))
                val lifetimeUs = lifetimeMs * 1_000L
                if (ageUs >= lifetimeUs) continue
                val age = ageUs.toFloat() / lifetimeUs.toFloat()
                val angle = (random01(emitter.seed, index, 2) - 0.5f) * emitter.spread - PI.toFloat() / 2f
                val speed = sqrt(emitter.velocityX * emitter.velocityX + emitter.velocityY * emitter.velocityY)
                val jitter = (random01(emitter.seed, index, 3) - 0.5f) * 0.2f
                val vx = cos(angle) * speed + emitter.velocityX * jitter
                val vy = sin(angle) * speed + emitter.velocityY * jitter
                val seconds = ageUs.toFloat() / 1_000_000f
                val driftX = (random01(emitter.seed, index, 4) - 0.5f) * emitter.spread * age
                val x = emitter.originX + vx * seconds + driftX
                val y = emitter.originY + vy * seconds + 0.5f * emitter.gravity * seconds * seconds
                val fade = when (emitter.preset) {
                    StudioParticlePreset.LIGHTNING -> min(1f, min(age * 8f, (1f - age) * 8f))
                    StudioParticlePreset.EXPLOSION, StudioParticlePreset.SPARKS -> 1f - age
                    else -> min(1f, (1f - age) * 1.5f)
                }.coerceIn(0f, 1f)
                val growth = when (emitter.preset) {
                    StudioParticlePreset.SMOKE -> 1f + age * 2.5f
                    StudioParticlePreset.EXPLOSION -> 1f + age * 0.6f
                    else -> 1f - age * 0.35f
                }
                output += StudioParticleInstance(
                    emitterId = emitter.id,
                    particleIndex = index,
                    x = x,
                    y = y,
                    size = lerp(emitter.minSize, emitter.maxSize, random01(emitter.seed, index, 5)) * growth,
                    opacity = fade,
                    rotationDegrees = random01(emitter.seed, index, 6) * 360f + age * 180f,
                    colorArgb = colorFor(emitter.preset, age),
                    preset = emitter.preset
                )
            }
        }
        return StudioParticleFrame(timestampUs, output, emptyList())
    }

    private fun random01(seed: Long, index: Int, salt: Int): Float {
        var value = seed xor (index.toLong() * -7046029254386353131L) xor (salt.toLong() * -4658895280553007687L)
        value = (value xor (value ushr 30)) * -4658895280553007687L
        value = (value xor (value ushr 27)) * -7723592293110705685L
        value = value xor (value ushr 31)
        return ((value ushr 40).toInt() and 0xFFFFFF) / 16_777_215f
    }

    private fun lerp(a: Float, b: Float, t: Float) = a + (b - a) * t
    private fun lerpInt(a: Int, b: Int, t: Float) = (a + (b - a) * t).toInt()

    private fun colorFor(preset: StudioParticlePreset, age: Float): Int {
        val startEnd = when (preset) {
            StudioParticlePreset.FIRE -> 0xFFFFF176.toInt() to 0xFFE53935.toInt()
            StudioParticlePreset.SMOKE -> 0xFFB0BEC5.toInt() to 0xFF37474F.toInt()
            StudioParticlePreset.EXPLOSION -> 0xFFFFF59D.toInt() to 0xFFD84315.toInt()
            StudioParticlePreset.SPARKS -> 0xFFFFE082.toInt() to 0xFFFF6F00.toInt()
            StudioParticlePreset.LIGHTNING -> 0xFFE3F2FD.toInt() to 0xFF64B5F6.toInt()
        }
        val t = age.coerceIn(0f, 1f)
        fun channel(shift: Int) = (((startEnd.first ushr shift) and 0xFF) * (1f - t) +
            ((startEnd.second ushr shift) and 0xFF) * t).toInt().coerceIn(0, 255)
        return (channel(24) shl 24) or (channel(16) shl 16) or (channel(8) shl 8) or channel(0)
    }
}
