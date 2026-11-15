package com.nearer.app

/** Pure sample-frame envelope used by both the offline mixer and its unit tests. */
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

    if (fadeInFrames > 0L && safeFrame < fadeInFrames) {
        envelope *= (safeFrame.toFloat() / fadeInFrames.toFloat()).coerceIn(0f, 1f)
    }

    if (fadeOutFrames > 0L && totalFrames > 0L) {
        val fadeStart = (totalFrames - fadeOutFrames).coerceAtLeast(0L)
        if (safeFrame >= fadeStart) {
            val remaining = (totalFrames - safeFrame).coerceAtLeast(0L)
            envelope *= (remaining.toFloat() / fadeOutFrames.toFloat()).coerceIn(0f, 1f)
        }
    }
    return envelope
}
