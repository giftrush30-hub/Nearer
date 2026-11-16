package com.nearer.app

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.util.UnstableApi
import java.nio.ByteBuffer
import java.nio.ByteOrder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.PI
import kotlin.math.sin

@OptIn(UnstableApi::class)
class StudioVocalPolishProcessorTest {
    private fun process(
        channels: Int,
        samples: FloatArray,
        controls: StudioVocalControls = StudioVocalControls()
    ): ShortArray {
        val processor = StudioVocalPolishProcessor(StudioVocalProfile.CLEAN, controls)
        val format = AudioProcessor.AudioFormat(48_000, channels, C.ENCODING_PCM_16BIT)
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

    @Test fun monoInputBecomesStereoWithMatchingChannels() {
        val input = FloatArray(2_048) { index ->
            (0.25 * sin(2.0 * PI * 220.0 * index / 48_000.0)).toFloat()
        }
        val output = process(1, input)
        assertEquals(input.size * 2, output.size)
        for (frame in 0 until input.size) {
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
        fun rms(samples: ShortArray): Double =
            kotlin.math.sqrt(samples.map { it.toDouble() * it }.average()) / 32767.0
        assertTrue("Warmth should add controlled low-frequency body", rms(warm) > rms(neutral) * 1.03)
    }
}
