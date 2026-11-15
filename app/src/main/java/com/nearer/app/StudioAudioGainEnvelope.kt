package com.nearer.app

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

/** Stereo balance: the opposite channel is attenuated while the selected side stays full. */
internal fun studioAudioPanFactors(pan: Float): Pair<Float, Float> {
    val balanced = pan.coerceIn(-1f, 1f)
    return if (balanced < 0f) 1f to (1f + balanced) else (1f - balanced) to 1f
}
