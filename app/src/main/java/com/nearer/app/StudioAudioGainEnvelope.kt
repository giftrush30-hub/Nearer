package com.nearer.app

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** Pure sample-frame envelope used by the offline mixer and its unit tests. */
internal fun studioAudioGainAtFrame(
    frame: Long,
    totalFrames: Long,
    baseGain: Float,
    fadeInFrames: Long,
    fadeOutFrames: Long
): Float {
    val safeGain = baseGain.coerceIn(0f, 1f)
    val safeFrame = frame.coerceAtLeast(0L)
    var envelope = safeGain
    val safeFadeInFrames = if (totalFrames > 0L) fadeInFrames.coerceAtMost(totalFrames) else fadeInFrames
    val safeFadeOutFrames = if (totalFrames > 0L) fadeOutFrames.coerceAtMost(totalFrames) else fadeOutFrames

    // Equal-power curves give a smoother perceived level when two tracks overlap:
    // fade-in uses sine, while fade-out uses the complementary cosine curve.
    if (safeFadeInFrames > 0L && safeFrame < safeFadeInFrames) {
        val progress = (safeFrame.toDouble() / safeFadeInFrames.toDouble()).coerceIn(0.0, 1.0)
        envelope *= sin(progress * PI * 0.5).toFloat()
    }

    if (safeFadeOutFrames > 0L && totalFrames > 0L) {
        val fadeStart = (totalFrames - safeFadeOutFrames).coerceAtLeast(0L)
        if (safeFrame >= fadeStart) {
            val progress = ((safeFrame - fadeStart).toDouble() / safeFadeOutFrames.toDouble())
                .coerceIn(0.0, 1.0)
            envelope *= cos(progress * PI * 0.5).toFloat()
        }
    }
    return envelope.coerceIn(0f, safeGain)
}

/** Stereo balance: the opposite channel is attenuated while the selected side stays full. */
internal fun studioAudioPanFactors(pan: Float): Pair<Float, Float> {
    val balanced = pan.coerceIn(-1f, 1f)
    return if (balanced < 0f) 1f to (1f + balanced) else (1f - balanced) to 1f
}
