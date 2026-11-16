package com.nearer.app

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import java.nio.ByteBuffer
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.pow
import kotlin.math.roundToInt

internal enum class StudioTuningStyle(val title: String, val strength: Float) {
    NATURAL("Natural", 0.72f),
    HARD("Hard Tune", 1f)
}

internal data class StudioPitchCorrectionSettings(
    val keyRoot: Int = 0,
    val minor: Boolean = false,
    val style: StudioTuningStyle = StudioTuningStyle.NATURAL,
    val retuneSpeedMs: Int = 90,
    val enabled: Boolean = false
) {
    fun normalized() = copy(
        keyRoot = keyRoot.coerceIn(0, 11),
        retuneSpeedMs = retuneSpeedMs.coerceIn(0, 500)
    )
}

internal object StudioPitchCorrectionMath {
    private val major = intArrayOf(0, 2, 4, 5, 7, 9, 11)
    private val minor = intArrayOf(0, 2, 3, 5, 7, 8, 10)

    fun targetMidi(midi: Float, root: Int, isMinor: Boolean): Int {
        val intervals = if (isMinor) minor else major
        val baseOctave = floor(midi / 12f).toInt()
        return (baseOctave - 1..baseOctave + 1)
            .flatMap { octave -> intervals.map { octave * 12 + root.coerceIn(0, 11) + it } }
            .minByOrNull { kotlin.math.abs(it - midi) } ?: midi.roundToInt()
    }

    fun pitchRatio(currentMidi: Float, targetMidi: Int, strength: Float): Float {
        val cents = (targetMidi - currentMidi) * 100f * strength.coerceIn(0f, 1f)
        return 2f.pow(cents / 1200f).coerceIn(0.5f, 2f)
    }

    fun smoothingCoefficient(sampleRate: Int, hopSamples: Int, retuneSpeedMs: Int): Float {
        if (retuneSpeedMs <= 0) return 0f
        val seconds = retuneSpeedMs.coerceIn(1, 500) / 1000f
        return exp(-hopSamples.toFloat() / (sampleRate.coerceAtLeast(8_000) * seconds))
    }
}

/**
 * Offline monophonic vocal pitch correction.
 *
 * YIN detects the lead-vocal fundamental and the selected scale supplies the nearest
 * legal note. A dual-head granular delay shifter applies the correction without
 * changing clip duration. It is intended for a single lead vocal, not chords or
 * polyphonic backing tracks. Formant preservation is not part of this first DSP pass.
 */
@androidx.media3.common.util.UnstableApi
internal class StudioPitchCorrectionProcessor(settings: StudioPitchCorrectionSettings) : BaseAudioProcessor() {
    private val settings = settings.normalized()
    private var sampleRate = 44_100
    private var channels = 2
    private val ringSize = 16_384
    private val ringMask = ringSize - 1
    private var ring = Array(2) { FloatArray(ringSize) }
    private var writeFrame = 0L
    private var analysisCounter = 0
    private val analysisHop = 512
    private val analysisFrame = FloatArray(2_048)
    private var grainSamples = 1f
    private var phase = 0f
    private var targetRatio = 1f
    private var smoothedRatio = 1f
    private var voiced = false

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        if (inputAudioFormat.encoding != C.ENCODING_PCM_16BIT || inputAudioFormat.channelCount !in 1..2) {
            throw AudioProcessor.UnhandledAudioFormatException(inputAudioFormat)
        }
        sampleRate = inputAudioFormat.sampleRate.coerceAtLeast(8_000)
        channels = inputAudioFormat.channelCount
        ring = Array(2) { FloatArray(ringSize) }
        grainSamples = (sampleRate * 0.040f).coerceAtLeast(256f)
        resetState()
        return inputAudioFormat
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val frameBytes = channels * 2
        val frames = inputBuffer.remaining() / frameBytes
        if (frames <= 0) return
        val output = replaceOutputBuffer(frames * frameBytes)
        repeat(frames) {
            val left = inputBuffer.short.toInt() / 32768f
            val right = if (channels == 2) inputBuffer.short.toInt() / 32768f else left
            val index = writeFrame.toInt() and ringMask
            ring[0][index] = left
            ring[1][index] = right

            analysisCounter++
            if (analysisCounter >= analysisHop && writeFrame >= analysisFrame.size) {
                analysisCounter = 0
                analyseVoice()
            }

            val phaseA = phase
            val phaseB = wrapPhase(phase + 0.5f)
            val delayA = delayForPhase(phaseA).coerceAtMost(writeFrame.toFloat())
            val delayB = delayForPhase(phaseB).coerceAtMost(writeFrame.toFloat())
            val weightA = cos(PI.toFloat() * phaseA).let { it * it }
            val weightB = cos(PI.toFloat() * phaseB).let { it * it }
            val shifting = settings.enabled && voiced && kotlin.math.abs(smoothedRatio - 1f) > 0.0005f

            for (channel in 0 until channels) {
                val sample = if (shifting) {
                    val a = readDelayed(channel, delayA)
                    val b = readDelayed(channel, delayB)
                    a * weightA + b * weightB
                } else {
                    if (channel == 0) left else right
                }
                output.putShort((sample.coerceIn(-1f, 1f) * 32767f).roundToInt().toShort())
            }

            phase = wrapPhase(phase + (1f - smoothedRatio) / grainSamples)
            writeFrame++
        }
        output.flip()
    }

    private fun analyseVoice() {
        val start = writeFrame - analysisFrame.size
        for (i in analysisFrame.indices) {
            val index = (start + i).toInt() and ringMask
            analysisFrame[i] = if (channels == 2) (ring[0][index] + ring[1][index]) * 0.5f else ring[0][index]
        }
        val reading = StudioPitchEngine.detect(analysisFrame, sampleRate, 75f, 950f)
        if (reading == null || reading.confidence < 0.55f) {
            voiced = false
            targetRatio = 1f
        } else {
            val target = StudioPitchCorrectionMath.targetMidi(reading.midiNote, settings.keyRoot, settings.minor)
            targetRatio = StudioPitchCorrectionMath.pitchRatio(reading.midiNote, target, settings.style.strength)
            voiced = true
        }
        val smoothing = StudioPitchCorrectionMath.smoothingCoefficient(sampleRate, analysisHop, settings.retuneSpeedMs)
        smoothedRatio = if (smoothing <= 0f) targetRatio else smoothing * smoothedRatio + (1f - smoothing) * targetRatio
    }

    private fun delayForPhase(value: Float): Float = wrapPhase(value + 0.5f) * grainSamples

    private fun readDelayed(channel: Int, delay: Float): Float {
        val whole = delay.toInt()
        val fraction = delay - whole
        val newerIndex = (writeFrame.toInt() - whole) and ringMask
        val olderIndex = (newerIndex - 1) and ringMask
        return ring[channel][newerIndex] * (1f - fraction) + ring[channel][olderIndex] * fraction
    }

    private fun wrapPhase(value: Float): Float {
        var result = value
        while (result >= 1f) result -= 1f
        while (result < 0f) result += 1f
        return result
    }

    private fun resetState() {
        ring.forEach { it.fill(0f) }
        writeFrame = 0L
        analysisCounter = 0
        phase = 0f
        targetRatio = 1f
        smoothedRatio = 1f
        voiced = false
    }

    override fun onFlush() = resetState()
    override fun onReset() = resetState()
}
