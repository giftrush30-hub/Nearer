package com.nearer.app

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import java.nio.ByteBuffer
import java.nio.ByteOrder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.PI
import kotlin.math.sin

class StudioVocalPolishProcessorTest {
    private fun process(
        channels: Int,
        samples: FloatArray,
        controls: StudioVocalControls = StudioVocalControls(),
        sampleRate: Int = 48_000
    ): ShortArray {
        val processor = StudioVocalPolishProcessor(StudioVocalProfile.CLEAN, controls)
        val format = AudioProcessor.AudioFormat(sampleRate, channels, C.ENCODING_PCM_16BIT)
        val outputFormat = processor.configure(format)
        assertEquals(2, outputFormat.channelCount)
        processor.flush()
        val input = ByteBuffer.allocateDirect(samples.size * 2).order(ByteOrder.nativeOrder())
        samples.forEach { input.putShort((it.coerceIn(-1f, 1f) * 32767f).toInt().toShort()) }
        input.flip()
        processor.queueInput(input)
        val output = processor.output
        val result = ShortArray(output.remaining() / 2)
        for (i in result.indices) result[i] = output.short
        processor.reset()
        return result
    }

    private fun rms(samples: ShortArray, startSample: Int = 0): Double {
        val usable = samples.drop(startSample.coerceIn(0, samples.size))
        if (usable.isEmpty()) return 0.0
        return kotlin.math.sqrt(usable.map { it.toDouble() * it }.average()) / 32767.0
    }

    @Test fun monoInputBecomesStereoWithMatchingChannels() {
        val input = FloatArray(2_048) { index ->
            (0.25 * sin(2.0 * PI * 220.0 * index / 48_000.0)).toFloat()
        }
        val output = process(1, input)
        assertEquals(input.size * 2, output.size)
        for (frame in input.indices) {
            assertEquals(output[frame * 2], output[frame * 2 + 1])
        }
        assertTrue(output.any { it.toInt() != 0 })
    }

    @Test fun sustainedHotSignalSettlesBelowConfiguredLimiterCeiling() {
        val ceiling = 0.78f
        val input = FloatArray(6_000) { index ->
            (0.98 * sin(2.0 * PI * 220.0 * index / 48_000.0)).toFloat()
        }
        val output = process(1, input, StudioVocalControls(limiterCeiling = ceiling))
        val lastFrames = output.takeLast(512)
        val peak = lastFrames.maxOf { abs(it.toInt()) } / 32767f
        assertTrue("Peak $peak should settle near or below limiter ceiling $ceiling", peak <= ceiling + 0.04f)
    }

    @Test fun silenceDoesNotCreateAnAudibleOutputSignal() {
        val output = process(1, FloatArray(2_048))
        assertTrue(output.all { abs(it.toInt()) <= 1 })
    }

    @Test fun stereoOutputRemainsBoundedForOppositePolarityInput() {
        val frames = 4_096
        val input = FloatArray(frames * 2) { index ->
            val frame = index / 2
            val value = (0.85 * sin(2.0 * PI * 330.0 * frame / 48_000.0)).toFloat()
            if (index % 2 == 0) value else -value
        }
        val output = process(2, input, StudioVocalControls(stereoWidth = 1.4f, limiterCeiling = 0.82f))
        assertEquals(frames * 2, output.size)
        val peak = output.maxOf { abs(it.toInt()) } / 32767f
        assertTrue("Processed PCM should remain within full scale", peak <= 1f)
    }

    @Test fun warmthControlAddsLowFrequencyBodyWithoutChangingTheOriginal() {
        val input = FloatArray(8_192) { index ->
            (0.12 * sin(2.0 * PI * 110.0 * index / 48_000.0)).toFloat()
        }
        val neutral = process(1, input, StudioVocalControls(warmth = 0f))
        val warm = process(1, input, StudioVocalControls(warmth = 1f))
        assertTrue("Warmth should add controlled low-frequency body", rms(warm) > rms(neutral) * 1.03)
    }

    @Test fun deEsserReducesSibilanceBandMoreThanLowVocalFundamental() {
        val frames = 24_000
        val highTone = FloatArray(frames) { index ->
            (0.48 * sin(2.0 * PI * 7_000.0 * index / 48_000.0)).toFloat()
        }
        val lowTone = FloatArray(frames) { index ->
            (0.48 * sin(2.0 * PI * 220.0 * index / 48_000.0)).toFloat()
        }
        val neutralControls = StudioVocalControls(deEss = 0f)
        val strongerDeEss = StudioVocalControls(deEss = 0.5f)
        val highNeutral = process(1, highTone, neutralControls)
        val highTreated = process(1, highTone, strongerDeEss)
        val lowNeutral = process(1, lowTone, neutralControls)
        val lowTreated = process(1, lowTone, strongerDeEss)
        val highReduction = rms(highNeutral, 4_000) - rms(highTreated, 4_000)
        val lowReduction = rms(lowNeutral, 4_000) - rms(lowTreated, 4_000)
        assertTrue("Additional de-essing should reduce the sibilance band", highReduction > 0.015)
        assertTrue("De-essing should preserve the low vocal fundamental", highReduction > lowReduction * 4.0)
    }

    @Test fun strongerCompressionControlsSustainedLoudVocalMoreThanGentleSetting() {
        val input = FloatArray(24_000) { index ->
            (0.86 * sin(2.0 * PI * 220.0 * index / 48_000.0)).toFloat()
        }
        val gentle = process(1, input, StudioVocalControls(compression = 0f))
        val stronger = process(1, input, StudioVocalControls(compression = 4f))
        assertTrue("A stronger ratio should reduce sustained loud-signal RMS",
            rms(stronger, 4_000) < rms(gentle, 4_000) * 0.94)
    }

    @Test fun zeroStereoWidthProducesAStableCentreSignal() {
        val frames = 8_192
        val input = FloatArray(frames * 2) { index ->
            val frame = index / 2
            val frequency = if (index % 2 == 0) 220.0 else 330.0
            (0.2 * sin(2.0 * PI * frequency * frame / 48_000.0)).toFloat()
        }
        val output = process(2, input, StudioVocalControls(stereoWidth = 0f))
        for (frame in 0 until frames) {
            assertEquals(output[frame * 2], output[frame * 2 + 1])
        }
    }

    @Test fun lowSampleRateConfigurationClampsAirFilterBelowNyquist() {
        val input = FloatArray(4_000) { index ->
            (0.2 * sin(2.0 * PI * 1_000.0 * index / 8_000.0)).toFloat()
        }
        val output = process(1, input, sampleRate = 8_000)
        assertEquals(input.size * 2, output.size)
        assertTrue(output.any { it.toInt() != 0 })
    }
}
