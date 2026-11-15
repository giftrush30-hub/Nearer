package com.nearer.app

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import java.nio.ByteBuffer
import kotlin.math.roundToInt

/**
 * Applies one track's offline gain and fade envelope during Media3 export.
 * ToInt16PcmAudioProcessor is placed before this processor in the effects chain.
 */
@androidx.media3.common.util.UnstableApi
internal class StudioTrackGainProcessor(
    private val gain: Float,
    private val durationMs: Long,
    private val fadeInMs: Long,
    private val fadeOutMs: Long
) : BaseAudioProcessor() {

    private var frameCursor = 0L

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        if (inputAudioFormat.encoding != C.ENCODING_PCM_16BIT) {
            throw AudioProcessor.UnhandledAudioFormatException(inputAudioFormat)
        }
        return inputAudioFormat
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val inputBytes = inputBuffer.remaining()
        if (inputBytes == 0) return
        val channels = inputAudioFormat.channelCount.coerceAtLeast(1)
        val sampleCount = inputBytes / 2
        val outputBuffer = replaceOutputBuffer(sampleCount * 2)
        var sampleIndex = 0
        while (inputBuffer.remaining() >= 2) {
            val frame = frameCursor + (sampleIndex / channels)
            val multiplier = studioAudioGainAtFrame(
                frame = frame,
                totalFrames = durationMs * inputAudioFormat.sampleRate / 1_000L,
                baseGain = gain,
                fadeInFrames = fadeInMs * inputAudioFormat.sampleRate / 1_000L,
                fadeOutFrames = fadeOutMs * inputAudioFormat.sampleRate / 1_000L
            )
            val sample = inputBuffer.short.toInt()
            outputBuffer.putShort((sample * multiplier).roundToInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort())
            sampleIndex++
        }
        frameCursor += sampleIndex / channels
        outputBuffer.flip()
    }

    override fun onReset() {
        frameCursor = 0L
    }
}
