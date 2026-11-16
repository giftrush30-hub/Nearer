package com.nearer.app

internal enum class StudioVideoTransitionStyle {
    CUT,
    FADE_TO_BLACK
}

internal data class StudioVideoTransitionSegment(
    val startUs: Long,
    val endUs: Long,
    val brightness: Float
)

internal object StudioVideoTransitionPlanner {
    fun plan(
        clipDurationMs: Long,
        transitionDurationMs: Int,
        includeFadeIn: Boolean,
        includeFadeOut: Boolean,
        steps: Int = 16
    ): List<StudioVideoTransitionSegment> {
        if (clipDurationMs <= 0L || steps <= 0 || (!includeFadeIn && !includeFadeOut)) return emptyList()
        val durationUs = clipDurationMs * 1_000L
        val fadeUs = (transitionDurationMs.coerceIn(200, 1_000) * 1_000L)
            .coerceAtMost(durationUs / if (includeFadeIn && includeFadeOut) 2L else 1L)
        if (fadeUs <= 0L) return emptyList()
        val safeSteps = minOf(steps, fadeUs.toInt().coerceAtLeast(1))
        val result = ArrayList<StudioVideoTransitionSegment>(safeSteps * 2)
        if (includeFadeIn) {
            repeat(safeSteps) { step ->
                val start = step * fadeUs / safeSteps
                val end = (step + 1L) * fadeUs / safeSteps
                result += StudioVideoTransitionSegment(start, end, -1f + step.toFloat() / safeSteps)
            }
        }
        if (includeFadeOut) {
            repeat(safeSteps) { step ->
                val start = durationUs - fadeUs + step * fadeUs / safeSteps
                val end = durationUs - fadeUs + (step + 1L) * fadeUs / safeSteps
                result += StudioVideoTransitionSegment(start, end, -(step + 1f) / safeSteps)
            }
        }
        return result.filter { it.endUs > it.startUs }
    }
}
