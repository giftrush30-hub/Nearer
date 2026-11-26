package com.nearer.app

/** Immutable request for one Filament off-screen render and downstream video frame hand-off. */
internal data class Studio3DOffscreenFrameRequest(
    val frameIndex: Int,
    val presentationTimeUs: Long,
    val width: Int,
    val height: Int,
    val rowStrideBytes: Int,
    val rgbaByteCount: Int,
    val animations: List<Studio3DAnimationExportSample>
)

internal data class Studio3DOffscreenFrameCompilation(
    val plan: Studio3DOffscreenFramePlan?,
    val errors: List<String>
) {
    val isValid: Boolean get() = plan != null && errors.isEmpty()
}

/**
 * Allocation-bounded bridge from the deterministic 3D timeline to an RGBA8 off-screen target.
 * Requests are generated one frame at a time; the entire movie is never retained in RAM.
 *
 * This is a scheduling and buffer contract only. It does not create Filament objects, read back
 * GPU pixels, feed Media3, or claim that 3D characters are already present in exported MP4s.
 */
internal class Studio3DOffscreenFramePlan private constructor(
    private val animationPlan: Studio3DAnimationExportPlan,
    val width: Int,
    val height: Int
) {
    val frameRate: Int = animationPlan.frameRate
    val frameCount: Int = animationPlan.frameCount
    val bytesPerFrame: Int = width * height * BYTES_PER_PIXEL
    val estimatedRawFrameBytes: Long = bytesPerFrame.toLong() * frameCount.toLong()

    fun frameAt(frameIndex: Int): Studio3DOffscreenFrameRequest {
        val frame = animationPlan.frameAt(frameIndex)
        return Studio3DOffscreenFrameRequest(
            frameIndex = frame.frameIndex,
            presentationTimeUs = frame.presentationTimeUs,
            width = width,
            height = height,
            rowStrideBytes = width * BYTES_PER_PIXEL,
            rgbaByteCount = bytesPerFrame,
            animations = frame.activeAnimations
        )
    }

    companion object {
        const val BYTES_PER_PIXEL = 4
        const val MAX_WIDTH = 3840
        const val MAX_HEIGHT = 2160
        const val MAX_PIXELS = 16_777_216
        const val MAX_FRAME_BYTES = 64 * 1024 * 1024
        const val MAX_ACTIVE_ACTORS_PER_FRAME = 32

        fun compile(
            animationPlan: Studio3DAnimationExportPlan,
            width: Int,
            height: Int
        ): Studio3DOffscreenFrameCompilation {
            val errors = mutableListOf<String>()
            if (width !in 1..MAX_WIDTH || height !in 1..MAX_HEIGHT ||
                width.toLong() * height.toLong() > MAX_PIXELS) {
                errors += "3D off-screen output must be within 1..3840 by 1..2160 and no larger than 16 megapixels."
            } else {
                val frameBytes = width.toLong() * height.toLong() * BYTES_PER_PIXEL
                if (frameBytes > MAX_FRAME_BYTES) errors += "One RGBA frame exceeds the 64 MiB render-buffer limit."
            }
            if (errors.isNotEmpty()) return Studio3DOffscreenFrameCompilation(null, errors)
            return Studio3DOffscreenFrameCompilation(
                Studio3DOffscreenFramePlan(animationPlan, width, height), emptyList()
            )
        }
    }
}
