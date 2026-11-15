package com.nearer.app

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import java.nio.ByteBuffer
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.tanh

internal enum class StudioVocalProfile(
    val title: String,
    val lowCutHz: Float,
    val presence: Float,
    val deEssAmount: Float,
    val compressorThreshold: Float,
    val compressorRatio: Float,
    val gateThreshold: Float,
    val roomMix: Float,
    val drive: Float
) {
    CLEAN("Clean Studio", 75f, 0.10f, 0.18f, 0.42f, 2.2f, 0.0018f, 0.025f, 1.02f),
    WARM_CONDENSER("Warm Condenser", 82f, 0.16f, 0.24f, 0.34f, 3.0f, 0.0024f, 0.055f, 1.08f),
    BRIGHT_POP("Bright Pop", 95f, 0.24f, 0.38f, 0.30f, 3.6f, 0.0028f, 0.075f, 1.12f),
    PODCAST("Podcast Voice", 90f, 0.20f, 0.30f, 0.28f, 3.2f, 0.0035f, 0.018f, 1.06f)
}

/**
 * Offline vocal polish chain for PCM16 mono/stereo exports.
 * It is intentionally a finishing processor, not a physical microphone model or pitch-correction engine.
 */
@androidx.media3.common.util.UnstableApi
internal class StudioVocalPolishProcessor(
    private val profile: StudioVocalProfile
) : BaseAudioProcessor() {
    private var frameCursor = 0L
    private var previousInput = FloatArray(2)
    private var previousHighPass = FloatArray(2)
    private var lowPassPresence = FloatArray(2)
    private var lowPassEss = FloatArray(2)
    private var compressorEnvelope = 0f
    private var gateEnvelope = 0f
    private var delay = FloatArray(2)
    private var delayIndex = 0
    private var highPassCoefficient = 0f
    private var presenceCoefficient = 0f
    private var essCoefficient = 0f
    private var delayFrames = 1

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        if (inputAudioFormat.encoding != C.ENCODING_PCM_16BIT || inputAudioFormat.channelCount !in 1..2) {
            throw AudioProcessor.UnhandledAudioFormatException(inputAudioFormat)
        }
        val sampleRate = inputAudioFormat.sampleRate.coerceAtLeast(8_000)
        highPassCoefficient = rcCoefficient(profile.lowCutHz, sampleRate)
        presenceCoefficient = rcCoefficient(2_400f, sampleRate)
        essCoefficient = rcCoefficient(5_800f, sampleRate)
        delayFrames = (sampleRate * 0.055f).toInt().coerceAtLeast(1)
        delay = FloatArray(delayFrames * 2)
        return AudioProcessor.AudioFormat(sampleRate, 2, C.ENCODING_PCM_16BIT)
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val inputChannels = inputAudioFormat.channelCount
        val frames = inputBuffer.remaining() / (inputChannels * 2)
        if (frames <= 0) return
        val output = replaceOutputBuffer(frames * 4)
        val sampleRate = inputAudioFormat.sampleRate.coerceAtLeast(8_000)
        val compressorAttack = exp(-1f / (sampleRate * 0.012f))
        val compressorRelease = exp(-1f / (sampleRate * 0.090f))
        val gateAttack = exp(-1f / (sampleRate * 0.004f))
        val gateRelease = exp(-1f / (sampleRate * 0.080f))
        val driveNorm = tanh(profile.drive.toDouble()).toFloat().coerceAtLeast(0.001f)

        repeat(frames) {
            val left = inputBuffer.short / 32768f
            val right = if (inputChannels == 2) inputBuffer.short / 32768f else left
            var framePeak = 0f
            val inputs = floatArrayOf(left, right)
            val processed = FloatArray(2)

            for (channel in 0..1) {
                val raw = inputs[channel]
                val highPass = highPassCoefficient * (previousHighPass[channel] + raw - previousInput[channel])
                previousInput[channel] = raw
                previousHighPass[channel] = highPass

                lowPassPresence[channel] += (highPass - lowPassPresence[channel]) * (1f - presenceCoefficient)
                val presence = highPass - lowPassPresence[channel]
                val present = highPass + presence * profile.presence

                lowPassEss[channel] += (present - lowPassEss[channel]) * (1f - essCoefficient)
                val sibilance = present - lowPassEss[channel]
                val essLevel = abs(sibilance)
                val essReduction = if (essLevel > 0.075f) {
                    (1f - profile.deEssAmount * ((essLevel - 0.075f) / 0.35f).coerceIn(0f, 1f))
                } else 1f
                processed[channel] = present - sibilance * (1f - essReduction)
                framePeak = maxOf(framePeak, abs(processed[channel]))
            }

            val gateCoefficient = if (framePeak > gateEnvelope) gateAttack else gateRelease
            gateEnvelope = gateCoefficient * gateEnvelope + (1f - gateCoefficient) * framePeak
            val gateGain = if (gateEnvelope < profile.gateThreshold) {
                (gateEnvelope / profile.gateThreshold).coerceIn(0.12f, 1f)
            } else 1f

            val compressorCoefficient = if (framePeak > compressorEnvelope) compressorAttack else compressorRelease
            compressorEnvelope = compressorCoefficient * compressorEnvelope + (1f - compressorCoefficient) * framePeak
            val compressedLevel = if (compressorEnvelope > profile.compressorThreshold) {
                profile.compressorThreshold +
                    (compressorEnvelope - profile.compressorThreshold) / profile.compressorRatio
            } else compressorEnvelope
            val compressorGain = if (compressorEnvelope > 0.0001f) {
                (compressedLevel / compressorEnvelope).coerceIn(0.35f, 1.5f)
            } else 1f

            for (channel in 0..1) {
                val driven = processed[channel] * gateGain * compressorGain * profile.drive
                val saturated = tanh(driven.toDouble()).toFloat() / driveNorm
                val delayPosition = delayIndex * 2 + channel
                val delayed = delay[delayPosition]
                delay[delayPosition] = (saturated + delayed * 0.16f).coerceIn(-1f, 1f)
                val polished = (saturated * (1f - profile.roomMix) + delayed * profile.roomMix)
                    .coerceIn(-1f, 1f)
                val pcm = (polished * 32767f).toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
                output.putShort(pcm.toShort())
            }
            delayIndex = (delayIndex + 1) % delayFrames
            frameCursor++
        }
        output.flip()
    }

    override fun onReset() {
        frameCursor = 0L
        previousInput = FloatArray(2)
        previousHighPass = FloatArray(2)
        lowPassPresence = FloatArray(2)
        lowPassEss = FloatArray(2)
        compressorEnvelope = 0f
        gateEnvelope = 0f
        delay.fill(0f)
        delayIndex = 0
    }

    private fun rcCoefficient(cutoffHz: Float, sampleRate: Int): Float {
        val rc = 1f / (2f * Math.PI.toFloat() * cutoffHz.coerceAtLeast(1f))
        val dt = 1f / sampleRate.toFloat()
        return rc / (rc + dt)
    }
}
