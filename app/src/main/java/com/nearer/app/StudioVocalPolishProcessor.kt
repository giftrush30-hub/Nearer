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
    PODCAST("Podcast Voice", 90f, 0.20f, 0.30f, 0.28f, 3.2f, 0.0035f, 0.018f, 1.06f),
    CLEAN_NARRATION("Clean Narration", 78f, 0.12f, 0.22f, 0.38f, 2.4f, 0.0016f, 0.012f, 1.015f),
    LEAD_VOCAL("Lead Vocal", 85f, 0.19f, 0.28f, 0.31f, 3.4f, 0.0022f, 0.045f, 1.075f)
}

/**
 * Offline vocal polish chain for PCM16 mono/stereo exports.
 * It is intentionally a finishing processor, not a physical microphone model or pitch-correction engine.
 */
@androidx.media3.common.util.UnstableApi
internal class StudioVocalPolishProcessor(
    private val profile: StudioVocalProfile,
    controls: StudioVocalControls = StudioVocalControls()
) : BaseAudioProcessor() {
    private val controls = controls.normalized()
    private var frameCursor = 0L
    private var previousInput = FloatArray(2)
    private var previousHighPass = FloatArray(2)
    private var lowPassPresence = FloatArray(2)
    private var lowPassEss = FloatArray(2)
    private var lowPassAir = FloatArray(2)
    private var compressorEnvelope = 0f
    private var gateEnvelope = 0f
    private var limiterGain = 1f
    private var delay = FloatArray(2)
    private val masterScratch = FloatArray(2)
    private var delayIndex = 0
    private val inputScratch = FloatArray(2)
    private val processedScratch = FloatArray(2)
    private var highPassCoefficient = 0f
    private var presenceCoefficient = 0f
    private var essCoefficient = 0f
    private var airCoefficient = 0f
    private var delayFrames = 1

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        if (inputAudioFormat.encoding != C.ENCODING_PCM_16BIT || inputAudioFormat.channelCount !in 1..2) {
            throw AudioProcessor.UnhandledAudioFormatException(inputAudioFormat)
        }
        val sampleRate = inputAudioFormat.sampleRate.coerceAtLeast(8_000)
        val adjustedLowCut = (profile.lowCutHz * (1f - controls.warmth * 0.3f)).coerceIn(50f, 180f)
        highPassCoefficient = rcCoefficient(adjustedLowCut, sampleRate)
        presenceCoefficient = rcCoefficient(2_400f, sampleRate)
        essCoefficient = rcCoefficient(5_800f, sampleRate)
        airCoefficient = rcCoefficient(9_000f, sampleRate)
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
        val presenceAmount = (profile.presence + controls.presence).coerceIn(0f, 0.5f)
        val deEssAmount = (profile.deEssAmount + controls.deEss).coerceIn(0f, 0.85f)
        val compressorRatio = (profile.compressorRatio + controls.compression).coerceIn(1.5f, 8f)
        val roomMix = (profile.roomMix + controls.ambience).coerceIn(0f, 0.25f)
        val outputGain = controls.outputGain
        val airAmount = controls.air
        val stereoWidth = controls.stereoWidth
        val limiterCeiling = controls.limiterCeiling
        val noiseReduction = controls.noiseReduction
        val gateThreshold = profile.gateThreshold * (1f + noiseReduction * 5f)
        val limiterAttack = exp(-1f / (sampleRate * 0.0015f))
        val limiterRelease = exp(-1f / (sampleRate * 0.080f))

        repeat(frames) {
            val left = inputBuffer.short / 32768f
            val right = if (inputChannels == 2) inputBuffer.short / 32768f else left
            var framePeak = 0f
            inputScratch[0] = left
            inputScratch[1] = right

            for (channel in 0..1) {
                val raw = inputScratch[channel]
                val highPass = highPassCoefficient * (previousHighPass[channel] + raw - previousInput[channel])
                previousInput[channel] = raw
                previousHighPass[channel] = highPass

                lowPassPresence[channel] += (highPass - lowPassPresence[channel]) * (1f - presenceCoefficient)
                val presence = highPass - lowPassPresence[channel]
                val present = highPass + presence * presenceAmount

                lowPassEss[channel] += (present - lowPassEss[channel]) * (1f - essCoefficient)
                lowPassAir[channel] += (present - lowPassAir[channel]) * (1f - airCoefficient)
                val airBand = present - lowPassAir[channel]
                val sibilance = present - lowPassEss[channel]
                val essLevel = abs(sibilance)
                val essReduction = if (essLevel > 0.075f) {
                    (1f - deEssAmount * ((essLevel - 0.075f) / 0.35f).coerceIn(0f, 1f))
                } else 1f
                processedScratch[channel] = present - sibilance * (1f - essReduction) + airBand * airAmount
                framePeak = maxOf(framePeak, abs(processedScratch[channel]))
            }
            if (inputChannels == 2 && stereoWidth != 1f) {
                val mid = (processedScratch[0] + processedScratch[1]) * 0.5f
                val side = (processedScratch[0] - processedScratch[1]) * 0.5f * stereoWidth
                processedScratch[0] = mid + side
                processedScratch[1] = mid - side
                framePeak = maxOf(abs(processedScratch[0]), abs(processedScratch[1]))
            }

            val gateCoefficient = if (framePeak > gateEnvelope) gateAttack else gateRelease
            gateEnvelope = gateCoefficient * gateEnvelope + (1f - gateCoefficient) * framePeak
            val gateGain = if (gateEnvelope < gateThreshold) {
                (gateEnvelope / gateThreshold).coerceIn(0.12f * (1f - noiseReduction), 1f)
            } else 1f

            val compressorCoefficient = if (framePeak > compressorEnvelope) compressorAttack else compressorRelease
            compressorEnvelope = compressorCoefficient * compressorEnvelope + (1f - compressorCoefficient) * framePeak
            val compressedLevel = if (compressorEnvelope > profile.compressorThreshold) {
                profile.compressorThreshold +
                    (compressorEnvelope - profile.compressorThreshold) / compressorRatio
            } else compressorEnvelope
            val compressorGain = if (compressorEnvelope > 0.0001f) {
                (compressedLevel / compressorEnvelope).coerceIn(0.35f, 1.5f)
            } else 1f

            for (channel in 0..1) {
                val driven = processedScratch[channel] * gateGain * compressorGain * profile.drive * outputGain
                val saturated = tanh(driven.toDouble()).toFloat() / driveNorm
                val delayPosition = delayIndex * 2 + channel
                val delayed = delay[delayPosition]
                delay[delayPosition] = (saturated + delayed * 0.16f).coerceIn(-1f, 1f)
                masterScratch[channel] = saturated * (1f - roomMix) + delayed * roomMix
            }
            val masterPeak = maxOf(abs(masterScratch[0]), abs(masterScratch[1]))
            val targetLimiterGain = if (masterPeak > limiterCeiling) limiterCeiling / masterPeak else 1f
            val limiterCoefficient = if (targetLimiterGain < limiterGain) limiterAttack else limiterRelease
            limiterGain = limiterCoefficient * limiterGain + (1f - limiterCoefficient) * targetLimiterGain
            for (channel in 0..1) {
                val polished = (masterScratch[channel] * limiterGain).coerceIn(-1f, 1f)
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
        lowPassAir = FloatArray(2)
        compressorEnvelope = 0f
        gateEnvelope = 0f
        limiterGain = 1f
        delay.fill(0f)
        delayIndex = 0
    }

    private fun rcCoefficient(cutoffHz: Float, sampleRate: Int): Float {
        val rc = 1f / (2f * Math.PI.toFloat() * cutoffHz.coerceAtLeast(1f))
        val dt = 1f / sampleRate.toFloat()
        return rc / (rc + dt)
    }
}
