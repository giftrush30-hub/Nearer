package com.nearer.app

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import java.nio.ByteBuffer
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * Per-track three-band tone shaping and a small stereo room reverb for offline mix export.
 * Flat EQ and zero reverb are transparent apart from PCM quantisation.
 */
@androidx.media3.common.util.UnstableApi
internal class StudioTrackToneProcessor(
    lowGainDb: Float,
    midGainDb: Float,
    highGainDb: Float,
    reverbMix: Float
) : BaseAudioProcessor() {
    private val lowGain = dbToLinear(lowGainDb)
    private val midGain = dbToLinear(midGainDb)
    private val highGain = dbToLinear(highGainDb)
    private val wetMix = reverbMix.coerceIn(0f, 0.35f)
    private var sampleRate = 48_000
    private var lowCoefficient = 0f
    private var upperCoefficient = 0f
    private val lowState = FloatArray(2)
    private val upperState = FloatArray(2)
    private var delayA = Array(2) { FloatArray(1) }
    private var delayB = Array(2) { FloatArray(1) }
    private var delayIndexA = 0
    private var delayIndexB = 0
    private var feedbackA = 0.28f
    private var feedbackB = 0.22f

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        if (inputAudioFormat.encoding != C.ENCODING_PCM_16BIT || inputAudioFormat.channelCount != 2) {
            throw AudioProcessor.UnhandledAudioFormatException(inputAudioFormat)
        }
        sampleRate = inputAudioFormat.sampleRate.coerceAtLeast(8_000)
        lowCoefficient = onePoleCoefficient(250f, sampleRate)
        upperCoefficient = onePoleCoefficient(4_000f, sampleRate)
        delayA = Array(2) { FloatArray((sampleRate * 0.029f).toInt().coerceAtLeast(1)) }
        delayB = Array(2) { FloatArray((sampleRate * 0.041f).toInt().coerceAtLeast(1)) }
        resetDsp()
        return inputAudioFormat
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val frames = inputBuffer.remaining() / 4
        if (frames <= 0) return
        val output = replaceOutputBuffer(frames * 4)
        repeat(frames) {
            for (channel in 0..1) {
                val input = inputBuffer.short / 32768f
                lowState[channel] += lowCoefficient * (input - lowState[channel])
                upperState[channel] += upperCoefficient * (input - upperState[channel])
                val low = lowState[channel]
                val mid = upperState[channel] - lowState[channel]
                val high = input - upperState[channel]
                val equalized = low * lowGain + mid * midGain + high * highGain

                val delayedA = delayA[channel][delayIndexA]
                val delayedB = delayB[channel][delayIndexB]
                delayA[channel][delayIndexA] = (input + delayedA * feedbackA).coerceIn(-1.5f, 1.5f)
                delayB[channel][delayIndexB] = (input + delayedB * feedbackB).coerceIn(-1.5f, 1.5f)
                val reverb = (delayedA + delayedB) * 0.5f
                val mixed = (equalized * (1f - wetMix) + reverb * wetMix).coerceIn(-1f, 1f)
                output.putShort((mixed * 32767f).roundToInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort())
            }
            delayIndexA = (delayIndexA + 1) % delayA[0].size
            delayIndexB = (delayIndexB + 1) % delayB[0].size
        }
        output.flip()
    }

    override fun onFlush() = resetDsp()
    override fun onReset() = resetDsp()

    private fun resetDsp() {
        lowState.fill(0f)
        upperState.fill(0f)
        delayA.forEach { it.fill(0f) }
        delayB.forEach { it.fill(0f) }
        delayIndexA = 0
        delayIndexB = 0
    }

    private fun dbToLinear(db: Float): Float = 10f.pow(db.coerceIn(-12f, 12f) / 20f)

    private fun onePoleCoefficient(cutoffHz: Float, rate: Int): Float =
        (1.0 - exp(-2.0 * PI * cutoffHz / rate)).toFloat().coerceIn(0f, 1f)
}
