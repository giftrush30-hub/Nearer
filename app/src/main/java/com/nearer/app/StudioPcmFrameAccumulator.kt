package com.nearer.app

/**
 * Reassembles arbitrarily-sized PCM reads into fixed pitch-analysis frames.
 * The input may be smaller or larger than the remaining space in the current frame.
 * The supplied frame is reused; consumers must finish processing it before returning.
 */
internal class StudioPcmFrameAccumulator(frameSize: Int) {
    private val frame = FloatArray(frameSize.also { require(it >= 512) })
    private var offset = 0

    fun append(input: FloatArray, count: Int, onFrame: (FloatArray) -> Unit) {
        require(count in 0..input.size)
        var sourceOffset = 0
        while (sourceOffset < count) {
            val copied = minOf(count - sourceOffset, frame.size - offset)
            System.arraycopy(input, sourceOffset, frame, offset, copied)
            sourceOffset += copied
            offset += copied
            if (offset == frame.size) {
                onFrame(frame)
                offset = 0
            }
        }
    }

    fun reset() {
        offset = 0
    }
}
