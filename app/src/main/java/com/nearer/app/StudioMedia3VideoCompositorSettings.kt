package com.nearer.app

import android.util.Size
import androidx.media3.common.VideoCompositorSettings
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.OverlaySettings
import androidx.media3.effect.StaticOverlaySettings

/** Normalized layout for one Composition sequence, in Media3 normalized device coordinates. */
internal data class StudioCompositorLayerLayout(
    val inputId: Int,
    val left: Float,
    val bottom: Float,
    val right: Float,
    val top: Float,
    val rotationDegrees: Float = 0f,
    val opacity: Float = 1f,
    val zIndex: Int = inputId
)

internal data class StudioCompositorLayerPlacement(
    val inputId: Int, val centerX: Float, val centerY: Float,
    val scaleX: Float, val scaleY: Float, val rotationDegrees: Float,
    val opacity: Float, val zIndex: Int
)

internal data class StudioCompositorLayoutCompilation(
    val placements: List<StudioCompositorLayerPlacement>,
    val errors: List<String>
) {
    val isValid: Boolean get() = errors.isEmpty()
}

/** Pure geometry validation and conversion; safe for local JVM tests. */
internal object StudioCompositorLayoutPlanner {
    const val MAX_INPUTS = 32

    fun plan(layouts: List<StudioCompositorLayerLayout>): StudioCompositorLayoutCompilation {
        val errors = mutableListOf<String>()
        if (layouts.size > MAX_INPUTS) errors += "Composition exceeds the 32 visual-input limit."
        val ids = mutableSetOf<Int>()
        layouts.forEach { layer ->
            if (layer.inputId !in 0 until MAX_INPUTS) errors += "Input ID must be within 0..31."
            if (!ids.add(layer.inputId)) errors += "Input ID ${layer.inputId} is duplicated."
            val values = listOf(layer.left, layer.bottom, layer.right, layer.top, layer.rotationDegrees, layer.opacity)
            if (values.any { !it.isFinite() }) {
                errors += "Input ${layer.inputId} contains a non-finite layout value."
            } else {
                if (layer.left !in -1f..1f || layer.right !in -1f..1f ||
                    layer.bottom !in -1f..1f || layer.top !in -1f..1f ||
                    layer.left >= layer.right || layer.bottom >= layer.top
                ) errors += "Input ${layer.inputId} has invalid normalized bounds."
                if (layer.rotationDegrees !in -36_000f..36_000f) {
                    errors += "Input ${layer.inputId} rotation is outside -36000..36000 degrees."
                }
                if (layer.opacity !in 0f..1f) errors += "Input ${layer.inputId} opacity must be within 0..1."
            }
        }
        if (errors.isNotEmpty()) return StudioCompositorLayoutCompilation(emptyList(), errors.distinct())
        val placements = layouts.map { layer ->
            StudioCompositorLayerPlacement(
                inputId = layer.inputId,
                centerX = (layer.left + layer.right) / 2f,
                centerY = (layer.bottom + layer.top) / 2f,
                scaleX = (layer.right - layer.left) / 2f,
                scaleY = (layer.top - layer.bottom) / 2f,
                rotationDegrees = ((layer.rotationDegrees % 360f) + 360f) % 360f,
                opacity = layer.opacity,
                zIndex = layer.zIndex
            )
        }.sortedWith(compareBy<StudioCompositorLayerPlacement> { it.zIndex }.thenBy { it.inputId })
        return StudioCompositorLayoutCompilation(placements, emptyList())
    }
}

/**
 * Media3 adapter for per-sequence placement, scale, rotation and alpha.
 * It does not claim to implement non-source-over blend modes or masks.
 */
@UnstableApi
internal class StudioMedia3VideoCompositorSettings(
    layouts: List<StudioCompositorLayerLayout>,
    private val fallbackOutputSize: Size = Size(1920, 1080)
) : VideoCompositorSettings {
    private val compilation = StudioCompositorLayoutPlanner.plan(layouts)
    private val byInputId = compilation.placements.associateBy { it.inputId }

    init {
        require(compilation.isValid) {
            "Invalid visual composition layout: ${compilation.errors.joinToString(" ")}"
        }
        require(fallbackOutputSize.width > 0 && fallbackOutputSize.height > 0) {
            "Output dimensions must be positive."
        }
    }

    override fun getOutputSize(inputSizes: MutableList<Size>): Size {
        if (inputSizes.isEmpty()) return fallbackOutputSize
        return Size(inputSizes.maxOf { it.width }.coerceAtLeast(1), inputSizes.maxOf { it.height }.coerceAtLeast(1))
    }

    override fun getOverlaySettings(inputId: Int, presentationTimeUs: Long): OverlaySettings {
        val placement = byInputId[inputId] ?: return StaticOverlaySettings.Builder().build()
        return StaticOverlaySettings.Builder()
            .setOverlayFrameAnchor(0f, 0f)
            .setBackgroundFrameAnchor(placement.centerX, placement.centerY)
            .setScale(placement.scaleX, placement.scaleY)
            .setRotationDegrees(placement.rotationDegrees)
            .setAlphaScale(placement.opacity)
            .build()
    }

    fun validationErrors(): List<String> = compilation.errors
}
