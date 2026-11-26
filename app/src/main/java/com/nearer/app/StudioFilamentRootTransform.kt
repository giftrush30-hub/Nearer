package com.nearer.app

import android.opengl.Matrix
import com.google.android.filament.utils.ModelViewer

internal data class StudioFilamentTransformSettings(
    val scale: Float = 1f,
    val rotationYDegrees: Float = 0f,
    val verticalOffset: Float = 0f
)

internal object StudioFilamentTransformPlanner {
    fun validate(settings: StudioFilamentTransformSettings): List<String> {
        val errors = mutableListOf<String>()
        if (!settings.scale.isFinite() || settings.scale !in 0.1f..4f) errors += "Model scale must be within 0.1x..4x."
        if (!settings.rotationYDegrees.isFinite() || settings.rotationYDegrees !in -360f..360f) errors += "Model rotation must be within -360..360 degrees."
        if (!settings.verticalOffset.isFinite() || settings.verticalOffset !in -1f..1f) errors += "Model height must be within -1..1."
        return errors
    }
}

/** Applies user transforms on top of ModelViewer's normalized unit-cube root transform. */
internal object StudioFilamentRootTransform {
    fun apply(
        viewer: ModelViewer,
        normalizedRootTransform: FloatArray,
        settings: StudioFilamentTransformSettings
    ) {
        require(normalizedRootTransform.size == 16) { "A 4x4 root transform is required." }
        require(StudioFilamentTransformPlanner.validate(settings).isEmpty()) { "Invalid 3D transform settings." }
        val asset = requireNotNull(viewer.asset) { "No GLB model is loaded." }
        val userTransform = FloatArray(16)
        Matrix.setIdentityM(userTransform, 0)
        Matrix.translateM(userTransform, 0, 0f, settings.verticalOffset, 0f)
        Matrix.rotateM(userTransform, 0, settings.rotationYDegrees, 0f, 1f, 0f)
        Matrix.scaleM(userTransform, 0, settings.scale, settings.scale, settings.scale)
        val combined = FloatArray(16)
        Matrix.multiplyMM(combined, 0, userTransform, 0, normalizedRootTransform, 0)
        val transformManager = viewer.engine.transformManager
        val rootInstance = transformManager.getInstance(asset.root)
        transformManager.setTransform(rootInstance, combined)
    }
}
