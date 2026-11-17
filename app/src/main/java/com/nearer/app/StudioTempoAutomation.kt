package com.nearer.app

import androidx.media3.common.C
import androidx.media3.common.SpeedParameters
import androidx.media3.common.util.ExperimentalApi
import androidx.media3.common.util.UnstableApi
import androidx.media3.common.audio.SpeedProvider
import kotlin.math.roundToLong

/** A speed keyframe measured in source-media milliseconds. */
internal data class StudioTempoPoint(
    val timeMs: Long,
    val speed: Float
)

/**
 * Bounded piecewise-linear tempo automation. Media3's SpeedProvider consumes discrete change
 * timestamps, so the curve is sampled on a 50 ms grid while keyframes remain user-editable.
 */
internal object StudioTempoAutomationPolicy {
    const val MIN_SPEED = 0.5f
    const val MAX_SPEED = 2.0f
    const val STEP_MS = 50L
    const val MAX_KEYFRAMES = 32
    const val MAX_AUTOMATION_MS = 30_000L

    fun normalize(points: List<StudioTempoPoint>, durationMs: Long): List<StudioTempoPoint> {
        val safeDuration = if (durationMs > 0L) durationMs.coerceAtMost(MAX_AUTOMATION_MS) else MAX_AUTOMATION_MS
        val byTime = points.map { point ->
            val snappedTime = (point.timeMs.coerceAtLeast(0L).toDouble() / STEP_MS).roundToLong() * STEP_MS
            StudioTempoPoint(
                timeMs = snappedTime.coerceIn(0L, safeDuration),
                speed = if (point.speed.isFinite()) point.speed.coerceIn(MIN_SPEED, MAX_SPEED) else 1f
            )
        }.sortedBy { it.timeMs }
            .groupBy { it.timeMs }
            .map { (_, duplicates) -> duplicates.last() }
            .toMutableList()

        if (byTime.none { it.timeMs == 0L }) byTime.add(0, StudioTempoPoint(0L, 1f))
        if (byTime.isEmpty()) return listOf(StudioTempoPoint(0L, 1f))

        val bounded = if (byTime.size <= MAX_KEYFRAMES) byTime else {
            // Preserve the starting and ending intent while keeping the list bounded.
            byTime.take(MAX_KEYFRAMES - 1) + byTime.last()
        }
        return bounded
    }

    fun twoPointRamp(startSpeed: Float, endSpeed: Float, durationMs: Long): List<StudioTempoPoint> {
        val start = if (startSpeed.isFinite()) startSpeed.coerceIn(MIN_SPEED, MAX_SPEED) else 1f
        val end = if (endSpeed.isFinite()) endSpeed.coerceIn(MIN_SPEED, MAX_SPEED) else 1f
        if (start == 1f && end == 1f) return emptyList()
        if (start == end) return listOf(StudioTempoPoint(0L, start))
        val endTimeMs = (if (durationMs > 0L) durationMs else MAX_AUTOMATION_MS)
            .coerceIn(STEP_MS, MAX_AUTOMATION_MS)
        return normalize(listOf(StudioTempoPoint(0L, start), StudioTempoPoint(endTimeMs, end)), endTimeMs)
    }

    fun speedAt(points: List<StudioTempoPoint>, timeMs: Long): Float {
        val safePoints = points.ifEmpty { listOf(StudioTempoPoint(0L, 1f)) }
        val time = timeMs.coerceAtLeast(0L)
        val previous = safePoints.lastOrNull { it.timeMs <= time } ?: safePoints.first()
        val next = safePoints.firstOrNull { it.timeMs > time } ?: return previous.speed
        val span = (next.timeMs - previous.timeMs).coerceAtLeast(1L)
        val progress = ((time - previous.timeMs).toFloat() / span.toFloat()).coerceIn(0f, 1f)
        return (previous.speed + (next.speed - previous.speed) * progress).coerceIn(MIN_SPEED, MAX_SPEED)
    }

    @OptIn(ExperimentalApi::class, UnstableApi::class)
    fun speedParameters(points: List<StudioTempoPoint>, durationMs: Long): SpeedParameters =
        SpeedParameters(StudioTempoAutomationSpeedProvider(points, durationMs), /* shouldMaintainPitch= */ true)

    /** Calculates rendered duration using the same 50 ms speed samples as the provider. */
    fun outputDurationMs(points: List<StudioTempoPoint>, sourceDurationMs: Long): Long {
        val safeDuration = sourceDurationMs.coerceAtLeast(0L)
        if (safeDuration == 0L) return 0L
        val normalized = normalize(points, safeDuration)
        var sourceCursorMs = 0L
        var outputDurationMs = 0.0
        while (sourceCursorMs < safeDuration) {
            val speed = speedAt(normalized, sourceCursorMs).coerceIn(MIN_SPEED, MAX_SPEED)
            val segmentMs = minOf(STEP_MS, safeDuration - sourceCursorMs)
            outputDurationMs += segmentMs.toDouble() / speed.toDouble()
            sourceCursorMs += segmentMs
        }
        return outputDurationMs.roundToLong().coerceAtLeast(1L)
    }

    fun nextChangeTimeUs(timeUs: Long, points: List<StudioTempoPoint>): Long {
        if (points.size < 2 || points.all { it.speed == points.first().speed }) return C.TIME_UNSET
        val currentMs = timeUs.coerceAtLeast(0L) / 1_000L
        val nextStepMs = ((currentMs / STEP_MS) + 1L) * STEP_MS
        val lastKeyframeMs = points.last().timeMs
        return if (nextStepMs <= lastKeyframeMs) nextStepMs * 1_000L else C.TIME_UNSET
    }
}

@UnstableApi
internal class StudioTempoAutomationSpeedProvider(
    points: List<StudioTempoPoint>,
    durationMs: Long
) : SpeedProvider {
    private val normalizedPoints = StudioTempoAutomationPolicy.normalize(points, durationMs)

    override fun getSpeed(timeUs: Long): Float {
        val sourceMs = (timeUs.coerceAtLeast(0L) / 1_000L / StudioTempoAutomationPolicy.STEP_MS) *
            StudioTempoAutomationPolicy.STEP_MS
        return StudioTempoAutomationPolicy.speedAt(normalizedPoints, sourceMs)
    }

    override fun getNextSpeedChangeTimeUs(timeUs: Long): Long =
        StudioTempoAutomationPolicy.nextChangeTimeUs(timeUs, normalizedPoints)
}
