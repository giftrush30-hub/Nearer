package com.nearer.app

internal data class StudioGpuBlendInput(
    val inputId: Int, val zIndex: Int, val blendMode: StudioVisualBlendMode,
    val opacity: Float, val presentationTimeUs: Long, val width: Int, val height: Int
)
internal data class StudioGpuBlendPass(
    val inputId: Int, val zIndex: Int, val modeCode: Int, val opacity: Float,
    val presentationTimeUs: Long, val width: Int, val height: Int
)
internal data class StudioGpuBlendPlan(
    val outputWidth: Int, val outputHeight: Int, val passes: List<StudioGpuBlendPass>, val errors: List<String>
) {
    val isValid: Boolean get() = errors.isEmpty()
    val requiresNonNormalBlend: Boolean get() = passes.any { it.modeCode != StudioGpuBlendModeCode.NORMAL }
}
internal object StudioGpuBlendModeCode {
    const val NORMAL = 0; const val ADD = 1; const val MULTIPLY = 2
    const val SCREEN = 3; const val OVERLAY = 4; const val DARKEN = 5; const val LIGHTEN = 6
    fun from(mode: StudioVisualBlendMode): Int = when (mode) {
        StudioVisualBlendMode.NORMAL -> NORMAL
        StudioVisualBlendMode.ADD -> ADD
        StudioVisualBlendMode.MULTIPLY -> MULTIPLY
        StudioVisualBlendMode.SCREEN -> SCREEN
        StudioVisualBlendMode.OVERLAY -> OVERLAY
        StudioVisualBlendMode.DARKEN -> DARKEN
        StudioVisualBlendMode.LIGHTEN -> LIGHTEN
    }
}
/**
 * Pure preflight planner for a ping-pong GPU compositor. This is deliberately not a
 * VideoCompositor: current public Media3 graph factories do not expose custom compositor
 * injection, so runtime scheduling and texture ownership require a custom graph.
 */
internal object StudioGpuBlendPlanner {
    const val MAX_SIMULTANEOUS_LAYERS = 8
    const val MAX_PIXELS = 16_777_216
    const val TIMESTAMP_TOLERANCE_US = 1_000L
    fun plan(inputs: List<StudioGpuBlendInput>): StudioGpuBlendPlan {
        val errors = mutableListOf<String>()
        if (inputs.isEmpty()) errors += "At least one visual input is required."
        if (inputs.size > MAX_SIMULTANEOUS_LAYERS) errors += "GPU composition is limited to 8 simultaneous inputs per render batch."
        val ids = mutableSetOf<Int>()
        inputs.forEach { input ->
            if (input.inputId !in 0 until MAX_SIMULTANEOUS_LAYERS) errors += "GPU input ID must be within 0..7."
            if (!ids.add(input.inputId)) errors += "GPU input ID ${input.inputId} is duplicated."
            if (input.width <= 0 || input.height <= 0 || input.width.toLong() * input.height > MAX_PIXELS)
                errors += "GPU input ${input.inputId} has invalid or oversized dimensions."
            if (!input.opacity.isFinite() || input.opacity !in 0f..1f)
                errors += "GPU input ${input.inputId} opacity must be within 0..1."
            if (input.presentationTimeUs < 0L) errors += "GPU input ${input.inputId} has a negative presentation timestamp."
        }
        if (inputs.isNotEmpty()) {
            val anchor = inputs.minOf { it.presentationTimeUs }
            if (inputs.any { kotlin.math.abs(it.presentationTimeUs - anchor) > TIMESTAMP_TOLERANCE_US })
                errors += "GPU inputs are not frame-synchronized within 1 ms."
        }
        if (errors.isNotEmpty()) return StudioGpuBlendPlan(0, 0, emptyList(), errors.distinct())
        val passes = inputs.sortedWith(compareBy<StudioGpuBlendInput> { it.zIndex }.thenBy { it.inputId }).map {
            StudioGpuBlendPass(it.inputId, it.zIndex, StudioGpuBlendModeCode.from(it.blendMode),
                it.opacity, it.presentationTimeUs, it.width, it.height)
        }
        return StudioGpuBlendPlan(inputs.maxOf { it.width }, inputs.maxOf { it.height }, passes, emptyList())
    }
}
