package com.nearer.app

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import java.nio.ByteBuffer
import kotlin.math.roundToInt

/**
 * Applies per-track gain, fades and stereo balance during offline Media3 export.
 * Mono sources are duplicated to stereo so mixed tracks share one output layout.
 * ToInt16PcmAudioProcessor must precede this processor in the effects chain.
 */
@androidx.media3.common.util.UnstableApi
internal class StudioTrackGainProcessor(
    private val gain: Float,
    private val pan: Float,
    private val durationMs: Long,
    private val fadeInMs: Long,
    private val fadeOutMs: Long
) : BaseAudioProcessor() {

    private var frameCursor = 0L

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        if (inputAudioFormat.encoding != C.ENCODING_PCM_16BIT || inputAudioFormat.channelCount !in 1..2) {
            throw AudioProcessor.UnhandledAudioFormatException(inputAudioFormat)
        }
        return if (inputAudioFormat.channelCount == 1) {
            AudioProcessor.AudioFormat(inputAudioFormat.sampleRate, 2, C.ENCODING_PCM_16BIT)
        } else inputAudioFormat
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val inputBytes = inputBuffer.remaining()
        if (inputBytes == 0) return
        val inputChannels = inputAudioFormat.channelCount
        val frames = inputBytes / (inputChannels * 2)
        val outputBuffer = replaceOutputBuffer(frames * 2 * 2)
        val (leftPan, rightPan) = studioAudioPanFactors(pan)
        val sampleRate = inputAudioFormat.sampleRate
        val totalFrames = durationMs * sampleRate / 1_000L
        val fadeInFrames = fadeInMs * sampleRate / 1_000L
        val fadeOutFrames = fadeOutMs * sampleRate / 1_000L

        repeat(frames) { frameInBuffer ->
            val frame = frameCursor + frameInBuffer
            val envelope = studioAudioGainAtFrame(frame, totalFrames, gain, fadeInFrames, fadeOutFrames)
            if (inputChannels == 1) {
                val mono = inputBuffer.short.toInt()
                outputBuffer.putShort(applyStudioGain(mono, envelope * leftPan))
                outputBuffer.putShort(applyStudioGain(mono, envelope * rightPan))
            } else {
                val left = inputBuffer.short.toInt()
                val right = inputBuffer.short.toInt()
                outputBuffer.putShort(applyStudioGain(left, envelope * leftPan))
                outputBuffer.putShort(applyStudioGain(right, envelope * rightPan))
            }
        }
        frameCursor += frames
        outputBuffer.flip()
    }

    override fun onReset() {
        frameCursor = 0L
    }
}

private fun applyStudioGain(sample: Int, gain: Float): Short =
    (sample * gain.coerceIn(0f, 1f)).roundToInt()
        .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
