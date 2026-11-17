package com.nearer.app

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.util.UnstableApi
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(UnstableApi::class)
class StudioTrackToneProcessorTest {
    private fun process(
        samples: ShortArray,
        lowDb: Float = 0f,
        midDb: Float = 0f,
        highDb: Float = 0f,
        reverb: Float = 0f
    ): ShortArray {
        val processor = StudioTrackToneProcessor(lowDb, midDb, highDb, reverb)
        val format = AudioProcessor.AudioFormat(48_000, 2, C.ENCODING_PCM_16BIT)
        assertEquals(2, processor.configure(format).channelCount)
        processor.flush()
        val input = ByteBuffer.allocateDirect(samples.size * 2).order(ByteOrder.nativeOrder())
        samples.forEach { input.putShort(it) }
        input.flip()
        processor.queueInput(input)
        // Queue EOS before draining the last input output to verify pending PCM is preserved.
        processor.queueEndOfStream()
        val output = processor.output
        val result = ShortArray(output.remaining() / 2) { output.short }
        processor.reset()
        return result
    }

    private fun tone(frequencyHz: Double, frames: Int = 8_192): ShortArray =
        ShortArray(frames * 2) { index ->
            val frame = index / 2
            val sample = (0.12 * sin(2.0 * PI * frequencyHz * frame / 48_000.0) * 32767.0).toInt()
            sample.toShort()
        }

    private fun rms(samples: ShortArray): Double =
        kotlin.math.sqrt(samples.map { it.toDouble() * it }.average())

    @Test fun flatDrySettingsPreserveTheStereoSignal() {
        val input = tone(440.0, 2_048)
        val output = process(input)
        assertEquals(input.size, output.size)
        assertTrue(input.indices.all { abs(input[it].toInt() - output[it].toInt()) <= 1 })
    }

    @Test fun lowBandBoostRaisesLowFrequencyEnergy() {
        val input = tone(100.0)
        val flat = process(input)
        val boosted = process(input, lowDb = 6f)
        assertTrue("Low EQ boost should increase low-frequency RMS", rms(boosted) > rms(flat) * 1.5)
    }

    @Test fun highBandBoostRaisesHighFrequencyEnergy() {
        val input = tone(8_000.0)
        val flat = process(input)
        val boosted = process(input, highDb = 6f)
        assertTrue("High EQ boost should increase high-frequency RMS", rms(boosted) > rms(flat) * 1.5)
    }

    @Test fun roomReverbCreatesAControlledTailAfterAnImpulse() {
        val frames = 4_096
        val input = ShortArray(frames * 2)
        input[0] = 19_660
        val dry = process(input)
        val wet = process(input, reverb = 0.3f)
        assertEquals(0, dry[1_392 * 2].toInt())
        assertTrue("Reverb should create a delayed left-channel tail", wet[1_392 * 2].toInt() > 500)
        assertEquals("Reverb must not leak into the silent right channel", 0, wet[1_392 * 2 + 1].toInt())
    }

    @Test fun reverbTailContinuesAfterTheFinalSourceFrame() {
        val frames = 256
        val input = ShortArray(frames * 2)
        input[(frames - 1) * 2] = 19_660
        val output = process(input, reverb = 0.3f)
        assertTrue("Reverb should extend the rendered track", output.size > input.size)
        assertTrue("The extended section should contain a decaying echo", output.drop(input.size).any { abs(it.toInt()) > 100 })
    }

    @Test fun unsafeSettingsAreBoundedWithoutClippingPcm() {
        val input = tone(1_000.0, 4_096)
        val output = process(input, lowDb = 80f, midDb = 80f, highDb = 80f, reverb = 1f)
        assertTrue(output.all { it.toInt() in Short.MIN_VALUE.toInt()..Short.MAX_VALUE.toInt() })
    }
}
